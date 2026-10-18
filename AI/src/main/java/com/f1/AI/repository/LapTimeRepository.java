package com.f1.AI.repository;

import com.f1.AI.model.LapTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LapTimeRepository extends JpaRepository<LapTime, Long> {

    /** Flat projection so analysis never touches lazy associations. */
    record LapRow(String driverId, int lap, int position, long timeMs) {
    }

    @Query("select new com.f1.AI.repository.LapTimeRepository$LapRow(l.driver.id, l.lap, l.position, l.timeMs) "
            + "from LapTime l where l.race.id = :raceId order by l.lap, l.position")
    List<LapRow> findRows(Long raceId);

    @Modifying
    @Query("delete from LapTime l where l.race.id = :raceId")
    void deleteByRaceId(Long raceId);
}
