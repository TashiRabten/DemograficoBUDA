package com.example.glossariobuda;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for CRUD operations on terms (Create, Read, Update, Delete).
 * Extracted from GlossarioController to follow Single Responsibility Principle.
 */
public class TermCRUDHandler {

    // Dependencies
    private final DatabaseManager dbManager;
    private final TermDialogHelper dialogHelper;
    private final ListView<DatabaseManager.Term> resultsListView;
    private final TextArea detailsArea;
    private final Label operationStatusLabel;
    private final Button openLinkButton;
    private NetworkStatusMonitor networkMonitor;

    // State
    private DatabaseManager.Term selectedTerm;
    private String currentMediaLink = null;

    // Callback
    private CRUDCallback callback;

    // Date formatters
    private static final java.time.format.DateTimeFormatter SQL_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final java.time.format.DateTimeFormatter OUTPUT_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ============================================================================
    // INTERFACES
    // ============================================================================

    public interface CRUDCallback {
        void onTermAdded(String message);
        void onTermUpdated(String message);
        void onTermsDeleted(int count, String message);
        void onOwnerChanged(); // Triggered when owner filter needs refresh
        void onError(String operation, String error);
    }

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================

    public TermCRUDHandler(DatabaseManager dbManager,
                           TermDialogHelper dialogHelper,
                           ListView<DatabaseManager.Term> resultsListView,
                           TextArea detailsArea,
                           Label operationStatusLabel,
                           Button openLinkButton) {
        this.dbManager = dbManager;
        this.dialogHelper = dialogHelper;
        this.openLinkButton = openLinkButton;
        this.resultsListView = resultsListView;
        this.detailsArea = detailsArea;
        this.operationStatusLabel = operationStatusLabel;
    }

    // ============================================================================
    // SETTERS
    // ============================================================================

    public void setNetworkMonitor(NetworkStatusMonitor monitor) {
        this.networkMonitor = monitor;
    }

    public void setCRUDCallback(CRUDCallback callback) {
        this.callback = callback;
    }

    public void setSelectedTerm(DatabaseManager.Term term) {
        this.selectedTerm = term;
    }

    // ============================================================================
    // PUBLIC API - CRUD Operations
    // ============================================================================

    /**
     * Show dialog to add a new term.
     */
    public void showAddTermDialog(Window owner) {
        dialogHelper.showAddDialog(owner, dbManager, new TermDialogHelper.TermOperationCallback() {
            @Override
            public void onSuccess(String message) {
                if (networkMonitor != null) {
                    networkMonitor.setSyncSuccess(message);
                } else {
                    operationStatusLabel.setText(message);
                }

                // Notify owner filter needs refresh
                if (callback != null) {
                    callback.onOwnerChanged();
                    callback.onTermAdded(message);
                }
            }

            @Override
            public void onError(String message) {
                if (networkMonitor != null) {
                    networkMonitor.setSyncError(message);
                } else {
                    operationStatusLabel.setText(message);
                }

                if (callback != null) {
                    callback.onError("add", message);
                }
            }
        });
    }

    /**
     * Edit the selected term.
     */
    public void editSelectedTerm(Window owner) {
        if (selectedTerm == null) {
            if (callback != null) {
                callback.onError("edit", "Selecione um termo para editar clicando nele na lista de resultados.");
            }
            return;
        }

        dialogHelper.showEditDialog(owner, dbManager, selectedTerm, new TermDialogHelper.TermOperationCallback() {
            @Override
            public void onSuccess(String message) {
                if (networkMonitor != null) {
                    networkMonitor.setSyncSuccess(message);
                } else {
                    operationStatusLabel.setText(message);
                }

                // Notify owner filter needs refresh
                if (callback != null) {
                    callback.onOwnerChanged();
                    callback.onTermUpdated(message);
                }
            }

            @Override
            public void onError(String message) {
                if (networkMonitor != null) {
                    networkMonitor.setSyncError(message);
                } else {
                    operationStatusLabel.setText(message);
                }

                if (callback != null) {
                    callback.onError("edit", message);
                }
            }
        });
    }

