package in.debagnik.catplotlib.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized configuration properties for Bambu Lab A1 plotter hardware,
 * plotting boundaries (A4 paper size), and toolhead nozzle-to-pen physical offsets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Configuration
@ConfigurationProperties(prefix = "plotter")
public class PlotterProperties {

    /**
     * Plotting boundary width in mm (default 210.0 mm for standard A4 width).
     */
    @Builder.Default
    private double boundaryWidth = 210.0;

    /**
     * Plotting boundary length in mm (default 256.0 mm for usable A1 bed length).
     */
    @Builder.Default
    private double boundaryLength = 256.0;

    /**
     * Physical bed width in mm (Bambu Lab A1 bed width: 256.0 mm).
     */
    @Builder.Default
    private double bedWidth = 256.0;

    /**
     * Physical bed length in mm (Bambu Lab A1 bed length: 256.0 mm).
     */
    @Builder.Default
    private double bedLength = 256.0;

    /**
     * Physical toolhead nozzle offset along X axis in mm.
     */
    @Builder.Default
    private double nozzleOffsetX = 0.0;

    /**
     * Physical toolhead nozzle offset along -Y axis in mm (default -35.0 mm).
     */
    @Builder.Default
    private double nozzleOffsetY = -35.0;

    @Builder.Default
    private Boundary boundary = new Boundary();

    @Builder.Default
    private Nozzle nozzle = new Nozzle();

    @Builder.Default
    private Bed bed = new Bed();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Boundary {
        private Double width;
        private Double length;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Nozzle {
        private Double offsetX;
        private Double offsetY;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bed {
        private Double width;
        private Double length;
    }

    public double getBoundaryWidth() {
        if (boundary != null && boundary.getWidth() != null) return boundary.getWidth();
        return boundaryWidth;
    }

    public double getBoundaryLength() {
        if (boundary != null && boundary.getLength() != null) return boundary.getLength();
        return boundaryLength;
    }

    public double getBedWidth() {
        if (bed != null && bed.getWidth() != null) return bed.getWidth();
        return bedWidth;
    }

    public double getBedLength() {
        if (bed != null && bed.getLength() != null) return bed.getLength();
        return bedLength;
    }

    public double getNozzleOffsetX() {
        if (nozzle != null && nozzle.getOffsetX() != null) return nozzle.getOffsetX();
        return nozzleOffsetX;
    }

    public double getNozzleOffsetY() {
        if (nozzle != null && nozzle.getOffsetY() != null) return nozzle.getOffsetY();
        return nozzleOffsetY;
    }
}
