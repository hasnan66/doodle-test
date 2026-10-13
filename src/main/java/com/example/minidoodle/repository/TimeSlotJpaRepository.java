package com.example.minidoodle.repository;

import com.example.minidoodle.entity.TimeSlotEntity;
import com.example.minidoodle.model.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSlotJpaRepository extends JpaRepository<TimeSlotEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from TimeSlotEntity s
            where s.id = :id and s.schedule.user.id = :userId
            """)
    Optional<TimeSlotEntity> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    // Separate queries avoid 'IS NULL' on untyped bind parameters in H2 / Hibernate.
    @Query("""
            select count(s) from TimeSlotEntity s
            where s.schedule.id = :scheduleId
              and s.startsAt < :end and s.endsAt > :start
            """)
    long countOverlapping(@Param("scheduleId") UUID scheduleId,
                          @Param("start") Instant start,
                          @Param("end") Instant end);

    @Query("""
            select count(s) from TimeSlotEntity s
            where s.schedule.id = :scheduleId and s.id <> :excludedId
              and s.startsAt < :end and s.endsAt > :start
            """)
    long countOverlappingExceptSlot(@Param("scheduleId") UUID scheduleId,
                                    @Param("excludedId") UUID excludedId,
                                    @Param("start") Instant start,
                                    @Param("end") Instant end);

    @Query("""
            select count(s) from TimeSlotEntity s
            where s.schedule.user.id = :userId and s.status = :busy
              and s.startsAt < :end and s.endsAt > :start
            """)
    long countBusy(@Param("userId") UUID userId,
                   @Param("start") Instant start,
                   @Param("end") Instant end,
                   @Param("busy") SlotStatus busy);

    @Query("""
            select count(s) from TimeSlotEntity s
            where s.schedule.user.id = :userId and s.status = :busy
              and s.id <> :excludedId
              and s.startsAt < :end and s.endsAt > :start
            """)
    long countBusyExceptSlot(@Param("userId") UUID userId,
                             @Param("excludedId") UUID excludedId,
                             @Param("start") Instant start,
                             @Param("end") Instant end,
                             @Param("busy") SlotStatus busy);

    @Query("""
            select s from TimeSlotEntity s
            where s.schedule.user.id = :userId
              and s.startsAt < :to and s.endsAt > :from
            order by s.startsAt asc, s.id asc
            """)
    List<TimeSlotEntity> findAvailability(@Param("userId") UUID userId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to,
                                          Pageable limit);

    @Query("""
            select s from TimeSlotEntity s
            where s.schedule.user.id = :userId
              and s.startsAt < :to and s.endsAt > :from
              and s.status = :status
            order by s.startsAt asc, s.id asc
            """)
    List<TimeSlotEntity> findAvailabilityByStatus(@Param("userId") UUID userId,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to,
                                                  @Param("status") SlotStatus status,
                                                  Pageable limit);
}
