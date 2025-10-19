package com.edu.controller;

import com.edu.model.EmailAddress;
import com.edu.model.EmailOpenTracking;
import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import com.edu.service.MailService;
import com.edu.service.TrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for MailController using MockMvc to verify HTTP handling and service delegation.
 */
@WebMvcTest(MailController.class)
class MailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // Spring Boot automatically provides this

    // Mock beans for the controller's dependencies
    @MockBean
    private MailService mailService;

    @MockBean
    private TrackingService trackingService;

    // Constants
    private static final String TEST_ID = "test-id-123";
    private static final String RECIPIENT_EMAIL = "test@example.com";
    private MailRequest mockMailRequest;

    @BeforeEach
    void setUp() {
        mockMailRequest = new MailRequest();
        mockMailRequest.getMessage().setSubject("Test Subject");
        mockMailRequest.getMessage().getBody().setContent("Test Body");
        mockMailRequest.getMessage().setToRecipients(Collections.singletonList(
                new MailRequest.RecipientModel(new EmailAddress(RECIPIENT_EMAIL, "Test User"))
        ));
    }

    // --- /api/mail/send Endpoint Tests ---

    @Test
    void sendMail_success_returnsAccepted() throws Exception {
        MailResponse successResponse = MailResponse.builder()
                .status("SUCCESS")
                .message("Batch send initiated.")
                .messageId("batch-id-456")
                .build();

        when(mailService.sendEmail(any(MailRequest.class))).thenReturn(successResponse);

        mockMvc.perform(post("/api/mail/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockMailRequest)))
                .andExpect(status().isAccepted()) // HTTP 202
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.messageId").value("batch-id-456"));
    }

    @Test
    void sendMail_failure_returnsInternalServerError() throws Exception {
        MailResponse failureResponse = MailResponse.builder()
                .status("FAILED")
                .message("Failed to send.")
                .messageId(null)
                .build();

        when(mailService.sendEmail(any(MailRequest.class))).thenReturn(failureResponse);

        mockMvc.perform(post("/api/mail/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockMailRequest)))
                .andExpect(status().isInternalServerError()) // HTTP 500
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    // --- /api/mail/status Endpoint Tests ---

    @Test
    void getMailStatus_found_returnsOk() throws Exception {
        MailResponse foundResponse = MailResponse.builder()
                .status("FOUND_IN_SENT_ITEMS")
                .message("Email found.")
                .messageId("msg-1")
                .build();

        when(mailService.getSentMailStatus(eq("Test Subject"), eq(RECIPIENT_EMAIL)))
                .thenReturn(foundResponse);

        mockMvc.perform(get("/api/mail/status")
                        .param("subject", "Test Subject")
                        .param("recipientEmail", RECIPIENT_EMAIL))
                .andExpect(status().isOk()) // HTTP 200
                .andExpect(jsonPath("$.status").value("FOUND_IN_SENT_ITEMS"));
    }

    @Test
    void getMailStatus_notFound_returnsNotFound() throws Exception {
        MailResponse notFoundResponse = MailResponse.builder()
                .status("NOT_FOUND_IN_SENT_ITEMS")
                .message("Email not found.")
                .messageId(null)
                .build();

        when(mailService.getSentMailStatus(eq("Test Subject"), eq(RECIPIENT_EMAIL)))
                .thenReturn(notFoundResponse);

        mockMvc.perform(get("/api/mail/status")
                        .param("subject", "Test Subject")
                        .param("recipientEmail", RECIPIENT_EMAIL))
                .andExpect(status().isNotFound()) // HTTP 404
                .andExpect(jsonPath("$.status").value("NOT_FOUND_IN_SENT_ITEMS"));
    }

    @Test
    void getMailStatus_failedCheck_returnsInternalServerError() throws Exception {
        MailResponse failedResponse = MailResponse.builder()
                .status("FAILED_STATUS_CHECK")
                .message("Error.")
                .messageId(null)
                .build();

        when(mailService.getSentMailStatus(any(), any())).thenReturn(failedResponse);

        mockMvc.perform(get("/api/mail/status")
                        .param("subject", "Test Subject")
                        .param("recipientEmail", RECIPIENT_EMAIL))
                .andExpect(status().isInternalServerError()) // HTTP 500
                .andExpect(jsonPath("$.status").value("FAILED_STATUS_CHECK"));
    }

    // --- /api/mail/track/{trackingId}.gif Endpoint Tests ---

    @Test
    void trackMailOpen_returnsTransparentGif() throws Exception {
        EmailOpenTracking mockTracking = new EmailOpenTracking(TEST_ID, 1, LocalDateTime.now(), LocalDateTime.now());

        // Mock the TrackingService call
        when(trackingService.trackOpen(eq(TEST_ID))).thenReturn(mockTracking);

        mockMvc.perform(get("/api/mail/track/{trackingId}.gif", TEST_ID))
                .andExpect(status().isOk()) // HTTP 200
                .andExpect(content().contentType("image/gif"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                // Corrected assertion: Content-Length is a Header property
                .andExpect(header().longValue("Content-Length", 43L));
    }

    // --- /api/mail/track/status/{trackingId} Endpoint Tests ---

    @Test
    void getTrackingStatus_found_returnsOk() throws Exception {
        EmailOpenTracking mockTracking = new EmailOpenTracking(TEST_ID, 5, LocalDateTime.now().minusDays(1), LocalDateTime.now());

        when(trackingService.getTrackingStatus(eq(TEST_ID))).thenReturn(mockTracking);

        mockMvc.perform(get("/api/mail/track/status/{trackingId}", TEST_ID))
                .andExpect(status().isOk()) // HTTP 200
                .andExpect(jsonPath("$.trackingId").value(TEST_ID))
                .andExpect(jsonPath("$.openCount").value(5));
    }

    @Test
    void getTrackingStatus_notFound_returnsNotFound() throws Exception {
        when(trackingService.getTrackingStatus(eq(TEST_ID))).thenReturn(null);

        mockMvc.perform(get("/api/mail/track/status/{trackingId}", TEST_ID))
                .andExpect(status().isNotFound()); // HTTP 404
    }

    // --- Other Endpoints ---
    @Test
    void hello_returnsHelloWorld() throws Exception {
        mockMvc.perform(get("/api/mail"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World"));
    }
}
