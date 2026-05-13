module com.example.disasterreport {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;       // ← required for WebView / WebEngine
    requires java.sql;
    requires jdk.jsobject;
    requires jdk.httpserver;
    requires java.net.http;

    opens com.example.disasterreport            to javafx.fxml;
    opens com.example.disasterreport.controller to javafx.fxml;
    opens com.example.disasterreport.model      to javafx.base;
    opens com.example.disasterreport.util       to javafx.fxml;

    exports com.example.disasterreport;
}