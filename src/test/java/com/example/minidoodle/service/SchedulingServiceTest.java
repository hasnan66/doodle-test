package com.example.minidoodle.service;

import com.example.minidoodle.exception.ConflictException;
import com.example.minidoodle.exception.NotFoundException;
import com.example.minidoodle.model.SlotStatus;
import com.example.minidoodle.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingServiceTest {
    private static final Instant START = Instant.parse("2032-03-01T10:00:00Z");
    private static final Instant END = START.plusSeconds(3600);

    private InMemoryRepositories db;
    private UUID ada;

    @BeforeEach
    void setUp() {
        db = new InMemoryRepositories();
        ada = db.createUser("Ada");
    }

    @Test
    void createUserCreatesUserAndSchedule() {
        assertEquals("Ada", db.userRows.get(ada).getDisplayName());
        assertTrue(db.scheduleRows.values().stream()
                .anyMatch(schedule -> schedule.getUser().getId().equals(ada)));
    }

    @Test
    void createFreeSlotPersistsSlotAndReturnsOwnerAndTimes() {
        TimeSlot slot = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        assertEquals(ada, slot.userId());
        assertEquals(START, slot.startsAt());
        assertEquals(END, slot.endsAt());
        assertEquals(SlotStatus.FREE, db.slotRows.get(slot.id()).getStatus());
    }

    @Test
    void createBusySlotPersistsBusyAvailability() {
        TimeSlot slot = db.scheduling.createSlot(ada, START, END, SlotStatus.BUSY);
        assertEquals(SlotStatus.BUSY, slot.status());
    }

    @Test
    void rejectInvalidOrZeroLengthSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> db.scheduling.createSlot(ada, END, START, SlotStatus.FREE));
        assertThrows(IllegalArgumentException.class,
                () -> db.scheduling.createSlot(ada, START, START, SlotStatus.FREE));
        assertTrue(db.slotRows.isEmpty());
    }

    @Test
    void overlappingSlotsAreRejectedButAdjacentOnesAllowed() {
        db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        assertThrows(ConflictException.class,
                () -> db.scheduling.createSlot(ada, START.plusSeconds(60), END.plusSeconds(60), SlotStatus.FREE));
        db.scheduling.createSlot(ada, END, END.plusSeconds(3600), SlotStatus.FREE);
        assertEquals(2, db.slotRows.size());
    }

    @Test
    void busySlotCannotOverlapExistingParticipantMeeting() {
        UUID grace = db.createUser("Grace");
        TimeSlot source = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        db.meetingService.book(ada, source.id(), "Planning", null, List.of(grace));
        assertThrows(ConflictException.class,
                () -> db.scheduling.createSlot(grace, START, END, SlotStatus.BUSY));
        assertEquals(1, db.slotRows.size());
    }

    @Test
    void validUpdateChangesTimeAndStatus() {
        TimeSlot original = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        Instant shifted = END.plusSeconds(900);
        TimeSlot updated = db.scheduling.updateSlot(ada, original.id(), START, shifted,
                SlotStatus.BUSY, original.version());
        assertEquals(shifted, updated.endsAt());
        assertEquals(SlotStatus.BUSY, updated.status());
    }

    @Test
    void staleVersionRejectedWithoutMutation() {
        TimeSlot original = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        assertThrows(ConflictException.class, () -> db.scheduling.updateSlot(
                ada, original.id(), START, END, SlotStatus.BUSY, 999));
        assertEquals(SlotStatus.FREE, db.slotRows.get(original.id()).getStatus());
    }

    @Test
    void updateCannotOverlapAnotherSlot() {
        TimeSlot first = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        db.scheduling.createSlot(ada, END, END.plusSeconds(3600), SlotStatus.FREE);
        assertThrows(ConflictException.class, () -> db.scheduling.updateSlot(
                ada, first.id(), START, END.plusSeconds(30), SlotStatus.FREE, first.version()));
        assertEquals(END, db.slotRows.get(first.id()).getEndsAt());
    }

    @Test
    void updateRejectsBookedSlotEvenIfVersionMatches() {
        TimeSlot source = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        db.meetingService.book(ada, source.id(), "Planning", null, List.of());
        assertThrows(ConflictException.class, () -> db.scheduling.updateSlot(
                ada, source.id(), START, END, SlotStatus.FREE, source.version()));
        assertEquals(SlotStatus.BUSY, db.slotRows.get(source.id()).getStatus());
    }

    @Test
    void deleteUnbookedSlotRemovesIt() {
        TimeSlot slot = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        db.scheduling.deleteSlot(ada, slot.id());
        assertFalse(db.slotRows.containsKey(slot.id()));
    }

    @Test
    void deleteBookedSlotIsRejected() {
        TimeSlot slot = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        db.meetingService.book(ada, slot.id(), "Planning", null, List.of());
        assertThrows(ConflictException.class, () -> db.scheduling.deleteSlot(ada, slot.id()));
        assertTrue(db.slotRows.containsKey(slot.id()));
    }

    @Test
    void usersCannotUpdateOrDeleteOtherUsersSlots() {
        UUID grace = db.createUser("Grace");
        TimeSlot slot = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        assertThrows(NotFoundException.class, () -> db.scheduling.updateSlot(
                grace, slot.id(), START, END, SlotStatus.BUSY, slot.version()));
        assertThrows(NotFoundException.class, () -> db.scheduling.deleteSlot(grace, slot.id()));
    }

    @Test
    void availabilityFiltersByStatusAndOverlappingWindow() {
        db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
        db.scheduling.createSlot(ada, END, END.plusSeconds(3600), SlotStatus.BUSY);
        List<TimeSlot> free = db.scheduling.availability(ada, START, END.plusSeconds(3600), SlotStatus.FREE);
        List<TimeSlot> busy = db.scheduling.availability(ada, START, END.plusSeconds(3600), SlotStatus.BUSY);
        List<TimeSlot> all = db.scheduling.availability(ada, START.plusSeconds(30), END.plusSeconds(30), null);
        assertEquals(1, free.size());
        assertEquals(1, busy.size());
        assertEquals(2, all.size());
    }

    @Test
    void availabilityRejectsOversizedWindowAndUnknownUser() {
        assertThrows(IllegalArgumentException.class, () -> db.scheduling.availability(
                ada, START, START.plusSeconds(367L * 86400), null));
        assertThrows(NotFoundException.class, () -> db.scheduling.availability(
                UUID.randomUUID(), START, END, null));
    }
}
