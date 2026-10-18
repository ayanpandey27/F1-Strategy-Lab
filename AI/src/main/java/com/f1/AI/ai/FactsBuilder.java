package com.f1.AI.ai;

import com.f1.AI.analysis.AnalysisModels.*;
import com.f1.AI.analysis.StrategyAnalyzer;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Renders analysis results as compact plain-text facts. Small local models follow short labelled lines far more
 * reliably than large JSON blobs.
 */
public final class FactsBuilder {

    private final StringBuilder out = new StringBuilder();

    public FactsBuilder summary(RaceSummary s) {
        return summary(s, true);
    }

    /** {@code withClassification=false} keeps the prompt short when the question is about specific drivers. */
    public FactsBuilder summary(RaceSummary s, boolean withClassification) {
        section("RACE");
        line("%s %d (round %d) at %s, %s. %d laps.", s.race().name(), s.race().season(), s.race().round(),
                s.race().circuit(), s.race().country(), s.race().totalLaps());
        line("Winner: %s", s.winner());
        line("Podium: %s", String.join("; ", s.podium()));
        if (s.fastestLap() != null) line("Fastest lap: %s %s (lap %s)", s.fastestLap().code(), s.fastestLap().time(), s.fastestLap().lap());
        line("Most common strategy: %s", s.mostCommonStopCount());
        line("Suspected Safety Car/VSC laps: %s", s.neutralisedLapRanges().isEmpty() ? "none detected" : String.join(", ", s.neutralisedLapRanges()));
        if (!s.stopsUnderNeutralisation().isEmpty()) {
            line("Stops under suspected SC/VSC: %s", s.stopsUnderNeutralisation().stream()
                    .map(n -> n.code() + " lap " + n.lap()).collect(Collectors.joining(", ")));
        }
        if (s.fastestPitStop() != null) line("Quickest pit-lane time: %s %s (lap %d)", s.fastestPitStop().code(), sec(s.fastestPitStop().pitLaneMs()), s.fastestPitStop().lap());
        line("Biggest gainers (grid->finish): %s", moves(s.biggestGainers()));
        line("Biggest losers (grid->finish): %s", moves(s.biggestLosers()));
        if (!s.retirements().isEmpty()) line("Retirements: %s", String.join("; ", s.retirements()));
        if (!withClassification) return this;
        section("CLASSIFICATION AND STRATEGIES");
        for (DriverLine d : s.classification()) {
            line("%s (%s): grid %d, finish %s, %s, %d stop(s) on laps %s, stint lengths %s", d.code(),
                    d.team(), d.grid(), d.finish(), d.status(), d.stops(), d.pitLaps(), d.stintLengths());
        }
        return this;
    }

    public FactsBuilder strategy(DriverStrategy s) {
        section("STRATEGY: " + s.driver().code() + " (" + s.driver().name() + ", " + s.driver().team() + ")");
        line("Grid %d, finish %s, status %s, %d stop(s) on laps %s", s.driver().grid(), s.driver().positionText(),
                s.driver().status(), s.stops(), s.pitLaps());
        line("Clean-lap average %s, std dev %s, coefficient of variation %s%%", sec(s.cleanAvgMs()),
                sec(s.cleanStdDevMs()), s.coefficientOfVariationPct());
        for (Stint st : s.stints()) {
            line("Stint %d: laps %d-%d (%d laps, %d clean), avg %s, median %s, std dev %s, best %s, degradation %s per lap of tyre age",
                    st.number(), st.startLap(), st.endLap(), st.laps(), st.cleanLaps(), sec(st.avgMs()),
                    sec(st.medianMs()), sec(st.stdDevMs()), st.bestMs() == null ? "n/a" : sec(st.bestMs().doubleValue()),
                    st.degradationMsPerLap() == null ? "n/a" : signed(st.degradationMsPerLap()));
        }
        return this;
    }

