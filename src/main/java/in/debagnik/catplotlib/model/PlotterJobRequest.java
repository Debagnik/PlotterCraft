package in.debagnik.catplotlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotterJobRequest {
    private String markdown;
    
    @Builder.Default
    private Map<String, String> svgFiles = new HashMap<>();
    
    // Bambu Lab A1 Motion Parameters
    @Builder.Default
    private Double feedrateDraw = 1400.0; // G1 draw feedrate mm/min
    
    @Builder.Default
    private Double feedrateTravel = 3500.0; // G0 travel feedrate mm/min
    
    @Builder.Default
    private Double zUp = 3.0; // Pen up clearance mm
    
    @Builder.Default
    private Double zDown = 0.0; // Pen down drawing height mm
    
    // Human-like Jitter Configuration
    @Builder.Default
    private Boolean jitterEnabled = true;
    
    @Builder.Default
    private Double jitterIntensity = 0.15; // baseline noise in mm (+/- 0.15 mm)
    
    // Page & Sheet Dimensions (Default fitted to Bambu Lab A1 256x256 mm bed)
    @Builder.Default
    private Double pageWidth = 256.0;
    
    @Builder.Default
    private Double pageHeight = 256.0;
    
    @Builder.Default
    private Double marginTop = 15.0;
    
    @Builder.Default
    private Double marginBottom = 15.0;
    
    @Builder.Default
    private Double marginLeft = 15.0;
    
    @Builder.Default
    private Double marginRight = 15.0;
    
    // Typography
    @Builder.Default
    private Double fontScale = 1.0; // Base font scale (standard line height ~5.0 mm)
    
    @Builder.Default
    private Double lineSpacing = 1.4; // Multiplier
    
    @Builder.Default
    private String fontStyle = "SIMPLEX"; // SIMPLEX, COMPLEX, DUPLEX

    // Axis Flipping / Bed Orientation (Default: flip Y so SVG top-down matches Cartesian bed)
    @Builder.Default
    private Boolean flipY = true;

    @Builder.Default
    private Boolean flipX = false;

    public double getFeedrateDraw() { return feedrateDraw != null ? feedrateDraw : 1400.0; }
    public double getFeedrateTravel() { return feedrateTravel != null ? feedrateTravel : 3500.0; }
    public double getZUp() { return zUp != null ? zUp : 3.0; }
    public double getZDown() { return zDown != null ? zDown : 0.0; }
    public boolean isJitterEnabled() { return jitterEnabled != null ? jitterEnabled : true; }
    public double getJitterIntensity() { return jitterIntensity != null ? jitterIntensity : 0.15; }
    public double getPageWidth() { return pageWidth != null ? pageWidth : 256.0; }
    public double getPageHeight() { return pageHeight != null ? pageHeight : 256.0; }
    public double getMarginTop() { return marginTop != null ? marginTop : 15.0; }
    public double getMarginBottom() { return marginBottom != null ? marginBottom : 15.0; }
    public double getMarginLeft() { return marginLeft != null ? marginLeft : 15.0; }
    public double getMarginRight() { return marginRight != null ? marginRight : 15.0; }
    public double getFontScale() { return fontScale != null ? fontScale : 1.0; }
    public double getLineSpacing() { return lineSpacing != null ? lineSpacing : 1.4; }
    public boolean isFlipY() { return flipY != null ? flipY : true; }
    public boolean isFlipX() { return flipX != null ? flipX : false; }
}
