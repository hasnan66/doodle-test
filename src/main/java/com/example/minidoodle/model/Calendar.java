package com.example.minidoodle.model;

import java.util.Objects;
import java.util.UUID;

/** Domain identity of the personal schedule; persistence uses the term "schedule". */
public record Calendar(UUID id, UUID userId) {
    public Calendar {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
    }
}
