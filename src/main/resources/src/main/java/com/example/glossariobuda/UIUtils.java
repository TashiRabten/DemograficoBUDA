package com.example.glossariobuda;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

/**
 * UI Utilities for Glossário BUDA
 * Provides consistent dialog and alert functionality
 */
public class UIUtils {

    /**
     * Shows error dialog with bilingual message
     */
    public static void showError(String englishMessage, String portugueseMessage) {
        String message = createBilingualError(englishMessage, portugueseMessage);
        showAlert(AlertType.ERROR, "Error / Erro", message);
    }

    /**
     * Shows error dialog with single message
     */
    public static void showError(String message) {
        showAlert(AlertType.ERROR, "Error / Erro", message);
    }

    /**
     * Shows success dialog with bilingual message
     */
    public static void showSuccess(String englishMessage, String portugueseMessage) {
        String message = createBilingualSuccess(englishMessage, portugueseMessage);
        showAlert(AlertType.INFORMATION, "Success / Sucesso", message);
    }

    /**
     * Shows success dialog with single message
     */
    public static void showSuccess(String message) {
        showAlert(AlertType.INFORMATION, "Success / Sucesso", message);
    }

    /**
     * Shows warning dialog with bilingual message
     */
    public static void showWarning(String englishMessage, String portugueseMessage) {
        String message = createBilingualWarning(englishMessage, portugueseMessage);
        showAlert(AlertType.WARNING, "Warning / Aviso", message);
    }

    /**
     * Shows warning dialog with single message
     */
    public static void showWarning(String message) {
        showAlert(AlertType.WARNING, "Warning / Aviso", message);
    }

    /**
     * Shows info dialog with bilingual message
     */
    public static void showInfo(String englishMessage, String portugueseMessage) {
        String message = createBilingualInfo(englishMessage, portugueseMessage);
        showAlert(AlertType.INFORMATION, "Info / Informação", message);
    }

    /**
     * Shows info dialog with single message
     */
    public static void showInfo(String message) {
        showAlert(AlertType.INFORMATION, "Info / Informação", message);
    }

    /**
     * Shows confirmation dialog with bilingual message
     */
    public static boolean showConfirmation(String englishTitle, String portugueseTitle,
                                         String englishMessage, String portugueseMessage) {
        String title = englishTitle + " / " + portugueseTitle;
        String message = createBilingualInfo(englishMessage, portugueseMessage);
        return showConfirmationDialog(title, message);
    }

    /**
     * Shows confirmation dialog with single message
     */
    public static boolean showConfirmation(String title, String message) {
        return showConfirmationDialog(title, message);
    }

    // Helper methods
    private static void showAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static boolean showConfirmationDialog(String title, String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        return alert.showAndWait()
                   .filter(response -> response == ButtonType.OK)
                   .isPresent();
    }

    // Bilingual message creators
    private static String createBilingualError(String englishMessage, String portugueseMessage) {
        return "❌ " + englishMessage + "\n❌ " + portugueseMessage;
    }

    private static String createBilingualSuccess(String englishMessage, String portugueseMessage) {
        return "✅ " + englishMessage + "\n✅ " + portugueseMessage;
    }

    private static String createBilingualWarning(String englishMessage, String portugueseMessage) {
        return "⚠️ " + englishMessage + "\n⚠️ " + portugueseMessage;
    }

    private static String createBilingualInfo(String englishMessage, String portugueseMessage) {
        return "ℹ️ " + englishMessage + "\nℹ️ " + portugueseMessage;
    }
}