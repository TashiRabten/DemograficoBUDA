module com.buda.demografico {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.sql;
    requires java.net.http;
    requires java.desktop;
    requires java.prefs;
    requires com.google.gson;

    exports com.buda.demografico;
    opens com.buda.demografico to javafx.fxml;
    opens icons;
    opens images;
}
