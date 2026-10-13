package com.example.minidoodle.controller.dto;

import com.example.minidoodle.model.SlotStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record SlotUpdateRequest(@NotNull Instant startsAt, @NotNull Instant endsAt,
                                @NotNull SlotStatus status,
                                @NotNull @PositiveOrZero Long version) { }
