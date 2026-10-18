package com.f1.AI.repository;

import com.f1.AI.model.Race;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RaceRepository extends JpaRepository<Race, Long> {

    Optional<Race> findBySeasonAndRound(int season, int round);

    List<Race> findBySeasonOrderByRound(int season);

    List<Race> findByImportedTrueOrderBySeasonDescRoundDesc();
}
