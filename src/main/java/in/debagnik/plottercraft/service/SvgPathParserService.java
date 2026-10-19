package in.debagnik.plottercraft.service;

import in.debagnik.plottercraft.model.BoundingBox;
import in.debagnik.plottercraft.model.Point2D;
import in.debagnik.plottercraft.model.VectorStroke;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computational Geometry Engine for parsing SVG vector paths,
 * shapes, and Bézier curves into discrete plotter line segments.
 */
@Service
public class SvgPathParserService {

    private static final double CHORD_TOLERANCE = 0.05; // 0.05 mm chordal error tolerance

    public static class SvgRenderResult {
        public final List<VectorStroke> strokes;
        public final BoundingBox bounds;
        public final double renderedWidth;
        public final double renderedHeight;

        public SvgRenderResult(List<VectorStroke> strokes, BoundingBox bounds, double renderedWidth, double renderedHeight) {
            this.strokes = strokes;
            this.bounds = bounds;
            this.renderedWidth = renderedWidth;
            this.renderedHeight = renderedHeight;
        }
    }

    /**
     * Parses an SVG XML document into discrete vector strokes,
     * auto-scaled to fit within the provided maxWidth and placed at (startX, startY).
     */
    public SvgRenderResult parseAndScaleSvg(String svgContent, double startX, double startY, double maxWidth, double maxHeight) {
        if (svgContent == null || svgContent.trim().isEmpty()) {
            return new SvgRenderResult(new ArrayList<>(), BoundingBox.empty(), 0, 0);
        }

        List<VectorStroke> rawStrokes = parseSvgStrokes(svgContent);
        if (rawStrokes.isEmpty()) {
            return new SvgRenderResult(new ArrayList<>(), BoundingBox.empty(), 0, 0);
        }

        // Calculate native bounding box
        BoundingBox box = BoundingBox.empty();
        for (VectorStroke stroke : rawStrokes) {
            for (Point2D pt : stroke.getPoints()) {
                box.expandToInclude(pt.getX(), pt.getY());
            }
        }

        if (!box.isValid()) {
            return new SvgRenderResult(new ArrayList<>(), box, 0, 0);
        }

        double nativeWidth = Math.max(0.1, box.getWidth());
        double nativeHeight = Math.max(0.1, box.getHeight());

        // Scale to fit within maxWidth and maxHeight (maintain aspect ratio)
        double scaleX = maxWidth / nativeWidth;
        double scaleY = maxHeight > 0 ? (maxHeight / nativeHeight) : scaleX;
        double scale = Math.min(scaleX, scaleY);

        double finalWidth = nativeWidth * scale;
        double finalHeight = nativeHeight * scale;

        // Transform strokes: translate native minX/minY to (startX, startY) with scaling
        List<VectorStroke> scaledStrokes = new ArrayList<>();
        BoundingBox scaledBox = BoundingBox.empty();

        for (VectorStroke raw : rawStrokes) {
            VectorStroke scaled = new VectorStroke();
            scaled.setTag("SVG");
            for (Point2D pt : raw.getPoints()) {
                double tx = startX + (pt.getX() - box.getMinX()) * scale;
                double ty = startY + (pt.getY() - box.getMinY()) * scale;
                scaled.addPoint(tx, ty);
                scaledBox.expandToInclude(tx, ty);
            }
            if (!scaled.isEmpty()) {
                scaledStrokes.add(scaled);
            }
        }

        return new SvgRenderResult(scaledStrokes, scaledBox, finalWidth, finalHeight);
    }

