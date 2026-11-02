-- ==============================================================================
-- Migration V4: Question Bank Module Schema and Seed Data
-- ==============================================================================

-- 1. Question Categories
CREATE TABLE IF NOT EXISTS question_categories (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT REFERENCES courses(id) ON DELETE SET NULL,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_question_categories_code ON question_categories(code);
CREATE INDEX IF NOT EXISTS idx_question_categories_course ON question_categories(course_id);

-- 2. Question Banks
CREATE TABLE IF NOT EXISTS question_banks (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_question_banks_course ON question_banks(course_id);
CREATE INDEX IF NOT EXISTS idx_question_banks_created_by ON question_banks(created_by);

-- 3. Questions
CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
    bank_id BIGINT REFERENCES question_banks(id) ON DELETE SET NULL,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    category_id BIGINT REFERENCES question_categories(id) ON DELETE SET NULL,
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    question_text TEXT NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    difficulty_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    default_marks NUMERIC(5, 2) NOT NULL DEFAULT 1.00,
    explanation TEXT,
    version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_questions_bank ON questions(bank_id);
CREATE INDEX IF NOT EXISTS idx_questions_course ON questions(course_id);
CREATE INDEX IF NOT EXISTS idx_questions_category ON questions(category_id);
CREATE INDEX IF NOT EXISTS idx_questions_created_by ON questions(created_by);
CREATE INDEX IF NOT EXISTS idx_questions_type ON questions(question_type);
CREATE INDEX IF NOT EXISTS idx_questions_difficulty ON questions(difficulty_level);

-- 4. Question Options
CREATE TABLE IF NOT EXISTS question_options (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL,
    option_order INT NOT NULL DEFAULT 1,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    explanation TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_question_options_question ON question_options(question_id);

-- ==============================================================================
-- Seed Sample Data for Course CS101 & Instructor
-- ==============================================================================

-- Seed Categories
INSERT INTO question_categories (course_id, name, code, description)
SELECT c.id, 'Core Programming Concepts', 'CAT-PROG', 'Fundamental syntax, data types, and control flow'
FROM courses c WHERE c.code = 'CS101'
ON CONFLICT (code) DO NOTHING;

INSERT INTO question_categories (course_id, name, code, description)
SELECT c.id, 'Data Structures & Algorithms', 'CAT-DS', 'Arrays, linked lists, stacks, queues, and tree fundamentals'
FROM courses c WHERE c.code = 'CS101'
ON CONFLICT (code) DO NOTHING;

INSERT INTO question_categories (course_id, name, code, description)
SELECT c.id, 'Software Architecture & Design', 'CAT-ARCH', 'Design paradigms, microservices, and system architecture'
FROM courses c WHERE c.code = 'CS101'
ON CONFLICT (code) DO NOTHING;

-- Seed Question Bank
INSERT INTO question_banks (course_id, created_by, name, description)
SELECT c.id, u.id, 'CS101 Midterm Examination Question Pool', 'Curated question pool for CS101 foundational exams'
FROM courses c, users u
WHERE c.code = 'CS101' AND u.email = 'instructor@college.edu'
ON CONFLICT DO NOTHING;

-- Seed Question 1: SINGLE_CHOICE
INSERT INTO questions (bank_id, course_id, category_id, created_by, question_text, question_type, difficulty_level, default_marks, explanation)
SELECT qb.id, c.id, qc.id, u.id,
    'Which data structure operates strictly on a Last In, First Out (LIFO) order of elements?',
    'SINGLE_CHOICE', 'EASY', 2.00,
    'A stack is a linear data structure in which insertions and deletions take place at the same end (the top).'
FROM question_banks qb, courses c, question_categories qc, users u
WHERE qb.name = 'CS101 Midterm Examination Question Pool'
  AND c.code = 'CS101'
  AND qc.code = 'CAT-DS'
  AND u.email = 'instructor@college.edu'
LIMIT 1;

-- Options for Question 1
INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'Stack', 1, true
FROM questions q WHERE q.question_text LIKE 'Which data structure operates strictly on a Last In, First Out%' LIMIT 1;

INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'Queue', 2, false
FROM questions q WHERE q.question_text LIKE 'Which data structure operates strictly on a Last In, First Out%' LIMIT 1;

INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'Linked List', 3, false
FROM questions q WHERE q.question_text LIKE 'Which data structure operates strictly on a Last In, First Out%' LIMIT 1;

INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'Binary Search Tree', 4, false
FROM questions q WHERE q.question_text LIKE 'Which data structure operates strictly on a Last In, First Out%' LIMIT 1;

-- Seed Question 2: MULTIPLE_CHOICE
INSERT INTO questions (bank_id, course_id, category_id, created_by, question_text, question_type, difficulty_level, default_marks, explanation)
SELECT qb.id, c.id, qc.id, u.id,
    'Which of the following are primitive data types in the Java programming language? (Select all that apply)',
    'MULTIPLE_CHOICE', 'MEDIUM', 3.00,
    'int, boolean, and double are built-in primitive types. String is a reference class type from java.lang.'
FROM question_banks qb, courses c, question_categories qc, users u
WHERE qb.name = 'CS101 Midterm Examination Question Pool'
  AND c.code = 'CS101'
  AND qc.code = 'CAT-PROG'
  AND u.email = 'instructor@college.edu'
LIMIT 1;

-- Options for Question 2
INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'int', 1, true
FROM questions q WHERE q.question_text LIKE 'Which of the following are primitive data types in the Java programming language%' LIMIT 1;

INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'boolean', 2, true
FROM questions q WHERE q.question_text LIKE 'Which of the following are primitive data types in the Java programming language%' LIMIT 1;

INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'String', 3, false
FROM questions q WHERE q.question_text LIKE 'Which of the following are primitive data types in the Java programming language%' LIMIT 1;

INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'double', 4, true
FROM questions q WHERE q.question_text LIKE 'Which of the following are primitive data types in the Java programming language%' LIMIT 1;

-- Seed Question 3: TRUE_FALSE
INSERT INTO questions (bank_id, course_id, category_id, created_by, question_text, question_type, difficulty_level, default_marks, explanation)
SELECT qb.id, c.id, qc.id, u.id,
    'In Java, an interface can implement another interface using the "implements" keyword.',
    'TRUE_FALSE', 'EASY', 1.00,
    'False. An interface extends another interface using the "extends" keyword. Classes implement interfaces.'
FROM question_banks qb, courses c, question_categories qc, users u
WHERE qb.name = 'CS101 Midterm Examination Question Pool'
  AND c.code = 'CS101'
  AND qc.code = 'CAT-PROG'
  AND u.email = 'instructor@college.edu'
LIMIT 1;

-- Options for Question 3
INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'True', 1, false
FROM questions q WHERE q.question_text LIKE 'In Java, an interface can implement another interface%' LIMIT 1;

INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'False', 2, true
FROM questions q WHERE q.question_text LIKE 'In Java, an interface can implement another interface%' LIMIT 1;

-- Seed Question 4: SHORT_ANSWER
INSERT INTO questions (bank_id, course_id, category_id, created_by, question_text, question_type, difficulty_level, default_marks, explanation)
SELECT qb.id, c.id, qc.id, u.id,
    'What is the average time complexity of searching for an element in a balanced Binary Search Tree containing n nodes? (Provide Big-O notation)',
    'SHORT_ANSWER', 'MEDIUM', 2.00,
    'A balanced BST halves the search space at every comparison level, resulting in logarithmic time complexity.'
FROM question_banks qb, courses c, question_categories qc, users u
WHERE qb.name = 'CS101 Midterm Examination Question Pool'
  AND c.code = 'CS101'
  AND qc.code = 'CAT-DS'
  AND u.email = 'instructor@college.edu'
LIMIT 1;

-- Accepted answer options for Question 4
INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'O(log n)', 1, true
FROM questions q WHERE q.question_text LIKE 'What is the average time complexity of searching for an element in a balanced Binary Search Tree%' LIMIT 1;

INSERT INTO question_options (question_id, option_text, option_order, is_correct)
SELECT q.id, 'O(logn)', 2, true
FROM questions q WHERE q.question_text LIKE 'What is the average time complexity of searching for an element in a balanced Binary Search Tree%' LIMIT 1;

-- Seed Question 5: ESSAY
INSERT INTO questions (bank_id, course_id, category_id, created_by, question_text, question_type, difficulty_level, default_marks, explanation)
SELECT qb.id, c.id, qc.id, u.id,
    'Compare monolithic architecture and microservices architecture. Evaluate their operational trade-offs regarding deployment complexity, fault isolation, and independent scalability.',
    'ESSAY', 'HARD', 5.00,
    'Instructor Rubric: Evaluate discussion of deployment overhead (2 pts), fault isolation and cascading failures (1.5 pts), and data consistency/distributed transactions (1.5 pts).'
FROM question_banks qb, courses c, question_categories qc, users u
WHERE qb.name = 'CS101 Midterm Examination Question Pool'
  AND c.code = 'CS101'
  AND qc.code = 'CAT-ARCH'
  AND u.email = 'instructor@college.edu'
LIMIT 1;
