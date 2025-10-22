package com.edu.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity representing an individual email copy sent to a single recipient.
 * This entity stores the initial send details and updates the open tracking metrics.
 */
@Entity
@Table(name = "email_tracking")
public class EmailTrackingEntity {

    // The unique ID embedded in the tracking pixel. This is the primary key.
    @Id
    @Column(name = "tracking_id", length = 255)
    private String trackingId;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "batch_id", length = 255)
    private String batchId;

    @Column(name = "sent_timestamp")
    private LocalDateTime sentTimestamp;

    @Column(name = "open_count")
    private int openCount = 0;

    @Column(name = "first_open_timestamp")
    private LocalDateTime firstOpenTimestamp;

    @Column(name = "last_open_timestamp")
    private LocalDateTime lastOpenTimestamp;

    // --- CRITICAL FIXES FOR TRUNCATION ---
    // User-Agent string can be > 255 chars, 512 is safe, or TEXT is safer.
    // Use length=512 to match your successful manual DDL.
    @Column(name = "client_user_agent", length = 512)
    private String clientUserAgent;

    @Column(name = "client_browser", length = 512)
    private String clientBrowser;

    @Column(name = "client_device", length = 512)
    private String clientDevice;

    // IP address needs 45 for IPv6
    @Column(name = "client_ip_address", length = 45)
    private String clientIpAddress;

    // Geo-location fields
    @Column(name = "client_city", length = 100)
    private String clientCity;

    @Column(name = "client_country", length = 100)
    private String clientCountry;
    // --- Constructors ---

    public EmailTrackingEntity() {}

    // Constructor for initial record creation
    public EmailTrackingEntity(String trackingId, String recipientEmail, String batchId, LocalDateTime sentTimestamp) {
        this.trackingId = trackingId;
        this.recipientEmail = recipientEmail;
        this.batchId = batchId;
        this.sentTimestamp = sentTimestamp;
        this.openCount = 0;
    }

    // --- Getters and Setters ---

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public LocalDateTime getSentTimestamp() {
        return sentTimestamp;
    }

    public void setSentTimestamp(LocalDateTime sentTimestamp) {
        this.sentTimestamp = sentTimestamp;
    }

    public int getOpenCount() {
        return openCount;
    }

    public void setOpenCount(int openCount) {
        this.openCount = openCount;
    }

    public LocalDateTime getFirstOpenTimestamp() {
        return firstOpenTimestamp;
    }

    public void setFirstOpenTimestamp(LocalDateTime firstOpenTimestamp) {
        this.firstOpenTimestamp = firstOpenTimestamp;
    }

    public LocalDateTime getLastOpenTimestamp() {
        return lastOpenTimestamp;
    }

    public void setLastOpenTimestamp(LocalDateTime lastOpenTimestamp) {
        this.lastOpenTimestamp = lastOpenTimestamp;
    }

    public String getClientIpAddress() {
        return clientIpAddress;
    }
    public void setClientIpAddress(String clientIpAddress) {
        this.clientIpAddress = clientIpAddress;
    }
    public String getClientUserAgent() {
        return clientUserAgent;
    }
    public void setClientUserAgent(String clientUserAgent) {
        this.clientUserAgent = clientUserAgent;
    }
    public String getClientBrowser() {
        return clientBrowser;
    }
    public void setClientBrowser(String clientBrowser) {
        this.clientBrowser = clientBrowser;
    }
    public String getClientDevice() {
        return clientDevice;
    }
    public void setClientDevice(String clientDevice) {
        this.clientDevice = clientDevice;

    }
    public String getClientCity() {
        return clientCity;
    }
    public void setClientCity(String clientCity) {
        this.clientCity = clientCity;
    }
    public String getClientCountry() {
        return clientCountry;
    }
    public void setClientCountry(String clientCountry) {
        this.clientCountry = clientCountry;
    }
}
