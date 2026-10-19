package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.config.PlotterProperties;
import in.debagnik.catplotlib.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates safe, collision-free G-code tailored specifically for the
 * Bambu Lab A1 3D printer running as a pen plotter.
 * Enforces zero-heat, zero-fan, no-wipe/purge, safe homing, and builds
 * structured 3D toolpath moves for the frontend slicer visualizer.
 */
@Service
public class GcodeGeneratorService {

    private final PlotterProperties plotterProperties;

    @Autowired
    public GcodeGeneratorService(PlotterProperties plotterProperties) {
        this.plotterProperties = plotterProperties != null ? plotterProperties : new PlotterProperties();
    }

    public GcodeGeneratorService() {
        this(new PlotterProperties());
    }

    public static class GcodeGenerationResult {
        public final String gcode;
        public final List<ToolpathMove> moves;
        public final PlotterStatistics statistics;

        public GcodeGenerationResult(String gcode, List<ToolpathMove> moves, PlotterStatistics statistics) {
            this.gcode = gcode;
            this.moves = moves;
            this.statistics = statistics;
        }
    }

    public GcodeGenerationResult generateGcode(List<VectorStroke> strokes, PlotterJobRequest request) {
        StringBuilder bodyGcode = new StringBuilder();
        List<ToolpathMove> moves = new ArrayList<>();

        double zUp = request.getZUp();
        double zDown = request.getZDown();
        double feedrateTravel = request.getFeedrateTravel();
        double feedrateDraw = request.getFeedrateDraw();
        double zLiftFeedrate = 1200.0;
        double zDropFeedrate = 1000.0;

        boolean flipY = request.isFlipY();
        boolean flipX = request.isFlipX();
        double pageHeight = request.getPageHeight() > 0 ? request.getPageHeight() : plotterProperties.getBedLength();
        double pageWidth = request.getPageWidth() > 0 ? request.getPageWidth() : plotterProperties.getBedWidth();

        double nozzleOffsetX = request.getNozzleOffsetX();
        double nozzleOffsetY = request.getNozzleOffsetY();

        // Statistics accumulators
        double totalDrawDistance = 0.0;
        double totalTravelDistance = 0.0;
        double totalTimeSeconds = 0.0;
        int penLiftsCount = 0;
        int moveIndex = 0;

        // Current toolhead position tracker (starts homed at 0, 0, 10mm)
        double curX = 0.0;
        double curY = 0.0;
        double curZ = 10.0;

        // Iterate through each vector stroke
        for (int strokeIdx = 0; strokeIdx < strokes.size(); strokeIdx++) {
            VectorStroke stroke = strokes.get(strokeIdx);
            List<Point2D> pts = stroke.getPoints();
            if (pts == null || pts.size() < 2) continue;

            Point2D startPt = pts.get(0);
            double sx = (flipX ? (pageWidth - startPt.getX()) : startPt.getX()) + nozzleOffsetX;
            double sy = (flipY ? (pageHeight - startPt.getY()) : startPt.getY()) + nozzleOffsetY;

            // A. Aerial Travel Move to Stroke Start Point (G0 with pen up)
            if (curX != sx || curY != sy || curZ != zUp) {
                // Ensure pen is up
                if (curZ != zUp) {
                    bodyGcode.append(String.format(Locale.US, "G0 Z%.2f F%.0f ; Pen Up\n", zUp, zLiftFeedrate));
                    double zDist = Math.abs(zUp - curZ);
                    totalTravelDistance += zDist;
                    totalTimeSeconds += (zDist / zLiftFeedrate) * 60.0;
                    curZ = zUp;
                }

                // Rapid travel in XY
                double travelDist = Math.hypot(sx - curX, sy - curY);
                double travelDuration = (travelDist / feedrateTravel) * 60.0;
                totalTravelDistance += travelDist;
                totalTimeSeconds += travelDuration;

                ToolpathPoint fromPt = new ToolpathPoint(curX, curY, zUp, "TRAVEL", false, strokeIdx);
                ToolpathPoint toPt = new ToolpathPoint(sx, sy, zUp, "TRAVEL", false, strokeIdx);

                moves.add(ToolpathMove.builder()
                        .from(fromPt)
                        .to(toPt)
                        .feedrate(feedrateTravel)
                        .distance(travelDist)
                        .durationSeconds(travelDuration)
                        .type("TRAVEL")
                        .moveIndex(moveIndex++)
                        .strokeIndex(strokeIdx)
                        .build());

                bodyGcode.append(String.format(Locale.US, "G0 X%.3f Y%.3f F%.0f\n", sx, sy, feedrateTravel));
                curX = sx;
                curY = sy;
            }

            // B. Lower Pen (Pen Down)
            bodyGcode.append(String.format(Locale.US, "G1 Z%.2f F%.0f ; Pen Down\n", zDown, zDropFeedrate));
            double zDropDist = Math.abs(curZ - zDown);
            totalTravelDistance += zDropDist;
            totalTimeSeconds += (zDropDist / zDropFeedrate) * 60.0;
            curZ = zDown;

            // C. Drawing Moves (G1 along stroke points)
            for (int i = 1; i < pts.size(); i++) {
                Point2D nextPt = pts.get(i);
                double nx = (flipX ? (pageWidth - nextPt.getX()) : nextPt.getX()) + nozzleOffsetX;
                double ny = (flipY ? (pageHeight - nextPt.getY()) : nextPt.getY()) + nozzleOffsetY;
                double drawDist = Math.hypot(nx - curX, ny - curY);
                if (drawDist < 0.001) continue; // Skip identical duplicate points

                double drawDuration = (drawDist / feedrateDraw) * 60.0;
                totalDrawDistance += drawDist;
                totalTimeSeconds += drawDuration;

                ToolpathPoint fromPt = new ToolpathPoint(curX, curY, zDown, "DRAW", true, strokeIdx);
                ToolpathPoint toPt = new ToolpathPoint(nx, ny, zDown, "DRAW", true, strokeIdx);

                moves.add(ToolpathMove.builder()
                        .from(fromPt)
                        .to(toPt)
                        .feedrate(feedrateDraw)
                        .distance(drawDist)
                        .durationSeconds(drawDuration)
                        .type("DRAW")
                        .moveIndex(moveIndex++)
                        .strokeIndex(strokeIdx)
                        .build());

                bodyGcode.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", nx, ny, feedrateDraw));
                curX = nx;
                curY = ny;
            }

            // D. Lift Pen at end of stroke
            bodyGcode.append(String.format(Locale.US, "G0 Z%.2f F%.0f ; Pen Lift\n", zUp, zLiftFeedrate));
            double zLiftDist = Math.abs(zUp - curZ);
            totalTravelDistance += zLiftDist;
            totalTimeSeconds += (zLiftDist / zLiftFeedrate) * 60.0;
            curZ = zUp;
            penLiftsCount++;
        }

        // Safe End Routine
        appendSafetyEndRoutine(bodyGcode);

        String formattedTime = formatDuration(totalTimeSeconds);

        // Prepend official Bambu Studio header with accurate metadata
        StringBuilder gcode = new StringBuilder();
        appendSafetyHeader(gcode, request, strokes.size(), formattedTime);
        gcode.append(bodyGcode);

        // Calculate line count
        int gcodeLinesCount = gcode.toString().split("\n").length;

        PlotterStatistics stats = PlotterStatistics.builder()
                .totalDrawDistanceMm(round(totalDrawDistance, 1))
                .totalTravelDistanceMm(round(totalTravelDistance, 1))
                .totalDistanceMm(round(totalDrawDistance + totalTravelDistance, 1))
                .totalTimeSeconds(round(totalTimeSeconds, 1))
                .formattedTime(formattedTime)
                .penLiftsCount(penLiftsCount)
                .totalMovesCount(moves.size())
                .gcodeLinesCount(gcodeLinesCount)
                .strokeCount(strokes.size())
                .build();

        return new GcodeGenerationResult(gcode.toString(), moves, stats);
    }