    /**
     * Delete selected term(s).
     */
    public void deleteSelectedTerms(Window owner, boolean showingRecents) {
        // Get all selected items
        var selectedIndices = resultsListView.getSelectionModel().getSelectedIndices();

        if (selectedIndices.isEmpty()) {
            if (callback != null) {
                callback.onError("delete", "Selecione um ou mais termos para deletar.");
            }
            return;
        }

        // Get the corresponding terms
        List<DatabaseManager.Term> termsToDelete = new ArrayList<>();
        for (int index : selectedIndices) {
            if (index >= 0 && index < resultsListView.getItems().size()) {
                termsToDelete.add(resultsListView.getItems().get(index));
            }
        }

        if (termsToDelete.isEmpty()) {
            return;
        }

        // Use centralized delete dialog
        dialogHelper.showDeleteDialog(owner, dbManager, termsToDelete,
                new TermDialogHelper.DeleteOperationCallback() {
                    @Override
                    public void onSuccess(int deletedCount, String message) {
                        // Show success message
                        if (networkMonitor != null) {
                            networkMonitor.setSyncSuccess(message);
                        }

                        selectedTerm = null;

                        // Force status refresh
                        Platform.runLater(() -> {
                            if (callback != null) {
                                callback.onTermsDeleted(deletedCount, message);
                            }
                        });
                    }

                    @Override
                    public void onCancel() {
                        // Do nothing on cancel
                    }
                });
    }

    /**
     * Display term details in TextArea.
     */
    public void displayTermDetails(DatabaseManager.Term term) {
        if (term == null) {
            detailsArea.clear();
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("🔤 Termo Original: ").append(term.getSourceTerm()).append("\n");
        details.append("🌍 Idioma Original: ").append(term.getSourceLanguage()).append("\n\n");
        details.append("📝 Tradução: ").append(term.getTargetTerm()).append("\n");
        details.append("🌍 Idioma Tradução: ").append(term.getTargetLanguage()).append("\n\n");

        // Handle List<String> for contexts
        List<String> contexts = term.getContexts();
        if (contexts != null && !contexts.isEmpty()) {
            details.append("📖 Contexto");
            if (contexts.size() > 1) {
                details.append("s");
            }
            details.append(":\n");
            for (int i = 0; i < contexts.size(); i++) {
                if (contexts.size() > 1) {
                    details.append("  ").append(i + 1).append(". ");
                }
                details.append(contexts.get(i)).append("\n");
            }
            details.append("\n");
        }

        // Handle List<String> for contributors
        List<String> contributors = term.getContributors();
        if (contributors != null && !contributors.isEmpty()) {
            details.append("👤 Contribuidor");
            if (contributors.size() > 1) {
                details.append("es");
            }
            details.append(": ").append(String.join(", ", contributors)).append("\n\n");
        }

        details.append("📅 Data: ").append(formatDate(term.getDateAdded())).append("\n");
        details.append("✓ Status: ").append(term.getVerifiedStatus()).append("\n");

        if (term.getNotes() != null && !term.getNotes().trim().isEmpty()) {
            String notes = term.getNotes();

            // Check if notes contain a link (PDF or Audio)
            String extractedUrl = extractUrlFromHtml(notes);
            if (extractedUrl != null && !extractedUrl.isEmpty()) {
                currentMediaLink = extractedUrl;
                openLinkButton.setVisible(true);
                openLinkButton.setManaged(true);

                // Remove HTML link from notes content
                String cleanedNotes = removeHtmlLink(notes);

                // Update button text and show disclaimer + actual notes content
                if (extractedUrl.contains(".mp3") || extractedUrl.contains("audio")) {
                    openLinkButton.setText("🔊 Abrir Áudio");
                    details.append("📌 Notas: Aperte o Botão de Áudio");
                } else if (extractedUrl.contains(".pdf")) {
                    openLinkButton.setText("📄 Abrir PDF");
                    details.append("📌 Notas: Aperte o Botão de PDF");
                } else {
                    openLinkButton.setText("🔗 Abrir Link");
                    details.append("📌 Notas: Aperte o Botão de Link");
                }

                // Add cleaned notes content if there's any
                if (!cleanedNotes.trim().isEmpty()) {
                    details.append("\n").append(cleanedNotes);
                }
                details.append("\n");
            } else {
                // No link found, show notes as-is
                currentMediaLink = null;
                openLinkButton.setVisible(false);
                openLinkButton.setManaged(false);
                details.append("📌 Notas: ").append(notes).append("\n");
            }
        } else {
            currentMediaLink = null;
            openLinkButton.setVisible(false);
            openLinkButton.setManaged(false);
        }

        detailsArea.setText(details.toString());
    }

    // ============================================================================
    // GETTERS
    // ============================================================================

    public DatabaseManager.Term getSelectedTerm() {
        return selectedTerm;
    }

    // ============================================================================
    // PRIVATE HELPERS
    // ============================================================================

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty() || "null".equals(isoDate)) {
            return "Data desconhecida";
        }

