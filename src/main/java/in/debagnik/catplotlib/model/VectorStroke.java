package in.debagnik.catplotlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorStroke {
    @Builder.Default
    private List<Point2D> points = new ArrayList<>();
    private String tag; // "TEXT", "HEADING", "SVG", "BORDER", "LIST"

    public VectorStroke(List<Point2D> points) {
        this.points = points != null ? points : new ArrayList<>();
    }

    public void addPoint(double x, double y) {
        if (points == null) {
            points = new ArrayList<>();
        }
        points.add(new Point2D(x, y));
    }

    public void addPoint(Point2D pt) {
        if (points == null) {
            points = new ArrayList<>();
        }
        points.add(pt);
    }

    public boolean isEmpty() {
        return points == null || points.size() < 2;
    }

    public double calculateLength() {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double length = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            length += points.get(i).distanceTo(points.get(i + 1));
        }
        return length;
    }
}
