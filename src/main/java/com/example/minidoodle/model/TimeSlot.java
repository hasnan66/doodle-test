package com.example.minidoodle.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TimeSlot(UUID id, UUID userId, Instant startsAt, Instant endsAt,
                       SlotStatus status, long version) {
    public TimeSlot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
    }
}
