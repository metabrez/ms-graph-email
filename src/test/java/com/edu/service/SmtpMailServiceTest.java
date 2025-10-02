package com.edu.service;

import com.edu.model.EmailAddress;
import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
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
    }

    private MailRequest createMailRequest() {
        MailRequest mailRequest = new MailRequest();
        mailRequest.setSubject("Test Subject SMTP");
        mailRequest.setBodyContent("Test Body Content");
        mailRequest.setBodyContentType("Text");
        mailRequest.setToRecipients(Arrays.asList(new EmailAddress("recipient1@example.com", "Recipient One")));
        mailRequest.setCcRecipients(Arrays.asList(new EmailAddress("cc@example.com", "CC User")));
        mailRequest.setBccRecipients(Arrays.asList(new EmailAddress("bcc@example.com", "BCC User")));
        return mailRequest;
    }

    @Test
    void sendSmtpEmail_success() {
        // Given
        MailRequest request = createMailRequest();

        // Mock mailSender.send() to do nothing (simulate success)
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));

        // When
        MailResponse response = smtpMailService.sendSmtpEmail(request);

        // Then
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Email sent successfully via SMTP.", response.getMessage());
        assertEquals("N/A_SmtpSend", response.getMessageId());

        // Verify that the send method was called exactly once with a SimpleMailMessage
        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSmtpEmail_failure() {
        // Given
        MailRequest request = createMailRequest();
        String errorMessage = "Authentication failed";

        // Mock mailSender.send() to throw an exception
        doThrow(new MailSendException(errorMessage)).when(javaMailSender).send(any(SimpleMailMessage.class));

        // When
        MailResponse response = smtpMailService.sendSmtpEmail(request);

        // Then
        assertNotNull(response);
        assertEquals("FAILED", response.getStatus());
        assertEquals("Failed to send email via SMTP: " + errorMessage, response.getMessage());
        assertEquals(null, response.getMessageId());

        // Verify that the send method was attempted
        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
