package com.example.disasterreport.util;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;

public class OfflineTileServer {
    private HttpServer server;
    // Where you drop your offline MBTiles extracted PNGs:
    private static final String TILE_CACHE_DIR = "offline_tiles/";

    public void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/tiles", exchange -> {
                // Request URI: /tiles/z/x/y.png
                String path = exchange.getRequestURI().getPath().replace("/tiles/", "");
                File tileFile = new File(TILE_CACHE_DIR, path);

                // CORS headers for Leaflet
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

                if (tileFile.exists() && tileFile.isFile()) {
                    byte[] imageBytes = Files.readAllBytes(tileFile.toPath());
                    exchange.getResponseHeaders().add("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, imageBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(imageBytes);
                    }
                } else {
                    // Send 404 transparent 1x1 pixel or standard 404
                    exchange.sendResponseHeaders(404, -1);
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
            server.start();
            System.out.println("Offline Tile Server running on http://localhost:8080/tiles");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            System.out.println("Offline Tile Server stopped.");
        }
    }
}