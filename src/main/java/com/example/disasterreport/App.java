package com.example.disasterreport;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.example.disasterreport.util.DatabaseManager;

public class App extends Application {

    // Minimum usable size — all panels fit comfortably at this resolution
    private static final double MIN_WIDTH  = 1024;
    private static final double MIN_HEIGHT = 680;

    @Override
    public void start(Stage stage) throws Exception {
        // Initialise DB before any view loads
        DatabaseManager.getInstance();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/example/disasterreport/LoginView.fxml")
        );

        // Login card is compact — 480×600 is fine
        Scene scene = new Scene(loader.load(), 480, 600);
        stage.setTitle("Disaster Report System – Login");
        stage.setScene(scene);

        // Enforce a minimum so controls never get squashed
        stage.setMinWidth(480);
        stage.setMinHeight(600);

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Call this after login to resize the window for the main dashboard.
     * Pass the existing Stage reference from LoginController.
     */
    public static void resizeForMain(Stage stage) {
        stage.setWidth(MIN_WIDTH);
        stage.setHeight(MIN_HEIGHT);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.centerOnScreen();
    }
}