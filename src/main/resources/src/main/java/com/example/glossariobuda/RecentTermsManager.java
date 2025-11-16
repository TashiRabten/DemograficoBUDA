package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for managing recent terms display with cursor-based pagination.
 * Modernized for large datasets (1M+ records) using cursor-based pagination and explicit load control.
 *
 * Key optimizations:
 * - Asynchronous loading (prevents UI freezing on large databases)
 * - Cursor-based pagination (ID-based, O(1) instead of OFFSET's O(n))
 * - "Carregar Mais" button (explicit user control instead of auto-scroll)
 * - Composite index on (date_added DESC, id DESC) for instant queries
 * - Larger page size (200 items instead of 50 for fewer queries)
 * - ListView virtualization (JavaFX built-in, only renders visible cells)
 */
public class RecentTermsManager {

    // Dependencies
    private final DatabaseManager dbManager;
    private final ListView<DatabaseManager.Term> resultsListView;
    private final TextArea detailsArea;
    private final Label operationStatusLabel;
    private final Button loadMoreButton;
    private NetworkStatusMonitor networkMonitor;

    // State
    private String currentOwner = "ALL";
    private List<DatabaseManager.Term> currentSearchResults;
    private DatabaseManager.Term selectedTerm;
    private boolean showingRecents = false;
    private int lastLoadedId = 0;  // Cursor for pagination (ID of last loaded term)
    private boolean isLoadingMore = false;  // Prevent concurrent loads
    private boolean hasMoreRecents = true;  // Track if more terms available

    // Modern pagination settings
    private static final int RECENTS_PER_PAGE = 200;  // Increased from 50 for fewer queries

    // Callback
    private RecentsCallback callback;

    // ============================================================================
    // INTERFACES
    // ============================================================================

    public interface RecentsCallback {
        void onRecentsLoaded(List<DatabaseManager.Term> recents, boolean hasMore);
        void onRecentsRefreshed();
    }

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================

    public RecentTermsManager(DatabaseManager dbManager,
                               ListView<DatabaseManager.Term> resultsListView,
                               TextArea detailsArea,
                               Label operationStatusLabel,
                               Button loadMoreButton) {
        this.dbManager = dbManager;
        this.resultsListView = resultsListView;
        this.detailsArea = detailsArea;
        this.operationStatusLabel = operationStatusLabel;
        this.loadMoreButton = loadMoreButton;
    }

    // ============================================================================
    // SETTERS
    // ============================================================================

    public void setNetworkMonitor(NetworkStatusMonitor monitor) {
        this.networkMonitor = monitor;
    }

    public void setRecentsCallback(RecentsCallback callback) {
        this.callback = callback;
    }

    public void setCurrentOwner(String owner) {
        this.currentOwner = owner;
    }

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Show recent terms with owner filter using modern cursor-based pagination.
     * Loads asynchronously to prevent UI freezing on large databases.
     */
    public void showRecentTerms() {
        // Reset to initial state
        showingRecents = true;
        lastLoadedId = 0;  // Start from newest (no cursor)
        hasMoreRecents = true;
        selectedTerm = null;

        // Show loading state immediately (on UI thread - no freeze!)
        resultsListView.getItems().clear();
        detailsArea.clear();
        loadMoreButton.setVisible(false);
        updateStatus("Carregando termos recentes...");

        // Capture owner filter for background thread
        final String ownerFilter = currentOwner;

        // Load data in background thread (prevents UI freeze)
        CompletableFuture.supplyAsync(() -> {
            System.out.println("[RecentTermsManager] Loading first page of recents (async)...");
            return dbManager.getRecentTermsByOwner(ownerFilter, RECENTS_PER_PAGE, 0);
        }).thenAcceptAsync(recentTerms -> {
            // Update UI on JavaFX thread
            currentSearchResults = recentTerms;

            resultsListView.getItems().clear();
            resultsListView.getItems().addAll(recentTerms);

            // Update cursor to ID of last loaded term
            if (!recentTerms.isEmpty()) {
                DatabaseManager.Term lastTerm = recentTerms.get(recentTerms.size() - 1);
                lastLoadedId = lastTerm.getId();
            }

            // Check if more terms available (got full page = likely more exist)
            hasMoreRecents = recentTerms.size() >= RECENTS_PER_PAGE;

            // Show "Carregar Mais" button if more terms available
            loadMoreButton.setVisible(hasMoreRecents);
            loadMoreButton.setDisable(false);
            if (hasMoreRecents) {
                loadMoreButton.setText("Carregar Mais (" + RECENTS_PER_PAGE + ")");
            }

            // Update status message with current filter
            String statusMessage = "Mostrando " + recentTerms.size() + " termos recentes";

            if (!ownerFilter.equals("ALL")) {
                if (ownerFilter.equals("shared")) {
                    statusMessage += " (compartilhados)";
                } else {
                    statusMessage += " de: " + ownerFilter;
                }
            }

            updateStatus(statusMessage);

            // Select first term
            if (!recentTerms.isEmpty()) {
                resultsListView.getSelectionModel().select(0);
                selectedTerm = recentTerms.get(0);
            } else {
                // Show message when no terms for filter
                if (!ownerFilter.equals("ALL")) {
                    operationStatusLabel.setText("Nenhum termo recente encontrado para: " + ownerFilter);
                }
            }

            // Callback
            if (callback != null) {
                callback.onRecentsLoaded(recentTerms, hasMoreRecents);
            }

            System.out.println("[RecentTermsManager] Loaded " + recentTerms.size() + " recents (async complete)");
        }, Platform::runLater).exceptionally(error -> {
            // Handle errors on JavaFX thread
            Platform.runLater(() -> {
                System.err.println("[RecentTermsManager] Error loading recents: " + error.getMessage());
                error.printStackTrace();
                updateStatus("Erro ao carregar termos recentes");
            });
            return null;
        });
    }

    /**
     * Show recent terms filtered by owner (alternate method).
     */
    public void showRecentTermsByOwner() {
        showRecentTerms();
    }

    /**
     * Load more recent terms using cursor-based pagination.
     * Called when user clicks the "Carregar Mais" button.
     * Loads asynchronously to prevent UI freezing.
     */
    public void loadMoreRecents() {
        // Prevent concurrent loads
        if (isLoadingMore || !hasMoreRecents) {
            return;
        }

        isLoadingMore = true;
        loadMoreButton.setDisable(true);
        loadMoreButton.setText("Carregando...");

        System.out.println("[RecentTermsManager] Loading more recents from cursor ID: " + lastLoadedId);

        // Capture state for background thread
        final int cursorId = lastLoadedId;
        final String ownerFilter = currentOwner;

        // Load data in background thread (prevents UI freeze)
        CompletableFuture.supplyAsync(() -> {
            return dbManager.getRecentTermsByOwner(ownerFilter, RECENTS_PER_PAGE, cursorId);
        }).thenAcceptAsync(moreRecents -> {
            // Update UI on JavaFX thread
            if (!moreRecents.isEmpty()) {
                // Add new terms to ListView
                resultsListView.getItems().addAll(moreRecents);

                // Update cursor to last loaded term ID
                DatabaseManager.Term lastTerm = moreRecents.get(moreRecents.size() - 1);
                lastLoadedId = lastTerm.getId();

                // Check if more terms available
                hasMoreRecents = moreRecents.size() >= RECENTS_PER_PAGE;

                // Update button visibility and text
                if (hasMoreRecents) {
                    loadMoreButton.setVisible(true);
                    loadMoreButton.setDisable(false);
                    loadMoreButton.setText("Carregar Mais (" + RECENTS_PER_PAGE + ")");
                } else {
                    loadMoreButton.setVisible(false);
                }

                // Update status
                int totalShown = resultsListView.getItems().size();
                String statusMessage = "Mostrando " + totalShown + " termos recentes";
                if (!ownerFilter.equals("ALL")) {
                    statusMessage += " de: " + ownerFilter;
                }
                updateStatus(statusMessage);

                System.out.println("[RecentTermsManager] Loaded " + moreRecents.size() + " more recents. Total: " + totalShown);
            } else {
                hasMoreRecents = false;
                loadMoreButton.setVisible(false);
                System.out.println("[RecentTermsManager] No more recents to load");
            }

            isLoadingMore = false;
        }, Platform::runLater).exceptionally(error -> {
            // Handle errors on JavaFX thread
            Platform.runLater(() -> {
                System.err.println("[RecentTermsManager] Error loading more recents: " + error.getMessage());
                error.printStackTrace();
                loadMoreButton.setVisible(false);
                updateStatus("Erro ao carregar mais termos");
                isLoadingMore = false;
            });
            return null;
        });
    }

    /**
     * Refresh recents list after deletion - stay on recents page.
     * Loads asynchronously to prevent UI freezing.
     */
    public void refreshRecents() {
        // Reset to first page of recents (cursor = 0)
        lastLoadedId = 0;
        hasMoreRecents = true;

        // Show loading state
        resultsListView.getItems().clear();
        loadMoreButton.setVisible(false);
        updateStatus("Atualizando termos recentes...");

        // Capture owner filter for background thread
        final String ownerFilter = currentOwner;

        // Load data in background thread (prevents UI freeze)
        CompletableFuture.supplyAsync(() -> {
            return dbManager.getRecentTermsByOwner(ownerFilter, RECENTS_PER_PAGE, 0);
        }).thenAcceptAsync(recentTerms -> {
            // Update UI on JavaFX thread
            currentSearchResults = recentTerms;

            resultsListView.getItems().clear();
            resultsListView.getItems().addAll(recentTerms);

            // Update cursor
            if (!recentTerms.isEmpty()) {
                DatabaseManager.Term lastTerm = recentTerms.get(recentTerms.size() - 1);
                lastLoadedId = lastTerm.getId();
            }

            // Check if more available
            hasMoreRecents = recentTerms.size() >= RECENTS_PER_PAGE;

            // Show "Carregar Mais" button if more terms available
            loadMoreButton.setVisible(hasMoreRecents);
            loadMoreButton.setDisable(false);
            if (hasMoreRecents) {
                loadMoreButton.setText("Carregar Mais (" + RECENTS_PER_PAGE + ")");
            }

            // Update status
            String statusMessage = "Mostrando " + recentTerms.size() + " termos mais recentes";
            updateStatus(statusMessage);

            // Callback
            if (callback != null) {
                callback.onRecentsRefreshed();
            }
        }, Platform::runLater).exceptionally(error -> {
            // Handle errors on JavaFX thread
            Platform.runLater(() -> {
                System.err.println("[RecentTermsManager] Error refreshing recents: " + error.getMessage());
                error.printStackTrace();
                updateStatus("Erro ao atualizar termos recentes");
            });
            return null;
        });
    }

    // ============================================================================
    // GETTERS
    // ============================================================================

    public boolean isShowingRecents() {
        return showingRecents;
    }

    public void setShowingRecents(boolean showing) {
        this.showingRecents = showing;
    }

    public List<DatabaseManager.Term> getCurrentSearchResults() {
        return currentSearchResults;
    }

    public DatabaseManager.Term getSelectedTerm() {
        return selectedTerm;
    }

    public void setSelectedTerm(DatabaseManager.Term term) {
        this.selectedTerm = term;
    }

    public String getCurrentOwner() {
        return currentOwner;
    }

    // ============================================================================
    // PRIVATE HELPERS
    // ============================================================================

    private void updateStatus(String message) {
        if (networkMonitor != null && networkMonitor.isOnline()) {
            Platform.runLater(() -> {
                String currentStatus = operationStatusLabel.getText();
                if (!currentStatus.contains("🟢") && !currentStatus.contains("🔴")) {
                    operationStatusLabel.setText(message);
                }
            });
        } else {
            operationStatusLabel.setText(message);
        }
    }
}
