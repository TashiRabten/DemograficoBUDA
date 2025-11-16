package com.example.glossariobuda;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for import/export operations for glossary terms.
 * Handles XML/CSV/JSON import and WhatsApp export functionality.
 * Extracted from GlossarioController to follow Single Responsibility Principle.
 *
 * @author GlossarioBuda Team
 * @version 1.0
 */
public class ImportExportManager {

    // ============================================================================
    // DEPENDENCIES
    // ============================================================================

    private final DatabaseManager dbManager;
    private final ExportManager exportManager;
    private final Label operationStatusLabel;
    private NetworkStatusMonitor networkMonitor;

    // ============================================================================
    // CALLBACK INTERFACE
    // ============================================================================

    /**
     * Callback interface for import/export operations.
     * Allows the controller to react to import/export events.
     */
    public interface ImportExportCallback {
        /**
         * Called when import progress is updated.
         * @param current Current progress count
         * @param total Total items to process
         * @param message Progress message
         */
        void onImportProgress(int current, int total, String message);

        /**
         * Called when import completes successfully.
         * @param totalLoaded Total number of terms loaded
         * @param message Completion message
         */
        void onImportComplete(int totalLoaded, String message);

        /**
         * Called when an import error occurs.
         * @param error Error message
         */
        void onImportError(String error);

        /**
         * Called when export completes successfully.
         * @param exportText The exported text content
         */
        void onExportComplete(String exportText);

        /**
         * Called when owner data has changed (new owners imported).
         * Signals that the owner filter should be refreshed.
         */
        void onOwnerChanged();
    }

    // ============================================================================
    // STATE
    // ============================================================================

    private ImportExportCallback callback;

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================

    /**
     * Constructs a new ImportExportManager with the specified dependencies.
     *
     * @param dbManager The database manager for term operations
     * @param exportManager The export manager for formatting exports
     * @param operationStatusLabel The label to display operation status
     */
    public ImportExportManager(DatabaseManager dbManager,
                               ExportManager exportManager,
                               Label operationStatusLabel) {
        this.dbManager = dbManager;
        this.exportManager = exportManager;
        this.operationStatusLabel = operationStatusLabel;
    }

    // ============================================================================
    // SETTERS
    // ============================================================================

    /**
     * Sets the network status monitor for online/offline status updates.
     *
     * @param monitor The network status monitor
     */
    public void setNetworkMonitor(NetworkStatusMonitor monitor) {
        this.networkMonitor = monitor;
    }

    /**
     * Sets the callback for import/export events.
     *
     * @param callback The callback implementation
     */
    public void setImportExportCallback(ImportExportCallback callback) {
        this.callback = callback;
    }

    // ============================================================================
    // PUBLIC API - IMPORT
    // ============================================================================

