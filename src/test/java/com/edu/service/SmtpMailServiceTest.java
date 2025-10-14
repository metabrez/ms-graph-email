package com.edu.service;

import com.edu.model.EmailAddress;
import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the SmtpMailService class, mocking the JavaMailSender.
 */
class SmtpMailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private SmtpMailService smtpMailService;

    private final String SMTP_USERNAME = "smtp_sender@example.com";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Inject the @Value field for smtpUsername
        ReflectionTestUtils.setField(smtpMailService, "smtpUsername", SMTP_USERNAME);

        // Mock the creation of MimeMessage by the mailSender
        when(javaMailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
    }

    private MailRequest createMailRequest() {
        // Create the nested structure based on the refactored MailRequest class
        MailRequest mailRequest = new MailRequest();

        MailRequest.BodyModel body = new MailRequest.BodyModel();
        body.setContentType("Text");
        body.setContent("Test Body Content");

        MailRequest.MessageModel message = new MailRequest.MessageModel();
        message.setSubject("Test Subject SMTP");
        message.setBody(body);

        // Recipients for TO, CC, and BCC
        List<MailRequest.RecipientModel> toRecipients = Arrays.asList(
                new MailRequest.RecipientModel(new EmailAddress("recipient1@example.com", "Recipient One"))
        );
        List<MailRequest.RecipientModel> ccRecipients = Arrays.asList(
                new MailRequest.RecipientModel(new EmailAddress("cc@example.com", "CC User"))
        );
        List<MailRequest.RecipientModel> bccRecipients = Arrays.asList(
                new MailRequest.RecipientModel(new EmailAddress("bcc@example.com", "BCC User"))
        );

        message.setToRecipients(toRecipients);
        message.setCcRecipients(ccRecipients);
        message.setBccRecipients(bccRecipients);

        mailRequest.setMessage(message);
        mailRequest.setSaveToSentItems(false);
        mailRequest.setPreferredProtocol("SMTP");
        mailRequest.setTrackingID("test-smtp-tracking-id");

        return mailRequest;
    }

    @Test
    void sendSmtpEmail_success() {
        // Given
        MailRequest request = createMailRequest();

        // Mock mailSender.send() to do nothing (simulate success)
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        // When
        MailResponse response = smtpMailService.sendSmtpEmail(request);

        // Then
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Email sent successfully via SMTP.", response.getMessage());
        assertEquals(request.getTrackingID(), response.getMessageId());

        // Verify that the send method was called exactly once with a MimeMessage
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendSmtpEmail_failure() {
        // Given
        MailRequest request = createMailRequest();
        String errorMessage = "Authentication failed";

        // Mock mailSender.send() to throw an exception
        doThrow(new MailSendException(errorMessage)).when(javaMailSender).send(any(MimeMessage.class));

        // When
        MailResponse response = smtpMailService.sendSmtpEmail(request);

        // Then
        assertNotNull(response);
        assertEquals("FAILED", response.getStatus());
        assertEquals("Failed to send email via SMTP: " + errorMessage, response.getMessage());
        assertEquals(null, response.getMessageId());

        // Verify that the send method was attempted
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }
}
