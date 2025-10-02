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
     * Sends an email using Microsoft Graph API with a fallback to SMTP.
     *
     * @param mailRequest The request containing email details (subject, body, recipients).
     * @return A MailResponse indicating the outcome of the send operation.
     */
    public MailResponse sendEmail(MailRequest mailRequest) {
        log.info("Attempting to send email via Microsoft Graph...");
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
                        EmailAddress emailAddress = new EmailAddress();
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
                            EmailAddress emailAddress = new EmailAddress();
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
                            EmailAddress emailAddress = new EmailAddress();
                            emailAddress.setAddress(r.getAddress());
                            emailAddress.setName(r.getName());
                            recipient.setEmailAddress(emailAddress);
                            return recipient;
                        })
                        .collect(Collectors.toList());
                message.setBccRecipients(bccRecipients);
            }

            // Build the sendMail request using SendMailPostRequestBody
            SendMailPostRequestBody sendMailBody = new SendMailPostRequestBody();
            sendMailBody.setMessage(message);
            sendMailBody.setSaveToSentItems(false);

            // Corrected call: The sendMail() method on the UsersItemRequestBuilder does not take arguments.
            // The request body is passed to the post() method.
            graphServiceClient.users().byUserId(senderEmail) // Use byUserId() to specify the user
                    .sendMail()
                    .post(sendMailBody);

            log.info("Email sent successfully from {} to: {} via Microsoft Graph.", senderEmail, mailRequest.getToRecipients().stream()
                    .map(com.edu.model.EmailAddress::getAddress)
                    .collect(Collectors.joining(", ")));

            return MailResponse.builder()
                    .status("SUCCESS")
                    .message("Email send request accepted by Microsoft Graph. It should appear in Sent Items shortly.")
                    .messageId("N/A_DirectSend")
                    .build();

        } catch (Exception e) {
            log.error("Error sending email via Microsoft Graph: {}. Falling back to SMTP...", e.getMessage(), e);
            // Fallback to SMTP
            MailResponse smtpResponse = smtpMailService.sendSmtpEmail(mailRequest);
            if ("SUCCESS".equals(smtpResponse.getStatus())) {
                log.info("Email sent successfully via SMTP.");
                return smtpResponse;
            } else {
                log.error("Failed to send email via SMTP as well. Subject: {}", mailRequest.getSubject());
                return MailResponse.builder()
                        .status("FAILED")
                        .message("Failed to send email via both Microsoft Graph and SMTP. Error: " + e.getMessage())
                        .messageId(null)
                        .build();
            }
        }
    }

    /**
     * Checks the status of a sent email by searching the user's "Sent Items" folder.
     * This method searches by subject and a recipient's email address.
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