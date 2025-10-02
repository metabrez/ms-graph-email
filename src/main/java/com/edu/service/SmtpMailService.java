package com.edu.service;

import com.edu.model.MailRequest;
import com.edu.model.MailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class SmtpMailService {

    private final JavaMailSender mailSender;

    @Autowired
    public SmtpMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public MailResponse sendSmtpEmail(MailRequest mailRequest) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("your-smtp-username"); // Replace with your configured SMTP username
            message.setSubject(mailRequest.getSubject());
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

            return MailResponse.builder()
                    .status("SUCCESS")
                    .message("Email sent successfully via SMTP.")
                    .messageId("N/A_SmtpSend")
                    .build();
        } catch (Exception e) {
            return MailResponse.builder()
                    .status("FAILED")
                    .message("Failed to send email via SMTP: " + e.getMessage())
                    .messageId(null)
                    .build();
        }
    }
}