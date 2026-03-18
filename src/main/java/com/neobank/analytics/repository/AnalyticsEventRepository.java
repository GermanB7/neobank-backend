package com.neobank.analytics.repository;

import com.neobank.analytics.domain.AnalyticsEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AnalyticsEventEntity.
 *
 * Provides data access for analytics events collected from Kafka.
 */
@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEventEntity, UUID> {

    /**
     * Find an analytics event by its event ID.
     *
     * @param eventId the event ID from Kafka
     * @return optional containing the entity if found
     */
    Optional<AnalyticsEventEntity> findByEventId(UUID eventId);

    /**
     * Count analytics events received within a time window.
     *
     * @param start start time (inclusive)
     * @param end end time (inclusive)
     * @return count of events in the window
     */
    long countByReceivedAtBetween(Instant start, Instant end);
}

