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
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private SmtpMailService smtpMailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Set SMTP username manually since @Value injection won't run in test context
        smtpMailService = new SmtpMailService(mailSender);
        try {
            java.lang.reflect.Field field = SmtpMailService.class.getDeclaredField("smtpUsername");
            field.setAccessible(true);
            field.set(smtpMailService, "no-reply@example.com");
        } catch (Exception e) {
            fail("Failed to inject smtpUsername");
        }
    }

    private MailRequest createMockMailRequest() {
        MailRequest request = new MailRequest();
        MailRequest.MessageModel message = new MailRequest.MessageModel();
        MailRequest.BodyModel body = new MailRequest.BodyModel();

        body.setContent("<p>Hello!</p>");
        body.setContentType("Html");
        message.setSubject("Test Email");
        message.setBody(body);

        // ✅ Use correct EmailAddress class
        EmailAddress email = new EmailAddress("to@example.com", "Test User");

        MailRequest.RecipientModel recipient = new MailRequest.RecipientModel();
        recipient.setEmailAddress(email);

        message.setToRecipients(Collections.singletonList(recipient));

        request.setMessage(message);
        request.setPreferredProtocol("SMTP");
        request.setRequestPixelTracking(true);
        request.setSaveToSentItems(true);
        request.setTrackingID("12345-batchXYZ");

        return request;
    }

    @Test
    void testSendSmtpEmail_Success() throws Exception {
        MailRequest request = createMockMailRequest();

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        MailResponse response = smtpMailService.sendSmtpEmail(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getMessage().contains("successfully"));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendSmtpEmail_Failure() throws Exception {
        MailRequest request = createMockMailRequest();

        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP Error"));

        MailResponse response = smtpMailService.sendSmtpEmail(request);

        assertNotNull(response);
        assertEquals("FAILED", response.getStatus());
        assertTrue(response.getMessage().contains("Failed to send email"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
