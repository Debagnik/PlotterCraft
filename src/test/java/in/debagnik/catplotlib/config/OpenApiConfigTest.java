package in.debagnik.catplotlib.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenApiConfig Unit Tests")
class OpenApiConfigTest {

    @Test
    @DisplayName("OpenAPI bean is properly configured with metadata")
    void testCatplotlibOpenAPIConfig() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.catplotlibOpenAPI();

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).contains("CatPlotLib API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(openAPI.getInfo().getContact()).isNotNull();
        assertThat(openAPI.getInfo().getLicense()).isNotNull();
    }
}
