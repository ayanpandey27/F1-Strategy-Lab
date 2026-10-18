package com.f1.AI.web;

import com.f1.AI.analysis.AnalysisModels.*;
import com.f1.AI.analysis.AnalysisService;
import com.f1.AI.analysis.RaceData.DriverInfo;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/races/{raceId}")
public class AnalysisController {

    private final AnalysisService analysis;

    public AnalysisController(AnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/drivers")
    public Collection<DriverInfo> drivers(@PathVariable long raceId) {
        return analysis.analyzer(raceId).drivers();
    }

    @GetMapping("/summary")
    public RaceSummary summary(@PathVariable long raceId) {
        return analysis.analyzer(raceId).summary();
    }

    @GetMapping("/laps")
    public LapChartData laps(@PathVariable long raceId) {
        return analysis.analyzer(raceId).lapChart();
    }

    @GetMapping("/drivers/{driverId}/strategy")
    public DriverStrategy strategy(@PathVariable long raceId, @PathVariable String driverId) {
        return analysis.analyzer(raceId).strategy(driverId);
    }

    @GetMapping("/drivers/{driverId}/pit-impact")
    public PitImpactReport pitImpact(@PathVariable long raceId, @PathVariable String driverId) {
        return analysis.analyzer(raceId).pitImpact(driverId);
    }

    @GetMapping("/drivers/{driverId}/what-if")
    public WhatIfResult whatIf(@PathVariable long raceId, @PathVariable String driverId,
                               @RequestParam(defaultValue = "1") int stop,
                               @RequestParam(defaultValue = "5") int lapsEarlier) {
        return analysis.analyzer(raceId).whatIf(driverId, stop, lapsEarlier);
    }

    @GetMapping("/compare")
    public Comparison compare(@PathVariable long raceId, @RequestParam String a, @RequestParam String b) {
        return analysis.analyzer(raceId).compare(a, b);
    }
}
