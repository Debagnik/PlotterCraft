# CatPlotLib 🐾⚡

> **CatPlotLib** (Cat Plot Lib) converts Markdown documents with embedded stylus SVG diagrams into safe, collision-free Bambu Lab A1 plotter G-code, paired with an interactive in-browser 3D slicer-style toolpath preview and visualizer.

---

## 📖 Table of Contents
- [Why CatPlotLib?](#-why-catplotlib)
- [How It Works (Architecture & Pipeline)](#-how-it-works-architecture--pipeline)
- [Key Features](#-key-features)
- [Getting Started](#-getting-started)
- [Gradle Commands Reference (All Commands)](#-gradle-commands-reference)
- [Markdown & Stylus SVG Syntax](#-markdown--stylus-svg-syntax)
- [REST API Reference](#-rest-api-reference)
- [Hardware & Plotting Guide](#-hardware--plotting-guide)

---

## 🎯 Why CatPlotLib?

Converting a consumer 3D printer like the **Bambu Lab A1** into an ultra-precise pen plotter traditionally comes with major risks:
1. **Destructive Start Sequences**: Default 3D printer start macros heat up the hotend (burning pens), heat the bed, scrape the nozzle against metal wipe tabs, purge filament lines, and perform force-sensing bed taps that would bend or crush mounted pens.
2. **Hollow TTF Font Outlines**: Standard document printers stroke font perimeters, producing double-line hollow text instead of natural single-line handwriting.
3. **Complex SVG Curves**: Mathematical vector curves (cubic/quadratic Bézier curves) cannot be executed directly by 3D printer firmware without precise, adaptive arc/chord discretization.

**CatPlotLib** solves all of these challenges with an integrated pipeline designed specifically for pen plotting on the Bambu Lab A1.

---

## ⚙️ How It Works (Architecture & Pipeline)

CatPlotLib operates via a 6-stage end-to-end processing pipeline:

```
[Markdown + SVG Inputs]
          │
          ▼
┌─────────────────────────────────┐
│ 1. Markdown AST & Lexer        │  --> Parses text, headings, tables, lists, and SVG images
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 2. Hershey Vector Font Engine   │  --> Centerline single-stroke rendering + NFD diacritics
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 3. Stylus SVG Geometry Engine   │  --> Adaptive Bézier curve discretization (0.05mm tolerance)
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 4. 2D Layout & Line Wrapping    │  --> Automatic document flow, margins & 256x256mm bounds
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 5. Safe Bambu Lab A1 G-Code Gen │  --> Enforces zero heat, safe homing, pen up/down kinematic Z
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 6. Three.js Slicer Visualizer   │  --> Interactive 3D preview, timeline scrubber & telemetry
└─────────────────────────────────┘
```

### 1. Markdown AST & Content Parsing
The input assignment text is parsed using CommonMark Java. Headings (`#`, `##`, `###`), ordered/unordered lists, paragraphs, and GFM tables are transformed into an abstract document tree. Image references such as `![Circuit Schematic](circuit.svg)` are mapped directly to corresponding vector diagrams.

### 2. Single-Stroke Vector Typography (Hershey Font Engine)
Unlike standard TrueType/OpenType fonts that outline character perimeters, CatPlotLib generates authentic single-stroke centerlines:
- **Comprehensive UTF-8 Coverage**: Complete ASCII (32–126), Greek scientific symbols ($\alpha, \beta, \gamma, \delta, \pi, \sigma, \theta, \lambda, \Omega, \Delta, \Phi$), mathematical operators ($\pm, \times, \div, \le, \ge, \neq, \approx, \infty, \int, \sum, \sqrt{}, \partial$), and currencies ($€, £, ¥, $, ₹).
- **Unicode NFD Diacritic Decomposition**: Accented glyphs (`é, è, ê, ñ, ç, å, ö, ü, á, í, ó, ú`) are split into base Latin characters and combined with synthesized vector accent marks.
- **Human Handwriting Jitter**: An optional stochastic perturbation engine modulates baseline vertical position ($\pm 0.15\text{ mm}$), character slant ($\pm 2^\circ$), and horizontal pitch to recreate realistic handwritten penmanship.

### 3. Stylus SVG Vector Geometry Engine
Embedded SVG diagrams are parsed across paths and geometric primitives:
- Supported elements: `<path>`, `<line>`, `<rect>`, `<circle>`, `<ellipse>`, `<polyline>`, and `<polygon>`.
- Path commands supported: `M`, `L`, `H`, `V`, `C`, `S`, `Q`, `T`, `A`, and `Z`.
- Cubic and quadratic Bézier segments are discretized into linear micro-chords using adaptive chordal deviation ($0.05\text{ mm}$ default), ensuring curves render smoothly without faceting.
- Diagrams are auto-scaled to fit within margins and automatically advance the document's vertical flow.

### 4. 2D Layout & Bed Coordinate Mapping
The layout engine enforces margins and line heights relative to the Bambu Lab A1's physical print bed ($256 \times 256\text{ mm}$). Text wraps gracefully around margins, maintaining predictable pagination and preventing toolhead overtravel.

### 5. Safe Bambu Lab A1 G-Code Engine
The G-code emitter applies strict safety constraints:
- **Zero-Heat Guarantee**: Emits `M104 S0` (hotend off), `M140 S0` (bed off), `M106 S0` (part cooling fan off), `M106 P1 S0` (aux fan off), and `M106 P2 S0` (chamber fan off) in the start routine.
- **Zero Calibration Collisions**: Completely eliminates nozzle cleaning wipes, bed scrapers, purge lines, and probing sequences.
- **Homing Routine**: Runs a safe `G28` homing command and immediately commands `G0 Z10.0` for safe pen clearance.
- **Kinematic Control**: Differentiates between non-contact rapid travel moves (`G0` at $3500\text{ mm/min}$ with pen raised at $Z_{up}$) and active drawing moves (`G1` at $1400\text{ mm/min}$ with pen contacting paper at $Z_{down}$).
- **Finish Routine**: Raises the pen to $Z = 25\text{ mm}$, presents the bed forward to $Y = 240\text{ mm}$, and disables stepper motors (`M84`).

### 6. Interactive 3D Slicer Visualizer (Three.js)
The frontend visualizer renders the toolpath moves in a 3D environment modeled after Bambu Studio and PrusaSlicer:
- **Draw Paths ($G1$)**: Rendered as crisp royal blue strokes on the virtual build surface.
- **Travel Paths ($G0$)**: Rendered as dashed translucent red aerial vectors with vertical pen-lift lines.
- **Scrubbing & Simulation**: Timeline scrubber ($0\%$ to $100\%$) allows inspecting toolpaths move-by-move. Real-time playback allows previewing the simulated pen head at speeds from $1\times$ to $25\times$.
- **Camera Angles**: Switch between 3D Isometric View, Top-Down 2D Slicer View, and Front Z-Clearance View.
- **Telemetry**: Displays total draw distance, travel distance, estimated plotting time, and pen lift count.

---

## 🌟 Key Features

| Feature | Description |
|---|---|
| **Bambu Lab A1 Safe** | Guaranteed zero heaters, fans disabled, zero nozzle wipes or purge collisions. |
| **Centerline Vector Text** | True single-stroke vector fonts (Hershey font family) for natural pen plotting. |
| **Extended UTF-8 & Math** | Greek letters, mathematical operators, scientific symbols, and accents. |
| **Human Handwriting Jitter** | Configurable micro-jitter for authentic handwritten appearance. |
| **SVG Stylus Integration** | Embed SVG path diagrams with adaptive Bézier discretization. |
| **Interactive 3D Visualizer** | Three.js slicer toolpath visualizer with playback, scrubbing, and telemetry. |
| **One-Click G-Code Export** | Instant download of ready-to-run `.gcode` for SD card or Bambu Handy. |

---

## 🚀 Getting Started

### Prerequisites
- **Java 25 LTS** (or Java 21+)
- **Gradle 9.x** (Included via Gradle Wrapper: `gradlew.bat` / `gradlew`)
- A modern web browser with WebGL support

### Running CatPlotLib

#### On Windows (PowerShell / Command Prompt):
```powershell
.\gradlew.bat bootRun
```

#### On Linux / macOS:
```bash
./gradlew bootRun
```

Once started, open your browser and visit:
```
http://localhost:5000
```

---

## 📦 Gradle Commands Reference

Below is a complete reference of all Gradle commands used for development, building, testing, and managing CatPlotLib:

### 1. Running the Application
| Command | Description |
|---|---|
| `.\gradlew.bat bootRun` | Starts the CatPlotLib Spring Boot web application locally on port `5000`. |
| `.\gradlew.bat bootRun --continuous` | Starts the app with file watching; auto-recompiles on source changes. |
| `.\gradlew.bat bootRun --args='--server.port=8080'` | Runs the server on a custom port (e.g. port 8080). |
| `.\gradlew.bat bootRun --debug-jvm` | Starts the application with JVM remote debug listener on port `5005`. |

### 2. Building & Packaging
| Command | Description |
|---|---|
| `.\gradlew.bat build` | Compiles code, runs tests, and packages the complete application. |
| `.\gradlew.bat bootJar` | Builds an executable fat JAR in `build/libs/catplotlib-0.0.1-SNAPSHOT.jar`. |
| `.\gradlew.bat assemble` | Compiles and packages archives without running test verification. |
| `.\gradlew.bat assemble -x test` | Fast build bypassing all tests. |
| `.\gradlew.bat clean` | Deletes the `build` directory and removes all compiled outputs. |
| `.\gradlew.bat clean build` | Full clean build from scratch with test suite execution. |

### 3. Testing & Verification
| Command | Description |
|---|---|
| `.\gradlew.bat test` | Executes all unit and integration tests. |
| `.\gradlew.bat test --info` | Runs tests with detailed information and output logging. |
| `.\gradlew.bat test --stacktrace` | Runs tests and prints full stack traces on failures. |
| `.\gradlew.bat test --tests in.debagnik.catplotlib.CatPlotLibTests` | Runs only the specified test class. |
| `.\gradlew.bat test --tests in.debagnik.catplotlib.CatPlotLibTests.contextLoads` | Runs a specific test method. |
| `.\gradlew.bat check` | Executes all verification tasks including unit tests. |

### 4. Dependency & Project Inspection
| Command | Description |
|---|---|
| `.\gradlew.bat dependencies` | Displays the full dependency tree for all configurations. |
| `.\gradlew.bat dependencies --configuration runtimeClasspath` | Displays only runtime classpath dependencies. |
| `.\gradlew.bat dependencyInsight --dependency lombok` | Analyzes why a specific dependency is included and resolved. |
| `.\gradlew.bat tasks` | Lists all primary available Gradle tasks. |
| `.\gradlew.bat tasks --all` | Lists all runnable Gradle tasks including internal subtasks. |
| `.\gradlew.bat properties` | Displays all Gradle project properties and environment values. |

### 5. Gradle Daemon & Performance
| Command | Description |
|---|---|
| `.\gradlew.bat --status` | Lists all active and idle Gradle Daemons. |
| `.\gradlew.bat --stop` | Terminates all running Gradle Daemons to free memory. |
| `.\gradlew.bat build --refresh-dependencies` | Forces checking for updated remote dependencies. |
| `.\gradlew.bat build -m` | Dry run: prints tasks in execution order without running them. |
| `.\gradlew.bat build --scan` | Generates an official Gradle Build Scan with detailed performance diagnostics. |

---

## 📝 Markdown & Stylus SVG Syntax

### Markdown Text & Math
```markdown
# Physics 201: RLC Resonant Circuits
**Author:** Alex Morgan  |  **Date:** October 19, 2026

## 1. Experimental Circuit Schematic
The diagram below illustrates the series configuration tested on the laboratory bench:

![Series RLC Circuit](circuit.svg)

## 2. Theoretical Analysis
* Resonant frequency: $\omega_0 = 1 / \sqrt{LC}$ rad/s
* Resistor ($R$): 220.0 $\Omega \pm 1\%$
* Quality Factor: $Q = \frac{1}{R}\sqrt{\frac{L}{C}}$
```

### Embedding Custom SVGs
1. In the CatPlotLib web studio, click **"Upload SVG"** in the sidebar.
2. In your Markdown editor, reference the SVG filename:
   ```markdown
   ![Diagram Description](your_uploaded_diagram.svg)
   ```
3. CatPlotLib parses the SVG, converts all curves to toolpaths, and streams them into your document.

---

## 🛠️ REST API & Swagger UI Reference

CatPlotLib provides full **OpenAPI 3 / Swagger UI** documentation with interactive request execution directly in the browser:

- **Interactive Swagger UI**: [http://localhost:5000/swagger-ui.html](http://localhost:5000/swagger-ui.html)
- **OpenAPI 3 JSON Specification**: [http://localhost:5000/v3/api-docs](http://localhost:5000/v3/api-docs)

You can explore schemas, test endpoints with the interactive **"Try it out"** button, and view live response structures.

### Key Endpoints

#### 1. `POST /api/generate-gcode`
Generates safe G-code and 3D toolpath movements from a JSON payload.

**Request Body (`application/json`):**
```json
{
  "markdown": "# Title\nContent with math and symbols...",
  "svgFiles": {
    "circuit.svg": "<svg xmlns=...><path d=\"M 10 10 L 50 50\"/></svg>"
  },
  "feedrateDraw": 1400.0,
  "feedrateTravel": 3500.0,
  "zUp": 3.0,
  "zDown": 0.0,
  "jitterEnabled": true,
  "jitterIntensity": 0.15,
  "pageWidth": 256.0,
  "pageHeight": 256.0,
  "marginLeft": 15.0,
  "marginRight": 15.0
}
```

**Response Body (`application/json`):**
```json
{
  "success": true,
  "gcode": "; CatPlotLib - Bambu Lab A1 Safe Plotter G-code\n...",
  "moves": [
    {
      "type": "TRAVEL",
      "points": [{"x": 15.0, "y": 15.0, "z": 3.0}],
      "feedrate": 3500.0
    },
    {
      "type": "DRAW",
      "points": [{"x": 15.0, "y": 15.0, "z": 0.0}, {"x": 20.0, "y": 25.0, "z": 0.0}],
      "feedrate": 1400.0
    }
  ],
  "statistics": {
    "totalDrawDistanceMm": 1845.2,
    "totalTravelDistanceMm": 920.4,
    "formattedTime": "1m 48s",
    "penLiftsCount": 42
  }
}
```

#### 2. `POST /api/generate-gcode-multipart`
Accepts `multipart/form-data` with raw file uploads for SVG vector assets alongside form fields and parameters.

#### 3. `POST /api/download-gcode`
Accepts `{ "gcode": "...", "filename": "assignment.gcode" }` and returns an attached `.gcode` file download stream.

#### 4. `GET /api/samples`
Returns bundled preset assignments (Physics RLC, Calculus Math Curves) with preloaded SVGs.

---

## 🖨️ Hardware & Plotting Guide

### Bambu Lab A1 Setup
1. **Pen Mount**: Attach your 3D printed spring-loaded pen bracket to the Bambu Lab A1 toolhead.
2. **Paper Placement**: Secure an A4 or standard paper sheet to the Bambu textured or smooth PEI sheet using low-tack painter's tape.
3. **Calibrate Contact ($Z_{down}$)**:
   - Adjust the pen tip in the bracket so that when $Z = 0.0\text{ mm}$, the pen gently contacts the paper with slight spring deflection.
   - For thicker paper or markers, adjust $Z_{down}$ in the CatPlotLib UI (e.g. `0.2 mm` or `-0.1 mm`).
4. **Execution**:
   - Save the downloaded `.gcode` to a micro-SD card or send via Bambu Studio / FTP.
   - Start the print. CatPlotLib will safely zero axes and begin drawing immediately without heating.
