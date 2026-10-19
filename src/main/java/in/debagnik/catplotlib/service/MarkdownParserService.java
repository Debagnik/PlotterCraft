package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.BoundingBox;
import in.debagnik.catplotlib.model.PlotterJobRequest;
import in.debagnik.catplotlib.model.Point2D;
import in.debagnik.catplotlib.model.VectorStroke;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Parses CommonMark Markdown documents into a continuous sequence of
 * single-stroke vector lines, handling headings, word wrapping, lists,
 * blockquotes, code blocks, and embedded stylus SVG diagrams.
 */
@Slf4j
@Service
public class MarkdownParserService {

    private final HersheyFontService fontService;
    private final SvgPathParserService svgParserService;
    private final MathExpressionParserService mathParserService;

    @Autowired
    public MarkdownParserService(HersheyFontService fontService,
                                 SvgPathParserService svgParserService,
                                 MathExpressionParserService mathParserService) {
        this.fontService = fontService;
        this.svgParserService = svgParserService;
        this.mathParserService = mathParserService;
    }

    public MarkdownParserService(HersheyFontService fontService, SvgPathParserService svgParserService) {
        this(fontService, svgParserService, new MathExpressionParserService());
    }

    public static class LayoutResult {
        public final List<VectorStroke> strokes;
        public final BoundingBox bounds;
        public final List<String> warnings;

        public LayoutResult(List<VectorStroke> strokes, BoundingBox bounds, List<String> warnings) {
            this.strokes = strokes;
            this.bounds = bounds;
            this.warnings = warnings;
        }
    }

    public LayoutResult layoutDocument(PlotterJobRequest request) {
        List<VectorStroke> allStrokes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BoundingBox bounds = BoundingBox.empty();

        if (request.getMarkdown() == null || request.getMarkdown().trim().isEmpty()) {
            return new LayoutResult(allStrokes, bounds, warnings);
        }

        // Initialize CommonMark parser with extensions
        Parser parser = Parser.builder()
                .extensions(List.of(
                        TablesExtension.create(),
                        AutolinkExtension.create(),
                        StrikethroughExtension.create()
                ))
                .build();

        Node document = parser.parse(request.getMarkdown());

        // Standard A4 width = 210 mm, Bambu Lab A1 Bed = 256 x 256 mm
        // Usable page length = 256 mm (the rest of the 297 mm A4 page remains blank)
        double bedWidth = 256.0;
        double bedLength = 256.0; // Usable length on A1 bed
        double a4Width = 210.0;   // Standard A4 width
        double sheetOffsetX = (bedWidth - a4Width) / 2.0; // 23.0 mm left & right blank border

        double marginLeft = request.getMarginLeft() > 0 ? request.getMarginLeft() : 15.0;
        double marginRight = request.getMarginRight() > 0 ? request.getMarginRight() : 15.0;
        double marginTop = request.getMarginTop() > 0 ? request.getMarginTop() : 15.0;
        double marginBottom = request.getMarginBottom() > 0 ? request.getMarginBottom() : 15.0;

        double minX = sheetOffsetX + marginLeft;
        double maxX = sheetOffsetX + a4Width - marginRight;
        double printableWidth = maxX - minX;
        double maxY = bedLength - marginBottom;

        double baseScale = request.getFontScale() > 0 ? request.getFontScale() : 1.0;
        double baseLineHeight = 5.2 * baseScale * request.getLineSpacing();

        double cursorX = minX;
        double cursorY = marginTop + (baseLineHeight * 0.85);

        // Document layout context
        LayoutContext ctx = new LayoutContext(
                cursorX, cursorY,
                minX,
                maxX,
                maxY,
                printableWidth,
                baseScale,
                baseLineHeight,
                request.isJitterEnabled(),
                request.getJitterIntensity(),
                request.getSvgFiles() != null ? request.getSvgFiles() : Collections.emptyMap()
        );

        Node child = document.getFirstChild();
        while (child != null) {
            renderNode(child, ctx, allStrokes);
            child = child.getNext();
        }

        // Update bounds
        for (VectorStroke s : allStrokes) {
            for (Point2D pt : s.getPoints()) {
                bounds.expandToInclude(pt.getX(), pt.getY());
            }
        }

        if (ctx.cursorY > ctx.maxY) {
            warnings.add(String.format("Content vertically exceeded printable page height (reached Y=%.1f mm, max is %.1f mm).",
                    ctx.cursorY, ctx.maxY));
        }

        return new LayoutResult(allStrokes, bounds, warnings);
    }

