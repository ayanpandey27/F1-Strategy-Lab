package com.f1.AI.analysis;

import com.f1.AI.analysis.AnalysisModels.*;
import com.f1.AI.analysis.RaceData.DriverInfo;
import com.f1.AI.analysis.RaceData.RaceInfo;
import com.f1.AI.repository.LapTimeRepository.LapRow;
import com.f1.AI.repository.PitStopRepository.PitRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.function.IntUnaryOperator;

import static org.assertj.core.api.Assertions.*;

/**
 * Synthetic 20-lap race with exact numbers so expected values can be worked out by hand:
 * AAA: base 90.000s + 0.100s per lap of tyre age, pits lap 10 (in-lap +5s, out-lap +15s), starts +3s.
 * BBB: base 90.200s + 0.100s per lap of tyre age, pits lap 15, starts +5s.
 * CCC: base 91.500s + 0.050s per lap of tyre age, no stops, starts +6s.
 */
public class StrategyAnalyzerTest {

    static final int LAPS = 20;

    public static RaceData race(Set<Integer> scLaps) {
        Map<String, IntUnaryOperator> lapTime = new LinkedHashMap<>();
        lapTime.put("aaa", lap -> lapTime(lap, 90_000, 100, 3_000, 10));
        lapTime.put("bbb", lap -> lapTime(lap, 90_200, 100, 5_000, 15));
        lapTime.put("ccc", lap -> lapTime(lap, 91_500, 50, 6_000, -1));

        Map<String, Long> cumulative = new HashMap<>();
        List<LapRow> rows = new ArrayList<>();
        for (int lap = 1; lap <= LAPS; lap++) {
            Map<String, Long> times = new HashMap<>();
            for (var e : lapTime.entrySet()) {
                long t = e.getValue().applyAsInt(lap);
                if (scLaps.contains(lap)) t = Math.round(t * 1.4);
                times.put(e.getKey(), t);
                cumulative.merge(e.getKey(), t, Long::sum);
            }
            List<String> order = new ArrayList<>(lapTime.keySet());
            order.sort(Comparator.comparingLong(cumulative::get));
            for (int i = 0; i < order.size(); i++) {
                rows.add(new LapRow(order.get(i), lap, i + 1, times.get(order.get(i))));
            }
        }
        List<DriverInfo> drivers = List.of(
                new DriverInfo("aaa", "AAA", "Alice Alpha", "Team A", 1, 1, "1", "Finished", 25, LAPS, 2, "1:30.100"),
                new DriverInfo("bbb", "BBB", "Bob Bravo", "Team B", 2, 2, "2", "Finished", 18, LAPS, 1, "1:30.000"),
                new DriverInfo("ccc", "CCC", "Cara Charlie", "Team C", 3, 3, "3", "Finished", 15, LAPS, 3, "1:31.550"));
        List<PitRow> stops = List.of(new PitRow("aaa", 1, 10, 22_000), new PitRow("bbb", 1, 15, 21_500));
        return new RaceData(new RaceInfo(1L, 2024, 1, "Test GP", "Test Circuit", "Nowhere", LocalDate.of(2024, 3, 2), LAPS),
                drivers, rows, stops);
    }

    /** Lap time with tyre age reset after {@code pitLap}; in-lap +5s and out-lap +15s. */
    static int lapTime(int lap, int base, int degradation, int startPenalty, int pitLap) {
        int age = pitLap > 0 && lap > pitLap ? lap - pitLap : lap;
        int t = base + degradation * age;
        if (lap == 1) t += startPenalty;
        if (lap == pitLap) t += 5_000;
        if (pitLap > 0 && lap == pitLap + 1) t += 15_000;
        return t;
    }

    final StrategyAnalyzer analyzer = new StrategyAnalyzer(race(Set.of()));

    @Test
    void splitsStintsAtPitStopsAndMeasuresDegradation() {
        List<Stint> stints = analyzer.stints("aaa");

        assertThat(stints).hasSize(2);
        assertThat(stints.get(0)).extracting(Stint::startLap, Stint::endLap, Stint::laps).containsExactly(1, 10, 10);
        assertThat(stints.get(1)).extracting(Stint::startLap, Stint::endLap, Stint::laps).containsExactly(11, 20, 10);
        assertThat(stints.get(0).degradationMsPerLap()).isEqualTo(100.0);
        assertThat(stints.get(1).degradationMsPerLap()).isEqualTo(100.0);
        // lap 1, in-lap 10 and out-lap 11 are excluded
        assertThat(stints.get(0).cleanLaps()).isEqualTo(8);
        assertThat(stints.get(1).cleanLaps()).isEqualTo(9);
    }

