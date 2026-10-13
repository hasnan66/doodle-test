package com.example.minidoodle.controller;

import com.example.minidoodle.controller.dto.CreateUserRequest;
import com.example.minidoodle.controller.dto.SlotRequest;
import com.example.minidoodle.controller.dto.SlotUpdateRequest;
import com.example.minidoodle.controller.dto.UserResponse;
import com.example.minidoodle.model.SlotStatus;
import com.example.minidoodle.model.TimeSlot;
import com.example.minidoodle.service.SchedulingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class SchedulingController {
    private final SchedulingService scheduling;

    public SchedulingController(SchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UUID id = scheduling.createUser(request.displayName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(id, request.displayName().trim()));
    }

    @PostMapping("/{userId}/slots")
    public ResponseEntity<TimeSlot> createSlot(@PathVariable UUID userId,
                                                @Valid @RequestBody SlotRequest request) {
        TimeSlot slot = scheduling.createSlot(userId, request.startsAt(),
                request.endsAt(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(slot);
    }

    @PutMapping("/{userId}/slots/{slotId}")
    public TimeSlot updateSlot(@PathVariable UUID userId, @PathVariable UUID slotId,
                               @Valid @RequestBody SlotUpdateRequest request) {
        return scheduling.updateSlot(userId, slotId, request.startsAt(),
                request.endsAt(), request.status(), request.version());
    }

    @DeleteMapping("/{userId}/slots/{slotId}")
    public ResponseEntity<Void> deleteSlot(@PathVariable UUID userId, @PathVariable UUID slotId) {
        scheduling.deleteSlot(userId, slotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/availability")
    public List<TimeSlot> availability(@PathVariable UUID userId,
                                        @RequestParam Instant from,
                                        @RequestParam Instant to,
                                        @RequestParam(required = false) SlotStatus status) {
        return scheduling.availability(userId, from, to, status);
    }
}
