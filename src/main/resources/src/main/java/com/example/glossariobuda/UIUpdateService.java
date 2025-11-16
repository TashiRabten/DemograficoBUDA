package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.List;

/**
 * Service responsible for UI setup, event handlers, status updates, and owner filter management.
 * Extracted from GlossarioController to follow Single Responsibility Principle.
 */
public class UIUpdateService {

    // UI Components
    private final ComboBox<String> ownerFilter;
    private final TextField searchField;
    private final ListView<DatabaseManager.Term> resultsListView;
    private final Label operationStatusLabel;
    private final Label connectionStatusLabel;
    private final Button clearButton;
    private final Button addTermButton;
    private final Button editButton;
    private final Button deleteButton;
    private final Button exportButton;
    private final Button recentButton;
    private final Button loadXMLButton;
    private final Button loadMoreButton;

    // Dependencies
    private final DatabaseManager dbManager;
    private NetworkStatusMonitor networkMonitor;

    // State
    private String currentOwner = "ALL";
    private DatabaseManager.Term selectedTerm;

    // Callbacks
    private UIEventCallback eventCallback;
    private OwnerFilterCallback ownerFilterCallback;

    // ============================================================================
    // INTERFACES
    // ============================================================================

    public interface UIEventCallback {
        void onSearchRequested();
        void onClearRequested();
        void onAddTermRequested();
        void onEditTermRequested();
        void onDeleteTermRequested();
        void onExportRequested();
        void onRecentRequested();
        void onLoadXMLRequested();
        void onLoadMoreRequested();
        void onTermSelected(DatabaseManager.Term term);
    }

    public interface OwnerFilterCallback {
        void onOwnerFilterChanged(String owner);
    }

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================

    public UIUpdateService(UIComponents components) {
        this.ownerFilter = components.ownerFilter;
        this.searchField = components.searchField;
        this.resultsListView = components.resultsListView;
        this.operationStatusLabel = components.operationStatusLabel;
        this.connectionStatusLabel = components.connectionStatusLabel;
        this.clearButton = components.clearButton;
        this.addTermButton = components.addTermButton;
        this.editButton = components.editButton;
        this.deleteButton = components.deleteButton;
        this.exportButton = components.exportButton;
        this.recentButton = components.recentButton;
        this.loadXMLButton = components.loadXMLButton;
        this.loadMoreButton = components.loadMoreButton;
        this.dbManager = components.dbManager;
    }

    // ============================================================================
    // SETTERS
    // ============================================================================

    public void setNetworkMonitor(NetworkStatusMonitor monitor) {
        this.networkMonitor = monitor;
    }

    public void setEventCallback(UIEventCallback callback) {
        this.eventCallback = callback;
    }

    public void setUIEventCallback(UIEventCallback callback) {
        this.eventCallback = callback;
    }

    public void setOwnerFilterCallback(OwnerFilterCallback callback) {
        this.ownerFilterCallback = callback;
    }

    public void setSelectedTerm(DatabaseManager.Term term) {
        this.selectedTerm = term;
    }

    // ============================================================================
    // PUBLIC API - SETUP METHODS
    // ============================================================================

    /**
     * Setup all UI components (main entry point).
     */
    public void setupUI() {
        setupEventHandlers();
        setupButtonIcons();
        setupTextAreaInteraction();
        setupOwnerFilter();
    }

