package in.debagnik.catplotlib.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlotterJobRequest Unit Tests")
class PlotterJobRequestTest {

    @Test
    @DisplayName("Default constructor applies standard Bambu Lab A1 plotter defaults")
    void testStandardDefaults() {
        PlotterJobRequest req = new PlotterJobRequest();

        assertThat(req.getFeedrateDraw()).isEqualTo(1400.0);
        assertThat(req.getFeedrateTravel()).isEqualTo(3500.0);
        assertThat(req.getZUp()).isEqualTo(3.0);
        assertThat(req.getZDown()).isEqualTo(0.0);
        assertThat(req.isJitterEnabled()).isTrue();
        assertThat(req.getJitterIntensity()).isEqualTo(0.15);
        assertThat(req.getPageWidth()).isEqualTo(256.0);
        assertThat(req.getPageHeight()).isEqualTo(256.0);
        assertThat(req.getMarginTop()).isEqualTo(15.0);
        assertThat(req.getMarginBottom()).isEqualTo(15.0);
        assertThat(req.getMarginLeft()).isEqualTo(15.0);
        assertThat(req.getMarginRight()).isEqualTo(15.0);
        assertThat(req.getFontScale()).isEqualTo(1.0);
        assertThat(req.getLineSpacing()).isEqualTo(1.4);
    }

    @Test
    @DisplayName("Null fields gracefully return fallback defaults in custom getters")
    void testNullFieldFallbacks() {
        PlotterJobRequest req = PlotterJobRequest.builder()
                .feedrateDraw(null)
                .feedrateTravel(null)
                .zUp(null)
                .zDown(null)
                .jitterEnabled(null)
                .jitterIntensity(null)
                .pageWidth(null)
                .pageHeight(null)
                .marginTop(null)
                .marginBottom(null)
                .marginLeft(null)
                .marginRight(null)
                .fontScale(null)
                .lineSpacing(null)
                .build();

        assertThat(req.getFeedrateDraw()).isEqualTo(1400.0);
        assertThat(req.getFeedrateTravel()).isEqualTo(3500.0);
        assertThat(req.getZUp()).isEqualTo(3.0);
        assertThat(req.getZDown()).isEqualTo(0.0);
        assertThat(req.isJitterEnabled()).isTrue();
        assertThat(req.getJitterIntensity()).isEqualTo(0.15);
        assertThat(req.getPageWidth()).isEqualTo(256.0);
        assertThat(req.getPageHeight()).isEqualTo(256.0);
        assertThat(req.getMarginTop()).isEqualTo(15.0);
        assertThat(req.getMarginBottom()).isEqualTo(15.0);
        assertThat(req.getMarginLeft()).isEqualTo(15.0);
        assertThat(req.getMarginRight()).isEqualTo(15.0);
        assertThat(req.getFontScale()).isEqualTo(1.0);
        assertThat(req.getLineSpacing()).isEqualTo(1.4);
    }

    @Test
    @DisplayName("Explicit values are properly preserved")
    void testExplicitValues() {
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown("# Hello")
                .feedrateDraw(800.0)
                .feedrateTravel(2000.0)
                .zUp(5.0)
                .zDown(-0.5)
                .jitterEnabled(false)
                .jitterIntensity(0.0)
                .pageWidth(210.0)
                .pageHeight(297.0)
                .fontScale(1.2)
                .lineSpacing(1.6)
                .build();

        assertThat(req.getMarkdown()).isEqualTo("# Hello");
        assertThat(req.getFeedrateDraw()).isEqualTo(800.0);
        assertThat(req.getFeedrateTravel()).isEqualTo(2000.0);
        assertThat(req.getZUp()).isEqualTo(5.0);
        assertThat(req.getZDown()).isEqualTo(-0.5);
        assertThat(req.isJitterEnabled()).isFalse();
        assertThat(req.getJitterIntensity()).isEqualTo(0.0);
        assertThat(req.getPageWidth()).isEqualTo(210.0);
        assertThat(req.getPageHeight()).isEqualTo(297.0);
        assertThat(req.getFontScale()).isEqualTo(1.2);
        assertThat(req.getLineSpacing()).isEqualTo(1.6);
    }
}
