package in.debagnik.plottercraft.controller;

import in.debagnik.plottercraft.model.PlotterJobRequest;
import in.debagnik.plottercraft.model.PlotterJobResponse;
import in.debagnik.plottercraft.service.PlotterPipelineService;
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
public class PlotterController {

    private final PlotterPipelineService pipelineService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "PlotterCraft - Bambu Lab A1 Plotter Studio");
        return "index";
    }

    @PostMapping("/api/generate-gcode")
    @ResponseBody
    public ResponseEntity<PlotterJobResponse> generateGcode(@RequestBody PlotterJobRequest request) {
        log.info("Received G-code generation request with markdown length {}",
                request.getMarkdown() != null ? request.getMarkdown().length() : 0);
        PlotterJobResponse response = pipelineService.processJob(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/api/generate-gcode-multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<PlotterJobResponse> generateGcodeMultipart(
            @RequestParam("markdown") String markdown,
            @RequestParam(value = "feedrateDraw", defaultValue = "1400.0") double feedrateDraw,
            @RequestParam(value = "feedrateTravel", defaultValue = "3500.0") double feedrateTravel,
            @RequestParam(value = "zUp", defaultValue = "3.0") double zUp,
            @RequestParam(value = "zDown", defaultValue = "0.0") double zDown,
            @RequestParam(value = "jitterEnabled", defaultValue = "true") boolean jitterEnabled,
            @RequestParam(value = "jitterIntensity", defaultValue = "0.15") double jitterIntensity,
            @RequestParam(value = "svgFiles", required = false) MultipartFile[] svgFiles) {

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
    public ResponseEntity<Map<String, Object>> getSamples() {
        return ResponseEntity.ok(pipelineService.getSampleAssignments());
    }

    @PostMapping("/api/download-gcode")
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
