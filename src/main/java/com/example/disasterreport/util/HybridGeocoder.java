package com.example.disasterreport.util;

import javafx.application.Platform;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;
import java.util.function.Consumer;

public class HybridGeocoder {

    private static final String GEO_CACHE_DB = "jdbc:sqlite:geocache.db";

    // --- 1. THE THREAD MANAGER ---
    public void getAddress(double lat, double lng, Consumer<String> callback) {
        Thread fetchThread = new Thread(() -> {
            String address;

            if (SyncManager.isInternetAvailable()) {
                // 1. Try to fetch online
                address = fetchFromOnlineAPI(lat, lng);

                // 2. If successful, cache it for later!
                if (address != null && !address.equals("Unknown Location")) {
                    cacheAddressOffline(lat, lng, address);
                } else {
                    // API failed (e.g., server down), fallback to offline cache
                    address = searchOfflineDatabase(lat, lng);
                }
            } else {
                // No Wi-Fi, go straight to offline cache
                address = searchOfflineDatabase(lat, lng);
            }

            // 3. Safe hand-off to the JavaFX UI Thread
            String finalAddress = address;
            Platform.runLater(() -> {
                callback.accept(finalAddress);
            });
        });

        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    // --- 2. THE ONLINE HTTP WORKER ---
    private String fetchFromOnlineAPI(double lat, double lng) {
        try {
            String urlString = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lng;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // FIX: Use a unique email address so OpenStreetMap doesn't flag you as a bot!
            conn.setRequestProperty("User-Agent", "DisasterReportSystem/1.0 (jojaloy.capstone@gmail.com)");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream();
                     Scanner scanner = new Scanner(in, "UTF-8")) {
                    String response = scanner.useDelimiter("\\A").next();

                    // Extract the highly specific "display_name" property
                    String address = extractJsonValue(response, "\"display_name\":\"");
                    if (address != null && !address.isEmpty()) {
                        return address;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Online geocoding failed: " + e.getMessage());
        }
        return "Unknown Location"; // Fallback if the HTTP connection completely fails
    }

    // --- 3. THE JSON EXTRACTOR ---
    private String extractJsonValue(String json, String key) {
        int startIndex = json.indexOf(key);
        if (startIndex == -1) return null;
        startIndex += key.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return null;
        return json.substring(startIndex, endIndex);
    }

    // --- 4. THE OFFLINE SQL WORKERS ---
    private void cacheAddressOffline(double lat, double lng, String address) {
        String sql = "INSERT OR REPLACE INTO address_cache (lat, lng, address) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(GEO_CACHE_DB);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, lat);
            pstmt.setDouble(2, lng);
            pstmt.setString(3, address);
            pstmt.executeUpdate();

        } catch (Exception e) {
            System.err.println("Failed to cache address: " + e.getMessage());
        }
    }

    private String searchOfflineDatabase(double lat, double lng) {
        String sql = "SELECT address FROM address_cache WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?";
        try (Connection conn = DriverManager.getConnection(GEO_CACHE_DB);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Search within a small radius (~100 meters)
            pstmt.setDouble(1, lat - 0.001);
            pstmt.setDouble(2, lat + 0.001);
            pstmt.setDouble(3, lng - 0.001);
            pstmt.setDouble(4, lng + 0.001);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("address") + " (Offline Mode)";
            }
        } catch (Exception e) {
            System.err.println("Offline DB Search failed: " + e.getMessage());
        }
        return "Unknown Location (Offline)";
    }
}