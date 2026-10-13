package com.example.minidoodle.service;

import java.time.Duration;
import java.time.Instant;

/** Shared time interval validation. All intervals are half-open: [start, end). */
final class SchedulingRules {
    private SchedulingRules() { }

    static void validWindow(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
    }

    static void validQueryWindow(Instant start, Instant end) {
        validWindow(start, end);
        if (Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0) {
            throw new IllegalArgumentException("Availability query cannot exceed 366 days");
        }
    }
}
