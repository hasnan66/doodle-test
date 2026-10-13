package com.example.minidoodle.service;

import com.example.minidoodle.entity.ScheduleEntity;
import com.example.minidoodle.entity.TimeSlotEntity;
import com.example.minidoodle.entity.UserEntity;
import com.example.minidoodle.exception.ConflictException;
import com.example.minidoodle.exception.NotFoundException;
import com.example.minidoodle.model.SlotStatus;
import com.example.minidoodle.model.TimeSlot;
import com.example.minidoodle.repository.MeetingJpaRepository;
import com.example.minidoodle.repository.ScheduleJpaRepository;
import com.example.minidoodle.repository.TimeSlotJpaRepository;
import com.example.minidoodle.repository.UserJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SchedulingService {
    private static final int MAX_AVAILABILITY_RESULTS = 1000;

    private final UserJpaRepository users;
    private final ScheduleJpaRepository schedules;
    private final TimeSlotJpaRepository slots;
    private final MeetingJpaRepository meetings;
    private final UserLockService userLocks;

    public SchedulingService(UserJpaRepository users, ScheduleJpaRepository schedules,
                             TimeSlotJpaRepository slots, MeetingJpaRepository meetings,
                             UserLockService userLocks) {
        this.users = users;
        this.schedules = schedules;
        this.slots = slots;
        this.meetings = meetings;
        this.userLocks = userLocks;
    }

    @Transactional
    public UUID createUser(String displayName) {
        UserEntity user = users.save(new UserEntity(UUID.randomUUID(), displayName.trim()));
        schedules.save(new ScheduleEntity(UUID.randomUUID(), user));
        return user.getId();
    }

    @Transactional
    public TimeSlot createSlot(UUID userId, Instant start, Instant end, SlotStatus status) {
        SchedulingRules.validWindow(start, end);
        userLocks.lockUsers(List.of(userId));
        ScheduleEntity schedule = ownedSchedule(userId);
        ensureNoSlotOverlap(schedule.getId(), null, start, end);
        if (status == SlotStatus.BUSY) {
            ensureNoMeetingConflict(userId, null, start, end);
        }
        return toModel(slots.saveAndFlush(new TimeSlotEntity(UUID.randomUUID(),
                schedule, start, end, status)));
    }

    @Transactional
    public TimeSlot updateSlot(UUID userId, UUID slotId, Instant start, Instant end,
                               SlotStatus status, long expectedVersion) {
        SchedulingRules.validWindow(start, end);
        userLocks.lockUsers(List.of(userId));
        ScheduleEntity schedule = ownedSchedule(userId);
        TimeSlotEntity slot = slots.lockOwned(slotId, userId)
                .orElseThrow(() -> new NotFoundException("Slot not found"));
        if (slot.getVersion() != expectedVersion) {
            throw new ConflictException("Slot version is stale");
        }
        ensureNotBooked(slotId);
        ensureNoSlotOverlap(schedule.getId(), slotId, start, end);
        if (status == SlotStatus.BUSY) {
            ensureNoMeetingConflict(userId, null, start, end);
        }
        slot.change(start, end, status);
        slots.flush();
        return toModel(slot);
    }

    @Transactional
    public void deleteSlot(UUID userId, UUID slotId) {
        userLocks.lockUsers(List.of(userId));
        ownedSchedule(userId);
        TimeSlotEntity slot = slots.lockOwned(slotId, userId)
                .orElseThrow(() -> new NotFoundException("Slot not found"));
        ensureNotBooked(slotId);
        slots.delete(slot);
        slots.flush();
    }

    @Transactional(readOnly = true)
    public List<TimeSlot> availability(UUID userId, Instant from, Instant to, SlotStatus status) {
        SchedulingRules.validQueryWindow(from, to);
        if (!users.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
        var limit = PageRequest.of(0, MAX_AVAILABILITY_RESULTS);
        var results = status == null
                ? slots.findAvailability(userId, from, to, limit)
                : slots.findAvailabilityByStatus(userId, from, to, status, limit);
        return results.stream().map(this::toModel).toList();
    }

    /** Executed only while the affected user's row is locked. */
    ScheduleEntity ownedSchedule(UUID userId) {
        return schedules.lockByUserId(userId)
                .orElseThrow(() -> new NotFoundException("User schedule not found"));
    }

    void ensureNoSlotOverlap(UUID scheduleId, UUID excludedId, Instant start, Instant end) {
        long overlaps = excludedId == null
                ? slots.countOverlapping(scheduleId, start, end)
                : slots.countOverlappingExceptSlot(scheduleId, excludedId, start, end);
        if (overlaps > 0) {
            throw new ConflictException("Time slots must not overlap");
        }
    }

    void ensureNoMeetingConflict(UUID userId, UUID excludedMeetingId,
                                 Instant start, Instant end) {
        long conflicts = excludedMeetingId == null
                ? meetings.countConflictingMeetings(userId, start, end)
                : meetings.countConflictingMeetingsExcept(userId, excludedMeetingId, start, end);
        if (conflicts > 0) {
            throw new ConflictException("An existing meeting overlaps this time window");
        }
    }

    private void ensureNotBooked(UUID slotId) {
        if (meetings.existsBySourceSlot_Id(slotId)) {
            throw new ConflictException("Booked slots cannot be modified or deleted");
        }
    }

    private TimeSlot toModel(TimeSlotEntity slot) {
        return new TimeSlot(slot.getId(), slot.getSchedule().getUser().getId(),
                slot.getStartsAt(), slot.getEndsAt(), slot.getStatus(), slot.getVersion());
    }
}
