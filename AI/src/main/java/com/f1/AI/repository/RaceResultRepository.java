package com.f1.AI.repository;

import com.f1.AI.model.RaceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RaceResultRepository extends JpaRepository<RaceResult, Long> {

    List<RaceResult> findByRaceId(Long raceId);

    @Modifying
    @Query("delete from RaceResult r where r.race.id = :raceId")
    void deleteByRaceId(Long raceId);
}
