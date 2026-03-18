package com.neobank.auth.domain.events;

import com.neobank.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * Published when a new user successfully registers.
 *
 * This event is published AFTER:
 * - User entity has been persisted to database
 * - User has been assigned ROLE_USER
 * - Password has been hashed and stored
 *
 * This event allows listeners to perform safe side effects like audit recording,
 * welcome email notifications, analytics tracking, etc.
 *
 * Listeners should NOT attempt to mutate the user in response to this event.
 */
public class UserRegisteredEvent extends DomainEvent {

    private static final String EVENT_TYPE = "UserRegistered";

    private final UUID userId;
    private final String email;

    public UserRegisteredEvent(UUID userId, String email) {
        super();
        this.userId = userId;
        this.email = email;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    // Accessors
    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        return super.toString() +
                " UserRegisteredEvent{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                '}';
    }
}

