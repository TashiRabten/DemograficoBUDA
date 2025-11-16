package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class to integrate SWT Tibetan Input Dialog with JavaFX.
 *
 * This class handles the thread synchronization between JavaFX (UI thread)
 * and SWT (requires its own display thread).
 *
 * Usage:
 *   TibetanInputHelper.openForTextField(myTextField);
 *   TibetanInputHelper.openForTextArea(myTextArea);
 */
public class TibetanInputHelper {

    /**
     * Opens Tibetan input dialog for a JavaFX TextField (single-line).
     *
     * @param textField The JavaFX TextField to populate with Tibetan text
     */
    public static void openForTextField(TextField textField) {
        openDialog(textField, false);
    }

    /**
     * Opens Tibetan input dialog for a JavaFX TextArea (multi-line).
     *
     * @param textArea The JavaFX TextArea to populate with Tibetan text
     */
    public static void openForTextArea(TextArea textArea) {
        openDialog(textArea, true);
    }

    /**
     * Opens Tibetan input dialog for any JavaFX TextInputControl.
     *
     * @param control The JavaFX text control
     * @param multiline Whether to show multi-line input
     */
    private static void openDialog(TextInputControl control, boolean multiline) {
        if (control == null) {
            return;
        }

        String initialText = control.getText();

        // Run SWT dialog in a separate thread (SWT requires its own display thread)
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            return runSWTDialog(initialText, multiline);
        });

        // When dialog completes, update JavaFX control on JavaFX thread
        future.thenAccept(result -> {
            if (result != null) {
                Platform.runLater(() -> {
                    control.setText(result);
                });
            }
        });
    }

    /**
     * Runs the SWT dialog in the current thread.
     * This method blocks until the dialog is closed.
     *
     * @param initialText Initial text to show
     * @param multiline Whether to show multi-line input
     * @return The entered text, or null if cancelled
     */
    private static String runSWTDialog(String initialText, boolean multiline) {
        AtomicReference<String> result = new AtomicReference<>(null);

        try {
            // Create SWT display and shell
            Display display = new Display();
            Shell shell = null; // Parent shell is null for standalone dialog

            // Open the dialog (this blocks until dialog closes)
            String dialogResult = TibetanInputDialog.open(shell, initialText, multiline);

            result.set(dialogResult);

            // Dispose display
            display.dispose();

        } catch (Exception e) {
            System.err.println("[Tibetan Input] Error opening dialog: " + e.getMessage());
            e.printStackTrace();
        }

        return result.get();
    }

    /**
     * Creates a Tibetan input button helper method for convenience.
     * This can be used to create buttons that open the Tibetan input dialog.
     *
     * Example:
     *   Button btn = new Button("བོད་");
     *   btn.setOnAction(e -> TibetanInputHelper.openForTextField(myTextField));
     *
     * @return Unicode Tibetan character "བོད་" for button text
     */
    public static String getTibetanButtonLabel() {
        return "བོད་"; // Tibetan "bod" (Tibet)
    }

    /**
     * Alternative button label using Tibetan script character.
     *
     * @return Unicode Tibetan character "ཀ" (simple Tibetan letter)
     */
    public static String getSimpleTibetanLabel() {
        return "ཀ"; // Simple Tibetan letter
    }
}
