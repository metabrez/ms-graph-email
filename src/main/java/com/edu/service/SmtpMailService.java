package com.edu.service;

import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
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
     * Helper method to convert RecipientModel list to a String array of addresses.
     */
    private String[] extractAddresses(List<MailRequest.RecipientModel> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return new String[0];
        }
        return recipients.stream()
                .filter(r -> r.getEmailAddress() != null)
                .map(r -> r.getEmailAddress().getAddress())
                .collect(Collectors.toList()).toArray(new String[0]);
    }

    /**
     * Sends an email using the configured SMTP server, supporting HTML content
     * required for pixel tracking and robust email sending.
     *
     * @param mailRequest The request containing email details.
     * @return A MailResponse indicating the outcome.
     */
    public MailResponse sendSmtpEmail(MailRequest mailRequest) {
        // Access nested models based on the MS Graph standard input structure
        MailRequest.MessageModel messageModel = mailRequest.getMessage();
        MailRequest.BodyModel bodyModel = messageModel.getBody();

        // Collect all primary recipient addresses for logging (this list contains only 1 recipient per call)
        String recipientsList = messageModel.getToRecipients().stream()
                .filter(r -> r.getEmailAddress() != null)
                .map(r -> r.getEmailAddress().getAddress())
                .collect(Collectors.joining(", "));

        // Extract the Batch ID from the full individual tracking ID (e.g., 7be460b8-...-xyz -> 7be460b8-...)
        String batchId = "N/A";
        String fullTrackingId = mailRequest.getTrackingID();
        if (fullTrackingId != null && fullTrackingId.contains("-")) {
            // The uniqueGroupId is the UUID part before the last hyphen and 8 characters
            int lastHyphen = fullTrackingId.lastIndexOf('-');
            if (lastHyphen > 0) {
                batchId = fullTrackingId.substring(0, lastHyphen);
            }
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, "UTF-8");

            messageHelper.setFrom(smtpUsername);
            messageHelper.setSubject(messageModel.getSubject());

            // Set content and determine if it should be treated as HTML
            boolean isHtml = bodyModel.getContentType().equalsIgnoreCase("Html");
            messageHelper.setText(bodyModel.getContent(), isHtml);

            // Set TO recipients
            messageHelper.setTo(extractAddresses(messageModel.getToRecipients()));

            // Set CC recipients
            if (messageModel.getCcRecipients() != null && !messageModel.getCcRecipients().isEmpty()) {
                messageHelper.setCc(extractAddresses(messageModel.getCcRecipients()));
            }

            // Set BCC recipients (Must use MimeMessage directly for BCC)
            if (messageModel.getBccRecipients() != null && !messageModel.getBccRecipients().isEmpty()) {
                String bccList = messageModel.getBccRecipients().stream()
                        .filter(r -> r.getEmailAddress() != null)
                        .map(r -> r.getEmailAddress().getAddress())
                        .collect(Collectors.joining(","));
                mimeMessage.setRecipients(jakarta.mail.Message.RecipientType.BCC, bccList);
            }

            mailSender.send(mimeMessage);

            // LOG MESSAGE: Updated to show sender and unique recipient for each message copy
            log.info("Email sent successfully via SMTP. From: {} To: {} (Batch ID: {})",
                    smtpUsername,
                    recipientsList,
                    batchId);

            return MailResponse.builder()
                    .status("SUCCESS")
                    .message("Email sent successfully via SMTP.")
                    .messageId(mailRequest.getTrackingID()) // Use tracking ID as the message ID
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
