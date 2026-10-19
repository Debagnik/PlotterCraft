package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.Point2D;
import in.debagnik.catplotlib.model.VectorStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@DisplayName("SvgPathParserService Unit Tests")
class SvgPathParserServiceTest {

    private SvgPathParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new SvgPathParserService();
    }

    @Test
    @DisplayName("Empty or null SVG string returns empty result")
    void testEmptyAndNullSvg() {
        SvgPathParserService.SvgRenderResult resNull = parserService.parseAndScaleSvg(null, 10, 10, 100, 100);
        assertThat(resNull.strokes).isEmpty();
        assertThat(resNull.bounds.isValid()).isFalse();

        SvgPathParserService.SvgRenderResult resEmpty = parserService.parseAndScaleSvg("", 10, 10, 100, 100);
        assertThat(resEmpty.strokes).isEmpty();

        List<VectorStroke> pathEmpty = parserService.parsePathData("");
        assertThat(pathEmpty).isEmpty();
    }

    @Test
    @DisplayName("Parse SVG <line> element")
    void testParseLine() {
        String svg = """
                <svg viewBox="0 0 100 100">
                    <line x1="10" y1="20" x2="90" y2="80" stroke="black"/>
                </svg>
                """;
        List<VectorStroke> strokes = parserService.parseSvgStrokes(svg);

        assertThat(strokes).hasSize(1);
        VectorStroke stroke = strokes.get(0);
        assertThat(stroke.getPoints()).hasSize(2);
        assertThat(stroke.getPoints().get(0)).isEqualTo(new Point2D(10.0, 20.0));
        assertThat(stroke.getPoints().get(1)).isEqualTo(new Point2D(90.0, 80.0));
    }

    @Test
    @DisplayName("Parse SVG <rect> element into closed rectangular stroke")
    void testParseRect() {
        String svg = """
                <svg viewBox="0 0 100 100">
                    <rect x="5" y="10" width="40" height="20" />
                </svg>
                """;
        List<VectorStroke> strokes = parserService.parseSvgStrokes(svg);

        assertThat(strokes).hasSize(1);
        List<Point2D> pts = strokes.get(0).getPoints();
        // 5 points: (x,y), (x+w,y), (x+w,y+h), (x,y+h), (x,y)
        assertThat(pts).hasSize(5);
        assertThat(pts.get(0)).isEqualTo(new Point2D(5.0, 10.0));
        assertThat(pts.get(1)).isEqualTo(new Point2D(45.0, 10.0));
        assertThat(pts.get(2)).isEqualTo(new Point2D(45.0, 30.0));
        assertThat(pts.get(3)).isEqualTo(new Point2D(5.0, 30.0));
        assertThat(pts.get(4)).isEqualTo(new Point2D(5.0, 10.0)); // closed
    }

    @Test
    @DisplayName("Parse SVG <circle> and <ellipse> into discretized closed loops")
    void testParseCircleAndEllipse() {
        String svg = """
                <svg viewBox="0 0 100 100">
                    <circle cx="50" cy="50" r="25" />
                    <ellipse cx="30" cy="40" rx="15" ry="10" />
                </svg>
                """;
        List<VectorStroke> strokes = parserService.parseSvgStrokes(svg);

        assertThat(strokes).hasSize(2);
        // Circle and ellipse should have multiple points forming a closed loop
        VectorStroke circleStroke = strokes.get(0);
        assertThat(circleStroke.getPoints().size()).isGreaterThan(16);
        Point2D firstCirclePt = circleStroke.getPoints().get(0);
        Point2D lastCirclePt = circleStroke.getPoints().get(circleStroke.getPoints().size() - 1);
        assertThat(firstCirclePt.distanceTo(lastCirclePt)).isLessThan(0.01);

        VectorStroke ellipseStroke = strokes.get(1);
        assertThat(ellipseStroke.getPoints().size()).isGreaterThan(16);
    }

    @Test
    @DisplayName("Parse <polyline> (open) and <polygon> (closed)")
    void testParsePolylineAndPolygon() {
        String svg = """
                <svg viewBox="0 0 100 100">
                    <polyline points="0,0 10,10 20,0" />
                    <polygon points="0,0 10,10 20,0" />
                </svg>
                """;
        List<VectorStroke> strokes = parserService.parseSvgStrokes(svg);

        assertThat(strokes).hasSize(2);
        VectorStroke polyline = strokes.get(0);
        VectorStroke polygon = strokes.get(1);

        assertThat(polyline.getPoints()).hasSize(3);
        // Polygon automatically appends first point at the end
        assertThat(polygon.getPoints()).hasSize(4);
        assertThat(polygon.getPoints().get(3)).isEqualTo(polygon.getPoints().get(0));
    }

    @Test
    @DisplayName("Parse SVG path line commands (M, L, H, V, Z) in both absolute and relative coordinates")
    void testPathLineCommands() {
        // Absolute
        String dAbs = "M 10 20 L 30 40 H 50 V 60 Z";
        List<VectorStroke> strokesAbs = parserService.parsePathData(dAbs);
        assertThat(strokesAbs).hasSize(1);
        List<Point2D> ptsAbs = strokesAbs.get(0).getPoints();
        assertThat(ptsAbs.get(0)).isEqualTo(new Point2D(10.0, 20.0));
        assertThat(ptsAbs.get(1)).isEqualTo(new Point2D(30.0, 40.0));
        assertThat(ptsAbs.get(2)).isEqualTo(new Point2D(50.0, 40.0));
        assertThat(ptsAbs.get(3)).isEqualTo(new Point2D(50.0, 60.0));
        assertThat(ptsAbs.get(4)).isEqualTo(new Point2D(10.0, 20.0)); // Z returns to start

        // Relative
        String dRel = "m 10 20 l 20 20 h 20 v 20 z";
        List<VectorStroke> strokesRel = parserService.parsePathData(dRel);
        assertThat(strokesRel).hasSize(1);
        List<Point2D> ptsRel = strokesRel.get(0).getPoints();
        assertThat(ptsRel.get(0)).isEqualTo(new Point2D(10.0, 20.0));
        assertThat(ptsRel.get(1)).isEqualTo(new Point2D(30.0, 40.0));
        assertThat(ptsRel.get(2)).isEqualTo(new Point2D(50.0, 40.0));
        assertThat(ptsRel.get(3)).isEqualTo(new Point2D(50.0, 60.0));
        assertThat(ptsRel.get(4)).isEqualTo(new Point2D(10.0, 20.0));
    }

    @Test
    @DisplayName("Parse Bézier curves: Cubic (C, S) and Quadratic (Q, T)")
    void testBezierCurves() {
        // Cubic Bézier (M, C, S)
        String dCubic = "M 10 80 C 40 10, 65 10, 95 80 S 150 150, 180 80";
        List<VectorStroke> strokesCubic = parserService.parsePathData(dCubic);
        assertThat(strokesCubic).hasSize(1);
        assertThat(strokesCubic.get(0).getPoints().size()).isGreaterThan(10);

        // Quadratic Bézier (M, Q, T)
        String dQuad = "M 20 80 Q 90 10, 160 80 T 300 80";
        List<VectorStroke> strokesQuad = parserService.parsePathData(dQuad);
        assertThat(strokesQuad).hasSize(1);
        assertThat(strokesQuad.get(0).getPoints().size()).isGreaterThan(10);
    }

    @Test
    @DisplayName("Parse Elliptical Arc (A / a)")
    void testArcCommands() {
        String dArc = "M 10 10 A 25 25 0 0 1 60 10";
        List<VectorStroke> strokesArc = parserService.parsePathData(dArc);
        assertThat(strokesArc).hasSize(1);
        assertThat(strokesArc.get(0).getPoints().size()).isGreaterThan(5);

        Point2D endPt = strokesArc.get(0).getPoints().get(strokesArc.get(0).getPoints().size() - 1);
        assertThat(endPt.getX()).isEqualTo(60.0, offset(0.5));
        assertThat(endPt.getY()).isEqualTo(10.0, offset(0.5));
    }

    @Test
    @DisplayName("Parse and scale SVG maintains aspect ratio and fits within bounds")
    void testParseAndScaleSvg() {
        String svg = """
                <svg viewBox="0 0 200 100">
                    <line x1="0" y1="0" x2="200" y2="100" />
                </svg>
                """;

        double startX = 20.0;
        double startY = 30.0;
        double maxWidth = 100.0;
        double maxHeight = 100.0;

        SvgPathParserService.SvgRenderResult result = parserService.parseAndScaleSvg(
                svg, startX, startY, maxWidth, maxHeight
        );

        assertThat(result.strokes).isNotEmpty();
        assertThat(result.renderedWidth).isEqualTo(100.0, offset(0.1));
        assertThat(result.renderedHeight).isEqualTo(50.0, offset(0.1)); // 2:1 aspect ratio
        assertThat(result.bounds.getMinX()).isEqualTo(startX, offset(0.1));
        assertThat(result.bounds.getMinY()).isEqualTo(startY, offset(0.1));
        assertThat(result.bounds.getMaxX()).isEqualTo(startX + 100.0, offset(0.1));
        assertThat(result.bounds.getMaxY()).isEqualTo(startY + 50.0, offset(0.1));
    }

    @Test
    @DisplayName("Parse relative curves (c, s, q, t, a) and repeated coordinates without commands")
    void testRelativeCurvesAndRepeatedCoordinates() {
        String d = "m 10 10 c 5 5 10 5 15 0 s 10 -5 15 0 q 5 5 10 0 t 10 0 a 10 10 0 0 1 20 0 z";
        List<VectorStroke> strokes = parserService.parsePathData(d);
        assertThat(strokes).hasSize(1);
        assertThat(strokes.get(0).getPoints().size()).isGreaterThan(10);

        // Sequence of numbers after M without repeated command letter
        String dRepeated = "M 10 10 20 20 30 30";
        List<VectorStroke> repeatedStrokes = parserService.parsePathData(dRepeated);
        assertThat(repeatedStrokes).hasSize(1);
        assertThat(repeatedStrokes.get(0).getPoints()).hasSize(3);
    }

    @Test
    @DisplayName("Commands without explicit leading M command start safely from origin")
    void testCommandsWithoutLeadingM() {
        String d = "L 20 30 H 40 V 50 C 60 70 80 90 100 110 S 120 130 140 150 Q 160 170 180 190 T 200 210 A 10 10 0 0 1 220 230";
        List<VectorStroke> strokes = parserService.parsePathData(d);
        assertThat(strokes).hasSize(1);
        assertThat(strokes.get(0).getPoints()).isNotEmpty();
    }

    @Test
    @DisplayName("Multiple disconnected subpaths within one path data attribute")
    void testMultipleSubpaths() {
        String d = "M 10 10 L 20 20 M 30 30 L 40 40";
        List<VectorStroke> strokes = parserService.parsePathData(d);
        assertThat(strokes).hasSize(2);
    }

    @Test
    @DisplayName("Elliptical Arc with zero radius, collinear endpoints, and large angle rotations")
    void testArcEdgeCases() {
        // Zero radius arc degenerates to straight line segment
        String dZeroR = "M 10 10 A 0 0 0 0 0 30 30";
        List<VectorStroke> strokesZero = parserService.parsePathData(dZeroR);
        assertThat(strokesZero).hasSize(1);

        // Same start and end point
        String dSamePt = "M 20 20 A 15 15 0 0 0 20 20";
        List<VectorStroke> strokesSame = parserService.parsePathData(dSamePt);
        assertThat(strokesSame).hasSize(1);

        // Arc with rotation and radii requiring scaling
        String dRotated = "M 0 0 A 5 2 45 1 0 50 50";
        List<VectorStroke> strokesRot = parserService.parsePathData(dRotated);
        assertThat(strokesRot).hasSize(1);
    }

    @Test
    @DisplayName("SVG containing unsupported or malformed tags gracefully ignored")
    void testUnsupportedOrMalformedTags() {
        String svg = """
                <svg viewBox="0 0 100 100">
                    <circle cx="50" cy="50" r="-5" />
                    <rect x="0" y="0" width="-10" height="20" />
                    <line x1="invalid" />
                    <unknownTag attr="val" />
                    <path d="X 10 20 30" />
                </svg>
                """;
        List<VectorStroke> strokes = parserService.parseSvgStrokes(svg);
        assertThat(strokes).isEmpty();

        SvgPathParserService.SvgRenderResult res = parserService.parseAndScaleSvg(svg, 0, 0, 100, 100);
        assertThat(res.strokes).isEmpty();
    }
}