    public FactsBuilder pitImpact(PitImpactReport r) {
        section("PIT STOP IMPACT: " + r.driver().code());
        if (r.stops().isEmpty()) line("No pit stops.");
        String me = r.driver().code();
        for (PitStopImpact p : r.stops()) {
            line("Stop %d on lap %d: pit-lane time %s; %s lost %s in total compared with a normal lap (in-lap + out-lap)%s",
                    p.stopNumber(), p.lap(), sec(p.pitLaneMs()), me, sec(p.timeLostMs()),
                    p.underNeutralisation() ? "; the stop was made under a suspected SC/VSC" : "");
            line("  %s was P%d before the stop, P%s after the out-lap and P%s three laps later",
                    me, p.positionBefore(), num(p.positionAfterOutLap()), num(p.positionThreeLapsLater()));
            line("  KEY FINDING: %s", keyFinding(me, p));
            if (!p.carsWithinPitLoss().isEmpty()) {
                line("  Cars BEHIND %s before the stop by less than the %s it lost: %s", me, sec(p.timeLostMs()),
                        gaps(p.carsWithinPitLoss()));
            }
            for (RivalMove m : p.passedBy()) {
                line("  %s got ahead of %s: %s", m.code(), me, m.explanation());
            }
            for (RivalMove m : p.gainedOn()) {
                line("  %s got ahead of %s: %s", me, m.code(), m.explanation());
            }
        }
        return this;
    }

    /** One deterministic sentence explaining the stop, so a small model does not have to infer the cause. */
    static String keyFinding(String me, PitStopImpact p) {
        int lost = p.positionsLost();
        if (lost <= 0) {
            return me + " lost no positions over this stop" + (lost < 0 ? " and gained " + (-lost) : "") + ".";
        }
        long temporary = p.passedBy().stream().filter(m -> m.explanation().contains("temporary")).count();
        long undercut = p.passedBy().stream().filter(m -> m.explanation().contains("undercut")).count();
        long stayedOut = p.passedBy().stream().filter(m -> m.explanation().contains("did not pit")).count();
        long lasting = p.passedBy().size() - temporary;
        StringBuilder sb = new StringBuilder(me + " dropped " + lost + " place(s) because the stop cost "
                + sec(p.timeLostMs()) + " and " + p.carsWithinPitLoss().size() + " car(s) were closer behind than that.");
        if (temporary > 0) {
            sb.append(' ').append(temporary).append(" of the cars that got ahead had not pitted yet and fell back behind ")
                    .append(me).append(" after their own stops, so those places were only temporary.");
        }
        if (lasting > 0) {
            sb.append(' ').append(lasting).append(" place(s) were lasting");
            if (undercut > 0) sb.append(", ").append(undercut).append(" of them to an undercut");
            if (stayedOut > 0) sb.append(", ").append(stayedOut).append(" to cars that did not stop in that window");
            sb.append('.');
        }
        return sb.toString();
    }

    static String compareFinding(Comparison c) {
        DriverStrategy a = c.first();
        DriverStrategy b = c.second();
        StringBuilder sb = new StringBuilder();
        Double delta = c.headToHead().meanDeltaMs();
        if (delta != null && delta != 0) {
            DriverStrategy faster = delta < 0 ? a : b;
            sb.append(faster.driver().code()).append(" was faster by ").append(sec(Math.abs(delta)))
                    .append(" per lap on average over ").append(c.headToHead().commonCleanLaps()).append(" laps where both ran cleanly. ");
        }
        Double covA = a.coefficientOfVariationPct();
        Double covB = b.coefficientOfVariationPct();
        if (covA != null && covB != null && !covA.equals(covB)) {
            DriverStrategy steadier = covA < covB ? a : b;
            sb.append(steadier.driver().code()).append(" was more consistent (lap-time variation ")
                    .append(Math.min(covA, covB)).append("% vs ").append(Math.max(covA, covB)).append("%). ");
        }
        sb.append(stintLine(a)).append(' ').append(stintLine(b));
        return sb.toString().trim();
    }

    private static String stintLine(DriverStrategy s) {
        String stints = s.stints().stream()
                .map(st -> st.laps() + " laps" + (st.degradationMsPerLap() == null ? "" : " (tyres slowing " + signed(st.degradationMsPerLap()) + "/lap)"))
                .collect(Collectors.joining(", "));
        return s.driver().code() + " ran " + s.stints().size() + " stints: " + stints + ".";
    }

