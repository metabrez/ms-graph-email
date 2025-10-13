package com.edu.service;

import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
     * Sends an email using the configured SMTP server, supporting HTML content
     * required for pixel tracking and robust email sending.
     *
     * @param mailRequest The request containing email details.
     * @return A MailResponse indicating the outcome.
     */
    public MailResponse sendSmtpEmail(MailRequest mailRequest) {
        try {
            // Use MimeMessage for complex content (like HTML)
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // Use UTF-8 encoding
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, "UTF-8");

            messageHelper.setFrom(smtpUsername);
            messageHelper.setSubject(mailRequest.getSubject());

            // Set content and determine if it should be treated as HTML
            boolean isHtml = mailRequest.getBodyContentType().equalsIgnoreCase("Html");
            messageHelper.setText(mailRequest.getBodyContent(), isHtml);

            // Set TO recipients
            String[] toArray = mailRequest.getToRecipients().stream()
                    .map(com.edu.model.EmailAddress::getAddress)
                    .collect(Collectors.toList()).toArray(new String[0]);
            messageHelper.setTo(toArray);

            // Set CC recipients
            if (mailRequest.getCcRecipients() != null && !mailRequest.getCcRecipients().isEmpty()) {
                String[] ccArray = mailRequest.getCcRecipients().stream()
                        .map(com.edu.model.EmailAddress::getAddress)
                        .collect(Collectors.toList()).toArray(new String[0]);
                messageHelper.setCc(ccArray);
            }

            // Set BCC recipients (Must use MimeMessage directly for BCC)
            if (mailRequest.getBccRecipients() != null && !mailRequest.getBccRecipients().isEmpty()) {
                String bccList = mailRequest.getBccRecipients().stream()
                        .map(com.edu.model.EmailAddress::getAddress)
                        .collect(Collectors.joining(","));
                // Note: Using jakarta.mail.Message.RecipientType.BCC requires the jakarta mail API
                mimeMessage.setRecipients(jakarta.mail.Message.RecipientType.BCC, bccList);
            }

            mailSender.send(mimeMessage);

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
