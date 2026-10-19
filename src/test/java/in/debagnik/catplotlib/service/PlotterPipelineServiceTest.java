package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.BoundingBox;
import in.debagnik.catplotlib.model.PlotterJobRequest;
import in.debagnik.catplotlib.model.PlotterJobResponse;
import in.debagnik.catplotlib.model.VectorStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlotterPipelineService Unit Tests")
class PlotterPipelineServiceTest {

    @Mock
    private MarkdownParserService mockMarkdownParserService;

    @Mock
    private GcodeGeneratorService mockGcodeGeneratorService;

    private PlotterPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        pipelineService = new PlotterPipelineService(mockMarkdownParserService, mockGcodeGeneratorService);
    }

    @Test
    @DisplayName("processJob successfully coordinates layout and G-code generation")
    void testProcessJobSuccess() {
        PlotterJobRequest request = PlotterJobRequest.builder()
                .markdown("# Test Title")
                .pageWidth(256.0)
                .pageHeight(256.0)
                .feedrateDraw(1400.0)
                .feedrateTravel(3500.0)
                .fontScale(1.0)
                .lineSpacing(1.4)
                .build();

        VectorStroke stroke = new VectorStroke();
        stroke.addPoint(10.0, 10.0);
        stroke.addPoint(20.0, 10.0);
        BoundingBox bounds = new BoundingBox(10.0, 10.0, 20.0, 10.0);

        MarkdownParserService.LayoutResult layoutResult = new MarkdownParserService.LayoutResult(
                List.of(stroke), bounds, List.of()
        );
        when(mockMarkdownParserService.layoutDocument(any(PlotterJobRequest.class))).thenReturn(layoutResult);

        GcodeGeneratorService.GcodeGenerationResult gcodeResult = new GcodeGeneratorService.GcodeGenerationResult(
                "G0 X10 Y10\n", List.of(), null
        );
        when(mockGcodeGeneratorService.generateGcode(any(), any())).thenReturn(gcodeResult);

        PlotterJobResponse response = pipelineService.processJob(request);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getGcode()).isEqualTo("G0 X10 Y10\n");
        assertThat(response.getBounds()).isEqualTo(bounds);
        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("processJob applies sane defaults when parameters are non-positive")
    void testProcessJobDefaultFallbacks() {
        PlotterJobRequest request = PlotterJobRequest.builder()
                .markdown("# Test")
                .pageWidth(0.0)
                .pageHeight(-1.0)
                .feedrateDraw(0.0)
                .feedrateTravel(-5.0)
                .fontScale(0.0)
                .lineSpacing(0.0)
                .build();

        MarkdownParserService.LayoutResult layoutResult = new MarkdownParserService.LayoutResult(
                List.of(), BoundingBox.empty(), List.of()
        );
        when(mockMarkdownParserService.layoutDocument(any())).thenReturn(layoutResult);

        GcodeGeneratorService.GcodeGenerationResult gcodeResult = new GcodeGeneratorService.GcodeGenerationResult(
                "; empty\n", List.of(), null
        );
        when(mockGcodeGeneratorService.generateGcode(any(), any())).thenReturn(gcodeResult);

        PlotterJobResponse response = pipelineService.processJob(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(request.getPageWidth()).isEqualTo(256.0);
        assertThat(request.getPageHeight()).isEqualTo(256.0);
        assertThat(request.getFeedrateDraw()).isEqualTo(1400.0);
        assertThat(request.getFeedrateTravel()).isEqualTo(3500.0);
        assertThat(request.getFontScale()).isEqualTo(1.0);
        assertThat(request.getLineSpacing()).isEqualTo(1.4);
    }

    @Test
    @DisplayName("processJob catches unhandled exceptions and returns success=false response")
    void testProcessJobExceptionHandling() {
        PlotterJobRequest request = PlotterJobRequest.builder().markdown("# Error").build();
        when(mockMarkdownParserService.layoutDocument(any()))
                .thenThrow(new RuntimeException("Simulated layout error"));

        PlotterJobResponse response = pipelineService.processJob(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Failed to generate G-code: Simulated layout error");
    }

    @Test
    @DisplayName("getSampleAssignments returns physics and calculus demo assignments with SVGs")
    void testGetSampleAssignments() {
        Map<String, Object> samples = pipelineService.getSampleAssignments();

        assertThat(samples).containsKey("physics");
        assertThat(samples).containsKey("calculus");

        @SuppressWarnings("unchecked")
        Map<String, Object> physics = (Map<String, Object>) samples.get("physics");
        assertThat(physics.get("title")).isNotNull();
        assertThat(physics.get("markdown")).asString().contains("RLC");
        assertThat(physics.get("svgFiles")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> calculus = (Map<String, Object>) samples.get("calculus");
        assertThat(calculus.get("title")).isNotNull();
        assertThat(calculus.get("markdown")).asString().contains("Definite Integrals");
        assertThat(calculus.get("svgFiles")).isNotNull();
    }
}
