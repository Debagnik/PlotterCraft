package in.debagnik.catplotlib.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catplotlibOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CatPlotLib API Reference")
                        .description("Interactive REST API documentation for CatPlotLib — Safe Bambu Lab A1 Plotter G-Code Generator, Slicer Toolpath Visualizer, and Centerline Hershey Vector Typography Engine.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Debagnik Kar")
                                .url("https://debagnik.in")
                                .email("debagnik@debagnik.in"))
                        .license(new License()
                                .name("WTFPL / Open Source")
                                .url("https://github.com/Debagnik/catplotlib")));
    }
}
