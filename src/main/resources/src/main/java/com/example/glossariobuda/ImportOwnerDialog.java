package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dialog for selecting owner before importing a glossary
 * Shows smart defaults based on file metadata or filename
 */
public class ImportOwnerDialog {

    /**
     * Show the import owner selection dialog (thread-safe)
     * Can be called from any thread - will automatically run on FX thread
     *
     * @param fileName Name of the file being imported
     * @param suggestedOwner Owner extracted from file metadata (can be null)
     * @param currentUser Current user name (e.g., "tashi")
     * @param termCount Number of terms found in file
     * @return Selected owner, or null if cancelled
     */
    public static String show(String fileName, String suggestedOwner, String currentUser, int termCount) {
        // Check if we're already on FX thread
        if (Platform.isFxApplicationThread()) {
            ImportOwnerDialog dialog = new ImportOwnerDialog();
            return dialog.showDialog(fileName, suggestedOwner, currentUser, termCount);
        } else {
            // We're on a background thread - need to show dialog on FX thread and wait
            AtomicReference<String> result = new AtomicReference<>(null);
            CountDownLatch latch = new CountDownLatch(1);

            Platform.runLater(() -> {
                try {
                    ImportOwnerDialog dialog = new ImportOwnerDialog();
                    String owner = dialog.showDialog(fileName, suggestedOwner, currentUser, termCount);
                    result.set(owner);
                } finally {
                    latch.countDown();
                }
            });

            try {
                latch.await();  // Wait for dialog to complete
                return result.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private String showDialog(String fileName, String suggestedOwner, String currentUser, int termCount) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Import Glossary");
        dialog.setHeaderText("Assign Owner to Imported Terms");

        // Set the button types
        ButtonType importButtonType = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importButtonType, ButtonType.CANCEL);

        // Create the layout
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // File info section
        Label fileInfoLabel = new Label("File: " + fileName);
        fileInfoLabel.setStyle("-fx-font-weight: bold;");
        Label termCountLabel = new Label("Found: " + termCount + " terms");

        // Owner selection section
        Label ownerLabel = new Label("Assign owner to all terms:");
        ownerLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0;");

        ToggleGroup ownerGroup = new ToggleGroup();

        // Option 1: Current user
        RadioButton currentUserRadio = new RadioButton("Me (" + currentUser + ")");
        currentUserRadio.setToggleGroup(ownerGroup);

        // Option 2: Shared
        RadioButton sharedRadio = new RadioButton("Shared (accessible to all)");
        sharedRadio.setToggleGroup(ownerGroup);

        // Option 3: Custom
        VBox customBox = new VBox(5);
        RadioButton customRadio = new RadioButton("Custom:");
        customRadio.setToggleGroup(ownerGroup);
        TextField customOwnerField = new TextField();
        customOwnerField.setPromptText("Enter custom owner name");
        customOwnerField.setDisable(true);
        customOwnerField.setPrefWidth(250);

        // Enable/disable custom field based on radio selection
        customRadio.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            customOwnerField.setDisable(!isSelected);
            if (isSelected) {
                customOwnerField.requestFocus();
            }
        });

        customBox.getChildren().addAll(customRadio, customOwnerField);

        // Smart default selection
        String smartDefault = determineSmartDefault(fileName, suggestedOwner);
        if (suggestedOwner != null && !suggestedOwner.isEmpty()) {
            // File has explicit owner metadata
            customOwnerField.setText(suggestedOwner);
            customRadio.setSelected(true);
            customOwnerField.setDisable(false);
        } else if (smartDefault != null) {
            // Use smart default from filename
            if (smartDefault.equals("shared")) {
                sharedRadio.setSelected(true);
            } else if (smartDefault.equals(currentUser)) {
                currentUserRadio.setSelected(true);
            } else {
                customOwnerField.setText(smartDefault);
                customRadio.setSelected(true);
                customOwnerField.setDisable(false);
            }
        } else {
            // Default to current user
            currentUserRadio.setSelected(true);
        }

        // Note section
        Label noteLabel = new Label("Note: Individual contributors will be preserved for each term");
        noteLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray; -fx-padding: 10 0 0 0;");

        // Add all elements to content
        content.getChildren().addAll(
            fileInfoLabel,
            termCountLabel,
            ownerLabel,
            currentUserRadio,
            sharedRadio,
            customBox,
            noteLabel
        );

        dialog.getDialogPane().setContent(content);

        // Convert the result when import button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == importButtonType) {
                if (currentUserRadio.isSelected()) {
                    return currentUser;
                } else if (sharedRadio.isSelected()) {
                    return "shared";
                } else if (customRadio.isSelected()) {
                    String custom = customOwnerField.getText().trim();
                    return custom.isEmpty() ? currentUser : custom;
                }
            }
            return null;
        });

        // Show dialog and get result
        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    /**
     * Determine smart default owner from filename
     * Patterns:
     * - "84000" -> "84000_official"
     * - "shared" -> "shared"
     * - "tashi" -> "tashi"
     * - etc.
     */
    private String determineSmartDefault(String fileName, String suggestedOwner) {
        if (suggestedOwner != null && !suggestedOwner.isEmpty()) {
            return suggestedOwner;
        }

        String lowerFileName = fileName.toLowerCase();

        // Check for common patterns
        if (lowerFileName.contains("84000")) {
            return "84000_official";
        }
        if (lowerFileName.contains("shared") || lowerFileName.contains("public")) {
            return "shared";
        }
        if (lowerFileName.contains("dharma")) {
            return "dharma_dictionary";
        }
        if (lowerFileName.contains("rangjung")) {
            return "rangjung_yeshe";
        }
        if (lowerFileName.contains("lotsawa")) {
            return "lotsawa_house";
        }

        // Check if filename contains a specific person's name
        // Could be expanded based on your needs

        return null;  // No smart default, will default to current user
    }
}
