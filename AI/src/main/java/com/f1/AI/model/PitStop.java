package com.f1.AI.model;

import jakarta.persistence.*;

@Entity
@Table(indexes = @Index(columnList = "race_id, driver_id"))
public class PitStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Driver driver;

    private int stopNumber;
    /** Lap on which the driver entered the pits (the in-lap). */
    @Column(name = "lap_number")
    private int lap;
    /** Pit-lane time as reported by Jolpica (entry to exit), in ms. */
    private long durationMs;
    private String timeOfDay;

    protected PitStop() {
    }

    public PitStop(Race race, Driver driver, int stopNumber, int lap, long durationMs, String timeOfDay) {
        this.race = race;
        this.driver = driver;
        this.stopNumber = stopNumber;
        this.lap = lap;
        this.durationMs = durationMs;
        this.timeOfDay = timeOfDay;
    }

    public Long getId() { return id; }
    public Race getRace() { return race; }
    public Driver getDriver() { return driver; }
    public int getStopNumber() { return stopNumber; }
    public int getLap() { return lap; }
    public long getDurationMs() { return durationMs; }
    public String getTimeOfDay() { return timeOfDay; }
}
