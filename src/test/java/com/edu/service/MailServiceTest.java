package com.edu.service;

import com.edu.model.EmailAddress;
import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.item.messages.MessagesRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the MailService class, focusing on the trySendEmail failover logic.
 */
class MailServiceTest {

    @Mock
    private GraphServiceClient graphServiceClient;

    @Mock
    private SmtpMailService smtpMailService;

    // Mocks for Graph status check
    @Mock
    private UserItemRequestBuilder userItemRequestBuilder;
    @Mock
    private MessagesRequestBuilder messagesRequestBuilder;

    @InjectMocks
    private MailService mailServiceSpy; // Use a spy to mock private methods

    private final String SENDER_EMAIL = "sender@example.com";
    private MailRequest successfulMailRequest; // Changed to be initialized in setup

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mailServiceSpy = spy(new MailService(graphServiceClient, smtpMailService));
        ReflectionTestUtils.setField(mailServiceSpy, "senderEmail", SENDER_EMAIL);

        // --- Setup common test request (FIXED for nested structure) ---

        MailRequest request = new MailRequest();

        // 1. Create Body Model
        MailRequest.BodyModel body = new MailRequest.BodyModel();
        body.setContentType("Text");
        body.setContent("Test Body Content");

        // 2. Create Recipient Model
        List<MailRequest.RecipientModel> toRecipients = Arrays.asList(
                new MailRequest.RecipientModel(new EmailAddress("recipient@example.com", "Recipient Name"))
        );

        // 3. Create Message Model
        MailRequest.MessageModel message = new MailRequest.MessageModel();
        message.setSubject("Test Subject");
        message.setBody(body);
        message.setToRecipients(toRecipients);
        message.setCcRecipients(Arrays.asList());
        message.setBccRecipients(Arrays.asList());

        // 4. Set Message and top-level fields
        request.setMessage(message);
        request.setSaveToSentItems(false);
        request.setTrackingID("test-tracking-id");
        request.setRequestPixelTracking(false); // Default to false for these tests

        // Initialize the class field
        successfulMailRequest = request;

