# doodle-test — Mini Doodle scheduling API

A focused Spring Boot 3.5 / Java 21 take-home implementation using **Spring Data JPA, H2, Flyway, Docker Compose, JUnit 5, and Actuator**. The design is a single deployable application, appropriate for hundreds of users and thousands of slots. No direct `EntityManager` calls or catch-all persistence facade.

## Quick start

Requires Docker with Compose:

```bash
docker compose up --build
```

Or Java 21 and Maven 3.9+:

```bash
mvn clean verify
mvn spring-boot:run
```

- REST API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`

**Persistence:** H2 is **file-backed**, not in-memory. Docker Compose mounts the named volume `h2-data` at `/data` so data survives container restarts. Local development writes `./data/minidoodle.mv.db`. `docker compose down -v` deletes the volume and its data.

## API examples

Replace `{userId}`, `{slotId}`, and `{meetingId}` with IDs returned from earlier requests.

**Create user**

```bash
curl -X POST 'http://localhost:8080/api/v1/users' \
  -H 'Content-Type: application/json' \
  -d '{"displayName":"Ada"}'
```

**Create free time slot**

```bash
curl -X POST 'http://localhost:8080/api/v1/users/{userId}/slots' \
  -H 'Content-Type: application/json' \
  -d '{"startsAt":"2032-03-01T10:00:00Z","endsAt":"2032-03-01T11:00:00Z","status":"FREE"}'
```

**Update time slot** (include the current optimistic-lock `version`, initially `0`)

```bash
curl -X PUT 'http://localhost:8080/api/v1/users/{userId}/slots/{slotId}' \
  -H 'Content-Type: application/json' \
  -d '{"startsAt":"2032-03-01T11:00:00Z","endsAt":"2032-03-01T12:00:00Z","status":"BUSY","version":0}'
```

**Delete time slot**

```bash
curl -X DELETE 'http://localhost:8080/api/v1/users/{userId}/slots/{slotId}'
```

**Query declared availability** (`status` is optional)

```bash
curl -G 'http://localhost:8080/api/v1/users/{userId}/availability' \
  --data-urlencode 'from=2032-03-01T00:00:00Z' \
  --data-urlencode 'to=2032-03-02T00:00:00Z' \
  --data-urlencode 'status=FREE'
