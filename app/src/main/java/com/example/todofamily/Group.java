package com.example.todofamily;

import java.util.Map;

public class Group {
    private String id;
    private String name;
    private String admin;
    private Map<String, Boolean> members;

    public Group() {}

    public Group(String id, String name, String admin, Map<String, Boolean> members) {
        this.id = id;
        this.name = name;
        this.admin = admin;
        this.members = members;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAdmin() { return admin; }
    public void setAdmin(String admin) { this.admin = admin; }

    public Map<String, Boolean> getMembers() { return members; }
    public void setMembers(Map<String, Boolean> members) { this.members = members; }
}