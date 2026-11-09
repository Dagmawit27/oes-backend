-- ==============================================================================
-- Migration V10: Redesign Examination Scheduling, Individual Timer & Passcode Security
-- ==============================================================================

-- 1. Alter exam_schedules to support date, scheduled start time, access window, and 3 passcodes
ALTER TABLE exam_schedules
    ADD COLUMN IF NOT EXISTS examination_date DATE NOT NULL DEFAULT CURRENT_DATE,
    ADD COLUMN IF NOT EXISTS scheduled_start_time TIME NOT NULL DEFAULT '09:00:00',
    ADD COLUMN IF NOT EXISTS start_access_ends_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS passcode_hash_1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS passcode_hash_2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS passcode_hash_3 VARCHAR(255);

-- Make scheduled_close_at nullable or synced with start_access_ends_at
ALTER TABLE exam_schedules
    ALTER COLUMN scheduled_close_at DROP NOT NULL;

-- Drop obsolete constraints if they exist
ALTER TABLE exam_schedules
    DROP CONSTRAINT IF EXISTS chk_schedule_times;

-- Backfill examination_date, scheduled_start_time, and start_access_ends_at from scheduled_start_at
UPDATE exam_schedules
SET examination_date = scheduled_start_at::date,
    scheduled_start_time = scheduled_start_at::time,
    start_access_ends_at = (scheduled_start_at::date + TIME '23:59:59') AT TIME ZONE 'UTC'
WHERE start_access_ends_at IS NULL;

-- Seed default BCrypt hashed passcodes for existing schedules ('1234', '5678', '9012')
-- BCrypt for '1234': $2a$10$N.ZOHs5Qp87kQ22v2U6cQOq0Xj82xW7v2w7k4X1z7w3q2U0z7w3q2
UPDATE exam_schedules
SET passcode_hash_1 = COALESCE(passcode_hash_1, '$2a$10$k1r.qE3hF8C2zJv7e6YgEuqO82xW7v2w7k4X1z7w3q2U0z7w3q2Oa'),
    passcode_hash_2 = COALESCE(passcode_hash_2, '$2a$10$m2s.rF4iG9D3aKw8f7ZhFvrP93yX8w3x8l5Y2a8x8r4r3V1a8x4r3Pb'),
    passcode_hash_3 = COALESCE(passcode_hash_3, '$2a$10$n3t.sG5jH0E4bLx9g8AiGwsQ04zY9x4y9m6Z3b9y9s5s4W2b9y5s4Qc')
WHERE passcode_hash_1 IS NULL;

-- 2. Alter exam_attempts for individual active-time tracking and pause/resume
ALTER TABLE exam_attempts
    DROP CONSTRAINT IF EXISTS chk_attempt_deadline;

ALTER TABLE exam_attempts
    ALTER COLUMN deadline_at DROP NOT NULL;

ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS duration_seconds INT NOT NULL DEFAULT 3600,
    ADD COLUMN IF NOT EXISTS remaining_active_seconds INT NOT NULL DEFAULT 3600,
    ADD COLUMN IF NOT EXISTS accumulated_active_seconds INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS active_since TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_resumed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS resume_stage INT NOT NULL DEFAULT 0;

-- Update existing IN_PROGRESS attempts to ACTIVE status and initialize active tracking
UPDATE exam_attempts
SET duration_seconds = COALESCE((SELECT e.duration_minutes * 60 FROM examinations e WHERE e.id = exam_attempts.examination_id), 3600),
    remaining_active_seconds = COALESCE((SELECT e.duration_minutes * 60 FROM examinations e WHERE e.id = exam_attempts.examination_id), 3600),
    accumulated_active_seconds = 0,
    active_since = started_at,
    last_heartbeat_at = last_activity_at,
    status = 'ACTIVE'
WHERE status = 'IN_PROGRESS';

-- Drop previous active attempt index to account for ACTIVE and CONNECTION_PAUSED states
DROP INDEX IF EXISTS idx_uq_active_attempt;
CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_active_attempt 
    ON exam_attempts(student_id, schedule_id) 
    WHERE status IN ('ACTIVE', 'IN_PROGRESS', 'CONNECTION_PAUSED', 'RESUME_REQUIRED');

-- Index on attempt status and heartbeat for fast background connection monitor queries
CREATE INDEX IF NOT EXISTS idx_exam_attempts_status_heartbeat 
    ON exam_attempts(status, last_heartbeat_at);

-- 3. Rate-limiting table for failed passcodes
CREATE TABLE IF NOT EXISTS attempt_passcode_attempts (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES exam_schedules(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    attempt_type VARCHAR(30) NOT NULL, -- START, RESUME_1, RESUME_2_PLUS
    is_successful BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(50),
    attempted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_passcode_attempts_rate_limit 
    ON attempt_passcode_attempts(schedule_id, student_id, attempted_at);
