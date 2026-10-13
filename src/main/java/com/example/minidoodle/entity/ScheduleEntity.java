package com.example.minidoodle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedules")
public class ScheduleEntity {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ScheduleEntity() { }

    public ScheduleEntity(UUID id, UserEntity user) {
        this.id = id;
        this.user = user;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }
    public UserEntity getUser() {
        return user;
    }
}
