module com.example.disasterreport {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens com.example.disasterreport to javafx.fxml;
    opens com.example.disasterreport.controller to javafx.fxml;
    opens com.example.disasterreport.model to javafx.base;

    exports com.example.disasterreport;
}