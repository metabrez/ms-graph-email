package com.edu.service;

import com.edu.model.EmailAddress;
import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.messages.MessagesRequestBuilder;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.graph.users.item.sendmail.SendMailRequestBuilder;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.graph.users.UsersRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the MailService, focusing on batch processing, protocol fallback,
 * and tracking pixel injection logic.
 */
@ExtendWith(MockitoExtension.class)
public class MailServiceTest {

    // Mocks for dependencies
    @Mock
    private GraphServiceClient graphServiceClient;
    @Mock
    private SmtpMailService smtpMailService;
    @Mock
    private EmailTrackingService emailTrackingService;

    // Mock for Graph SDK chain components
    @Mock
    private UsersRequestBuilder usersRequestBuilder;
    @Mock
    private UserItemRequestBuilder userItemRequestBuilder;
    @Mock
    private SendMailRequestBuilder sendMailRequestBuilder;
    @Mock
    private MessagesRequestBuilder messagesRequestBuilder;

    // Service under test
    @InjectMocks
    private MailService mailService;

    // Constants for setting @Value fields
    private static final String SENDER_EMAIL = "sender@example.com";
    private static final String TRACKING_BASE_URL = "https://track.example.com";

    @BeforeEach
    void setUp() {
        // Use ReflectionTestUtils to set the @Value fields
        ReflectionTestUtils.setField(mailService, "senderEmail", SENDER_EMAIL);
        ReflectionTestUtils.setField(mailService, "trackingBaseUrl", TRACKING_BASE_URL);

        // Removed unnecessary general Graph client stubbing to prevent UnnecessaryStubbingException
        // when the SUT returns early (like in the no-recipients test).
    }

    // --- Utility Methods for MailRequest Creation ---

    private MailRequest.RecipientModel createRecipient(String email, String name) {
        return new MailRequest.RecipientModel(new EmailAddress(email, name));
    }

    private MailRequest createMailRequest(List<MailRequest.RecipientModel> toRecipients, String protocol, boolean tracking) {
        MailRequest request = new MailRequest();
        request.setPreferredProtocol(protocol);
        request.setRequestPixelTracking(tracking);
        request.getMessage().setSubject("Test Subject");
        request.getMessage().getBody().setContent("Test Body Content");
        request.getMessage().setToRecipients(toRecipients);
        request.getMessage().getBody().setContentType("Html");
        return request;
    }

    // --- Test Cases for sendEmail (Batch Logic) ---

    @Test
    void sendEmail_noRecipients_returnsFailedResponse() {
        MailRequest request = createMailRequest(Collections.emptyList(), "MSGRAPH", false);

        MailResponse response = mailService.sendEmail(request);

        assertEquals("FAILED", response.getStatus());
        assertTrue(response.getMessage().contains("No recipients found"));
        assertNull(response.getMessageId());
        verifyNoInteractions(emailTrackingService, smtpMailService);
        // We verify that the mock chain was never started
        verify(graphServiceClient, never()).users();
    }

