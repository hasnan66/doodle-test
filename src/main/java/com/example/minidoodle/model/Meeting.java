package com.example.minidoodle.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Meeting(UUID id, UUID slotId, UUID organizerId, String title,
                      String description, Instant startsAt, Instant endsAt,
                      List<UUID> participants, long version) {
    public Meeting {
        participants = List.copyOf(participants);
    }
}
