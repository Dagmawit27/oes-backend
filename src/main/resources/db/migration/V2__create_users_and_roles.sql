-- V2__create_users_and_roles.sql: Users, Roles, User_Roles, and Refresh_Tokens

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Seed System Roles
INSERT INTO roles (name, description) VALUES
('ROLE_ADMIN', 'System Administrator with full access to accounts and college configuration'),
('ROLE_EXAM_OFFICER', 'Exam Officer responsible for reviewing, scheduling, and publishing exams'),
('ROLE_INSTRUCTOR', 'Instructor responsible for questions, examination authoring, and subjective grading'),
('ROLE_STUDENT', 'Student enrolled in courses taking scheduled examinations')
ON CONFLICT (name) DO NOTHING;

-- Seed Default Accounts (password for all seeded accounts is: Admin@123)
-- Hash generated via Spring Security BCrypt 10 rounds: $2a$10$wHxQRmEH9liYaYTt1GG6EuEiY9bVYtAVzwyVlhvYNHNDu7wFd5bsm
INSERT INTO users (email, password_hash, first_name, last_name, phone, is_active) VALUES
('admin@college.edu', '$2a$10$wHxQRmEH9liYaYTt1GG6EuEiY9bVYtAVzwyVlhvYNHNDu7wFd5bsm', 'System', 'Administrator', '+1-555-0100', true),
('officer@college.edu', '$2a$10$wHxQRmEH9liYaYTt1GG6EuEiY9bVYtAVzwyVlhvYNHNDu7wFd5bsm', 'Exam', 'Officer', '+1-555-0101', true),
('instructor@college.edu', '$2a$10$wHxQRmEH9liYaYTt1GG6EuEiY9bVYtAVzwyVlhvYNHNDu7wFd5bsm', 'Lead', 'Instructor', '+1-555-0102', true),
('student@college.edu', '$2a$10$wHxQRmEH9liYaYTt1GG6EuEiY9bVYtAVzwyVlhvYNHNDu7wFd5bsm', 'Alex', 'Student', '+1-555-0103', true)
ON CONFLICT (email) DO NOTHING;

-- Assign Roles to Seeded Accounts
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@college.edu' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'officer@college.edu' AND r.name = 'ROLE_EXAM_OFFICER'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'instructor@college.edu' AND r.name = 'ROLE_INSTRUCTOR'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'student@college.edu' AND r.name = 'ROLE_STUDENT'
ON CONFLICT DO NOTHING;
