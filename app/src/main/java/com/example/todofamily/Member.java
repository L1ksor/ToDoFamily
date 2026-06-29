package com.example.todofamily;

public class Member {
    private String uid;
    private String username;
    private String email;
    private String avatarUrl;

    public Member() {
    }

    public Member(String uid, String username, String email) {
        this.uid = uid;
        this.username = username;
        this.email = email;
    }

    public Member(String uid, String username, String email, String avatarUrl) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}