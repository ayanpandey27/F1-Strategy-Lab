package com.f1.AI.analysis;

import com.f1.AI.analysis.RaceData.DriverInfo;
import com.f1.AI.analysis.RaceData.RaceInfo;

import java.util.List;

/** Result types returned by {@link StrategyAnalyzer}. All times are milliseconds. */
public final class AnalysisModels {

    private AnalysisModels() {
    }

    /**
     * A stint is the run of laps between pit stops. Jolpica has no tyre-compound data, so compounds are unknown.
     * degradationMsPerLap is the slope of a linear fit of clean lap time against tyre age (null if too few laps).
     */
    public record Stint(int number, int startLap, int endLap, int laps, int cleanLaps, Double avgMs, Double medianMs,
                        Double stdDevMs, Long bestMs, Double degradationMsPerLap) {
    }

    public record DriverStrategy(DriverInfo driver, int stops, List<Integer> pitLaps, List<Stint> stints,
                                 Double cleanAvgMs, Double cleanStdDevMs, Double coefficientOfVariationPct) {
    }

    public record RivalMove(String driverId, String code, int positionBefore, int positionAfter, String explanation) {
    }

    public record GapBehind(String code, double gapMs) {
    }

    public record PitStopImpact(int stopNumber, int lap, long pitLaneMs, Double timeLostMs, int positionBefore,
                                Integer positionAfterOutLap, Integer positionThreeLapsLater, int positionsLost,
                                boolean underNeutralisation, List<GapBehind> carsBehindBefore,
                                List<GapBehind> carsWithinPitLoss, List<RivalMove> passedBy,
                                List<RivalMove> gainedOn) {
    }

    public record PitImpactReport(DriverInfo driver, List<PitStopImpact> stops) {
    }

    public record WhatIfResult(DriverInfo driver, int stopNumber, int originalPitLap, int newPitLap,
                               int segmentStartLap, int segmentEndLap, long originalSegmentMs, long newSegmentMs,
                               long deltaMs, Double oldStintDegradationMsPerLap, Double newStintDegradationMsPerLap,
                               long estimatedPitLossMs, Integer actualPosition, Integer estimatedPosition,
                               List<String> positionChanges, List<String> warnings, List<String> assumptions) {
    }

    public record GapPoint(int lap, double gapMs) {
    }

    public record HeadToHead(int commonCleanLaps, Double meanDeltaMs, int firstDriverFasterLaps,
                             int secondDriverFasterLaps) {
    }

    public record Comparison(DriverStrategy first, DriverStrategy second, HeadToHead headToHead,
                             List<GapPoint> gapByLap) {
    }

    public record DriverLine(String code, String name, String team, int grid, String finish, String status,
                             int stops, List<Integer> pitLaps, List<Integer> stintLengths) {
    }

    public record Movement(String code, int grid, int finish, int change) {
    }

    public record FastestLap(String code, String time, Integer lap) {
    }

    public record FastestStop(String code, long pitLaneMs, int lap) {
    }

    public record NeutralisedStop(String code, int lap, int stopNumber) {
    }

    public record RaceSummary(RaceInfo race, String winner, List<String> podium, FastestLap fastestLap,
                              List<DriverLine> classification, String mostCommonStopCount,
                              List<Movement> biggestGainers, List<Movement> biggestLosers,
                              List<String> retirements, List<String> neutralisedLapRanges,
                              List<NeutralisedStop> stopsUnderNeutralisation, FastestStop fastestPitStop) {
    }

    public record LapPoint(int lap, long timeMs, int position) {
    }

    public record DriverLaps(String driverId, String code, String name, String team, List<LapPoint> laps,
                             List<Integer> pitLaps) {
    }

    public record LapChartData(int totalLaps, List<Integer> neutralisedLaps, List<DriverLaps> drivers) {
    }
}
