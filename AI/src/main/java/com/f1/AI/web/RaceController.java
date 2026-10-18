package com.f1.AI.web;

import com.f1.AI.analysis.AnalysisService;
import com.f1.AI.model.Race;
import com.f1.AI.service.RaceImportService;
import com.f1.AI.repository.RaceRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RaceController {

    public record RaceView(Long id, int season, int round, String name, String circuit, String country,
                           LocalDate date, int totalLaps, boolean imported) {
        static RaceView of(Race r) {
            return new RaceView(r.getId(), r.getSeason(), r.getRound(), r.getRaceName(), r.getCircuitName(),
                    r.getCountry(), r.getRaceDate(), r.getTotalLaps(), r.isImported());
        }
    }

    private final RaceImportService importer;
    private final RaceRepository races;
    private final AnalysisService analysis;

    public RaceController(RaceImportService importer, RaceRepository races, AnalysisService analysis) {
        this.importer = importer;
        this.races = races;
        this.analysis = analysis;
    }

    /** Season calendar (fetched from Jolpica once, then served from H2). */
    @GetMapping("/seasons/{season}/races")
    public List<RaceView> schedule(@PathVariable int season) {
        return importer.schedule(season).stream().map(RaceView::of).toList();
    }

    /** Races already downloaded to the local database. */
    @GetMapping("/races")
    public List<RaceView> imported() {
        return races.findByImportedTrueOrderBySeasonDescRoundDesc().stream().map(RaceView::of).toList();
    }

    @PostMapping("/seasons/{season}/races/{round}/import")
    public RaceView importRace(@PathVariable int season, @PathVariable int round,
                               @RequestParam(defaultValue = "false") boolean force) {
        Race race = importer.importRace(season, round, force);
        analysis.evict(race.getId());
        return RaceView.of(race);
    }
}
