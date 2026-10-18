package com.f1.AI.analysis;

import com.f1.AI.analysis.AnalysisModels.*;
import com.f1.AI.analysis.RaceData.DriverInfo;
import com.f1.AI.repository.LapTimeRepository.LapRow;
import com.f1.AI.repository.PitStopRepository.PitRow;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic race-strategy calculations over one race's lap and pit data. Every number the AI is allowed to quote
 * comes from here.
 */
public class StrategyAnalyzer {

    /** A lap whose field-median time exceeds the race's typical lap by this factor is treated as SC/VSC. */
    static final double NEUTRALISED_FACTOR = 1.15;
    /** Laps slower than this factor of a driver's own median are excluded from pace statistics (traffic, errors). */
    static final double OUTLIER_FACTOR = 1.07;
    private static final int MIN_FIT_POINTS = 3;

    private final RaceData data;
    private final Map<String, DriverInfo> driversById = new LinkedHashMap<>();
    private final Map<String, NavigableMap<Integer, LapRow>> lapsByDriver = new HashMap<>();
    private final Map<String, List<PitRow>> stopsByDriver = new HashMap<>();
    private final Map<String, NavigableMap<Integer, Long>> cumulativeByDriver = new HashMap<>();
    private final Set<Integer> neutralisedLaps;

    public StrategyAnalyzer(RaceData data) {
        this.data = data;
        data.drivers().forEach(d -> driversById.put(d.id(), d));
        for (LapRow row : data.laps()) {
            lapsByDriver.computeIfAbsent(row.driverId(), k -> new TreeMap<>()).put(row.lap(), row);
        }
        for (PitRow stop : data.stops()) {
            stopsByDriver.computeIfAbsent(stop.driverId(), k -> new ArrayList<>()).add(stop);
        }
        stopsByDriver.values().forEach(list -> list.sort(Comparator.comparingInt(PitRow::lap)));
        lapsByDriver.forEach((driver, laps) -> {
            NavigableMap<Integer, Long> cumulative = new TreeMap<>();
            long sum = 0;
            for (LapRow row : laps.values()) {
                sum += row.timeMs();
                cumulative.put(row.lap(), sum);
            }
            cumulativeByDriver.put(driver, cumulative);
        });
        this.neutralisedLaps = detectNeutralisedLaps();
    }

    // ---------------------------------------------------------------- public API

    public DriverInfo driver(String driverId) {
        DriverInfo d = driversById.get(driverId);
        if (d == null) throw new NoSuchElementException("Driver not in this race: " + driverId);
        return d;
    }

    public Collection<DriverInfo> drivers() {
        return driversById.values();
    }

    public Set<Integer> neutralisedLaps() {
        return Collections.unmodifiableSet(neutralisedLaps);
    }

    public DriverStrategy strategy(String driverId) {
        DriverInfo d = driver(driverId);
        List<PitRow> stops = stops(driverId);
        List<Stint> stints = stints(driverId);
        List<Long> clean = cleanLaps(driverId).values().stream().map(LapRow::timeMs).toList();
        Double avg = mean(clean);
        Double sd = stdDev(clean);
        Double cov = avg != null && sd != null ? Math.round(sd / avg * 100 * 1000) / 1000.0 : null;
        return new DriverStrategy(d, stops.size(), stops.stream().map(PitRow::lap).toList(), stints,
                round1(avg), round1(sd), cov);
    }

    public List<Stint> stints(String driverId) {
        driver(driverId);
        NavigableMap<Integer, LapRow> laps = laps(driverId);
        if (laps.isEmpty()) return List.of();
        int lastLap = laps.lastKey();
        Map<Integer, LapRow> clean = cleanLaps(driverId);

        List<Stint> stints = new ArrayList<>();
        int start = 1;
        int number = 1;
        List<Integer> boundaries = new ArrayList<>(stops(driverId).stream().map(PitRow::lap).toList());
        boundaries.add(lastLap);
        for (int end : boundaries) {
            if (end < start) continue;
            int stintStart = start;
            List<long[]> agePoints = new ArrayList<>();
            List<Long> times = new ArrayList<>();
            for (int lap = stintStart; lap <= end; lap++) {
                LapRow row = clean.get(lap);
                if (row == null) continue;
                times.add(row.timeMs());
                agePoints.add(new long[]{lap - stintStart + 1, row.timeMs()});
            }
            Fit fit = fit(agePoints);
            stints.add(new Stint(number++, stintStart, end, end - stintStart + 1, times.size(), round1(mean(times)),
                    round1(median(times)), round1(stdDev(times)),
                    times.isEmpty() ? null : Collections.min(times), fit == null ? null : round1(fit.slope)));
            start = end + 1;
        }
        return stints;
    }

