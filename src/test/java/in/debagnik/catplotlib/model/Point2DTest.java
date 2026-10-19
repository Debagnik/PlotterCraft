package in.debagnik.catplotlib.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@DisplayName("Point2D Unit Tests")
class Point2DTest {

    @Test
    @DisplayName("Distance to another point calculates accurate Euclidean distance")
    void testDistanceTo() {
        Point2D p1 = new Point2D(0.0, 0.0);
        Point2D p2 = new Point2D(3.0, 4.0);

        assertThat(p1.distanceTo(p2)).isEqualTo(5.0, offset(1e-9));
        assertThat(p2.distanceTo(p1)).isEqualTo(5.0, offset(1e-9));
        assertThat(p1.distanceTo(p1)).isEqualTo(0.0, offset(1e-9));
    }

    @Test
    @DisplayName("Copy produces an independent identical instance")
    void testCopy() {
        Point2D original = new Point2D(12.5, 34.8);
        Point2D copy = original.copy();

        assertThat(copy).isEqualTo(original);
        assertThat(copy).isNotSameAs(original);

        copy.setX(99.0);
        assertThat(original.getX()).isEqualTo(12.5);
        assertThat(copy.getX()).isEqualTo(99.0);
    }

    @Test
    @DisplayName("Constructors and setters work correctly")
    void testConstructorsAndSetters() {
        Point2D p = new Point2D();
        p.setX(1.5);
        p.setY(2.5);

        assertThat(p.getX()).isEqualTo(1.5);
        assertThat(p.getY()).isEqualTo(2.5);
        assertThat(p.toString()).contains("1.5", "2.5");
    }
}
