package in.debagnik.catplotlib.controller;

import in.debagnik.catplotlib.model.PlotterJobRequest;
import in.debagnik.catplotlib.model.PlotterJobResponse;
import in.debagnik.catplotlib.service.PlotterPipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@Tag(name = "Plotter Engine", description = "Endpoints for generating safe Bambu Lab A1 G-code, toolpath simulation moves, and downloads")
public class PlotterController {

    private final PlotterPipelineService pipelineService;

    @GetMapping("/")
    @Operation(summary = "Render Studio UI", description = "Serves the main interactive CatPlotLib web studio interface.")
    public String index(Model model) {
        model.addAttribute("title", "CatPlotLib - Bambu Lab A1 Plotter Studio");
        return "index";
    }

    @PostMapping("/api/generate-gcode")
    @ResponseBody
    @Operation(summary = "Generate G-Code & 3D Toolpaths (JSON)", description = "Processes Markdown and embedded SVGs to produce Bambu Lab A1 safe G-code and 3D toolpath moves.")
    public ResponseEntity<PlotterJobResponse> generateGcode(@RequestBody PlotterJobRequest request) {
        log.info("Received G-code generation request with markdown length {}",
                request.getMarkdown() != null ? request.getMarkdown().length() : 0);
        PlotterJobResponse response = pipelineService.processJob(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/api/generate-gcode-multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @Operation(summary = "Generate G-Code & 3D Toolpaths (Multipart)", description = "Accepts raw SVG file uploads alongside kinematic parameters and Markdown text.")
    public ResponseEntity<PlotterJobResponse> generateGcodeMultipart(
            @Parameter(description = "Markdown text content") @RequestParam("markdown") String markdown,
            @Parameter(description = "Drawing feedrate in mm/min") @RequestParam(value = "feedrateDraw", defaultValue = "1400.0") double feedrateDraw,
            @Parameter(description = "Travel transit feedrate in mm/min") @RequestParam(value = "feedrateTravel", defaultValue = "3500.0") double feedrateTravel,
            @Parameter(description = "Pen Up Z clearance in mm") @RequestParam(value = "zUp", defaultValue = "3.0") double zUp,
            @Parameter(description = "Pen Down drawing surface Z in mm") @RequestParam(value = "zDown", defaultValue = "0.0") double zDown,
            @Parameter(description = "Enable natural handwriting jitter") @RequestParam(value = "jitterEnabled", defaultValue = "true") boolean jitterEnabled,
            @Parameter(description = "Jitter intensity factor") @RequestParam(value = "jitterIntensity", defaultValue = "0.15") double jitterIntensity,
            @Parameter(description = "Attached SVG diagram files") @RequestParam(value = "svgFiles", required = false) MultipartFile[] svgFiles) {

        Map<String, String> svgMap = new HashMap<>();
        if (svgFiles != null) {
            for (MultipartFile file : svgFiles) {
                if (!file.isEmpty() && file.getOriginalFilename() != null) {
                    try {
                        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                        svgMap.put(file.getOriginalFilename(), content);
                    } catch (Exception e) {
                        log.warn("Failed to read uploaded SVG file: {}", file.getOriginalFilename(), e);
                    }
                }
            }
        }

        PlotterJobRequest req = PlotterJobRequest.builder()
                .markdown(markdown)
                .svgFiles(svgMap)
                .feedrateDraw(feedrateDraw)
                .feedrateTravel(feedrateTravel)
                .zUp(zUp)
                .zDown(zDown)
                .jitterEnabled(jitterEnabled)
                .jitterIntensity(jitterIntensity)
                .pageWidth(256.0)
                .pageHeight(256.0)
                .build();

        PlotterJobResponse response = pipelineService.processJob(req);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/samples")
    @ResponseBody
    @Operation(summary = "Get Sample Assignments", description = "Retrieves preloaded demo assignments (Physics RLC, Math Curves) with bundled SVGs.")
    public ResponseEntity<Map<String, Object>> getSamples() {
        return ResponseEntity.ok(pipelineService.getSampleAssignments());
    }

    @PostMapping("/api/download-gcode")
    @Operation(summary = "Download G-Code File", description = "Streams a downloadable .gcode file with proper Content-Disposition header.")
    public ResponseEntity<byte[]> downloadGcode(@RequestBody Map<String, String> payload) {
        String gcode = payload.getOrDefault("gcode", "; Empty G-code");
        String filename = payload.getOrDefault("filename", "assignment_plotter.gcode");
        if (!filename.endsWith(".gcode")) {
            filename += ".gcode";
        }

        byte[] bytes = gcode.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(bytes);
    }
}
