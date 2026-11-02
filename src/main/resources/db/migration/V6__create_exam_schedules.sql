-- ==============================================================================
-- Migration V6: Examination Scheduling, Student Eligibility, and Rooms
-- ==============================================================================

-- 1. Physical Examination Rooms
CREATE TABLE IF NOT EXISTS exam_rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    building VARCHAR(100),
    capacity INT NOT NULL DEFAULT 50,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exam_rooms_active ON exam_rooms(is_active);

-- 2. Examination Schedules
CREATE TABLE IF NOT EXISTS exam_schedules (
    id BIGSERIAL PRIMARY KEY,
    examination_id BIGINT NOT NULL REFERENCES examinations(id) ON DELETE RESTRICT,
    scheduled_start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_close_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INT NOT NULL,
    room_id BIGINT REFERENCES exam_rooms(id) ON DELETE SET NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, ONGOING, COMPLETED, CANCELLED, RESCHEDULED
    instructions TEXT,
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_schedule_times CHECK (scheduled_close_at > scheduled_start_at),
    CONSTRAINT chk_duration_positive CHECK (duration_minutes > 0)
);

CREATE INDEX IF NOT EXISTS idx_exam_schedules_exam ON exam_schedules(examination_id);
CREATE INDEX IF NOT EXISTS idx_exam_schedules_start ON exam_schedules(scheduled_start_at);
CREATE INDEX IF NOT EXISTS idx_exam_schedules_close ON exam_schedules(scheduled_close_at);
CREATE INDEX IF NOT EXISTS idx_exam_schedules_status ON exam_schedules(status);
CREATE INDEX IF NOT EXISTS idx_exam_schedules_room ON exam_schedules(room_id);

-- 3. Student Eligibility Table
CREATE TABLE IF NOT EXISTS exam_eligibilities (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES exam_schedules(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    eligibility_status VARCHAR(30) NOT NULL DEFAULT 'ELIGIBLE', -- ELIGIBLE, INELIGIBLE, EXEMPTED, BLOCKED
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(schedule_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_exam_eligibility_schedule ON exam_eligibilities(schedule_id);
CREATE INDEX IF NOT EXISTS idx_exam_eligibility_student ON exam_eligibilities(student_id);
CREATE INDEX IF NOT EXISTS idx_exam_eligibility_status ON exam_eligibilities(eligibility_status);

-- 4. Examination Notification Audit Table
CREATE TABLE IF NOT EXISTS exam_notifications (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT REFERENCES exam_schedules(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- EXAM_SCHEDULED, EXAM_RESCHEDULED, EXAM_CANCELLED, EXAM_INSTRUCTIONS_UPDATED
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exam_notifications_user ON exam_notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_exam_notifications_schedule ON exam_notifications(schedule_id);

-- ==============================================================================
-- Seed Sample Rooms, Schedule, and Student Eligibility
-- ==============================================================================

-- Seed Physical Examination Rooms
INSERT INTO exam_rooms (name, building, capacity, is_active)
VALUES
    ('Lab 201 - Computer Center', 'Tech Block A', 45, true),
    ('Lecture Hall 101', 'Science Complex', 120, true),
    ('Auditorium Main', 'Administration Building', 250, true)
ON CONFLICT (name) DO NOTHING;

-- Seed Examination Schedule for Approved CS101 Midterm
INSERT INTO exam_schedules (
    examination_id, scheduled_start_at, scheduled_close_at, duration_minutes,
    room_id, timezone, status, instructions, created_by
)
SELECT
    e.id,
    CURRENT_TIMESTAMP + INTERVAL '3 days',
    CURRENT_TIMESTAMP + INTERVAL '3 days' + INTERVAL '4 hours',
    e.duration_minutes,
    r.id,
    'UTC',
    'SCHEDULED',
    'Please arrive 15 minutes before exam start. Bring college student ID.',
    u.id
FROM examinations e
CROSS JOIN users u
LEFT JOIN exam_rooms r ON r.name = 'Lab 201 - Computer Center'
WHERE e.title = 'CS101 Midterm Examination - Fall 2025'
  AND u.email = 'officer@college.edu'
LIMIT 1;

-- Update Examination status to SCHEDULED
UPDATE examinations
SET status = 'SCHEDULED'
WHERE title = 'CS101 Midterm Examination - Fall 2025';

-- Generate Student Eligibility for Alex Student (enrolled in CS101 Section A)
INSERT INTO exam_eligibilities (schedule_id, student_id, eligibility_status, reason)
SELECT
    s.id,
    stu.id,
    'ELIGIBLE',
    'Enrolled in CS101 Section A'
FROM exam_schedules s
JOIN examinations e ON s.examination_id = e.id
JOIN course_offerings co ON e.course_offering_id = co.id
JOIN enrollments en ON en.course_offering_id = co.id
JOIN students stu ON en.student_id = stu.id
WHERE e.title = 'CS101 Midterm Examination - Fall 2025'
  AND stu.registration_number = 'STU-2025-001'
ON CONFLICT (schedule_id, student_id) DO NOTHING;

-- Seed Notification for Student
INSERT INTO exam_notifications (schedule_id, user_id, event_type, title, message)
SELECT
    s.id,
    stu.user_id,
    'EXAM_SCHEDULED',
    'Examination Scheduled: CS101 Midterm',
    'Your examination for CS101 has been scheduled. Review your schedule for start times and room allocation.'
FROM exam_schedules s
JOIN examinations e ON s.examination_id = e.id
JOIN students stu ON stu.registration_number = 'STU-2025-001'
WHERE e.title = 'CS101 Midterm Examination - Fall 2025'
LIMIT 1;
