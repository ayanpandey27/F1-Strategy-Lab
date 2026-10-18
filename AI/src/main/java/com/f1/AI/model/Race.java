package com.f1.AI.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"season", "race_round"}))
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int season;
    @Column(name = "race_round")
    private int round;
    private String raceName;
    private String circuitName;
    private String country;
    private LocalDate raceDate;
    private int totalLaps;
    private boolean imported;

    protected Race() {
    }

    public Race(int season, int round) {
        this.season = season;
        this.round = round;
    }

    public Long getId() { return id; }
    public int getSeason() { return season; }
    public int getRound() { return round; }
    public String getRaceName() { return raceName; }
    public void setRaceName(String raceName) { this.raceName = raceName; }
    public String getCircuitName() { return circuitName; }
    public void setCircuitName(String circuitName) { this.circuitName = circuitName; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public LocalDate getRaceDate() { return raceDate; }
    public void setRaceDate(LocalDate raceDate) { this.raceDate = raceDate; }
    public int getTotalLaps() { return totalLaps; }
    public void setTotalLaps(int totalLaps) { this.totalLaps = totalLaps; }
    public boolean isImported() { return imported; }
    public void setImported(boolean imported) { this.imported = imported; }
}
