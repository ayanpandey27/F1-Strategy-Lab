package com.f1.AI.jolpica;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Minimal mirror of the Jolpica (Ergast-compatible) JSON responses. All numeric fields arrive as strings.
 */
public final class JolpicaDtos {

    private JolpicaDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(@JsonProperty("MRData") MRData mrData) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MRData(String limit, String offset, String total,
                         @JsonProperty("RaceTable") RaceTable raceTable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RaceTable(String season, @JsonProperty("Races") List<RaceDto> races) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RaceDto(String season, String round, String raceName, String date,
                          @JsonProperty("Circuit") CircuitDto circuit,
                          @JsonProperty("Results") List<ResultDto> results,
                          @JsonProperty("Laps") List<LapDto> laps,
                          @JsonProperty("PitStops") List<PitStopDto> pitStops) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CircuitDto(String circuitId, String circuitName, @JsonProperty("Location") LocationDto location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationDto(String locality, String country) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultDto(String number, String position, String positionText, String points,
                            @JsonProperty("Driver") DriverDto driver,
                            @JsonProperty("Constructor") ConstructorDto constructor,
                            String grid, String laps, String status,
                            @JsonProperty("Time") TimeDto time,
                            @JsonProperty("FastestLap") FastestLapDto fastestLap) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriverDto(String driverId, String permanentNumber, String code,
                            String givenName, String familyName, String nationality) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConstructorDto(String constructorId, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeDto(String millis, String time) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FastestLapDto(String rank, String lap, @JsonProperty("Time") TimeDto time) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LapDto(String number, @JsonProperty("Timings") List<TimingDto> timings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimingDto(String driverId, String position, String time) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PitStopDto(String driverId, String lap, String stop, String time, String duration) {
    }
}
