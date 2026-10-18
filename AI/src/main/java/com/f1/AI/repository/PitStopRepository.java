package com.f1.AI.repository;

import com.f1.AI.model.PitStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PitStopRepository extends JpaRepository<PitStop, Long> {

    record PitRow(String driverId, int stopNumber, int lap, long durationMs) {
    }

    @Query("select new com.f1.AI.repository.PitStopRepository$PitRow(p.driver.id, p.stopNumber, p.lap, p.durationMs) "
            + "from PitStop p where p.race.id = :raceId order by p.lap, p.stopNumber")
    List<PitRow> findRows(Long raceId);

    @Modifying
    @Query("delete from PitStop p where p.race.id = :raceId")
    void deleteByRaceId(Long raceId);
}
