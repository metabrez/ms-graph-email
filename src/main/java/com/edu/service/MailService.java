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

    @Value("${graph.tracking-base-url}")
    private String trackingBaseUrl;

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

        // --- PRE-PROCESSING FOR PIXEL TRACKING ---
        if (mailRequest.isRequestPixelTracking()) {
            // 1. Generate unique tracking ID
            String trackingId = UUID.randomUUID().toString();

            // IMPORTANT: Set the tracking ID on the request object.
            // This ensures the ID is retained if the request object is logged or used later.
            mailRequest.setTrackingID(trackingId);

            // 2. Inject pixel into HTML content (modifies mailRequest.bodyContent)
            mailRequest.setBodyContent(injectTrackingPixel(mailRequest.getBodyContent(), trackingId));

            // Ensure the body type is set to Html, as the pixel is always HTML
            mailRequest.setBodyContentType("Html");

            log.info("Enabled pixel tracking. Tracking ID: {}", trackingId);
        }

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
     * Helper method to prepend an invisible tracking pixel to the email body.
     * Includes logic to force HTTP protocol for local tunnel environments (like ngrok)
     * to prevent security/redirect issues when hitting localhost/internal IP.
     */
    private String injectTrackingPixel(String originalBody, String trackingId) {
        String baseUrl = trackingBaseUrl;

        // CRITICAL FIX: If using ngrok's HTTPS URL, change the base URL to use HTTP.
        // The ngrok tunnel already handles the HTTPS to HTTP conversion, but the browser/mail client
        // may reject the direct HTTPS call to the local service if the URL is not corrected.
        if (baseUrl.toLowerCase().startsWith("https://")) {
            baseUrl = "http://" + baseUrl.substring(8);
        }

        // Construct the tracking pixel URL pointing to the new controller endpoint
        String pixelUrl = String.format("%s/api/mail/track/%s.gif", baseUrl, trackingId);

        // Create the invisible HTML image tag, using highly optimized inline CSS to prevent visibility
        String pixelTag = String.format("<img src=\"%s\" width=\"1\" height=\"1\" border=\"0\" style=\"height:1px !important; width:1px !important; border-width:0 !important; margin-top:0 !important; margin-bottom:0 !important; margin-right:0 !important; margin-left:0 !important; padding-top:0 !important; padding-bottom:0 !important; padding-right:0 !important; padding-left:0 !important; display:block !important;\" />", pixelUrl);

        // Prepend the pixel tag to the original body content
        return pixelTag + originalBody;
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
        switch (preferredProtocol) {
            case "MSGRAPH":
                MailResponse graphResponse = sendEmailViaGraph(mailRequest);
                isSuccess = "SUCCESS".equals(graphResponse.getStatus());
                break;
            case "SMTP":
                MailResponse smtpResponse = sendEmailViaSmtp(mailRequest);
                isSuccess = "SUCCESS".equals(smtpResponse.getStatus());
                break;
            default:
                log.warn("Unknown preferred protocol: {}. Defaulting to MSGRAPH for attempt 1.", preferredProtocol);
                MailResponse defaultGraphResponse = sendEmailViaGraph(mailRequest);
                isSuccess = "SUCCESS".equals(defaultGraphResponse.getStatus());
                preferredProtocol = "MSGRAPH"; // Update for logging if needed
                fallbackProtocol = "SMTP";
                break;
        }
        log.info("Attempt 1 ({}): Success={}", preferredProtocol, isSuccess);


        // --- 2. Retry with Fallback Protocol if needed ---
        if (!isSuccess) {
            log.warn("Attempt 1 failed. Retrying with fallback protocol: {}", fallbackProtocol);

            switch (fallbackProtocol) {
                case "MSGRAPH":
                    MailResponse graphFallbackResponse = sendEmailViaGraph(mailRequest);
                    isSuccess = "SUCCESS".equals(graphFallbackResponse.getStatus());
                    break;
                case "SMTP":
                    MailResponse smtpFallbackResponse = sendEmailViaSmtp(mailRequest);
                    isSuccess = "SUCCESS".equals(smtpFallbackResponse.getStatus());
                    break;
                // No default case needed here since fallbackProtocol is always one of the two
            }
            log.info("Attempt 2 ({}): Success={}", fallbackProtocol, isSuccess);
        }

        return isSuccess;
    }

    /**
     * Helper method to send email via Microsoft Graph API.
     */
    public MailResponse sendEmailViaGraph(MailRequest mailRequest) {
        try {
            // Generate a unique identifier (Note: if tracking is enabled, ID is already set)
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
    public MailResponse sendEmailViaSmtp(MailRequest mailRequest) {
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
