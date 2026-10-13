CREATE TABLE users (
    id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE schedules (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_schedule_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE time_slots (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_slot_schedule FOREIGN KEY (schedule_id) REFERENCES schedules(id),
    CONSTRAINT chk_slot_window CHECK (starts_at < ends_at),
    CONSTRAINT chk_slot_status CHECK (status IN ('FREE', 'BUSY'))
);
CREATE INDEX idx_slot_schedule_window ON time_slots (schedule_id, starts_at, ends_at);

CREATE TABLE meetings (
    id UUID PRIMARY KEY,
    source_slot_id UUID NOT NULL UNIQUE,
    organizer_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_meeting_source_slot FOREIGN KEY (source_slot_id) REFERENCES time_slots(id),
    CONSTRAINT fk_meeting_organizer FOREIGN KEY (organizer_id) REFERENCES users(id),
    CONSTRAINT chk_meeting_window CHECK (starts_at < ends_at)
);

CREATE TABLE meeting_participants (
    meeting_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (meeting_id, user_id),
    CONSTRAINT fk_participant_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id),
    CONSTRAINT fk_participant_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_participant_user_meeting ON meeting_participants(user_id, meeting_id);
CREATE INDEX idx_meeting_window ON meetings(starts_at, ends_at);
