package com.example.disasterreport.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Optional;

public class ModernDialog {

    private static Stage createBaseStage(String titleText, VBox contentBox) {
        Stage stage = new Stage();

        Window owner = Window.getWindows().stream()
                .filter(Window::isFocused).findFirst()
                .orElseGet(() -> Window.getWindows().stream().findFirst().orElse(null));

        if (owner != null) stage.initOwner(owner);

        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        VBox container = new VBox(16);
        container.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 24; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0, 0, 8);");
        container.setMinWidth(340);

        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f1623; -fx-font-family: 'Segoe UI', Arial, sans-serif;");

        container.getChildren().add(title);
        container.getChildren().addAll(contentBox.getChildren());

        StackPane root = new StackPane(container);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();

        return stage;
    }

    public static void showMessage(String title, String message, boolean isError) {
        VBox content = new VBox(20);
        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #374151; -fx-font-family: 'Segoe UI', Arial, sans-serif;");

        Button btn = new Button("OK");
        String btnColor = isError ? "#dc2626" : "#e8550a";
        btn.setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 24;");

        HBox btnBox = new HBox(btn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(msgLabel, btnBox);
        Stage stage = createBaseStage(title, content);
        btn.setOnAction(e -> stage.close());
        stage.showAndWait();
    }

    // NEW: Image Viewer Popup
    public static void showImagePopup(String title, Image image) {
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);

        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        // Constrain the size so it fits nicely on screen
        imageView.setFitWidth(600);
        imageView.setFitHeight(450);
        imageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 4);");

        Button btn = new Button("Close Image");
        btn.setStyle("-fx-background-color: #e8550a; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 24;");

        content.getChildren().addAll(imageView, btn);
        Stage stage = createBaseStage(title, content);
        btn.setOnAction(e -> stage.close());
        stage.showAndWait();
    }

    public static Optional<String> showChoice(String title, String message, String[] choices) {
        VBox content = new VBox(16);
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #374151; -fx-font-family: 'Segoe UI', Arial, sans-serif;");

        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(choices);
        combo.getSelectionModel().selectFirst();
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-padding: 4; -fx-font-size: 14px;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: white; -fx-text-fill: #374151; -fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-cursor: hand; -fx-padding: 8 16;");
        Button confirmBtn = new Button("Confirm");
        confirmBtn.setStyle("-fx-background-color: #e8550a; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 16;");

        HBox btnBox = new HBox(12, cancelBtn, confirmBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        content.getChildren().addAll(msgLabel, combo, btnBox);
        Stage stage = createBaseStage(title, content);

        final String[] result = {null};
        cancelBtn.setOnAction(e -> stage.close());
        confirmBtn.setOnAction(e -> { result[0] = combo.getValue(); stage.close(); });
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    public static Optional<String> showTextInput(String title, String message, String defaultValue) {
        VBox content = new VBox(16);
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #374151; -fx-font-family: 'Segoe UI', Arial, sans-serif;");

        TextField input = new TextField(defaultValue);
        input.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-padding: 8; -fx-font-size: 14px;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: white; -fx-text-fill: #374151; -fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-cursor: hand; -fx-padding: 8 16;");
        Button confirmBtn = new Button("Submit");
        confirmBtn.setStyle("-fx-background-color: #e8550a; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 16;");

        HBox btnBox = new HBox(12, cancelBtn, confirmBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        content.getChildren().addAll(msgLabel, input, btnBox);
        Stage stage = createBaseStage(title, content);

        final String[] result = {null};
        cancelBtn.setOnAction(e -> stage.close());
        confirmBtn.setOnAction(e -> { result[0] = input.getText(); stage.close(); });
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }
}