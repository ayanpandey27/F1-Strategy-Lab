package com.f1.AI.service;

/** Converts Ergast-style time strings ("1:37.284", "22.345", "1:02:03.5") to milliseconds and back. */
public final class TimeParser {

    private TimeParser() {
    }

    public static long toMillis(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty time");
        }
        String[] parts = text.trim().split(":");
        double seconds = 0;
        for (String part : parts) {
            seconds = seconds * 60 + Double.parseDouble(part);
        }
        return Math.round(seconds * 1000);
    }

    /** Formats as m:ss.SSS (or ss.SSS under a minute). */
    public static String format(long millis) {
        boolean negative = millis < 0;
        long abs = Math.abs(millis);
        long minutes = abs / 60_000;
        double seconds = (abs % 60_000) / 1000.0;
        String body = minutes > 0 ? String.format("%d:%06.3f", minutes, seconds) : String.format("%.3f", seconds);
        return negative ? "-" + body : body;
    }

    /** Formats a signed delta in seconds, e.g. "+1.234s". */
    public static String delta(double millis) {
        return String.format("%+.3fs", millis / 1000.0);
    }
}
