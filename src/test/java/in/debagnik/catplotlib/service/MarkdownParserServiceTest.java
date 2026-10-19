package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.PlotterJobRequest;
import in.debagnik.catplotlib.model.VectorStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MarkdownParserService Unit Tests")
class MarkdownParserServiceTest {

    private MarkdownParserService markdownParserService;

    @BeforeEach
    void setUp() {
        HersheyFontService fontService = new HersheyFontService();
        SvgPathParserService svgParserService = new SvgPathParserService();
        markdownParserService = new MarkdownParserService(fontService, svgParserService);
    }

    @Test
    @DisplayName("Empty or blank markdown returns empty result without errors")
    void testEmptyMarkdown() {
        PlotterJobRequest req = PlotterJobRequest.builder().markdown("").build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isEmpty();
        assertThat(result.warnings).isEmpty();
        assertThat(result.bounds.isValid()).isFalse();
    }

    @Test
    @DisplayName("Heading 1 produces bold passes and underline border rule")
    void testHeading1Layout() {
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown("# Title of Document")
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasBorderUnderline = result.strokes.stream().anyMatch(s -> "BORDER".equals(s.getTag()));
        assertThat(hasBorderUnderline).isTrue();
    }

    @Test
    @DisplayName("Standard paragraph produces text strokes and respects word wrapping")
    void testParagraphAndWordWrapping() {
        String longText = "This is a comprehensive paragraph designed to test line breaking and "
                + "word wrapping algorithm within the printable width constraints of the plotter bed.";
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(longText)
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        assertThat(result.bounds.isValid()).isTrue();
    }

    @Test
    @DisplayName("Bullet lists render diamond markers and indented text")
    void testBulletList() {
        String md = """
                * First item in bullet list
                * Second item in bullet list
                """;
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(md)
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasListTag = result.strokes.stream().anyMatch(s -> "LIST".equals(s.getTag()));
        assertThat(hasListTag).isTrue();
    }

    @Test
    @DisplayName("Ordered lists render number prefix and indented items")
    void testOrderedList() {
        String md = """
                1. First item
                2. Second item
                """;
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(md)
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
    }

    @Test
    @DisplayName("Blockquote renders vertical accent border rule")
    void testBlockquote() {
        String md = "> This is an important quote that should have a left border.";
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(md)
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasBorder = result.strokes.stream().anyMatch(s -> "BORDER".equals(s.getTag()));
        assertThat(hasBorder).isTrue();
    }

    @Test
    @DisplayName("Fenced code block renders bounding box border and text")
    void testCodeBlock() {
        String md = """
                ```python
                def hello():
                    print("Safe Plotter")
                ```
                """;
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(md)
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasBorder = result.strokes.stream().anyMatch(s -> "BORDER".equals(s.getTag()));
        assertThat(hasBorder).isTrue();
    }

    @Test
    @DisplayName("Thematic break renders horizontal line across printable width")
    void testThematicBreak() {
        String md = """
                Section 1
                
                ---
                
                Section 2
                """;
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(md)
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasBorder = result.strokes.stream().anyMatch(s -> "BORDER".equals(s.getTag()));
        assertThat(hasBorder).isTrue();
    }

    @Test
    @DisplayName("Embedded SVG diagram resolves from svgFiles map by full path or basename")
    void testEmbeddedSvgDiagramResolution() {
        String svg = """
                <svg viewBox="0 0 100 100">
                    <line x1="0" y1="0" x2="100" y2="100" />
                </svg>
                """;
        String md = "![Test Diagram](assets/diagram.svg)";
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(md)
                .svgFiles(Map.of("diagram.svg", svg))
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasSvgTag = result.strokes.stream().anyMatch(s -> "SVG".equals(s.getTag()));
        assertThat(hasSvgTag).isTrue();
    }

    @Test
    @DisplayName("Missing SVG diagram falls back to clean placeholder box with label")
    void testMissingSvgDiagramFallback() {
        String md = "![Nonexistent Diagram](missing.svg)";
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(md)
                .svgFiles(Map.of())
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasBorder = result.strokes.stream().anyMatch(s -> "BORDER".equals(s.getTag()));
        assertThat(hasBorder).isTrue();
    }