    @Test
    void driverWithoutStopsHasOneStint() {
        assertThat(analyzer.stints("ccc")).singleElement()
                .extracting(Stint::startLap, Stint::endLap).containsExactly(1, 20);
        assertThat(analyzer.strategy("ccc").stops()).isZero();
    }

    @Test
    void pitImpactExplainsLostPosition() {
        PitStopImpact stop = analyzer.pitImpact("aaa").stops().getFirst();

        assertThat(stop.lap()).isEqualTo(10);
        assertThat(stop.positionBefore()).isEqualTo(1);
        // BBB was 3.8s behind and CCC 14.25s behind, both less than the ~20s lost in the pits
        assertThat(stop.positionAfterOutLap()).isEqualTo(3);
        assertThat(stop.positionsLost()).isEqualTo(2);
        // (96.000 + 105.100) - 2 x 90.500 reference
        assertThat(stop.timeLostMs()).isEqualTo(20_100.0);
        assertThat(stop.carsWithinPitLoss()).extracting(GapBehind::code).containsExactly("BBB", "CCC");
        assertThat(stop.passedBy()).extracting(RivalMove::code).containsExactly("BBB", "CCC");
        assertThat(stop.passedBy().get(0).explanation()).contains("pitted on lap 15").contains("temporary");
        assertThat(stop.passedBy().get(1).explanation()).contains("did not pit");
        assertThat(stop.underNeutralisation()).isFalse();
    }

    @Test
    void whatIfEarlierStopUsesFittedDegradation() {
        WhatIfResult w = analyzer.whatIf("aaa", 1, 5);

        assertThat(w.originalPitLap()).isEqualTo(10);
        assertThat(w.newPitLap()).isEqualTo(5);
        assertThat(w.estimatedPitLossMs()).isEqualTo(20_000);
        assertThat(w.originalSegmentMs()).isEqualTo(1_470_000);
        assertThat(w.newSegmentMs()).isEqualTo(1_472_500);
        assertThat(w.deltaMs()).isEqualTo(2_500);
        assertThat(w.estimatedPosition()).isEqualTo(1);
        assertThat(w.warnings()).anyMatch(s -> s.contains("15 laps long"));
    }

    @Test
    void whatIfRejectsImpossibleStops() {
        assertThatThrownBy(() -> analyzer.whatIf("aaa", 2, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analyzer.whatIf("ccc", 1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analyzer.whatIf("aaa", 1, 9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analyzer.whatIf("nobody", 1, 5)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void compareGivesGapByLapAndHeadToHead() {
        Comparison c = analyzer.compare("aaa", "bbb");

        assertThat(c.gapByLap()).hasSize(LAPS);
        assertThat(c.gapByLap().getLast().gapMs()).isEqualTo(-8_500.0);
        assertThat(c.headToHead().commonCleanLaps()).isPositive();
    }

    @Test
    void summaryListsWinnerAndStrategies() {
        RaceSummary s = analyzer.summary();

        assertThat(s.winner()).isEqualTo("Alice Alpha (Team A)");
        assertThat(s.mostCommonStopCount()).startsWith("1-stop (2");
        assertThat(s.classification()).extracting(DriverLine::code).containsExactly("AAA", "BBB", "CCC");
        assertThat(s.fastestPitStop().code()).isEqualTo("BBB");
        assertThat(s.neutralisedLapRanges()).isEmpty();
    }

    @Test
    void detectsSafetyCarLapsAndExcludesThemFromCleanLaps() {
        StrategyAnalyzer sc = new StrategyAnalyzer(race(Set.of(6, 7)));

        assertThat(sc.neutralisedLaps()).containsExactlyInAnyOrder(6, 7);
        assertThat(sc.summary().neutralisedLapRanges()).containsExactly("laps 6-7");
        assertThat(sc.cleanLaps("ccc")).doesNotContainKeys(1, 6, 7);
    }

    @Test
    void rangesCollapsesConsecutiveLaps() {
        assertThat(StrategyAnalyzer.ranges(List.of(3, 4, 5, 9, 12, 13))).containsExactly("laps 3-5", "lap 9", "laps 12-13");
    }
}
