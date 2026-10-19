package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.*;
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
        StringBuilder gcode = new StringBuilder();
        List<ToolpathMove> moves = new ArrayList<>();

        double zUp = request.getZUp();
        double zDown = request.getZDown();
        double feedrateTravel = request.getFeedrateTravel();
        double feedrateDraw = request.getFeedrateDraw();
        double zLiftFeedrate = 1200.0;
        double zDropFeedrate = 1000.0;

        // Statistics accumulators
        double totalDrawDistance = 0.0;
        double totalTravelDistance = 0.0;
        double totalTimeSeconds = 0.0;
        int penLiftsCount = 0;
        int moveIndex = 0;

        // Current toolhead position tracker
        double curX = 0.0;
        double curY = 0.0;
        double curZ = 10.0; // Starts homed and lifted to 10mm

        // 1. Safety Header & Zero-Heat Configuration
        appendSafetyHeader(gcode, request, strokes.size());

        // 2. Iterate through each vector stroke
        for (int strokeIdx = 0; strokeIdx < strokes.size(); strokeIdx++) {
            VectorStroke stroke = strokes.get(strokeIdx);
            List<Point2D> pts = stroke.getPoints();
            if (pts == null || pts.size() < 2) continue;

            Point2D startPt = pts.get(0);

            // A. Aerial Travel Move to Stroke Start Point (G0 with pen up)
            if (curX != startPt.getX() || curY != startPt.getY() || curZ != zUp) {
                // Ensure pen is up
                if (curZ != zUp) {
                    gcode.append(String.format(Locale.US, "G0 Z%.2f F%.0f ; Pen Up\n", zUp, zLiftFeedrate));
                    double zDist = Math.abs(zUp - curZ);
                    totalTravelDistance += zDist;
                    totalTimeSeconds += (zDist / zLiftFeedrate) * 60.0;
                    curZ = zUp;
                }

                // Rapid travel in XY
                double travelDist = Math.hypot(startPt.getX() - curX, startPt.getY() - curY);
                double travelDuration = (travelDist / feedrateTravel) * 60.0;
                totalTravelDistance += travelDist;
                totalTimeSeconds += travelDuration;

                ToolpathPoint fromPt = new ToolpathPoint(curX, curY, zUp, "TRAVEL", false, strokeIdx);
                ToolpathPoint toPt = new ToolpathPoint(startPt.getX(), startPt.getY(), zUp, "TRAVEL", false, strokeIdx);

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

                gcode.append(String.format(Locale.US, "G0 X%.3f Y%.3f F%.0f\n", startPt.getX(), startPt.getY(), feedrateTravel));
                curX = startPt.getX();
                curY = startPt.getY();
            }

            // B. Lower Pen (Pen Down)
            gcode.append(String.format(Locale.US, "G1 Z%.2f F%.0f ; Pen Down\n", zDown, zDropFeedrate));
            double zDropDist = Math.abs(curZ - zDown);
            totalTravelDistance += zDropDist;
            totalTimeSeconds += (zDropDist / zDropFeedrate) * 60.0;
            curZ = zDown;

            // C. Drawing Moves (G1 along stroke points)
            for (int i = 1; i < pts.size(); i++) {
                Point2D nextPt = pts.get(i);
                double drawDist = Math.hypot(nextPt.getX() - curX, nextPt.getY() - curY);
                if (drawDist < 0.001) continue; // Skip identical duplicate points

                double drawDuration = (drawDist / feedrateDraw) * 60.0;
                totalDrawDistance += drawDist;
                totalTimeSeconds += drawDuration;

                ToolpathPoint fromPt = new ToolpathPoint(curX, curY, zDown, "DRAW", true, strokeIdx);
                ToolpathPoint toPt = new ToolpathPoint(nextPt.getX(), nextPt.getY(), zDown, "DRAW", true, strokeIdx);

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

                gcode.append(String.format(Locale.US, "G1 X%.3f Y%.3f F%.0f\n", nextPt.getX(), nextPt.getY(), feedrateDraw));
                curX = nextPt.getX();
                curY = nextPt.getY();
            }

            // D. Lift Pen at end of stroke
            gcode.append(String.format(Locale.US, "G0 Z%.2f F%.0f ; Pen Lift\n", zUp, zLiftFeedrate));
            double zLiftDist = Math.abs(zUp - curZ);
            totalTravelDistance += zLiftDist;
            totalTimeSeconds += (zLiftDist / zLiftFeedrate) * 60.0;
            curZ = zUp;
            penLiftsCount++;
        }

        // 3. Safe End Routine
        appendSafetyEndRoutine(gcode);

        // Calculate line count and formatted time
        int gcodeLinesCount = gcode.toString().split("\n").length;
        String formattedTime = formatDuration(totalTimeSeconds);

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

    private void appendSafetyHeader(StringBuilder gcode, PlotterJobRequest req, int strokeCount) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
    }

    private void appendSafetyEndRoutine(StringBuilder gcode) {
        gcode.append("\n; --- END OF PLOT ROUTINE ---\n");
        gcode.append("G0 Z25.0 F1500 ; Raise pen high (25mm clearance)\n");
        gcode.append("G0 X10.0 Y240.0 F3000 ; Present bed forward for easy paper removal\n");
        gcode.append("M400 ; Wait for all moves to complete\n");
        gcode.append("M84 ; Disable all stepper motors\n");
        gcode.append("; CatPlotLib job completed safely.\n");
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
