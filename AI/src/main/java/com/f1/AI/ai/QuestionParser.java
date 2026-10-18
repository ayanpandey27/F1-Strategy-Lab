package com.f1.AI.ai;

import com.f1.AI.analysis.RaceData.DriverInfo;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls structured intent out of a free-text question (which drivers, whether it is a what-if) so the backend can
 * compute the right facts before the LLM sees anything. Deliberately simple and deterministic.
 */
public final class QuestionParser {

    private static final Map<String, Integer> WORD_NUMBERS = Map.ofEntries(
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
            Map.entry("nine", 9), Map.entry("ten", 10));
    private static final Pattern WHAT_IF = Pattern.compile(
            "(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+laps?\\s+(earlier|sooner|before|later|after)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STOP_ORDINAL = Pattern.compile(
            "\\b(first|second|third|fourth|1st|2nd|3rd|4th)\\s+(pit\\s*)?stop", Pattern.CASE_INSENSITIVE);
    private static final Pattern STOP_NUMBER = Pattern.compile("\\bstop\\s*#?\\s*(\\d)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> ORDINALS = Map.of("first", 1, "1st", 1, "second", 2, "2nd", 2,
            "third", 3, "3rd", 3, "fourth", 4, "4th", 4);

    private QuestionParser() {
    }

    public record WhatIf(int lapsEarlier, int stopNumber) {
    }

    public record ParsedQuestion(List<String> driverIds, Optional<WhatIf> whatIf) {
    }

    public static ParsedQuestion parse(String question, Collection<DriverInfo> drivers, List<String> selectedDriverIds) {
        String normalized = fold(question);
        // Keep mention order so "compare A and B" keeps A first.
        TreeMap<Integer, String> mentions = new TreeMap<>();
        for (DriverInfo d : drivers) {
            int at = firstMention(question, normalized, d);
            if (at >= 0) mentions.putIfAbsent(at, d.id());
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>(mentions.values());
        if (selectedDriverIds != null) ids.addAll(selectedDriverIds);

        Optional<WhatIf> whatIf = Optional.empty();
        Matcher m = WHAT_IF.matcher(question);
        if (m.find()) {
            String n = m.group(1).toLowerCase(Locale.ROOT);
            int laps = WORD_NUMBERS.containsKey(n) ? WORD_NUMBERS.get(n) : Integer.parseInt(n);
            String dir = m.group(2).toLowerCase(Locale.ROOT);
            if (dir.equals("later") || dir.equals("after")) laps = -laps;
            whatIf = Optional.of(new WhatIf(laps, stopNumber(question)));
        }
        return new ParsedQuestion(new ArrayList<>(ids), whatIf);
    }

    static int stopNumber(String question) {
        Matcher ord = STOP_ORDINAL.matcher(question);
        if (ord.find()) return ORDINALS.get(ord.group(1).toLowerCase(Locale.ROOT));
        Matcher num = STOP_NUMBER.matcher(question);
        if (num.find()) return Integer.parseInt(num.group(1));
        return 1;
    }

    private static int firstMention(String original, String normalized, DriverInfo d) {
        int best = -1;
        String[] nameParts = fold(d.name()).split("\\s+");
        String family = nameParts[nameParts.length - 1];
        best = minPositive(best, wordIndex(normalized, family));
        best = minPositive(best, wordIndex(normalized, fold(d.name())));
        best = minPositive(best, wordIndex(normalized, fold(d.id()).replace('_', ' ')));
        // Three-letter codes only when written in capitals, to avoid matching words like "per" or "sai".
        if (d.code() != null) {
            Matcher m = Pattern.compile("\\b" + Pattern.quote(d.code()) + "\\b").matcher(original);
            if (m.find()) best = minPositive(best, m.start());
        }
        return best;
    }

    private static int wordIndex(String haystack, String needle) {
        if (needle == null || needle.length() < 3) return -1;
        Matcher m = Pattern.compile("\\b" + Pattern.quote(needle) + "('s)?\\b").matcher(haystack);
        return m.find() ? m.start() : -1;
    }

    private static int minPositive(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }
}
