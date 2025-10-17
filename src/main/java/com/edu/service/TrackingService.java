package com.edu.service;

import com.edu.model.EmailOpenTracking;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrackingService {

    // In-memory storage: Maps individual tracking ID to its stats
    private final Map<String, EmailOpenTracking> trackingStore = new ConcurrentHashMap<>();

    /**
     * Records a new email open event for the given trackingId.
     * @param trackingId The unique ID for the email copy.
     * @return The updated tracking information.
     */
    public EmailOpenTracking trackOpen(String trackingId) {
        LocalDateTime now = LocalDateTime.now();
        EmailOpenTracking tracking = trackingStore.compute(trackingId, (key, existingTracking) -> {
            if (existingTracking == null) {
                // First open
                return new EmailOpenTracking(
                        trackingId,
                        1,
                        now,
                        now
                );
            } else {
                // Subsequent open
                existingTracking.setOpenCount(existingTracking.getOpenCount() + 1);
                existingTracking.setLastOpenTimestamp(now);
                // firstOpenTimestamp remains unchanged
                return existingTracking;
            }
        });
        return tracking;
    }

    /**
     * Retrieves the tracking status for a given trackingId.
     * @param trackingId The unique ID for the email copy.
     * @return The tracking information, or null if not found.
     */
    public EmailOpenTracking getTrackingStatus(String trackingId) {
        return trackingStore.get(trackingId);
    }
}