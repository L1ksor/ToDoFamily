package com.example.todofamily;

public class Notification {
    private String id;
    private String title;
    private String message;
    private long timestamp;
    private String type; // e.g., "TASK_COMPLETED", "TASK_REJECTED"

    public Notification() {}

    public Notification(String id, String title, String message, long timestamp, String type) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}