    public PitImpactReport pitImpact(String driverId) {
        DriverInfo d = driver(driverId);
        NavigableMap<Integer, LapRow> laps = laps(driverId);
        List<PitStopImpact> impacts = new ArrayList<>();
        for (PitRow stop : stops(driverId)) {
            int p = stop.lap();
            int positionBefore = p <= 1 ? d.grid() : positionAt(driverId, p - 1).orElse(d.grid());
            Integer afterOut = positionAt(driverId, p + 1).orElse(null);
            Integer later = positionAt(driverId, Math.min(p + 3, laps.isEmpty() ? p : laps.lastKey())).orElse(null);
            int lost = afterOut == null ? 0 : afterOut - positionBefore;

            Double reference = referencePace(driverId, p);
            Double timeLost = null;
            LapRow in = laps.get(p);
            LapRow out = laps.get(p + 1);
            if (reference != null && in != null && out != null) {
                timeLost = round1(in.timeMs() + out.timeMs() - 2 * reference);
            }

            List<GapBehind> behind = new ArrayList<>();
            if (p > 1) {
                Long mine = cumulative(driverId, p - 1);
                if (mine != null) {
                    for (String other : lapsByDriver.keySet()) {
                        if (other.equals(driverId)) continue;
                        Long theirs = cumulative(other, p - 1);
                        if (theirs != null && theirs > mine) behind.add(new GapBehind(code(other), theirs - mine));
                    }
                }
            }
            behind.sort(Comparator.comparingDouble(GapBehind::gapMs));
            final Double loss = timeLost;
            List<GapBehind> withinLoss = loss == null ? List.of()
                    : behind.stream().filter(g -> g.gapMs() < loss).toList();

            List<RivalMove> passedBy = new ArrayList<>();
            List<RivalMove> gainedOn = new ArrayList<>();
            if (afterOut != null) {
                for (String other : lapsByDriver.keySet()) {
                    if (other.equals(driverId)) continue;
                    Integer rBefore = p <= 1 ? Integer.valueOf(driversById.getOrDefault(other, d).grid())
                            : positionAt(other, p - 1).orElse(null);
                    Integer rAfter = positionAt(other, p + 1).orElse(null);
                    if (rBefore == null || rAfter == null) continue;
                    if (rBefore > positionBefore && rAfter < afterOut) {
                        passedBy.add(new RivalMove(other, code(other), rBefore, rAfter, explainRival(driverId, other, p)));
                    } else if (rBefore < positionBefore && rAfter > afterOut) {
                        gainedOn.add(new RivalMove(other, code(other), rBefore, rAfter, explainRival(driverId, other, p)));
                    }
                }
            }
            passedBy.sort(Comparator.comparingInt(RivalMove::positionAfter));
            gainedOn.sort(Comparator.comparingInt(RivalMove::positionAfter));

            boolean neutral = neutralisedLaps.contains(p) || neutralisedLaps.contains(p - 1);
            impacts.add(new PitStopImpact(stop.stopNumber(), p, stop.durationMs(), timeLost, positionBefore, afterOut,
                    later, lost, neutral, behind.stream().limit(5).toList(), withinLoss, passedBy, gainedOn));
        }
        return new PitImpactReport(d, impacts);
    }

