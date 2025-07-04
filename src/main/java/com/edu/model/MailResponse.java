package com.edu.model;

import java.util.Objects;

public class MailResponse {

    private String messageId;
    private String status;
    private String message;

    public MailResponse() {
        // Default constructor
    }

    // Constructor for building a response
    public MailResponse(String messageId, String status, String message) {
        this.messageId = messageId;
        this.status = status;
        this.message = message;
    }

    // Builder-like pattern for convenience (optional, but good practice)
    public static MailResponseBuilder builder() {
        return new MailResponseBuilder();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailResponse that = (MailResponse) o;
        return Objects.equals(messageId, that.messageId) &&
                Objects.equals(status, that.status) &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, status, message);
    }

    @Override
    public String toString() {
        return "MailResponse{" +
                "messageId='" + messageId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    // Inner static class for builder pattern
    public static class MailResponseBuilder {
        private String messageId;
        private String status;
        private String message;

        public MailResponseBuilder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public MailResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public MailResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public MailResponse build() {
            return new MailResponse(messageId, status, message);
        }
    }
}
