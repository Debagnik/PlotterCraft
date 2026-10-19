package in.debagnik.catplotlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolpathPoint {
    private double x;
    private double y;
    private double z;
    private String type; // "TRAVEL" or "DRAW"
    private boolean penDown;
    private int strokeIndex;
}