    /**
     * Estimates the effect of moving one pit stop by {@code lapsEarlier} laps (negative = later). Lap times on each
     * stint are modelled as intercept + slope * tyreAge, fitted on that stint's clean laps; pit-lane loss is carried
     * over from the real stop. SC/VSC laps keep their real time in both scenarios.
     */
    public WhatIfResult whatIf(String driverId, int stopNumber, int lapsEarlier) {
        DriverInfo d = driver(driverId);
        List<PitRow> stops = stops(driverId);
        if (stops.isEmpty()) throw new IllegalArgumentException(d.code() + " made no pit stops in this race");
        if (stopNumber < 1 || stopNumber > stops.size()) {
            throw new IllegalArgumentException(d.code() + " made " + stops.size() + " stop(s); stop " + stopNumber + " does not exist");
        }
        if (lapsEarlier == 0) throw new IllegalArgumentException("lapsEarlier must not be 0");

        NavigableMap<Integer, LapRow> laps = laps(driverId);
        int lastLap = laps.lastKey();
        int idx = stopNumber - 1;
        int p = stops.get(idx).lap();
        int newP = p - lapsEarlier;
        int stintAStart = idx == 0 ? 1 : stops.get(idx - 1).lap() + 1;
        int stintBEnd = idx + 1 < stops.size() ? stops.get(idx + 1).lap() : lastLap;

        if (newP <= stintAStart) {
            throw new IllegalArgumentException("Pitting on lap " + newP + " would leave no stint before it (previous stint starts lap " + stintAStart + ")");
        }
        if (newP + 1 >= stintBEnd || p + 1 > lastLap) {
            throw new IllegalArgumentException("Pitting on lap " + newP + " collides with the next stop or the finish (lap " + stintBEnd + ")");
        }

        List<String> warnings = new ArrayList<>();
        Fit fitA = stintFit(driverId, stintAStart, p, stintAStart - 1);
        Fit fitB = stintFit(driverId, p + 1, stintBEnd, p);
        if (fitA == null) {
            fitA = flatFit(driverId, stintAStart, p);
            warnings.add("Too few clean laps before the stop to measure degradation; assumed flat pace for that stint");
        }
        if (fitB == null) {
            fitB = flatFit(driverId, p + 1, stintBEnd);
            warnings.add("Too few clean laps after the stop to measure degradation; assumed flat pace for that stint");
        }
        if (fitA == null || fitB == null) {
            throw new IllegalStateException("Not enough clean laps around stop " + stopNumber + " to build a model");
        }

        int segStart = Math.min(p, newP);
        for (int lap = segStart; lap <= stintBEnd; lap++) {
            if (!laps.containsKey(lap)) throw new IllegalStateException("Missing lap time for lap " + lap);
        }

        double predAtP = fitA.at(p - stintAStart + 1);
        double predB1 = fitB.at(1);
        double pitLoss = laps.get(p).timeMs() + laps.get(p + 1).timeMs() - predAtP - predB1;

        long original = 0;
        double modelled = 0;
        for (int lap = segStart; lap <= stintBEnd; lap++) {
            long actual = laps.get(lap).timeMs();
            original += actual;
            boolean neutral = neutralisedLaps.contains(lap);
            double t;
            if (lap < newP) {
                t = (lap < p || neutral) ? actual : fitA.at(lap - stintAStart + 1);
            } else if (lap == newP) {
                double base = (lap < p || neutral) ? actual : fitA.at(lap - stintAStart + 1);
                t = base + pitLoss;
            } else {
                t = neutral ? actual : fitB.at(lap - newP);
            }
            modelled += t;
        }
        long modelledMs = Math.round(modelled);
        long delta = modelledMs - original;

        int newStintLength = stintBEnd - newP;
        int oldStintLength = stintBEnd - p;
        if (lapsEarlier > 0 && newStintLength > oldStintLength + 3) {
            warnings.add("The next stint becomes " + newStintLength + " laps long (was " + oldStintLength
                    + "); degradation beyond the observed tyre age is extrapolated linearly");
        }
        if (neutralisedLaps.contains(p) || neutralisedLaps.contains(p - 1)) {
            warnings.add("The real stop on lap " + p + " was made under a suspected Safety Car/VSC, so its pit loss was "
                    + "unusually small; a green-flag stop on lap " + newP + " would likely cost more than modelled");
        }
        if (neutralisedLaps.contains(newP) || neutralisedLaps.contains(newP - 1)) {
            warnings.add("Lap " + newP + " was itself under a suspected Safety Car/VSC; a stop there would likely cost less than modelled");
        }

        Integer estimated = null;
        List<String> changes = new ArrayList<>();
        Long myFinal = cumulative(driverId, lastLap);
        if (d.position() != null && myFinal != null) {
            long newFinal = myFinal + delta;
            int pos = d.position();
            for (String other : lapsByDriver.keySet()) {
                if (other.equals(driverId)) continue;
                DriverInfo o = driversById.get(other);
                Long theirs = cumulative(other, lastLap);
                if (o == null || o.position() == null || theirs == null) continue;
                boolean wasAhead = o.position() < d.position();
                if (wasAhead && newFinal < theirs) {
                    pos--;
                    changes.add("would finish ahead of " + o.code() + " (was " + fmtSec(theirs - myFinal) + " behind them)");
                } else if (!wasAhead && newFinal > theirs) {
                    pos++;
                    changes.add("would finish behind " + o.code() + " (was " + fmtSec(myFinal - theirs) + " ahead of them)");
                }
            }
            estimated = pos;
        }

        List<String> assumptions = List.of(
                "Lap time = fitted intercept + degradation slope x tyre age, per stint, from clean green-flag laps",
                "Pit-lane loss is the same as the real stop (" + fmtSec(pitLoss) + " incl. in/out lap)",
                "No traffic, no rival reaction and no change in Safety Car timing",
                "Tyre compounds are unknown (not in Jolpica data); the same compounds are assumed",
                "Fuel burn is only captured implicitly through each stint's fitted slope");

        return new WhatIfResult(d, stopNumber, p, newP, segStart, stintBEnd, original, modelledMs, delta,
                round1(fitA.slope), round1(fitB.slope), Math.round(pitLoss), d.position(), estimated, changes,
                warnings, assumptions);
    }

