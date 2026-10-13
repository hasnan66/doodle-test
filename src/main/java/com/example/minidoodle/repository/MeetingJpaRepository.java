package com.example.minidoodle.repository;

import com.example.minidoodle.entity.MeetingEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingJpaRepository extends JpaRepository<MeetingEntity, UUID> {

    boolean existsBySourceSlot_Id(UUID slotId);

    Optional<MeetingEntity> findByIdAndOrganizer_Id(UUID id, UUID organizerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MeetingEntity m where m.id = :id and m.organizer.id = :organizerId")
    Optional<MeetingEntity> lockOwned(@Param("id") UUID id,
                                      @Param("organizerId") UUID organizerId);

    @Query("select u.id from MeetingEntity m join m.participants u where m.id = :meetingId")
    List<UUID> participantIds(@Param("meetingId") UUID meetingId);

    @Query("""
            select count(m) from MeetingEntity m join m.participants p
            where p.id = :userId
              and m.startsAt < :end and m.endsAt > :start
            """)
    long countConflictingMeetings(@Param("userId") UUID userId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);

    @Query("""
            select count(m) from MeetingEntity m join m.participants p
            where p.id = :userId and m.id <> :excludedMeetingId
              and m.startsAt < :end and m.endsAt > :start
            """)
    long countConflictingMeetingsExcept(@Param("userId") UUID userId,
                                        @Param("excludedMeetingId") UUID excludedMeetingId,
                                        @Param("start") Instant start,
                                        @Param("end") Instant end);
}
