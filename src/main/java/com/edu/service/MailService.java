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
import java.util.List;
import java.util.Arrays;
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
     * Primary endpoint for sending email. Now iterates through recipients and sends a personalized copy to each.
     *
     * @param originalRequest The request containing email details (subject, body, recipients, preferred protocol).
     * @return A MailResponse indicating the outcome of the send operation.
     */
    public MailResponse sendEmail(MailRequest originalRequest) {

        // --- Accessing nested message model ---
        MailRequest.MessageModel messageModel = originalRequest.getMessage();
        List<MailRequest.RecipientModel> originalToRecipients = messageModel.getToRecipients();

        if (originalToRecipients == null || originalToRecipients.isEmpty()) {
            return MailResponse.builder()
                    .status("FAILED")
                    .message("No recipients found in toRecipients list.")
                    .messageId(null)
                    .build();
        }

        // Generate a single UUID that represents the entire send campaign/batch.
        String uniqueGroupId = UUID.randomUUID().toString();

        log.info("Starting batch send (Group ID: {} ) to {} recipients.", uniqueGroupId, originalToRecipients.size());

        // --- 1. PREPARE STATIC PARTS ---
        // Store original CC/BCC lists (they will be copied to every recipient's cloned message)
        List<MailRequest.RecipientModel> originalCcRecipients = messageModel.getCcRecipients();
        List<MailRequest.RecipientModel> originalBccRecipients = messageModel.getBccRecipients();

        // --- 2. LOOP THROUGH RECIPIENTS AND SEND UNIQUE COPIES ---
        boolean anySuccess = false;

        for (MailRequest.RecipientModel individualRecipient : originalToRecipients) {

            String recipientEmail = individualRecipient.getEmailAddress().getAddress();

            // Create a unique ID for this specific recipient and message copy
            // FORMAT: [GroupID]-[Short_Random_Suffix]-[RecipientEmail]
            String individualTrackingId = uniqueGroupId + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + recipientEmail;

            // Clone the request object for modification
            MailRequest mailRequestCopy = originalRequest.deepCopy();
            MailRequest.MessageModel copyMessageModel = mailRequestCopy.getMessage();
            MailRequest.BodyModel copyBodyModel = copyMessageModel.getBody();

            // Set the unique ID for this recipient's pixel and the API response tracking.
            mailRequestCopy.setTrackingID(individualTrackingId);

            // --- Set Subject and Recipients ---
            // 1. Set the individual as the ONLY ToRecipients
            copyMessageModel.setToRecipients(Arrays.asList(individualRecipient));
            // 2. Append the group ID to the subject
            copyMessageModel.setSubject(messageModel.getSubject() + " (Batch ID: " + uniqueGroupId + ")");
            // 3. Re-assign CC/BCC (optional, if you want them in the individual copies)
            copyMessageModel.setCcRecipients(originalCcRecipients);
            copyMessageModel.setBccRecipients(originalBccRecipients);


            // --- 4. PIXEL TRACKING INJECTION ---
            if (mailRequestCopy.isRequestPixelTracking()) {

                // Inject pixel into HTML content (modifies content with individualTrackingId)
                copyBodyModel.setContent(injectTrackingPixel(copyBodyModel.getContent(), individualTrackingId));
                copyBodyModel.setContentType("Html");

                log.info("-> Preparing copy for {} with individual Tracking ID: {}",
                        recipientEmail, individualTrackingId);
            }


            // --- 5. ATTEMPT SEND ---
            if (trySendEmail(mailRequestCopy)) {
                anySuccess = true;
            }

            // CRITICAL FIX: Log the completion status for the individual recipient.
            log.info("Finished attempt for recipient: {} (Individual ID: {})",
                    recipientEmail, individualTrackingId);
        }

        // --- 6. RETURN GROUP STATUS ---
        if (anySuccess) {
            return MailResponse.builder()
                    .status("SUCCESS")
                    .message("Batch send initiated. At least one email was sent successfully. Use Group ID for reference.")
                    .messageId(uniqueGroupId) // Return the Group ID
                    .build();
        } else {
            return MailResponse.builder()
                    .status("FAILED")
                    .message("Failed to send email to any recipient after attempting both MSGraph and SMTP protocols.")
                    .messageId(null)
                    .build();
        }
    }

    /**
     * Helper method to prepend an invisible tracking pixel to the email body.
     */
    private String injectTrackingPixel(String originalBody, String trackingId) {
        String baseUrl = trackingBaseUrl;

        // CRITICAL FIX: If using ngrok's HTTPS URL, change the base URL to use HTTP.
        if (baseUrl != null && baseUrl.toLowerCase().startsWith("https://")) {
            baseUrl = "http://" + baseUrl.substring(8);
        } else if (baseUrl == null) {
            log.warn("trackingBaseUrl is null. Cannot inject pixel.");
            return originalBody;
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
     */
    public boolean trySendEmail(MailRequest mailRequest) {
        boolean isSuccess = false;
        String preferredProtocol = mailRequest.getPreferredProtocol().toUpperCase();
        String fallbackProtocol = preferredProtocol.equals("MSGRAPH") ? "SMTP" : "MSGRAPH";

        // --- Prepare contextual logging info ---
        String recipient = mailRequest.getMessage().getToRecipients().stream()
                .findFirst()
                .map(r -> r.getEmailAddress().getAddress())
                .orElse("UNKNOWN_RECIPIENT");

        // Safely extract the batch ID from the full tracking ID
        String fullTrackingId = mailRequest.getTrackingID();
        String batchId = fullTrackingId != null && fullTrackingId.contains("-")
                ? fullTrackingId.substring(0, fullTrackingId.indexOf('-'))
                : "N/A";

        // Log the start of the attempt for this specific recipient
        log.info("--- Attempting send for {} (Batch ID: {}) ---", recipient, batchId);


        // --- 1. Attempt with Preferred Protocol ---
        switch (preferredProtocol) {
            case "MSGRAPH":
                MailResponse graphResponse = sendEmailViaGraph(mailRequest);
                isSuccess = "SUCCESS".equals(graphResponse.getStatus());
                log.info("Attempt 1 (MSGRAPH) for {}: Success={}", recipient, isSuccess);
                break;
            case "SMTP":
                MailResponse smtpResponse = sendEmailViaSmtp(mailRequest);
                isSuccess = "SUCCESS".equals(smtpResponse.getStatus());
                // The SMTP service logs its own success message, so we only need the generic attempt status here.
                log.info("Attempt 1 (SMTP) for {}: Success={}", recipient, isSuccess);
                break;
            default:
                log.warn("Unknown preferred protocol: {}. Defaulting to MSGRAPH for attempt 1.", preferredProtocol);
                MailResponse defaultGraphResponse = sendEmailViaGraph(mailRequest);
                isSuccess = "SUCCESS".equals(defaultGraphResponse.getStatus());
                break;
        }

        // --- 2. Retry with Fallback Protocol if needed ---
        if (!isSuccess) {
            log.warn("Attempt 1 failed for {}. Retrying with fallback protocol: {}", recipient, fallbackProtocol);

            switch (fallbackProtocol) {
                case "MSGRAPH":
                    MailResponse graphFallbackResponse = sendEmailViaGraph(mailRequest);
                    isSuccess = "SUCCESS".equals(graphFallbackResponse.getStatus());
                    log.info("Attempt 2 (MSGRAPH) for {}: Success={}", recipient, isSuccess);
                    break;
                case "SMTP":
                    MailResponse smtpFallbackResponse = sendEmailViaSmtp(mailRequest);
                    isSuccess = "SUCCESS".equals(smtpFallbackResponse.getStatus());
                    // The SMTP service logs its own success message, so we only need the generic attempt status here.
                    log.info("Attempt 2 (SMTP) for {}: Success={}", recipient, isSuccess);
                    break;
                // No default case needed here since fallbackProtocol is always one of the two
            }
        }

        // Log final outcome of this specific recipient's transmission attempt
        log.info("--- Transmission complete for {} (Success: {}) ---", recipient, isSuccess);


        return isSuccess;
    }

    // ... (Helper methods for Graph/SMTP remain below) ...

    /**
     * Helper method to convert our MailRequest structure to MS Graph SDK structure
     */
    private com.microsoft.graph.models.EmailAddress toGraphEmailAddress(com.edu.model.EmailAddress appEmail) {
        com.microsoft.graph.models.EmailAddress graphEmail = new com.microsoft.graph.models.EmailAddress();
        graphEmail.setAddress(appEmail.getAddress());
        graphEmail.setName(appEmail.getName());
        return graphEmail;
    }

    /**
     * Helper method to convert our MailRequest structure to MS Graph SDK structure
     */
    private Recipient toGraphRecipient(MailRequest.RecipientModel appRecipient) {
        Recipient graphRecipient = new Recipient();
        if (appRecipient != null && appRecipient.getEmailAddress() != null) {
            graphRecipient.setEmailAddress(toGraphEmailAddress(appRecipient.getEmailAddress()));
        }
        return graphRecipient;
    }

    /**
     * Helper method to send email via Microsoft Graph API.
     */
    public MailResponse sendEmailViaGraph(MailRequest mailRequest) {
        try {
            MailRequest.MessageModel appMessage = mailRequest.getMessage();

            // Create a new Message object
            Message message = new Message();
            message.setSubject(appMessage.getSubject());

            // Set the email body content and type
            ItemBody body = new ItemBody();
            body.setContentType(appMessage.getBody().getContentType().equalsIgnoreCase("Html") ? BodyType.Html : BodyType.Text);
            body.setContent(appMessage.getBody().getContent());
            message.setBody(body);

            // Add 'To' recipients
            java.util.List<Recipient> toRecipients = appMessage.getToRecipients().stream()
                    .map(this::toGraphRecipient)
                    .collect(Collectors.toList());
            message.setToRecipients(toRecipients);

            // Add 'CC' recipients if provided
            if (appMessage.getCcRecipients() != null && !appMessage.getCcRecipients().isEmpty()) {
                java.util.List<Recipient> ccRecipients = appMessage.getCcRecipients().stream()
                        .map(this::toGraphRecipient)
                        .collect(Collectors.toList());
                message.setCcRecipients(ccRecipients);
            }

            // Add 'BCC' recipients if provided
            if (appMessage.getBccRecipients() != null && !appMessage.getBccRecipients().isEmpty()) {
                java.util.List<Recipient> bccRecipients = appMessage.getBccRecipients().stream()
                        .map(this::toGraphRecipient)
                        .collect(Collectors.toList());
                message.setBccRecipients(bccRecipients);
            }

            SendMailPostRequestBody sendMailBody = new SendMailPostRequestBody();
            sendMailBody.setMessage(message);
            sendMailBody.setSaveToSentItems(mailRequest.getSaveToSentItems());

            log.info("Sending via MSGraph: Subject='{}', Sender='{}'", appMessage.getSubject(), senderEmail);

            graphServiceClient.users().byUserId(senderEmail)
                    .sendMail()
                    .post(sendMailBody);

            return MailResponse.builder()
                    .status("SUCCESS")
                    .message("Email send request accepted by Microsoft Graph.")
                    .messageId(mailRequest.getTrackingID())
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
     */
    public MailResponse sendEmailViaSmtp(MailRequest mailRequest) {
        return smtpMailService.sendSmtpEmail(mailRequest);
    }


    /**
     * Checks the status of a sent email by searching the user's "Sent Items" folder.
     */
    public MailResponse getSentMailStatus(String subject, String recipientEmail) {
        try {
            // Note: The subject now contains the unique ID, which is good for searching.
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
