package com.example.disasterreport.model;

public class Request {
    private int requestID;
    private String username;
    private String type;
    private String details;
    private String status;

    public Request(int requestID, String username, String type, String details, String status) {
        this.requestID = requestID;
        this.username = username;
        this.type = type;
        this.details = details;
        this.status = status;
    }

    public int getRequestID() { return requestID; }
    public String getUsername() { return username; }
    public String getType() { return type; }
    public String getDetails() { return details; }
    public String getStatus() { return status; }
}