    /**
     * Shows a file chooser dialog and loads a glossary from XML/CSV/JSON file.
     * Supports multiple file formats: XML, CSV, TSV, TXT, and JSON.
     * The import process runs in a background thread to avoid blocking the UI.
     *
     * @param ownerWindow The parent window for the file chooser dialog
     */
    public void loadXMLGlossary(javafx.stage.Window ownerWindow) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar Arquivo de Glossário");

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*", "*"),
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("CSV/Text Files", "*.csv", "*.txt", "*.tsv"),
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );

        File selectedFile = fileChooser.showOpenDialog(ownerWindow);
        if (selectedFile == null) {
            return;
        }

        // Get owner name
        TextInputDialog ownerDialog = new TextInputDialog(System.getProperty("user.name", "user"));
        ownerDialog.setTitle("Import Owner");
        ownerDialog.setHeaderText("Enter owner name for this import:");
        ownerDialog.setContentText("Owner:");

        String owner = ownerDialog.showAndWait().orElse(System.getProperty("user.name", "user"));

        // Show parser selection dialog
        FormatParser parser = XMLParserChooserDialogFX.chooseParser(ownerWindow, selectedFile);

        if (parser == null) {
            return; // User cancelled
        }

        // Import in background thread
        Thread loadThread = new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    if (networkMonitor != null) {
                        networkMonitor.setSyncing("Importing with " + parser.getFormatDescription());
                    }
                });

                java.util.List<GlossaryTerm> terms = parser.parse(selectedFile, new FormatParser.LoadProgressCallback() {
                    @Override
                    public void onProgress(int current, int total, String message) {
                        Platform.runLater(() -> {
                            if (networkMonitor != null) {
                                networkMonitor.setSyncing(message);
                            }
                            if (callback != null) {
                                callback.onImportProgress(current, total, message);  // ✅ NOW wrapped
                            }
                        });
                    }

                    @Override
                    public void onComplete(int totalLoaded, String message) {
                        // Will be called after database insertion
                    }

                    @Override
                    public void onError(String error) {
                        Platform.runLater(() -> {
                            if (networkMonitor != null) {
                                networkMonitor.setSyncError(error);
                            }
                            if (callback != null) {
                                callback.onImportError(error);
                            }
                            showAlert("Import Error", error);
                        });
                    }
                });

                // Add to database
                int count = 0;
                for (GlossaryTerm term : terms) {
                    term.setOwner(owner);
                    AddTermParams params = new AddTermParams(
                            term.getSourceTerm(),
                            term.getSourceLanguage(),
                            term.getTargetTerm(),
                            term.getTargetLanguage(),
                            term.getContext(),
                            term.getContributor(),
                            term.getNotes(),
                            term.getOwner()
                    );
                    dbManager.addTerm(params);
                    count++;
                }

                final int finalCount = count;
                Platform.runLater(() -> {
                    if (networkMonitor != null) {
                        networkMonitor.setSyncSuccess("Imported " + finalCount + " terms");
                    }
                    if (callback != null) {
                        callback.onImportComplete(finalCount, "Imported " + finalCount + " terms");
                        callback.onOwnerChanged();
                    }
                    showAlert("Import Complete", "Successfully imported " + finalCount + " terms!");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (networkMonitor != null) {
                        networkMonitor.setSyncError("Import failed: " + e.getMessage());
                    }
                    if (callback != null) {
                        callback.onImportError(e.getMessage());
                    }
                    showAlert("Import Error", "Import failed: " + e.getMessage());
                });
                e.printStackTrace();
            }
        });

        loadThread.setDaemon(true);
        loadThread.start();
    }

    // ============================================================================
    // PUBLIC API - EXPORT
    // ============================================================================

    /**
     * Shows a dialog for exporting terms to WhatsApp format.
     * Allows the user to select how many terms to export (10, 25, 50, 100, or all).
     * The exported text is displayed in a copyable text area.
     *
     * @param ownerWindow The parent window for the export dialog
     */
    public void showExportDialog(javafx.stage.Window ownerWindow) {
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Exportar para WhatsApp");
        dialogStage.initOwner(ownerWindow);

        try {
            dialogStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/pramana.png")));
        } catch (Exception e) {
            System.err.println("Warning: Could not load dialog icon: " + e.getMessage());
        }

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label headerLabel = new Label("Escolha o tipo de exportação");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        RadioButton recent10Radio = new RadioButton("Últimos 10 termos");
        RadioButton recent25Radio = new RadioButton("Últimos 25 termos");
        RadioButton recent50Radio = new RadioButton("Últimos 50 termos");
        RadioButton recent100Radio = new RadioButton("Últimos 100 termos");
        RadioButton allRadio = new RadioButton("Todos os termos (até 1000)");
        recent25Radio.setSelected(true);

        ToggleGroup group = new ToggleGroup();
        recent10Radio.setToggleGroup(group);
        recent25Radio.setToggleGroup(group);
        recent50Radio.setToggleGroup(group);
        recent100Radio.setToggleGroup(group);
        allRadio.setToggleGroup(group);

        content.getChildren().addAll(
                new Label("Selecione quantos termos exportar:"),
                recent10Radio,
                recent25Radio,
                recent50Radio,
                recent100Radio,
                allRadio
        );

        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Button exportButton = new Button("Exportar");
        exportButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        exportButton.setOnAction(e -> {
            // Disable button and show loading state immediately (prevents double-click)
            exportButton.setDisable(true);
            exportButton.setText("Exportando...");

            // Show exporting status
            if (networkMonitor != null) {
                networkMonitor.setSyncing("Exportando termos...");
            }

            // Determine export count based on selected radio
            int exportCount;
            boolean isAllTerms;
            if (recent10Radio.isSelected()) {
                exportCount = 10;
                isAllTerms = false;
            } else if (recent25Radio.isSelected()) {
                exportCount = 25;
                isAllTerms = false;
            } else if (recent50Radio.isSelected()) {
                exportCount = 50;
                isAllTerms = false;
            } else if (recent100Radio.isSelected()) {
                exportCount = 100;
                isAllTerms = false;
            } else {
                exportCount = 1000;
                isAllTerms = true;
            }

            final int count = exportCount;
            final boolean exportAll = isAllTerms;

            // Run export in background thread (prevents UI freeze)
            CompletableFuture.supplyAsync(() -> {
                System.out.println("[ImportExportManager] Exporting " + count + " terms for WhatsApp (async)...");
                if (exportAll) {
                    return exportManager.exportAllTermsForWhatsApp();
                } else {
                    return exportManager.exportRecentTermsForWhatsApp(count);
                }
            }).thenAcceptAsync(export -> {
                // Update UI on JavaFX thread
                if (networkMonitor != null) {
                    // Criar o delay SEM bloquear a UI
                    PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
                    pause.setOnFinished(event -> {
                        networkMonitor.setSyncSuccess("Exportados " + count + " termos");
                    });
                    pause.play();
                }

                // Notify callback
                if (callback != null) {
                    callback.onExportComplete(export);
                }

                showExportResult(export);
                dialogStage.close();

                System.out.println("[ImportExportManager] Export complete: " + count + " terms");
            }, Platform::runLater).exceptionally(error -> {
                // Handle errors on JavaFX thread
                Platform.runLater(() -> {
                    System.err.println("[ImportExportManager] Export failed: " + error.getMessage());
                    error.printStackTrace();

                    if (networkMonitor != null) {
                        networkMonitor.setSyncError("Erro ao exportar termos");
                    }

                    // Re-enable button on error
                    exportButton.setDisable(false);
                    exportButton.setText("Exportar");

                    // Show error dialog
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Erro na Exportação");
                    alert.setHeaderText("Falha ao exportar termos");
                    alert.setContentText(error.getMessage());
                    alert.showAndWait();
                });
                return null;
            });
        });

        Button cancelButton = new Button("Cancelar");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelButton.setOnAction(e -> dialogStage.close());

        buttonBar.getChildren().addAll(exportButton, cancelButton);

        root.getChildren().addAll(headerLabel, content, buttonBar);

        Scene scene = new Scene(root, 400, 300);
        dialogStage.setScene(scene);
        dialogStage.show();
    }

    /**
     * Displays the exported text in a copyable dialog window.
     * The text area is read-only and pre-filled with the exported content.
     *
     * @param exportText The text to display in the dialog
     */
    public void showExportResult(String exportText) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Exportação Completa");
        dialog.setHeaderText("Copie o texto abaixo para o WhatsApp:");

        TextArea textArea = new TextArea(exportText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);
        textArea.setPrefColumnCount(50);

        dialog.getDialogPane().setContent(textArea);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    // ============================================================================
    // PUBLIC API - CONVERSION
    // ============================================================================

    /**
     * Converts a GlossaryTerm object to a SupabaseClient.TermDTO for cloud sync.
     * Handles both modern and legacy XML format conversions.
     *
     * @param term The GlossaryTerm to convert
     * @return A TermDTO ready for Supabase upload
     */
    public SupabaseClient.TermDTO convertToTermDTO(GlossaryTerm term) {
        SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();

        // Extract source and target terms with language detection
        populateSourceAndTarget(dto, term);

        // Build context and notes
        dto.context = buildTermContext(term);
        dto.notes = buildTermNotes(term, dto.source_language);
        dto.contributor = term.getContributor() != null ? term.getContributor() : "84000 Glossary";
        dto.verified_status = "unverified";

        // Generate content hash and set timestamp
        dto.content_hash = SupabaseClient.generateHash(
                dto.source_term, dto.source_language,
                dto.target_term, dto.target_language,
                dto.context, dto.contributor
        );
        dto.date_added = java.time.Instant.now().toString();

        return dto;
    }

    private void populateSourceAndTarget(SupabaseClient.TermDTO dto, GlossaryTerm term) {
        if (term.getSourceTerm() != null && !term.getSourceTerm().isEmpty()) {
            dto.source_term = term.getSourceTerm();
            dto.source_language = term.getSourceLanguage() != null ? term.getSourceLanguage() : "English";
            dto.target_term = term.getTargetTerm();
            dto.target_language = term.getTargetLanguage() != null ? term.getTargetLanguage() : "Tibetan";
        } else {
            populateLegacyXmlFormat(dto, term);
        }
    }

    private void populateLegacyXmlFormat(SupabaseClient.TermDTO dto, GlossaryTerm term) {
        if (term.getSanskrit() != null && !term.getSanskrit().isEmpty()) {
            dto.source_term = term.getSanskrit();
            dto.source_language = "Sanskrit";
        } else {
            dto.source_term = term.getTranslation();
            dto.source_language = "English";
        }
        dto.target_term = term.getTibetan();
        dto.target_language = "Tibetan";
    }

    private String buildTermContext(GlossaryTerm term) {
        StringBuilder context = new StringBuilder();

        String definition = term.getContext() != null ? term.getContext() : term.getDefinition();
        if (definition != null && !definition.isEmpty()) {
            context.append(definition);
        }

        if (term.getReferences() != null && !term.getReferences().isEmpty()) {
            if (context.length() > 0) {
                context.append("\n\n");
            }
            context.append("Referências:\n");
            for (String ref : term.getReferences()) {
                context.append("• ").append(ref).append("\n");
            }
        }

        return context.toString();
    }

    private String buildTermNotes(GlossaryTerm term, String sourceLanguage) {
        StringBuilder notes = new StringBuilder();

        appendNoteIfPresent(notes, "Wylie: ", term.getWylie());

        if (term.getSanskrit() != null && !term.getSanskrit().isEmpty()
                && !sourceLanguage.equals("Sanskrit")) {
            appendNoteIfPresent(notes, "Sanskrit: ", term.getSanskrit());
        }

        appendNoteIfPresent(notes, "Type: ", term.getType());

        if (term.getNotes() != null && !term.getNotes().isEmpty()) {
            if (notes.length() > 0) {
                notes.append("\n");
            }
            notes.append(term.getNotes());
        }

        return notes.toString();
    }

    private void appendNoteIfPresent(StringBuilder notes, String prefix, String value) {
        if (value != null && !value.isEmpty()) {
            notes.append(prefix).append(value).append("\n");
        }
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    /**
     * Shows an information alert dialog with the specified title and message.
     * The dialog automatically adjusts its size to fit the content.
     *
     * @param title The dialog title
     * @param message The message to display
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Ajustar automaticamente ao conteúdo
        alert.getDialogPane().setMinWidth(500);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        alert.showAndWait();
    }
}
