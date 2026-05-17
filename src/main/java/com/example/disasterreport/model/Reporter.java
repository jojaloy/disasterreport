package com.example.disasterreport.model;

public class Reporter extends User {
    private int trustScore;

    public Reporter(int userID, String username, String password, int trustScore) {
        super(userID, username, password);
        this.trustScore = trustScore;
    }

    public int getTrustScore() { return trustScore; }

    @Override public String getRoleName() { return "reporter"; }
    @Override public boolean canUpdateIncidentStatus() { return false; }
    @Override public boolean canManageUsers() { return false; }
}