package com.edu.service;

import com.edu.model.EmailTrackingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service to manage email tracking persistence using a MySQL database via JPA.
 * This service handles saving the initial record and updating open tracking metrics.
 */
@Service
public class EmailTrackingService {

    private static final Logger log = LoggerFactory.getLogger(EmailTrackingService.class);
    @Autowired
    private com.edu.repository.EmailTrackingRepository trackingRepository;

    public EmailTrackingService() {
        this.trackingRepository = trackingRepository;
    }

    /**
     * Records the initial email send event in the database.
     * This is called after an email has been successfully transmitted.
     * @param trackingId The unique ID for this individual email copy.
     * @param recipientEmail The email address of the recipient.
     * @param batchId The ID of the overall sending group.
     * @param sentTimestamp The time the email was sent.
     */
    public void saveSentEmail(String trackingId, String recipientEmail, String batchId, LocalDateTime sentTimestamp) {
        try {
            EmailTrackingEntity newTracking = new EmailTrackingEntity(
                    trackingId,
                    recipientEmail,
                    batchId,
                    sentTimestamp
            );
            trackingRepository.save(newTracking);
            log.info("Saved initial tracking record for ID: {}", trackingId);
        } catch (Exception e) {
            log.error("Failed to save initial tracking record for ID {}: {}", trackingId, e.getMessage(), e);
        }
    }

    /**
     * NEW: Retrieves all email tracking records from the database.
     * @return A list of all EmailTrackingEntity objects.
     */
    public List<EmailTrackingEntity> getAllTrackingStatus() {
        // JpaRepository's findAll() method retrieves all records.
        return trackingRepository.findAll();
    }

    /**
     * Handles the tracking pixel hit by incrementing the open count and updating timestamps.
     * Uses @Transactional to ensure the read-modify-write is atomic.
     * @param trackingId The unique ID from the tracking pixel.
     * @return The updated tracking information, or null if the record doesn't exist.
     */
    @Transactional
    public Optional<EmailTrackingEntity> trackOpen(String trackingId, String ipAddress, String userAgent) {
        Optional<EmailTrackingEntity> trackingOpt = trackingRepository.findByTrackingId(trackingId);

        if (trackingOpt.isPresent()) {
            EmailTrackingEntity tracking = trackingOpt.get();
            LocalDateTime now = LocalDateTime.now();

            if (tracking.getOpenCount() == 0) {
                // First open
                tracking.setFirstOpenTimestamp(now);

                // 1. IP Address and Raw User Agent
                tracking.setClientIpAddress(ipAddress);
                tracking.setClientUserAgent(userAgent);

                // 2. Parsed Data (Browser/Device) and Geolocation
                // In a real app, this is where you'd use a parser library (like UAParser)
                // and a GeoIP service (like MaxMind)

                // NOTE: Using raw userAgent as a temporary placeholder for Browser/Device
                tracking.setClientBrowser(userAgent != null ? userAgent : "Unknown");
                tracking.setClientDevice(userAgent != null ? userAgent : "Unknown");
                tracking.setClientCity("N/A - GeoIP Required");
                tracking.setClientCountry("N/A - GeoIP Required");

                log.info("First open recorded for ID {}. IP: {}, Agent: {}", trackingId, ipAddress, userAgent);
            }
            // Subsequent open
            tracking.setOpenCount(tracking.getOpenCount() + 1);
            tracking.setLastOpenTimestamp(now);

            // The save is implicitly handled by the transaction's commit, but we call it explicitly for clarity/immediate persistence
            return Optional.of(trackingRepository.save(tracking));
        } else {
            log.warn("Tracking record not found for open event ID: {}", trackingId);
            return Optional.empty();
        }
    }

    /**
     * Retrieves the tracking status for a given trackingId from the database.
     * @param trackingId The unique ID for the email copy.
     * @return The tracking information, or Optional.empty() if not found.
     */
    public Optional<EmailTrackingEntity> getTrackingStatus(String trackingId) {
        return trackingRepository.findByTrackingId(trackingId);
    }
}
