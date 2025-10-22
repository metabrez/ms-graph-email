package com.edu.controller;

import com.edu.model.*;
import com.edu.service.EmailTrackingService;
import com.edu.service.MailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the MailController class using MockMvc and Mockito.
 */
@WebMvcTest(MailController.class)
public class MailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MailService mailService;

    @MockBean
    private EmailTrackingService trackingService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private MailRequest mailRequest;
    private final String MOCK_TRACKING_ID = "test-group-abc-recipient@test.com";

    @BeforeEach
    void setUp() {
        // Setup a reusable MailRequest object
        MailRequest.BodyModel body = new MailRequest.BodyModel();
        body.setContentType("Html");
        body.setContent("<html><body>Test body.</body></html>");

        MailRequest.MessageModel message = new MailRequest.MessageModel();
        message.setSubject("Test Subject");
        message.setBody(body);

        MailRequest.RecipientModel recipient = new MailRequest.RecipientModel();
        recipient.setEmailAddress(new EmailAddress("recipient@test.com", "Recipient Name"));
        message.setToRecipients(Arrays.asList(recipient));

        mailRequest = new MailRequest();
        mailRequest.setMessage(message);
        mailRequest.setPreferredProtocol("SMTP");
        mailRequest.setRequestPixelTracking(true);
    }

    // --- TEST SUITE: /api/mail/send ---

    @Test
    void sendMail_Success() throws Exception {
        // Mock the successful response from the MailService
        MailResponse successResponse = MailResponse.builder()
                .status("SUCCESS")
                .message("Batch send initiated.")
                .messageId("batch-id-123")
                .build();

        when(mailService.sendEmail(any(MailRequest.class))).thenReturn(successResponse);

        mockMvc.perform(post("/api/mail/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mailRequest)))
                .andExpect(status().isAccepted()) // Expect 202 Accepted
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.messageId").value("batch-id-123"));

        verify(mailService, times(1)).sendEmail(any(MailRequest.class));
    }

    @Test
    void sendMail_Failure() throws Exception {
        // Mock the failure response from the MailService
        MailResponse failureResponse = MailResponse.builder()
                .status("FAILED")
                .message("Failed to connect to SMTP.")
                .messageId(null)
                .build();

        when(mailService.sendEmail(any(MailRequest.class))).thenReturn(failureResponse);

        mockMvc.perform(post("/api/mail/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mailRequest)))
                .andExpect(status().isInternalServerError()) // Expect 500 Internal Server Error
                .andExpect(jsonPath("$.status").value("FAILED"));

        verify(mailService, times(1)).sendEmail(any(MailRequest.class));
    }

    // --- TEST SUITE: /api/mail/status ---

    @Test
    void getMailStatus_Found() throws Exception {
        // Mock the successful status check response
        MailResponse foundResponse = MailResponse.builder()
                .status("FOUND_IN_SENT_ITEMS")
                .message("Email found.")
                .messageId("msg-123")
                .build();

        when(mailService.getSentMailStatus(anyString(), anyString())).thenReturn(foundResponse);

        mockMvc.perform(get("/api/mail/status")
                        .param("subject", "Test Subject")
                        .param("recipientEmail", "test@example.com"))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(jsonPath("$.status").value("FOUND_IN_SENT_ITEMS"));

        verify(mailService, times(1)).getSentMailStatus(eq("Test Subject"), eq("test@example.com"));
    }

    @Test
    void getMailStatus_NotFound() throws Exception {
        // Mock the not found status check response
        MailResponse notFoundResponse = MailResponse.builder()
                .status("NOT_FOUND_IN_SENT_ITEMS")
                .message("Email not found.")
                .messageId(null)
                .build();

        when(mailService.getSentMailStatus(anyString(), anyString())).thenReturn(notFoundResponse);

        mockMvc.perform(get("/api/mail/status")
                        .param("subject", "Test Subject")
                        .param("recipientEmail", "test@example.com"))
                .andExpect(status().isNotFound()) // Expect 404 Not Found
                .andExpect(jsonPath("$.status").value("NOT_FOUND_IN_SENT_ITEMS"));
    }

    @Test
    void getMailStatus_InternalServerError() throws Exception {
        // This test covers the final 'else' block (around line 80) in MailController.java
        MailResponse failedResponse = MailResponse.builder()
                .status("FAILED_STATUS_CHECK") // A status that isn't OK or NOT_FOUND
                .message("Error checking mail status.")
                .messageId(null)
                .build();

        when(mailService.getSentMailStatus(anyString(), anyString())).thenReturn(failedResponse);

        mockMvc.perform(get("/api/mail/status")
                        .param("subject", "Test Subject")
                        .param("recipientEmail", "test@example.com"))
                .andExpect(status().isInternalServerError()) // Expect 500 Internal Server Error
                .andExpect(jsonPath("$.status").value("FAILED_STATUS_CHECK"));

        verify(mailService, times(1)).getSentMailStatus(eq("Test Subject"), eq("test@example.com"));
    }

    // --- TEST SUITE: /api/mail/track/{trackingId}.gif ---

    @Test
    void trackMailOpen_Success() throws Exception {
        // Setup mock tracking entity for successful open
        EmailTrackingEntity mockEntity = new EmailTrackingEntity();
        mockEntity.setOpenCount(1);

        when(trackingService.trackOpen(eq(MOCK_TRACKING_ID), anyString(), anyString()))
                .thenReturn(Optional.of(mockEntity));

        // Note: The controller manually extracts IP and User-Agent from HttpServletRequest.
        // MockMvc handles this by default.

        ResultActions result = mockMvc.perform(get("/api/mail/track/{trackingId}.gif", MOCK_TRACKING_ID)
                        .header("User-Agent", "Test-Agent-String")
                        .header("X-FORWARDED-FOR", "192.168.1.1"))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(content().contentType("image/gif"))
                .andExpect(header().string("Content-Length", "43")); // The size of the transparent GIF byte array

        // Verify that the tracking service was called with the correct ID and non-null client details
        verify(trackingService, times(1)).trackOpen(
                eq(MOCK_TRACKING_ID),
                eq("192.168.1.1"), // from X-FORWARDED-FOR header
                eq("Test-Agent-String")
        );
    }

    @Test
    void trackMailOpen_UsesRemoteAddrIfNoForwardedFor() throws Exception {
        EmailTrackingEntity mockEntity = new EmailTrackingEntity();
        mockEntity.setOpenCount(1);

        when(trackingService.trackOpen(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(mockEntity));

        // FIX: Manually setting the remote address via a post-processor lambda, as 'withRemoteAddr' fails to resolve.
        final String remoteIp = "10.0.0.5";

        mockMvc.perform(get("/api/mail/track/{trackingId}.gif", MOCK_TRACKING_ID)
                        // Use a lambda to set the remote address directly on the MockHttpServletRequest
                        .with(request -> {
                            request.setRemoteAddr(remoteIp);
                            return request;
                        })
                        .header("User-Agent", "Test-Agent"))
                .andExpect(status().isOk());

        // Verify that the tracking service was called with the remote IP address
        verify(trackingService, times(1)).trackOpen(
                eq(MOCK_TRACKING_ID),
                eq(remoteIp),
                eq("Test-Agent")
        );
    }

    @Test
    void trackMailOpen_UsesRemoteAddrIfForwardedForIsUnknown() throws Exception {
        EmailTrackingEntity mockEntity = new EmailTrackingEntity();
        mockEntity.setOpenCount(1);

        when(trackingService.trackOpen(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(mockEntity));

        final String remoteIp = "10.0.0.6";

        // This test covers the line 93 logic: if X-FORWARDED-FOR is "unknown", fall back to getRemoteAddr().
        mockMvc.perform(get("/api/mail/track/{trackingId}.gif", MOCK_TRACKING_ID)
                        .header("X-FORWARDED-FOR", "unknown") // Triggers the fallback logic
                        .with(request -> {
                            request.setRemoteAddr(remoteIp); // The value getRemoteAddr() returns
                            return request;
                        })
                        .header("User-Agent", "Test-Agent-Unknown"))
                .andExpect(status().isOk());

        // Verify that the tracking service was called with the remote IP address (10.0.0.6)
        // instead of the 'unknown' value from the header.
        verify(trackingService, times(1)).trackOpen(
                eq(MOCK_TRACKING_ID),
                eq(remoteIp),
                eq("Test-Agent-Unknown")
        );
    }

    @Test
    void trackMailOpen_NotFoundReturnsServerError() throws Exception {
        // Mock the service to return Optional.empty(), simulating a non-existent tracking ID.
        when(trackingService.trackOpen(eq("non-existent-id"), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // When the Optional is empty, tracking.get() will crash the controller.
        // We expect an Internal Server Error (500) if the code is not fixed.
        // We use a mock IP and User-Agent to ensure the trackOpen method is called.
        mockMvc.perform(get("/api/mail/track/{trackingId}.gif", "non-existent-id")
                        .header("User-Agent", "Missing-Record-Agent")
                        .header("X-FORWARDED-FOR", "1.1.1.1"))
                // The current, buggy controller implementation will throw an exception
                // leading to a 500 status. This test ensures we cover that failure path.
                .andExpect(status().isInternalServerError());

        verify(trackingService, times(1)).trackOpen(
                eq("non-existent-id"),
                eq("1.1.1.1"),
                eq("Missing-Record-Agent")
        );
    }

    // --- TEST SUITE: /api/mail/track/status/{trackingId} ---

    @Test
    void getTrackingStatus_Found() throws Exception {
        // Mock a found EmailTrackingEntity
        EmailTrackingEntity mockEntity = new EmailTrackingEntity();
        mockEntity.setTrackingId(MOCK_TRACKING_ID);
        mockEntity.setOpenCount(5);
        mockEntity.setLastOpenTimestamp(LocalDateTime.now());
        mockEntity.setClientBrowser("Chrome 100");

        when(trackingService.getTrackingStatus(MOCK_TRACKING_ID))
                .thenReturn(Optional.of(mockEntity));

        // Since LocalDateTime is complex, we primarily check for 200 status and the ID field
        mockMvc.perform(get("/api/mail/track/status/{trackingId}", MOCK_TRACKING_ID))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.trackingId").value(MOCK_TRACKING_ID))
                .andExpect(jsonPath("$.openCount").value(5));

        verify(trackingService, times(1)).getTrackingStatus(eq(MOCK_TRACKING_ID));
    }

    @Test
    void getTrackingStatus_NotFound() throws Exception {
        // Mock a not found response
        when(trackingService.getTrackingStatus(anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/mail/track/status/{trackingId}", "non-existent-id"))
                .andExpect(status().isNotFound()) // Expect 404 Not Found
                .andExpect(content().string("")); // Expect empty body

        verify(trackingService, times(1)).getTrackingStatus(eq("non-existent-id"));
    }
}
