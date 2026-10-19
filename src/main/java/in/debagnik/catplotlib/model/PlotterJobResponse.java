package in.debagnik.catplotlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotterJobResponse {
    private boolean success;
    private String message;
    private String gcode;
    
    @Builder.Default
    private List<ToolpathMove> moves = new ArrayList<>();
    
    private PlotterStatistics statistics;
    private BoundingBox bounds;
    
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