    private static String num(Integer p) {
        return p == null ? "n/a" : String.valueOf(p);
    }

    public FactsBuilder comparison(Comparison c) {
        String a = c.first().driver().code();
        String b = c.second().driver().code();
        section("HEAD TO HEAD: " + a + " vs " + b);
        HeadToHead h = c.headToHead();
        line("KEY FINDING: %s", compareFinding(c));
        line("Laps where both were on clean laps: %d. Mean lap delta %s minus %s: %s (negative = %s faster)",
                h.commonCleanLaps(), a, b, h.meanDeltaMs() == null ? "n/a" : signed(h.meanDeltaMs()), a);
        line("%s faster on %d of those laps, %s faster on %d", a, h.firstDriverFasterLaps(), b, h.secondDriverFasterLaps());
        List<GapPoint> gaps = c.gapByLap();
        if (!gaps.isEmpty()) {
            int step = Math.max(1, gaps.size() / 8);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < gaps.size(); i += step) appendGap(sb, gaps.get(i));
            if ((gaps.size() - 1) % step != 0) appendGap(sb, gaps.get(gaps.size() - 1));
            line("Race-time gap %s minus %s by lap (negative = %s ahead): %s", a, b, a, sb.toString().trim());
        }
        return this;
    }

    public FactsBuilder whatIf(WhatIfResult w) {
        section("WHAT-IF: " + w.driver().code() + " stop " + w.stopNumber() + " on lap " + w.newPitLap() + " instead of lap " + w.originalPitLap());
        line("Modelled laps %d-%d: real %s, modelled %s, difference %s (negative = faster)", w.segmentStartLap(),
                w.segmentEndLap(), sec(w.originalSegmentMs()), sec(w.newSegmentMs()), signed(w.deltaMs()));
        line("Degradation before the stop %s per lap, after the stop %s per lap; estimated pit loss %s",
                w.oldStintDegradationMsPerLap() == null ? "n/a" : signed(w.oldStintDegradationMsPerLap()),
                w.newStintDegradationMsPerLap() == null ? "n/a" : signed(w.newStintDegradationMsPerLap()),
                sec(w.estimatedPitLossMs()));
        line("Actual finish %s, estimated finish %s", pos(w.actualPosition()), pos(w.estimatedPosition()));
        if (!w.positionChanges().isEmpty()) line("Position changes: %s", String.join("; ", w.positionChanges()));
        for (String warn : w.warnings()) line("Warning: %s", warn);
        line("Assumptions: %s", String.join("; ", w.assumptions()));
        return this;
    }

    public FactsBuilder note(String text) {
        line("Note: %s", text);
        return this;
    }

    public String build() {
        return out.toString().trim();
    }

    private void section(String title) {
        out.append('\n').append("## ").append(title).append('\n');
    }

    private void line(String format, Object... args) {
        out.append("- ").append(String.format(Locale.ROOT, format, args)).append('\n');
    }

    private static void appendGap(StringBuilder sb, GapPoint g) {
        sb.append("L").append(g.lap()).append(' ').append(signed(g.gapMs())).append(", ");
    }

    private static String moves(List<Movement> moves) {
        if (moves.isEmpty()) return "none";
        return moves.stream().map(m -> m.code() + " P" + m.grid() + "->P" + m.finish() + " (" + (m.change() > 0 ? "+" : "") + m.change() + ")")
                .collect(Collectors.joining(", "));
    }

    private static String gaps(List<GapBehind> gaps) {
        if (gaps.isEmpty()) return "none";
        return gaps.stream().map(g -> g.code() + " " + sec(g.gapMs())).collect(Collectors.joining(", "));
    }

    private static String pos(Integer p) {
        return p == null ? "n/a" : "P" + p;
    }

    static String sec(Double ms) {
        return ms == null ? "n/a" : String.format(Locale.ROOT, "%.3fs", ms / 1000.0);
    }

    static String sec(long ms) {
        return sec((double) ms);
    }

    static String signed(double ms) {
        return String.format(Locale.ROOT, "%+.3fs", ms / 1000.0);
    }
}
