-- ==============================================================================
-- Migration V7: Student Examination Execution, Answer Autosave, and Audit Events
-- ==============================================================================

-- 1. Examination Attempts
CREATE TABLE IF NOT EXISTS exam_attempts (
    id BIGSERIAL PRIMARY KEY,
    examination_id BIGINT NOT NULL REFERENCES examinations(id) ON DELETE RESTRICT,
    schedule_id BIGINT NOT NULL REFERENCES exam_schedules(id) ON DELETE RESTRICT,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    attempt_number INT NOT NULL DEFAULT 1,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, SUBMITTED, EXPIRED, ABANDONED
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_score NUMERIC(7, 2),
    is_graded BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_attempt_deadline CHECK (deadline_at > started_at)
);

CREATE INDEX IF NOT EXISTS idx_exam_attempts_student ON exam_attempts(student_id);
CREATE INDEX IF NOT EXISTS idx_exam_attempts_schedule ON exam_attempts(schedule_id);
CREATE INDEX IF NOT EXISTS idx_exam_attempts_status ON exam_attempts(status);
CREATE INDEX IF NOT EXISTS idx_exam_attempts_deadline ON exam_attempts(deadline_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_student_schedule_attempt ON exam_attempts(student_id, schedule_id, attempt_number);
-- Unique index guaranteeing only ONE active IN_PROGRESS attempt per student and schedule
CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_active_attempt ON exam_attempts(student_id, schedule_id) WHERE status = 'IN_PROGRESS';

-- 2. Student Answers (Per Question Autosave)
CREATE TABLE IF NOT EXISTS student_answers (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES exam_attempts(id) ON DELETE CASCADE,
    exam_question_id BIGINT NOT NULL REFERENCES exam_questions(id) ON DELETE RESTRICT,
    answer_text TEXT,
    is_flagged BOOLEAN NOT NULL DEFAULT FALSE,
    saved_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attempt_question UNIQUE(attempt_id, exam_question_id)
);

CREATE INDEX IF NOT EXISTS idx_student_answers_attempt ON student_answers(attempt_id);
CREATE INDEX IF NOT EXISTS idx_student_answers_question ON student_answers(exam_question_id);

-- 3. Student Answer Options (Selected Choices for Single/Multiple Choice & True/False)
CREATE TABLE IF NOT EXISTS student_answer_options (
    id BIGSERIAL PRIMARY KEY,
    student_answer_id BIGINT NOT NULL REFERENCES student_answers(id) ON DELETE CASCADE,
    question_option_id BIGINT NOT NULL REFERENCES question_options(id) ON DELETE RESTRICT,
    selected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_answer_option UNIQUE(student_answer_id, question_option_id)
);

CREATE INDEX IF NOT EXISTS idx_answer_options_answer ON student_answer_options(student_answer_id);
CREATE INDEX IF NOT EXISTS idx_answer_options_option ON student_answer_options(question_option_id);

-- 4. Examination Audit Events (Integrity & Proactive Monitoring)
CREATE TABLE IF NOT EXISTS exam_events (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES exam_attempts(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- STARTED, ANSWER_SAVED, QUESTION_FLAGGED, TAB_BLUR, WINDOW_RESIZED, DISCONNECTED, RECONNECTED, SUBMITTED, AUTO_SUBMITTED_EXPIRED
    event_payload TEXT,
    ip_address VARCHAR(50),
    user_agent TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exam_events_attempt ON exam_events(attempt_id);
CREATE INDEX IF NOT EXISTS idx_exam_events_type ON exam_events(event_type);
CREATE INDEX IF NOT EXISTS idx_exam_events_time ON exam_events(occurred_at);