    @Test
    void sendEmail_singleRecipient_success() {
        MailRequest.RecipientModel recipient = createRecipient("test1@example.com", "Test One");
        MailRequest request = createMailRequest(Arrays.asList(recipient), "MSGRAPH", false);

        // Mock trySendEmail to return success for the single recipient
        mockGraphSendSuccess();
        // Since the main logic calls trySendEmail, we set the underlying send method to succeed.

        MailResponse response = mailService.sendEmail(request);

        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getMessage().contains("Batch send initiated"));
        assertNotNull(response.getMessageId()); // Should contain the Group ID
        verify(userItemRequestBuilder, times(1)).sendMail();
        verify(emailTrackingService, times(1)).saveSentEmail(
                anyString(), eq("test1@example.com"), eq(response.getMessageId()), any()
        );
    }

    @Test
    void sendEmail_multipleRecipients_allSuccess_returnsSuccess() {
        MailRequest.RecipientModel r1 = createRecipient("r1@example.com", "R1");
        MailRequest.RecipientModel r2 = createRecipient("r2@example.com", "R2");
        MailRequest request = createMailRequest(Arrays.asList(r1, r2), "MSGRAPH", false);

        // Mock trySendEmail success for both
        mockGraphSendSuccess();

        MailResponse response = mailService.sendEmail(request);

        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getMessageId());
        // Verify underlying send is called twice
        verify(userItemRequestBuilder, times(2)).sendMail();
        // Verify tracking is saved twice
        verify(emailTrackingService, times(2)).saveSentEmail(
                anyString(), anyString(), eq(response.getMessageId()), any()
        );
    }

    @Test
    void sendEmail_multipleRecipients_oneFails_oneSucceeds_returnsSuccess() {
        MailRequest.RecipientModel r1 = createRecipient("success@example.com", "R1");
        MailRequest.RecipientModel r2 = createRecipient("fail@example.com", "R2");
        MailRequest request = createMailRequest(Arrays.asList(r1, r2), "MSGRAPH", false);

        // Set up responses for the two individual calls:
        // 1. Success recipient: MSGRAPH success
        // 2. Fail recipient: MSGRAPH fail, SMTP fail
        when(graphServiceClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.sendMail()).thenReturn(sendMailRequestBuilder);
        doNothing() // First call (for r1)
                .doThrow(new RuntimeException("Graph Error for R2")) // Second call (for r2)
                .when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class))).thenReturn(
                MailResponse.builder().status("FAILED").message("SMTP fail").build()
        ); // The fallback attempt for r2 fails too

        MailResponse response = mailService.sendEmail(request);

        assertEquals("SUCCESS", response.getStatus());
        // Verify tracking is saved once (only for the successful recipient)
        verify(emailTrackingService, times(1)).saveSentEmail(
                anyString(), eq("success@example.com"), eq(response.getMessageId()), any()
        );
    }

    @Test
    void sendEmail_allFail_returnsFailed() {
        MailRequest.RecipientModel r1 = createRecipient("r1@example.com", "R1");
        MailRequest request = createMailRequest(Arrays.asList(r1), "MSGRAPH", false);

        // Set up the full mock chain for the failure scenario
        when(graphServiceClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.sendMail()).thenReturn(sendMailRequestBuilder);

        // Mock both protocols to fail for the single recipient
        doThrow(new RuntimeException("Graph Error")).when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class))).thenReturn(
                MailResponse.builder().status("FAILED").message("SMTP fail").build()
        );

        MailResponse response = mailService.sendEmail(request);

        assertEquals("FAILED", response.getStatus());
        assertNull(response.getMessageId());
        verifyNoInteractions(emailTrackingService); // No save on failure
    }

    @Test
    void sendEmail_withPixelTracking_injectsPixelAndSucceeds() {
        MailRequest.RecipientModel recipient = createRecipient("tracker@example.com", "Tracker");
        MailRequest request = createMailRequest(Arrays.asList(recipient), "SMTP", true); // Use SMTP protocol

        // Mock SMTP success (first attempt)
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class))).thenAnswer(invocation -> {
            MailRequest mailRequestCopy = invocation.getArgument(0);

            // CRITICAL FIX: Get the tracking ID from the argument AFTER MailService has set
            // it.
            String trackingID = mailRequestCopy.getTrackingID();
            String content = mailRequestCopy.getMessage().getBody().getContent();

            // CRITICAL ASSERTION 1: Check if the tracking ID is present
            assertTrue(content.contains(trackingID), "Content must contain the generated tracking ID.");

            // CRITICAL FIX 2: Check for the injected pixel URL fragment
            // (http://domain/api/mail/track/ID)
            // The service converts the HTTPS base URL to HTTP when injecting the pixel.
            String expectedPixelUrlFragment = "http://track.example.com/api/mail/track/" + trackingID;
            assertTrue(content.contains(expectedPixelUrlFragment),
                    "Pixel URL must be correctly formed and injected using HTTP protocol.");

            // CRITICAL ASSERTION 3: Should contain the original content
            assertTrue(content.contains("Test Body Content"), "Content must retain the original body content.");

            return MailResponse.builder().status("SUCCESS").message("Sent").messageId(trackingID).build();
        });

        MailResponse response = mailService.sendEmail(request);

        assertEquals("SUCCESS", response.getStatus());
        verify(smtpMailService, times(1)).sendSmtpEmail(any(MailRequest.class));
        verify(emailTrackingService, times(1)).saveSentEmail(
                anyString(), eq("tracker@example.com"), eq(response.getMessageId()), any());
    }

    @Test
    void sendEmail_withPixelTracking_baseHttpsForcesHttpInPixel() {
        // Set up the tracking URL to use HTTPS to test the fix in injectTrackingPixel
        ReflectionTestUtils.setField(mailService, "trackingBaseUrl", "https://ngrok.io");

        MailRequest.RecipientModel recipient = createRecipient("https@example.com", "Https User");
        MailRequest request = createMailRequest(Arrays.asList(recipient), "SMTP", true);

        when(smtpMailService.sendSmtpEmail(any(MailRequest.class))).thenAnswer(invocation -> {
            MailRequest mailRequestCopy = invocation.getArgument(0);
            String content = mailRequestCopy.getMessage().getBody().getContent();
            // CRITICAL ASSERTION: Should use HTTP in the pixel source
            assertTrue(content.contains("http://ngrok.io/api/mail/track/"));
            return MailResponse.builder().status("SUCCESS").message("Sent").build();
        });

        mailService.sendEmail(request);
    }


    // --- Test Cases for trySendEmail (Fallback/Retry Logic) ---

    @Test
    void trySendEmail_preferredGraph_success_noFallback() {
        MailRequest request = createMailRequest(Arrays.asList(createRecipient("r1@ex.com", "R1")), "MSGRAPH", false);
        request.setTrackingID("unique-id");

        mockGraphSendSuccess(); // Mock Graph success

        boolean result = mailService.trySendEmail(request, "r1@ex.com", "batch-id");

        assertTrue(result);
        verify(userItemRequestBuilder, times(1)).sendMail(); // Only graph called
        verify(smtpMailService, never()).sendSmtpEmail(any()); // SMTP not called
        verify(emailTrackingService, times(1)).saveSentEmail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void trySendEmail_preferredGraph_fails_fallbackSmtp_success() {
        MailRequest request = createMailRequest(Arrays.asList(createRecipient("r1@ex.com", "R1")), "MSGRAPH", false);
        request.setTrackingID("unique-id");

        // Set up the mock chain for the Graph failure scenario
        when(graphServiceClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.sendMail()).thenReturn(sendMailRequestBuilder);

        // Attempt 1 (Graph) fails
        doThrow(new RuntimeException("Graph Failed")).when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));
        // Attempt 2 (SMTP) succeeds
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class)))
                .thenReturn(MailResponse.builder().status("SUCCESS").message("Sent via SMTP").build());

        boolean result = mailService.trySendEmail(request, "r1@ex.com", "batch-id");

        assertTrue(result);
        verify(userItemRequestBuilder, times(1)).sendMail(); // Graph called once
        verify(smtpMailService, times(1)).sendSmtpEmail(any()); // SMTP called once (fallback)
        verify(emailTrackingService, times(1)).saveSentEmail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void trySendEmail_preferredSmtp_fails_fallbackGraph_success() {
        MailRequest request = createMailRequest(Arrays.asList(createRecipient("r1@ex.com", "R1")), "SMTP", false);
        request.setTrackingID("unique-id");

        // Attempt 1 (SMTP) fails
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class)))
                .thenReturn(MailResponse.builder().status("FAILED").message("SMTP fail").build());
        // Attempt 2 (Graph) succeeds
        mockGraphSendSuccess();

        boolean result = mailService.trySendEmail(request, "r1@ex.com", "batch-id");

        assertTrue(result);
        verify(userItemRequestBuilder, times(1)).sendMail(); // Graph called once (fallback)
        verify(smtpMailService, times(1)).sendSmtpEmail(any()); // SMTP called once
        verify(emailTrackingService, times(1)).saveSentEmail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void trySendEmail_bothProtocolsFail_returnsFalse() {
        MailRequest request = createMailRequest(Arrays.asList(createRecipient("r1@ex.com", "R1")), "MSGRAPH", false);

        // Set up the mock chain for the Graph failure scenario
        when(graphServiceClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.sendMail()).thenReturn(sendMailRequestBuilder);

        // Attempt 1 (Graph) fails
        doThrow(new RuntimeException("Graph Failed")).when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));
        // Attempt 2 (SMTP) fails
        when(smtpMailService.sendSmtpEmail(any(MailRequest.class)))
                .thenReturn(MailResponse.builder().status("FAILED").message("SMTP fail").build());

        boolean result = mailService.trySendEmail(request, "r1@ex.com", "batch-id");

        assertFalse(result);
        verify(userItemRequestBuilder, times(1)).sendMail(); // Graph called once
        verify(smtpMailService, times(1)).sendSmtpEmail(any()); // SMTP called once
        verifyNoInteractions(emailTrackingService); // Tracking service should not be called
    }

    // --- Test Cases for getSentMailStatus ---

   /* @Test
    void getSentMailStatus_emailFound_returnsFound() throws Exception {
        // Mock the Graph SDK chain for the status check
        when(userItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);

        // Use a real MessageCollectionResponse or a mock, here using a mock for simplicity
        MessageCollectionResponse mockResponse = mock(MessageCollectionResponse.class);
        when(messagesRequestBuilder.get(any(Consumer.class))).thenReturn(mockResponse);

        // Mock the message list to be non-empty and contain a message ID
        com.microsoft.graph.models.Message foundMessage = mock(com.microsoft.graph.models.Message.class);
        when(foundMessage.getId()).thenReturn("sent-message-id");
        when(mockResponse.getValue()).thenReturn(Collections.singletonList(foundMessage));


        MailResponse response = mailService.getSentMailStatus("Test Subject", "recipient@ex.com");

        assertEquals("FOUND_IN_SENT_ITEMS", response.getStatus());
        assertEquals("sent-message-id", response.getMessageId());

        // Verify the filter logic is set up correctly in the request configuration
        verify(messagesRequestBuilder).get(argThat(config -> {
            RequestConfiguration<MessagesRequestBuilderGetQueryParameters> requestConfig = (RequestConfiguration<MessagesRequestBuilderGetQueryParameters>) config;
            String filter = requestConfig.queryParameters.filter;
            return filter.contains("subject eq 'Test Subject'") && filter.contains("address eq 'recipient@ex.com'");
        }));
    }*/

    @Test
    void getSentMailStatus_emailNotFound_returnsNotFound() throws Exception {
        // FIX: Mock the Graph SDK chain for the status check to prevent NullPointerException
        when(graphServiceClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);

        MessageCollectionResponse mockResponse = mock(MessageCollectionResponse.class);
        when(messagesRequestBuilder.get(any(Consumer.class))).thenReturn(mockResponse);

        // Mock an empty message list
        when(mockResponse.getValue()).thenReturn(Collections.emptyList());

        MailResponse response = mailService.getSentMailStatus("Non-Existent Subject", "no-such-recipient@ex.com");

        assertEquals("NOT_FOUND_IN_SENT_ITEMS", response.getStatus());
        assertNull(response.getMessageId());
    }

    @Test
    void getSentMailStatus_exceptionThrown_returnsFailedStatusCheck() throws Exception {
        // Mock the Graph SDK chain for the status check
        when(graphServiceClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);

        // Mock the Graph SDK chain to throw an exception during the GET request
        when(messagesRequestBuilder.get(any(Consumer.class))).thenThrow(new RuntimeException("Graph API Error"));

        MailResponse response = mailService.getSentMailStatus("Any Subject", "any@recipient.com");

        assertEquals("FAILED_STATUS_CHECK", response.getStatus());
        assertTrue(response.getMessage().contains("Graph API Error"));
    }


    // --- Mock Setup Helpers ---

    private void mockGraphSendSuccess() {
        when(graphServiceClient.users()).thenReturn(usersRequestBuilder);
        when(usersRequestBuilder.byUserId(anyString())).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.sendMail()).thenReturn(sendMailRequestBuilder);
        // Mock the void post method to do nothing (simulate success)
        doNothing().when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));
    }
}
