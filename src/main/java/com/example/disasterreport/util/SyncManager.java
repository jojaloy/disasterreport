package com.example.disasterreport.util;

import com.example.disasterreport.model.Incident;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class SyncManager {
    private static SyncManager instance;
    private Thread backgroundSyncThread;
    private static final String CACHE_FILE = "offline_cache.dat";

    private SyncManager() {}

    public static SyncManager getInstance() {
        if (instance == null) instance = new SyncManager();
        return instance;
    }

    // --- PART 4: Raw Thread Implementation ---
    public void startAutoSync() {
        backgroundSyncThread = new Thread(() -> {
            while (true) {
                try {
                    if (isInternetAvailable()) syncPendingData();
                    Thread.sleep(15000); // Sleep for 15 seconds
                } catch (InterruptedException e) {
                    System.out.println("Sync thread stopped.");
                    break;
                }
            }
        });
        backgroundSyncThread.setDaemon(true); // Kills thread when app closes
        backgroundSyncThread.start();
    }

    public static boolean isInternetAvailable() {
        try {
            URLConnection connection = new URL("http://www.google.com").openConnection();
            connection.connect();
            return true;
        } catch (Exception e) { return false; }
    }

    // --- PART 3: Serialization Implementation ---
    public void queueLocally(Incident inc) {
        List<Incident> pending = getLocalIncidents();
        pending.add(inc);
        saveLocalIncidents(pending);
        System.out.println("Saved to offline .dat file.");
    }

    @SuppressWarnings("unchecked")
    private List<Incident> getLocalIncidents() {
        File file = new File(CACHE_FILE);
        if (!file.exists()) return new ArrayList<>();

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (List<Incident>) in.readObject();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private void saveLocalIncidents(List<Incident> incidents) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
            out.writeObject(incidents);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void syncPendingData() {
        List<Incident> offlineReports = getLocalIncidents();
        if (offlineReports.isEmpty()) return;

        try {
            for (Incident inc : offlineReports) {
                DatabaseManager.getInstance().saveIncident(inc);
            }
            new File(CACHE_FILE).delete(); // Wipe cache on successful upload
            System.out.println("Successfully synced offline data to MySQL!");
        } catch (Exception e) {
            // If DB fails, do nothing. Data stays in the .dat file.
        }
    }
}