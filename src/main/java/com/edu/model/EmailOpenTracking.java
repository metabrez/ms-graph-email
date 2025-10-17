package com.edu.model;

import java.time.LocalDateTime;

public class EmailOpenTracking {
    private String trackingId;
    private int openCount;
    private LocalDateTime firstOpenTimestamp;
    private LocalDateTime lastOpenTimestamp;

    // AllArgsConstructor
    public EmailOpenTracking(String trackingId, int openCount, LocalDateTime firstOpenTimestamp, LocalDateTime lastOpenTimestamp) {
        this.trackingId = trackingId;
        this.openCount = openCount;
        this.firstOpenTimestamp = firstOpenTimestamp;
        this.lastOpenTimestamp = lastOpenTimestamp;
    }

    // Default Constructor (for potential deserialization)
    public EmailOpenTracking() {}

    // Getters
    public String getTrackingId() { return trackingId; }
    public int getOpenCount() { return openCount; }
    public LocalDateTime getFirstOpenTimestamp() { return firstOpenTimestamp; }
    public LocalDateTime getLastOpenTimestamp() { return lastOpenTimestamp; }

    // Setters (required for Map.compute update logic)
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
    public void setOpenCount(int openCount) { this.openCount = openCount; }
    public void setFirstOpenTimestamp(LocalDateTime firstOpenTimestamp) { this.firstOpenTimestamp = firstOpenTimestamp; }
    public void setLastOpenTimestamp(LocalDateTime lastOpenTimestamp) { this.lastOpenTimestamp = lastOpenTimestamp; }
}