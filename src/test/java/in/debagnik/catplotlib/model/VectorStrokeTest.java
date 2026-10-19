package in.debagnik.catplotlib.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@DisplayName("VectorStroke Unit Tests")
class VectorStrokeTest {

    @Test
    @DisplayName("Empty check should return true when points are null, empty, or single point")
    void testIsEmpty() {
        VectorStroke vsNull = new VectorStroke();
        vsNull.setPoints(null);
        assertThat(vsNull.isEmpty()).isTrue();

        VectorStroke vsZero = new VectorStroke();
        assertThat(vsZero.isEmpty()).isTrue();

        VectorStroke vsOne = new VectorStroke();
        vsOne.addPoint(5.0, 5.0);
        assertThat(vsOne.isEmpty()).isTrue();

        VectorStroke vsTwo = new VectorStroke();
        vsTwo.addPoint(0.0, 0.0);
        vsTwo.addPoint(10.0, 0.0);
        assertThat(vsTwo.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Calculate length accurately sums Euclidean distance across multi-point polyline")
    void testCalculateLength() {
        VectorStroke vs = new VectorStroke();
        assertThat(vs.calculateLength()).isEqualTo(0.0);

        vs.addPoint(0.0, 0.0);
        assertThat(vs.calculateLength()).isEqualTo(0.0);

        vs.addPoint(3.0, 0.0);
        assertThat(vs.calculateLength()).isEqualTo(3.0, offset(1e-9));

        vs.addPoint(3.0, 4.0);
        // (0,0)->(3,0) is 3, (3,0)->(3,4) is 4 => total length 7
        assertThat(vs.calculateLength()).isEqualTo(7.0, offset(1e-9));

        vs.addPoint(new Point2D(0.0, 0.0));
        // (3,4)->(0,0) is 5 => total length 12
        assertThat(vs.calculateLength()).isEqualTo(12.0, offset(1e-9));
    }

    @Test
    @DisplayName("Adding points with null point list initializes list properly")
    void testAddPointWithNullList() {
        VectorStroke vs = new VectorStroke();
        vs.setPoints(null);
        vs.addPoint(1.0, 2.0);
        assertThat(vs.getPoints()).hasSize(1);

        vs.setPoints(null);
        vs.addPoint(new Point2D(3.0, 4.0));
        assertThat(vs.getPoints()).hasSize(1);
    }

    @Test
    @DisplayName("Constructors, builder, and tag assignments")
    void testTagAndBuilder() {
        VectorStroke vs = VectorStroke.builder()
                .tag("BORDER")
                .points(new ArrayList<>())
                .build();
        vs.addPoint(0.0, 0.0);
        vs.addPoint(1.0, 1.0);

        assertThat(vs.getTag()).isEqualTo("BORDER");
        assertThat(vs.getPoints()).hasSize(2);

        VectorStroke fromList = new VectorStroke(vs.getPoints());
        assertThat(fromList.getPoints()).hasSize(2);
    }
}
