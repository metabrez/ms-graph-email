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
    @Column(length = 255)
    private String trackingId;

    @Column(nullable = false, length = 255)
    private String recipientEmail;

    @Column(nullable = false, length = 36)
    private String batchId; // The ID of the overall send operation

    @Column(nullable = false)
    private LocalDateTime sentTimestamp;

    // Tracking metrics
    @Column(nullable = false)
    private int openCount = 0;

    private LocalDateTime firstOpenTimestamp;

    private LocalDateTime lastOpenTimestamp;

    @Column(length = 50)
    private String clientIpAddress;

    // We will store the raw User-Agent string
    @Column(length = 512)
    private String clientUserAgent;

    // Fields for derived data (e.g., from a User-Agent parser or GeoIP service)
    @Column(length = 50)
    private String clientBrowser;

    @Column(length = 50)
    private String clientDevice;

    // Fields for Geolocation (requires external GeoIP library to populate)
    @Column(length = 50)
    private String clientCity;

    @Column(length = 50)
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
