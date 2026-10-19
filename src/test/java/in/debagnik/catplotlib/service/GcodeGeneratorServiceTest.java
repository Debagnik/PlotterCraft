package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.PlotterJobRequest;
import in.debagnik.catplotlib.model.Point2D;
import in.debagnik.catplotlib.model.ToolpathMove;
import in.debagnik.catplotlib.model.VectorStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@DisplayName("GcodeGeneratorService Unit Tests")
class GcodeGeneratorServiceTest {

    private GcodeGeneratorService gcodeService;

    @BeforeEach
    void setUp() {
        gcodeService = new GcodeGeneratorService();
    }

    @Test
    @DisplayName("Generated G-code contains critical Bambu Lab A1 safety headers")
    void testSafetyHeader() {
        PlotterJobRequest request = PlotterJobRequest.builder().build();
        GcodeGeneratorService.GcodeGenerationResult result = gcodeService.generateGcode(List.of(), request);

        String gcode = result.gcode;
        // Verify Zero-Heat Enforcement
        assertThat(gcode).contains("M104 S0 ; Disable Hotend Heater");
        assertThat(gcode).contains("M140 S0 ; Disable Bed Heater");
        assertThat(gcode).contains("M106 S0 ; Disable Part Cooling Fan");
        assertThat(gcode).contains("M106 P1 S0 ; Disable Aux Fan");
        assertThat(gcode).contains("M106 P2 S0 ; Disable Chamber Fan");

        // Verify Motion Initialization
        assertThat(gcode).contains("G90 ; Set positioning to Absolute Coordinates");
        assertThat(gcode).contains("G21 ; Set units to Millimeters");
        assertThat(gcode).contains("M220 S100");
        assertThat(gcode).contains("M221 S100");

        // Verify Safe Homing
        assertThat(gcode).contains("G28 ; Safe home all axes");
        assertThat(gcode).contains("G0 Z10.0 F1200 ; Immediately raise Z to safe 10mm clearance");
    }

    @Test
    @DisplayName("Generated G-code contains safe completion and bed presentation end routine")
    void testSafetyEndRoutine() {
        PlotterJobRequest request = PlotterJobRequest.builder().build();
        GcodeGeneratorService.GcodeGenerationResult result = gcodeService.generateGcode(List.of(), request);

        String gcode = result.gcode;
        assertThat(gcode).contains("G0 Z25.0 F1500 ; Raise pen high (25mm clearance)");
        assertThat(gcode).contains("G0 X10.0 Y240.0 F3000 ; Present bed forward for easy paper removal");
        assertThat(gcode).contains("M400 ; Wait for all moves to complete");
        assertThat(gcode).contains("M84 ; Disable all stepper motors");
    }

    @Test
    @DisplayName("Generating G-code for strokes correctly generates travel, pen drop, draw, and lift moves")
    void testStrokeMotionSequence() {
        VectorStroke stroke1 = new VectorStroke();
        stroke1.addPoint(10.0, 10.0);
        stroke1.addPoint(20.0, 10.0);

        PlotterJobRequest request = PlotterJobRequest.builder()
                .feedrateDraw(1200.0)
                .feedrateTravel(3000.0)
                .zUp(4.0)
                .zDown(0.2)
                .build();

        GcodeGeneratorService.GcodeGenerationResult result = gcodeService.generateGcode(List.of(stroke1), request);

        String gcode = result.gcode;

        // Verify travel move
        assertThat(gcode).contains("G0 Z4.00 F1200 ; Pen Up");
        assertThat(gcode).contains("G0 X10.000 Y10.000 F3000");

        // Verify pen drop
        assertThat(gcode).contains("G1 Z0.20 F1000 ; Pen Down");

        // Verify drawing move
        assertThat(gcode).contains("G1 X20.000 Y10.000 F1200");

        // Verify pen lift at end
        assertThat(gcode).contains("G0 Z4.00 F1200 ; Pen Lift");

        // Verify toolpath moves
        List<ToolpathMove> moves = result.moves;
        assertThat(moves).isNotEmpty();

        boolean hasTravel = moves.stream().anyMatch(m -> "TRAVEL".equals(m.getType()));
        boolean hasDraw = moves.stream().anyMatch(m -> "DRAW".equals(m.getType()));
        assertThat(hasTravel).isTrue();
        assertThat(hasDraw).isTrue();

        // Check statistics
        assertThat(result.statistics.getTotalDrawDistanceMm()).isEqualTo(10.0, offset(0.1));
        assertThat(result.statistics.getPenLiftsCount()).isEqualTo(1);
        assertThat(result.statistics.getStrokeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Duplicate consecutive points (<0.001 mm) are skipped to avoid redundant G-code commands")
    void testDuplicatePointSuppression() {
        VectorStroke stroke = new VectorStroke();
        stroke.addPoint(10.0, 10.0);
        stroke.addPoint(10.0001, 10.0001); // duplicate/microscopic move
        stroke.addPoint(30.0, 10.0);

        PlotterJobRequest request = PlotterJobRequest.builder().build();
        GcodeGeneratorService.GcodeGenerationResult result = gcodeService.generateGcode(List.of(stroke), request);

        // Only one drawing move should be generated (from 10,10 to 30,10)
        long drawMovesCount = result.moves.stream().filter(m -> "DRAW".equals(m.getType())).count();
        assertThat(drawMovesCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Empty strokes list handled without error")
    void testEmptyStrokesList() {
        PlotterJobRequest request = PlotterJobRequest.builder().build();
        GcodeGeneratorService.GcodeGenerationResult result = gcodeService.generateGcode(new ArrayList<>(), request);

        assertThat(result.gcode).isNotEmpty();
        assertThat(result.moves).isEmpty();
        assertThat(result.statistics.getStrokeCount()).isEqualTo(0);
        assertThat(result.statistics.getTotalDrawDistanceMm()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Handles strokes with fewer than 2 points gracefully")
    void testStrokesWithFewerThanTwoPoints() {
        VectorStroke singlePt = new VectorStroke();
        singlePt.addPoint(10.0, 10.0);

        VectorStroke nullPts = new VectorStroke();
        nullPts.setPoints(null);

        PlotterJobRequest request = PlotterJobRequest.builder().build();
        GcodeGeneratorService.GcodeGenerationResult result = gcodeService.generateGcode(
                List.of(singlePt, nullPts), request
        );

        assertThat(result.moves).isEmpty();
        assertThat(result.statistics.getTotalDrawDistanceMm()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Duration formatting handles hours, minutes, and seconds")
    void testDurationFormattingLongJob() {
        // Construct a long zigzag path that takes > 1 hour
        VectorStroke longStroke = new VectorStroke();
        longStroke.addPoint(0.0, 0.0);
        for (int i = 1; i <= 200; i++) {
            longStroke.addPoint(i * 100.0, (i % 2) * 100.0);
        }

        // Extremely low feedrate to force multi-hour duration
        PlotterJobRequest request = PlotterJobRequest.builder()
                .feedrateDraw(10.0) // 10 mm/min
                .feedrateTravel(10.0)
                .build();

        GcodeGeneratorService.GcodeGenerationResult result = gcodeService.generateGcode(
                List.of(longStroke), request
        );

        assertThat(result.statistics.getFormattedTime()).contains("h");
    }
}
