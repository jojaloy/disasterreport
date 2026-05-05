package com.example.disasterreport.model;

import com.example.disasterreport.util.DatabaseManager;
import java.io.Serializable;
import java.time.LocalDate;

public class Incident implements Serializable {
    private static final long serialVersionUID = 1L;

    private int       incidentID;
    private String    type;
    private String    location;
    private String    description;
    private LocalDate date;
    private String    status;
    private String    severity;
    private String    reportedBy;

    // Map coordinates — 0.0 / 0.0 means "not yet geocoded"
    private double latitude;
    private double longitude;

    // ── Full constructor (used when reading from DB) ───────────────────────
    public Incident(int incidentID, String type, String location, String description,
                    LocalDate date, String status, String severity, String reportedBy,
                    double latitude, double longitude) {
        this.incidentID  = incidentID;
        this.type        = type;
        this.location    = location;
        this.description = description;
        this.date        = date;
        this.status      = status;
        this.severity    = severity;
        this.reportedBy  = reportedBy;
        this.latitude    = latitude;
        this.longitude   = longitude;
    }

    /**
     * Legacy constructor (no coordinates) — keeps existing code compiling.
     * Defaults lat/lng to 0.0.
     */
    public Incident(int incidentID, String type, String location, String description,
                    LocalDate date, String status, String severity, String reportedBy) {
        this(incidentID, type, location, description,
                date, status, severity, reportedBy, 0.0, 0.0);
    }

    // ── Business methods ──────────────────────────────────────────────────
    public void report() {
        DatabaseManager.getInstance().saveIncident(this);
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus;
        DatabaseManager.getInstance().updateIncidentStatus(this.incidentID, newStatus);
    }

    // Setter needed by IncidentListController
    public void setStatus(String status) { this.status = status; }

    // ── Getters ───────────────────────────────────────────────────────────
    public int       getIncidentID()  { return incidentID; }
    public String    getType()        { return type; }
    public String    getLocation()    { return location; }
    public String    getDescription() { return description; }
    public LocalDate getDate()        { return date; }
    public String    getStatus()      { return status; }
    public String    getSeverity()    { return severity; }
    public String    getReportedBy()  { return reportedBy; }
    public double    getLatitude()    { return latitude; }
    public double    getLongitude()   { return longitude; }

    public String getDateString() {
        return date != null ? date.toString() : "";
    }
}