package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.Point2D;
import in.debagnik.catplotlib.model.VectorStroke;
import org.springframework.stereotype.Service;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, full UTF-8 Single-Stroke Vector Font Engine with Dynamic AWT Fallback.
 * Provides authentic single-line centerline vector fonts for pen plotting.
 * Supports:
 *  - Standard ASCII & Latin typography with NFD diacritics
 *  - Greek scientific alphabet (lowercase & uppercase)
 *  - Mathematical operators, calculus symbols, superscripts & subscripts
 *  - Native single-stroke Japanese Hiragana & Katakana penmanship
 *  - Native single-stroke Devanagari (Hindi, Sanskrit, Marathi) with continuous Shirorekha headline
 *  - Universal AWT Outline Vector Fallback for any unmapped Unicode characters (Kanji, CJK, etc.)
 */
@Service
public class HersheyFontService {

    // Internal glyph representation: left limit, right limit, and list of sub-strokes
    public static class Glyph {
        public final int minX;
        public final int maxX;
        public final List<List<int[]>> strokes;

        public Glyph(int minX, int maxX, List<List<int[]>> strokes) {
            this.minX = minX;
            this.maxX = maxX;
            this.strokes = strokes;
        }

        public int getWidth() {
            return maxX - minX;
        }
    }

    private final Map<Character, Glyph> glyphMap = new ConcurrentHashMap<>();
    private final Map<Character, List<List<int[]>>> diacriticMap = new HashMap<>();
    private final Random random = new Random(42);

    private static final String[] CANDIDATE_FONT_FAMILIES = {
            "Nirmala UI",       // Windows Devanagari & Indian scripts
            "MS Gothic",        // Windows Japanese Kana & Kanji
            "Yu Gothic",        // Windows Japanese
            "Meiryo",           // Windows Japanese
            "Microsoft YaHei",  // Windows Simplified Chinese
            "SimSun",           // Windows Chinese
            "Malgun Gothic",    // Windows Korean
            "Segoe UI",         // Windows Latin, Greek, Cyrillic, Arabic, Hebrew
            "Arial Unicode MS", // Universal Unicode
            Font.DIALOG,        // Java logical font
            Font.SANS_SERIF     // Java logical font
    };

    public HersheyFontService() {
        initializeAsciiGlyphs();
        initializeGreekGlyphs();
        initializeMathAndSymbols();
        initializeSubscriptsAndSuperscripts();
        initializeDiacritics();
        initializeHiraganaGlyphs();
        initializeKatakanaGlyphs();
        initializeDevanagariGlyphs();
    }

    /**
     * Renders a single line of UTF-8 text into a list of single-stroke vector paths.
     *
     * @param text String to render
     * @param startX Starting X coordinate in mm
     * @param startY Starting Y coordinate (baseline) in mm
     * @param scale Overall font scaling factor (1.0 = ~4.5 mm capital height)
     * @param jitterEnabled Whether to apply human-like handwriting variations
     * @param jitterIntensity Magnitude of noise in mm (e.g. 0.15 mm)
     * @return List of continuous VectorStrokes (pen-down moves)
     */
    public List<VectorStroke> renderText(String text, double startX, double startY, double scale,
                                         boolean jitterEnabled, double jitterIntensity) {
        List<VectorStroke> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }

