package com.example.minidoodle.controller.dto;

import com.example.minidoodle.model.SlotStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SlotRequest(@NotNull Instant startsAt, @NotNull Instant endsAt,
                          @NotNull SlotStatus status) { }
