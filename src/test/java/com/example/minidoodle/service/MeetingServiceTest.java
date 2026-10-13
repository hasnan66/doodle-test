package com.example.minidoodle.service;

import com.example.minidoodle.exception.ConflictException;
import com.example.minidoodle.exception.NotFoundException;
import com.example.minidoodle.model.Meeting;
import com.example.minidoodle.model.SlotStatus;
import com.example.minidoodle.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MeetingServiceTest {
    private static final Instant START = Instant.parse("2032-03-01T10:00:00Z");
    private static final Instant END = START.plusSeconds(3600);

    private InMemoryRepositories db;
    private UUID ada;
    private UUID grace;
    private UUID linus;
    private TimeSlot source;

    @BeforeEach
    void setUp() {
        db = new InMemoryRepositories();
        ada = db.createUser("Ada");
        grace = db.createUser("Grace");
        linus = db.createUser("Linus");
        source = db.scheduling.createSlot(ada, START, END, SlotStatus.FREE);
    }

    @Test
    void bookingMarksSlotBusyAndIncludesOrganizerAndGuest() {
        Meeting booked = db.meetingService.book(ada, source.id(), " Planning ", "Agenda", List.of(grace));
        assertEquals("Planning", booked.title());
        assertEquals("Agenda", booked.description());
        assertEquals(source.id(), booked.slotId());
        assertEquals(START, booked.startsAt());
        assertEquals(END, booked.endsAt());
        assertEquals(ada, booked.organizerId());
        assertEquals(List.of(ada, grace).stream().sorted().toList(), booked.participants());
        assertEquals(SlotStatus.BUSY, db.slotRows.get(source.id()).getStatus());
        assertEquals(1, db.meetingRows.size());
    }

    @Test
    void organizerAloneCanBookAndDuplicateParticipantsAreRemoved() {
        Meeting meeting = db.meetingService.book(ada, source.id(), "Planning", null,
                List.of(ada, ada));
        assertEquals(List.of(ada), meeting.participants());
    }

    @Test
    void bookingRejectsBusySourceSlot() {
        db.slotRows.get(source.id()).change(START, END, SlotStatus.BUSY);
        assertThrows(ConflictException.class,
                () -> db.meetingService.book(ada, source.id(), "Planning", null, List.of()));
        assertTrue(db.meetingRows.isEmpty());
    }

    @Test
    void bookingRejectsSlotThatHasAlreadyBeenBooked() {
        db.meetingService.book(ada, source.id(), "Planning", null, List.of());
        assertThrows(ConflictException.class,
                () -> db.meetingService.book(ada, source.id(), "Again", null, List.of()));
        assertEquals(1, db.meetingRows.size());
    }

    @Test
    void bookingRejectsMissingSlotOrWrongOwner() {
        assertThrows(NotFoundException.class,
                () -> db.meetingService.book(ada, UUID.randomUUID(), "Planning", null, List.of()));
        assertThrows(NotFoundException.class,
                () -> db.meetingService.book(grace, source.id(), "Planning", null, List.of()));
    }

    @Test
    void bookingRejectsGuestsWithBusySlotsWithoutBookingSource() {
        db.scheduling.createSlot(grace, START, END, SlotStatus.BUSY);
        assertThrows(ConflictException.class,
                () -> db.meetingService.book(ada, source.id(), "Planning", null, List.of(grace)));
        assertEquals(SlotStatus.FREE, db.slotRows.get(source.id()).getStatus());
        assertTrue(db.meetingRows.isEmpty());
    }

    @Test
    void bookingRejectsGuestWithOverlappingMeeting() {
        TimeSlot graceSource = db.scheduling.createSlot(grace, START, END, SlotStatus.FREE);
        db.meetingService.book(grace, graceSource.id(), "Standup", null, List.of());
        assertThrows(ConflictException.class,
                () -> db.meetingService.book(ada, source.id(), "Planning", null, List.of(grace)));
        assertEquals(SlotStatus.FREE, db.slotRows.get(source.id()).getStatus());
    }

    @Test
    void bookingRejectsNullParticipantListOrElementWithClearError() {
        assertThrows(IllegalArgumentException.class,
                () -> db.meetingService.book(ada, source.id(), "Planning", null, null));
        List<UUID> containsNull = new ArrayList<>(Arrays.asList(grace, null));
        assertThrows(IllegalArgumentException.class,
                () -> db.meetingService.book(ada, source.id(), "Planning", null, containsNull));
        assertEquals(SlotStatus.FREE, db.slotRows.get(source.id()).getStatus());
    }

    @Test
    void unknownParticipantCannotBeBooked() {
        assertThrows(NotFoundException.class,
                () -> db.meetingService.book(ada, source.id(), "Planning", null, List.of(UUID.randomUUID())));
        assertEquals(SlotStatus.FREE, db.slotRows.get(source.id()).getStatus());
    }

    @Test
    void editingMeetingChangesDetailsAndParticipantsButNotTime() {
        Meeting booked = db.meetingService.book(ada, source.id(), "Planning", "Old", List.of(grace));
        Meeting edited = db.meetingService.edit(ada, booked.id(), " Review ", "New", List.of(linus));
        assertEquals("Review", edited.title());
        assertEquals("New", edited.description());
        assertEquals(List.of(ada, linus).stream().sorted().toList(), edited.participants());
        assertEquals(booked.startsAt(), edited.startsAt());
        assertEquals(booked.endsAt(), edited.endsAt());
        assertEquals(booked.slotId(), edited.slotId());
    }

    @Test
    void editingMeetingRejectsBusyNewParticipantAndPreservesDetails() {
        Meeting booked = db.meetingService.book(ada, source.id(), "Planning", "Old", List.of(grace));
        db.scheduling.createSlot(linus, START, END, SlotStatus.BUSY);
        assertThrows(ConflictException.class, () -> db.meetingService.edit(
                ada, booked.id(), "Changed", "New", List.of(linus)));
        assertEquals("Planning", db.meetingRows.get(booked.id()).getTitle());
        assertEquals(2, db.meetingRows.get(booked.id()).getParticipants().size());
    }

    @Test
    void editOnlyAllowedForOrganizer() {
        Meeting booked = db.meetingService.book(ada, source.id(), "Planning", null, List.of(grace));
        assertThrows(NotFoundException.class, () -> db.meetingService.edit(
                grace, booked.id(), "Hacked", null, List.of()));
        assertEquals("Planning", db.meetingRows.get(booked.id()).getTitle());
    }

    @Test
    void editRejectsNullParticipantsWithoutNullPointerException() {
        Meeting booked = db.meetingService.book(ada, source.id(), "Planning", null, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> db.meetingService.edit(ada, booked.id(), "New", null, null));
    }
}
