-- ==============================================================================
-- Migration V8: Simple Marks-Based Grading and Course Results Management
-- ==============================================================================

-- 1. Examination Results Table
CREATE TABLE IF NOT EXISTS exam_results (
    id BIGSERIAL PRIMARY KEY,
    examination_id BIGINT NOT NULL REFERENCES examinations(id) ON DELETE RESTRICT,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    attempt_id BIGINT REFERENCES exam_attempts(id) ON DELETE SET NULL,
    earned_marks NUMERIC(7, 2), -- Nullable when grading is incomplete/unavailable
    total_marks NUMERIC(7, 2) NOT NULL,
    grading_status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, IN_PROGRESS, COMPLETED
    publication_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT, PUBLISHED
    graded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    graded_at TIMESTAMP WITH TIME ZONE,
    published_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exam_student_result UNIQUE(examination_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_exam_results_exam ON exam_results(examination_id);
CREATE INDEX IF NOT EXISTS idx_exam_results_student ON exam_results(student_id);
CREATE INDEX IF NOT EXISTS idx_exam_results_attempt ON exam_results(attempt_id);
CREATE INDEX IF NOT EXISTS idx_exam_results_grading_status ON exam_results(grading_status);
CREATE INDEX IF NOT EXISTS idx_exam_results_publication_status ON exam_results(publication_status);

-- 2. Question-Level Grade Details Table
CREATE TABLE IF NOT EXISTS grade_details (
    id BIGSERIAL PRIMARY KEY,
    exam_result_id BIGINT NOT NULL REFERENCES exam_results(id) ON DELETE CASCADE,
    exam_question_id BIGINT NOT NULL REFERENCES exam_questions(id) ON DELETE RESTRICT,
    student_answer_id BIGINT REFERENCES student_answers(id) ON DELETE SET NULL,
    awarded_marks NUMERIC(6, 2), -- Nullable until graded. 0.00 is a valid earned zero mark!
    maximum_marks NUMERIC(6, 2), -- Nullable if not configured on the exam question
    feedback TEXT,
    is_auto_graded BOOLEAN NOT NULL DEFAULT FALSE,
    graded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    graded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_awarded_non_negative CHECK (awarded_marks IS NULL OR awarded_marks >= 0),
    CONSTRAINT uq_result_question_grade UNIQUE(exam_result_id, exam_question_id)
);

CREATE INDEX IF NOT EXISTS idx_grade_details_result ON grade_details(exam_result_id);
CREATE INDEX IF NOT EXISTS idx_grade_details_question ON grade_details(exam_question_id);
CREATE INDEX IF NOT EXISTS idx_grade_details_answer ON grade_details(student_answer_id);

-- 3. Result Correction Audits Table (Auditable corrections to published results)
CREATE TABLE IF NOT EXISTS result_correction_audits (
    id BIGSERIAL PRIMARY KEY,
    exam_result_id BIGINT NOT NULL REFERENCES exam_results(id) ON DELETE CASCADE,
    corrected_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    previous_earned_marks NUMERIC(7, 2),
    new_earned_marks NUMERIC(7, 2) NOT NULL,
    reason TEXT NOT NULL,
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_result_corrections_result ON result_correction_audits(exam_result_id);
CREATE INDEX IF NOT EXISTS idx_result_corrections_user ON result_correction_audits(corrected_by);