    private void appendSafetyHeader(StringBuilder gcode, PlotterJobRequest req, int strokeCount, String formattedTime) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        gcode.append("; HEADER_BLOCK_START\n");
        gcode.append("; printer_model = Bambu Lab A1\n");
        gcode.append("; generated by BambuStudio 01.09.05.51\n");
        gcode.append("; date: ").append(timestamp).append("\n");
        gcode.append("; total_layers = 1\n");
        gcode.append("; total_filament_used = 0.00g\n");
        gcode.append("; estimated_time = ").append(formattedTime).append("\n");
        gcode.append("; max_z_height = 25.00\n");
        gcode.append("; HEADER_BLOCK_END\n\n");

        gcode.append("; ==========================================================================\n");
        gcode.append("; CatPlotLib - Bambu Lab A1 Safe Plotter G-code\n");
        gcode.append("; Generated on: ").append(timestamp).append("\n");
        gcode.append("; Target Machine: Bambu Lab A1 (256x256 mm Bed)\n");
        gcode.append("; Total Strokes: ").append(strokeCount).append("\n");
        gcode.append("; Safety Flags: Zero Heat | No Wipe | No Purge | No Bed Leveling Collision\n");
        gcode.append("; ==========================================================================\n\n");

        gcode.append("; --- SAFETY ENFORCEMENT: ALL HEATERS & FANS OFF ---\n");
        gcode.append("M104 S0 ; Disable Hotend Heater\n");
        gcode.append("M140 S0 ; Disable Bed Heater\n");
        gcode.append("M106 S0 ; Disable Part Cooling Fan\n");
        gcode.append("M106 P1 S0 ; Disable Aux Fan\n");
        gcode.append("M106 P2 S0 ; Disable Chamber Fan\n\n");

