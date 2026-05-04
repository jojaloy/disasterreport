package com.example.disasterreport.model;

import com.example.disasterreport.util.DatabaseManager;
import java.io.Serializable;
import java.time.LocalDate;

public class Incident implements Serializable {
    private static final long serialVersionUID = 1L;

    private int incidentID;
    private String type;
    private String location;
    private String description;
    private LocalDate date;
    private String status;
    private String severity;
    private String reportedBy;

    public Incident(int incidentID, String type, String location, String description,
                    LocalDate date, String status, String severity, String reportedBy) {
        this.incidentID = incidentID;
        this.type = type;
        this.location = location;
        this.description = description;
        this.date = date;
        this.status = status;
        this.severity = severity;
        this.reportedBy = reportedBy;
    }

    public void report() {
        DatabaseManager.getInstance().saveIncident(this);
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus;
        DatabaseManager.getInstance().updateIncidentStatus(this.incidentID, newStatus);
    }

    // REQUIRED GETTERS (must match PropertyValueFactory names)
    public int getIncidentID() { return incidentID; }
    public String getType() { return type; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public LocalDate getDate() { return date; }
    public String getStatus() { return status; }
    public String getSeverity() { return severity; }
    public String getReportedBy() { return reportedBy; }

    // Safe UI helper (optional)
    public String getDateString() {
        return date != null ? date.toString() : "";
    }
}