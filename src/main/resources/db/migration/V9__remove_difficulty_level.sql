-- ====================================================================
-- Migration: V9__remove_difficulty_level.sql
-- Description: Drop unused difficulty_level column and its index from questions table
-- ====================================================================

DROP INDEX IF EXISTS idx_questions_difficulty;

ALTER TABLE questions DROP COLUMN IF EXISTS difficulty_level;