        // --- Setup Mocks for Graph Status Check ---
        when(graphServiceClient.users()).thenReturn(mock(com.microsoft.graph.users.UsersRequestBuilder.class));
        when(graphServiceClient.users().byUserId(SENDER_EMAIL)).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);
    }

    // --- Mock Response Objects ---

    private MailResponse getSuccessResponse(String protocol) {
        return MailResponse.builder().status("SUCCESS").message("Success via " + protocol).messageId("ID_" + protocol).build();
    }

    private MailResponse getFailureResponse(String protocol) {
        return MailResponse.builder().status("FAILED").message("Failure via " + protocol).messageId(null).build();
    }

    // --- trySendEmail Tests ---

    /**
     * Scenario 1: Preferred protocol (MSGRAPH) succeeds immediately.
     */
    @Test
    void trySendEmail_MSGraphPreferred_Success() throws Exception {
        // Given
        successfulMailRequest.setPreferredProtocol("MSGRAPH");
        doReturn(getSuccessResponse("MSGRAPH")).when(mailServiceSpy).sendEmailViaGraph(any(MailRequest.class));
        doReturn(getFailureResponse("SMTP")).when(mailServiceSpy).sendEmailViaSmtp(any(MailRequest.class)); // Should not be called

        // When
        boolean result = mailServiceSpy.trySendEmail(successfulMailRequest);

        // Then
        assertEquals(true, result, "Should succeed on first attempt (MSGRAPH)");
        verify(mailServiceSpy, times(1)).sendEmailViaGraph(any(MailRequest.class));
        verify(mailServiceSpy, never()).sendEmailViaSmtp(any(MailRequest.class));
    }

    /**
     * Scenario 2: Preferred protocol (MSGRAPH) fails, fallback to SMTP succeeds.
     */
    @Test
    void trySendEmail_MSGraphPreferred_FailoverToSMTP_Success() throws Exception {
        // Given
        successfulMailRequest.setPreferredProtocol("MSGRAPH");
        doReturn(getFailureResponse("MSGRAPH")).when(mailServiceSpy).sendEmailViaGraph(any(MailRequest.class));
        doReturn(getSuccessResponse("SMTP")).when(mailServiceSpy).sendEmailViaSmtp(any(MailRequest.class));

        // When
        boolean result = mailServiceSpy.trySendEmail(successfulMailRequest);

        // Then
        assertEquals(true, result, "Should succeed on fallback attempt (SMTP)");
        verify(mailServiceSpy, times(1)).sendEmailViaGraph(any(MailRequest.class));
        verify(mailServiceSpy, times(1)).sendEmailViaSmtp(any(MailRequest.class));
    }

    /**
     * Scenario 3: Preferred protocol (MSGRAPH) fails, and fallback (SMTP) fails.
     */
    @Test
    void trySendEmail_MSGraphPreferred_TotalFailure() throws Exception {
        // Given
        successfulMailRequest.setPreferredProtocol("MSGRAPH");
        doReturn(getFailureResponse("MSGRAPH")).when(mailServiceSpy).sendEmailViaGraph(any(MailRequest.class));
        doReturn(getFailureResponse("SMTP")).when(mailServiceSpy).sendEmailViaSmtp(any(MailRequest.class));

        // When
        boolean result = mailServiceSpy.trySendEmail(successfulMailRequest);

        // Then
        assertEquals(false, result, "Should fail after both attempts");
        verify(mailServiceSpy, times(1)).sendEmailViaGraph(any(MailRequest.class));
        verify(mailServiceSpy, times(1)).sendEmailViaSmtp(any(MailRequest.class));
    }

    /**
     * Scenario 4: Preferred protocol (SMTP) succeeds immediately.
     */
    @Test
    void trySendEmail_SMTPPreferred_Success() throws Exception {
        // Given
        successfulMailRequest.setPreferredProtocol("SMTP");
        doReturn(getSuccessResponse("SMTP")).when(mailServiceSpy).sendEmailViaSmtp(any(MailRequest.class));
        doReturn(getFailureResponse("MSGRAPH")).when(mailServiceSpy).sendEmailViaGraph(any(MailRequest.class)); // Should not be called

        // When
        boolean result = mailServiceSpy.trySendEmail(successfulMailRequest);

        // Then
        assertEquals(true, result, "Should succeed on first attempt (SMTP)");
        verify(mailServiceSpy, times(1)).sendEmailViaSmtp(any(MailRequest.class));
        verify(mailServiceSpy, never()).sendEmailViaGraph(any(MailRequest.class));
    }

    /**
     * Scenario 5: Preferred protocol (SMTP) fails, fallback to MSGRAPH succeeds.
     */
    @Test
    void trySendEmail_SMTPPreferred_FailoverToMSGraph_Success() throws Exception {
        // Given
        successfulMailRequest.setPreferredProtocol("SMTP");
        doReturn(getFailureResponse("SMTP")).when(mailServiceSpy).sendEmailViaSmtp(any(MailRequest.class));
        doReturn(getSuccessResponse("MSGRAPH")).when(mailServiceSpy).sendEmailViaGraph(any(MailRequest.class));

        // When
        boolean result = mailServiceSpy.trySendEmail(successfulMailRequest);

        // Then
        assertEquals(true, result, "Should succeed on fallback attempt (MSGRAPH)");
        verify(mailServiceSpy, times(1)).sendEmailViaSmtp(any(MailRequest.class));
        verify(mailServiceSpy, times(1)).sendEmailViaGraph(any(MailRequest.class));
    }

    /**
     * Scenario 6: Preferred protocol (SMTP) fails, and fallback (MSGRAPH) fails.
     */
    @Test
    void trySendEmail_SMTPPreferred_TotalFailure() throws Exception {
        // Given
        successfulMailRequest.setPreferredProtocol("SMTP");
        doReturn(getFailureResponse("SMTP")).when(mailServiceSpy).sendEmailViaSmtp(any(MailRequest.class));
        doReturn(getFailureResponse("MSGRAPH")).when(mailServiceSpy).sendEmailViaGraph(any(MailRequest.class));

        // When
        boolean result = mailServiceSpy.trySendEmail(successfulMailRequest);

        // Then
        assertEquals(false, result, "Should fail after both attempts");
        verify(mailServiceSpy, times(1)).sendEmailViaSmtp(any(MailRequest.class));
        verify(mailServiceSpy, times(1)).sendEmailViaGraph(any(MailRequest.class));
    }

    /**
     * Scenario 7: Unknown preferred protocol defaults to MSGRAPH and succeeds.
     */
    @Test
    void trySendEmail_UnknownProtocol_DefaultsToMSGraph_Success() throws Exception {
        // Given
        successfulMailRequest.setPreferredProtocol("UNKNOWN");
        doReturn(getSuccessResponse("MSGRAPH")).when(mailServiceSpy).sendEmailViaGraph(any(MailRequest.class));
        doReturn(getFailureResponse("SMTP")).when(mailServiceSpy).sendEmailViaSmtp(any(MailRequest.class));

        // When
        boolean result = mailServiceSpy.trySendEmail(successfulMailRequest);

        // Then
        assertEquals(true, result, "Should default to MSGRAPH and succeed");
        // Verify MSGraph was called, SMTP was not
        verify(mailServiceSpy, times(1)).sendEmailViaGraph(any(MailRequest.class));
        verify(mailServiceSpy, never()).sendEmailViaSmtp(any(MailRequest.class));
    }

    // --- MailResponse sendEmail Tests (End-to-end integration of trySendEmail) ---

    @Test
    void sendEmail_SuccessReturnsSuccessResponse() throws Exception {
        // Given: Mock trySendEmail to return true
        successfulMailRequest.setPreferredProtocol("MSGRAPH");
        doReturn(true).when(mailServiceSpy).trySendEmail(any(MailRequest.class));

        // When
        MailResponse response = mailServiceSpy.sendEmail(successfulMailRequest);

        // Then
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Email sent successfully using preferred or fallback protocol.", response.getMessage());
        // Note: uniqueId is generated inside sendEmail, so we verify structure/status
    }

    @Test
    void sendEmail_FailureReturnsFailedResponse() throws Exception {
        // Given: Mock trySendEmail to return false
        successfulMailRequest.setPreferredProtocol("MSGRAPH");
        doReturn(false).when(mailServiceSpy).trySendEmail(any(MailRequest.class));

        // When
        MailResponse response = mailServiceSpy.sendEmail(successfulMailRequest);

        // Then
        assertEquals("FAILED", response.getStatus());
        assertEquals("Failed to send email after attempting both MSGraph and SMTP protocols.", response.getMessage());
    }


    // --- getSentMailStatus Tests (Existing logic verification) ---

    @Test
    void getSentMailStatus_found() throws Exception {
        // Given
        String subject = "Found Email Subject";
        String recipientEmail = "found@example.com";

        // Create mock Message object and response
        Message mockMessage = new Message();
        mockMessage.setId("mockMessageId123");
        MessageCollectionResponse mockResponse = new MessageCollectionResponse();
        mockResponse.setValue(Collections.singletonList(mockMessage));

        // Mock the get() method of messagesRequestBuilder
        when(messagesRequestBuilder.get(any(Consumer.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(mockResponse));

        // When
        MailResponse response = mailServiceSpy.getSentMailStatus(subject, recipientEmail);

        // Then
        assertEquals("FOUND_IN_SENT_ITEMS", response.getStatus());
        assertEquals("mockMessageId123", response.getMessageId());
    }

    @Test
    void getSentMailStatus_notFound() throws Exception {
        // Given
        String subject = "Not Found Email Subject";
        String recipientEmail = "notfound@example.com";

        // Create an empty mock MessageCollectionResponse
        MessageCollectionResponse mockResponse = new MessageCollectionResponse();
        mockResponse.setValue(Collections.emptyList());

        // Mock the get() method of messagesRequestBuilder
        when(messagesRequestBuilder.get(any(Consumer.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(mockResponse));

        // When
        MailResponse response = mailServiceSpy.getSentMailStatus(subject, recipientEmail);

        // Then
        assertEquals("NOT_FOUND_IN_SENT_ITEMS", response.getStatus());
    }

    @Test
    void getSentMailStatus_failure() throws Exception {
        // Given
        String subject = "Error Email";
        String recipientEmail = "error@example.com";

        // Mock the get() method to throw an exception
        when(messagesRequestBuilder.get(any(Consumer.class)))
                .thenThrow(new RuntimeException("Graph API Error"));

        // When
        MailResponse response = mailServiceSpy.getSentMailStatus(subject, recipientEmail);

        // Then
        assertEquals("FAILED_STATUS_CHECK", response.getStatus());
    }
}
