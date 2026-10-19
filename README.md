# PlotterCraft 🖋️⚡

> Convert Markdown assignments with embedded stylus SVG diagrams into safe, collision-free Bambu Lab A1 plotter G-code, complete with an interactive in-browser 3D slicer-style toolpath preview.

![PlotterCraft Studio](https://raw.githubusercontent.com/bambulab/BambuStudio/master/resources/images/splash.png)

---

## 🌟 Key Features

### 1. Bambu Lab A1 Safe G-Code Generator
- **Zero Heat Enforced**: Automatically issues `M104 S0`, `M140 S0`, `M106 S0`, `M106 P1 S0`, and `M106 P2 S0` at startup. No hotend or heated bed danger.
- **Zero Calibration Collisions**: Completely eliminates stock nozzle wiping, purge lines, and bed-leveling probe macros that would snap or drag a pen toolhead.
- **Safe Homing Routine**: Performs safe `G28` homing and immediately raises $Z$ to safe clearance ($Z = 10\text{ mm}$).
- **Kinematic Controls**: Configurable Pen Up ($Z_{up} = 3.0\text{ mm}$), Pen Down ($Z_{down} = 0.0\text{ mm}$), Travel Feedrate ($G0 = 3500\text{ mm/min}$), and Draw Feedrate ($G1 = 1400\text{ mm/min}$).
- **Finish Routine**: Safe high pen lift ($Z = 25\text{ mm}$), bed presentation forward ($Y = 240\text{ mm}$), and stepper motor release (`M84`).

### 2. Full UTF-8 Single-Stroke Vector Font Engine
- **True Centerlines**: Uses single-stroke vector centerline paths (not hollow TTF outlines), perfectly tailored for physical pen plotting.
- **Comprehensive UTF-8 Coverage**:
  - Full Latin ASCII (32–126).
  - Greek scientific alphabet ($\alpha, \beta, \gamma, \delta, \pi, \sigma, \theta, \lambda, \Omega, \Delta, \Phi$).
  - Mathematical operators & symbols ($\pm, \times, \div, \le, \ge, \neq, \approx, \infty, \int, \sum, \sqrt{}, \partial$).
  - Currencies & typography ($€, £, ¥, $, ₹, °, µ, \rightarrow, \leftarrow, \uparrow, \downarrow, •, —$).
- **Unicode NFD Diacritic Decomposition**: Accented Latin characters (`é, è, ê, ñ, ç, å, ö, ü, á, í, ó, ú`) automatically decompose into base characters with synthesized single-stroke diacritics.
- **Human-Like Jitter Engine**: Configurable baseline perturbation ($\pm 0.15\text{ mm}$), character slant variation ($\pm 2^\circ$), and inter-character pitch variations to simulate authentic handwriting.

### 3. Stylus SVG Integration & Curve Discretization
- Detects `![alt](diagram.svg)` in Markdown text.
- Parses SVG path commands (`M`, `L`, `H`, `V`, `C`, `S`, `Q`, `T`, `A`, `Z`) and primitives (`<line>`, `<rect>`, `<circle>`, `<ellipse>`, `<polyline>`, `<polygon>`).
- Discretizes cubic and quadratic Bézier curves with adaptive chord tolerance ($0.05\text{ mm}$) for ultra-smooth physical curves.
- Auto-scales diagrams to fit within target page margins and advances vertical document $Y$-cursor automatically.

### 4. Interactive 3D Slicer-Style Toolpath Visualizer (Three.js)
- Simulated Bambu Lab A1 build plate ($256 \times 256\text{ mm}$) with textured grid and orientation markers.
- Dual-color toolpath rendering (Bambu Studio / PrusaSlicer style):
  - **Draw Moves ($G1$)**: Solid deep royal ink blue on the paper surface.
  - **Travel Moves ($G0$)**: Translucent dashed/red aerial transit paths.
- **Slicer Scrubbing Controls**: Interactive $0\%$ to $100\%$ progress slider scrubbing through toolpath moves in exact order.
- **Simulation Loop**: Real-time virtual pen toolhead playback at variable speeds ($1\times, 2\times, 5\times, 10\times, 25\times$).
- **Camera Views**: 3D Isometric, Top-Down 2D Slicer View, and Front Z-Clearance View.
- **Slicer Telemetry**: Live estimated plot time, total draw distance, travel distance, and pen lifts count.

---

## 🚀 Getting Started

### Prerequisites
- **Java 25 LTS** (or Java 21+)
- **Gradle 9.x** (Gradle Wrapper included: `gradlew.bat` / `gradlew`)
- Or **Maven 3.9+** (`mvn`)

### Running the Application

#### Option A: Using Gradle Wrapper (Recommended)
```powershell
.\gradlew.bat bootRun
```

On Linux / macOS:
```bash
./gradlew bootRun
```

#### Option B: Using Maven
```powershell
mvn spring-boot:run
```

Once started, open your web browser and navigate to:
```
http://localhost:8080
```

---

## 📋 Markdown & SVG Usage Guide

### Embedding Diagrams
In the editor, reference any uploaded or bundled SVG by name:
```markdown
# Physics 201: RLC Resonant Circuits
**Name:** Alex Morgan  |  **Date:** October 19, 2026

## 1. Experimental Circuit Schematic
The diagram below illustrates the series configuration tested on the laboratory bench:

![Series RLC Circuit](circuit.svg)

## 2. Theoretical Analysis
* Angular frequency $\\omega_0 = 1 / \\sqrt{LC}$ rad/s
* Resistor ($R$): 220.0 $\\Omega \\pm 1\\%$
```

### Pre-loaded Sample Assignments
Click the quick preset buttons in the header:
- **Physics RLC**: Contains full laboratory writeup with circuit schematic SVG.
- **Math Curves**: Contains calculus definite integral assignment with parabola curve SVG.

---

## 🛠️ API Reference

### `POST /api/generate-gcode`
Generates safe G-code and structured 3D toolpath moves.
- **Request Body**:
  ```json
  {
    "markdown": "# Assignment Title\\nBody text...",
    "svgFiles": {
      "diagram.svg": "<svg>...</svg>"
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
- **Response**:
  ```json
  {
    "success": true,
    "gcode": "; Bambu Lab A1 Safe Plotter G-code...",
    "moves": [ ... ],
    "statistics": {
      "totalDrawDistanceMm": 1845.2,
      "totalTravelDistanceMm": 920.4,
      "formattedTime": "1m 48s",
      "penLiftsCount": 42
    }
  }
  ```

### `POST /api/download-gcode`
Directly downloads the `.gcode` file ready for your Bambu Lab SD card or Bambu Handy app.

---

## 📄 License
MIT License. Created for the Bambu Lab 3D Printing & Pen Plotting Community.