    private static class LayoutContext {
        double cursorX;
        double cursorY;
        final double minX;
        final double maxX;
        final double maxY;
        final double printableWidth;
        final double baseScale;
        final double baseLineHeight;
        final boolean jitterEnabled;
        final double jitterIntensity;
        final Map<String, String> svgFiles;

        LayoutContext(double cursorX, double cursorY, double minX, double maxX, double maxY,
                      double printableWidth, double baseScale, double baseLineHeight,
                      boolean jitterEnabled, double jitterIntensity, Map<String, String> svgFiles) {
            this.cursorX = cursorX;
            this.cursorY = cursorY;
            this.minX = minX;
            this.maxX = maxX;
            this.maxY = maxY;
            this.printableWidth = printableWidth;
            this.baseScale = baseScale;
            this.baseLineHeight = baseLineHeight;
            this.jitterEnabled = jitterEnabled;
            this.jitterIntensity = jitterIntensity;
            this.svgFiles = svgFiles;
        }
    }

    private void renderNode(Node node, LayoutContext ctx, List<VectorStroke> strokes) {
        if (node instanceof Heading heading) {
            renderHeading(heading, ctx, strokes);
        } else if (node instanceof Paragraph para) {
            renderParagraph(para, ctx, strokes);
        } else if (node instanceof BulletList bulletList) {
            renderBulletList(bulletList, ctx, strokes);
        } else if (node instanceof OrderedList orderedList) {
            renderOrderedList(orderedList, ctx, strokes);
        } else if (node instanceof BlockQuote blockQuote) {
            renderBlockQuote(blockQuote, ctx, strokes);
        } else if (node instanceof FencedCodeBlock codeBlock) {
            renderCodeBlock(codeBlock.getLiteral(), ctx, strokes);
        } else if (node instanceof IndentedCodeBlock codeBlock) {
            renderCodeBlock(codeBlock.getLiteral(), ctx, strokes);
        } else if (node instanceof ThematicBreak) {
            renderThematicBreak(ctx, strokes);
        }
    }

    private void renderHeading(Heading heading, LayoutContext ctx, List<VectorStroke> strokes) {
        double headingScale = switch (heading.getLevel()) {
            case 1 -> ctx.baseScale * 1.85;
            case 2 -> ctx.baseScale * 1.5;
            case 3 -> ctx.baseScale * 1.25;
            default -> ctx.baseScale * 1.1;
        };
        double headingLineHeight = 5.2 * headingScale * 1.3;

        // Top margin before heading
        ctx.cursorY += headingLineHeight * 0.4;

        String text = extractText(heading);
        List<String> lines = wordWrap(text, headingScale, ctx.printableWidth);

        for (String line : lines) {
            List<VectorStroke> lineStrokes = fontService.renderText(
                    line, ctx.minX, ctx.cursorY, headingScale, ctx.jitterEnabled, ctx.jitterIntensity
            );
            strokes.addAll(lineStrokes);

            // Double-pass offset for Heading 1 & 2 for bold physical ink weight
            if (heading.getLevel() <= 2) {
                double boldOffset = 0.15;
                List<VectorStroke> boldPass = fontService.renderText(
                        line, ctx.minX + boldOffset, ctx.cursorY, headingScale, false, 0.0
                );
                strokes.addAll(boldPass);
            }

            ctx.cursorY += headingLineHeight;
        }

        // Bottom underline stroke for Heading 1
        if (heading.getLevel() == 1) {
            double underlineY = ctx.cursorY - (headingLineHeight * 0.4);
            VectorStroke rule = new VectorStroke();
            rule.setTag("BORDER");
            rule.addPoint(ctx.minX, underlineY);
            rule.addPoint(ctx.minX + ctx.printableWidth, underlineY);
            strokes.add(rule);
            ctx.cursorY += ctx.baseLineHeight * 0.5;
        } else {
            ctx.cursorY += ctx.baseLineHeight * 0.3;
        }
    }

