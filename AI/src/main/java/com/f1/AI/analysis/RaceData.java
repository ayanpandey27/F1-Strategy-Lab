package com.f1.AI.analysis;

import com.f1.AI.repository.LapTimeRepository.LapRow;
import com.f1.AI.repository.PitStopRepository.PitRow;

import java.time.LocalDate;
import java.util.List;

/** Everything needed to analyse one race, detached from JPA so the analysis is pure and easy to unit-test. */
public record RaceData(RaceInfo race, List<DriverInfo> drivers, List<LapRow> laps, List<PitRow> stops) {

    public record RaceInfo(Long id, int season, int round, String name, String circuit, String country,
                           LocalDate date, int totalLaps) {
    }

    public record DriverInfo(String id, String code, String name, String team, int grid, Integer position,
                             String positionText, String status, double points, int lapsCompleted,
                             Integer fastestLapRank, String fastestLapTime) {
    }
}
