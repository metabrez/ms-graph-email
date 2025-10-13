package com.edu.model;

import java.util.List;
import java.util.Objects;

public class MailRequest {
    private String subject;
    private String bodyContent;
    private String bodyContentType = "Text"; // Default to "Text", can be "Html"
    private java.util.List<EmailAddress> toRecipients; // Explicitly use java.util.List
    private java.util.List<EmailAddress> ccRecipients; // Optional, Explicitly use java.util.List
    private java.util.List<EmailAddress> bccRecipients; // Optional, Explicitly use java.util.List
    // New field to specify preferred sending protocol (e.g., "MSGRAPH", "SMTP")
    private String preferredProtocol = "MSGRAPH";
    private String trackingID ;
    private boolean requestPixelTracking = false;


    public MailRequest() {
        // Default constructor
    }

    public MailRequest(String subject, String bodyContent, String bodyContentType,
                       List<EmailAddress> toRecipients, List<EmailAddress> ccRecipients,
                       List<EmailAddress> bccRecipients,  String preferredProtocol,  String trackingID,  boolean requestPixelTracking) {
        this.subject = subject;
        this.bodyContent = bodyContent;
        this.bodyContentType = bodyContentType;
        this.toRecipients = toRecipients;
        this.ccRecipients = ccRecipients;
        this.bccRecipients = bccRecipients;
        this.preferredProtocol = preferredProtocol;
        this.trackingID = trackingID;
        this.requestPixelTracking = requestPixelTracking;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyContent() {
        return bodyContent;
    }

    public void setBodyContent(String bodyContent) {
        this.bodyContent = bodyContent;
    }

    public String getBodyContentType() {
        return bodyContentType;
    }

    public void setBodyContentType(String bodyContentType) {
        this.bodyContentType = bodyContentType;
    }

    public java.util.List<EmailAddress> getToRecipients() {
        return toRecipients;
    }

    public void setToRecipients(java.util.List<EmailAddress> toRecipients) {
        this.toRecipients = toRecipients;
    }

    public java.util.List<EmailAddress> getCcRecipients() {
        return ccRecipients;
    }

    public void setCcRecipients(java.util.List<EmailAddress> ccRecipients) {
        this.ccRecipients = ccRecipients;
    }

    public java.util.List<EmailAddress> getBccRecipients() {
        return bccRecipients;
    }

    public void setBccRecipients(java.util.List<EmailAddress> bccRecipients) {
        this.bccRecipients = bccRecipients;
    }

    public String getPreferredProtocol() {
        return preferredProtocol;
    }

    public void setPreferredProtocol(String preferredProtocol) {
        this.preferredProtocol = preferredProtocol;
    }

    public String getTrackingID() {
        return trackingID;
    }
    public void setTrackingID(String trackingID) {
        this.trackingID = trackingID;
    }
    public boolean isRequestPixelTracking() {
        return requestPixelTracking;
    }
    public void setRequestPixelTracking(boolean requestPixelTracking) {
        this.requestPixelTracking = requestPixelTracking;
    }


    @Override
    public String toString() {
        return "MailRequest{" +
                "subject='" + subject + '\'' +
                ", bodyContent='" + bodyContent + '\'' +
                ", bodyContentType='" + bodyContentType + '\'' +
                ", toRecipients=" + toRecipients +
                ", ccRecipients=" + ccRecipients +
                ", bccRecipients=" + bccRecipients +
                ", preferredProtocol='" + preferredProtocol + '\'' +
                ", trackingID='" + trackingID + '\'' +
                ", requestPixelTracking=" + requestPixelTracking +
                '}';
    }

}
