package com.example.minidoodle.repository;

import com.example.minidoodle.entity.ScheduleEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleJpaRepository extends JpaRepository<ScheduleEntity, UUID> {
    Optional<ScheduleEntity> findByUser_Id(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduleEntity s where s.user.id = :userId")
    Optional<ScheduleEntity> lockByUserId(@Param("userId") UUID userId);
}
