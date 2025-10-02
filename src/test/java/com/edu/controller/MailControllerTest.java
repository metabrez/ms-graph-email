package com.edu.controller;

import com.edu.model.EmailAddress;
import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import com.edu.service.MailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;




@WebMvcTest(MailController.class) // Focuses on testing MailController
class MailControllerTest {

    @Autowired
    private MockMvc mockMvc; // Used to simulate HTTP requests

    @Autowired
    private ObjectMapper objectMapper; // Used to convert objects to JSON and vice versa

    @MockBean
    private MailService mailService; // Mocks the MailService dependency

    private MailRequest createMailRequest(String protocol) {
        MailRequest mailRequest = new MailRequest();
        mailRequest.setSubject("Test Subject");
        mailRequest.setBodyContent("Test Body");
        mailRequest.setPreferredProtocol(protocol);
        mailRequest.setToRecipients(Arrays.asList(new EmailAddress("recipient@example.com", "Recipient Name")));
        return mailRequest;
    }

    /**
     * Tests the POST /api/mail/send endpoint for a successful email send
     * (regardless of which protocol succeeded via trySendEmail).
     */
    @Test
    void sendMail_success() throws Exception {
        // Given
        MailRequest mailRequest = createMailRequest("MSGRAPH");

        // Mock MailService to return a final SUCCESS response
        MailResponse mockResponse = MailResponse.builder()
                .messageId("N/A_RobustSend")
                .status("SUCCESS")
                .message("Email sent successfully using preferred or fallback protocol.")
                .build();

        // Mock the MailService behavior
        when(mailService.sendEmail(any(MailRequest.class))).thenReturn(mockResponse);

        // When & Then
        MockHttpServletRequestBuilder requestBuilder = post("/api/mail/send")
                .content(objectMapper.writeValueAsString(mailRequest))
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(requestBuilder)
                .andExpect(status().isAccepted()) // Expect 202 Accepted
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Email sent successfully using preferred or fallback protocol."))
                .andExpect(jsonPath("$.messageId").value("N/A_RobustSend"));
    }

    /**
     * Tests the POST /api/mail/send endpoint for a failed email send
     * (meaning both protocols failed via trySendEmail).
     */
    @Test
    void sendMail_failure() throws Exception {
        // Given
        MailRequest mailRequest = createMailRequest("MSGRAPH");

        // Mock MailService to return a final FAILED response
        MailResponse mockResponse = MailResponse.builder()
                .status("FAILED")
                .message("Failed to send email after attempting both MSGraph and SMTP protocols.")
                .messageId(null)
                .build();

        // Mock the MailService behavior to return a failed response
        when(mailService.sendEmail(any(MailRequest.class))).thenReturn(mockResponse);

        // When & Then
        MockHttpServletRequestBuilder requestBuilder = post("/api/mail/send")
                .content(objectMapper.writeValueAsString(mailRequest))
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(requestBuilder)
                .andExpect(status().isInternalServerError()) // Expect 500 Internal Server Error
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Failed to send email after attempting both MSGraph and SMTP protocols."))
                .andExpect(jsonPath("$.messageId").doesNotExist());
    }

    /**
     * Tests the GET /api/mail/status endpoint when the email is found. (No change needed here)
     */
    @Test
    void getMailStatus_found() throws Exception {
        // Given
        String subject = "Found Email";
        String recipient = "found@example.com";
        MailResponse mockResponse = new MailResponse("msgId123", "FOUND_IN_SENT_ITEMS", "Email found.");

        // Mock the MailService behavior
        when(mailService.getSentMailStatus(subject, recipient)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/mail/status")
                        .param("subject", subject)
                        .param("recipientEmail", recipient)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(jsonPath("$.status").value("FOUND_IN_SENT_ITEMS"))
                .andExpect(jsonPath("$.message").value("Email found."))
                .andExpect(jsonPath("$.messageId").value("msgId123"));
    }

    /**
     * Tests the GET /api/mail/status endpoint when the email is not found. (No change needed here)
     */
    @Test
    void getMailStatus_notFound() throws Exception {
        // Given
        String subject = "Not Found Email";
        String recipient = "notfound@example.com";
        MailResponse mockResponse = new MailResponse(null, "NOT_FOUND_IN_SENT_ITEMS", "Email not found.");

        // Mock the MailService behavior
        when(mailService.getSentMailStatus(subject, recipient)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/mail/status")
                        .param("subject", subject)
                        .param("recipientEmail", recipient)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()) // Expect 404 Not Found
                .andExpect(jsonPath("$.status").value("NOT_FOUND_IN_SENT_ITEMS"))
                .andExpect(jsonPath("$.message").value("Email not found."))
                .andExpect(jsonPath("$.messageId").doesNotExist());
    }

    /**
     * Tests the GET /api/mail/status endpoint for a failed status check. (No change needed here)
     */
    @Test
    void getMailStatus_failure() throws Exception {
        // Given
        String subject = "Error Email";
        String recipient = "error@example.com";
        MailResponse mockResponse = new MailResponse(null, "FAILED_STATUS_CHECK", "Failed to check status: Error");

        // Mock the MailService behavior to return a failed response
        when(mailService.getSentMailStatus(subject, recipient)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/mail/status")
                        .param("subject", subject)
                        .param("recipientEmail", recipient)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError()) // Expect 500 Internal Server Error
                .andExpect(jsonPath("$.status").value("FAILED_STATUS_CHECK"))
                .andExpect(jsonPath("$.message").value("Failed to check status: Error"))
                .andExpect(jsonPath("$.messageId").doesNotExist());
    }
}
