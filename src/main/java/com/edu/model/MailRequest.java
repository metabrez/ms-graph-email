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

    public MailRequest() {
        // Default constructor
    }

    public MailRequest(String subject, String bodyContent, String bodyContentType,
                       List<EmailAddress> toRecipients, List<EmailAddress> ccRecipients,
                       List<EmailAddress> bccRecipients) {
        this.subject = subject;
        this.bodyContent = bodyContent;
        this.bodyContentType = bodyContentType;
        this.toRecipients = toRecipients;
        this.ccRecipients = ccRecipients;
        this.bccRecipients = bccRecipients;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailRequest that = (MailRequest) o;
        return Objects.equals(subject, that.subject) &&
                Objects.equals(bodyContent, that.bodyContent) &&
                Objects.equals(bodyContentType, that.bodyContentType) &&
                Objects.equals(toRecipients, that.toRecipients) &&
                Objects.equals(ccRecipients, that.ccRecipients) &&
                Objects.equals(bccRecipients, that.bccRecipients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, bodyContent, bodyContentType, toRecipients, ccRecipients, bccRecipients);
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
                '}';
    }

}
