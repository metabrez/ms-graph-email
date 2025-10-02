package com.edu.service;

import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class SmtpMailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}") // Inject the SMTP username to use as the sender address
    private String smtpUsername;

    @Autowired
    public SmtpMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an email using the configured SMTP server.
     *
     * @param mailRequest The request containing email details.
     * @return A MailResponse indicating the outcome.
     */
    public MailResponse sendSmtpEmail(MailRequest mailRequest) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(smtpUsername); // Set sender from configured username
            message.setSubject(mailRequest.getSubject());

            // Check body content type. SimpleMailMessage only supports plain text.
            if (mailRequest.getBodyContentType().equalsIgnoreCase("Html")) {
                log.warn("HTML content detected for SMTP. Sending as plain text only. For full HTML support, use MimeMessageHelper.");
            }
            message.setText(mailRequest.getBodyContent());

            message.setTo(mailRequest.getToRecipients().stream()
                    .map(com.edu.model.EmailAddress::getAddress)
                    .collect(Collectors.toList()).toArray(new String[0]));

            if (mailRequest.getCcRecipients() != null && !mailRequest.getCcRecipients().isEmpty()) {
                message.setCc(mailRequest.getCcRecipients().stream()
                        .map(com.edu.model.EmailAddress::getAddress)
                        .collect(Collectors.toList()).toArray(new String[0]));
            }

            if (mailRequest.getBccRecipients() != null && !mailRequest.getBccRecipients().isEmpty()) {
                message.setBcc(mailRequest.getBccRecipients().stream()
                        .map(com.edu.model.EmailAddress::getAddress)
                        .collect(Collectors.toList()).toArray(new String[0]));
            }

            mailSender.send(message);

            log.info("Email sent successfully via SMTP.");

            return MailResponse.builder()
                    .status("SUCCESS")
                    .message("Email sent successfully via SMTP.")
                    .messageId("N/A_SmtpSend")
                    .build();
        } catch (Exception e) {
            log.error("Failed to send email via SMTP: {}", e.getMessage(), e);
            return MailResponse.builder()
                    .status("FAILED")
                    .message("Failed to send email via SMTP: " + e.getMessage())
                    .messageId(null)
                    .build();
        }
    }
}
