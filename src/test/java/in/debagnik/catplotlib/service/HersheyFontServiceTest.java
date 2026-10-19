package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.Point2D;
import in.debagnik.catplotlib.model.VectorStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@DisplayName("HersheyFontService Unit Tests")
class HersheyFontServiceTest {

    private HersheyFontService fontService;

    @BeforeEach
    void setUp() {
        fontService = new HersheyFontService();
    }

    @Test
    @DisplayName("Empty or null input returns empty stroke list")
    void testNullAndEmptyInput() {
        assertThat(fontService.renderText(null, 10.0, 20.0, 1.0, false, 0.0)).isEmpty();
        assertThat(fontService.renderText("", 10.0, 20.0, 1.0, false, 0.0)).isEmpty();
        assertThat(fontService.measureTextWidth(null, 1.0)).isEqualTo(0.0);
        assertThat(fontService.measureTextWidth("", 1.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Standard ASCII rendering generates non-empty strokes with tag TEXT")
    void testAsciiRendering() {
        List<VectorStroke> strokes = fontService.renderText("Hello, World! 123", 10.0, 20.0, 1.0, false, 0.0);

        assertThat(strokes).isNotEmpty();
        assertThat(strokes).allMatch(s -> "TEXT".equals(s.getTag()));
        assertThat(strokes).allMatch(s -> s.getPoints().size() >= 2);
    }

    @Test
    @DisplayName("Greek scientific characters render successfully")
    void testGreekAlphabet() {
        String greek = "α β γ δ ε θ λ μ π σ ω Ω Δ";
        List<VectorStroke> strokes = fontService.renderText(greek, 10.0, 20.0, 1.0, false, 0.0);

        assertThat(strokes).isNotEmpty();
        double width = fontService.measureTextWidth(greek, 1.0);
        assertThat(width).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Math and scientific symbols render successfully")
    void testMathSymbols() {
        String math = "± × ÷ ≠ ≤ ≥ ≈ ∞ ∫ √ ∑ ∈";
        List<VectorStroke> strokes = fontService.renderText(math, 10.0, 20.0, 1.0, false, 0.0);

        assertThat(strokes).isNotEmpty();
        double width = fontService.measureTextWidth(math, 1.0);
        assertThat(width).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Unicode combining diacritics produce DIACRITIC tagged strokes")
    void testDiacriticsRendering() {
        // "café" with acute accent
        List<VectorStroke> strokes = fontService.renderText("café", 10.0, 20.0, 1.0, false, 0.0);

        assertThat(strokes).isNotEmpty();
        boolean hasDiacritic = strokes.stream().anyMatch(s -> "DIACRITIC".equals(s.getTag()));
        assertThat(hasDiacritic).isTrue();
    }

    @Test
    @DisplayName("Spaces and tabs advance width correctly without producing blank strokes")
    void testWhitespaceAndTabs() {
        double widthWord = fontService.measureTextWidth("A", 1.0);
        double widthWordWithSpace = fontService.measureTextWidth("A ", 1.0);
        double widthWordWithTab = fontService.measureTextWidth("A\t", 1.0);

        assertThat(widthWordWithSpace).isGreaterThan(widthWord);
        assertThat(widthWordWithTab).isGreaterThan(widthWordWithSpace);

        // Rendering spaces should not produce empty or phantom strokes
        List<VectorStroke> spaceStrokes = fontService.renderText("   \t  ", 10.0, 20.0, 1.0, false, 0.0);
        assertThat(spaceStrokes).isEmpty();
    }

    @Test
    @DisplayName("Jitter enabled applies slight coordinate variations compared to no-jitter")
    void testJitterEffect() {
        String sample = "Calculus Assignment";
        List<VectorStroke> noJitter = fontService.renderText(sample, 10.0, 20.0, 1.0, false, 0.0);
        List<VectorStroke> withJitter = fontService.renderText(sample, 10.0, 20.0, 1.0, true, 0.5);

        assertThat(noJitter).isNotEmpty();
        assertThat(withJitter).isNotEmpty();
        assertThat(withJitter.size()).isEqualTo(noJitter.size());

        // At least some points should differ due to human jitter noise
        boolean anyDifference = false;
        for (int i = 0; i < noJitter.size(); i++) {
            List<Point2D> pts1 = noJitter.get(i).getPoints();
            List<Point2D> pts2 = withJitter.get(i).getPoints();
            for (int j = 0; j < pts1.size(); j++) {
                if (Math.abs(pts1.get(j).getX() - pts2.get(j).getX()) > 1e-4
                        || Math.abs(pts1.get(j).getY() - pts2.get(j).getY()) > 1e-4) {
                    anyDifference = true;
                    break;
                }
            }
            if (anyDifference) break;
        }
        assertThat(anyDifference).isTrue();
    }

    @Test
    @DisplayName("Unmapped character renders fallback glyph without throwing exceptions")
    void testFallbackGlyph() {
        // Character not in standard map (e.g. arbitrary emoji or rare ideograph)
        List<VectorStroke> strokes = fontService.renderText("\u2708", 10.0, 20.0, 1.0, false, 0.0);
        assertThat(strokes).isNotEmpty();
    }

    @Test
    @DisplayName("Text width scales linearly with scale factor")
    void testMeasureTextWidthScaling() {
        String text = "PlotterCraft Engine";
        double w1 = fontService.measureTextWidth(text, 1.0);
        double w2 = fontService.measureTextWidth(text, 2.0);

        assertThat(w2).isEqualTo(w1 * 2.0, offset(1e-6));
    }
}
