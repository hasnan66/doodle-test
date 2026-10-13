package com.example.minidoodle.entity;

import com.example.minidoodle.model.SlotStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "time_slots")
public class TimeSlotEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private ScheduleEntity schedule;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SlotStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimeSlotEntity() { }

    public TimeSlotEntity(UUID id, ScheduleEntity schedule, Instant startsAt,
                          Instant endsAt, SlotStatus status) {
        this.id = id;
        this.schedule = schedule;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void change(Instant startsAt, Instant endsAt, SlotStatus status) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
    }

    public void book() {
        if (status != SlotStatus.FREE) {
            throw new IllegalStateException("Only a FREE slot can be booked");
        }
        status = SlotStatus.BUSY;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public UUID getId() {
        return id;
    }
    public ScheduleEntity getSchedule() {
        return schedule;
    }
    public Instant getStartsAt() {
        return startsAt;
    }
    public Instant getEndsAt() {
        return endsAt;
    }
    public SlotStatus getStatus() {
        return status;
    }
    public long getVersion() {
        return version;
    }
}
