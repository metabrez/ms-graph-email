package com.edu.service;

import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import com.microsoft.graph.models.*;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final GraphServiceClient graphServiceClient;
    private final SmtpMailService smtpMailService;

    @Value("${graph.sender-email}") // Inject the sender email from application.yml
    private String senderEmail;

    // Constructor injection for GraphServiceClient and SmtpMailService
    public MailService(GraphServiceClient graphServiceClient, SmtpMailService smtpMailService) {
        this.graphServiceClient = graphServiceClient;
        this.smtpMailService = smtpMailService;
    }

    /**
     * Primary endpoint for sending email. Uses trySendEmail for robust protocol handling.
     *
     * @param mailRequest The request containing email details (subject, body, recipients, preferred protocol).
     * @return A MailResponse indicating the outcome of the send operation.
     */
    public MailResponse sendEmail(MailRequest mailRequest) {
        // We now rely on trySendEmail to handle all the logic and fallbacks.
        boolean isSuccess = trySendEmail(mailRequest);

        if (isSuccess) {
            return MailResponse.builder()
                    .status("SUCCESS")
                    .message("Email sent successfully using preferred or fallback protocol.")
                    .messageId("N/A_RobustSend")
                    .build();
        } else {
            return MailResponse.builder()
                    .status("FAILED")
                    .message("Failed to send email after attempting both MSGraph and SMTP protocols.")
                    .messageId(null)
                    .build();
        }
    }

    /**
     * Attempts to send an email using the preferred protocol, and retries with the
     * alternative protocol if the first attempt fails.
     *
     * @param mailRequest The request containing email details.
     * @return true if the email was successfully sent by either protocol, false otherwise.
     */
    public boolean trySendEmail(MailRequest mailRequest) {
        boolean isSuccess = false;
        String preferredProtocol = mailRequest.getPreferredProtocol().toUpperCase();
        String fallbackProtocol = preferredProtocol.equals("MSGRAPH") ? "SMTP" : "MSGRAPH";

        // --- 1. Attempt with Preferred Protocol ---
        if (preferredProtocol.equals("MSGRAPH")) {
            MailResponse response = sendEmailViaGraph(mailRequest);
            isSuccess = "SUCCESS".equals(response.getStatus());
            log.info("Attempt 1 ({}): Success={}", preferredProtocol, isSuccess);
        } else if (preferredProtocol.equals("SMTP")) {
            MailResponse response = sendEmailViaSmtp(mailRequest);
            isSuccess = "SUCCESS".equals(response.getStatus());
            log.info("Attempt 1 ({}): Success={}", preferredProtocol, isSuccess);
        }

        // --- 2. Retry with Fallback Protocol if needed ---
        if (!isSuccess) {
            log.warn("Attempt 1 failed. Retrying with fallback protocol: {}", fallbackProtocol);
            if (fallbackProtocol.equals("MSGRAPH")) {
                MailResponse response = sendEmailViaGraph(mailRequest);
                isSuccess = "SUCCESS".equals(response.getStatus());
            } else if (fallbackProtocol.equals("SMTP")) {
                MailResponse response = sendEmailViaSmtp(mailRequest);
                isSuccess = "SUCCESS".equals(response.getStatus());
            }
            log.info("Attempt 2 ({}): Success={}", fallbackProtocol, isSuccess);
        }

        return isSuccess;
    }

    /**
     * Helper method to send email via Microsoft Graph API.
     */
    private MailResponse sendEmailViaGraph(MailRequest mailRequest) {
        try {
            // Generate a unique identifier
            String uniqueId = UUID.randomUUID().toString();

            // Create a new Message object
            Message message = new Message();
            // Append the unique ID to the subject
            message.setSubject(mailRequest.getSubject() + " - " + uniqueId);

            // Set the email body content and type
            ItemBody body = new ItemBody();
            body.setContentType(mailRequest.getBodyContentType().equalsIgnoreCase("Html") ? BodyType.Html : BodyType.Text);
            body.setContent(mailRequest.getBodyContent());
            message.setBody(body);

            // Add 'To' recipients
            java.util.List<Recipient> toRecipients = mailRequest.getToRecipients().stream()
                    .map(r -> {
                        Recipient recipient = new Recipient();
                        com.microsoft.graph.models.EmailAddress emailAddress = new com.microsoft.graph.models.EmailAddress();
                        emailAddress.setAddress(r.getAddress());
                        emailAddress.setName(r.getName());
                        recipient.setEmailAddress(emailAddress);
                        return recipient;
                    })
                    .collect(Collectors.toList());
            message.setToRecipients(toRecipients);

            // Add 'CC' recipients if provided
            if (mailRequest.getCcRecipients() != null && !mailRequest.getCcRecipients().isEmpty()) {
                java.util.List<Recipient> ccRecipients = mailRequest.getCcRecipients().stream()
                        .map(r -> {
                            Recipient recipient = new Recipient();
                            com.microsoft.graph.models.EmailAddress emailAddress = new com.microsoft.graph.models.EmailAddress();
                            emailAddress.setAddress(r.getAddress());
                            emailAddress.setName(r.getName());
                            recipient.setEmailAddress(emailAddress);
                            return recipient;
                        })
                        .collect(Collectors.toList());
                message.setCcRecipients(ccRecipients);
            }

            // Add 'BCC' recipients if provided
            if (mailRequest.getBccRecipients() != null && !mailRequest.getBccRecipients().isEmpty()) {
                java.util.List<Recipient> bccRecipients = mailRequest.getBccRecipients().stream()
                        .map(r -> {
                            Recipient recipient = new Recipient();
                            com.microsoft.graph.models.EmailAddress emailAddress = new com.microsoft.graph.models.EmailAddress();
                            emailAddress.setAddress(r.getAddress());
                            emailAddress.setName(r.getName());
                            recipient.setEmailAddress(emailAddress);
                            return recipient;
                        })
                        .collect(Collectors.toList());
                message.setBccRecipients(bccRecipients);
            }

            SendMailPostRequestBody sendMailBody = new SendMailPostRequestBody();
            sendMailBody.setMessage(message);
            sendMailBody.setSaveToSentItems(false);

            graphServiceClient.users().byUserId(senderEmail)
                    .sendMail()
                    .post(sendMailBody);

            log.info("Email sent successfully via MSGraph from {} to: {}", senderEmail, mailRequest.getToRecipients().stream()
                    .map(com.edu.model.EmailAddress::getAddress)
                    .collect(Collectors.joining(", ")));

            return MailResponse.builder()
                    .status("SUCCESS")
                    .message("Email send request accepted by Microsoft Graph.")
                    .messageId("N/A_GraphSend")
                    .build();

        } catch (Exception e) {
            log.error("Error sending email via Microsoft Graph: {}", e.getMessage(), e);
            return MailResponse.builder()
                    .status("FAILED")
                    .message("Failed to send email via MSGraph: " + e.getMessage())
                    .messageId(null)
                    .build();
        }
    }

    /**
     * Helper method to send email via SMTP.
     * This method simply wraps the call to the dedicated SmtpMailService.
     */
    private MailResponse sendEmailViaSmtp(MailRequest mailRequest) {
        return smtpMailService.sendSmtpEmail(mailRequest);
    }


    /**
     * Checks the status of a sent email by searching the user's "Sent Items" folder.
     * This method searches by subject and a recipient's email address.
     * NOTE: This status check is only valid for emails sent via MSGraph.
     *
     * @param subject The subject of the email to search for.
     * @param recipientEmail The email address of one of the recipients.
     * @return A MailResponse indicating if the email was found in Sent Items.
     */
    public MailResponse getSentMailStatus(String subject, String recipientEmail) {
        try {
            String filter = String.format("subject eq '%s' and toRecipients/any(r:r/emailAddress/address eq '%s')",
                    subject, recipientEmail);

            // Corrected: Use byUserId() to specify the user
            MessageCollectionResponse messagesResponse = graphServiceClient.users().byUserId(senderEmail).messages()
                    .get(requestConfiguration -> {
                        assert requestConfiguration.queryParameters != null;
                        requestConfiguration.queryParameters.filter = filter;
                        requestConfiguration.queryParameters.top = 10;
                        requestConfiguration.queryParameters.orderby = new String[]{"sentDateTime desc"};
                    });


            if (messagesResponse != null && messagesResponse.getValue() != null && !messagesResponse.getValue().isEmpty()) {
                for (Message message : messagesResponse.getValue()) {
                    log.info("Found message with ID: {} in Sent Items.", message.getId());
                    return MailResponse.builder()
                            .messageId(message.getId())
                            .status("FOUND_IN_SENT_ITEMS")
                            .message("Email found in Sent Items folder.")
                            .build();
                }
            }

            log.info("Email with subject '{}' to '{}' not found in Sent Items.", subject, recipientEmail);
            return MailResponse.builder()
                    .messageId(null)
                    .status("NOT_FOUND_IN_SENT_ITEMS")
                    .message("Email not found in Sent Items folder. It might still be processing or failed.")
                    .build();

        } catch (Exception e) {
            log.error("Error checking email status: {}", e.getMessage(), e);
            return MailResponse.builder()
                    .status("FAILED_STATUS_CHECK")
                    .message("Failed to check email status: " + e.getMessage())
                    .messageId(null)
                    .build();
        }
    }
}