        gcode.append("; --- MOTION INITIALIZATION ---\n");
        gcode.append("G90 ; Set positioning to Absolute Coordinates\n");
        gcode.append("G21 ; Set units to Millimeters\n");
        gcode.append("M220 S100 ; Reset feedrate multiplier to 100%\n");
        gcode.append("M221 S100 ; Reset flowrate multiplier to 100%\n\n");

        gcode.append("; --- SAFE HOMING ROUTINE (ZERO COLLISION) ---\n");
        gcode.append("G28 ; Safe home all axes\n");
        gcode.append("G0 Z10.0 F1200 ; Immediately raise Z to safe 10mm clearance\n\n");

        gcode.append("; --- BEGIN PLOT PATHS ---\n");
        gcode.append("; LAYER_CHANGE\n");
        gcode.append("; Z: 0.00\n");
        gcode.append("; HEIGHT: 0.20\n");
        gcode.append("; BEFORE_LAYER_CHANGE\n\n");
    }

    private void appendSafetyEndRoutine(StringBuilder gcode) {
        gcode.append("\n; --- END OF PLOT ROUTINE ---\n");
        gcode.append("G0 Z25.0 F1500 ; Raise pen high (25mm clearance)\n");
        gcode.append("G0 X10.0 Y240.0 F3000 ; Present bed forward for easy paper removal\n");
        gcode.append("M400 ; Wait for all moves to complete\n");
        gcode.append("M84 ; Disable all stepper motors\n");
        gcode.append("; CatPlotLib job completed safely.\n\n");

        gcode.append("; CONFIG_BLOCK_START\n");
        gcode.append("; printer_model = Bambu Lab A1\n");
        gcode.append("; nozzle_diameter = 0.4\n");
        gcode.append("; bed_type = Textured PEI Plate\n");
        gcode.append("; CONFIG_BLOCK_END\n");
    }

    private String formatDuration(double seconds) {
        long sec = Math.round(seconds);
        long hours = sec / 3600;
        long minutes = (sec % 3600) / 60;
        long remainingSec = sec % 60;

        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, remainingSec);
        } else if (minutes > 0) {
            return String.format("%dm %02ds", minutes, remainingSec);
        } else {
            return String.format("%ds", remainingSec);
        }
    }

    private double round(double val, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(val * factor) / factor;
    }
}