    private void renderParagraph(Paragraph para, LayoutContext ctx, List<VectorStroke> strokes) {
        // First check if this paragraph is solely an Image or contains an Image
        Node child = para.getFirstChild();
        boolean hasImage = false;
        while (child != null) {
            if (child instanceof Image img) {
                renderImage(img, ctx, strokes);
                hasImage = true;
            }
            child = child.getNext();
        }

        if (hasImage) {
            return;
        }

        // Standard paragraph text
        String text = extractText(para);
        if (text.trim().isEmpty()) return;

        List<String> lines = wordWrap(text, ctx.baseScale, ctx.printableWidth);
        for (String line : lines) {
            List<VectorStroke> lineStrokes = fontService.renderText(
                    line, ctx.minX, ctx.cursorY, ctx.baseScale, ctx.jitterEnabled, ctx.jitterIntensity
            );
            strokes.addAll(lineStrokes);
            ctx.cursorY += ctx.baseLineHeight;
        }

        // Paragraph spacing
        ctx.cursorY += ctx.baseLineHeight * 0.4;
    }

    private void renderBulletList(BulletList list, LayoutContext ctx, List<VectorStroke> strokes) {
        Node itemNode = list.getFirstChild();
        double indent = 6.0; // 6 mm indent

        while (itemNode != null) {
            if (itemNode instanceof ListItem item) {
                // Render bullet marker (a small single-stroke circle/diamond)
                double bulletX = ctx.minX + 2.0;
                double bulletY = ctx.cursorY - (ctx.baseLineHeight * 0.3);
                VectorStroke bullet = new VectorStroke();
                bullet.setTag("LIST");
                bullet.addPoint(bulletX - 0.8, bulletY);
                bullet.addPoint(bulletX, bulletY - 0.8);
                bullet.addPoint(bulletX + 0.8, bulletY);
                bullet.addPoint(bulletX, bulletY + 0.8);
                bullet.addPoint(bulletX - 0.8, bulletY);
                strokes.add(bullet);

                String text = extractText(item);
                List<String> lines = wordWrap(text, ctx.baseScale, ctx.printableWidth - indent);
                for (String line : lines) {
                    List<VectorStroke> lineStrokes = fontService.renderText(
                            line, ctx.minX + indent, ctx.cursorY, ctx.baseScale, ctx.jitterEnabled, ctx.jitterIntensity
                    );
                    strokes.addAll(lineStrokes);
                    ctx.cursorY += ctx.baseLineHeight;
                }
            }
            itemNode = itemNode.getNext();
        }
        ctx.cursorY += ctx.baseLineHeight * 0.3;
    }

    private void renderOrderedList(OrderedList list, LayoutContext ctx, List<VectorStroke> strokes) {
        Node itemNode = list.getFirstChild();
        int counter = list.getStartNumber();
        double indent = 8.0; // 8 mm indent

        while (itemNode != null) {
            if (itemNode instanceof ListItem item) {
                String prefix = counter + ".";
                counter++;

                // Render number prefix
                List<VectorStroke> prefixStrokes = fontService.renderText(
                        prefix, ctx.minX, ctx.cursorY, ctx.baseScale, false, 0.0
                );
                strokes.addAll(prefixStrokes);

                String text = extractText(item);
                List<String> lines = wordWrap(text, ctx.baseScale, ctx.printableWidth - indent);
                for (String line : lines) {
                    List<VectorStroke> lineStrokes = fontService.renderText(
                            line, ctx.minX + indent, ctx.cursorY, ctx.baseScale, ctx.jitterEnabled, ctx.jitterIntensity
                    );
                    strokes.addAll(lineStrokes);
                    ctx.cursorY += ctx.baseLineHeight;
                }
            }
            itemNode = itemNode.getNext();
        }
        ctx.cursorY += ctx.baseLineHeight * 0.3;
    }