```

**Book a free slot**

```bash
curl -X POST 'http://localhost:8080/api/v1/users/{userId}/slots/{slotId}/meeting' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Planning","description":"Review","participants":[]}'
```

**Edit a scheduled meeting** (optional extension: details and attendees only, **not** its time)

```bash
curl -X PUT 'http://localhost:8080/api/v1/users/{userId}/meetings/{meetingId}' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Revised planning","description":"New agenda","participants":[]}'
```

`participants` is a list of **existing user UUIDs**. The organizer is included automatically; duplicates are ignored.

### HTTP responses

- `201 Created`: user, slot, or meeting created.
- `200 OK`: listing or update.
- `204 No Content`: slot deleted.
- `400 Bad Request`: invalid payload, dates, or missing fields.
- `404 Not Found`: unknown user, slot, or meeting (including wrong organizer).
- `409 Conflict`: slot overlap, busy participant, booked slot mutation, or stale version.

Errors use Spring `ProblemDetail` (`application/problem+json`).

## Architecture

```text
src/main/java/com/example/minidoodle/
├── controller/    HTTP API, request DTOs, and exception mapping
│   └── dto/
├── service/       Transactional use cases, lock ordering, scheduling rules
├── model/         Immutable Calendar, TimeSlot, Meeting, SlotStatus
├── exception/         Immutable Calendar, TimeSlot, Meeting, SlotStatus
├── entity/        Encapsulated JPA entities and state changes
└── repository/    Focused Spring Data JpaRepository interfaces
```

`Calendar` exists **only in the domain (`model`)**, as requested. Persistence uses `ScheduleEntity` and the `schedules` table. Domain objects are immutable; JPA entities are private-field persistence models. Controllers do not return JPA entities. `Flyway` owns the schema, and Hibernate runs with `ddl-auto: validate`.

### Scheduling and concurrency decisions

- All intervals use **half-open** `[startsAt, endsAt)` semantics; adjacent slots do not overlap.
- Users have one schedule; a user's own declared slots cannot overlap, whether `FREE` or `BUSY`.
- Every write that affects a user first acquires **pessimistic user-row locks**; multi-user operations acquire them in **ascending UUID order**. Slot mutations then lock the schedule and relevant slot; meeting updates lock the meeting after user locks. This prevents races between marking a participant BUSY and booking a meeting for them.
- Slot edits additionally require `@Version` to match the last response; clients receive HTTP 409 for stale versions.
- A meeting can only be created from a `FREE` organizer slot. Booking marks that slot `BUSY`, persists the meeting, and writes its participants **in one transaction**. The source slot cannot subsequently be edited or deleted.
- Booking checks both **participants' own manually BUSY slots** and **other meetings they attend**. The system does not require participants to have created a matching `FREE` slot.
- A participant's pre-existing `FREE` declaration is not automatically split or rewritten when they attend another user's meeting. Thus the availability endpoint exposes **declared slots**, not a computed free-time subtraction of all invitations. A consumer must consider meeting attendance to determine effective availability; booking itself validates it transactionally.
- Meeting editing, provided as an optional extension, changes only title/description/participants, preserving the reserved time. Rescheduling and cancellation were not requested in the challenge and are deliberately omitted.
- Availability queries have a **366-day window** limit and return at most **1,000 declared slots**. There is no continuation token in this challenge version; for larger workloads add cursor pagination.
- Indices cover schedule-window searches and the attendee-to-meeting relationship.

### Trade-offs and limits

H2 keeps the take-home runnable without a separate database container, but lacks a portable database constraint forbidding overlapping time slots. **Every application write must follow the lock protocol**; manual SQL writes can violate the invariant. I'll migrate to PostgreSQL and add an exclusion constraint on per-user intervals. H2 file storage is durable across normal Docker restarts but is **not** a substitute for a replicated production database.

This sample has **no authentication or authorization**: a path `userId` is trusted for demo purposes. Real clients require authenticated user principals, invitation policy, rate limits, observability, backups, migrations against production databases, and load tests. It is not a production-ready scheduling platform.

## Tests — fast service unit tests

Run all **37 JUnit 5 service unit tests**:

```bash
mvn clean test
```

Tests live in `src/test/java/com/example/minidoodle/service/`:

- `SchedulingServiceTest` — user creation, slot creation/update/deletion, overlap rejection, stale versions, authorization by owner ID, and availability.
- `MeetingServiceTest` — booking, organizer inclusion, participant validation, busy-slot conflicts, and meeting editing.
- `UserLockServiceTest` — the immutable-list null-check regression, null IDs, user existence, and deterministic UUID lock ordering.
- `SchedulingRulesTest` — half-open time intervals and query window validation.

These tests instantiate the real services directly. `InMemoryRepositories` implements
the repository operations with **handwritten Java dynamic-proxy fakes**, not Mockito.
They do not launch Spring or use H2. The test dependency is **JUnit Jupiter** only.

## Source/requirements traceability

| Challenge requirement | Implementation |
| --- | --- |
| Personal calendars, editable slots | `SchedulingService` / `ScheduleEntity` / slot endpoints |
| Convert available slots to meeting | `MeetingService.book()` and booking endpoint |
| Title, description, participants | `MeetingEntity` / `MeetingRequest` |
| Free/busy queries by timeframe | `GET /availability` with `status` filter |
| Hundreds of users / thousands of slots | Indexed H2 schema, bounded queries, application-level transaction discipline |
| Persist all data | Flyway schema and file-backed H2 Compose volume |
| Local `docker-compose` | `compose.yml` + multi-stage `Dockerfile` |
| Tests, documentation, metrics | JUnit 5 unit tests, Swagger, README, Actuator |

