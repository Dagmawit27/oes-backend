-- Add optional Academic Program and Student Year Level metadata to Course Assignments (course_offerings)
ALTER TABLE course_offerings
    ADD COLUMN IF NOT EXISTS program_id BIGINT REFERENCES programs(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS student_year_level INT;

CREATE INDEX IF NOT EXISTS idx_course_offerings_program ON course_offerings(program_id);
