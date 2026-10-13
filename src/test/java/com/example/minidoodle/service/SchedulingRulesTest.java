package com.example.minidoodle.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingRulesTest {
    private static final Instant START = Instant.parse("2032-03-01T10:00:00Z");

    @Test
    void validHalfOpenIntervalAccepted() {
        assertDoesNotThrow(() -> SchedulingRules.validWindow(START, START.plusSeconds(1)));
    }

    @Test
    void nullOrReversedIntervalRejected() {
        assertThrows(IllegalArgumentException.class, () -> SchedulingRules.validWindow(null, START));
        assertThrows(IllegalArgumentException.class, () -> SchedulingRules.validWindow(START, null));
        assertThrows(IllegalArgumentException.class, () -> SchedulingRules.validWindow(START, START));
        assertThrows(IllegalArgumentException.class, () -> SchedulingRules.validWindow(START, START.minusSeconds(1)));
    }

    @Test
    void availabilityWindowAtLimitAcceptedAndBeyondLimitRejected() {
        assertDoesNotThrow(() -> SchedulingRules.validQueryWindow(START, START.plusSeconds(366L * 86400)));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulingRules.validQueryWindow(START, START.plusSeconds(367L * 86400)));
    }
}
