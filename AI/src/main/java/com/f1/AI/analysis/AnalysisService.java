package com.f1.AI.analysis;

import com.f1.AI.analysis.RaceData.DriverInfo;
import com.f1.AI.analysis.RaceData.RaceInfo;
import com.f1.AI.model.Race;
import com.f1.AI.model.RaceResult;
import com.f1.AI.repository.LapTimeRepository;
import com.f1.AI.repository.PitStopRepository;
import com.f1.AI.repository.RaceRepository;
import com.f1.AI.repository.RaceResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/** Loads an imported race from H2 into a {@link StrategyAnalyzer}; analysers are cached per race. */
@Service
public class AnalysisService {

    private final RaceRepository races;
    private final RaceResultRepository results;
    private final LapTimeRepository laps;
    private final PitStopRepository stops;
    private final Map<Long, StrategyAnalyzer> cache = new ConcurrentHashMap<>();

    public AnalysisService(RaceRepository races, RaceResultRepository results, LapTimeRepository laps,
                           PitStopRepository stops) {
        this.races = races;
        this.results = results;
        this.laps = laps;
        this.stops = stops;
    }

    @Transactional(readOnly = true)
    public StrategyAnalyzer analyzer(long raceId) {
        StrategyAnalyzer cached = cache.get(raceId);
        if (cached != null) return cached;
        StrategyAnalyzer analyzer = new StrategyAnalyzer(load(raceId));
        cache.put(raceId, analyzer);
        return analyzer;
    }

    public void evict(long raceId) {
        cache.remove(raceId);
    }

    private RaceData load(long raceId) {
        Race race = races.findById(raceId).orElseThrow(() -> new NoSuchElementException("Race " + raceId + " not found"));
        if (!race.isImported()) throw new IllegalStateException("Race " + raceId + " has not been imported yet");
        List<DriverInfo> drivers = results.findByRaceId(raceId).stream().map(AnalysisService::toInfo).toList();
        return new RaceData(
                new RaceInfo(race.getId(), race.getSeason(), race.getRound(), race.getRaceName(), race.getCircuitName(),
                        race.getCountry(), race.getRaceDate(), race.getTotalLaps()),
                drivers, laps.findRows(raceId), stops.findRows(raceId));
    }

    private static DriverInfo toInfo(RaceResult r) {
        return new DriverInfo(r.getDriver().getId(), r.getDriver().getCode(), r.getDriver().getFullName(),
                r.getConstructorName(), r.getGrid(), r.getPosition(), r.getPositionText(), r.getStatus(), r.getPoints(),
                r.getLaps(), r.getFastestLapRank(), r.getFastestLapTime());
    }
}