    /**
     * Extracts and converts all SVG elements (<path>, <line>, <rect>, <circle>, <ellipse>, <polyline>, <polygon>)
     * into unscaled vector strokes.
     */
    public List<VectorStroke> parseSvgStrokes(String svgContent) {
        List<VectorStroke> allStrokes = new ArrayList<>();

        // 1. Parse <path d="..." ...>
        Pattern pathPattern = Pattern.compile("<path[^>]*?\\bd=[\"']([^\"']+)[\"'][^>]*?>", Pattern.CASE_INSENSITIVE);
        Matcher pathMatcher = pathPattern.matcher(svgContent);
        while (pathMatcher.find()) {
            String pathData = pathMatcher.group(1);
            allStrokes.addAll(parsePathData(pathData));
        }

        // 2. Parse <line x1="..." y1="..." x2="..." y2="..." ...>
        Pattern linePattern = Pattern.compile("<line[^>]*?>", Pattern.CASE_INSENSITIVE);
        Matcher lineMatcher = linePattern.matcher(svgContent);
        while (lineMatcher.find()) {
            String tag = lineMatcher.group(0);
            Double x1 = getAttrDouble(tag, "x1");
            Double y1 = getAttrDouble(tag, "y1");
            Double x2 = getAttrDouble(tag, "x2");
            Double y2 = getAttrDouble(tag, "y2");
            if (x1 != null && y1 != null && x2 != null && y2 != null) {
                VectorStroke vs = new VectorStroke();
                vs.setTag("SVG");
                vs.addPoint(x1, y1);
                vs.addPoint(x2, y2);
                allStrokes.add(vs);
            }
        }

        // 3. Parse <rect x="..." y="..." width="..." height="..." ...>
        Pattern rectPattern = Pattern.compile("<rect[^>]*?>", Pattern.CASE_INSENSITIVE);
        Matcher rectMatcher = rectPattern.matcher(svgContent);
        while (rectMatcher.find()) {
            String tag = rectMatcher.group(0);
            double x = getAttrDouble(tag, "x", 0.0);
            double y = getAttrDouble(tag, "y", 0.0);
            Double w = getAttrDouble(tag, "width");
            Double h = getAttrDouble(tag, "height");
            if (w != null && h != null && w > 0 && h > 0) {
                VectorStroke vs = new VectorStroke();
                vs.setTag("SVG");
                vs.addPoint(x, y);
                vs.addPoint(x + w, y);
                vs.addPoint(x + w, y + h);
                vs.addPoint(x, y + h);
                vs.addPoint(x, y); // Close rect
                allStrokes.add(vs);
            }
        }

        // 4. Parse <circle cx="..." cy="..." r="..." ...>
        Pattern circlePattern = Pattern.compile("<circle[^>]*?>", Pattern.CASE_INSENSITIVE);
        Matcher circleMatcher = circlePattern.matcher(svgContent);
        while (circleMatcher.find()) {
            String tag = circleMatcher.group(0);
            double cx = getAttrDouble(tag, "cx", 0.0);
            double cy = getAttrDouble(tag, "cy", 0.0);
            Double r = getAttrDouble(tag, "r");
            if (r != null && r > 0) {
                allStrokes.add(discretizeEllipse(cx, cy, r, r));
            }
        }

        // 5. Parse <ellipse cx="..." cy="..." rx="..." ry="..." ...>
        Pattern ellipsePattern = Pattern.compile("<ellipse[^>]*?>", Pattern.CASE_INSENSITIVE);
        Matcher ellipseMatcher = ellipsePattern.matcher(svgContent);
        while (ellipseMatcher.find()) {
            String tag = ellipseMatcher.group(0);
            double cx = getAttrDouble(tag, "cx", 0.0);
            double cy = getAttrDouble(tag, "cy", 0.0);
            Double rx = getAttrDouble(tag, "rx");
            Double ry = getAttrDouble(tag, "ry");
            if (rx != null && ry != null && rx > 0 && ry > 0) {
                allStrokes.add(discretizeEllipse(cx, cy, rx, ry));
            }
        }

        // 6. Parse <polyline points="..." ...> and <polygon points="..." ...>
        Pattern polyPattern = Pattern.compile("<(polyline|polygon)[^>]*?\\bpoints=[\"']([^\"']+)[\"'][^>]*?>", Pattern.CASE_INSENSITIVE);
        Matcher polyMatcher = polyPattern.matcher(svgContent);
        while (polyMatcher.find()) {
            boolean isPolygon = "polygon".equalsIgnoreCase(polyMatcher.group(1));
            String ptsStr = polyMatcher.group(2);
            VectorStroke vs = parsePointsList(ptsStr, isPolygon);
            if (!vs.isEmpty()) {
                allStrokes.add(vs);
            }
        }

        return allStrokes;
    }

