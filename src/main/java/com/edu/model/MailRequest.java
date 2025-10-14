package com.edu.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MailRequest {
    // Application-specific top-level fields
    private String preferredProtocol = "MSGRAPH";
    private boolean requestPixelTracking = false;
    private String trackingID; // Used for the entire group/batch

    // MS Graph standard nested structure
    private MessageModel message;
    private Boolean saveToSentItems = false; // Added as per MS Graph standard

    // Default constructor (required for JSON deserialization)
    public MailRequest() {
        this.message = new MessageModel();
    }

    // --- Deep Copy Method (FIXED) ---
    public MailRequest deepCopy() {
        MailRequest copy = new MailRequest();

        // Copy top-level properties
        copy.setPreferredProtocol(this.preferredProtocol);
        copy.setRequestPixelTracking(this.requestPixelTracking);
        copy.setTrackingID(this.trackingID);
        copy.setSaveToSentItems(this.saveToSentItems);

        // Deep copy the nested MessageModel
        if (this.message != null) {
            MessageModel msgCopy = new MessageModel();
            msgCopy.setSubject(this.message.getSubject());

            // Deep copy BodyModel
            if (this.message.getBody() != null) {
                BodyModel bodyCopy = new BodyModel();
                // We must copy the content string here so injection doesn't affect the original object's content
                bodyCopy.setContent(this.message.getBody().getContent());
                bodyCopy.setContentType(this.message.getBody().getContentType());
                msgCopy.setBody(bodyCopy);
            } else {
                msgCopy.setBody(new BodyModel());
            }

            // Copy Recipients (shallow copy of list is fine, since RecipientModel/EmailAddress are immutable for now)
            // Note: Use of ArrayList<>(list) creates a new list object containing references to the original recipient objects.
            if (this.message.getToRecipients() != null) {
                msgCopy.setToRecipients(new ArrayList<>(this.message.getToRecipients()));
            }
            if (this.message.getCcRecipients() != null) {
                msgCopy.setCcRecipients(new ArrayList<>(this.message.getCcRecipients()));
            }
            if (this.message.getBccRecipients() != null) {
                msgCopy.setBccRecipients(new ArrayList<>(this.message.getBccRecipients()));
            }

            copy.setMessage(msgCopy);
        } else {
            copy.setMessage(new MessageModel());
        }
        return copy;
    }


    // --- Nested Models (MS Graph Standard) ---

    public static class MessageModel {
        private String subject;
        private BodyModel body;
        private List<RecipientModel> toRecipients;
        private List<RecipientModel> ccRecipients; // Optional
        private List<RecipientModel> bccRecipients; // Optional

        public MessageModel() {
            this.body = new BodyModel();
            this.toRecipients = new ArrayList<>();
        }

        // Getters and Setters ...
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public BodyModel getBody() { return body; }
        public void setBody(BodyModel body) { this.body = body; }
        public List<RecipientModel> getToRecipients() { return toRecipients; }
        public void setToRecipients(List<RecipientModel> toRecipients) { this.toRecipients = toRecipients; }
        public List<RecipientModel> getCcRecipients() { return ccRecipients; }
        public void setCcRecipients(List<RecipientModel> ccRecipients) { this.ccRecipients = ccRecipients; }
        public List<RecipientModel> getBccRecipients() { return bccRecipients; }
        public void setBccRecipients(List<RecipientModel> bccRecipients) { this.bccRecipients = bccRecipients; }
    }

    public static class BodyModel {
        private String contentType = "Text"; // Default to "Text", can be "Html"
        private String content;

        // Getters and Setters ...
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class RecipientModel {
        private EmailAddress emailAddress;

        public RecipientModel() {}
        public RecipientModel(EmailAddress emailAddress) { this.emailAddress = emailAddress; }

        // Getters and Setters ...
        public EmailAddress getEmailAddress() { return emailAddress; }
        public void setEmailAddress(EmailAddress emailAddress) { this.emailAddress = emailAddress; }
    }

    // --- Top-Level Getters and Setters ---

    public String getPreferredProtocol() { return preferredProtocol; }
    public void setPreferredProtocol(String preferredProtocol) { this.preferredProtocol = preferredProtocol; }
    public boolean isRequestPixelTracking() { return requestPixelTracking; }
    public void setRequestPixelTracking(boolean requestPixelTracking) { this.requestPixelTracking = requestPixelTracking; }
    public String getTrackingID() { return trackingID; }
    public void setTrackingID(String trackingID) { this.trackingID = trackingID; }
    public MessageModel getMessage() { return message; }
    public void setMessage(MessageModel message) { this.message = message; }
    public Boolean getSaveToSentItems() { return saveToSentItems; }
    public void setSaveToSentItems(Boolean saveToSentItems) { this.saveToSentItems = saveToSentItems; }
}
