package in.debagnik.catplotlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolpathMove {
    private ToolpathPoint from;
    private ToolpathPoint to;
    private double feedrate;
    private double distance;
    private double durationSeconds;
    private String type; // "TRAVEL" (G0) or "DRAW" (G1)
    private int moveIndex;
    private int strokeIndex;
}