        // Decompose unicode to base characters + combining diacritics
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);

        double curX = startX;
        double curY = startY;
        double baseScale = scale * 0.35; // Coordinate units to mm conversion

        // Baseline noise phase for continuous smooth human jitter
        double noisePhase = (startX * 0.05 + startY * 0.03);

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);

            // Check if this is a combining diacritical mark
            if (isCombiningDiacritic(ch)) {
                List<List<int[]>> diacStrokes = diacriticMap.get(ch);
                if (diacStrokes != null) {
                    double charWidth = 16.0 * baseScale;
                    double centerX = curX - (charWidth / 2.0);
                    renderStrokes(diacStrokes, centerX, curY, baseScale, jitterEnabled, jitterIntensity, noisePhase, result);
                }
                continue;
            }

            if (ch == ' ') {
                curX += 16.0 * baseScale;
                continue;
            }
            if (ch == '\u3000') { // CJK full-width space
                curX += 32.0 * baseScale;
                continue;
            }
            if (ch == '\t') {
                curX += 48.0 * baseScale;
                continue;
            }
            if (ch == '\r' || ch == '\n') {
                continue;
            }

            Glyph glyph = glyphMap.get(ch);
            if (glyph == null) {
                glyph = getOrGenerateFallbackGlyph(ch);
            }

            // Apply slight random character slant / spacing jitter
            double slant = 0.0;
            double charJitterX = 0.0;
            double charJitterY = 0.0;
            if (jitterEnabled) {
                noisePhase += 0.8;
                double n1 = Math.sin(noisePhase * 1.7) * Math.cos(noisePhase * 0.9);
                double n2 = Math.cos(noisePhase * 1.3) * Math.sin(noisePhase * 2.1);
                charJitterX = n1 * jitterIntensity * 0.4;
                charJitterY = n2 * jitterIntensity;
                slant = n1 * 0.04;
            }

            double glyphX = curX - glyph.minX * baseScale + charJitterX;
            double glyphY = curY + charJitterY;

            for (List<int[]> stroke : glyph.strokes) {
                if (stroke.isEmpty()) continue;
                VectorStroke vs = new VectorStroke();
                vs.setTag("TEXT");

                for (int[] pt : stroke) {
                    double px = pt[0] * baseScale;
                    double py = -pt[1] * baseScale; // Invert Y: Hershey Y is positive up, printhead Y is positive down

                    // Slant transform
                    px += py * slant;

                    double finalX = glyphX + px;
                    double finalY = glyphY + py;

                    // Micro-jitter along stroke points
                    if (jitterEnabled && stroke.size() > 1) {
                        double microNoise = Math.sin((finalX + finalY) * 5.0) * (jitterIntensity * 0.2);
                        finalY += microNoise;
                    }

                    vs.addPoint(finalX, finalY);
                }

                if (!vs.isEmpty()) {
                    result.add(vs);
                }
            }

            double glyphWidth = glyph.getWidth() * baseScale;
            curX += glyphWidth + (4.0 * baseScale); // character spacing
        }

        return result;
    }

    private void renderStrokes(List<List<int[]>> strokes, double x, double y, double scale,
                               boolean jitterEnabled, double jitterIntensity, double noisePhase,
                               List<VectorStroke> result) {
        for (List<int[]> stroke : strokes) {
            VectorStroke vs = new VectorStroke();
            vs.setTag("DIACRITIC");
            for (int[] pt : stroke) {
                double px = pt[0] * scale;
                double py = -pt[1] * scale;
                vs.addPoint(x + px, y + py);
            }
            if (!vs.isEmpty()) {
                result.add(vs);
            }
        }
    }

    public double measureTextWidth(String text, double scale) {
        if (text == null || text.isEmpty()) return 0.0;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        double baseScale = scale * 0.35;
        double width = 0.0;

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (isCombiningDiacritic(ch)) continue;
            if (ch == ' ') {
                width += 16.0 * baseScale;
                continue;
            }
            if (ch == '\u3000') {
                width += 32.0 * baseScale;
                continue;
            }
            if (ch == '\t') {
                width += 48.0 * baseScale;
                continue;
            }
            if (ch == '\r' || ch == '\n') {
                continue;
            }
            Glyph glyph = glyphMap.get(ch);
            if (glyph == null) {
                glyph = getOrGenerateFallbackGlyph(ch);
            }
            width += (glyph.getWidth() * baseScale) + (4.0 * baseScale);
        }
        return width;
    }

    private boolean isCombiningDiacritic(char ch) {
        return (ch >= '\u0300' && ch <= '\u036F');
    }

    private Glyph getOrGenerateFallbackGlyph(char ch) {
        Glyph cached = glyphMap.get(ch);
        if (cached != null) {
            return cached;
        }
        Glyph awtGlyph = generateAwtGlyph(ch);
        if (awtGlyph != null) {
            glyphMap.put(ch, awtGlyph);
            return awtGlyph;
        }
        return getFallbackBoxGlyph(ch);
    }

    private Glyph generateAwtGlyph(char ch) {
        try {
            Font font = findFontForChar(ch);
            if (font == null) {
                return null;
            }

            Font sizedFont = font.deriveFont(16.0f);
            FontRenderContext frc = new FontRenderContext(null, true, true);
            GlyphVector gv = sizedFont.createGlyphVector(frc, String.valueOf(ch));
            Shape shape = gv.getOutline(0, 0);

            PathIterator pi = shape.getPathIterator(null, 0.4);
            List<List<int[]>> strokes = new ArrayList<>();
            List<int[]> currentStroke = new ArrayList<>();
            float[] coords = new float[6];

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int startX = 0;
            int startY = 0;

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                int x = Math.round(coords[0]);
                int y = Math.round(-coords[1]); // Invert Y: AWT is Y-down, Hershey is Y-up

                if (type == PathIterator.SEG_MOVETO) {
                    if (currentStroke.size() > 1) {
                        strokes.add(currentStroke);
                    }
                    currentStroke = new ArrayList<>();
                    currentStroke.add(new int[]{x, y});
                    startX = x;
                    startY = y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                } else if (type == PathIterator.SEG_LINETO) {
                    currentStroke.add(new int[]{x, y});
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                } else if (type == PathIterator.SEG_CLOSE) {
                    if (!currentStroke.isEmpty()) {
                        currentStroke.add(new int[]{startX, startY});
                        strokes.add(currentStroke);
                        currentStroke = new ArrayList<>();
                    }
                }
                pi.next();
            }

            if (currentStroke.size() > 1) {
                strokes.add(currentStroke);
            }

            if (strokes.isEmpty()) {
                return null;
            }

            if (minX > maxX) {
                minX = -5;
                maxX = 5;
            }

            return new Glyph(minX, maxX, strokes);
        } catch (Throwable t) {
            return null;
        }
    }

    private Font findFontForChar(char ch) {
        for (String name : CANDIDATE_FONT_FAMILIES) {
            try {
                Font f = new Font(name, Font.PLAIN, 16);
                if (f.canDisplay(ch)) {
                    return f;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Font dialog = new Font(Font.DIALOG, Font.PLAIN, 16);
            if (dialog.canDisplay(ch)) {
                return dialog;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Glyph getFallbackBoxGlyph(char ch) {
        List<List<int[]>> strokes = new ArrayList<>();
        strokes.add(makeStroke(-5, 0, -5, 16, 5, 16, 5, 0, -5, 0)); // box
        strokes.add(makeStroke(-3, 2, 3, 14)); // diagonal 1
        strokes.add(makeStroke(-3, 14, 3, 2)); // diagonal 2
        return new Glyph(-6, 6, strokes);
    }

    private static List<int[]> makeStroke(int... coords) {
        List<int[]> pts = new ArrayList<>();
        for (int i = 0; i < coords.length; i += 2) {
            pts.add(new int[]{coords[i], coords[i + 1]});
        }
        return pts;
    }

    @SafeVarargs
    private void addGlyph(char ch, int minX, int maxX, List<int[]>... strokes) {
        List<List<int[]>> strokeList = new ArrayList<>(Arrays.asList(strokes));
        glyphMap.put(ch, new Glyph(minX, maxX, strokeList));
    }

    // =========================================================================
    // GLYPH DEFINITIONS (Single-Stroke Centerlines)
    // Baseline is Y=0. Standard Cap Height is Y=14 to 16. Descenders go to Y=-4.
    // =========================================================================

    private void initializeAsciiGlyphs() {
        // Uppercase Latin
        addGlyph('A', -8, 8, makeStroke(-7, 0, 0, 16, 7, 0), makeStroke(-4, 6, 4, 6));
        addGlyph('B', -8, 8, makeStroke(-6, 0, -6, 16, 2, 16, 5, 13, 2, 8, -6, 8), makeStroke(2, 8, 6, 5, 3, 0, -6, 0));
        addGlyph('C', -8, 8, makeStroke(7, 13, 3, 16, -3, 16, -7, 12, -7, 4, -3, 0, 3, 0, 7, 3));
        addGlyph('D', -8, 8, makeStroke(-6, 0, -6, 16, 1, 16, 6, 11, 6, 5, 1, 0, -6, 0));
        addGlyph('E', -7, 7, makeStroke(6, 16, -6, 16, -6, 0, 6, 0), makeStroke(-6, 8, 3, 8));
        addGlyph('F', -7, 7, makeStroke(-6, 0, -6, 16, 6, 16), makeStroke(-6, 8, 2, 8));
        addGlyph('G', -8, 8, makeStroke(7, 13, 3, 16, -3, 16, -7, 12, -7, 4, -3, 0, 4, 0, 7, 3, 7, 8, 1, 8));
        addGlyph('H', -8, 8, makeStroke(-6, 0, -6, 16), makeStroke(6, 0, 6, 16), makeStroke(-6, 8, 6, 8));
        addGlyph('I', -4, 4, makeStroke(0, 0, 0, 16), makeStroke(-3, 16, 3, 16), makeStroke(-3, 0, 3, 0));
        addGlyph('J', -6, 6, makeStroke(-5, 4, -2, 0, 3, 0, 3, 16), makeStroke(0, 16, 6, 16));
        addGlyph('K', -8, 8, makeStroke(-6, 0, -6, 16), makeStroke(6, 16, -5, 6, 6, 0));
        addGlyph('L', -7, 7, makeStroke(-6, 16, -6, 0, 6, 0));
        addGlyph('M', -9, 9, makeStroke(-8, 0, -8, 16, 0, 2, 8, 16, 8, 0));
        addGlyph('N', -8, 8, makeStroke(-7, 0, -7, 16, 7, 0, 7, 16));
        addGlyph('O', -8, 8, makeStroke(-3, 0, 3, 0, 7, 4, 7, 12, 3, 16, -3, 16, -7, 12, -7, 4, -3, 0));
        addGlyph('P', -8, 8, makeStroke(-6, 0, -6, 16, 3, 16, 6, 13, 3, 8, -6, 8));
        addGlyph('Q', -8, 8, makeStroke(-3, 0, 3, 0, 7, 4, 7, 12, 3, 16, -3, 16, -7, 12, -7, 4, -3, 0), makeStroke(2, 3, 8, -3));
        addGlyph('R', -8, 8, makeStroke(-6, 0, -6, 16, 3, 16, 6, 13, 3, 8, -6, 8), makeStroke(1, 8, 6, 0));
        addGlyph('S', -7, 7, makeStroke(6, 13, 3, 16, -3, 16, -6, 13, -3, 9, 3, 7, 6, 3, 3, 0, -3, 0, -6, 3));
        addGlyph('T', -7, 7, makeStroke(0, 0, 0, 16), makeStroke(-6, 16, 6, 16));
        addGlyph('U', -8, 8, makeStroke(-6, 16, -6, 4, -2, 0, 2, 0, 6, 4, 6, 16));
        addGlyph('V', -8, 8, makeStroke(-7, 16, 0, 0, 7, 16));
        addGlyph('W', -10, 10, makeStroke(-9, 16, -5, 0, 0, 12, 5, 0, 9, 16));
        addGlyph('X', -8, 8, makeStroke(-6, 0, 6, 16), makeStroke(-6, 16, 6, 0));
        addGlyph('Y', -8, 8, makeStroke(0, 0, 0, 7), makeStroke(-6, 16, 0, 7, 6, 16));
        addGlyph('Z', -7, 7, makeStroke(-6, 16, 6, 16, -6, 0, 6, 0));

        // Lowercase Latin (x-height is Y=10)
        addGlyph('a', -6, 6, makeStroke(5, 7, 2, 10, -2, 10, -5, 7, -5, 3, -2, 0, 2, 0, 5, 3, 5, 0), makeStroke(5, 10, 5, 0));
        addGlyph('b', -6, 6, makeStroke(-5, 16, -5, 0), makeStroke(-5, 7, -2, 10, 2, 10, 5, 7, 5, 3, 2, 0, -2, 0, -5, 3));
        addGlyph('c', -6, 6, makeStroke(5, 8, 2, 10, -2, 10, -5, 7, -5, 3, -2, 0, 2, 0, 5, 2));
        addGlyph('d', -6, 6, makeStroke(5, 16, 5, 0), makeStroke(5, 7, 2, 10, -2, 10, -5, 7, -5, 3, -2, 0, 2, 0, 5, 3));
        addGlyph('e', -6, 6, makeStroke(-5, 5, 5, 5, 5, 7, 2, 10, -2, 10, -5, 7, -5, 3, -2, 0, 3, 0, 5, 2));
        addGlyph('f', -5, 5, makeStroke(4, 16, 1, 16, -2, 14, -2, 0), makeStroke(-5, 10, 2, 10));
        addGlyph('g', -6, 6, makeStroke(5, 10, 5, -2, 2, -5, -3, -5, -5, -3), makeStroke(5, 7, 2, 10, -2, 10, -5, 7, -5, 3, -2, 0, 2, 0, 5, 3));
        addGlyph('h', -6, 6, makeStroke(-5, 16, -5, 0), makeStroke(-5, 6, -2, 10, 2, 10, 5, 7, 5, 0));
        addGlyph('i', -3, 3, makeStroke(0, 0, 0, 10), makeStroke(-1, 14, 1, 14));
        addGlyph('j', -4, 4, makeStroke(1, 10, 1, -3, -2, -5, -4, -4), makeStroke(0, 14, 2, 14));
        addGlyph('k', -6, 6, makeStroke(-5, 16, -5, 0), makeStroke(5, 10, -4, 3, 5, 0));
        addGlyph('l', -3, 3, makeStroke(0, 16, 0, 0));
        addGlyph('m', -9, 9, makeStroke(-7, 0, -7, 10), makeStroke(-7, 7, -4, 10, -1, 10, 0, 7, 0, 0), makeStroke(0, 7, 3, 10, 6, 10, 7, 7, 7, 0));
        addGlyph('n', -6, 6, makeStroke(-5, 0, -5, 10), makeStroke(-5, 7, -2, 10, 2, 10, 5, 7, 5, 0));
        addGlyph('o', -6, 6, makeStroke(-2, 0, 2, 0, 5, 3, 5, 7, 2, 10, -2, 10, -5, 7, -5, 3, -2, 0));
        addGlyph('p', -6, 6, makeStroke(-5, 10, -5, -5), makeStroke(-5, 7, -2, 10, 2, 10, 5, 7, 5, 3, 2, 0, -2, 0, -5, 3));
        addGlyph('q', -6, 6, makeStroke(5, 10, 5, -5), makeStroke(5, 7, 2, 10, -2, 10, -5, 7, -5, 3, -2, 0, 2, 0, 5, 3));
        addGlyph('r', -5, 5, makeStroke(-4, 0, -4, 10), makeStroke(-4, 6, -1, 10, 3, 10, 4, 8));
        addGlyph('s', -5, 5, makeStroke(4, 8, 2, 10, -2, 10, -4, 8, -2, 6, 2, 5, 4, 3, 2, 0, -2, 0, -4, 2));
        addGlyph('t', -4, 4, makeStroke(-1, 14, -1, 2, 1, 0, 3, 0), makeStroke(-4, 10, 2, 10));
        addGlyph('u', -6, 6, makeStroke(-5, 10, -5, 3, -2, 0, 2, 0, 5, 3, 5, 10), makeStroke(5, 3, 5, 0));
        addGlyph('v', -6, 6, makeStroke(-5, 10, 0, 0, 5, 10));
        addGlyph('w', -8, 8, makeStroke(-7, 10, -4, 0, 0, 7, 4, 0, 7, 10));
        addGlyph('x', -6, 6, makeStroke(-5, 0, 5, 10), makeStroke(-5, 10, 5, 0));
        addGlyph('y', -6, 6, makeStroke(-5, 10, 0, 0, 5, 10), makeStroke(0, 0, -2, -4, -5, -5));
        addGlyph('z', -5, 5, makeStroke(-5, 10, 5, 10, -5, 0, 5, 0));

        // Digits
        addGlyph('0', -6, 6, makeStroke(-2, 0, 2, 0, 5, 4, 5, 12, 2, 16, -2, 16, -5, 12, -5, 4, -2, 0), makeStroke(-4, 3, 4, 13));
        addGlyph('1', -5, 5, makeStroke(-3, 12, 0, 16, 0, 0), makeStroke(-4, 0, 4, 0));
        addGlyph('2', -6, 6, makeStroke(-5, 12, -2, 16, 2, 16, 5, 13, 5, 9, -5, 0, 5, 0));
        addGlyph('3', -6, 6, makeStroke(-4, 16, 5, 16, 0, 9, 3, 9, 5, 6, 4, 2, 0, 0, -4, 0));
        addGlyph('4', -6, 6, makeStroke(2, 0, 2, 16, -5, 5, 5, 5));
        addGlyph('5', -6, 6, makeStroke(4, 16, -4, 16, -4, 9, 1, 9, 5, 7, 5, 3, 2, 0, -3, 0, -5, 2));
        addGlyph('6', -6, 6, makeStroke(3, 15, -1, 16, -4, 13, -5, 8, -5, 3, -2, 0, 2, 0, 5, 3, 5, 7, 2, 9, -5, 8));
        addGlyph('7', -6, 6, makeStroke(-5, 16, 5, 16, -2, 0));
        addGlyph('8', -6, 6, makeStroke(0, 8, -3, 9, -5, 12, -3, 16, 0, 16, 3, 16, 5, 12, 3, 9, 0, 8, -4, 6, -5, 3, -3, 0, 0, 0, 3, 0, 5, 3, 4, 6, 0, 8));
        addGlyph('9', -6, 6, makeStroke(5, 8, -2, 7, -5, 9, -5, 13, -2, 16, 2, 16, 5, 13, 5, 8, 4, 3, 1, 0, -3, 1));

        // Standard Punctuation & Symbols
        addGlyph('.', -3, 3, makeStroke(-1, 0, 1, 0, 1, 2, -1, 2, -1, 0));
        addGlyph(',', -3, 3, makeStroke(0, 2, 1, 2, 0, -2, -2, -3));
        addGlyph(':', -3, 3, makeStroke(0, 2, 0, 3), makeStroke(0, 8, 0, 9));
        addGlyph(';', -3, 3, makeStroke(0, 8, 0, 9), makeStroke(0, 2, 1, 2, 0, -2, -2, -3));
        addGlyph('!', -3, 3, makeStroke(0, 16, 0, 5), makeStroke(-1, 0, 1, 0, 1, 2, -1, 2, -1, 0));
        addGlyph('?', -5, 5, makeStroke(-4, 12, -2, 16, 2, 16, 4, 13, 3, 9, 0, 7, 0, 4), makeStroke(-1, 0, 1, 0, 1, 2, -1, 2, -1, 0));
        addGlyph('-', -5, 5, makeStroke(-4, 7, 4, 7));
        addGlyph('_', -6, 6, makeStroke(-6, -2, 6, -2));
        addGlyph('+', -6, 6, makeStroke(0, 2, 0, 14), makeStroke(-5, 8, 5, 8));
        addGlyph('=', -6, 6, makeStroke(-5, 5, 5, 5), makeStroke(-5, 10, 5, 10));
        addGlyph('/', -5, 5, makeStroke(-4, 0, 4, 16));
        addGlyph('\\', -5, 5, makeStroke(-4, 16, 4, 0));
        addGlyph('(', -4, 4, makeStroke(2, 16, -1, 13, -3, 8, -1, 3, 2, 0));
        addGlyph(')', -4, 4, makeStroke(-2, 16, 1, 13, 3, 8, 1, 3, -2, 0));
        addGlyph('[', -4, 4, makeStroke(2, 16, -2, 16, -2, 0, 2, 0));
        addGlyph(']', -4, 4, makeStroke(-2, 16, 2, 16, 2, 0, -2, 0));
        addGlyph('{', -5, 5, makeStroke(2, 16, 0, 15, 0, 10, -3, 8, 0, 6, 0, 1, 2, 0));
        addGlyph('}', -5, 5, makeStroke(-2, 16, 0, 15, 0, 10, 3, 8, 0, 6, 0, 1, -2, 0));
        addGlyph('<', -5, 5, makeStroke(4, 14, -4, 8, 4, 2));
        addGlyph('>', -5, 5, makeStroke(-4, 14, 4, 8, -4, 2));
        addGlyph('*', -5, 5, makeStroke(0, 5, 0, 13), makeStroke(-4, 6, 4, 12), makeStroke(-4, 12, 4, 6));
        addGlyph('&', -7, 7, makeStroke(6, 0, -3, 9, -3, 13, 0, 16, 3, 13, -4, 4, -4, 2, -1, 0, 3, 0, 6, 4));
        addGlyph('%', -7, 7, makeStroke(-5, 0, 5, 16), makeStroke(-3, 15, -4, 13, -2, 11, -1, 13, -3, 15), makeStroke(3, 5, 2, 3, 4, 1, 5, 3, 3, 5));
        addGlyph('^', -5, 5, makeStroke(-4, 10, 0, 15, 4, 10));
        addGlyph('~', -6, 6, makeStroke(-5, 7, -3, 10, 0, 8, 3, 6, 5, 8));
        addGlyph('"', -4, 4, makeStroke(-2, 16, -2, 10), makeStroke(2, 16, 2, 10));
        addGlyph('\'', -2, 2, makeStroke(0, 16, 0, 10));
        addGlyph('`', -3, 3, makeStroke(-2, 16, 1, 12));
        addGlyph('|', -2, 2, makeStroke(0, -3, 0, 17));
        addGlyph('#', -6, 6, makeStroke(-2, 0, -1, 16), makeStroke(2, 0, 3, 16), makeStroke(-5, 5, 5, 5), makeStroke(-5, 11, 5, 11));
        addGlyph('@', -8, 8, makeStroke(4, 3, 1, 0, -3, 0, -6, 4, -6, 11, -3, 15, 1, 15, 4, 12, 4, 5, 1, 4, -1, 6, -1, 8, 1, 9, 3, 8));
    }

    private void initializeGreekGlyphs() {
        // Greek lowercase
        addGlyph('α', -6, 6, makeStroke(5, 10, 2, 2, -2, 0, -5, 3, -5, 7, -2, 10, 2, 8, 5, 0));
        addGlyph('β', -6, 6, makeStroke(-5, -4, -5, 16, 0, 16, 3, 13, 0, 9, -5, 9), makeStroke(0, 9, 4, 6, 2, 0, -5, 0));
        addGlyph('γ', -6, 6, makeStroke(-5, 10, -2, 0, 3, 0, 5, 10), makeStroke(0, 0, -3, -5));
        addGlyph('δ', -6, 6, makeStroke(2, 16, -2, 16, 0, 13, -4, 9, -5, 5, -3, 0, 2, 0, 5, 4, 4, 9, -1, 11));
        addGlyph('ε', -5, 5, makeStroke(4, 9, 1, 10, -3, 8, -1, 5, 3, 5), makeStroke(-1, 5, -3, 2, 0, 0, 4, 1));
        addGlyph('θ', -6, 6, makeStroke(-2, 0, 2, 0, 5, 4, 5, 12, 2, 16, -2, 16, -5, 12, -5, 4, -2, 0), makeStroke(-4, 8, 4, 8));
        addGlyph('λ', -6, 6, makeStroke(-5, 0, 4, 16), makeStroke(0, 9, -5, 16));
        addGlyph('μ', -6, 6, makeStroke(-5, -4, -5, 10), makeStroke(-5, 3, -2, 0, 2, 0, 5, 3, 5, 10));
        addGlyph('π', -6, 6, makeStroke(-6, 10, 6, 10), makeStroke(-3, 10, -3, 0), makeStroke(3, 10, 3, 0, 5, 0));
        addGlyph('σ', -6, 6, makeStroke(5, 10, -2, 10, -5, 7, -5, 3, -2, 0, 2, 0, 5, 3, 5, 7, 2, 10));
        addGlyph('ω', -7, 7, makeStroke(-6, 10, -6, 4, -3, 0, 0, 4, 0, 7, 0, 4, 3, 0, 6, 4, 6, 10));
        addGlyph('φ', -6, 6, makeStroke(0, -4, 0, 16), makeStroke(-2, 2, 2, 2, 5, 5, 5, 7, 2, 10, -2, 10, -5, 7, -5, 5, -2, 2));

        // Greek uppercase
        addGlyph('Δ', -8, 8, makeStroke(-7, 0, 0, 16, 7, 0, -7, 0));
        addGlyph('Ω', -8, 8, makeStroke(-7, 0, -3, 0, -5, 4, -6, 9, -3, 14, 0, 15, 3, 14, 6, 9, 5, 4, 3, 0, 7, 0));
        addGlyph('Σ', -7, 7, makeStroke(6, 16, -5, 16, 0, 8, -5, 0, 6, 0));
        addGlyph('Π', -8, 8, makeStroke(-7, 16, 7, 16), makeStroke(-5, 16, -5, 0), makeStroke(5, 16, 5, 0));
        addGlyph('Φ', -8, 8, makeStroke(0, -2, 0, 18), makeStroke(-3, 3, 3, 3, 6, 6, 6, 10, 3, 13, -3, 13, -6, 10, -6, 6, -3, 3));
    }

    private void initializeMathAndSymbols() {
        // Mathematical Operators
        addGlyph('±', -6, 6, makeStroke(0, 4, 0, 16), makeStroke(-5, 10, 5, 10), makeStroke(-5, 0, 5, 0));
        addGlyph('×', -5, 5, makeStroke(-4, 3, 4, 11), makeStroke(-4, 11, 4, 3));
        addGlyph('÷', -6, 6, makeStroke(-5, 8, 5, 8), makeStroke(0, 12, 0, 13), makeStroke(0, 3, 0, 4));
        addGlyph('≠', -6, 6, makeStroke(-5, 5, 5, 5), makeStroke(-5, 10, 5, 10), makeStroke(-3, 2, 3, 13));
        addGlyph('≤', -6, 6, makeStroke(4, 15, -4, 10, 4, 5), makeStroke(-4, 1, 4, 1));
        addGlyph('≥', -6, 6, makeStroke(-4, 15, 4, 10, -4, 5), makeStroke(-4, 1, 4, 1));
        addGlyph('≈', -6, 6, makeStroke(-5, 5, -3, 7, 0, 5, 3, 7, 5, 5), makeStroke(-5, 9, -3, 11, 0, 9, 3, 11, 5, 9));
        addGlyph('∞', -8, 8, makeStroke(-3, 5, -6, 5, -6, 10, -3, 10, 3, 5, 6, 5, 6, 10, 3, 10, -3, 5));
        addGlyph('∫', -5, 5, makeStroke(4, 16, 2, 17, 0, 15, 0, 0, -2, -2, -4, -1));
        addGlyph('√', -7, 7, makeStroke(-6, 7, -4, 4, -1, 0, 3, 16, 7, 16));
        addGlyph('∂', -6, 6, makeStroke(4, 14, 0, 16, -3, 14, 0, 10, 3, 7, 4, 4, 2, 0, -2, 0, -5, 3, -5, 7, -2, 10, 3, 10));
        addGlyph('°', -4, 4, makeStroke(-2, 12, 0, 14, 2, 12, 0, 10, -2, 12));
        addGlyph('µ', -6, 6, makeStroke(-5, -4, -5, 10), makeStroke(-5, 3, -2, 0, 2, 0, 5, 3, 5, 10));
        addGlyph('∈', -6, 6, makeStroke(5, 14, 0, 14, -4, 11, -4, 5, 0, 2, 5, 2), makeStroke(-3, 8, 4, 8));
        addGlyph('∉', -6, 6, makeStroke(5, 14, 0, 14, -4, 11, -4, 5, 0, 2, 5, 2), makeStroke(-3, 8, 4, 8), makeStroke(-4, 0, 5, 16));
        addGlyph('·', -2, 2, makeStroke(-1, 8, 1, 8, 1, 9, -1, 9, -1, 8));
        addGlyph('∇', -8, 8, makeStroke(-6, 16, 6, 16, 0, 0, -6, 16));
        addGlyph('∬', -8, 8, makeStroke(1, 16, -1, 17, -3, 15, -3, 0, -5, -2, -7, -1), makeStroke(7, 16, 5, 17, 3, 15, 3, 0, 1, -2, -1, -1));
        addGlyph('∮', -6, 6, makeStroke(4, 16, 2, 17, 0, 15, 0, 0, -2, -2, -4, -1), makeStroke(-3, 6, 0, 4, 3, 6, 3, 10, 0, 12, -3, 10, -3, 6));

        // Arrows & Typographical
        addGlyph('→', -8, 8, makeStroke(-7, 8, 7, 8), makeStroke(3, 12, 7, 8, 3, 4));
        addGlyph('←', -8, 8, makeStroke(7, 8, -7, 8), makeStroke(-3, 12, -7, 8, -3, 4));
        addGlyph('↑', -5, 5, makeStroke(0, 0, 0, 16), makeStroke(-4, 12, 0, 16, 4, 12));
        addGlyph('↓', -5, 5, makeStroke(0, 16, 0, 0), makeStroke(-4, 4, 0, 0, 4, 4));
        addGlyph('•', -3, 3, makeStroke(-1, 7, 1, 7, 1, 9, -1, 9, -1, 7));
        addGlyph('—', -8, 8, makeStroke(-8, 8, 8, 8));
        addGlyph('–', -6, 6, makeStroke(-6, 8, 6, 8));

        // Currencies
        addGlyph('$', -6, 6, makeStroke(0, -2, 0, 18), makeStroke(4, 13, 1, 16, -3, 16, -5, 13, -2, 9, 2, 8, 5, 4, 3, 0, -2, 0, -5, 3));
        addGlyph('€', -7, 7, makeStroke(6, 13, 2, 16, -3, 16, -6, 12, -6, 4, -3, 0, 2, 0, 6, 3), makeStroke(-7, 9, 2, 9), makeStroke(-7, 6, 1, 6));
        addGlyph('£', -7, 7, makeStroke(5, 14, 2, 16, -2, 16, -5, 13, -3, 9, -5, 2, -5, 0, 5, 0), makeStroke(-6, 7, 1, 7));
        addGlyph('¥', -7, 7, makeStroke(-6, 16, 0, 9, 6, 16), makeStroke(0, 9, 0, 0), makeStroke(-4, 7, 4, 7), makeStroke(-4, 4, 4, 4));
        addGlyph('₹', -7, 7, makeStroke(-5, 16, 5, 16), makeStroke(-5, 12, 5, 12), makeStroke(-4, 12, 1, 12, 4, 9, 2, 6, -4, 6), makeStroke(-1, 6, 5, 0));
    }

    private void initializeSubscriptsAndSuperscripts() {
        // Mathematical Superscripts
        addGlyph('⁰', -4, 4, makeStroke(-1, 9, 1, 9, 3, 11, 3, 14, 1, 16, -1, 16, -3, 14, -3, 11, -1, 9));
        addGlyph('¹', -3, 3, makeStroke(-2, 13, 0, 16, 0, 9), makeStroke(-2, 9, 2, 9));
        addGlyph('²', -4, 4, makeStroke(-3, 14, -1, 16, 1, 16, 3, 14, 3, 12, -3, 9, 3, 9));
        addGlyph('³', -4, 4, makeStroke(-3, 16, 3, 16, 0, 13, 2, 13, 3, 11, 2, 9, -2, 9));
        addGlyph('⁴', -4, 4, makeStroke(1, 9, 1, 16, -3, 11, 3, 11));
        addGlyph('⁵', -4, 4, makeStroke(2, 16, -2, 16, -2, 13, 1, 13, 3, 12, 3, 10, 1, 9, -2, 9));
        addGlyph('⁶', -4, 4, makeStroke(2, 15, -1, 16, -3, 14, -3, 11, -1, 9, 1, 9, 3, 11, 3, 12, 1, 13, -3, 11));
        addGlyph('⁷', -4, 4, makeStroke(-3, 16, 3, 16, -1, 9));
        addGlyph('⁸', -4, 4, makeStroke(0, 12, -2, 13, -3, 14, -2, 16, 0, 16, 2, 16, 3, 14, 2, 13, 0, 12, -2, 11, -3, 10, -2, 9, 0, 9, 2, 9, 3, 10, 2, 11, 0, 12));
        addGlyph('⁹', -4, 4, makeStroke(3, 13, -1, 12, -3, 13, -3, 15, -1, 16, 1, 16, 3, 14, 3, 11, 1, 9, -1, 9));
        addGlyph('⁺', -3, 3, makeStroke(0, 10, 0, 15), makeStroke(-2, 12, 2, 12));
        addGlyph('⁻', -3, 3, makeStroke(-2, 12, 2, 12));
        addGlyph('⁼', -3, 3, makeStroke(-2, 11, 2, 11), makeStroke(-2, 14, 2, 14));
        addGlyph('⁽', -2, 2, makeStroke(1, 16, -1, 12, 1, 9));
        addGlyph('⁾', -2, 2, makeStroke(-1, 16, 1, 12, -1, 9));
        addGlyph('ⁿ', -4, 4, makeStroke(-2, 9, -2, 14, 0, 16, 2, 14, 2, 9));
        addGlyph('ⁱ', -2, 2, makeStroke(0, 9, 0, 14), makeStroke(0, 15, 0, 16));
        addGlyph('ˣ', -3, 3, makeStroke(-2, 9, 2, 15), makeStroke(-2, 15, 2, 9));

        // Mathematical Subscripts
        addGlyph('₀', -4, 4, makeStroke(-1, -4, 1, -4, 3, -2, 3, 2, 1, 4, -1, 4, -3, 2, -3, -2, -1, -4));
        addGlyph('₁', -3, 3, makeStroke(-2, 1, 0, 4, 0, -4), makeStroke(-2, -4, 2, -4));
        addGlyph('₂', -4, 4, makeStroke(-3, 2, -1, 4, 1, 4, 3, 2, 3, 0, -3, -4, 3, -4));
        addGlyph('₃', -4, 4, makeStroke(-3, 4, 3, 4, 0, 1, 2, 1, 3, -1, 2, -4, -2, -4));
        addGlyph('₄', -4, 4, makeStroke(1, -4, 1, 4, -3, -1, 3, -1));
        addGlyph('₅', -4, 4, makeStroke(2, 4, -2, 4, -2, 1, 1, 1, 3, 0, 3, -3, 1, -4, -2, -4));
        addGlyph('₆', -4, 4, makeStroke(2, 3, -1, 4, -3, 2, -3, -2, -1, -4, 1, -4, 3, -2, 3, -1, 1, 0, -3, -2));
        addGlyph('₇', -4, 4, makeStroke(-3, 4, 3, 4, -1, -4));
        addGlyph('₈', -4, 4, makeStroke(0, 0, -2, 1, -3, 2, -2, 4, 0, 4, 2, 4, 3, 2, 2, 1, 0, 0, -2, -1, -3, -2, -2, -4, 0, -4, 2, -4, 3, -2, 2, -1, 0, 0));
        addGlyph('₉', -4, 4, makeStroke(3, 1, -1, 0, -3, 1, -3, 3, -1, 4, 1, 4, 3, 2, 3, -2, 1, -4, -1, -4));
        addGlyph('₊', -3, 3, makeStroke(0, -3, 0, 3), makeStroke(-2, 0, 2, 0));
        addGlyph('₋', -3, 3, makeStroke(-2, 0, 2, 0));
        addGlyph('₌', -3, 3, makeStroke(-2, -1, 2, -1), makeStroke(-2, 2, 2, 2));
        addGlyph('₍', -2, 2, makeStroke(1, 4, -1, 0, 1, -4));
        addGlyph('₎', -2, 2, makeStroke(-1, 4, 1, 0, -1, -4));
        addGlyph('ₐ', -3, 3, makeStroke(2, 0, 1, 3, -1, 3, -2, 0, -2, -2, -1, -4, 1, -4, 2, -2, 2, -4), makeStroke(2, 3, 2, -4));
        addGlyph('ₑ', -3, 3, makeStroke(-2, 0, 2, 0, 2, 1, 1, 3, -1, 3, -2, 1, -2, -2, -1, -4, 2, -4));
        addGlyph('ₒ', -3, 3, makeStroke(-1, -4, 1, -4, 2, -2, 2, 1, 1, 3, -1, 3, -2, 1, -2, -2, -1, -4));
        addGlyph('ₓ', -3, 3, makeStroke(-2, -4, 2, 3), makeStroke(-2, 3, 2, -4));
        addGlyph('ᵢ', -2, 2, makeStroke(0, -4, 0, 2), makeStroke(0, 3, 0, 4));
        addGlyph('ⱼ', -3, 3, makeStroke(1, 2, 1, -5, -1, -6, -2, -5), makeStroke(1, 3, 1, 4));
    }

    private void initializeDiacritics() {
        diacriticMap.put('\u0301', List.of(makeStroke(-1, 12, 3, 16)));
        diacriticMap.put('\u0300', List.of(makeStroke(1, 12, -3, 16)));
        diacriticMap.put('\u0302', List.of(makeStroke(-3, 12, 0, 16, 3, 12)));
        diacriticMap.put('\u0303', List.of(makeStroke(-4, 13, -2, 15, 0, 14, 2, 16, 4, 15)));
        diacriticMap.put('\u0308', List.of(makeStroke(-3, 13, -2, 13), makeStroke(2, 13, 3, 13)));
        diacriticMap.put('\u030A', List.of(makeStroke(-2, 13, 0, 15, 2, 13, 0, 11, -2, 13)));
        diacriticMap.put('\u0327', List.of(makeStroke(0, 0, 1, -2, 0, -4, -2, -5)));
        diacriticMap.put('\u0304', List.of(makeStroke(-4, 14, 4, 14)));
        diacriticMap.put('\u030C', List.of(makeStroke(-3, 15, 0, 12, 3, 15)));
    }

    // =========================================================================
    // JAPANESE HIRAGANA & KATAKANA SINGLE-STROKE CENTERLINES
    // =========================================================================

    private void initializeHiraganaGlyphs() {
        // Vowels
        addGlyph('あ', -6, 6, makeStroke(-5, 12, 3, 12), makeStroke(-1, 15, -1, 3), makeStroke(2, 10, -4, 5, -3, 1, 3, 1, 5, 6, 3, 9, 0, 9, -3, 6));
        addGlyph('い', -5, 5, makeStroke(-4, 13, -5, 4, -3, 2), makeStroke(3, 11, 4, 5));
        addGlyph('う', -5, 5, makeStroke(-1, 15, 2, 13), makeStroke(-3, 10, 3, 10, 4, 6, 2, 2, -3, 1));
        addGlyph('え', -5, 5, makeStroke(-1, 15, 2, 13), makeStroke(-4, 9, 3, 9, -3, 4, 3, 4, 5, 2));
        addGlyph('お', -6, 6, makeStroke(-5, 12, 1, 12), makeStroke(-1, 15, -1, 7, -4, 4, -4, 1, 1, 1, 3, 5, 1, 8), makeStroke(3, 14, 5, 12));

        // K-row
        addGlyph('か', -6, 6, makeStroke(-4, 13, 0, 13, 0, 4, -2, 2), makeStroke(-1, 15, 2, 2), makeStroke(3, 12, 5, 10));
        addGlyph('き', -5, 5, makeStroke(-4, 12, 4, 12), makeStroke(-5, 9, 3, 9), makeStroke(1, 15, -1, 5), makeStroke(-3, 3, 1, 1, 4, 3));
        addGlyph('く', -5, 5, makeStroke(3, 13, -4, 7, 3, 1));
        addGlyph('け', -5, 5, makeStroke(-4, 14, -4, 2, -2, 1), makeStroke(-1, 10, 4, 10), makeStroke(2, 14, 2, 2));
        addGlyph('こ', -5, 5, makeStroke(-4, 11, 4, 11, 3, 9), makeStroke(-3, 2, 3, 2, 4, 4));

        // S-row
        addGlyph('さ', -5, 5, makeStroke(-4, 10, 4, 11), makeStroke(1, 14, -1, 5, 2, 2, 4, 5));
        addGlyph('し', -5, 5, makeStroke(-2, 14, -2, 4, 1, 1, 4, 3, 5, 7));
        addGlyph('す', -5, 5, makeStroke(-5, 11, 5, 11), makeStroke(0, 15, 0, 8, -3, 8, -3, 5, 0, 5, 0, 1, -2, -1));
        addGlyph('せ', -5, 5, makeStroke(-5, 10, 5, 10), makeStroke(2, 14, 2, 4, 0, 3), makeStroke(-3, 13, -3, 3, 4, 3));
        addGlyph('そ', -5, 5, makeStroke(-4, 13, 3, 13, -3, 8, 3, 8, 4, 5, 2, 2, -3, 2));

        // T-row
        addGlyph('た', -5, 5, makeStroke(-4, 11, 1, 11), makeStroke(-1, 14, -3, 3), makeStroke(1, 9, 4, 9), makeStroke(0, 4, 4, 4));
        addGlyph('ち', -5, 5, makeStroke(-4, 11, 3, 11), makeStroke(0, 14, -2, 7, 3, 7, 4, 4, 1, 1, -3, 1));
        addGlyph('つ', -5, 5, makeStroke(-4, 10, 3, 11, 4, 8, 2, 3, -3, 1));
        addGlyph('て', -5, 5, makeStroke(-4, 11, 3, 11, -2, 7, 3, 3, 4, 5));
        addGlyph('と', -5, 5, makeStroke(-2, 14, 0, 8), makeStroke(-2, 8, 3, 8, 4, 4, 1, 1, -3, 1));

        // N-row
        addGlyph('な', -5, 5, makeStroke(-5, 10, -1, 10), makeStroke(-3, 13, -2, 4), makeStroke(1, 12, 3, 10), makeStroke(2, 8, 2, 4, 0, 4, 0, 6, 2, 6, 3, 2));
        addGlyph('に', -5, 5, makeStroke(-4, 14, -4, 2, -2, 1), makeStroke(0, 9, 4, 9), makeStroke(0, 3, 4, 3));
        addGlyph('ぬ', -6, 6, makeStroke(-3, 13, 2, 3), makeStroke(-1, 14, -4, 6, 3, 6, 4, 3, 2, 1, -1, 2, 2, 4, 4, 4, 4, 1));
        addGlyph('ね', -5, 5, makeStroke(-4, 14, -4, 1), makeStroke(-4, 11, 2, 11, -3, 5, 2, 5, 3, 3, 1, 1, -1, 2, 2, 4, 3, 2));
        addGlyph('の', -5, 5, makeStroke(0, 11, -3, 5, 1, 2, 4, 5, 3, 10, -2, 10, -4, 7));

        // H-row
        addGlyph('は', -5, 5, makeStroke(-4, 14, -4, 2, -2, 1), makeStroke(-1, 9, 3, 9), makeStroke(1, 14, 1, 4, -1, 4, -1, 6, 2, 6, 4, 2));
        addGlyph('ひ', -5, 5, makeStroke(-4, 9, -2, 11, -2, 3, 2, 3, 2, 11, 4, 9));
        addGlyph('ふ', -5, 5, makeStroke(0, 14, 1, 12), makeStroke(0, 10, -1, 5, 1, 3), makeStroke(-4, 7, -3, 5), makeStroke(3, 7, 4, 5));
        addGlyph('へ', -5, 5, makeStroke(-5, 4, -2, 11, 5, 3));
        addGlyph('ほ', -5, 5, makeStroke(-5, 14, -5, 2, -3, 1), makeStroke(-2, 11, 4, 11), makeStroke(-2, 8, 3, 8), makeStroke(1, 14, 1, 4, -1, 4, -1, 6, 2, 6, 4, 2));

        // M-row
        addGlyph('ま', -5, 5, makeStroke(-4, 11, 4, 11), makeStroke(-4, 8, 4, 8), makeStroke(0, 14, 0, 4, -2, 4, -2, 6, 1, 6, 3, 2));
        addGlyph('み', -5, 5, makeStroke(-4, 11, 1, 11, 1, 7, -2, 7, -2, 9, 3, 2), makeStroke(2, 9, 4, 3));
        addGlyph('む', -5, 5, makeStroke(-5, 11, 3, 11), makeStroke(-2, 14, -2, 5, -4, 5, -4, 7, 2, 7, 3, 10), makeStroke(3, 12, 5, 10));
        addGlyph('め', -5, 5, makeStroke(-2, 13, 2, 3), makeStroke(-1, 14, -4, 7, 2, 6, 4, 3, 1, 1, -2, 3));
        addGlyph('も', -5, 5, makeStroke(-2, 14, -2, 4, 1, 1, 3, 3), makeStroke(-4, 10, 3, 10), makeStroke(-4, 7, 3, 7));

        // Y-row
        addGlyph('や', -5, 5, makeStroke(-4, 5, -4, 9, 0, 12, 2, 10, 0, 5, 0, 2), makeStroke(-1, 14, 0, 12), makeStroke(3, 13, 2, 2));
        addGlyph('ゆ', -5, 5, makeStroke(-3, 13, -3, 6, 2, 6, 3, 9, 0, 11, -3, 9), makeStroke(1, 14, 1, 1, -1, 1));
        addGlyph('よ', -5, 5, makeStroke(-4, 10, 0, 10), makeStroke(0, 13, 0, 4, -2, 4, -2, 6, 1, 6, 3, 2));

        // R-row
        addGlyph('ら', -5, 5, makeStroke(-1, 14, 1, 12), makeStroke(-3, 9, 2, 9, 3, 5, 1, 2, -2, 2));
        addGlyph('り', -5, 5, makeStroke(-3, 12, -3, 6, -2, 5), makeStroke(2, 14, 2, 4, 0, 1));
        addGlyph('る', -5, 5, makeStroke(-3, 12, 2, 12, -2, 8, 2, 8, 3, 5, 1, 2, -1, 2, 0, 4, 2, 4, 1, 2));
        addGlyph('れ', -5, 5, makeStroke(-4, 14, -4, 2), makeStroke(-4, 11, 1, 11, -3, 5, 2, 5, 4, 8));
        addGlyph('ろ', -5, 5, makeStroke(-3, 12, 2, 12, -2, 8, 2, 8, 3, 5, 1, 2, -2, 2));

        // W-row & N
        addGlyph('わ', -5, 5, makeStroke(-4, 14, -4, 2), makeStroke(-4, 11, 1, 11, -3, 5, 1, 6, 4, 5, 3, 2, -1, 2));
        addGlyph('を', -5, 5, makeStroke(-4, 11, 2, 11), makeStroke(-1, 14, -3, 6, 1, 6), makeStroke(0, 5, 3, 5, 3, 2, -2, 2));
        addGlyph('ん', -5, 5, makeStroke(-3, 12, -3, 5, 0, 8, 3, 2, 4, 2));

        // Small kana
        addGlyph('っ', -4, 4, makeStroke(-3, 7, 2, 8, 3, 5, 1, 2, -2, 1));
        addGlyph('ゃ', -4, 4, makeStroke(-3, 4, -3, 7, 0, 9, 1, 7, 0, 4), makeStroke(2, 9, 1, 1));
        addGlyph('ゅ', -4, 4, makeStroke(-2, 9, -2, 4, 1, 4, 2, 6), makeStroke(1, 9, 1, 1));
        addGlyph('ょ', -4, 4, makeStroke(-3, 7, 0, 7), makeStroke(0, 9, 0, 3, -1, 3, -1, 4, 1, 4, 2, 2));

        // Voiced Dakuten & Handakuten (composed with dots/circles)
        addGlyph('が', -6, 7, makeStroke(-4, 13, 0, 13, 0, 4, -2, 2), makeStroke(-1, 15, 2, 2), makeStroke(3, 12, 5, 10), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ぎ', -5, 7, makeStroke(-4, 12, 4, 12), makeStroke(-5, 9, 3, 9), makeStroke(1, 15, -1, 5), makeStroke(-3, 3, 1, 1, 4, 3), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ぐ', -5, 7, makeStroke(3, 13, -4, 7, 3, 1), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('げ', -5, 7, makeStroke(-4, 14, -4, 2, -2, 1), makeStroke(-1, 10, 4, 10), makeStroke(2, 14, 2, 2), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ご', -5, 7, makeStroke(-4, 11, 4, 11, 3, 9), makeStroke(-3, 2, 3, 2, 4, 4), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));

        addGlyph('ざ', -5, 7, makeStroke(-4, 10, 4, 11), makeStroke(1, 14, -1, 5, 2, 2, 4, 5), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('じ', -5, 7, makeStroke(-2, 14, -2, 4, 1, 1, 4, 3, 5, 7), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ず', -5, 7, makeStroke(-5, 11, 5, 11), makeStroke(0, 15, 0, 8, -3, 8, -3, 5, 0, 5, 0, 1, -2, -1), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ぜ', -5, 7, makeStroke(-5, 10, 5, 10), makeStroke(2, 14, 2, 4, 0, 3), makeStroke(-3, 13, -3, 3, 4, 3), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ぞ', -5, 7, makeStroke(-4, 13, 3, 13, -3, 8, 3, 8, 4, 5, 2, 2, -3, 2), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));

        addGlyph('だ', -5, 7, makeStroke(-4, 11, 1, 11), makeStroke(-1, 14, -3, 3), makeStroke(1, 9, 4, 9), makeStroke(0, 4, 4, 4), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ぢ', -5, 7, makeStroke(-4, 11, 3, 11), makeStroke(0, 14, -2, 7, 3, 7, 4, 4, 1, 1, -3, 1), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('づ', -5, 7, makeStroke(-4, 10, 3, 11, 4, 8, 2, 3, -3, 1), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('で', -5, 7, makeStroke(-4, 11, 3, 11, -2, 7, 3, 3, 4, 5), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ど', -5, 7, makeStroke(-2, 14, 0, 8), makeStroke(-2, 8, 3, 8, 4, 4, 1, 1, -3, 1), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));

        addGlyph('ば', -5, 7, makeStroke(-4, 14, -4, 2, -2, 1), makeStroke(-1, 9, 3, 9), makeStroke(1, 14, 1, 4, -1, 4, -1, 6, 2, 6, 4, 2), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('び', -5, 7, makeStroke(-4, 9, -2, 11, -2, 3, 2, 3, 2, 11, 4, 9), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ぶ', -5, 7, makeStroke(0, 14, 1, 12), makeStroke(0, 10, -1, 5, 1, 3), makeStroke(-4, 7, -3, 5), makeStroke(3, 7, 4, 5), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('べ', -5, 7, makeStroke(-5, 4, -2, 11, 5, 3), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));
        addGlyph('ぼ', -5, 7, makeStroke(-5, 14, -5, 2, -3, 1), makeStroke(-2, 11, 4, 11), makeStroke(-2, 8, 3, 8), makeStroke(1, 14, 1, 4, -1, 4, -1, 6, 2, 6, 4, 2), makeStroke(4, 14, 6, 16), makeStroke(6, 13, 8, 15));

        addGlyph('ぱ', -5, 7, makeStroke(-4, 14, -4, 2, -2, 1), makeStroke(-1, 9, 3, 9), makeStroke(1, 14, 1, 4, -1, 4, -1, 6, 2, 6, 4, 2), makeStroke(4, 14, 6, 14, 6, 16, 4, 16, 4, 14));
        addGlyph('ぴ', -5, 7, makeStroke(-4, 9, -2, 11, -2, 3, 2, 3, 2, 11, 4, 9), makeStroke(4, 14, 6, 14, 6, 16, 4, 16, 4, 14));
        addGlyph('ぷ', -5, 7, makeStroke(0, 14, 1, 12), makeStroke(0, 10, -1, 5, 1, 3), makeStroke(-4, 7, -3, 5), makeStroke(3, 7, 4, 5), makeStroke(4, 14, 6, 14, 6, 16, 4, 16, 4, 14));
        addGlyph('ぺ', -5, 7, makeStroke(-5, 4, -2, 11, 5, 3), makeStroke(4, 14, 6, 14, 6, 16, 4, 16, 4, 14));
        addGlyph('ぽ', -5, 7, makeStroke(-5, 14, -5, 2, -3, 1), makeStroke(-2, 11, 4, 11), makeStroke(-2, 8, 3, 8), makeStroke(1, 14, 1, 4, -1, 4, -1, 6, 2, 6, 4, 2), makeStroke(4, 14, 6, 14, 6, 16, 4, 16, 4, 14));

        // Punctuation & Marks
        addGlyph('、', -3, 3, makeStroke(-1, 2, 1, 0));
        addGlyph('。', -3, 3, makeStroke(-1, 1, 1, 1, 1, 3, -1, 3, -1, 1));
        addGlyph('ー', -5, 5, makeStroke(-5, 7, 5, 7));
        addGlyph('〜', -5, 5, makeStroke(-4, 7, -2, 9, 0, 7, 2, 5, 4, 7));
        addGlyph('「', -4, 4, makeStroke(2, 14, -2, 14, -2, 0));
        addGlyph('」', -4, 4, makeStroke(2, 14, 2, 0, -2, 0));
        addGlyph('゛', -3, 3, makeStroke(-2, 13, 0, 15), makeStroke(0, 12, 2, 14));
        addGlyph('゜', -3, 3, makeStroke(-1, 13, 1, 13, 1, 15, -1, 15, -1, 13));
    }

    private void initializeKatakanaGlyphs() {
        addGlyph('ア', -5, 5, makeStroke(-4, 12, 2, 12, 0, 7), makeStroke(-1, 9, 3, 2));
        addGlyph('イ', -5, 5, makeStroke(-2, 14, -4, 6), makeStroke(-1, 10, -1, 2));
        addGlyph('ウ', -5, 5, makeStroke(0, 15, 0, 13), makeStroke(-4, 10, -4, 7), makeStroke(-4, 10, 3, 10, 1, 2));
        addGlyph('エ', -5, 5, makeStroke(-4, 13, 4, 13), makeStroke(0, 13, 0, 3), makeStroke(-4, 3, 4, 3));
        addGlyph('オ', -5, 5, makeStroke(-4, 11, 4, 11), makeStroke(0, 14, 0, 2, -2, 4), makeStroke(-1, 8, -4, 3));

        addGlyph('カ', -5, 5, makeStroke(-4, 12, 2, 12, 2, 4, 0, 2), makeStroke(-1, 14, 3, 2));
        addGlyph('キ', -5, 5, makeStroke(-4, 12, 4, 12), makeStroke(-5, 9, 3, 9), makeStroke(0, 14, 0, 2));
        addGlyph('ク', -5, 5, makeStroke(-1, 13, -3, 9), makeStroke(-3, 9, 3, 9, 0, 2));
        addGlyph('ケ', -5, 5, makeStroke(-2, 14, -4, 6), makeStroke(-3, 8, 4, 8), makeStroke(1, 12, 1, 2));
        addGlyph('コ', -5, 5, makeStroke(-4, 12, 3, 12, 3, 3, -4, 3));

        addGlyph('サ', -5, 5, makeStroke(-5, 10, 5, 10), makeStroke(-2, 13, -2, 4), makeStroke(2, 13, 2, 2, 0, 1));
        addGlyph('シ', -5, 5, makeStroke(-4, 11, -2, 10), makeStroke(-4, 7, -2, 6), makeStroke(-4, 2, 3, 11));
        addGlyph('ス', -5, 5, makeStroke(-4, 12, 3, 12, -1, 6), makeStroke(0, 7, 3, 2));
        addGlyph('セ', -5, 5, makeStroke(-4, 10, 4, 10), makeStroke(2, 13, 2, 2), makeStroke(-2, 12, -2, 4, 3, 4));
        addGlyph('ソ', -5, 5, makeStroke(-2, 12, -1, 10), makeStroke(2, 13, -3, 2));

        addGlyph('タ', -5, 5, makeStroke(-4, 12, 2, 12), makeStroke(-1, 14, -3, 3), makeStroke(-1, 8, 3, 4));
        addGlyph('チ', -5, 5, makeStroke(3, 13, -3, 11), makeStroke(-4, 8, 4, 8), makeStroke(0, 11, 0, 2, -2, 1));
        addGlyph('ツ', -5, 5, makeStroke(-3, 12, -2, 10), makeStroke(0, 12, 1, 10), makeStroke(3, 13, -3, 2));
        addGlyph('テ', -5, 5, makeStroke(-3, 12, 3, 12), makeStroke(-4, 8, 4, 8), makeStroke(0, 8, -1, 2, 2, 1));
        addGlyph('ト', -5, 5, makeStroke(-1, 14, -1, 2), makeStroke(-1, 8, 3, 6));

        addGlyph('ナ', -5, 5, makeStroke(-4, 11, 4, 11), makeStroke(0, 14, -2, 2));
        addGlyph('ニ', -5, 5, makeStroke(-3, 10, 3, 10), makeStroke(-4, 4, 4, 4));
        addGlyph('ヌ', -5, 5, makeStroke(-4, 12, 3, 12, -3, 4), makeStroke(0, 8, 3, 2));
        addGlyph('ネ', -5, 5, makeStroke(0, 14, 0, 12), makeStroke(-2, 11, -2, 2), makeStroke(-2, 9, 3, 6), makeStroke(0, 7, 3, 2));
        addGlyph('ノ', -5, 5, makeStroke(3, 13, -3, 2));

        addGlyph('ハ', -5, 5, makeStroke(-3, 12, -4, 3), makeStroke(2, 12, 4, 3));
        addGlyph('ヒ', -5, 5, makeStroke(-4, 11, -1, 11), makeStroke(-2, 13, -2, 3, 3, 3), makeStroke(2, 11, 2, 3));
        addGlyph('フ', -5, 5, makeStroke(-4, 12, 3, 12, 0, 7, -2, 2));
        addGlyph('ヘ', -5, 5, makeStroke(-5, 4, -2, 11, 5, 3));
        addGlyph('ホ', -5, 5, makeStroke(-4, 12, 4, 12), makeStroke(0, 14, 0, 2), makeStroke(-3, 8, -4, 3), makeStroke(3, 8, 4, 3));

        addGlyph('マ', -5, 5, makeStroke(-4, 12, 3, 12, -2, 6, 4, 6));
        addGlyph('ミ', -5, 5, makeStroke(-3, 11, 2, 10), makeStroke(-3, 7, 2, 6), makeStroke(-3, 3, 2, 2));
        addGlyph('ム', -5, 5, makeStroke(0, 13, -3, 4, 3, 4), makeStroke(2, 7, 4, 3));
        addGlyph('メ', -5, 5, makeStroke(2, 13, -3, 3), makeStroke(-2, 11, 3, 3));
        addGlyph('モ', -5, 5, makeStroke(-4, 11, 4, 11), makeStroke(-4, 7, 4, 7), makeStroke(0, 13, 0, 3, 3, 3));

        addGlyph('ヤ', -5, 5, makeStroke(-4, 6, -4, 10, 1, 12, 3, 10, 1, 5, 1, 2), makeStroke(-1, 14, 0, 12));
        addGlyph('ユ', -5, 5, makeStroke(-3, 12, 2, 12, 2, 5, -3, 5), makeStroke(0, 13, 0, 2, 3, 2));
        addGlyph('ヨ', -5, 5, makeStroke(2, 12, -3, 12, -3, 3, 2, 3), makeStroke(-3, 7, 1, 7));

        addGlyph('ラ', -5, 5, makeStroke(-3, 13, 2, 13), makeStroke(-2, 9, 2, 9, 2, 4, 0, 2));
        addGlyph('リ', -5, 5, makeStroke(-3, 12, -3, 5), makeStroke(2, 13, 2, 3, 0, 2));
        addGlyph('ル', -5, 5, makeStroke(-2, 13, -3, 3), makeStroke(2, 13, 2, 3, 4, 5));
        addGlyph('レ', -5, 5, makeStroke(-2, 13, -2, 2, 3, 4));
        addGlyph('ロ', -5, 5, makeStroke(-3, 12, 3, 12, 3, 3, -3, 3, -3, 12));

        addGlyph('ワ', -5, 5, makeStroke(-3, 12, -3, 4), makeStroke(-3, 12, 3, 12, 3, 4, 1, 2));
        addGlyph('ヲ', -5, 5, makeStroke(-4, 12, 3, 12), makeStroke(-3, 8, 2, 8), makeStroke(-1, 8, -2, 3, 3, 3));
        addGlyph('ン', -5, 5, makeStroke(-3, 9, -1, 7), makeStroke(-4, 3, 3, 12));
        addGlyph('ッ', -4, 4, makeStroke(-2, 8, -1, 6), makeStroke(1, 9, 2, 7), makeStroke(2, 10, -2, 3));
    }

    // =========================================================================
    // DEVANAGARI SCRIPT (Single-Stroke Centerlines with Authentic Shirorekha)
    // Headline (Shirorekha) is at Y=16. Baseline is Y=0.
    // =========================================================================

    private void initializeDevanagariGlyphs() {
        // Vowels
        addGlyph('अ', -6, 6, makeStroke(-5, 14, -2, 13, -3, 10, -5, 10), makeStroke(-3, 10, -1, 7, -3, 3, -5, 3), makeStroke(-2, 10, 2, 10), makeStroke(2, 16, 2, 0), makeStroke(0, 16, 5, 16));
        addGlyph('आ', -6, 7, makeStroke(-5, 14, -2, 13, -3, 10, -5, 10), makeStroke(-3, 10, -1, 7, -3, 3, -5, 3), makeStroke(-2, 10, 2, 10), makeStroke(2, 16, 2, 0), makeStroke(5, 16, 5, 0), makeStroke(0, 16, 7, 16));
        addGlyph('इ', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 13, 3, 11, -3, 8, 2, 5, -2, 2, 3, -2));
        addGlyph('ई', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 13, 3, 11, -3, 8, 2, 5, -2, 2, 3, -2), makeStroke(0, 16, 2, 19, 5, 17));
        addGlyph('उ', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-3, 14, 2, 12, 0, 9, -3, 9), makeStroke(0, 9, 3, 6, 0, 2, -4, 2));
        addGlyph('ऊ', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-3, 14, 2, 12, 0, 9, -3, 9), makeStroke(0, 9, 3, 6, 0, 2, -4, 2), makeStroke(0, 9, 3, 8, 5, 4));
        addGlyph('ऋ', -6, 6, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 0), makeStroke(-4, 13, 0, 8, -4, 4), makeStroke(0, 10, 3, 12, 4, 10, 2, 7, 4, 3));
        addGlyph('ए', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-2, 16, -2, 9, 2, 0), makeStroke(2, 16, 2, 9, -1, 9));
        addGlyph('ऐ', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-2, 16, -2, 9, 2, 0), makeStroke(2, 16, 2, 9, -1, 9), makeStroke(0, 16, 3, 20));
        addGlyph('ओ', -6, 7, makeStroke(-5, 14, -2, 13, -3, 10, -5, 10), makeStroke(-3, 10, -1, 7, -3, 3, -5, 3), makeStroke(-2, 10, 2, 10), makeStroke(2, 16, 2, 0), makeStroke(5, 16, 5, 0), makeStroke(0, 16, 7, 16), makeStroke(5, 16, 2, 20));
        addGlyph('औ', -6, 7, makeStroke(-5, 14, -2, 13, -3, 10, -5, 10), makeStroke(-3, 10, -1, 7, -3, 3, -5, 3), makeStroke(-2, 10, 2, 10), makeStroke(2, 16, 2, 0), makeStroke(5, 16, 5, 0), makeStroke(0, 16, 7, 16), makeStroke(5, 16, 2, 20), makeStroke(5, 16, 4, 20));

        // Consonants
        addGlyph('क', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(0, 16, 0, 0), makeStroke(0, 12, -4, 12, -5, 8, -4, 4, 0, 4), makeStroke(0, 11, 4, 11, 5, 8, 4, 3, 3, 2));
        addGlyph('ख', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 16, -2, 13, -2, 9, -5, 3, 0, 3), makeStroke(3, 16, 3, 0), makeStroke(3, 8, 0, 8, -1, 5, 0, 3, 3, 3));
        addGlyph('ग', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-2, 16, -2, 5, -4, 5, -4, 8, -2, 8), makeStroke(3, 16, 3, 0));
        addGlyph('घ', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 16, -1, 13, -3, 10, -5, 10), makeStroke(-3, 10, 0, 8, -2, 4, 3, 4), makeStroke(3, 16, 3, 0));
        addGlyph('ङ', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 13, 3, 11, -3, 8, 2, 5, -2, 2), makeStroke(4, 5, 5, 5));

        addGlyph('च', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 8, 0, 8, -3, 3, 3, 3), makeStroke(3, 16, 3, 0));
        addGlyph('छ', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 16, -1, 13, -3, 10, -5, 10), makeStroke(-3, 10, 0, 8, -2, 4, 1, 4, 2, 6, 0, 8), makeStroke(1, 8, 1, 11));
        addGlyph('ज', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(3, 16, 3, 0), makeStroke(3, 8, -1, 8, -4, 6, -4, 3, 0, 3, 0, 6));
        addGlyph('झ', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-3, 16, -3, 13, 0, 11, -5, 8, 0, 5, -4, 2, 0, -2), makeStroke(-2, 8, 3, 8), makeStroke(3, 16, 3, 0));
        addGlyph('ञ', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 12, -2, 12, -2, 5, -4, 5), makeStroke(-2, 8, 3, 8), makeStroke(3, 16, 3, 0));

        addGlyph('ट', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 12, -4, 10, -4, 4, 3, 1, 4, 3));
        addGlyph('ठ', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 12), makeStroke(0, 12, -4, 10, -4, 4, 0, 2, 4, 4, 4, 10, 0, 12));
        addGlyph('ड', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 13, 3, 11, -3, 8, 2, 5, -3, 2));
        addGlyph('ढ', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 12, -4, 10, -4, 4, 2, 2, 3, 4, 1, 6, -1, 5));
        addGlyph('ण', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 16, -4, 5, -1, 5, -1, 16), makeStroke(3, 16, 3, 0));

        addGlyph('त', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(2, 16, 2, 0), makeStroke(2, 9, -2, 9, -4, 5, -4, 0));
        addGlyph('थ', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-3, 14, -4, 12, -2, 10, -4, 7, -2, 4, 2, 4), makeStroke(2, 16, 2, 0));
        addGlyph('द', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 12, -3, 10, -3, 5, 1, 4, 2, 1, 3, -2));
        addGlyph('ध', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-3, 14, -4, 12, -2, 10, -4, 10), makeStroke(-3, 10, 0, 8, -2, 4, 3, 4), makeStroke(3, 16, 3, 0));
        addGlyph('न', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-5, 7, -5, 4, -3, 4, 3, 7), makeStroke(3, 16, 3, 0));

        addGlyph('प', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-3, 16, -3, 6, 3, 6), makeStroke(3, 16, 3, 0));
        addGlyph('फ', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-3, 16, -3, 6, 0, 6), makeStroke(0, 16, 0, 0), makeStroke(0, 8, 4, 8, 5, 5, 4, 2));
        addGlyph('ब', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(3, 16, 3, 0), makeStroke(3, 11, -2, 11, -4, 8, -2, 4, 3, 4), makeStroke(-2, 10, 2, 5));
        addGlyph('भ', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 14, -4, 11, -3, 10, -5, 6, -3, 4, 3, 4), makeStroke(3, 16, 3, 0));
        addGlyph('म', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-3, 16, -3, 5, -5, 5, -5, 8, 3, 8), makeStroke(3, 16, 3, 0));

        addGlyph('य', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 16, -1, 13, -3, 10, -1, 6, 3, 6), makeStroke(3, 16, 3, 0));
        addGlyph('र', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-2, 16, 2, 13, 2, 9, -3, 0));
        addGlyph('ल', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 5, -2, 5, 0, 8, 3, 8), makeStroke(3, 16, 3, 0));
        addGlyph('व', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(2, 16, 2, 0), makeStroke(2, 11, -3, 11, -4, 8, -3, 4, 2, 4));

        addGlyph('श', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-4, 14, -3, 12, -4, 10, -1, 8, -4, 0), makeStroke(-2, 9, 0, 7), makeStroke(3, 16, 3, 0));
        addGlyph('ष', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(-3, 16, -3, 6, 3, 6), makeStroke(3, 16, 3, 0), makeStroke(-3, 12, 3, 6));
        addGlyph('स', -6, 6, makeStroke(-6, 16, 6, 16), makeStroke(-3, 16, 0, 13, 0, 9, -4, 0), makeStroke(-1, 8, 3, 8), makeStroke(3, 16, 3, 0));
        addGlyph('ह', -5, 5, makeStroke(-5, 16, 5, 16), makeStroke(0, 16, 0, 13, 3, 11, -2, 8, 2, 4, -3, 1));

        // Matras
        addGlyph('ा', -3, 3, makeStroke(-3, 16, 3, 16), makeStroke(0, 16, 0, 0));
        addGlyph('ि', -4, 4, makeStroke(-4, 16, 4, 16), makeStroke(-2, 16, -2, 0), makeStroke(-2, 16, 0, 20, 2, 16));
        addGlyph('ी', -4, 4, makeStroke(-4, 16, 4, 16), makeStroke(2, 16, 2, 0), makeStroke(-2, 16, 0, 20, 2, 16));
        addGlyph('ु', -3, 3, makeStroke(0, 0, -2, -3, -4, -1, -2, 0));
        addGlyph('ू', -3, 3, makeStroke(0, 0, 2, -3, 4, -1, 2, 0, 4, -4));
        addGlyph('ृ', -3, 3, makeStroke(0, 0, 0, -3, 3, -3, 3, -1));
        addGlyph('े', -3, 3, makeStroke(0, 16, -3, 21));
        addGlyph('ै', -3, 3, makeStroke(0, 16, -4, 21), makeStroke(1, 16, 0, 21));
        addGlyph('ो', -3, 3, makeStroke(-3, 16, 3, 16), makeStroke(0, 16, 0, 0), makeStroke(0, 16, -3, 21));
        addGlyph('ौ', -3, 3, makeStroke(-3, 16, 3, 16), makeStroke(0, 16, 0, 0), makeStroke(0, 16, -4, 21), makeStroke(1, 16, 0, 21));
        addGlyph('ं', -2, 2, makeStroke(-1, 18, 1, 18, 1, 20, -1, 20, -1, 18));
        addGlyph('ः', -2, 2, makeStroke(0, 11, 1, 11, 1, 12, 0, 12, 0, 11), makeStroke(0, 5, 1, 5, 1, 6, 0, 6, 0, 5));
        addGlyph('्', -2, 2, makeStroke(0, 0, -3, -4));
        addGlyph('ँ', -3, 3, makeStroke(-3, 18, 0, 17, 3, 18), makeStroke(0, 19, 0, 20));

        // Digits
        addGlyph('०', -4, 4, makeStroke(-3, 3, 0, 1, 3, 3, 3, 11, 0, 13, -3, 11, -3, 3));
        addGlyph('१', -4, 4, makeStroke(2, 8, 0, 10, -2, 8, 0, 6, 2, 8, 2, 1, 0, 0));
        addGlyph('२', -4, 4, makeStroke(-3, 10, 0, 13, 3, 10, 1, 7, -3, 3, 2, 1, 3, -2));
        addGlyph('३', -4, 4, makeStroke(-3, 12, 1, 12, 2, 9, -1, 7, 2, 5, 1, 2, -3, 2, -2, -2));
        addGlyph('४', -4, 4, makeStroke(-1, 12, 2, 12, -2, 2, 3, 2, 3, 12, 3, 0));
        addGlyph('५', -4, 4, makeStroke(-3, 12, -3, 7, 1, 7, 3, 5, 3, 2, -1, 1, -3, 2));
        addGlyph('६', -4, 4, makeStroke(3, 12, -1, 12, -2, 9, 1, 7, -2, 5, -1, 2, 3, 2, 2, -2));
        addGlyph('७', -4, 4, makeStroke(-3, 6, -1, 8, 1, 6, -1, 4, -3, 6, 1, 10, 3, 4, 3, 0));
        addGlyph('८', -4, 4, makeStroke(-3, 2, 1, 6, 3, 11, 1, 13, -2, 11, -2, 8, 3, 2));
        addGlyph('९', -4, 4, makeStroke(2, 6, 0, 8, -2, 6, 0, 4, 2, 6, 2, 12, -1, 12));

        // Sacred & Punctuation
        addGlyph('।', -2, 2, makeStroke(0, 16, 0, 0));
        addGlyph('॥', -3, 3, makeStroke(-2, 16, -2, 0), makeStroke(2, 16, 2, 0));
        addGlyph('ॐ', -6, 7, makeStroke(-4, 14, 0, 12, -2, 9, -4, 9), makeStroke(-2, 9, 1, 6, -1, 2, -4, 2, -3, 0, 4, 3, 6, 9), makeStroke(3, 13, 5, 11, 7, 13), makeStroke(5, 15, 5, 16));
    }
}
