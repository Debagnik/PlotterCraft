package in.debagnik.catplotlib;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.debagnik.catplotlib.controller.PlotterController;
import in.debagnik.catplotlib.model.BoundingBox;
import in.debagnik.catplotlib.model.PlotterJobRequest;
import in.debagnik.catplotlib.model.PlotterJobResponse;
import in.debagnik.catplotlib.model.Point2D;
import in.debagnik.catplotlib.model.VectorStroke;
import in.debagnik.catplotlib.service.PlotterPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("CatPlotLib Spring Boot Integration Tests")
class CatPlotLibTests {

	private MockMvc mockMvc;

	@Autowired
	private PlotterController plotterController;

	@Autowired
	private PlotterPipelineService pipelineService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(plotterController).build();
	}

	@Test
	@DisplayName("Spring Application Context loads all beans successfully")
	void contextLoads() {
		assertThat(pipelineService).isNotNull();
		assertThat(plotterController).isNotNull();
	}

	@Test
	@DisplayName("Full End-to-End Pipeline on Physics Sample with RLC SVG")
	void testPhysicsSampleJobE2E() {
		Map<String, Object> samples = pipelineService.getSampleAssignments();
		assertThat(samples).containsKey("physics");

		@SuppressWarnings("unchecked")
		Map<String, Object> physics = (Map<String, Object>) samples.get("physics");
		String md = (String) physics.get("markdown");
		@SuppressWarnings("unchecked")
		Map<String, String> svgs = (Map<String, String>) physics.get("svgFiles");

		PlotterJobRequest request = PlotterJobRequest.builder()
				.markdown(md)
				.svgFiles(svgs)
				.feedrateDraw(1400.0)
				.feedrateTravel(3500.0)
				.zUp(3.0)
				.zDown(0.0)
				.jitterEnabled(true)
				.jitterIntensity(0.15)
				.build();

		PlotterJobResponse response = pipelineService.processJob(request);

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getGcode()).contains("; CatPlotLib - Bambu Lab A1 Safe Plotter G-code");
		assertThat(response.getGcode()).contains("M104 S0");
		assertThat(response.getMoves()).isNotEmpty();
		assertThat(response.getStatistics()).isNotNull();
		assertThat(response.getStatistics().getTotalDrawDistanceMm()).isGreaterThan(0.0);
	}

	@Test
	@DisplayName("Full End-to-End Pipeline on Calculus Sample with Math Curve SVG")
	void testCalculusSampleJobE2E() {
		Map<String, Object> samples = pipelineService.getSampleAssignments();
		assertThat(samples).containsKey("calculus");

		@SuppressWarnings("unchecked")
		Map<String, Object> calculus = (Map<String, Object>) samples.get("calculus");
		String md = (String) calculus.get("markdown");
		@SuppressWarnings("unchecked")
		Map<String, String> svgs = (Map<String, String>) calculus.get("svgFiles");

		PlotterJobRequest request = PlotterJobRequest.builder()
				.markdown(md)
				.svgFiles(svgs)
				.jitterEnabled(false)
				.build();

		PlotterJobResponse response = pipelineService.processJob(request);

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getMoves()).isNotEmpty();
	}

	@Test
	@DisplayName("Full E2E exercise of all Markdown syntactic elements and SVG geometries")
	void testComprehensiveMarkdownAndSvgE2E() {
		String richSvg = """
				<svg viewBox="0 0 200 150">
				  <line x1="10" y1="10" x2="190" y2="10" />
				  <rect x="10" y="20" width="50" height="30" />
				  <circle cx="90" cy="35" r="15" />
				  <ellipse cx="150" cy="35" rx="20" ry="12" />
				  <polyline points="10,70 30,90 50,70" />
				  <polygon points="70,70 90,90 110,70" />
				  <path d="M 120 70 C 130 50, 150 50, 160 70 S 180 90, 190 70" />
				  <path d="M 10 110 Q 30 140, 50 110 T 90 110" />
				  <path d="M 110 110 A 15 15 0 0 1 140 110" />
				  <path d="m 150 110 l 10 10 h 10 v -10 z" />
				</svg>
				""";

		String comprehensiveMd = """
				# Comprehensive Heading 1
				## Section 2: Properties
				### Subsection 2.1: Details
				
				Paragraph with **bold**, *italic*, and `inline code`.
				Special characters: ±, ×, ÷, α, β, π, Ω, café.
				
				* Bullet point item 1
				* Bullet point item 2
				
				1. Ordered item 1
				2. Ordered item 2
				
				> Blockquote highlighting critical parameters
				
				```python
				def generate():
				    return "G-code"
				```
				
				---
				
				![Geometric Diagram](rich_diagram.svg)
				![Missing Diagram](missing.svg)
				""";

		PlotterJobRequest request = PlotterJobRequest.builder()
				.markdown(comprehensiveMd)
				.svgFiles(Map.of("rich_diagram.svg", richSvg))
				.pageWidth(256.0)
				.pageHeight(256.0)
				.feedrateDraw(1200.0)
				.feedrateTravel(3000.0)
				.zUp(4.0)
				.zDown(0.2)
				.jitterEnabled(true)
				.jitterIntensity(0.12)
				.build();

		PlotterJobResponse response = pipelineService.processJob(request);

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getGcode()).isNotEmpty();
		assertThat(response.getMoves()).isNotEmpty();
		assertThat(response.getBounds().isValid()).isTrue();
	}

	@Test
	@DisplayName("Live HTTP MockMvc requests to PlotterController endpoints")
	void testControllerLiveIntegration() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/samples"))
				.andExpect(status().isOk());

		PlotterJobRequest req = PlotterJobRequest.builder()
				.markdown("# Integration Test Heading\nTesting live controller.")
				.build();

		mockMvc.perform(post("/api/generate-gcode")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk());

		Map<String, String> downloadReq = Map.of(
				"gcode", "; Sample G-code\nG28\n",
				"filename", "integration_output"
		);

		mockMvc.perform(post("/api/download-gcode")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(downloadReq)))
				.andExpect(status().isOk());

		// Test multipart endpoint
		MockMultipartFile svgFile = new MockMultipartFile(
				"svgFiles", "circle.svg", "image/svg+xml",
				"<svg><circle cx='20' cy='20' r='10'/></svg>".getBytes(StandardCharsets.UTF_8)
		);
		plotterController.generateGcodeMultipart(
				"# Test Multipart", 1400.0, 3500.0, 3.0, 0.0, true, 0.15,
				new MockMultipartFile[]{svgFile}
		);
	}

	@Test
	@DisplayName("Model objects methods exercise")
	void testModelsE2E() {
		Point2D p1 = new Point2D(0.0, 0.0);
		Point2D p2 = new Point2D(3.0, 4.0);
		assertThat(p1.distanceTo(p2)).isEqualTo(5.0);
		assertThat(p1.copy()).isEqualTo(p1);

		VectorStroke vs = new VectorStroke();
		assertThat(vs.isEmpty()).isTrue();
		vs.addPoint(p1);
		vs.addPoint(p2);
		assertThat(vs.isEmpty()).isFalse();
		assertThat(vs.calculateLength()).isEqualTo(5.0);

		BoundingBox box = BoundingBox.empty();
		box.expandToInclude(10.0, 20.0);
		assertThat(box.isValid()).isTrue();

		try (var ctx = org.springframework.boot.SpringApplication.run(
				CatPlotLib.class,
				"--spring.main.web-application-type=none",
				"--spring.main.banner-mode=off",
				"--server.port=0")) {
			assertThat(ctx).isNotNull();
		}
	}
}
