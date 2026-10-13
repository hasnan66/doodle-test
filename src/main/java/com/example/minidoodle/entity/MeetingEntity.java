package com.example.minidoodle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class MeetingEntity {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_slot_id", nullable = false, unique = true)
    private TimeSlotEntity sourceSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private UserEntity organizer;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(name = "meeting_participants",
            joinColumns = @JoinColumn(name = "meeting_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<UserEntity> participants = new LinkedHashSet<>();

    protected MeetingEntity() { }

    public MeetingEntity(UUID id, TimeSlotEntity sourceSlot, UserEntity organizer,
                         String title, String description) {
        this.id = id;
        this.sourceSlot = sourceSlot;
        this.organizer = organizer;
        this.title = title;
        this.description = description;
        this.startsAt = sourceSlot.getStartsAt();
        this.endsAt = sourceSlot.getEndsAt();
        this.createdAt = Instant.now();
    }

    public void edit(String title, String description, Set<UserEntity> attendees) {
        this.title = title;
        this.description = description;
        this.participants.clear();
        this.participants.addAll(attendees);
    }

    public UUID getId() {
        return id;
    }
    public TimeSlotEntity getSourceSlot() {
        return sourceSlot;
    }
    public UserEntity getOrganizer() {
        return organizer;
    }
    public String getTitle() {
        return title;
    }
    public String getDescription() {
        return description;
    }
    public Instant getStartsAt() {
        return startsAt;
    }
    public Instant getEndsAt() {
        return endsAt;
    }
    public long getVersion() {
        return version;
    }
    public Set<UserEntity> getParticipants() {
        return Set.copyOf(participants);
    }
    public void setParticipants(Set<UserEntity> attendees) {
        participants.clear();
        participants.addAll(attendees);
    }
}