    private void renderBlockQuote(BlockQuote blockQuote, LayoutContext ctx, List<VectorStroke> strokes) {
        double startY = ctx.cursorY - (ctx.baseLineHeight * 0.7);
        double indent = 7.0;

        String text = extractText(blockQuote);
        List<String> lines = wordWrap(text, ctx.baseScale * 0.95, ctx.printableWidth - indent);

        for (String line : lines) {
            List<VectorStroke> lineStrokes = fontService.renderText(
                    line, ctx.minX + indent, ctx.cursorY, ctx.baseScale * 0.95, ctx.jitterEnabled, ctx.jitterIntensity
            );
            strokes.addAll(lineStrokes);
            ctx.cursorY += ctx.baseLineHeight;
        }

        double endY = ctx.cursorY - (ctx.baseLineHeight * 0.3);

        // Render vertical accent bar on left margin
        VectorStroke bar = new VectorStroke();
        bar.setTag("BORDER");
        bar.addPoint(ctx.minX + 2.0, startY);
        bar.addPoint(ctx.minX + 2.0, endY);
        strokes.add(bar);

        ctx.cursorY += ctx.baseLineHeight * 0.4;
    }

    private void renderCodeBlock(String code, LayoutContext ctx, List<VectorStroke> strokes) {
        if (code == null || code.trim().isEmpty()) return;

        double codeScale = ctx.baseScale * 0.85;
        double codeLineHeight = 5.2 * codeScale * 1.25;
        String[] lines = code.split("\r?\n");

        double boxPadding = 3.0;
        double startY = ctx.cursorY - (codeLineHeight * 0.75);
        double textX = ctx.minX + boxPadding + 1.0;

        for (String line : lines) {
            List<VectorStroke> lineStrokes = fontService.renderText(
                    line, textX, ctx.cursorY, codeScale, false, 0.0 // no jitter for code
            );
            strokes.addAll(lineStrokes);
            ctx.cursorY += codeLineHeight;
        }

        double endY = ctx.cursorY + (boxPadding * 0.5);

        // Render clean bounding box around code
        VectorStroke box = new VectorStroke();
        box.setTag("BORDER");
        box.addPoint(ctx.minX, startY);
        box.addPoint(ctx.minX + ctx.printableWidth, startY);
        box.addPoint(ctx.minX + ctx.printableWidth, endY);
        box.addPoint(ctx.minX, endY);
        box.addPoint(ctx.minX, startY);
        strokes.add(box);

        ctx.cursorY += ctx.baseLineHeight * 0.5;
    }

    private void renderThematicBreak(LayoutContext ctx, List<VectorStroke> strokes) {
        ctx.cursorY += ctx.baseLineHeight * 0.3;
        VectorStroke rule = new VectorStroke();
        rule.setTag("BORDER");
        rule.addPoint(ctx.minX, ctx.cursorY);
        rule.addPoint(ctx.minX + ctx.printableWidth, ctx.cursorY);
        strokes.add(rule);
        ctx.cursorY += ctx.baseLineHeight * 0.8;
    }

