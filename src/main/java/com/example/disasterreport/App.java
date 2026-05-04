package com.example.disasterreport;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.example.disasterreport.util.DatabaseManager;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Initialize DB connection before anything loads
        DatabaseManager.getInstance();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/example/disasterreport/LoginView.fxml")
        );
        Scene scene = new Scene(loader.load());
        stage.setTitle("Disaster Report System");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}