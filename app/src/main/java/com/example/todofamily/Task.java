package com.example.todofamily;

public class Task {
    private String id;
    private String title;
    private String description;
    private boolean completed;
    private long dueDate;
    private String assignedBy;
    private String assignedTo;
    private String assignedByName;
    private String imageUrl;
    private boolean photoRequired;
    private int status; // 0: PENDING, 1: WAITING_APPROVAL, 2: REJECTED, 3: COMPLETED
    private String rejectionComment;
    private int repeatType; // 0: None, 1: Daily, 2: Weekly, 3: Monthly

    public Task() {
        // Пустой конструктор для Firebase
    }

    public Task(String id, String title, String description, boolean completed, long dueDate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.dueDate = dueDate;
    }

    // Полный конструктор для назначений
    public Task(String id, String title, String description, boolean completed, long dueDate, String assignedBy, String assignedTo, String assignedByName) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.dueDate = dueDate;
        this.assignedBy = assignedBy;
        this.assignedTo = assignedTo;
        this.assignedByName = assignedByName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public long getDueDate() { return dueDate; }
    public void setDueDate(long dueDate) { this.dueDate = dueDate; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getAssignedByName() { return assignedByName; }
    public void setAssignedByName(String assignedByName) { this.assignedByName = assignedByName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public boolean isPhotoRequired() { return photoRequired; }
    public void setPhotoRequired(boolean photoRequired) { this.photoRequired = photoRequired; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getRejectionComment() { return rejectionComment; }
    public void setRejectionComment(String rejectionComment) { this.rejectionComment = rejectionComment; }

    public int getRepeatType() { return repeatType; }
    public void setRepeatType(int repeatType) { this.repeatType = repeatType; }
}