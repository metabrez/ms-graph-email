// src/test/java/com/edu/service/MailServiceTest.java
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
/**
 * Unit tests for the MailService class.
 * Mocks the GraphServiceClient and its chained methods to isolate MailService logic.
 */
class MailServiceTest {

    @Mock
    private GraphServiceClient graphServiceClient;

    @Mock
    private UserItemRequestBuilder userItemRequestBuilder; // Mocks graphServiceClient.users().byUserId(senderEmail)

    @Mock
    private SendMailRequestBuilder sendMailRequestBuilder; // Mocks .sendMail()

    @Mock
    private MessagesRequestBuilder messagesRequestBuilder; // Mocks .messages()

    @InjectMocks
    private MailService mailService;

    private final String SENDER_EMAIL = "sender@example.com";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Initializes mocks

        // Inject the @Value field for senderEmail
        ReflectionTestUtils.setField(mailService, "senderEmail", SENDER_EMAIL);

        // Define common mock behaviors for chained calls
        when(graphServiceClient.users()).thenReturn(mock(com.microsoft.graph.users.UsersRequestBuilder.class));
        when(graphServiceClient.users().byUserId(SENDER_EMAIL)).thenReturn(userItemRequestBuilder);
        when(userItemRequestBuilder.sendMail()).thenReturn(sendMailRequestBuilder);
        when(userItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);
    }

    /**
     * Tests the sendEmail method for a successful email sending scenario.
     */
    @Test
    void sendEmail_success() {
        // Given
        MailRequest mailRequest = new MailRequest();
        mailRequest.setSubject("Test Subject");
        mailRequest.setBodyContent("Test Body");
        mailRequest.setToRecipients(Arrays.asList(new EmailAddress("recipient@example.com", "Recipient Name")));

        // Mock the post() method of sendMailRequestBuilder to do nothing (simulate success)
        doNothing().when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

        // When
        MailResponse response = mailService.sendEmail(mailRequest);

        // Then
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Email send request accepted by Microsoft Graph. It should appear in Sent Items shortly.", response.getMessage());
        assertEquals("N/A_DirectSend", response.getMessageId());

        // Verify that the sendMail().post() method was called exactly once with any SendMailPostRequestBody
        verify(sendMailRequestBuilder, times(1)).post(any(SendMailPostRequestBody.class));
    }

    /**
     * Tests the sendEmail method for a failed email sending scenario (e.g., Graph API error).
     */
    @Test
    void sendEmail_failure() {
        // Given
        MailRequest mailRequest = new MailRequest();
        mailRequest.setSubject("Test Subject");
        mailRequest.setBodyContent("Test Body");
        mailRequest.setToRecipients(Arrays.asList(new EmailAddress("recipient@example.com", "Recipient Name")));

        // Mock the post() method to throw an exception
        doThrow(new RuntimeException("Graph API Error")).when(sendMailRequestBuilder).post(any(SendMailPostRequestBody.class));

        // When
        MailResponse response = mailService.sendEmail(mailRequest);

        // Then
        assertNotNull(response);
        assertEquals("FAILED", response.getStatus());
        assertEquals("Failed to send email: Graph API Error", response.getMessage());
        assertEquals(null, response.getMessageId());

        // Verify that the sendMail().post() method was still attempted
        verify(sendMailRequestBuilder, times(1)).post(any(SendMailPostRequestBody.class));
    }

    /**
     * Tests the getSentMailStatus method when an email is found in Sent Items.
     */
    @Test
    void getSentMailStatus_found() {
        // Given
        String subject = "Found Email Subject";
        String recipientEmail = "found@example.com";

        // Create a mock Message object
        Message mockMessage = new Message();
        mockMessage.setId("mockMessageId123");
        mockMessage.setSubject(subject); // Ensure subject matches for filter

        // Create a mock MessageCollectionResponse
        MessageCollectionResponse mockResponse = new MessageCollectionResponse();
        mockResponse.setValue(Collections.singletonList(mockMessage)); // Set the list of messages

        // Mock the get() method of messagesRequestBuilder to return our mock response wrapped in CompletableFuture
        // Use thenAnswer to explicitly return the CompletableFuture
        // This is the most reliable way to mock CompletableFuture returns
        when(messagesRequestBuilder.get(any(Consumer.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(mockResponse));

        // When
        MailResponse response = mailService.getSentMailStatus(subject, recipientEmail);

        // Then
        assertNotNull(response);
        assertEquals("FOUND_IN_SENT_ITEMS", response.getStatus());
        assertEquals("Email found in Sent Items folder.", response.getMessage());
        assertEquals("mockMessageId123", response.getMessageId());

        // Verify that the messages().get() method was called
        verify(messagesRequestBuilder, times(1)).get(any(Consumer.class));
    }

    /**
     * Tests the getSentMailStatus method when an email is NOT found in Sent Items.
     */
    @Test
    void getSentMailStatus_notFound() {
        // Given
        String subject = "Not Found Email Subject";
        String recipientEmail = "notfound@example.com";

        // Create an empty mock MessageCollectionResponse
        MessageCollectionResponse mockResponse = new MessageCollectionResponse();
        mockResponse.setValue(Collections.emptyList()); // No messages found

        // Mock the get() method of messagesRequestBuilder to return our empty mock response wrapped in CompletableFuture
        // Use thenAnswer to explicitly return the CompletableFuture
        when(messagesRequestBuilder.get(any(Consumer.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(mockResponse));

        // When
        MailResponse response = mailService.getSentMailStatus(subject, recipientEmail);

        // Then
        assertNotNull(response);
        assertEquals("NOT_FOUND_IN_SENT_ITEMS", response.getStatus());
        assertEquals("Email not found in Sent Items folder. It might still be processing or failed.", response.getMessage());
        assertEquals(null, response.getMessageId());

        // Verify that the messages().get() method was called
        verify(messagesRequestBuilder, times(1)).get(any(Consumer.class));
    }

    /**
     * Tests the getSentMailStatus method for a failed status check scenario (e.g., Graph API error).
     */
    /*@Test
    void getSentMailStatus_failure() {
        // Given
        String subject = "Error Subject";
        String recipientEmail = "error@example.com";

        // Mock the get() method to throw an exception
        // The exception should be thrown by the CompletableFuture itself to mimic async error
        when(messagesRequestBuilder.get(any(Consumer.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Graph Status Check Error"))); // Use failedFuture

        // When
        MailResponse response = mailService.getSentMailStatus(subject, recipientEmail);

        // Then
        assertNotNull(response);
        assertEquals("FAILED_STATUS_CHECK", response.getStatus());
        assertEquals("Failed to check email status: Graph Status Check Error", response.getMessage());
        assertEquals(null, response.getMessageId());

        // Verify that the messages().get() method was attempted
        verify(messagesRequestBuilder, times(1)).get(any(Consumer.class));
    }*/
}