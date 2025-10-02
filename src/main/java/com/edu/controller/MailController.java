package com.edu.controller;

import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import com.edu.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mail")
public class MailController {

    private static final Logger log = LoggerFactory.getLogger(MailController.class);

    private final MailService mailService;

    // Constructor injection for MailService
    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    /**
     * Endpoint to send an email.
     *
     * @param mailRequest The JSON payload containing email details.
     * @return ResponseEntity with MailResponse and HTTP status.
     */
    @PostMapping("/send")
    public ResponseEntity<MailResponse> sendMail(@RequestBody MailRequest mailRequest) {
        log.info("Received request to send email. Subject: {}, To: {}",
                mailRequest.getSubject(), mailRequest.getToRecipients());
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
}