    /**
     * Setup all event handlers for UI components.
     */
    public void setupEventHandlers() {
        // Enable multiple selection with Shift/Ctrl
        resultsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Search on key release
        searchField.setOnKeyReleased(e -> {
            if (eventCallback != null) {
                eventCallback.onSearchRequested();
            }
        });

        // Update term display only for single selection
        resultsListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null && resultsListView.getSelectionModel().getSelectedIndices().size() == 1) {
                        selectedTerm = newValue;
                        if (eventCallback != null) {
                            eventCallback.onTermSelected(selectedTerm);
                        }
                    }
                }
        );

        // Add Delete key handler for multiple deletion
        resultsListView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.DELETE && eventCallback != null) {
                eventCallback.onDeleteTermRequested();
            }
        });

        // Double-click to edit
        resultsListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && selectedTerm != null && eventCallback != null) {
                eventCallback.onEditTermRequested();
            }
        });
    }

    /**
     * Setup button icons using ButtonImageUtils.
     */
    public void setupButtonIcons() {
        ButtonImageUtils.assignButtonIcon(clearButton, "clear");
        ButtonImageUtils.assignButtonIcon(addTermButton, "add");
        ButtonImageUtils.assignButtonIcon(editButton, "edit");
        ButtonImageUtils.assignButtonIcon(deleteButton, "delete");
        ButtonImageUtils.assignButtonIcon(exportButton, "export");
        ButtonImageUtils.assignButtonIcon(recentButton, "recent");
        ButtonImageUtils.assignButtonIcon(loadXMLButton, "add");
        ButtonImageUtils.assignButtonIcon(loadMoreButton, "navigation");
    }

    /**
     * Setup ListView cell factory and context menu.
     */
    public void setupTextAreaInteraction() {
        resultsListView.setCellFactory(lv -> new ListCell<DatabaseManager.Term>() {
            @Override
            protected void updateItem(DatabaseManager.Term term, boolean empty) {
                super.updateItem(term, empty);
                if (empty || term == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String displayText = term.getSourceTerm();
                    if (term.getTargetTerm() != null && !term.getTargetTerm().isEmpty()) {
                        displayText += " → " + term.getTargetTerm();
                    }
                    setText(displayText);
                }
            }
        });

        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Editar Termo");
        MenuItem deleteItem = new MenuItem("Deletar Termo");

        editItem.setOnAction(e -> {
            if (eventCallback != null) {
                eventCallback.onEditTermRequested();
            }
        });
        deleteItem.setOnAction(e -> {
            if (eventCallback != null) {
                eventCallback.onDeleteTermRequested();
            }
        });

        contextMenu.getItems().addAll(editItem, deleteItem);
        resultsListView.setContextMenu(contextMenu);
    }

    /**
     * Setup owner filter ComboBox.
     */
    public void setupOwnerFilter() {
        refreshOwnerFilter();

        ownerFilter.setOnAction(e -> {
            String selected = ownerFilter.getValue();

            if (selected == null || selected.equals("Todos os termos")) {
                currentOwner = "ALL";
            } else if (selected.equals("Compartilhados")) {
                currentOwner = "shared";
            } else {
                currentOwner = selected;
            }

            System.out.println("[UIUpdateService] Filtro mudado para: " + currentOwner);

            if (ownerFilterCallback != null) {
                ownerFilterCallback.onOwnerFilterChanged(currentOwner);
            }
        });
    }

    /**
     * Refresh owner filter dropdown with DB data.
     */
    public void refreshOwnerFilter() {
        String currentSelection = ownerFilter.getValue();

        // Temporarily disable handler to prevent interference during refresh
        ownerFilter.setOnAction(null);

        // Get unique owners from database
        List<String> owners = dbManager.getUniqueOwners();

        ownerFilter.getItems().clear();
        ownerFilter.getItems().add("Todos os termos");
        ownerFilter.getItems().add("Compartilhados");

        if (owners != null && !owners.isEmpty()) {
            for (String owner : owners) {
                // Skip 'shared' as we already added "Compartilhados"
                if (!"shared".equalsIgnoreCase(owner)) {
                    ownerFilter.getItems().add(owner);
                }
            }
        }

        // Restore previous selection if it still exists
        if (currentSelection != null && ownerFilter.getItems().contains(currentSelection)) {
            ownerFilter.setValue(currentSelection);
        } else {
            ownerFilter.setValue("Todos os termos");
            currentOwner = "ALL";
        }

        // Re-enable handler
        setupOwnerFilterHandler();
    }

    // ============================================================================
    // PUBLIC API - STATUS UPDATES
    // ============================================================================

    public void updateStatusBar(String connectionStatus, String operationStatus) {
        if (connectionStatus != null && !connectionStatus.isEmpty()) {
            connectionStatusLabel.setText(connectionStatus);

            // ADD THIS: Apply CSS styles based on connection status
            NetworkStatusMonitor.ConnectionState state = detectStateFromText(connectionStatus);
            updateConnectionStatusStyle(state);
        }

        if (operationStatus != null && !operationStatus.isEmpty()) {
            operationStatusLabel.setText(operationStatus);
        }
    }

    private void updateConnectionStatusStyle(NetworkStatusMonitor.ConnectionState state) {
        // Remove all status classes BUT KEEP the base class
        connectionStatusLabel.getStyleClass().removeAll(
                "status-online", "status-offline", "status-checking",
                "status-syncing", "status-success", "status-error", "status-queued"
        );

        // Ensure base class is present
        if (!connectionStatusLabel.getStyleClass().contains("connection-status-text")) {
            connectionStatusLabel.getStyleClass().add("connection-status-text");
        }

        // Add appropriate class based on state
        switch (state) {
            case ONLINE:
                connectionStatusLabel.getStyleClass().add("status-online");
                break;
            case OFFLINE:
                connectionStatusLabel.getStyleClass().add("status-offline");
                break;
            case CHECKING:
                connectionStatusLabel.getStyleClass().add("status-checking");
                break;
            case SYNCING:
                connectionStatusLabel.getStyleClass().add("status-syncing");
                break;
            case SYNC_SUCCESS:
                connectionStatusLabel.getStyleClass().add("status-success");
                break;
            case SYNC_ERROR:
                connectionStatusLabel.getStyleClass().add("status-error");
                break;
            case SYNC_QUEUED:
                connectionStatusLabel.getStyleClass().add("status-queued");
                break;
        }
    }

    /**
     * Update operation status message.
     */
    public void updateStatusMessage(String message) {
        Platform.runLater(() -> {
            operationStatusLabel.setText(message);
        });
    }

    /**
     * Force status refresh after operations complete.
     */
    public void forceStatusRefresh() {
        if (networkMonitor != null) {
            networkMonitor.forceCheck();
        }
    }

    /**
     * Show success message with delay.
     */
    public void showSuccessMessageWithDelay(String message) {
        if (networkMonitor != null) {
            networkMonitor.setSyncSuccess(message);
        } else {
            updateStatusMessage(message);
        }
    }

    // ============================================================================
    // PUBLIC API - DIALOGS
    // ============================================================================

    /**
     * Show generic alert dialog.
     */
    public void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Adjust automatically to content
        alert.getDialogPane().setMinWidth(500);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        alert.showAndWait();
    }

    /**
     * Detect ConnectionState from status text.
     */
    public NetworkStatusMonitor.ConnectionState detectStateFromText(String statusText) {
        if (statusText == null || statusText.isEmpty()) {
            return NetworkStatusMonitor.ConnectionState.OFFLINE;
        }

        String lower = statusText.toLowerCase();

        if (isOnlineState(lower)) return NetworkStatusMonitor.ConnectionState.ONLINE;
        if (isOfflineState(lower)) return NetworkStatusMonitor.ConnectionState.OFFLINE;
        if (isSyncingState(lower)) return NetworkStatusMonitor.ConnectionState.SYNCING;
        if (isSyncSuccessState(lower)) return NetworkStatusMonitor.ConnectionState.SYNC_SUCCESS;
        if (isSyncErrorState(lower)) return NetworkStatusMonitor.ConnectionState.SYNC_ERROR;
        if (isSyncQueuedState(lower)) return NetworkStatusMonitor.ConnectionState.SYNC_QUEUED;
        if (isCheckingState(lower)) return NetworkStatusMonitor.ConnectionState.CHECKING;

        return NetworkStatusMonitor.ConnectionState.OFFLINE;
    }

    private boolean isOnlineState(String lower) {
        return lower.contains("online") || lower.contains("🟢");
    }

    private boolean isOfflineState(String lower) {
        return lower.contains("offline") || lower.contains("🔴");
    }

    private boolean isSyncingState(String lower) {
        return lower.contains("sincronizando") || lower.contains("syncing");
    }

    private boolean isSyncSuccessState(String lower) {
        return lower.contains("✅") || lower.contains("sucesso") || lower.contains("success");
    }

    private boolean isSyncErrorState(String lower) {
        return lower.contains("❌") || lower.contains("erro") || lower.contains("error");
    }

    private boolean isSyncQueuedState(String lower) {
        return lower.contains("fila") || lower.contains("queued");
    }

    private boolean isCheckingState(String lower) {
        return lower.contains("verificando") || lower.contains("checking");
    }

    // ============================================================================
    // GETTERS
    // ============================================================================

    public String getCurrentOwner() {
        return currentOwner;
    }

    public DatabaseManager.Term getSelectedTerm() {
        return selectedTerm;
    }

    // ============================================================================
    // PRIVATE HELPERS
    // ============================================================================

    private void setupOwnerFilterHandler() {
        ownerFilter.setOnAction(e -> {
            String selected = ownerFilter.getValue();

            if (selected == null || selected.equals("Todos os termos")) {
                currentOwner = "ALL";
            } else if (selected.equals("Compartilhados")) {
                currentOwner = "shared";
            } else {
                currentOwner = selected;
            }

            System.out.println("[UIUpdateService] Filtro mudado para: " + currentOwner);

            if (ownerFilterCallback != null) {
                ownerFilterCallback.onOwnerFilterChanged(currentOwner);
            }
        });
    }
}