    /**
     * Parses an SVG path data string (M, L, C, S, Q, T, A, Z) into a list of continuous VectorStrokes.
     */
    public List<VectorStroke> parsePathData(String d) {
        List<VectorStroke> strokes = new ArrayList<>();
        if (d == null || d.trim().isEmpty()) {
            return strokes;
        }

        // Tokenize path into command tokens and numeric coordinates
        List<String> tokens = tokenizePath(d);
        if (tokens.isEmpty()) {
            return strokes;
        }

        double curX = 0.0;
        double curY = 0.0;
        double startX = 0.0;
        double startY = 0.0;
        double lastCtrlX = 0.0;
        double lastCtrlY = 0.0;
        char lastCmd = ' ';

        VectorStroke currentStroke = null;
        int idx = 0;

        while (idx < tokens.size()) {
            String token = tokens.get(idx);
            char cmd = token.charAt(0);

            if (Character.isLetter(cmd)) {
                idx++;
            } else {
                // If token is a number without a command letter, repeat previous command
                cmd = (lastCmd == 'M') ? 'L' : (lastCmd == 'm') ? 'l' : lastCmd;
            }

            boolean isRelative = Character.isLowerCase(cmd);
            char ucCmd = Character.toUpperCase(cmd);

            switch (ucCmd) {
                case 'M': { // Move to
                    if (idx + 1 < tokens.size() && isNumeric(tokens.get(idx)) && isNumeric(tokens.get(idx + 1))) {
                        double x = Double.parseDouble(tokens.get(idx++));
                        double y = Double.parseDouble(tokens.get(idx++));
                        if (isRelative) {
                            curX += x;
                            curY += y;
                        } else {
                            curX = x;
                            curY = y;
                        }
                        startX = curX;
                        startY = curY;
                        lastCtrlX = curX;
                        lastCtrlY = curY;

                        // Start a new stroke
                        if (currentStroke != null && !currentStroke.isEmpty()) {
                            strokes.add(currentStroke);
                        }
                        currentStroke = new VectorStroke();
                        currentStroke.setTag("SVG");
                        currentStroke.addPoint(curX, curY);

                        // Subsequent coordinates after M are treated as L / l
                        lastCmd = isRelative ? 'l' : 'L';
                    }
                    break;
                }
                case 'L': { // Line to
                    while (idx + 1 < tokens.size() && isNumeric(tokens.get(idx)) && isNumeric(tokens.get(idx + 1))) {
                        double x = Double.parseDouble(tokens.get(idx++));
                        double y = Double.parseDouble(tokens.get(idx++));
                        if (isRelative) {
                            curX += x;
                            curY += y;
                        } else {
                            curX = x;
                            curY = y;
                        }
                        if (currentStroke == null) {
                            currentStroke = new VectorStroke();
                            currentStroke.setTag("SVG");
                            currentStroke.addPoint(startX, startY);
                        }
                        currentStroke.addPoint(curX, curY);
                        lastCtrlX = curX;
                        lastCtrlY = curY;
                    }
                    lastCmd = cmd;
                    break;
                }
                case 'H': { // Horizontal Line to
                    while (idx < tokens.size() && isNumeric(tokens.get(idx))) {
                        double x = Double.parseDouble(tokens.get(idx++));
                        curX = isRelative ? (curX + x) : x;
                        if (currentStroke == null) {
                            currentStroke = new VectorStroke();
                            currentStroke.setTag("SVG");
                            currentStroke.addPoint(startX, startY);
                        }
                        currentStroke.addPoint(curX, curY);
                        lastCtrlX = curX;
                        lastCtrlY = curY;
                    }
                    lastCmd = cmd;
                    break;
                }
                case 'V': { // Vertical Line to
                    while (idx < tokens.size() && isNumeric(tokens.get(idx))) {
                        double y = Double.parseDouble(tokens.get(idx++));
                        curY = isRelative ? (curY + y) : y;
                        if (currentStroke == null) {
                            currentStroke = new VectorStroke();
                            currentStroke.setTag("SVG");
                            currentStroke.addPoint(startX, startY);
                        }
                        currentStroke.addPoint(curX, curY);
                        lastCtrlX = curX;
                        lastCtrlY = curY;
                    }
                    lastCmd = cmd;
                    break;
                }
                case 'C': { // Cubic Bézier (x1, y1, x2, y2, x, y)
                    while (idx + 5 < tokens.size() && isNumeric(tokens.get(idx)) && isNumeric(tokens.get(idx + 1))
                            && isNumeric(tokens.get(idx + 2)) && isNumeric(tokens.get(idx + 3))
                            && isNumeric(tokens.get(idx + 4)) && isNumeric(tokens.get(idx + 5))) {
                        double x1 = Double.parseDouble(tokens.get(idx++));
                        double y1 = Double.parseDouble(tokens.get(idx++));
                        double x2 = Double.parseDouble(tokens.get(idx++));
                        double y2 = Double.parseDouble(tokens.get(idx++));
                        double x = Double.parseDouble(tokens.get(idx++));
                        double y = Double.parseDouble(tokens.get(idx++));

                        if (isRelative) {
                            x1 += curX; y1 += curY;
                            x2 += curX; y2 += curY;
                            x += curX; y += curY;
                        }

                        if (currentStroke == null) {
                            currentStroke = new VectorStroke();
                            currentStroke.setTag("SVG");
                            currentStroke.addPoint(curX, curY);
                        }

                        discretizeCubicBezier(curX, curY, x1, y1, x2, y2, x, y, currentStroke);

                        curX = x;
                        curY = y;
                        lastCtrlX = x2;
                        lastCtrlY = y2;
                    }
                    lastCmd = cmd;
                    break;
                }
                case 'S': { // Smooth Cubic Bézier (x2, y2, x, y)
                    while (idx + 3 < tokens.size() && isNumeric(tokens.get(idx)) && isNumeric(tokens.get(idx + 1))
                            && isNumeric(tokens.get(idx + 2)) && isNumeric(tokens.get(idx + 3))) {
                        double x2 = Double.parseDouble(tokens.get(idx++));
                        double y2 = Double.parseDouble(tokens.get(idx++));
                        double x = Double.parseDouble(tokens.get(idx++));
                        double y = Double.parseDouble(tokens.get(idx++));

                        if (isRelative) {
                            x2 += curX; y2 += curY;
                            x += curX; y += curY;
                        }

                        // Reflection of last control point
                        double x1 = (Character.toUpperCase(lastCmd) == 'C' || Character.toUpperCase(lastCmd) == 'S')
                                ? (2 * curX - lastCtrlX) : curX;
                        double y1 = (Character.toUpperCase(lastCmd) == 'C' || Character.toUpperCase(lastCmd) == 'S')
                                ? (2 * curY - lastCtrlY) : curY;

                        if (currentStroke == null) {
                            currentStroke = new VectorStroke();
                            currentStroke.setTag("SVG");
                            currentStroke.addPoint(curX, curY);
                        }

                        discretizeCubicBezier(curX, curY, x1, y1, x2, y2, x, y, currentStroke);

                        curX = x;
                        curY = y;
                        lastCtrlX = x2;
                        lastCtrlY = y2;
                    }
                    lastCmd = cmd;
                    break;
                }
                case 'Q': { // Quadratic Bézier (x1, y1, x, y)
                    while (idx + 3 < tokens.size() && isNumeric(tokens.get(idx)) && isNumeric(tokens.get(idx + 1))
                            && isNumeric(tokens.get(idx + 2)) && isNumeric(tokens.get(idx + 3))) {
                        double x1 = Double.parseDouble(tokens.get(idx++));
                        double y1 = Double.parseDouble(tokens.get(idx++));
                        double x = Double.parseDouble(tokens.get(idx++));
                        double y = Double.parseDouble(tokens.get(idx++));

                        if (isRelative) {
                            x1 += curX; y1 += curY;
                            x += curX; y += curY;
                        }

                        if (currentStroke == null) {
                            currentStroke = new VectorStroke();
                            currentStroke.setTag("SVG");
                            currentStroke.addPoint(curX, curY);
                        }

                        discretizeQuadBezier(curX, curY, x1, y1, x, y, currentStroke);

                        curX = x;
                        curY = y;
                        lastCtrlX = x1;
                        lastCtrlY = y1;
                    }
                    lastCmd = cmd;
                    break;
                }
                case 'T': { // Smooth Quadratic Bézier (x, y)
                    while (idx + 1 < tokens.size() && isNumeric(tokens.get(idx)) && isNumeric(tokens.get(idx + 1))) {
                        double x = Double.parseDouble(tokens.get(idx++));
                        double y = Double.parseDouble(tokens.get(idx++));

                        if (isRelative) {
                            x += curX; y += curY;
                        }

                        double x1 = (Character.toUpperCase(lastCmd) == 'Q' || Character.toUpperCase(lastCmd) == 'T')
                                ? (2 * curX - lastCtrlX) : curX;
                        double y1 = (Character.toUpperCase(lastCmd) == 'Q' || Character.toUpperCase(lastCmd) == 'T')
                                ? (2 * curY - lastCtrlY) : curY;

                        if (currentStroke == null) {
                            currentStroke = new VectorStroke();
                            currentStroke.setTag("SVG");
                            currentStroke.addPoint(curX, curY);
                        }

                        discretizeQuadBezier(curX, curY, x1, y1, x, y, currentStroke);

                        curX = x;
                        curY = y;
                        lastCtrlX = x1;
                        lastCtrlY = y1;
                    }
                    lastCmd = cmd;
                    break;
                }
                case 'A': { // Elliptical Arc (rx, ry, x-rot, large-arc, sweep, x, y)
                    while (idx + 6 < tokens.size() && isNumeric(tokens.get(idx)) && isNumeric(tokens.get(idx + 1))
                            && isNumeric(tokens.get(idx + 2)) && isNumeric(tokens.get(idx + 3))
                            && isNumeric(tokens.get(idx + 4)) && isNumeric(tokens.get(idx + 5))
                            && isNumeric(tokens.get(idx + 6))) {
                        double rx = Math.abs(Double.parseDouble(tokens.get(idx++)));
                        double ry = Math.abs(Double.parseDouble(tokens.get(idx++)));
                        double rot = Double.parseDouble(tokens.get(idx++));
                        boolean largeArc = Double.parseDouble(tokens.get(idx++)) != 0;
                        boolean sweep = Double.parseDouble(tokens.get(idx++)) != 0;
                        double x = Double.parseDouble(tokens.get(idx++));
                        double y = Double.parseDouble(tokens.get(idx++));

                        if (isRelative) {
                            x += curX;
                            y += curY;
                        }

                        if (currentStroke == null) {
                            currentStroke = new VectorStroke();
                            currentStroke.setTag("SVG");
                            currentStroke.addPoint(curX, curY);
                        }

                        discretizeArc(curX, curY, rx, ry, rot, largeArc, sweep, x, y, currentStroke);

                        curX = x;
                        curY = y;
                        lastCtrlX = curX;
                        lastCtrlY = curY;
                    }
                    lastCmd = cmd;
                    break;
                }
                case 'Z': { // Close path
                    if (currentStroke != null) {
                        currentStroke.addPoint(startX, startY);
                        curX = startX;
                        curY = startY;
                        lastCtrlX = curX;
                        lastCtrlY = curY;
                        strokes.add(currentStroke);
                        currentStroke = null;
                    }
                    lastCmd = cmd;
                    break;
                }
                default:
                    idx++;
                    break;
            }
        }

        if (currentStroke != null && !currentStroke.isEmpty()) {
            strokes.add(currentStroke);
        }

        return strokes;
    }

