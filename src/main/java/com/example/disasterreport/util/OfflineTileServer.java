package com.example.disasterreport.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.Executors;

public class OfflineTileServer {
    private HttpServer server;
    private static final String CACHE_DIR = "offline_tiles";
    private static final String CARTO_URL = "https://a.basemaps.cartocdn.com/rastertiles/voyager/%d/%d/%d.png";
    private byte[] offlineTileCache = null; // Caches our generated offline image

    public void startServer() {
        try {
            new File(CACHE_DIR).mkdirs();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
            server.createContext("/tiles", this::handleTileRequest);

            server.setExecutor(Executors.newFixedThreadPool(16));
            server.start();
            System.out.println("Auto-Caching Map Server running on http://127.0.0.1:8080/tiles");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTileRequest(HttpExchange exchange) {
        try {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            String path = exchange.getRequestURI().getPath().replace("/tiles/", "").replace(".png", "");
            String[] parts = path.split("/");

            if (parts.length != 3) {
                sendPlaceholder(exchange);
                return;
            }

            int z = Integer.parseInt(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);

            File tileFile = new File(CACHE_DIR + "/" + z + "/" + x + "/" + y + ".png");

            // 1. If we have the tile saved offline, serve it!
            if (tileFile.exists() && tileFile.length() > 0) {
                serveFile(exchange, tileFile);
            } else {
                // 2. If missing, try to download it
                downloadAndServe(exchange, z, x, y, tileFile);
            }
        } catch (Exception e) {
            sendPlaceholder(exchange);
        }
    }

    private void downloadAndServe(HttpExchange exchange, int z, int x, int y, File tileFile) {
        try {
            URL url = new URL(String.format(CARTO_URL, z, x, y));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(2000); // Fail fast if network is unreachable
            conn.setReadTimeout(2000);

            if (conn.getResponseCode() == 200) {
                tileFile.getParentFile().mkdirs();
                try (InputStream in = conn.getInputStream();
                     OutputStream out = new FileOutputStream(tileFile)) {
                    in.transferTo(out);
                }
                serveFile(exchange, tileFile);
            } else {
                sendPlaceholder(exchange);
            }
        } catch (Exception e) {
            // "Network is unreachable" triggers this. Send the visual placeholder!
            sendPlaceholder(exchange);
        }
    }

    private void serveFile(HttpExchange exchange, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            sendPlaceholder(exchange);
        }
    }

    /**
     * Dynamically generates a beautiful "Offline" map tile instead of a broken gray square!
     */
    private void sendPlaceholder(HttpExchange exchange) {
        try {
            byte[] data = getOfflinePlaceholder();
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } catch (Exception e) {}
    }

    private byte[] getOfflinePlaceholder() {
        if (offlineTileCache != null) return offlineTileCache;
        try {
            BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = img.createGraphics();

            // Draw a light gray map grid
            g2d.setColor(new Color(235, 238, 240));
            g2d.fillRect(0, 0, 256, 256);
            g2d.setColor(new Color(200, 205, 210));
            g2d.drawRect(0, 0, 255, 255);

            // Draw the text
            g2d.setColor(new Color(107, 114, 128));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2d.drawString("No Internet", 85, 120);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2d.drawString("Map tile not cached", 75, 140);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            offlineTileCache = baos.toByteArray();
            return offlineTileCache;
        } catch (Exception e) { return new byte[0]; }
    }

    public void stopServer() {
        if (server != null) server.stop(0);
    }
}