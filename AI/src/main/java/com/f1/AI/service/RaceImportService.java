package com.f1.AI.service;

import com.f1.AI.jolpica.JolpicaClient;
import com.f1.AI.jolpica.JolpicaDtos.*;
import com.f1.AI.model.*;
import com.f1.AI.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/** Pulls a race from Jolpica once and stores it in the local H2 file database; later reads never hit the network. */
@Service
public class RaceImportService {

    private static final Logger log = LoggerFactory.getLogger(RaceImportService.class);

    private final JolpicaClient jolpica;
    private final RaceRepository races;
    private final DriverRepository drivers;
    private final RaceResultRepository results;
    private final LapTimeRepository laps;
    private final PitStopRepository pitStops;

    public RaceImportService(JolpicaClient jolpica, RaceRepository races, DriverRepository drivers,
                             RaceResultRepository results, LapTimeRepository laps, PitStopRepository pitStops) {
        this.jolpica = jolpica;
        this.races = races;
        this.drivers = drivers;
        this.results = results;
        this.laps = laps;
        this.pitStops = pitStops;
    }

    /** Returns the season calendar, fetching it from Jolpica only the first time. */
    @Transactional
    public List<Race> schedule(int season) {
        List<Race> stored = races.findBySeasonOrderByRound(season);
        if (!stored.isEmpty()) return stored;
        for (RaceDto dto : jolpica.schedule(season)) {
            Race race = new Race(season, Integer.parseInt(dto.round()));
            applyRaceInfo(race, dto);
            races.save(race);
        }
        return races.findBySeasonOrderByRound(season);
    }

    @Transactional
    public Race importRace(int season, int round, boolean force) {
        Race race = races.findBySeasonAndRound(season, round).orElseGet(() -> new Race(season, round));
        if (race.isImported() && !force) return race;

        RaceDto resultDto = jolpica.results(season, round)
                .orElseThrow(() -> new NoSuchElementException("No results for " + season + " round " + round + " (race not run yet?)"));
        List<LapDto> lapDtos = jolpica.laps(season, round);
        List<PitStopDto> pitDtos = jolpica.pitStops(season, round);

        applyRaceInfo(race, resultDto);
        race = races.save(race);
        if (race.getId() != null) {
            results.deleteByRaceId(race.getId());
            laps.deleteByRaceId(race.getId());
            pitStops.deleteByRaceId(race.getId());
        }

        Map<String, Driver> driverById = new HashMap<>();
        int maxLaps = 0;
        for (ResultDto r : resultDto.results()) {
            Driver driver = drivers.save(new Driver(r.driver().driverId(), r.driver().code(), r.driver().givenName(),
                    r.driver().familyName(), r.driver().nationality(), r.driver().permanentNumber()));
            driverById.put(driver.getId(), driver);

            RaceResult result = new RaceResult(race, driver);
            result.setConstructorName(r.constructor() != null ? r.constructor().name() : null);
            result.setGrid(intOr(r.grid(), 0));
            result.setPositionText(r.positionText());
            result.setPosition(isNumeric(r.positionText()) ? Integer.valueOf(r.positionText()) : null);
            result.setPoints(r.points() == null ? 0 : Double.parseDouble(r.points()));
            result.setLaps(intOr(r.laps(), 0));
            result.setStatus(r.status());
            if (r.time() != null && r.time().millis() != null) result.setFinishTimeMs(Long.parseLong(r.time().millis()));
            if (r.fastestLap() != null) {
                result.setFastestLapRank(intOrNull(r.fastestLap().rank()));
                result.setFastestLapNumber(intOrNull(r.fastestLap().lap()));
                result.setFastestLapTime(r.fastestLap().time() != null ? r.fastestLap().time().time() : null);
            }
            results.save(result);
            maxLaps = Math.max(maxLaps, result.getLaps());
        }

        List<LapTime> lapRows = new ArrayList<>();
        for (LapDto lap : lapDtos) {
            int lapNumber = Integer.parseInt(lap.number());
            for (TimingDto t : lap.timings()) {
                Driver d = driverById.get(t.driverId());
                if (d == null || t.time() == null) continue;
                lapRows.add(new LapTime(race, d, lapNumber, intOr(t.position(), 0), TimeParser.toMillis(t.time())));
            }
        }
        laps.saveAll(lapRows);

        List<PitStop> pitRows = new ArrayList<>();
        for (PitStopDto p : pitDtos) {
            Driver d = driverById.get(p.driverId());
            if (d == null) continue;
            long duration = p.duration() == null ? 0 : TimeParser.toMillis(p.duration());
            pitRows.add(new PitStop(race, d, intOr(p.stop(), 0), intOr(p.lap(), 0), duration, p.time()));
        }
        pitStops.saveAll(pitRows);

        race.setTotalLaps(maxLaps);
        race.setImported(true);
        log.info("Imported {} {} R{}: {} results, {} lap times, {} pit stops",
                race.getRaceName(), season, round, resultDto.results().size(), lapRows.size(), pitRows.size());
        return races.save(race);
    }

    private static void applyRaceInfo(Race race, RaceDto dto) {
        race.setRaceName(dto.raceName());
        if (dto.date() != null) race.setRaceDate(LocalDate.parse(dto.date()));
        if (dto.circuit() != null) {
            race.setCircuitName(dto.circuit().circuitName());
            if (dto.circuit().location() != null) race.setCountry(dto.circuit().location().country());
        }
    }

    private static boolean isNumeric(String s) {
        return s != null && !s.isBlank() && s.chars().allMatch(Character::isDigit);
    }

    private static int intOr(String s, int fallback) {
        return isNumeric(s) ? Integer.parseInt(s) : fallback;
    }

    private static Integer intOrNull(String s) {
        return isNumeric(s) ? Integer.valueOf(s) : null;
    }
}
