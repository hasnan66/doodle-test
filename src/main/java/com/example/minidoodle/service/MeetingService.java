package com.example.minidoodle.service;

import com.example.minidoodle.entity.MeetingEntity;
import com.example.minidoodle.entity.TimeSlotEntity;
import com.example.minidoodle.entity.UserEntity;
import com.example.minidoodle.exception.ConflictException;
import com.example.minidoodle.exception.NotFoundException;
import com.example.minidoodle.model.Meeting;
import com.example.minidoodle.model.SlotStatus;
import com.example.minidoodle.repository.MeetingJpaRepository;
import com.example.minidoodle.repository.TimeSlotJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class MeetingService {
    private final UserLockService locks;
    private final SchedulingService scheduling;
    private final MeetingJpaRepository meetings;
    private final TimeSlotJpaRepository slots;

    public MeetingService(UserLockService locks, SchedulingService scheduling,
                          MeetingJpaRepository meetings, TimeSlotJpaRepository slots) {
        this.locks = locks;
        this.scheduling = scheduling;
        this.meetings = meetings;
        this.slots = slots;
    }

    @Transactional
    public Meeting book(UUID organizerId, UUID slotId, String title,
                        String description, List<UUID> requestedParticipants) {
        Set<UUID> ids = attendeeIds(organizerId, requestedParticipants);
        Map<UUID, UserEntity> lockedUsers = locks.lockUsers(ids);
        scheduling.ownedSchedule(organizerId);
        TimeSlotEntity slot = slots.lockOwned(slotId, organizerId)
                .orElseThrow(() -> new NotFoundException("Slot not found"));
        if (slot.getStatus() != SlotStatus.FREE || meetings.existsBySourceSlot_Id(slotId)) {
            throw new ConflictException("Slot has already been booked or is busy");
        }

        for (UUID attendee : ids) {
            checkAvailability(attendee, null, slot, organizerId);
        }

        slot.book();
        MeetingEntity meeting = new MeetingEntity(UUID.randomUUID(), slot,
                lockedUsers.get(organizerId), title.trim(), description);
        meeting.setParticipants(new LinkedHashSet<>(lockedUsers.values()));
        meetings.saveAndFlush(meeting);
        slots.flush();
        return toModel(meeting);
    }

    /** Optional extension: edit details and participants, but never silently reschedule. */
    @Transactional
    public Meeting edit(UUID organizerId, UUID meetingId, String title,
                        String description, List<UUID> requestedParticipants) {
        Set<UUID> nextIds = attendeeIds(organizerId, requestedParticipants);
        // Read participants first so every affected user can be locked in UUID order.
        meetings.findByIdAndOrganizer_Id(meetingId, organizerId)
                .orElseThrow(() -> new NotFoundException("Meeting not found"));
        Set<UUID> before = new HashSet<>(meetings.participantIds(meetingId));
        Set<UUID> lockedIds = new HashSet<>(before);
        lockedIds.addAll(nextIds);
        Map<UUID, UserEntity> lockedUsers = locks.lockUsers(lockedIds);

        // A parallel edit may have changed the list between the first read and locks.
        MeetingEntity meeting = meetings.lockOwned(meetingId, organizerId)
                .orElseThrow(() -> new NotFoundException("Meeting not found"));
        if (!before.equals(new HashSet<>(meetings.participantIds(meetingId)))) {
            throw new ConflictException("Meeting participants changed; retry the update");
        }
        for (UUID attendee : nextIds) {
            checkAvailability(attendee, meetingId, meeting.getSourceSlot(), organizerId);
        }
        Set<UserEntity> attendees = new LinkedHashSet<>();
        for (UUID attendee : nextIds.stream().sorted().toList()) {
            attendees.add(lockedUsers.get(attendee));
        }
        meeting.edit(title.trim(), description, attendees);
        meetings.flush();
        return toModel(meeting);
    }

    private void checkAvailability(UUID userId, UUID excludedMeetingId,
                                   TimeSlotEntity sourceSlot, UUID organizerId) {
        // The organizer's source slot becomes BUSY as part of this very booking.
        UUID excludedSlotId = userId.equals(organizerId) ? sourceSlot.getId() : null;
        long conflicts = excludedSlotId == null
                ? slots.countBusy(userId, sourceSlot.getStartsAt(), sourceSlot.getEndsAt(), SlotStatus.BUSY)
                : slots.countBusyExceptSlot(userId, excludedSlotId,
                        sourceSlot.getStartsAt(), sourceSlot.getEndsAt(), SlotStatus.BUSY);
        if (conflicts > 0) {
            throw new ConflictException("Participant has a BUSY slot: " + userId);
        }
        scheduling.ensureNoMeetingConflict(userId, excludedMeetingId,
                sourceSlot.getStartsAt(), sourceSlot.getEndsAt());
    }

    private Set<UUID> attendeeIds(UUID organizerId, List<UUID> participants) {
        if (participants == null || participants.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("participants must be a list of valid user IDs");
        }
        Set<UUID> ids = new LinkedHashSet<>(participants);
        ids.add(organizerId);
        return ids;
    }

    private Meeting toModel(MeetingEntity meeting) {
        List<UUID> ids = new ArrayList<>(meeting.getParticipants().stream()
                .map(UserEntity::getId).sorted().toList());
        return new Meeting(meeting.getId(), meeting.getSourceSlot().getId(),
                meeting.getOrganizer().getId(), meeting.getTitle(), meeting.getDescription(),
                meeting.getStartsAt(), meeting.getEndsAt(), ids, meeting.getVersion());
    }
}
