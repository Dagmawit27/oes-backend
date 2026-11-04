-- ====================================================================
-- Migration: V9__remove_difficulty_level.sql
-- Description: Drop unused difficulty_level column and its index from questions table,
-- and clean up pre-seeded questions so instructors start with clean data.
-- ====================================================================

-- 1. Drop difficulty level column and index if exists
DROP INDEX IF EXISTS idx_questions_difficulty;

ALTER TABLE questions DROP COLUMN IF EXISTS difficulty_level;

-- 2. Remove pre-seeded questions and their associations in dependency order
DELETE FROM grade_details;
DELETE FROM student_answers;
DELETE FROM exam_questions;
DELETE FROM question_options;
DELETE FROM questions;
