package com.edu.service;

import com.edu.model.EmailAddress;
import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.messages.MessagesRequestBuilder;
import com.microsoft.graph.users.item.sendmail.SendMailRequestBuilder;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
/**
 * Unit tests for the MailService class, including MSGraph, SMTP, and Fallback logic.
 */
class MailServiceTest {

    @Mock
    private GraphServiceClient graphServiceClient;

    @Mock
    private SmtpMailService smtpMailService; // Mock the new dependency

    @Mock
    private UserItemRequestBuilder userItemRequestBuilder;

    @Mock
    private SendMailRequestBuilder sendMailRequestBuilder;

    @Mock
    private MessagesRequestBuilder messagesRequestBuilder;

    @InjectMocks
    private MailService mailService;

    private final String SENDER_EMAIL = "sender@example.com";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Initializes mocks

        // Inject the @Value field for senderEmail
        ReflectionTestUtils.setField(mailService, "senderEmail", SENDER_EMAIL);

        // Define common mock behaviors for Graph chained calls (only setup needed)
        when(graphServiceClient.users()).thenReturn(mock(com.microsoft.graph.users.UsersRequestBuilder.class));
        when(graphServiceClient.users().byUserId(SENDER_EMAIL)).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.sendMail()).thenReturn(sendMailRequestBuilder);
        when(userItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);

        // Reset the behavior of core sending mocks before each test
        doNothing().when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class))).thenReturn(
                MailResponse.builder().status("SUCCESS").message("SMTP OK").messageId("N/A_SmtpSend").build());
    }

    private MailRequest createMailRequest(String protocol) {
        MailRequest mailRequest = new MailRequest();
        mailRequest.setSubject("Test Subject");
        mailRequest.setBodyContent("Test Body");
        mailRequest.setPreferredProtocol(protocol);
        mailRequest.setToRecipients(Arrays.asList(new EmailAddress("recipient@example.com", "Recipient Name")));
        return mailRequest;
    }

    private MailResponse createGraphSuccessResponse() {
        return MailResponse.builder().status("SUCCESS").message("Email send request accepted by Microsoft Graph.").messageId("N/A_GraphSend").build();
    }

    private MailResponse createGraphFailureResponse(String error) {
        return MailResponse.builder().status("FAILED").message("Failed to send email via MSGraph: " + error).messageId(null).build();
    }

    private MailResponse createSmtpSuccessResponse() {
        return MailResponse.builder().status("SUCCESS").message("Email sent successfully via SMTP.").messageId("N/A_SmtpSend").build();
    }

    private MailResponse createSmtpFailureResponse(String error) {
        return MailResponse.builder().status("FAILED").message("Failed to send email via SMTP: " + error).messageId(null).build();
    }


    /**
     * Test case 1: MSGRAPH is preferred and succeeds.
     */
    @Test
    void trySendEmail_preferredGraph_success() {
        // Given
        MailRequest request = createMailRequest("MSGRAPH");

        // When
        boolean isSuccess = mailService.trySendEmail(request);

        // Then
        assertTrue(isSuccess);
        // Verify only Graph attempt was made
        verify(sendMailRequestBuilder, times(1)).post(any(SendMailPostRequestBody.class));
        verify(smtpMailService, never()).sendSmtpEmail(any(MailRequest.class));
    }

    /**
     * Test case 2: SMTP is preferred and succeeds.
     */
    @Test
    void trySendEmail_preferredSmtp_success() {
        // Given
        MailRequest request = createMailRequest("SMTP");
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class))).thenReturn(createSmtpSuccessResponse());

        // When
        boolean isSuccess = mailService.trySendEmail(request);

        // Then
        assertTrue(isSuccess);
        // Verify only SMTP attempt was made
        verify(smtpMailService, times(1)).sendSmtpEmail(any(MailRequest.class));
        verify(sendMailRequestBuilder, never()).post(any(SendMailPostRequestBody.class));
    }

    /**
     * Test case 3: MSGRAPH fails, falls back to SMTP and succeeds.
     */
    @Test
    void trySendEmail_graphFails_fallbackSmtp_success() {
        // Given: MSGRAPH fails
        MailRequest request = createMailRequest("MSGRAPH");
        doThrow(new RuntimeException("Graph Error")).when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

        // When
        boolean isSuccess = mailService.trySendEmail(request);

        // Then
        assertTrue(isSuccess);
        // Verify both attempts were made
        verify(sendMailRequestBuilder, times(1)).post(any(SendMailPostRequestBody.class)); // Attempt 1 (Graph)
        verify(smtpMailService, times(1)).sendSmtpEmail(any(MailRequest.class));       // Attempt 2 (SMTP Fallback)
    }

    /**
     * Test case 4: SMTP fails, falls back to MSGRAPH and succeeds.
     */
    @Test
    void trySendEmail_smtpFails_fallbackGraph_success() {
        // Given: SMTP fails, Graph succeeds (default mock behavior)
        MailRequest request = createMailRequest("SMTP");
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class)))
                .thenReturn(createSmtpFailureResponse("SMTP Error"));

        // When
        boolean isSuccess = mailService.trySendEmail(request);

        // Then
        assertTrue(isSuccess);
        // Verify both attempts were made
        verify(smtpMailService, times(1)).sendSmtpEmail(any(MailRequest.class));       // Attempt 1 (SMTP)
        verify(sendMailRequestBuilder, times(1)).post(any(SendMailPostRequestBody.class)); // Attempt 2 (Graph Fallback)
    }

    /**
     * Test case 5: Both MSGRAPH and SMTP fail.
     */
    @Test
    void trySendEmail_bothFail_failure() {
        // Given: Both protocols fail (preferred MSGRAPH)
        MailRequest request = createMailRequest("MSGRAPH");
        doThrow(new RuntimeException("Graph Error")).when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class)))
                .thenReturn(createSmtpFailureResponse("SMTP Error"));

        // When
        boolean isSuccess = mailService.trySendEmail(request);
        MailResponse response = mailService.sendEmail(request); // Test the wrapper method

        // Then
        assertEquals(false, isSuccess);
        assertEquals("FAILED", response.getStatus());
        assertEquals("Failed to send email after attempting both MSGraph and SMTP protocols.", response.getMessage());

        // Verify both attempts were made exactly once
        verify(sendMailRequestBuilder, times(1)).post(any(SendMailPostRequestBody.class));
        verify(smtpMailService, times(1)).sendSmtpEmail(any(MailRequest.class));
    }

    /**
     * Test case 6: The original getSentMailStatus still works (for Graph-sent emails).
     */
    @Test
    void getSentMailStatus_found() {
        // Given
        String subject = "Found Email Subject";
        String recipientEmail = "found@example.com";

        Message mockMessage = new Message();
        mockMessage.setId("mockMessageId123");
        mockMessage.setSubject(subject);

        MessageCollectionResponse mockResponse = new MessageCollectionResponse();
        mockResponse.setValue(Collections.singletonList(mockMessage));

        when(messagesRequestBuilder.get(any(Consumer.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(mockResponse));

        // When
        MailResponse response = mailService.getSentMailStatus(subject, recipientEmail);

        // Then
        assertNotNull(response);
        assertEquals("FOUND_IN_SENT_ITEMS", response.getStatus());
        assertEquals("mockMessageId123", response.getMessageId());

        verify(messagesRequestBuilder, times(1)).get(any(Consumer.class));
    }
}