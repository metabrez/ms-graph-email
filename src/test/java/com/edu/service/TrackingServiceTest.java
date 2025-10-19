package com.edu.service;

import com.edu.model.EmailOpenTracking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TrackingService, which handles in-memory email open tracking.
 */
class TrackingServiceTest {

    private TrackingService trackingService;
    private static final String TEST_ID = "test-tracking-123";

    @BeforeEach
    void setUp() {
        // Initialize a new service instance for each test to ensure isolation
        this.trackingService = new TrackingService();
    }

    @Test
    void trackOpen_firstOpen_createsNewRecord() {
        // Track the first open
        EmailOpenTracking result = trackingService.trackOpen(TEST_ID);

        assertNotNull(result);
        assertEquals(TEST_ID, result.getTrackingId());
        assertEquals(1, result.getOpenCount());

        // Assert that the first and last open timestamps are very close to 'now'
        assertTrue(result.getFirstOpenTimestamp().isBefore(LocalDateTime.now().plus(1, ChronoUnit.SECONDS)));
        assertEquals(result.getFirstOpenTimestamp(), result.getLastOpenTimestamp());
    }

    @Test
    void trackOpen_subsequentOpen_updatesCountAndLastTimestamp() throws InterruptedException {
        // 1. First Open
        EmailOpenTracking firstOpen = trackingService.trackOpen(TEST_ID);
        LocalDateTime initialFirstOpenTime = firstOpen.getFirstOpenTimestamp();

        // Ensure a time difference between opens
        Thread.sleep(100);

        // 2. Second Open
        EmailOpenTracking secondOpen = trackingService.trackOpen(TEST_ID);

        // Assert open count incremented
        assertEquals(2, secondOpen.getOpenCount());

        // Assert first open timestamp remains the same
        assertEquals(initialFirstOpenTime, secondOpen.getFirstOpenTimestamp());

        // Assert last open timestamp is updated and is later than the first open
        assertTrue(secondOpen.getLastOpenTimestamp().isAfter(initialFirstOpenTime));
    }

    @Test
    void getTrackingStatus_recordExists_returnsTrackingRecord() {
        trackingService.trackOpen(TEST_ID);

        EmailOpenTracking result = trackingService.getTrackingStatus(TEST_ID);

        assertNotNull(result);
        assertEquals(1, result.getOpenCount());
    }

    @Test
    void getTrackingStatus_recordDoesNotExist_returnsNull() {
        EmailOpenTracking result = trackingService.getTrackingStatus("non-existent-id");
        assertNull(result);
    }
}