    // =========================================================================
    // CURVE DISCRETIZATION & DE CASTELJAU SUBDIVISION
    // =========================================================================

    private void discretizeCubicBezier(double x0, double y0, double x1, double y1,
                                       double x2, double y2, double x3, double y3,
                                       VectorStroke stroke) {
        // Approximate length of control polygon
        double len = Math.hypot(x1 - x0, y1 - y0) + Math.hypot(x2 - x1, y2 - y1) + Math.hypot(x3 - x2, y3 - y2);
        int steps = Math.max(6, Math.min(64, (int) Math.ceil(len / 0.8)));

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double u = 1.0 - t;
            double tt = t * t;
            double uu = u * u;
            double uuu = uu * u;
            double ttt = tt * t;

            double x = uuu * x0 + 3 * uu * t * x1 + 3 * u * tt * x2 + ttt * x3;
            double y = uuu * y0 + 3 * uu * t * y1 + 3 * u * tt * y2 + ttt * y3;
            stroke.addPoint(x, y);
        }
    }

    private void discretizeQuadBezier(double x0, double y0, double x1, double y1,
                                      double x2, double y2, VectorStroke stroke) {
        double len = Math.hypot(x1 - x0, y1 - y0) + Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(4, Math.min(48, (int) Math.ceil(len / 0.8)));

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double u = 1.0 - t;
            double x = u * u * x0 + 2 * u * t * x1 + t * t * x2;
            double y = u * u * y0 + 2 * u * t * y1 + t * t * y2;
            stroke.addPoint(x, y);
        }
    }

    private void discretizeArc(double x0, double y0, double rx, double ry, double rotDeg,
                               boolean largeArc, boolean sweep, double x1, double y1,
                               VectorStroke stroke) {
        if (rx == 0 || ry == 0) {
            stroke.addPoint(x1, y1);
            return;
        }

        double rad = Math.toRadians(rotDeg % 360.0);
        double cosRad = Math.cos(rad);
        double sinRad = Math.sin(rad);

        double dx = (x0 - x1) / 2.0;
        double dy = (y0 - y1) / 2.0;
        double x1p = cosRad * dx + sinRad * dy;
        double y1p = -sinRad * dx + cosRad * dy;

        double prx = rx * rx;
        double pry = ry * ry;
        double px1 = x1p * x1p;
        double py1 = y1p * y1p;

        // Check if radii are large enough
        double radiiCheck = px1 / prx + py1 / pry;
        if (radiiCheck > 1.0) {
            rx *= Math.sqrt(radiiCheck);
            ry *= Math.sqrt(radiiCheck);
            prx = rx * rx;
            pry = ry * ry;
        }

        double sign = (largeArc == sweep) ? -1.0 : 1.0;
        double sq = ((prx * pry) - (prx * py1) - (pry * px1)) / ((prx * py1) + (pry * px1));
        sq = Math.max(0.0, sq);
        double coef = sign * Math.sqrt(sq);
        double cxp = coef * ((rx * y1p) / ry);
        double cyp = coef * (-(ry * x1p) / rx);

        double cx = cosRad * cxp - sinRad * cyp + (x0 + x1) / 2.0;
        double cy = sinRad * cxp + cosRad * cyp + (y0 + y1) / 2.0;

        double ux = (x1p - cxp) / rx;
        double uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx;
        double vy = (-y1p - cyp) / ry;

        double n = Math.sqrt(ux * ux + uy * uy);
        double p = ux; // dot with (1, 0)
        sign = (uy < 0) ? -1.0 : 1.0;
        double startAngle = sign * Math.acos(p / n);

        n = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        p = ux * vx + uy * vy;
        sign = (ux * vy - uy * vx < 0) ? -1.0 : 1.0;
        double deltaAngle = sign * Math.acos(p / n);

        if (!sweep && deltaAngle > 0) {
            deltaAngle -= 2 * Math.PI;
        } else if (sweep && deltaAngle < 0) {
            deltaAngle += 2 * Math.PI;
        }

        int steps = Math.max(6, (int) Math.ceil(Math.abs(deltaAngle) / (Math.PI / 12.0)));
        for (int i = 1; i <= steps; i++) {
            double angle = startAngle + deltaAngle * ((double) i / steps);
            double ex = rx * Math.cos(angle);
            double ey = ry * Math.sin(angle);
            double ptX = cosRad * ex - sinRad * ey + cx;
            double ptY = sinRad * ex + cosRad * ey + cy;
            stroke.addPoint(ptX, ptY);
        }
    }

    private VectorStroke discretizeEllipse(double cx, double cy, double rx, double ry) {
        VectorStroke vs = new VectorStroke();
        vs.setTag("SVG");
        int steps = 36;
        for (int i = 0; i <= steps; i++) {
            double angle = (2 * Math.PI * i) / steps;
            double px = cx + rx * Math.cos(angle);
            double py = cy + ry * Math.sin(angle);
            vs.addPoint(px, py);
        }
        return vs;
    }

    private VectorStroke parsePointsList(String pointsStr, boolean close) {
        VectorStroke vs = new VectorStroke();
        vs.setTag("SVG");
        if (pointsStr == null || pointsStr.trim().isEmpty()) return vs;

        String[] tokens = pointsStr.trim().split("[,\\s]+");
        for (int i = 0; i + 1 < tokens.length; i += 2) {
            try {
                double x = Double.parseDouble(tokens[i]);
                double y = Double.parseDouble(tokens[i + 1]);
                vs.addPoint(x, y);
            } catch (NumberFormatException ignored) {}
        }
        if (close && !vs.isEmpty() && vs.getPoints().size() > 2) {
            Point2D first = vs.getPoints().get(0);
            vs.addPoint(first.getX(), first.getY());
        }
        return vs;
    }

    // =========================================================================
    // SVG TOKENIZER & ATTRIBUTE HELPERS
    // =========================================================================

    private List<String> tokenizePath(String d) {
        List<String> tokens = new ArrayList<>();
        // Matches commands or floating-point numbers including negatives and scientific notation
        Pattern p = Pattern.compile("([MmLlHhVvCcSsQqTtAaZz])|([+-]?(?:\\d+\\.\\d+|\\.\\d+|\\d+)(?:[eE][+-]?\\d+)?)");
        Matcher m = p.matcher(d);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        char c = str.charAt(0);
        return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.';
    }

    private Double getAttrDouble(String tag, String attrName) {
        Pattern p = Pattern.compile("\\b" + attrName + "=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(tag);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1).replaceAll("[^0-9.-]", ""));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private double getAttrDouble(String tag, String attrName, double defaultValue) {
        Double val = getAttrDouble(tag, attrName);
        return val != null ? val : defaultValue;
    }
}
