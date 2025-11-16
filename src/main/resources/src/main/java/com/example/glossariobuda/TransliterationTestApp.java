package com.example.glossariobuda;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TransliterationTestApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("TransliterationTest.fxml"));
        Scene scene = new Scene(loader.load());
        stage.setTitle("Sanskrit Transliteration Tester");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