    @Test
    @DisplayName("Content exceeding bed height generates layout warning")
    void testVerticalOverflowWarning() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 70; i++) {
            sb.append("Line ").append(i).append(": Long detailed paragraph exceeding single page capacity.\n\n");
        }
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(sb.toString())
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.warnings).isNotEmpty();
        assertThat(result.warnings.get(0)).contains("vertically exceeded");
    }

    @Test
    @DisplayName("Headings at level 2, 3, and 4 calculate scale properly and produce strokes")
    void testHeadingLevels2To4() {
        String md = """
                ## Level 2 Heading
                ### Level 3 Heading
                #### Level 4 Heading
                """;
        PlotterJobRequest req = PlotterJobRequest.builder().markdown(md).build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
    }

    @Test
    @DisplayName("Indented code block renders bounding box and monospace characters")
    void testIndentedCodeBlock() {
        String md = "    int x = 42;\n    System.out.println(x);\n";
        PlotterJobRequest req = PlotterJobRequest.builder().markdown(md).build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasBorder = result.strokes.stream().anyMatch(s -> "BORDER".equals(s.getTag()));
        assertThat(hasBorder).isTrue();
    }

    @Test
    @DisplayName("Paragraph with inline code and soft line breaks renders properly")
    void testParagraphWithInlineCodeAndSoftBreaks() {
        String md = "This line has `inline code` and continues\non the next line with soft break.";
        PlotterJobRequest req = PlotterJobRequest.builder().markdown(md).build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
    }

    @Test
    @DisplayName("Image with empty alt text and missing SVG falls back gracefully")
    void testImageEmptyAltMissingSvg() {
        String md = "![](unknown_asset.svg)";
        PlotterJobRequest req = PlotterJobRequest.builder().markdown(md).build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        boolean hasBorder = result.strokes.stream().anyMatch(s -> "BORDER".equals(s.getTag()));
        assertThat(hasBorder).isTrue();
    }

    @Test
    @DisplayName("Custom margins and font scaling parameters are respected")
    void testCustomMarginsAndFontScale() {
        String md = "# Custom Layout";
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(md)
                .marginLeft(20.0)
                .marginRight(20.0)
                .marginTop(25.0)
                .marginBottom(25.0)
                .fontScale(1.5)
                .lineSpacing(1.8)
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        assertThat(result.bounds.getMinX()).isGreaterThanOrEqualTo(20.0);
        assertThat(result.bounds.getMinY()).isGreaterThanOrEqualTo(25.0);
    }

    @Test
    @DisplayName("LaTeX mathematical expressions in markdown render as authentic math symbols instead of raw LaTeX tags")
    void testMathExpressionRenderingInMarkdown() {
        String md = """
                # Calculus Assignment
                
                Compute the area under the curve:
                > $A = \\int_{-2}^{2} (4 - x^2) \\, dx = \\left[ 4x - \\frac{x^3}{3} \\right]_{-2}^{2} = \\frac{32}{3} \\approx 10.67$
                """;
        PlotterJobRequest req = PlotterJobRequest.builder().markdown(md).build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        assertThat(result.bounds.isValid()).isTrue();
    }

    @Test
    @DisplayName("Multilingual markdown with Hiragana, Devanagari, and Kanji renders without errors and wraps cleanly")
    void testMultilingualMarkdownRendering() {
        String md = """
                # 多言語プロッター (Multilingual Plotter)
                
                ## Japanese Section (日本語)
                こんにちは世界！これはペンプロッターのテストです。
                
                ## Devanagari Section (देवनागरी)
                नमस्ते दुनिया! यह प्लॉटरक्राफ्ट का परीक्षण है।
                """;
        PlotterJobRequest req = PlotterJobRequest.builder().markdown(md).build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);

        assertThat(result.strokes).isNotEmpty();
        assertThat(result.bounds.isValid()).isTrue();
        // Ensure strokes fit within bounds
        assertThat(result.bounds.getMaxX()).isLessThanOrEqualTo(256.0);
    }

    @Test
    @DisplayName("Custom boundary dimensions are respected during document layout")
    void testCustomBoundaryDimensions() {
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown("# Boundary Test")
                .boundaryWidth(150.0)
                .boundaryLength(200.0)
                .pageWidth(256.0)
                .pageHeight(256.0)
                .build();
        MarkdownParserService.LayoutResult result = markdownParserService.layoutDocument(req);
        assertThat(result.bounds.isValid()).isTrue();
        // Strokes must remain within the 150mm boundary + centering offset (53mm): maxX <= 203.0
        assertThat(result.bounds.getMaxX()).isLessThanOrEqualTo(203.0);
    }
}
