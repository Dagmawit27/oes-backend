-- V1__init_schema.sql: Initial database initialization and verification table
CREATE TABLE IF NOT EXISTS system_health_check (
    id BIGSERIAL PRIMARY KEY,
    component_name VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);

INSERT INTO system_health_check (component_name, status, details)
VALUES ('DATABASE', 'INITIALIZED', 'PostgreSQL database connection and Flyway migration initialized successfully.')
ON CONFLICT (component_name) DO UPDATE 
SET status = EXCLUDED.status, 
    checked_at = CURRENT_TIMESTAMP, 
    details = EXCLUDED.details;
