package com.example.disasterreport.util;

import com.example.disasterreport.model.Incident;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ReportGenerator {

    private List<Incident> reportData;

    public ReportGenerator(List<Incident> data) {
        this.reportData = data;
    }

    public void generateSummary() {
        long active   = reportData.stream().filter(i -> "Active".equals(i.getStatus())).count();
        long resolved = reportData.stream().filter(i -> "Resolved".equals(i.getStatus())).count();
        System.out.println("Total Incidents : " + reportData.size());
        System.out.println("Active          : " + active);
        System.out.println("Resolved        : " + resolved);
    }

    public Map<String, Long> countByType() {
        return reportData.stream()
                .collect(Collectors.groupingBy(Incident::getType, Collectors.counting()));
    }

    // Serialization — saves the list to a .ser file
    public void exportReport(String filename) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(reportData);
            System.out.println("Report exported to: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Deserialization — reads the list back from a .ser file
    public List<Incident> importReport(String filename) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            return (List<Incident>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}