package com.example.todofamily;

public class Task {
    private String id;
    private String title;
    private String description;
    private boolean completed;
    private long dueDate; // Таймстамп дедлайна

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
}