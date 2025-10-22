package com.edu.service;

import com.edu.model.EmailTrackingEntity;
import com.edu.repository.EmailTrackingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailTrackingService, mocking the JPA repository interaction.
 */
@ExtendWith(MockitoExtension.class)
class EmailTrackingServiceTest {

    @Mock
    private EmailTrackingRepository trackingRepository;

    @InjectMocks
    private EmailTrackingService emailTrackingService;

    private static final String TEST_ID = "test-tracking-456";
    private static final String RECIPIENT = "test@user.com";
    private static final String BATCH_ID = "batch-xyz";
    private LocalDateTime SENT_TIME = LocalDateTime.now().minusHours(1);

    private EmailTrackingEntity createTestEntity(int openCount) {
        EmailTrackingEntity entity = new EmailTrackingEntity(TEST_ID, RECIPIENT, BATCH_ID, SENT_TIME);
        entity.setOpenCount(openCount);
        if (openCount > 0) {
            entity.setFirstOpenTimestamp(SENT_TIME.plusMinutes(1));
            entity.setLastOpenTimestamp(SENT_TIME.plusMinutes(1 + openCount));
        }
        return entity;
    }

    @Test
    void saveSentEmail_savesNewEntity() {
        emailTrackingService.saveSentEmail(TEST_ID, RECIPIENT, BATCH_ID, SENT_TIME);

        // Verify that the save method was called exactly once with the correct entity data
        verify(trackingRepository, times(1)).save(argThat(entity ->
                TEST_ID.equals(entity.getTrackingId()) &&
                        RECIPIENT.equals(entity.getRecipientEmail()) &&
                        BATCH_ID.equals(entity.getBatchId()) &&
                        SENT_TIME.equals(entity.getSentTimestamp()) &&
                        entity.getOpenCount() == 0
        ));
    }

    @Test
    void trackOpen_firstOpen_updatesTimestampsAndCount() {
        EmailTrackingEntity initialEntity = createTestEntity(0);

        // Mock finding the entity with 0 opens
        when(trackingRepository.findByTrackingId(TEST_ID)).thenReturn(Optional.of(initialEntity));
        // Mock the saving process (which should return the modified entity)
        when(trackingRepository.save(any(EmailTrackingEntity.class))).thenAnswer(i -> i.getArgument(0));

        Optional<EmailTrackingEntity> result = emailTrackingService.trackOpen(TEST_ID,"12344","agent");

        assertTrue(result.isPresent());
        EmailTrackingEntity updatedEntity = result.get();

        // Assert open count incremented
        assertEquals(1, updatedEntity.getOpenCount());

        // Assert first and last open timestamps are set to 'now' (or close to it)
        assertNotNull(updatedEntity.getFirstOpenTimestamp());
        assertNotNull(updatedEntity.getLastOpenTimestamp());

        assertEquals(updatedEntity.getFirstOpenTimestamp(), updatedEntity.getLastOpenTimestamp());
        assertTrue(updatedEntity.getLastOpenTimestamp().isAfter(SENT_TIME));

        // Verify save was called implicitly/explicitly
        verify(trackingRepository, times(1)).save(updatedEntity);
    }

    @Test
    void trackOpen_subsequentOpen_updatesCountAndLastTimestamp() throws InterruptedException { // Added throws InterruptedException
        EmailTrackingEntity existingEntity = createTestEntity(5);
        LocalDateTime oldLastOpen = existingEntity.getLastOpenTimestamp();
        LocalDateTime oldFirstOpen = existingEntity.getFirstOpenTimestamp();

        // Mock finding the entity with 5 opens
        when(trackingRepository.findByTrackingId(TEST_ID)).thenReturn(Optional.of(existingEntity));
        // Mock the saving process
        when(trackingRepository.save(any(EmailTrackingEntity.class))).thenAnswer(i -> i.getArgument(0));

        // CRITICAL FIX: Add a small delay to ensure LocalDateTime.now() in the service is strictly later
        Thread.sleep(5);

        Optional<EmailTrackingEntity> result = emailTrackingService.trackOpen(TEST_ID, "12344","agent");

        assertTrue(result.isPresent());
        EmailTrackingEntity updatedEntity = result.get();

        // Assert open count incremented (Expected 6 based on 5 + 1)
        assertEquals(6, updatedEntity.getOpenCount());

        // Assert first open timestamp is unchanged
        assertEquals(oldFirstOpen, updatedEntity.getFirstOpenTimestamp());

        // Assert last open timestamp is updated and is STRICTLY after the old timestamp
        assertTrue(updatedEntity.getLastOpenTimestamp().isAfter(oldLastOpen));

        verify(trackingRepository, times(1)).save(updatedEntity);
    }

    @Test
    void trackOpen_recordNotFound_returnsEmptyOptional() {
        // Mock repository returning empty Optional
        when(trackingRepository.findByTrackingId(TEST_ID)).thenReturn(Optional.empty());

        Optional<EmailTrackingEntity> result = emailTrackingService.trackOpen(TEST_ID,"12344","agent");

        assertFalse(result.isPresent());
        // Verify save was not called
        verify(trackingRepository, never()).save(any(EmailTrackingEntity.class));
    }

    @Test
    void getTrackingStatus_recordExists_returnsEntity() {
        EmailTrackingEntity expectedEntity = createTestEntity(1);
        when(trackingRepository.findByTrackingId(TEST_ID)).thenReturn(Optional.of(expectedEntity));

        Optional<EmailTrackingEntity> result = emailTrackingService.getTrackingStatus(TEST_ID);

        assertTrue(result.isPresent());
        assertEquals(expectedEntity, result.get());
        verify(trackingRepository, times(1)).findByTrackingId(TEST_ID);
    }

    @Test
    void getTrackingStatus_recordDoesNotExist_returnsEmptyOptional() {
        when(trackingRepository.findByTrackingId(TEST_ID)).thenReturn(Optional.empty());

        Optional<EmailTrackingEntity> result = emailTrackingService.getTrackingStatus(TEST_ID);

        assertFalse(result.isPresent());
        verify(trackingRepository, times(1)).findByTrackingId(TEST_ID);
    }
}