        try {
            java.time.ZonedDateTime dateTime;

            // Normalize: replace +00:00 with Z for ISO parser
            String normalizedDate = isoDate.replace("+00:00", "Z");

            // Parse ISO 8601 format (with T)
            if (normalizedDate.contains("T")) {
                dateTime = java.time.ZonedDateTime.parse(normalizedDate,
                        java.time.format.DateTimeFormatter.ISO_DATE_TIME);
            }
            // SQL format (yyyy-MM-dd HH:mm:ss)
            else if (normalizedDate.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                dateTime = java.time.LocalDateTime.parse(normalizedDate, SQL_FORMATTER)
                        .atZone(java.time.ZoneOffset.UTC);
            }
            // Simple date (yyyy-MM-dd)
            else if (normalizedDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                dateTime = java.time.LocalDate.parse(normalizedDate)
                        .atStartOfDay(java.time.ZoneOffset.UTC);
            }
            else {
                System.err.println("Unknown date format: " + isoDate);
                return "Data desconhecida";
            }

            // Convert to local timezone and format
            dateTime = dateTime.withZoneSameInstant(java.time.ZoneId.systemDefault());
            return dateTime.format(OUTPUT_FORMATTER);

        } catch (Exception e) {
            System.err.println("Error formatting date: " + isoDate + " - " + e.getMessage());
            return "Data desconhecida";
        }
    }

    /**
     * Extract URL from HTML anchor tag.
     * Pattern: <a href="URL" ...>
     */
    private String extractUrlFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        // Look for <a href="..."> pattern
        int hrefStart = html.indexOf("href=\"");
        if (hrefStart == -1) {
            return null;
        }

        // Move past href="
        int urlStart = hrefStart + 6;

        // Find closing quote
        int urlEnd = html.indexOf("\"", urlStart);
        if (urlEnd == -1) {
            return null;
        }

        return html.substring(urlStart, urlEnd);
    }

    /**
     * Remove HTML anchor tag from notes, keeping any surrounding text.
     * Example: "Audio: <a href="...">Play</a>" → ""
     * Example: "See reference\nAudio: <a href="...">Play</a>" → "See reference"
     */
    private String removeHtmlLink(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        // Find the <a> tag and remove entire line containing it
        // Pattern: Audio: <a href="...">...</a> or PDF: <a href="...">...</a>
        String result = html.replaceAll("(?m)^.*?<a\\s+href=\"[^\"]+\"[^>]*>.*?</a>.*?$", "");

        // Clean up extra newlines
        result = result.replaceAll("\n{2,}", "\n").trim();

        return result;
    }

    /**
     * Open the current media link in the default browser.
     * Called by openLinkButton in FXML.
     */
    public void openMediaLink() {
        if (currentMediaLink == null || currentMediaLink.isEmpty()) {
            operationStatusLabel.setText("❌ Nenhum link disponível");
            return;
        }

        try {
            // Use AWT Desktop to open link in default browser
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(new java.net.URI(currentMediaLink));
                operationStatusLabel.setText("✓ Link aberto no navegador");
            } else {
                operationStatusLabel.setText("❌ Não foi possível abrir o link");
            }
        } catch (Exception e) {
            operationStatusLabel.setText("❌ Erro ao abrir link: " + e.getMessage());
            e.printStackTrace();
        }

        // Reset status after 3 seconds (using PauseTransition to avoid blocking UI)
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(event -> operationStatusLabel.setText(""));
        pause.play();
    }
}
