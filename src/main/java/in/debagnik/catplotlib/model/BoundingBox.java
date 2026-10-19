package in.debagnik.catplotlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoundingBox {
    private double minX;
    private double minY;
    private double maxX;
    private double maxY;

    public double getWidth() {
        return Math.max(0.0, maxX - minX);
    }

    public double getHeight() {
        return Math.max(0.0, maxY - minY);
    }

    public void expandToInclude(double x, double y) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
    }

    public static BoundingBox empty() {
        return new BoundingBox(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
    }

    public boolean isValid() {
        return minX <= maxX && minY <= maxY && minX != Double.MAX_VALUE;
    }
}
