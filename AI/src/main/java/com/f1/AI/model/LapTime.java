package com.f1.AI.model;

import jakarta.persistence.*;

@Entity
@Table(indexes = @Index(columnList = "race_id, driver_id"))
public class LapTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Driver driver;

    @Column(name = "lap_number")
    private int lap;
    /** Running position at the end of this lap. */
    @Column(name = "lap_position")
    private int position;
    private long timeMs;

    protected LapTime() {
    }

    public LapTime(Race race, Driver driver, int lap, int position, long timeMs) {
        this.race = race;
        this.driver = driver;
        this.lap = lap;
        this.position = position;
        this.timeMs = timeMs;
    }

    public Long getId() { return id; }
    public Race getRace() { return race; }
    public Driver getDriver() { return driver; }
    public int getLap() { return lap; }
    public int getPosition() { return position; }
    public long getTimeMs() { return timeMs; }
}