    public Comparison compare(String firstId, String secondId) {
        DriverStrategy a = strategy(firstId);
        DriverStrategy b = strategy(secondId);
        Map<Integer, LapRow> cleanA = cleanLaps(firstId);
        Map<Integer, LapRow> cleanB = cleanLaps(secondId);
        List<Long> deltas = new ArrayList<>();
        int aFaster = 0;
        int bFaster = 0;
        for (Map.Entry<Integer, LapRow> e : cleanA.entrySet()) {
            LapRow other = cleanB.get(e.getKey());
            if (other == null) continue;
            long diff = e.getValue().timeMs() - other.timeMs();
            deltas.add(diff);
            if (diff < 0) aFaster++;
            else if (diff > 0) bFaster++;
        }
        List<GapPoint> gaps = new ArrayList<>();
        NavigableMap<Integer, Long> cumA = cumulativeByDriver.getOrDefault(firstId, new TreeMap<>());
        NavigableMap<Integer, Long> cumB = cumulativeByDriver.getOrDefault(secondId, new TreeMap<>());
        for (Map.Entry<Integer, Long> e : cumA.entrySet()) {
            Long other = cumB.get(e.getKey());
            if (other != null) gaps.add(new GapPoint(e.getKey(), e.getValue() - other));
        }
        return new Comparison(a, b, new HeadToHead(deltas.size(), round1(mean(deltas)), aFaster, bFaster), gaps);
    }

    public RaceSummary summary() {
        List<DriverInfo> classified = driversById.values().stream()
                .sorted(Comparator.comparing(DriverInfo::position, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DriverInfo::lapsCompleted, Comparator.reverseOrder()))
                .toList();
        String winner = classified.stream().filter(d -> Integer.valueOf(1).equals(d.position()))
                .map(d -> d.name() + " (" + d.team() + ")").findFirst().orElse(null);
        List<String> podium = classified.stream().filter(d -> d.position() != null && d.position() <= 3)
                .map(d -> "P" + d.position() + " " + d.name() + " (" + d.team() + ")").toList();
        FastestLap fastest = driversById.values().stream().filter(d -> Integer.valueOf(1).equals(d.fastestLapRank()))
                .findFirst().map(d -> new FastestLap(d.code(), d.fastestLapTime(), fastestLapNumber(d.id()))).orElse(null);

        List<DriverLine> lines = classified.stream().map(d -> {
            List<Stint> stints = stints(d.id());
            List<PitRow> stops = stops(d.id());
            return new DriverLine(d.code(), d.name(), d.team(), d.grid(), d.positionText(), d.status(), stops.size(),
                    stops.stream().map(PitRow::lap).toList(), stints.stream().map(Stint::laps).toList());
        }).toList();

        Map<Integer, Long> stopCounts = driversById.values().stream()
                .filter(d -> d.position() != null)
                .collect(Collectors.groupingBy(d -> stops(d.id()).size(), TreeMap::new, Collectors.counting()));
        String common = stopCounts.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + "-stop (" + e.getValue() + " classified drivers)").orElse(null);

