package com.oes.onlineexamsystem.execution.entity;

public enum AttemptStatus {
    NOT_STARTED,
    WAITING_FOR_START,
    ACTIVE,
    IN_PROGRESS,
    CONNECTION_PAUSED,
    RESUME_REQUIRED,
    SUBMITTED,
    AUTO_SUBMITTED,
    EXPIRED,
    CANCELLED,
    ABANDONED;

    public boolean isFinished() {
        return this == SUBMITTED || this == AUTO_SUBMITTED || this == EXPIRED || this == CANCELLED || this == ABANDONED;
    }

    public boolean canResume() {
        return this == CONNECTION_PAUSED || this == RESUME_REQUIRED;
    }

    public boolean isActive() {
        return this == ACTIVE || this == IN_PROGRESS;
    }
}
