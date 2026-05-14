package com.example.disasterreport.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

public class HybridGeocoder {

    private static final String GEO_CACHE_DB = "jdbc:sqlite:geocache.db";

    public HybridGeocoder() {
        try (Connection conn = DriverManager.getConnection(GEO_CACHE_DB);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS addresses (id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL, lng REAL, address TEXT)");
        } catch (Exception e) { e.printStackTrace(); }
    }

    public CompletableFuture<String> getAddress(double lat, double lng) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String urlStr = String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%f&lon=%f", lat, lng);
                URL url = new URL(urlStr);

                // Using legacy HttpURLConnection to bypass modern Java 11 SSL/Proxy quirks on Mac
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "DisasterReportApp/1.0 (student@university.edu)");
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         Scanner scanner = new Scanner(in, "UTF-8")) {
                        String response = scanner.useDelimiter("\\A").next();
                        String address = extractJsonValue(response, "\"display_name\":\"");
                        if (address != null && !address.isEmpty()) {
                            cacheAddressOffline(lat, lng, address);
                            return address;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Online geocoding failed: " + e.getMessage());
            }
            return searchOfflineDatabase(lat, lng);
        });
    }

    private void cacheAddressOffline(double lat, double lng, String address) {
        String sql = "INSERT INTO addresses (lat, lng, address) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(GEO_CACHE_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, lat); ps.setDouble(2, lng); ps.setString(3, address);
            ps.executeUpdate();
        } catch (Exception e) {}
    }

    private String searchOfflineDatabase(double lat, double lng) {
        String sql = "SELECT address, ((lat - ?) * (lat - ?) + (lng - ?) * (lng - ?)) AS distance FROM addresses ORDER BY distance ASC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(GEO_CACHE_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, lat); ps.setDouble(2, lat); ps.setDouble(3, lng); ps.setDouble(4, lng);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getDouble("distance") < 0.01) {
                    return "[Offline Cached] " + rs.getString("address");
                }
            }
        } catch (Exception e) {}

        return String.format(Locale.US, "GPS: %.5f, %.5f", lat, lng);
    }

    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf(key);
        if (idx == -1) return null;
        int start = idx + key.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end).replace("\\\"", "\"");
    }
}