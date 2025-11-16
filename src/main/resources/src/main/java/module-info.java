module com.example.glossariobuda {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;
    requires javafx.base;
    requires javafx.swing;
    requires javafx.web;
    requires java.desktop;
    requires java.base;
    requires java.net.http;
    requires java.sql;
    requires jdk.jsobject;
    requires org.java_websocket;


    // External libraries as automatic modules
    requires org.apache.pdfbox;

    // SWT for better IME support (as unnamed module)
    requires org.eclipse.swt;
    requires org.eclipse.swt.win32.win32.x86_64;
    requires com.google.gson;
    requires jdk.jdi;
    requires org.json;
    requires com.ibm.icu;

    // Wylie to Tibetan Unicode converter (automatic module name from jar)
    requires ewts.converter;

    opens com.example.glossariobuda to javafx.fxml;
    exports com.example.glossariobuda;
}