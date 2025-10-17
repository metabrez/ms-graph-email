package com.edu.controller;

import com.edu.model.EmailOpenTracking;
import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import com.edu.service.MailService;
import com.edu.service.TrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSender;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mail")
public class MailController {

    private static final Logger log = LoggerFactory.getLogger(MailController.class);

    private final MailService mailService;
    private final TrackingService trackingService;

    // A tiny 1x1 transparent GIF image in Base64 format
    // Used to respond to tracking pixel requests without showing a broken image icon.
    private static final byte[] TRACKING_PIXEL_GIF = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            0x00, 0x00, 0x00, 0x21, (byte) 0xf9, 0x04, 0x01, 0x0a, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00, 0x00,
            0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3b
    };

    // Constructor injection for MailService
    public MailController(MailService mailService, TrackingService trackingService) {
        this.mailService = mailService;
        this.trackingService = trackingService;

    }

    /**
     * Endpoint to send an email.
     *
     * @param mailRequest The JSON payload containing email details.
     * @return ResponseEntity with MailResponse and HTTP status.
     */
    /**
     * Endpoint to send an email.
     *
     * @param mailRequest The JSON payload containing email details.
     * @return ResponseEntity with MailResponse and HTTP status.
     */
    @PostMapping("/send")
    public ResponseEntity<MailResponse> sendMail(@RequestBody MailRequest mailRequest) {
        // Access nested fields for logging
        String subject = mailRequest.getMessage().getSubject();
        String toRecipients = mailRequest.getMessage().getToRecipients().stream()
                .map(r -> r.getEmailAddress().getAddress())
                .collect(Collectors.joining(", "));

        log.info("Received request to send email. Subject: {}, To: {}", subject, toRecipients);

        MailResponse response = mailService.sendEmail(mailRequest);
        if ("SUCCESS".equals(response.getStatus())) {
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED); // 202 Accepted
        } else {
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR); // 500 Internal Server Error
        }
    }

    /**
     * Endpoint to check the status of a previously sent email.
     * This checks if the email is present in the sender's "Sent Items" folder.
     *
     * @param subject The subject of the email to search for.
     * @param recipientEmail The email address of one of the recipients.
     * @return A MailResponse indicating if the email was found in Sent Items.
     */
    @GetMapping("/status")
    public ResponseEntity<MailResponse> getMailStatus(
            @RequestParam String subject,
            @RequestParam String recipientEmail) {
        log.info("Received request to check mail status. Subject: {}, Recipient: {}", subject, recipientEmail);
        MailResponse response = mailService.getSentMailStatus(subject, recipientEmail);
        if ("FOUND_IN_SENT_ITEMS".equals(response.getStatus())) {
            return new ResponseEntity<>(response, HttpStatus.OK); // 200 OK
        } else if ("NOT_FOUND_IN_SENT_ITEMS".equals(response.getStatus())) {
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND); // 404 Not Found
        } else {
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR); // 500 Internal Server Error
        }
    }

    /**
     * NEW: Endpoint to handle the tracking pixel hit.
     * When a recipient opens the email, their mail client calls this endpoint.
     *
     * @param trackingId The unique ID embedded in the email for tracking.
     * @return A tiny 1x1 transparent GIF image.
     */
    @GetMapping("/track/{trackingId}.gif")
    public ResponseEntity<byte[]> trackMailOpen(@PathVariable String trackingId) {
        // Log the event, which indicates the email was opened and images were loaded.
        log.info("Email successfully opened/read. Tracking ID: {}", trackingId);

        // CRITICAL: Record the open event and retrieve the tracking data
        EmailOpenTracking tracking = trackingService.trackOpen(trackingId);

        // Log the event, which indicates the email was opened and images were loaded.
        log.info("Email successfully opened/read. Tracking ID: {}, Total Opens: {}, Last Open: {}",
                trackingId, tracking.getOpenCount(), tracking.getLastOpenTimestamp());

        // Build headers for the image response
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("image/gif"));
        headers.setContentLength(TRACKING_PIXEL_GIF.length);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");

        // Return the transparent GIF byte array
        return new ResponseEntity<>(TRACKING_PIXEL_GIF, headers, HttpStatus.OK);
    }
    @GetMapping
    public String hello(){
        return "Hello World";
    }

    // --- NEW ENDPOINT TO RETRIEVE TRACKING DATA ---
    /**
     * Endpoint to retrieve the email open tracking status.
     *
     * @param trackingId The individual tracking ID for the email copy (e.g., [GroupID]-[Short_Random_Suffix]-[RecipientEmail]).
     * @return EmailOpenTracking object with open count and timestamps.
     */
    @GetMapping("/track/status/{trackingId}")
    public ResponseEntity<EmailOpenTracking> getTrackingStatus(@PathVariable String trackingId) {
        log.info("Received request to check open tracking status for ID: {}", trackingId);
        EmailOpenTracking tracking = trackingService.getTrackingStatus(trackingId);

        if (tracking != null) {
            // Returns: trackingId, openCount, firstOpenTimestamp, lastOpenTimestamp
            return new ResponseEntity<>(tracking, HttpStatus.OK); // 200 OK
        } else {
            log.warn("Tracking status not found for ID {}", trackingId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404 Not Found
        }
    }
}