package in.debagnik.catplotlib.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlotterProperties Unit Tests")
class PlotterPropertiesTest {

    @Test
    @DisplayName("Default properties correspond to A4 boundary on Bambu Lab A1 and -35mm nozzle offset")
    void testDefaultValues() {
        PlotterProperties props = new PlotterProperties();

        assertThat(props.getBoundaryWidth()).isEqualTo(210.0);
        assertThat(props.getBoundaryLength()).isEqualTo(256.0);
        assertThat(props.getBedWidth()).isEqualTo(256.0);
        assertThat(props.getBedLength()).isEqualTo(256.0);
        assertThat(props.getNozzleOffsetX()).isEqualTo(0.0);
        assertThat(props.getNozzleOffsetY()).isEqualTo(-35.0);
    }

    @Test
    @DisplayName("Nested properties override flat default values if specified")
    void testNestedPropertiesOverride() {
        PlotterProperties props = new PlotterProperties();
        props.setBoundary(new PlotterProperties.Boundary(180.0, 240.0));
        props.setBed(new PlotterProperties.Bed(300.0, 300.0));
        props.setNozzle(new PlotterProperties.Nozzle(2.5, -42.0));

        assertThat(props.getBoundaryWidth()).isEqualTo(180.0);
        assertThat(props.getBoundaryLength()).isEqualTo(240.0);
        assertThat(props.getBedWidth()).isEqualTo(300.0);
        assertThat(props.getBedLength()).isEqualTo(300.0);
        assertThat(props.getNozzleOffsetX()).isEqualTo(2.5);
        assertThat(props.getNozzleOffsetY()).isEqualTo(-42.0);
    }

    @Test
    @DisplayName("Builder pattern creates populated PlotterProperties correctly")
    void testBuilder() {
        PlotterProperties props = PlotterProperties.builder()
                .boundaryWidth(200.0)
                .boundaryLength(250.0)
                .bedWidth(256.0)
                .bedLength(256.0)
                .nozzleOffsetX(1.0)
                .nozzleOffsetY(-35.0)
                .build();

        assertThat(props.getBoundaryWidth()).isEqualTo(200.0);
        assertThat(props.getBoundaryLength()).isEqualTo(250.0);
        assertThat(props.getNozzleOffsetX()).isEqualTo(1.0);
        assertThat(props.getNozzleOffsetY()).isEqualTo(-35.0);
    }
}
