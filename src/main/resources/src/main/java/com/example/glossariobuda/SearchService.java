package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Service responsible for search operations, filtering, and pagination.
 * Extracted from GlossarioController to follow Single Responsibility Principle.
 */
public class SearchService {

    // Dependencies
    private final DatabaseManager dbManager;
    private final TextField searchField;
    private final ListView<DatabaseManager.Term> resultsListView;
    private final TextArea detailsArea;
    private final Label operationStatusLabel;
    private final Button loadMoreButton;
    private NetworkStatusMonitor networkMonitor;

    // Search state
    private String lastSearchText = "";
    private int currentOffset = 0;
    private String currentOwner = "ALL";
    private List<DatabaseManager.Term> currentSearchResults;
    private DatabaseManager.Term selectedTerm;

    // Debounce for search optimization
    private Timer searchTimer;
    private static final int SEARCH_DELAY_MS = 300;
    private static final int RESULTS_PER_PAGE = 100;

    // Callback
    private SearchCallback callback;

    // ============================================================================
    // INTERFACES
    // ============================================================================

    public interface SearchCallback {
        void onSearchComplete(List<DatabaseManager.Term> results, int totalCount);
        void onSearchCleared();
        void onFilterChanged(String owner);
    }

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================

    public SearchService(DatabaseManager dbManager,
                         TextField searchField,
                         ListView<DatabaseManager.Term> resultsListView,
                         TextArea detailsArea,
                         Label operationStatusLabel,
                         Button loadMoreButton) {
        this.dbManager = dbManager;
        this.searchField = searchField;
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

    public void setSearchCallback(SearchCallback callback) {
        this.callback = callback;
    }

    public void setCurrentOwner(String owner) {
        this.currentOwner = owner;
        if (callback != null) {
            callback.onFilterChanged(owner);
        }
    }

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Perform search with debounce (300ms delay).
     */
    public void performSearchWithDebounce() {
        if (searchTimer != null) {
            searchTimer.cancel();
            searchTimer.purge();
        }

        searchTimer = new Timer();
        searchTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> performSearch());
            }
        }, SEARCH_DELAY_MS);
    }

    /**
     * Perform immediate search.
     */
    public void performSearch() {
        String searchText = searchField.getText().trim();

        if (searchText.isEmpty()) {
            clearSearchState();
            return;
        }

        // Reset offset if search text changed
        if (!searchText.equals(lastSearchText)) {
            currentOffset = 0;
            resultsListView.getItems().clear();
        }

        lastSearchText = searchText;

        // Search with owner filter
        List<DatabaseManager.Term> results = dbManager.searchTerms(
                searchText, RESULTS_PER_PAGE, currentOffset, currentOwner);

        currentSearchResults = results;
        selectedTerm = null;

        // Update UI
        if (currentOffset == 0) {
            resultsListView.getItems().clear();
        }
        resultsListView.getItems().addAll(results);

        // Show/hide "Load More" button
        if (results.size() >= RESULTS_PER_PAGE) {
            loadMoreButton.setVisible(true);
        } else {
            loadMoreButton.setVisible(false);
        }

        // Update status
        int totalShown = resultsListView.getItems().size();
        String statusMessage = "Encontrados " + results.size() + " resultados";
        if (currentOwner != null && !currentOwner.equals("ALL")) {
            statusMessage += " (filtrado por: " + currentOwner + ")";
        }
        operationStatusLabel.setText(statusMessage);

        // Select first result
        if (!results.isEmpty() && currentOffset == 0) {
            resultsListView.getSelectionModel().select(0);
            selectedTerm = results.get(0);
        } else if (results.isEmpty()) {
            detailsArea.clear();
        }

        // Callback
        if (callback != null) {
            callback.onSearchComplete(results, totalShown);
        }
    }

    /**
     * Load more search results (pagination).
     */
    public void loadMoreSearchResults() {
        if (lastSearchText.isEmpty()) {
            return;
        }

        currentOffset += RESULTS_PER_PAGE;

        List<DatabaseManager.Term> results = dbManager.searchTerms(
                lastSearchText,
                RESULTS_PER_PAGE,
                currentOffset,
                currentOwner
        );

        if (!results.isEmpty()) {
            resultsListView.getItems().addAll(results);

            if (results.size() < RESULTS_PER_PAGE) {
                loadMoreButton.setVisible(false);
            }

            int totalShown = resultsListView.getItems().size();
            String statusMessage = "Mostrando " + totalShown + " resultados para '" + lastSearchText + "'.";
            updateStatus(statusMessage);
        } else {
            loadMoreButton.setVisible(false);
            int totalShown = resultsListView.getItems().size();
            String statusMessage = "Mostrando todos os " + totalShown + " resultados para '" + lastSearchText + "'.";
            updateStatus(statusMessage);
        }
    }

    /**
     * Clear search field and results.
     */
    public void clearSearch() {
        searchField.clear();
        clearSearchState();

        if (networkMonitor != null && networkMonitor.isOnline()) {
            Platform.runLater(() -> {
                String currentStatus = operationStatusLabel.getText();
                if (!currentStatus.contains("🟢") && !currentStatus.contains("🔴")) {
                    operationStatusLabel.setText("Pronto. Digite um termo para buscar.");
                }
            });
        } else {
            operationStatusLabel.setText("Pronto. Digite um termo para buscar.");
        }

        if (callback != null) {
            callback.onSearchCleared();
        }
    }

    /**
     * Refresh search if there's an active search.
     */
    public void refreshSearchIfNeeded() {
        if (lastSearchText != null && !lastSearchText.isEmpty()) {
            currentOffset = 0;
            performSearch();
        }
    }

    /**
     * Refresh current search (alias for refreshSearchIfNeeded).
     */
    public void refreshSearch() {
        refreshSearchIfNeeded();
    }

    /**
     * Load more results (handles both search and recents).
     */
    public void loadMoreResults() {
        loadMoreSearchResults();
    }

    /**
     * Open Tibetan search input helper.
     */
    public void openTibetanSearchInput() {
        TibetanInputHelper.openForTextField(searchField);
    }

    // ============================================================================
    // GETTERS
    // ============================================================================

    public List<DatabaseManager.Term> getCurrentSearchResults() {
        return currentSearchResults;
    }

    public DatabaseManager.Term getSelectedTerm() {
        return selectedTerm;
    }

    public void setSelectedTerm(DatabaseManager.Term term) {
        this.selectedTerm = term;
    }

    public String getLastSearchText() {
        return lastSearchText;
    }

    public String getCurrentOwner() {
        return currentOwner;
    }

    public boolean hasActiveSearch() {
        return lastSearchText != null && !lastSearchText.isEmpty();
    }

    // ============================================================================
    // PRIVATE HELPERS
    // ============================================================================

    private void clearSearchState() {
        resultsListView.getItems().clear();
        detailsArea.clear();
        operationStatusLabel.setText("Digite um termo para buscar.");
        currentSearchResults = null;
        selectedTerm = null;
        lastSearchText = "";
        currentOffset = 0;
        loadMoreButton.setVisible(false);
    }

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

    // ============================================================================
    // CLEANUP
    // ============================================================================

    public void cleanup() {
        if (searchTimer != null) {
            searchTimer.cancel();
            searchTimer.purge();
            searchTimer = null;
        }
    }
}
