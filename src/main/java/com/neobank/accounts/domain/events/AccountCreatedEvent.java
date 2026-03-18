package com.neobank.accounts.domain.events;

import com.neobank.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * Published when a new account is successfully created.
 *
 * This event is published AFTER:
 * - Account entity has been persisted to database
 * - Account has been assigned an account number
 * - Initial balance has been set
 *
 * This event allows listeners to perform safe side effects like audit recording,
 * user notifications, analytics tracking, etc.
 *
 * Listeners should NOT attempt to mutate the account in response to this event.
 */
public class AccountCreatedEvent extends DomainEvent {

    private static final String EVENT_TYPE = "AccountCreated";

    private final UUID accountId;
    private final String accountNumber;
    private final UUID ownerId;
    private final String accountType;
    private final String currency;

    public AccountCreatedEvent(
            UUID accountId,
            String accountNumber,
            UUID ownerId,
            String accountType,
            String currency
    ) {
        super();
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.ownerId = ownerId;
        this.accountType = accountType;
        this.currency = currency;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    // Accessors
    public UUID getAccountId() {
        return accountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public String toString() {
        return super.toString() +
                " AccountCreatedEvent{" +
                "accountId=" + accountId +
                ", accountNumber='" + accountNumber + '\'' +
                ", ownerId=" + ownerId +
                ", accountType='" + accountType + '\'' +
                ", currency='" + currency + '\'' +
                '}';
    }
}