        int fieldSize = driversById.size();
        List<Movement> moves = driversById.values().stream().filter(d -> d.position() != null).map(d -> {
            int grid = d.grid() == 0 ? fieldSize : d.grid(); // grid 0 = pit-lane start
            return new Movement(d.code(), grid, d.position(), grid - d.position());
        }).toList();
        List<Movement> gainers = moves.stream().filter(m -> m.change() > 0)
                .sorted(Comparator.comparingInt(Movement::change).reversed()).limit(3).toList();
        List<Movement> losers = moves.stream().filter(m -> m.change() < 0)
                .sorted(Comparator.comparingInt(Movement::change)).limit(3).toList();

        List<String> retirements = driversById.values().stream().filter(d -> d.position() == null)
                .map(d -> d.code() + ": " + d.status() + " after " + d.lapsCompleted() + " laps").toList();

        List<NeutralisedStop> underSc = new ArrayList<>();
        FastestStop fastestStop = null;
        for (PitRow stop : data.stops()) {
            if (neutralisedLaps.contains(stop.lap()) || neutralisedLaps.contains(stop.lap() - 1)) {
                underSc.add(new NeutralisedStop(code(stop.driverId()), stop.lap(), stop.stopNumber()));
            }
            if (stop.durationMs() > 0 && (fastestStop == null || stop.durationMs() < fastestStop.pitLaneMs())) {
                fastestStop = new FastestStop(code(stop.driverId()), stop.durationMs(), stop.lap());
            }
        }
        return new RaceSummary(data.race(), winner, podium, fastest, lines, common, gainers, losers, retirements,
                ranges(neutralisedLaps), underSc, fastestStop);
    }

    public LapChartData lapChart() {
        List<DriverLaps> series = driversById.values().stream()
                .filter(d -> lapsByDriver.containsKey(d.id()))
                .map(d -> new DriverLaps(d.id(), d.code(), d.name(), d.team(),
                        laps(d.id()).values().stream().map(r -> new LapPoint(r.lap(), r.timeMs(), r.position())).toList(),
                        stops(d.id()).stream().map(PitRow::lap).toList()))
                .toList();
        return new LapChartData(data.race().totalLaps(), neutralisedLaps.stream().sorted().toList(), series);
    }

    // ---------------------------------------------------------------- internals

    NavigableMap<Integer, LapRow> laps(String driverId) {
        return lapsByDriver.getOrDefault(driverId, new TreeMap<>());
    }

    List<PitRow> stops(String driverId) {
        return stopsByDriver.getOrDefault(driverId, List.of());
    }

    /** Laps usable for pace: not lap 1, not in/out laps, not SC/VSC, and within 107% of the driver's median. */
    Map<Integer, LapRow> cleanLaps(String driverId) {
        Set<Integer> pitAffected = new HashSet<>();
        for (PitRow stop : stops(driverId)) {
            pitAffected.add(stop.lap());
            pitAffected.add(stop.lap() + 1);
        }
        List<LapRow> candidates = laps(driverId).values().stream()
                .filter(r -> r.lap() > 1 && !pitAffected.contains(r.lap()) && !neutralisedLaps.contains(r.lap()))
                .toList();
        Double med = median(candidates.stream().map(LapRow::timeMs).toList());
        Map<Integer, LapRow> clean = new TreeMap<>();
        if (med == null) return clean;
        for (LapRow r : candidates) {
            if (r.timeMs() <= med * OUTLIER_FACTOR) clean.put(r.lap(), r);
        }
        return clean;
    }

    private Set<Integer> detectNeutralisedLaps() {
        Map<Integer, List<Long>> byLap = new TreeMap<>();
        for (LapRow row : data.laps()) {
            if (row.lap() > 1) byLap.computeIfAbsent(row.lap(), k -> new ArrayList<>()).add(row.timeMs());
        }
        Map<Integer, Double> lapMedians = new TreeMap<>();
        byLap.forEach((lap, times) -> lapMedians.put(lap, median(times)));
        Double typical = median(new ArrayList<>(lapMedians.values()));
        Set<Integer> result = new TreeSet<>();
        if (typical == null) return result;
        lapMedians.forEach((lap, med) -> {
            if (med > typical * NEUTRALISED_FACTOR) result.add(lap);
        });
        return result;
    }

    private Optional<Integer> positionAt(String driverId, int lap) {
        LapRow row = laps(driverId).get(lap);
        return row == null ? Optional.empty() : Optional.of(row.position());
    }

    private Long cumulative(String driverId, int lap) {
        NavigableMap<Integer, Long> c = cumulativeByDriver.get(driverId);
        return c == null ? null : c.get(lap);
    }

    private String code(String driverId) {
        DriverInfo d = driversById.get(driverId);
        return d == null ? driverId : d.code();
    }

    private Integer fastestLapNumber(String driverId) {
        return laps(driverId).values().stream().min(Comparator.comparingLong(LapRow::timeMs)).map(LapRow::lap).orElse(null);
    }

    /** Median clean lap within five laps either side of {@code lap}, falling back to the whole race. */
    private Double referencePace(String driverId, int lap) {
        Map<Integer, LapRow> clean = cleanLaps(driverId);
        List<Long> near = clean.values().stream().filter(r -> Math.abs(r.lap() - lap) <= 5).map(LapRow::timeMs).toList();
        if (near.size() >= 2) return median(near);
        return median(clean.values().stream().map(LapRow::timeMs).toList());
    }

    /** Explains how a rival swapped places with {@code driverId} around the stop on {@code lap}. */
    private String explainRival(String driverId, String rivalId, int lap) {
        List<PitRow> rivalStops = stops(rivalId).stream().filter(s -> Math.abs(s.lap() - lap) <= 5).toList();
        if (rivalStops.isEmpty()) return "did not pit within 5 laps of lap " + lap + " (gained track position while the other car was in the pits)";
        PitRow s = rivalStops.get(0);
        if (s.lap() < lap) return "pitted earlier on lap " + s.lap() + " (undercut)";
        if (s.lap() == lap) return "pitted on the same lap and came out ahead";
        // Rival had not stopped yet: check who was ahead once the rival had made its own stop.
        Optional<Integer> mine = positionAt(driverId, s.lap() + 1);
        Optional<Integer> theirs = positionAt(rivalId, s.lap() + 1);
        String base = "had not stopped yet (pitted on lap " + s.lap() + ")";
        if (mine.isEmpty() || theirs.isEmpty()) return base;
        return mine.get() < theirs.get()
                ? base + "; the place was temporary, the other car was back ahead after that stop"
                : base + "; still ahead after its own stop (overcut worked)";
    }

    private Fit stintFit(String driverId, int fromLap, int toLap, int ageOffset) {
        List<long[]> points = new ArrayList<>();
        cleanLaps(driverId).values().stream().filter(r -> r.lap() >= fromLap && r.lap() <= toLap)
                .forEach(r -> points.add(new long[]{r.lap() - ageOffset, r.timeMs()}));
        return fit(points);
    }

    private Fit flatFit(String driverId, int fromLap, int toLap) {
        Double med = median(cleanLaps(driverId).values().stream().filter(r -> r.lap() >= fromLap && r.lap() <= toLap)
                .map(LapRow::timeMs).toList());
        return med == null ? null : new Fit(med, 0);
    }

    record Fit(double intercept, double slope) {
        double at(double x) {
            return intercept + slope * x;
        }
    }

    static Fit fit(List<long[]> points) {
        if (points.size() < MIN_FIT_POINTS) return null;
        double n = points.size();
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (long[] p : points) {
            sx += p[0];
            sy += p[1];
            sxx += (double) p[0] * p[0];
            sxy += (double) p[0] * p[1];
        }
        double denom = n * sxx - sx * sx;
        if (denom == 0) return null;
        double slope = (n * sxy - sx * sy) / denom;
        return new Fit((sy - slope * sx) / n, slope);
    }

    static Double mean(List<Long> values) {
        if (values.isEmpty()) return null;
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    static Double median(List<? extends Number> values) {
        if (values.isEmpty()) return null;
        double[] sorted = values.stream().mapToDouble(Number::doubleValue).sorted().toArray();
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    static Double stdDev(List<Long> values) {
        if (values.size() < 2) return null;
        double m = mean(values);
        double ss = values.stream().mapToDouble(v -> (v - m) * (v - m)).sum();
        return Math.sqrt(ss / (values.size() - 1));
    }

    private static Double round1(Double v) {
        return v == null ? null : Math.round(v * 10) / 10.0;
    }

    private static String fmtSec(double ms) {
        return String.format(Locale.ROOT, "%.3fs", ms / 1000.0);
    }

    static List<String> ranges(Collection<Integer> laps) {
        List<Integer> sorted = laps.stream().sorted().toList();
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            int start = sorted.get(i);
            int end = start;
            while (i + 1 < sorted.size() && sorted.get(i + 1) == end + 1) end = sorted.get(++i);
            out.add(start == end ? "lap " + start : "laps " + start + "-" + end);
            i++;
        }
        return out;
    }
}
