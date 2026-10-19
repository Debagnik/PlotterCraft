package in.debagnik.catplotlib.service;

import in.debagnik.catplotlib.model.*;
import in.debagnik.catplotlib.service.GcodeGeneratorService.GcodeGenerationResult;
import in.debagnik.catplotlib.service.MarkdownParserService.LayoutResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates the full pipeline:
 * Markdown + SVG -> Vector Strokes -> G-code & 3D Slicer Toolpaths.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlotterPipelineService {

    private final MarkdownParserService markdownParserService;
    private final GcodeGeneratorService gcodeGeneratorService;

    public PlotterJobResponse processJob(PlotterJobRequest request) {
        try {
            // Apply sane defaults if omitted
            if (request.getPageWidth() <= 0) request.setPageWidth(256.0);
            if (request.getPageHeight() <= 0) request.setPageHeight(256.0);
            if (request.getFeedrateDraw() <= 0) request.setFeedrateDraw(1400.0);
            if (request.getFeedrateTravel() <= 0) request.setFeedrateTravel(3500.0);
            if (request.getFontScale() <= 0) request.setFontScale(1.0);
            if (request.getLineSpacing() <= 0) request.setLineSpacing(1.4);

            // Step 1: Layout Markdown & Stylus SVG Diagrams into Vector Strokes
            LayoutResult layout = markdownParserService.layoutDocument(request);

            // Step 2: Generate Safe Bambu Lab A1 G-code and 3D Toolpath Moves
            GcodeGenerationResult gcodeResult = gcodeGeneratorService.generateGcode(layout.strokes, request);

            return PlotterJobResponse.builder()
                    .success(true)
                    .message("G-code and toolpaths generated successfully.")
                    .gcode(gcodeResult.gcode)
                    .moves(gcodeResult.moves)
                    .statistics(gcodeResult.statistics)
                    .bounds(layout.bounds)
                    .warnings(layout.warnings)
                    .build();

        } catch (Exception e) {
            log.error("Error processing plotter job", e);
            return PlotterJobResponse.builder()
                    .success(false)
                    .message("Failed to generate G-code: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Provides rich pre-built sample assignments with embedded SVG diagrams.
     */
    public Map<String, Object> getSampleAssignments() {
        Map<String, Object> samples = new HashMap<>();

        // Sample 1: Physics Assignment with RLC Circuit Diagram
        String physicsMarkdown = """
# Physics 201: RLC Resonant Circuits
**Name:** Rak Kingabed  |  **Date:** 29th, Feb 2000

## 1. Theoretical Background
An RLC circuit is an electrical circuit consisting of a resistor ($R$), an inductor ($L$), and a capacitor ($C$), connected in series or in parallel.

The natural angular resonant frequency $\\omega_0$ is given by:
> $\\omega_0 = \\frac{1}{\\sqrt{LC}}$ rad/s  |  $f_0 = \\frac{\\omega_0}{2\\pi}$

## 2. Experimental Circuit Schematic
The diagram below illustrates the series configuration tested on the laboratory bench:

![Series RLC Circuit](circuit.svg)

## 3. Measured Experimental Data
* Resistor ($R$): 220.0 $\\Omega \\pm 1\\%$
* Inductor ($L$): 47.0 $\\mu$H $\\pm 5\\%$
* Capacitor ($C$): 10.0 nF $\\pm 2\\%$
* Calculated resonance frequency $f_0 \\approx 232.1$ kHz.

> **Safety Warning:** Ensure oscilloscope ground clip is connected to circuit reference ground before powering AC source.
""";

        String circuitSvg = """
<svg viewBox="0 0 320 120" xmlns="http://www.w3.org/2000/svg">
  <!-- AC Source Circle -->
  <circle cx="40" cy="60" r="22" />
  <!-- AC Sine Wave inside -->
  <path d="M 28 60 Q 34 46, 40 60 T 52 60" />
  <!-- Top Wire -->
  <line x1="40" y1="38" x2="40" y2="20" />
  <line x1="40" y1="20" x2="90" y2="20" />
  <!-- Resistor Zigzag -->
  <path d="M 90 20 L 96 10 L 108 30 L 120 10 L 132 30 L 144 10 L 150 20" />
  <!-- Wire between Resistor and Inductor -->
  <line x1="150" y1="20" x2="180" y2="20" />
  <!-- Inductor Coils -->
  <path d="M 180 20 A 8 8 0 0 1 196 20 A 8 8 0 0 1 212 20 A 8 8 0 0 1 228 20 A 8 8 0 0 1 244 20" />
  <!-- Wire to Right Corner -->
  <line x1="244" y1="20" x2="280" y2="20" />
  <line x1="280" y1="20" x2="280" y2="48" />
  <!-- Capacitor Parallel Plates -->
  <line x1="265" y1="48" x2="295" y2="48" />
  <line x1="265" y1="58" x2="295" y2="58" />
  <!-- Bottom Wire back to Source -->
  <line x1="280" y1="58" x2="280" y2="100" />
  <line x1="280" y1="100" x2="40" y2="100" />
  <line x1="40" y1="100" x2="40" y2="82" />
</svg>
""";

        // Sample 2: Calculus Assignment with Function Curve Diagram
        String mathMarkdown = """
# Mathematics 301: Definite Integrals
**Assignment:** Area Under Curve Analysis  |  **Score:** 100/100

## 1. Problem Formulation
Compute the area under the parabolic curve $f(x) = 4 - x^2$ bounded between the roots $x \\in [-2, 2]$.

Using the Fundamental Theorem of Calculus:
> $A = \\int_{-2}^{2} (4 - x^2) \\, dx = \\left[ 4x - \\frac{x^3}{3} \\right]_{-2}^{2} = \\frac{32}{3} \\approx 10.67$

## 2. Geometric Curve Representation
![Parabolic Curve](parabola.svg)

## 3. Observations & Key Properties
1. The function exhibits even symmetry: $f(-x) = f(x)$.
2. The peak occurs at vertex $(0, 4)$ where $f'(x) = -2x = 0$.
3. Total enclosed area matches Riemann sum limit as $\\Delta x \\to 0$.
""";

        String mathSvg = """
<svg viewBox="0 0 280 140" xmlns="http://www.w3.org/2000/svg">
  <!-- X and Y Axes -->
  <line x1="20" y1="110" x2="260" y2="110" />
  <line x1="140" y1="10" x2="140" y2="130" />
  <!-- Axis Arrows -->
  <path d="M 255 106 L 260 110 L 255 114" />
  <path d="M 136 15 L 140 10 L 144 15" />
  <!-- Parabola Curve C(x) -->
  <path d="M 50 110 Q 140 -30, 230 110" />
  <!-- Shading Hatch Lines -->
  <line x1="80" y1="110" x2="80" y2="65" />
  <line x1="100" y1="110" x2="100" y2="40" />
  <line x1="120" y1="110" x2="120" y2="22" />
  <line x1="140" y1="110" x2="140" y2="15" />
  <line x1="160" y1="110" x2="160" y2="22" />
  <line x1="180" y1="110" x2="180" y2="40" />
  <line x1="200" y1="110" x2="200" y2="65" />
</svg>
""";

        Map<String, String> sample1Svgs = Map.of("circuit.svg", circuitSvg);
        Map<String, String> sample2Svgs = Map.of("parabola.svg", mathSvg);

        samples.put("physics", Map.of(
                "title", "Physics 201: RLC Circuits",
                "markdown", physicsMarkdown,
                "svgFiles", sample1Svgs
        ));

        samples.put("calculus", Map.of(
                "title", "Math 301: Definite Integrals",
                "markdown", mathMarkdown,
                "svgFiles", sample2Svgs
        ));

        return samples;
    }
}
