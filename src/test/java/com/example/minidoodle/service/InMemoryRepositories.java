package com.example.minidoodle.service;

import com.example.minidoodle.entity.MeetingEntity;
import com.example.minidoodle.entity.ScheduleEntity;
import com.example.minidoodle.entity.TimeSlotEntity;
import com.example.minidoodle.entity.UserEntity;
import com.example.minidoodle.model.SlotStatus;
import com.example.minidoodle.repository.MeetingJpaRepository;
import com.example.minidoodle.repository.ScheduleJpaRepository;
import com.example.minidoodle.repository.TimeSlotJpaRepository;
import com.example.minidoodle.repository.UserJpaRepository;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Small, deliberately strict in-memory fakes for service UNIT tests.
 * Unsupported repository calls fail immediately to prevent accidental silent stubbing.
 * These fakes do not emulate JPA locking, transactions, or flush/version behavior.
 */
final class InMemoryRepositories {
    final Map<UUID, UserEntity> userRows = new LinkedHashMap<>();
    final Map<UUID, ScheduleEntity> scheduleRows = new LinkedHashMap<>();
    final Map<UUID, TimeSlotEntity> slotRows = new LinkedHashMap<>();
    final Map<UUID, MeetingEntity> meetingRows = new LinkedHashMap<>();
    final List<UUID> lockOrder = new ArrayList<>();

    final UserJpaRepository users = repository(UserJpaRepository.class, (method, args) -> switch (method) {
        case "save" -> {
            UserEntity user = (UserEntity) args[0];
            userRows.put(user.getId(), user);
            yield user;
        }
        case "lockById" -> {
            UUID id = (UUID) args[0];
            lockOrder.add(id);
            yield Optional.ofNullable(userRows.get(id));
        }
        case "existsById" -> userRows.containsKey(args[0]);
        default -> unsupported("UserJpaRepository", method);
    });

    final ScheduleJpaRepository schedules = repository(ScheduleJpaRepository.class, (method, args) -> switch (method) {
        case "save" -> {
            ScheduleEntity schedule = (ScheduleEntity) args[0];
            scheduleRows.put(schedule.getId(), schedule);
            yield schedule;
        }
        case "lockByUserId", "findByUser_Id" -> scheduleRows.values().stream()
                .filter(s -> s.getUser().getId().equals(args[0])).findFirst();
        default -> unsupported("ScheduleJpaRepository", method);
    });

    final TimeSlotJpaRepository slots = repository(TimeSlotJpaRepository.class, (method, args) -> switch (method) {
        case "saveAndFlush" -> {
            TimeSlotEntity slot = (TimeSlotEntity) args[0];
            slotRows.put(slot.getId(), slot);
            yield slot;
        }
        case "lockOwned" -> Optional.ofNullable(slotRows.get(args[0]))
                .filter(slot -> slot.getSchedule().getUser().getId().equals(args[1]));
        case "countOverlapping" -> countSlots(s -> s.getSchedule().getId().equals(args[0])
                && overlaps(s, (Instant) args[1], (Instant) args[2]));
        case "countOverlappingExceptSlot" -> countSlots(s -> s.getSchedule().getId().equals(args[0])
                && !s.getId().equals(args[1]) && overlaps(s, (Instant) args[2], (Instant) args[3]));
        case "countBusy" -> countSlots(s -> s.getSchedule().getUser().getId().equals(args[0])
                && s.getStatus() == args[3] && overlaps(s, (Instant) args[1], (Instant) args[2]));
        case "countBusyExceptSlot" -> countSlots(s -> s.getSchedule().getUser().getId().equals(args[0])
                && !s.getId().equals(args[1]) && s.getStatus() == args[4]
                && overlaps(s, (Instant) args[2], (Instant) args[3]));
        case "findAvailability" -> availability((UUID) args[0], (Instant) args[1],
                (Instant) args[2], null, (Pageable) args[3]);
        case "findAvailabilityByStatus" -> availability((UUID) args[0], (Instant) args[1],
                (Instant) args[2], (SlotStatus) args[3], (Pageable) args[4]);
        case "delete" -> { slotRows.remove(((TimeSlotEntity) args[0]).getId()); yield null; }
        case "flush" -> null;
        default -> unsupported("TimeSlotJpaRepository", method);
    });

    final MeetingJpaRepository meetings = repository(MeetingJpaRepository.class, (method, args) -> switch (method) {
        case "saveAndFlush" -> {
            MeetingEntity meeting = (MeetingEntity) args[0];
            meetingRows.put(meeting.getId(), meeting);
            yield meeting;
        }
        case "existsBySourceSlot_Id" -> meetingRows.values().stream()
                .anyMatch(m -> m.getSourceSlot().getId().equals(args[0]));
        case "findByIdAndOrganizer_Id", "lockOwned" -> Optional.ofNullable(meetingRows.get(args[0]))
                .filter(m -> m.getOrganizer().getId().equals(args[1]));
        case "participantIds" -> meetingRows.get(args[0]).getParticipants().stream()
                .map(UserEntity::getId).toList();
        case "countConflictingMeetings" -> countMeetings((UUID) args[0], null,
                (Instant) args[1], (Instant) args[2]);
        case "countConflictingMeetingsExcept" -> countMeetings((UUID) args[0],
                (UUID) args[1], (Instant) args[2], (Instant) args[3]);
        case "flush" -> null;
        default -> unsupported("MeetingJpaRepository", method);
    });

    final UserLockService userLocks = new UserLockService(users);
    final SchedulingService scheduling = new SchedulingService(users, schedules, slots, meetings, userLocks);
    final MeetingService meetingService = new MeetingService(userLocks, scheduling, meetings, slots);

    UUID createUser(String name) { return scheduling.createUser(name); }

    TimeSlotEntity createSlotEntity(UUID ownerId, Instant start, Instant end, SlotStatus status) {
        UUID slotId = scheduling.createSlot(ownerId, start, end, status).id();
        return slotRows.get(slotId);
    }

    private long countSlots(Predicate<TimeSlotEntity> predicate) {
        return slotRows.values().stream().filter(predicate).count();
    }

    private long countMeetings(UUID userId, UUID excludeId, Instant start, Instant end) {
        return meetingRows.values().stream()
                .filter(m -> excludeId == null || !m.getId().equals(excludeId))
                .filter(m -> m.getParticipants().stream().anyMatch(p -> p.getId().equals(userId)))
                .filter(m -> m.getStartsAt().isBefore(end) && m.getEndsAt().isAfter(start))
                .count();
    }

    private List<TimeSlotEntity> availability(UUID userId, Instant from, Instant to,
                                               SlotStatus status, Pageable limit) {
        return slotRows.values().stream()
                .filter(s -> s.getSchedule().getUser().getId().equals(userId))
                .filter(s -> overlaps(s, from, to))
                .filter(s -> status == null || s.getStatus() == status)
                .sorted(Comparator.comparing(TimeSlotEntity::getStartsAt).thenComparing(TimeSlotEntity::getId))
                .limit(limit.getPageSize())
                .toList();
    }

    private static boolean overlaps(TimeSlotEntity slot, Instant start, Instant end) {
        return slot.getStartsAt().isBefore(end) && slot.getEndsAt().isAfter(start);
    }

    private static Object unsupported(String repository, String method) {
        throw new UnsupportedOperationException("No fake implementation for " + repository + "." + method);
    }

    private interface Call { Object invoke(String method, Object[] args); }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, Call call) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "Fake" + type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            return call.invoke(method.getName(), arguments == null ? new Object[0] : arguments);
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
