package in.debagnik.plottercraft.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotterStatistics {
    private double totalDrawDistanceMm;
    private double totalTravelDistanceMm;
    private double totalDistanceMm;
    private double totalTimeSeconds;
    private String formattedTime;
    private int penLiftsCount;
    private int totalMovesCount;
    private int gcodeLinesCount;
    private int strokeCount;
}
