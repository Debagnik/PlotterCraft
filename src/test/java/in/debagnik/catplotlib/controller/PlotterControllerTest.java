package in.debagnik.catplotlib.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.debagnik.catplotlib.model.PlotterJobRequest;
import in.debagnik.catplotlib.model.PlotterJobResponse;
import in.debagnik.catplotlib.service.PlotterPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlotterController Web & API Tests")
class PlotterControllerTest {

    @Mock
    private PlotterPipelineService pipelineService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        PlotterController controller = new PlotterController(pipelineService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET / returns index view with title in model")
    void testIndexEndpoint() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("title"));
    }

    @Test
    @DisplayName("POST /api/generate-gcode processes JSON payload and returns response")
    void testGenerateGcodeJson() throws Exception {
        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown("# Test Title")
                .feedrateDraw(1400.0)
                .build();

        PlotterJobResponse mockResponse = PlotterJobResponse.builder()
                .success(true)
                .message("G-code generated")
                .gcode("G28\nG0 Z10\n")
                .build();

        when(pipelineService.processJob(any(PlotterJobRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/generate-gcode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("G-code generated"))
                .andExpect(jsonPath("$.gcode").value("G28\nG0 Z10\n"));
    }

    @Test
    @DisplayName("POST /api/generate-gcode-multipart accepts file uploads and form parameters")
    void testGenerateGcodeMultipart() throws Exception {
        MockMultipartFile svgFile = new MockMultipartFile(
                "svgFiles", "diagram.svg", "image/svg+xml",
                "<svg><circle cx='10' cy='10' r='5'/></svg>".getBytes(StandardCharsets.UTF_8)
        );

        PlotterJobResponse mockResponse = PlotterJobResponse.builder()
                .success(true)
                .message("Processed multipart job")
                .gcode("G28\n")
                .build();

        when(pipelineService.processJob(any(PlotterJobRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/generate-gcode-multipart")
                        .file(svgFile)
                        .param("markdown", "# Multipart Document\n![Diagram](diagram.svg)")
                        .param("feedrateDraw", "1200.0")
                        .param("feedrateTravel", "3000.0")
                        .param("zUp", "4.0")
                        .param("zDown", "0.1")
                        .param("jitterEnabled", "false")
                        .param("jitterIntensity", "0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.gcode").value("G28\n"));
    }

    @Test
    @DisplayName("GET /api/samples returns demo assignments")
    void testGetSamples() throws Exception {
        Map<String, Object> mockSamples = Map.of(
                "physics", Map.of("title", "Physics 201"),
                "calculus", Map.of("title", "Math 301")
        );

        when(pipelineService.getSampleAssignments()).thenReturn(mockSamples);

        mockMvc.perform(get("/api/samples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.physics.title").value("Physics 201"))
                .andExpect(jsonPath("$.calculus.title").value("Math 301"));
    }

    @Test
    @DisplayName("POST /api/download-gcode streams file with attachment header and auto-appends .gcode extension")
    void testDownloadGcode() throws Exception {
        Map<String, String> payload = Map.of(
                "gcode", "G28 ; Home\nM84 ; Off",
                "filename", "physics_assignment"
        );

        mockMvc.perform(post("/api/download-gcode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"physics_assignment.gcode\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("G28 ; Home\nM84 ; Off"));
    }

    @Test
    @DisplayName("POST /api/generate-gcode handles null markdown in request")
    void testGenerateGcodeWithNullMarkdown() throws Exception {
        PlotterJobRequest req = PlotterJobRequest.builder().markdown(null).build();
        PlotterJobResponse mockResponse = PlotterJobResponse.builder().success(true).build();
        when(pipelineService.processJob(any(PlotterJobRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/generate-gcode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/generate-gcode-multipart handles faulty upload and keeps existing .gcode extension in download")
    void testMultipartWithFaultyFileAndDownloadExistingExtension() throws Exception {
        org.springframework.web.multipart.MultipartFile faultyFile = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(faultyFile.isEmpty()).thenReturn(false);
        when(faultyFile.getOriginalFilename()).thenReturn("faulty.svg");
        when(faultyFile.getBytes()).thenThrow(new java.io.IOException("Disk read error"));

        PlotterJobResponse mockResponse = PlotterJobResponse.builder().success(true).build();
        when(pipelineService.processJob(any())).thenReturn(mockResponse);

        // Call controller method directly for mock multipart file throwing checked IOException
        PlotterController controller = new PlotterController(pipelineService);
        controller.generateGcodeMultipart(
                "# Test", 1400.0, 3500.0, 3.0, 0.0, true, 0.15, true, false,
                new org.springframework.web.multipart.MultipartFile[]{faultyFile}
        );

        // Also test download with already provided .gcode extension
        Map<String, String> payload = Map.of(
                "gcode", "G28",
                "filename", "already_has_ext.gcode"
        );
        mockMvc.perform(post("/api/download-gcode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"already_has_ext.gcode\""));
    }

    @Test
    @DisplayName("POST /api/generate-gcode-multipart passes flipY and flipX options correctly")
    void testMultipartWithFlipOptions() throws Exception {
        PlotterJobResponse mockResponse = PlotterJobResponse.builder().success(true).build();
        when(pipelineService.processJob(any())).thenReturn(mockResponse);

        MockMultipartFile file = new MockMultipartFile("svgFiles", "diagram.svg", "image/svg+xml",
                "<svg><circle cx='10' cy='10' r='5'/></svg>".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/generate-gcode-multipart")
                        .file(file)
                        .param("markdown", "# Flipping Test")
                        .param("flipY", "true")
                        .param("flipX", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
