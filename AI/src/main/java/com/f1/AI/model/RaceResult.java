package com.f1.AI.model;

import jakarta.persistence.*;

@Entity
@Table(indexes = @Index(columnList = "race_id"))
public class RaceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Race race;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Driver driver;

    private String constructorName;
    private int grid;
    /** Null when the driver was not classified. */
    private Integer position;
    private String positionText;
    private double points;
    private int laps;
    private String status;
    private Long finishTimeMs;
    private Integer fastestLapRank;
    private Integer fastestLapNumber;
    private String fastestLapTime;

    protected RaceResult() {
    }

    public RaceResult(Race race, Driver driver) {
        this.race = race;
        this.driver = driver;
    }

    public Long getId() { return id; }
    public Race getRace() { return race; }
    public Driver getDriver() { return driver; }
    public String getConstructorName() { return constructorName; }
    public void setConstructorName(String constructorName) { this.constructorName = constructorName; }
    public int getGrid() { return grid; }
    public void setGrid(int grid) { this.grid = grid; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public String getPositionText() { return positionText; }
    public void setPositionText(String positionText) { this.positionText = positionText; }
    public double getPoints() { return points; }
    public void setPoints(double points) { this.points = points; }
    public int getLaps() { return laps; }
    public void setLaps(int laps) { this.laps = laps; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getFinishTimeMs() { return finishTimeMs; }
    public void setFinishTimeMs(Long finishTimeMs) { this.finishTimeMs = finishTimeMs; }
    public Integer getFastestLapRank() { return fastestLapRank; }
    public void setFastestLapRank(Integer fastestLapRank) { this.fastestLapRank = fastestLapRank; }
    public Integer getFastestLapNumber() { return fastestLapNumber; }
    public void setFastestLapNumber(Integer fastestLapNumber) { this.fastestLapNumber = fastestLapNumber; }
    public String getFastestLapTime() { return fastestLapTime; }
    public void setFastestLapTime(String fastestLapTime) { this.fastestLapTime = fastestLapTime; }
}
