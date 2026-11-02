-- ==============================================================================
-- Migration V5: Examination Creation and Approval Module Schema & Seed Data
-- ==============================================================================

-- 1. Examinations Table
CREATE TABLE IF NOT EXISTS examinations (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    instructions TEXT,
    course_offering_id BIGINT NOT NULL REFERENCES course_offerings(id) ON DELETE RESTRICT,
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    duration_minutes INT NOT NULL,
    total_marks NUMERIC(6, 2) NOT NULL,
    passing_marks NUMERIC(6, 2) NOT NULL,
    randomize_questions BOOLEAN NOT NULL DEFAULT FALSE,
    randomize_options BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_limit INT NOT NULL DEFAULT 1,
    negative_marking_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    negative_mark_value NUMERIC(4, 2) DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_examinations_offering ON examinations(course_offering_id);
CREATE INDEX IF NOT EXISTS idx_examinations_created_by ON examinations(created_by);
CREATE INDEX IF NOT EXISTS idx_examinations_status ON examinations(status);

-- 2. Exam Questions (Relationship & Snapshot)
CREATE TABLE IF NOT EXISTS exam_questions (
    id BIGSERIAL PRIMARY KEY,
    examination_id BIGINT NOT NULL REFERENCES examinations(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    question_order INT NOT NULL DEFAULT 1,
    marks NUMERIC(5, 2) NOT NULL,
    question_version INT NOT NULL DEFAULT 1,
    question_snapshot TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(examination_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_exam_questions_exam ON exam_questions(examination_id);
CREATE INDEX IF NOT EXISTS idx_exam_questions_question ON exam_questions(question_id);

-- 3. Exam Approval & Review Audits
CREATE TABLE IF NOT EXISTS exam_approvals (
    id BIGSERIAL PRIMARY KEY,
    examination_id BIGINT NOT NULL REFERENCES examinations(id) ON DELETE CASCADE,
    reviewer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decision VARCHAR(30) NOT NULL, -- APPROVED, REJECTED
    comments TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exam_approvals_exam ON exam_approvals(examination_id);
CREATE INDEX IF NOT EXISTS idx_exam_approvals_reviewer ON exam_approvals(reviewer_id);

-- ==============================================================================
-- Seed Sample Examination Data
-- ==============================================================================

-- Seed Examination 1: Approved Midterm
INSERT INTO examinations (
    title, description, instructions, course_offering_id, created_by,
    duration_minutes, total_marks, passing_marks, randomize_questions, randomize_options,
    attempt_limit, negative_marking_enabled, negative_mark_value, status, version
)
SELECT
    'CS101 Midterm Examination - Fall 2025',
    'Comprehensive midterm examination covering fundamental programming and data structures',
    'Instructions: 1. You have 60 minutes to complete all questions. 2. Autosave is active. 3. Do not switch tabs.',
    co.id,
    u.id,
    60,
    13.00,
    6.50,
    false,
    false,
    1,
    false,
    0.00,
    'APPROVED',
    1
FROM course_offerings co, users u
WHERE co.section = 'A' AND u.email = 'instructor@college.edu'
LIMIT 1;

-- Seed Exam Questions for Examination 1 (Link the 5 seeded questions)
INSERT INTO exam_questions (examination_id, question_id, question_order, marks, question_version, question_snapshot)
SELECT
    e.id,
    q.id,
    ROW_NUMBER() OVER (ORDER BY q.id),
    q.default_marks,
    q.version,
    json_build_object(
        'id', q.id,
        'questionText', q.question_text,
        'questionType', q.question_type,
        'difficultyLevel', q.difficulty_level,
        'defaultMarks', q.default_marks,
        'explanation', q.explanation
    )::text
FROM examinations e, questions q
WHERE e.title = 'CS101 Midterm Examination - Fall 2025'
ON CONFLICT (examination_id, question_id) DO NOTHING;

-- Seed Approval Audit for Examination 1
INSERT INTO exam_approvals (examination_id, reviewer_id, decision, comments)
SELECT
    e.id,
    u.id,
    'APPROVED',
    'Comprehensive question distribution and balanced marks. Approved for scheduling.'
FROM examinations e, users u
WHERE e.title = 'CS101 Midterm Examination - Fall 2025'
  AND u.email = 'officer@college.edu'
ON CONFLICT DO NOTHING;

-- Seed Examination 2: Draft Quiz
INSERT INTO examinations (
    title, description, instructions, course_offering_id, created_by,
    duration_minutes, total_marks, passing_marks, randomize_questions, randomize_options,
    attempt_limit, negative_marking_enabled, negative_mark_value, status, version
)
SELECT
    'CS101 Quiz 1 - Algorithms & Complexity',
    'Short pop quiz on basic complexity and stack structures',
    'Read each question carefully. Select the best answer.',
    co.id,
    u.id,
    20,
    4.00,
    2.00,
    false,
    false,
    1,
    false,
    0.00,
    'DRAFT',
    1
FROM course_offerings co, users u
WHERE co.section = 'A' AND u.email = 'instructor@college.edu'
LIMIT 1;
