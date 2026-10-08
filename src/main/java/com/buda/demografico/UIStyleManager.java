package com.buda.demografico;

import javafx.scene.control.Button;

/**
 * Manager for creating styled buttons
 */
public class UIStyleManager {

    public enum ButtonType {
        PRIMARY,
        PROCESS,
        CANCEL,
        DEFAULT
    }

    public static Button createStyledButton(String text, ButtonType type) {
        Button button = new Button(text);

        switch (type) {
            case PRIMARY:
                button.setStyle("-fx-background-color: linear-gradient(to bottom, #27AE60, #229954); " +
                              "-fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                break;
            case PROCESS:
                button.setStyle("-fx-background-color: linear-gradient(to bottom, #3498DB, #2980B9); " +
                              "-fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                break;
            case CANCEL:
                button.setStyle("-fx-background-color: linear-gradient(to bottom, #E74C3C, #C0392B); " +
                              "-fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                break;
            default:
                button.setStyle("-fx-cursor: hand;");
                break;
        }

        return button;
    }
}