    private void renderImage(Image img, LayoutContext ctx, List<VectorStroke> strokes) {
        String dest = img.getDestination();
        String alt = extractText(img);
        if (alt == null || alt.isEmpty()) {
            alt = "Diagram";
        }

        ctx.cursorY += ctx.baseLineHeight * 0.3;

        // Check if SVG content is available in map
        String svgContent = null;
        if (dest != null) {
            svgContent = ctx.svgFiles.get(dest);
            if (svgContent == null) {
                // Try basename
                String base = dest.substring(dest.lastIndexOf('/') + 1);
                svgContent = ctx.svgFiles.get(base);
            }
        }

        if (svgContent != null && !svgContent.trim().isEmpty()) {
            // Scale diagram proportionally with baseScale (default baseScale=1.0)
            double targetDiagWidth = Math.min(ctx.printableWidth, ctx.printableWidth * 0.9 * Math.max(0.4, ctx.baseScale));
            double maxDiagHeight = Math.max(30.0, (ctx.maxY - ctx.cursorY) * 0.85);

            SvgPathParserService.SvgRenderResult svgResult = svgParserService.parseAndScaleSvg(
                    svgContent, ctx.minX, ctx.cursorY, targetDiagWidth, maxDiagHeight
            );

            // Center the diagram within the printable column
            double centerOffsetX = (ctx.printableWidth - svgResult.renderedWidth) / 2.0;
            if (centerOffsetX > 0) {
                for (VectorStroke s : svgResult.strokes) {
                    for (Point2D pt : s.getPoints()) {
                        pt.setX(pt.getX() + centerOffsetX);
                    }
                }
            }

            strokes.addAll(svgResult.strokes);
            ctx.cursorY += svgResult.renderedHeight + (ctx.baseLineHeight * 0.6);

            // Render caption if alt is present
            if (alt != null && !alt.isEmpty() && !alt.equalsIgnoreCase(dest)) {
                String caption = "Fig: " + alt;
                double capScale = ctx.baseScale * 0.85;
                double capW = fontService.measureTextWidth(caption, capScale);
                double capX = ctx.minX + (ctx.printableWidth - capW) / 2.0; // Center caption
                strokes.addAll(fontService.renderText(caption, capX, ctx.cursorY, capScale, false, 0.0));
                ctx.cursorY += ctx.baseLineHeight * 0.8;
            }
        } else {
            // Render neat placeholder box
            double boxHeight = 35.0;
            VectorStroke box = new VectorStroke();
            box.setTag("BORDER");
            box.addPoint(ctx.minX, ctx.cursorY);
            box.addPoint(ctx.minX + ctx.printableWidth, ctx.cursorY);
            box.addPoint(ctx.minX + ctx.printableWidth, ctx.cursorY + boxHeight);
            box.addPoint(ctx.minX, ctx.cursorY + boxHeight);
            box.addPoint(ctx.minX, ctx.cursorY);
            strokes.add(box);

            String label = "[SVG Diagram: " + (dest != null ? dest : alt) + "]";
            double labelScale = ctx.baseScale * 0.85;
            double labelW = fontService.measureTextWidth(label, labelScale);
            double labelX = ctx.minX + (ctx.printableWidth - labelW) / 2.0;
            double labelY = ctx.cursorY + (boxHeight / 2.0) + (ctx.baseLineHeight * 0.3);

            strokes.addAll(fontService.renderText(label, labelX, labelY, labelScale, false, 0.0));
            ctx.cursorY += boxHeight + (ctx.baseLineHeight * 0.8);
        }
    }

    private List<String> wordWrap(String text, double scale, double maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;

            // If an individual token exceeds maxWidth (common in CJK non-spaced text or formulas)
            if (fontService.measureTextWidth(word, scale) > maxWidth) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                for (int i = 0; i < word.length(); i++) {
                    char c = word.charAt(i);
                    String testChar = currentLine.toString() + c;
                    if (fontService.measureTextWidth(testChar, scale) > maxWidth && !currentLine.isEmpty()) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder().append(c);
                    } else {
                        currentLine.append(c);
                    }
                }
                continue;
            }

            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            double width = fontService.measureTextWidth(testLine, scale);

            if (width > maxWidth && !currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        appendNodeText(node, sb);
        String raw = sb.toString();
        return mathParserService != null ? mathParserService.renderMathInText(raw) : raw;
    }

    private void appendNodeText(Node node, StringBuilder sb) {
        if (node instanceof Text text) {
            sb.append(text.getLiteral());
        } else if (node instanceof Code code) {
            sb.append(code.getLiteral());
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            sb.append(" ");
        }

        Node child = node.getFirstChild();
        while (child != null) {
            appendNodeText(child, sb);
            child = child.getNext();
        }
    }
}
