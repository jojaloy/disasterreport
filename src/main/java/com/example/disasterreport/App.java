package com.example.disasterreport;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.example.disasterreport.util.DatabaseManager;
import com.example.disasterreport.util.OfflineTileServer;
import com.example.disasterreport.util.SyncManager;

import java.io.File;

public class App extends Application {

    private static final double MIN_WIDTH  = 1024;
    private static final double MIN_HEIGHT = 680;

    private OfflineTileServer tileServer;
    private SyncManager syncManager;

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseManager.getInstance();

        // Ensure offline_tiles folder exists so the server doesn't crash
        new File("offline_tiles").mkdirs();

        // Start background synchronization & offline map servers
        syncManager = new SyncManager();
        syncManager.startAutoSync();

        tileServer = new OfflineTileServer();
        tileServer.startServer();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/disasterreport/LoginView.fxml"));
        Scene scene = new Scene(loader.load(), 480, 600);
        stage.setTitle("Disaster Report System – Login");
        stage.setScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(600);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (tileServer != null) tileServer.stopServer();
        super.stop();
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        launch(args);
    }

    public static void resizeForMain(Stage stage) {
        stage.setWidth(MIN_WIDTH); stage.setHeight(MIN_HEIGHT);
        stage.setMinWidth(MIN_WIDTH); stage.setMinHeight(MIN_HEIGHT);
        stage.centerOnScreen();
    }
}