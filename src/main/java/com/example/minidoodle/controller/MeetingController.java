package com.example.minidoodle.controller;

import com.example.minidoodle.controller.dto.MeetingRequest;
import com.example.minidoodle.model.Meeting;
import com.example.minidoodle.service.MeetingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}")
public class MeetingController {
    private final MeetingService meetings;

    public MeetingController(MeetingService meetings) { this.meetings = meetings; }

    @PostMapping("/slots/{slotId}/meeting")
    public ResponseEntity<Meeting> book(@PathVariable UUID userId, @PathVariable UUID slotId,
                                         @Valid @RequestBody MeetingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetings.book(userId, slotId,
                request.title(), request.description(), request.participants()));
    }

    @PutMapping("/meetings/{meetingId}")
    public Meeting edit(@PathVariable UUID userId, @PathVariable UUID meetingId,
                        @Valid @RequestBody MeetingRequest request) {
        return meetings.edit(userId, meetingId,
                request.title(), request.description(), request.participants());
    }
}
