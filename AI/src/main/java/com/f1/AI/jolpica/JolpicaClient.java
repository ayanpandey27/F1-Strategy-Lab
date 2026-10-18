package com.f1.AI.jolpica;

import com.f1.AI.jolpica.JolpicaDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

/**
 * Client for the free Jolpica F1 API (https://api.jolpi.ca/ergast/f1). No API key needed.
 * The public rate limit is 4 requests/second and 500/hour, so calls are spaced out and paged at 100 rows.
 */
@Component
public class JolpicaClient {

    private static final Logger log = LoggerFactory.getLogger(JolpicaClient.class);
    private static final int PAGE_SIZE = 100;

    private final RestClient http;
    private final long delayMs;

    public JolpicaClient(@Value("${f1.jolpica.base-url:https://api.jolpi.ca/ergast/f1}") String baseUrl,
                         @Value("${f1.jolpica.request-delay-ms:350}") long delayMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.delayMs = delayMs;
    }

    public List<RaceDto> schedule(int season) {
        return races(get("/{season}.json?limit=100", season));
    }

    public Optional<RaceDto> results(int season, int round) {
        return races(get("/{season}/{round}/results.json?limit=100", season, round)).stream().findFirst();
    }

    /** Lap timings are paged per timing row, so a single lap can be split across two pages; merge by lap number. */
    public List<LapDto> laps(int season, int round) {
        Map<String, List<TimingDto>> byLap = new LinkedHashMap<>();
        int offset = 0;
        int total;
        do {
            Envelope page = get("/{season}/{round}/laps.json?limit={limit}&offset={offset}", season, round, PAGE_SIZE, offset);
            total = parseInt(page.mrData().total());
            for (RaceDto race : races(page)) {
                if (race.laps() == null) continue;
                for (LapDto lap : race.laps()) {
                    byLap.computeIfAbsent(lap.number(), k -> new ArrayList<>()).addAll(lap.timings());
                }
            }
            offset += PAGE_SIZE;
        } while (offset < total);
        List<LapDto> merged = new ArrayList<>();
        byLap.forEach((number, timings) -> merged.add(new LapDto(number, timings)));
        return merged;
    }

    public List<PitStopDto> pitStops(int season, int round) {
        List<PitStopDto> all = new ArrayList<>();
        int offset = 0;
        int total;
        do {
            Envelope page = get("/{season}/{round}/pitstops.json?limit={limit}&offset={offset}", season, round, PAGE_SIZE, offset);
            total = parseInt(page.mrData().total());
            for (RaceDto race : races(page)) {
                if (race.pitStops() != null) all.addAll(race.pitStops());
            }
            offset += PAGE_SIZE;
        } while (offset < total);
        return all;
    }

    private Envelope get(String uri, Object... vars) {
        throttle();
        log.debug("GET {} {}", uri, Arrays.toString(vars));
        Envelope env = http.get().uri(uri, vars).retrieve().body(Envelope.class);
        if (env == null || env.mrData() == null) {
            throw new IllegalStateException("Empty response from Jolpica for " + uri);
        }
        return env;
    }

    private static List<RaceDto> races(Envelope env) {
        if (env.mrData().raceTable() == null || env.mrData().raceTable().races() == null) return List.of();
        return env.mrData().raceTable().races();
    }

    private void throttle() {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int parseInt(String s) {
        return s == null || s.isBlank() ? 0 : Integer.parseInt(s);
    }
}
