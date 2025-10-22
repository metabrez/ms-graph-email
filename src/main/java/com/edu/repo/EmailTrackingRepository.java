package com.edu.repository;

import com.edu.model.EmailTrackingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; // NEW IMPORT
import org.springframework.data.jpa.repository.Query; // NEW IMPORT
import org.springframework.data.repository.query.Param; // NEW IMPORT
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime; // NEW IMPORT
import java.util.Optional;

/**
 * Spring Data JPA repository for the EmailTrackingEntity.
 * Provides standard CRUD and query functionality based on the primary key (trackingId).
 */
@Repository
public interface EmailTrackingRepository extends JpaRepository<EmailTrackingEntity, String> {

    Optional<EmailTrackingEntity> findByTrackingId(String trackingId);

    /**
     * Atomically increments the openCount and updates the lastOpenTimestamp for the given tracking ID.
     * This avoids concurrency issues (lost updates) that occur when reading and then saving the entity.
     * @param trackingId The unique ID of the record to update.
     * @param timestamp The current time to set as the last open timestamp.
     * @return The number of rows affected (should be 1).
     */
    @Modifying
    @Query("UPDATE EmailTrackingEntity e SET " +
            "e.openCount = e.openCount + 1, " +
            "e.lastOpenTimestamp = :timestamp, " +
            "e.firstOpenTimestamp = CASE WHEN e.openCount = 0 THEN :timestamp ELSE e.firstOpenTimestamp END " +
            "WHERE e.trackingId = :trackingId")
    int incrementOpenCountAndSetTimestamps(@Param("trackingId") String trackingId, @Param("timestamp") LocalDateTime timestamp);

}
