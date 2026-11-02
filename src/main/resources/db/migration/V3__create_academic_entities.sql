-- V3__create_academic_entities.sql: Academic Management Module Entities

CREATE TABLE IF NOT EXISTS departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    code VARCHAR(20) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS programs (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    degree_type VARCHAR(50) NOT NULL,
    duration_years INT NOT NULL DEFAULT 4,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic_years (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS semesters (
    id BIGSERIAL PRIMARY KEY,
    academic_year_id BIGINT NOT NULL REFERENCES academic_years(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    semester_number INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_semester_year_number UNIQUE (academic_year_id, semester_number)
);

CREATE TABLE IF NOT EXISTS courses (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    description TEXT,
    credits INT NOT NULL DEFAULT 3,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS instructors (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    department_id BIGINT NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    specialization VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS students (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    program_id BIGINT NOT NULL REFERENCES programs(id) ON DELETE RESTRICT,
    registration_number VARCHAR(50) NOT NULL UNIQUE,
    year_of_study INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS course_offerings (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    semester_id BIGINT NOT NULL REFERENCES semesters(id) ON DELETE RESTRICT,
    instructor_id BIGINT NOT NULL REFERENCES instructors(id) ON DELETE RESTRICT,
    section VARCHAR(20) NOT NULL DEFAULT 'A',
    max_students INT NOT NULL DEFAULT 50,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_course_offering_unique UNIQUE (course_id, semester_id, section)
);

CREATE TABLE IF NOT EXISTS enrollments (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    course_offering_id BIGINT NOT NULL REFERENCES course_offerings(id) ON DELETE RESTRICT,
    enrolled_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_offering_enrollment UNIQUE (student_id, course_offering_id)
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_programs_dept ON programs(department_id);
CREATE INDEX IF NOT EXISTS idx_semesters_academic_year ON semesters(academic_year_id);
CREATE INDEX IF NOT EXISTS idx_courses_dept ON courses(department_id);
CREATE INDEX IF NOT EXISTS idx_course_offerings_course ON course_offerings(course_id);
CREATE INDEX IF NOT EXISTS idx_course_offerings_semester ON course_offerings(semester_id);
CREATE INDEX IF NOT EXISTS idx_course_offerings_instructor ON course_offerings(instructor_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_student ON enrollments(student_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_offering ON enrollments(course_offering_id);

-- Seed Academic Foundation Data
INSERT INTO departments (name, code, description) VALUES
('Computer Science and Information Technology', 'CSIT', 'Department of Computer Science and Software Engineering'),
('Mathematics and Statistics', 'MATH', 'Department of Pure and Applied Mathematics')
ON CONFLICT (code) DO NOTHING;

INSERT INTO programs (department_id, name, code, degree_type, duration_years)
SELECT d.id, 'Bachelor of Science in Computer Science', 'BSCS', 'BACHELOR_OF_SCIENCE', 4
FROM departments d WHERE d.code = 'CSIT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO programs (department_id, name, code, degree_type, duration_years)
SELECT d.id, 'Bachelor of Science in Software Engineering', 'BSSE', 'BACHELOR_OF_SCIENCE', 4
FROM departments d WHERE d.code = 'CSIT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO academic_years (name, start_date, end_date, is_current) VALUES
('2025-2026', '2025-09-01', '2026-06-30', true)
ON CONFLICT (name) DO NOTHING;

INSERT INTO semesters (academic_year_id, name, semester_number, start_date, end_date, is_current)
SELECT ay.id, 'Fall 2025', 1, '2025-09-01', '2026-01-31', true
FROM academic_years ay WHERE ay.name = '2025-2026'
ON CONFLICT DO NOTHING;

INSERT INTO courses (department_id, name, code, description, credits)
SELECT d.id, 'Introduction to Computer Science', 'CS101', 'Foundations of computing, problem solving, and algorithms', 3
FROM departments d WHERE d.code = 'CSIT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO courses (department_id, name, code, description, credits)
SELECT d.id, 'Data Structures & Algorithms', 'CS201', 'Analysis of linear and non-linear data structures, sorting and graph algorithms', 4
FROM departments d WHERE d.code = 'CSIT'
ON CONFLICT (code) DO NOTHING;

-- Link Seeded Instructor
INSERT INTO instructors (user_id, department_id, employee_id, specialization)
SELECT u.id, d.id, 'INS-001', 'Algorithms & Software Engineering'
FROM users u, departments d
WHERE u.email = 'instructor@college.edu' AND d.code = 'CSIT'
ON CONFLICT (user_id) DO NOTHING;

-- Link Seeded Student
INSERT INTO students (user_id, program_id, registration_number, year_of_study)
SELECT u.id, p.id, 'STU-2025-001', 2
FROM users u, programs p
WHERE u.email = 'student@college.edu' AND p.code = 'BSCS'
ON CONFLICT (user_id) DO NOTHING;

-- Seed Course Offering for CS101 in Fall 2025 taught by INS-001
INSERT INTO course_offerings (course_id, semester_id, instructor_id, section, max_students)
SELECT c.id, s.id, i.id, 'A', 60
FROM courses c, semesters s, instructors i
WHERE c.code = 'CS101' AND s.name = 'Fall 2025' AND i.employee_id = 'INS-001'
ON CONFLICT DO NOTHING;

-- Enroll Seeded Student into CS101 Offering
INSERT INTO enrollments (student_id, course_offering_id, status)
SELECT st.id, co.id, 'ACTIVE'
FROM students st, course_offerings co, courses c
WHERE st.registration_number = 'STU-2025-001' AND co.course_id = c.id AND c.code = 'CS101'
ON CONFLICT DO NOTHING;
