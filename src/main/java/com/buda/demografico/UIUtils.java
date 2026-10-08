package com.buda.demografico;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/**
 * Utility class for common UI dialogs
 */
public class UIUtils {

    public static void showError(String englishMessage, String portugueseMessage) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error / Erro");
        alert.setHeaderText(englishMessage);
        alert.setContentText(portugueseMessage);
        alert.showAndWait();
    }

    public static void showInfo(String englishMessage, String portugueseMessage) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information / Informação");
        alert.setHeaderText(englishMessage);
        alert.setContentText(portugueseMessage);
        alert.showAndWait();
    }

    public static boolean showConfirmation(String englishMessage, String portugueseMessage) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation / Confirmação");
        alert.setHeaderText(englishMessage);
        alert.setContentText(portugueseMessage);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
}
