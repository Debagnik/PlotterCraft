package in.debagnik.catplotlib.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BoundingBox Unit Tests")
class BoundingBoxTest {

    @Test
    @DisplayName("Empty bounding box should be invalid with zero dimensions")
    void testEmptyBoundingBox() {
        BoundingBox box = BoundingBox.empty();
        assertThat(box.isValid()).isFalse();
        assertThat(box.getWidth()).isEqualTo(0.0);
        assertThat(box.getHeight()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Expanding empty box with a single point makes it valid with zero width/height")
    void testSinglePointExpansion() {
        BoundingBox box = BoundingBox.empty();
        box.expandToInclude(10.5, 20.25);

        assertThat(box.isValid()).isTrue();
        assertThat(box.getMinX()).isEqualTo(10.5);
        assertThat(box.getMaxX()).isEqualTo(10.5);
        assertThat(box.getMinY()).isEqualTo(20.25);
        assertThat(box.getMaxY()).isEqualTo(20.25);
        assertThat(box.getWidth()).isEqualTo(0.0);
        assertThat(box.getHeight()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Expanding box with multiple points correctly updates bounds and dimensions")
    void testMultiplePointsExpansion() {
        BoundingBox box = BoundingBox.empty();
        box.expandToInclude(10.0, 5.0);
        box.expandToInclude(30.0, 25.0);
        box.expandToInclude(15.0, 12.0);

        assertThat(box.isValid()).isTrue();
        assertThat(box.getMinX()).isEqualTo(10.0);
        assertThat(box.getMaxX()).isEqualTo(30.0);
        assertThat(box.getMinY()).isEqualTo(5.0);
        assertThat(box.getMaxY()).isEqualTo(25.0);
        assertThat(box.getWidth()).isEqualTo(20.0);
        assertThat(box.getHeight()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("Handles negative coordinate space expansion properly")
    void testNegativeCoordinates() {
        BoundingBox box = BoundingBox.empty();
        box.expandToInclude(-50.0, -20.0);
        box.expandToInclude(-10.0, -5.0);

        assertThat(box.isValid()).isTrue();
        assertThat(box.getMinX()).isEqualTo(-50.0);
        assertThat(box.getMaxX()).isEqualTo(-10.0);
        assertThat(box.getMinY()).isEqualTo(-20.0);
        assertThat(box.getMaxY()).isEqualTo(-5.0);
        assertThat(box.getWidth()).isEqualTo(40.0);
        assertThat(box.getHeight()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("Lombok builder and constructors work properly")
    void testBuilderAndConstructors() {
        BoundingBox box = BoundingBox.builder()
                .minX(0.0)
                .minY(0.0)
                .maxX(100.0)
                .maxY(200.0)
                .build();

        assertThat(box.getMinX()).isEqualTo(0.0);
        assertThat(box.getMaxX()).isEqualTo(100.0);
        assertThat(box.getWidth()).isEqualTo(100.0);
        assertThat(box.getHeight()).isEqualTo(200.0);
        assertThat(box.isValid()).isTrue();

        BoundingBox noArg = new BoundingBox();
        assertThat(noArg).isNotNull();
    }
}
