package com.example.glossariobuda;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Main Controller for Glossario BUDA Application.
 * Refactored to follow Facade/Coordinator pattern - delegates all operations to specialized services.
 *
 * This controller acts as a thin coordination layer between the FXML UI and business logic services.
 * All heavy lifting is delegated to specialized service classes.
 *
 * Architecture:
 * - SearchService: Handles all search operations and pagination
 * - RecentTermsManager: Manages recent terms display
 * - TermCRUDHandler: Handles Create, Read, Update, Delete operations
 * - ImportExportManager: Handles import/export functionality
 * - AdminOperationsHandler: Handles admin operations (duplicates, reset)
 * - UIUpdateService: Handles UI setup and event routing
 *
 * @author GlossarioBuda Team
 * @version 2.0 - Refactored with Service Layer Pattern
 */
public class GlossarioController implements Initializable {

    // ============================================================================
    // FXML INJECTED COMPONENTS (Must be preserved for FXML binding)
    // ============================================================================

    @FXML private ComboBox<String> ownerFilter;
    @FXML private Button syncButton;
    @FXML private TextField searchField;
    @FXML private ListView<DatabaseManager.Term> resultsListView;
    @FXML private TextArea detailsArea;
    @FXML private Label operationStatusLabel;  // Left side - operation status
    @FXML private Label connectionStatusLabel;  // Right side - connection status
    @FXML private Button clearButton;
    @FXML private Button addTermButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button exportButton;
    @FXML private Button recentButton;
    @FXML private Button loadXMLButton;
    @FXML private Button compactViewButton;
    @FXML private Button loadMoreButton;
    @FXML private Button tibetanSearchButton;
    @FXML private Button openLinkButton;

    // ============================================================================
    // CORE DEPENDENCIES
    // ============================================================================

    private DatabaseManager dbManager;
    private NetworkStatusMonitor networkMonitor;
    private ExportManager exportManager;
    private TermDialogHelper dialogHelper;

    // ============================================================================
    // SPECIALIZED SERVICES (Delegation Targets)
    // ============================================================================

    private SearchService searchService;
    private RecentTermsManager recentsManager;
    private TermCRUDHandler crudHandler;
    private ImportExportManager importExportManager;
    private AdminOperationsHandler adminHandler;
    private UIUpdateService uiService;

    // ============================================================================
    // UI STATE
    // ============================================================================

    private Stage compactViewStage;
    private String currentOwner = "ALL";

    // Date formatters (used for term display)
    private static final java.time.format.DateTimeFormatter SQL_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final java.time.format.DateTimeFormatter OUTPUT_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Debouncing fields for status updates
    private String lastLoggedStatus = "";
    private long lastStatusLogTime = 0;
    private static final long STATUS_LOG_DEBOUNCE_MS = 2000; // 2 seconds

    public interface TermChangeListener {
        void onTermsChanged();
    }

    private TermChangeListener termChangeListener;

    public void setTermChangeListener(TermChangeListener listener) {
        this.termChangeListener = listener;
    }


    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeManagers();
        initializeServices();
        initializeNetworkMonitor();
        setupServiceCallbacks();

        // Delegate UI setup to UIUpdateService
        uiService.setupUI();

        // ONLY run auto-sync if NO reset was handled
        if (!dbManager.wasResetHandled()) {
            performAutoSync();
        } else {
            System.out.println("[AutoSync] Reset was handled during initialization - skipping auto-sync");
            Platform.runLater(() -> {
                if (networkMonitor != null) {
                    networkMonitor.setSyncSuccess("Glossário atualizado via reset");
                }
            });
        }

        if (dbManager != null) {
            setDatabaseManager(dbManager);
        }

    }

    /**
     * Initialize core managers (DatabaseManager, ExportManager, etc.)
     */
    private void initializeManagers() {
        if (dbManager == null) {
            dbManager = new DatabaseManager();
        }
        exportManager = new ExportManager(dbManager);
        dialogHelper = new TermDialogHelper(dbManager);
    }

    /**
     * Initialize all specialized services with their dependencies.
     * This is where the Facade pattern delegates to service implementations.
     */
    private void initializeServices() {
        // 1. Search Service
        searchService = new SearchService(
            dbManager,
            searchField,
            resultsListView,
            detailsArea,
            operationStatusLabel,
            loadMoreButton
        );

        // 2. Recent Terms Manager
        recentsManager = new RecentTermsManager(
            dbManager,
            resultsListView,
            detailsArea,
            operationStatusLabel,
            loadMoreButton
        );

        // 3. CRUD Handler
        crudHandler = new TermCRUDHandler(
            dbManager,
            dialogHelper,
            resultsListView,
            detailsArea,
            operationStatusLabel,
            openLinkButton
        );

        // 4. Import/Export Manager
        importExportManager = new ImportExportManager(
            dbManager,
            exportManager,
            operationStatusLabel
        );

        // 5. Admin Operations Handler
        adminHandler = new AdminOperationsHandler(
            dbManager,
            networkMonitor
        );

        // 6. UI Update Service
        UIComponents components = new UIComponents(
            ownerFilter,
            searchField,
            resultsListView,
            operationStatusLabel,
            connectionStatusLabel,
            clearButton,
            addTermButton,
            editButton,
            deleteButton,
            exportButton,
            recentButton,
            loadXMLButton,
            loadMoreButton,
            dbManager
        );
        uiService = new UIUpdateService(components);
    }

    /**
     * Initialize network monitoring for connection status.
     */
    private void initializeNetworkMonitor() {
        if (networkMonitor == null) {
            networkMonitor = new NetworkStatusMonitor();
        }

        // Start monitoring with UI update callback
        networkMonitor.startMonitoring((connectionStatus, operationStatus) -> {
            Platform.runLater(() -> {
                updateStatusBar(connectionStatus, operationStatus);
            });
        });

        // Connect network monitor to sync manager
        if (dbManager != null) {
            SyncManager syncManager = dbManager.getSyncManager();
            if (syncManager != null) {
                syncManager.setNetworkMonitor(networkMonitor);

                // Setup owner change callback for cloud sync and realtime updates
                syncManager.setOwnerChangeCallback(new SyncManager.OwnerChangeCallback() {
                    @Override
                    public void onOwnerChanged() {
                        Platform.runLater(() -> {
                            System.out.println("[GlossarioController] Owner changed - refreshing dropdown");
                            uiService.refreshOwnerFilter();
                            refreshTermViews();
                            if (termChangeListener != null) {
                                termChangeListener.onTermsChanged();
                            }
                        });
                    }
                });

                System.out.println("[GlossarioController] Network monitor connected to SyncManager");
            }
        }

        // Inject network monitor into services
        searchService.setNetworkMonitor(networkMonitor);
        recentsManager.setNetworkMonitor(networkMonitor);
        crudHandler.setNetworkMonitor(networkMonitor);
        importExportManager.setNetworkMonitor(networkMonitor);
        uiService.setNetworkMonitor(networkMonitor);
    }

    /**
     * Setup callbacks between services for coordination.
     * This is the heart of the Facade pattern - coordinating between services.
     */
    private void setupServiceCallbacks() {
        // 1. Search Service Callback
        searchService.setSearchCallback(new SearchService.SearchCallback() {
            @Override
            public void onSearchComplete(List<DatabaseManager.Term> results, int totalCount) {
                // Search completed - UI already updated by service
            }

            @Override
            public void onSearchCleared() {
                // Search cleared - show recent terms
                recentsManager.showRecentTermsByOwner();
            }

            @Override
            public void onFilterChanged(String owner) {
                currentOwner = owner;
            }
        });

        // 2. Recent Terms Manager Callback
        recentsManager.setRecentsCallback(new RecentTermsManager.RecentsCallback() {
            @Override
            public void onRecentsLoaded(List<DatabaseManager.Term> recents, boolean hasMore) {
                // Recents loaded - UI already updated by service
            }

            @Override
            public void onRecentsRefreshed() {
                // Recents refreshed successfully
            }
        });

        // 3. CRUD Handler Callback
        crudHandler.setCRUDCallback(new TermCRUDHandler.CRUDCallback() {
            @Override
            public void onTermAdded(String message) {
                Platform.runLater(() -> {
                    uiService.refreshOwnerFilter();
                    refreshTermViews();
                    // NEW: Notify compact view
                    if (termChangeListener != null) {
                        termChangeListener.onTermsChanged();
                    }
                });
            }

            @Override
            public void onTermUpdated(String message) {
                Platform.runLater(() -> {
                    uiService.refreshOwnerFilter();  // Also refresh on edit in case owner changed
                    refreshTermViews();
                    // NEW: Notify compact view
                    if (termChangeListener != null) {
                        termChangeListener.onTermsChanged();
                    }
                });
            }

            @Override
            public void onTermsDeleted(int count, String message) {
                Platform.runLater(() -> {
                    uiService.refreshOwnerFilter();
                    refreshTermViews();
                    // NEW: Notify compact view
                    if (termChangeListener != null) {
                        termChangeListener.onTermsChanged();
                    }
                });
            }

            @Override
            public void onOwnerChanged() {
                Platform.runLater(() -> {
                    uiService.refreshOwnerFilter();
                });
            }

            @Override
            public void onError(String operation, String error) {
                showAlert("Erro", "Erro em " + operation + ": " + error);
            }
        });

        // 4. Import/Export Manager Callback
        importExportManager.setImportExportCallback(new ImportExportManager.ImportExportCallback() {
            @Override
            public void onImportProgress(int current, int total, String message) {
                operationStatusLabel.setText(message);
            }

            @Override
            public void onImportComplete(int totalLoaded, String message) {
                uiService.refreshOwnerFilter();
                if (networkMonitor != null) {
                    networkMonitor.setSyncSuccess(message);
                }
            }

            @Override
            public void onImportError(String error) {
                showAlert("Erro de Importação", error);
            }

            @Override
            public void onExportComplete(String exportText) {
                // Export completed successfully
            }

            @Override
            public void onOwnerChanged() {
                uiService.refreshOwnerFilter();
            }
        });

        // 5. Admin Operations Handler Callback
        adminHandler.setAdminCallback(new AdminOperationsHandler.AdminCallback() {
            @Override
            public void onDuplicatesRemoved(int count) {
                refreshTermViews();
            }

            @Override
            public void onResetComplete(int deletedCount, int importedCount, int newVersion) {
                uiService.refreshOwnerFilter();
                refreshTermViews();
            }

            @Override
            public void onResetProgress(String message) {
                operationStatusLabel.setText(message);
            }

            @Override
            public void onResetError(String error) {
                showAlert("Erro de Reset", error);
            }

            @Override
            public void onOwnerChanged(String newOwner) {
                uiService.refreshOwnerFilter();
            }
        });

        // 6. UI Update Service Callbacks
        uiService.setUIEventCallback(new UIUpdateService.UIEventCallback() {
            @Override
            public void onSearchRequested() {
                searchService.performSearchWithDebounce();
            }

            @Override
            public void onClearRequested() {
                searchService.clearSearch();
            }

            @Override
            public void onAddTermRequested() {
                showAddTermDialog();
            }

            @Override
            public void onEditTermRequested() {
                editSelectedTerm();
            }

            @Override
            public void onDeleteTermRequested() {
                deleteSelectedTerm();
            }

            @Override
            public void onExportRequested() {
                showExportDialog();
            }

            @Override
            public void onRecentRequested() {
                showRecentTerms();
            }

            @Override
            public void onLoadXMLRequested() {
                loadXMLGlossary();
            }

            @Override
            public void onLoadMoreRequested() {
                loadMoreResults();
            }

            @Override
            public void onTermSelected(DatabaseManager.Term term) {
                crudHandler.setSelectedTerm(term);
                crudHandler.displayTermDetails(term);  // ← ADD THIS LINE

            }
        });

        uiService.setOwnerFilterCallback(new UIUpdateService.OwnerFilterCallback() {
            @Override
            public void onOwnerFilterChanged(String owner) {
                currentOwner = owner;
                searchService.setCurrentOwner(owner);
                recentsManager.setCurrentOwner(owner);

                // Refresh display
                if (searchField.getText().trim().isEmpty()) {
                    recentsManager.showRecentTermsByOwner();
                } else {
                    searchService.performSearch();
                }
            }
        });
    }

    private void refreshTermViews() {
        if (searchService == null || recentsManager == null) {
            return;
        }

        Runnable refreshTask = () -> {
            if (searchService.hasActiveSearch()) {
                searchService.refreshSearch();
            } else {
                recentsManager.setShowingRecents(true);
                recentsManager.refreshRecents();
            }
        };

        if (Platform.isFxApplicationThread()) {
            refreshTask.run();
        } else {
            Platform.runLater(refreshTask);
        }
    }

    /**
     * Perform automatic sync on startup (background thread).
     */
    private void performAutoSync() {
        Thread autoSyncThread = new Thread(() -> {
            try {
                Thread.sleep(2000);

                // Double-check - reset might have completed during sleep
                if (dbManager.wasResetHandled()) {
                    System.out.println("[AutoSync] Reset completed during wait - skipping auto-sync");
                    Platform.runLater(() -> {
                        if (networkMonitor != null) {
                            networkMonitor.setSyncSuccess("Atualizado via reset");
                        }
                    });
                    return;
                }

                // Update status to show syncing
                Platform.runLater(() -> {
                    if (networkMonitor != null) {
                        networkMonitor.setSyncing("Verificando novos termos...");
                    }
                });

                // Download new terms from cloud (background operation)
                DatabaseManager.SyncResult result = dbManager.syncWithCloud();

                // Update UI with result
                Platform.runLater(() -> {
                    if (networkMonitor != null) {
                        if (result.termsAdded > 0) {
                            networkMonitor.setSyncSuccess(
                                result.termsAdded + " novo(s) termo(s) baixado(s)"
                            );
                        } else {
                            networkMonitor.setSyncSuccess("Glossário atualizado");
                        }
                    }
                });

            } catch (Exception e) {
                System.err.println("[AutoSync] Failed: " + e.getMessage());
                Platform.runLater(() -> {
                    if (networkMonitor != null) {
                        networkMonitor.setSyncError("Falha na sincronização: " + e.getMessage());
                    }
                });
            } finally {
                // Always clear syncing status
                Platform.runLater(() -> {
                    if (networkMonitor != null) {
                        networkMonitor.setSyncing(null);
                    }
                });
            }
        });
        autoSyncThread.setName("AutoSync-Thread");
        autoSyncThread.setDaemon(true);
        autoSyncThread.start();
    }

    // ============================================================================
    // FXML EVENT HANDLERS (Must be preserved - bound to FXML)
    // ============================================================================

    /**
     * FXML Handler: Clear search field and show recent terms.
     */
    @FXML
    private void clearSearch() {
        searchService.clearSearch();
    }

    /**
     * FXML Handler: Show Tibetan search input dialog.
     */
    @FXML
    private void openTibetanSearchInput() {
        searchService.openTibetanSearchInput();
    }

    /**
     * FXML Handler: Delete selected term(s).
     */
    @FXML
    private void deleteSelectedTerm() {
        boolean showingRecents = recentsManager.isShowingRecents();
        crudHandler.deleteSelectedTerms(searchField.getScene().getWindow(), showingRecents);
    }

    /**
     * FXML Handler: Show export dialog.
     */
    @FXML
    private void showExportDialog() {
        importExportManager.showExportDialog(searchField.getScene().getWindow());
    }

    /**
     * FXML Handler: Show recent terms.
     */
    @FXML
    private void showRecentTerms() {
        recentsManager.showRecentTerms();
    }

    /**
     * FXML Handler: Load XML glossary file.
     */
    @FXML
    private void loadXMLGlossary() {
        importExportManager.loadXMLGlossary(searchField.getScene().getWindow());
    }

    /**
     * FXML Handler: Load more search results (pagination).
     */
    @FXML
    private void loadMoreResults() {
        if (recentsManager.isShowingRecents()) {
            recentsManager.loadMoreRecents();
        } else {
            searchService.loadMoreResults();
        }
    }

    /**
     * FXML Handler: Show add term dialog.
     */
    @FXML
    private void showAddTermDialog() {
        crudHandler.showAddTermDialog(searchField.getScene().getWindow());
    }

    /**
     * FXML Handler: Edit selected term.
     */
    @FXML
    private void editSelectedTerm() {
        crudHandler.editSelectedTerm(searchField.getScene().getWindow());
    }

    /**
     * FXML Handler: Open media link (audio/PDF) in browser.
     */
    @FXML
    private void openMediaLink() {
        crudHandler.openMediaLink();
    }

    /**
     * FXML Handler: Clean duplicate terms.
     */
    @FXML
    private void cleanDuplicates() {
        adminHandler.cleanDuplicates();
    }

    /**
     * FXML Handler: Main sync with cloud dialog.
     * This is a complex method that must remain in the controller as it manages
     * a multi-step dialog workflow and coordinates multiple services.
     */
    @FXML
    private void syncWithCloud() {
        if (dbManager == null) {
            showAlert("Erro", "Gerenciador de banco de dados não inicializado");
            return;
        }

        if (networkMonitor != null && !networkMonitor.isOnline()) {
            showAlert("Sem Conexão", "Não há conexão com a internet.");
            return;
        }

        Stage dialogStage = new Stage();
        dialogStage.setTitle("Sincronizando com Supabase");
        dialogStage.initOwner(searchField.getScene().getWindow());
        dialogStage.initModality(Modality.APPLICATION_MODAL);

        dialogStage.setWidth(800);
        dialogStage.setHeight(400);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Escolha o tipo de sincronização:");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label descLabel = new Label("Sincronização normal: baixa novos termos da nuvem");
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        CheckBox fullSyncCheckBox = new CheckBox("Sincronizar todos os termos locais para nuvem");
        fullSyncCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label checkboxHint = new Label("(Use isso apenas na primeira vez ou para resolver problemas)");
        checkboxHint.setStyle("-fx-font-size: 11px; -fx-text-fill: gray; -fx-padding: 0 0 0 25;");

        VBox statusBox = new VBox(20);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setVisible(false);

        Label statusLabelDialog = new Label("");
        statusLabelDialog.setStyle("-fx-font-size: 14px;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(60, 60);

        Label detailsLabel = new Label("");
        detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        statusBox.getChildren().addAll(statusLabelDialog, progress, detailsLabel);

        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Button startButton = new Button("Iniciar Sincronização");
        startButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");

        Button cancelButton = new Button("Cancelar");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 10 20;");
        cancelButton.setOnAction(e -> dialogStage.close());

        Button cleanDuplicatesButton = new Button("Limpar Duplicados");
        cleanDuplicatesButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        cleanDuplicatesButton.setOnAction(e -> {
            try {
                cleanDuplicates();
            } catch (Exception ex) {
                showAlert("Erro", "Erro ao limpar duplicados:\n" + ex.getMessage());
            }
        });

        Button adminResetButton = new Button("Reset Admin");
        System.out.println("[DEBUG] Admin Reset button created: " + adminResetButton);
        adminResetButton.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        adminResetButton.setOnAction(e -> {
            System.out.println("[DEBUG] ====== BUTTON CLICKED ======");
            dialogStage.close();  // Close sync dialog first

            // ✅ ADD A SMALL DELAY to ensure dialog is fully closed
            Platform.runLater(() -> {
                System.out.println("[DEBUG] Calling showAdminResetDialog after dialog close");
                adminHandler.showAdminResetDialog(searchField.getScene().getWindow());
            });
        });

        buttonBar.getChildren().addAll(startButton, cancelButton, cleanDuplicatesButton, adminResetButton);

        root.getChildren().addAll(titleLabel, descLabel, fullSyncCheckBox, checkboxHint, statusBox, buttonBar);

        Scene scene = new Scene(root, 400, 280);
        dialogStage.setScene(scene);

        try {
            dialogStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/pramana.png")));
        } catch (Exception ex) {
            System.err.println("Warning: Could not load dialog icon: " + ex.getMessage());
        }

        startButton.setOnAction(e -> {
            titleLabel.setVisible(false);
            descLabel.setVisible(false);
            fullSyncCheckBox.setVisible(false);
            checkboxHint.setVisible(false);
            buttonBar.setVisible(false);
            statusBox.setVisible(true);

            dialogStage.setOnCloseRequest(event -> event.consume());

            // Update network monitor
            if (networkMonitor != null) {
                networkMonitor.setSyncing("Sincronizando com a nuvem...");
            }

            Thread syncThread = new Thread(() -> {
                try {
                    DatabaseManager.SyncResult downloadResult = dbManager.fullBidirectionalSync();
                    int uploadedCount = 0;

                    Platform.runLater(() -> {
                        statusLabelDialog.setText("Baixando novos termos da nuvem...");
                        detailsLabel.setText("Sincronizando termos adicionados por outros usuários");
                    });

                    downloadResult = dbManager.syncWithCloud();

                    if (fullSyncCheckBox.isSelected()) {
                        Platform.runLater(() -> {
                            statusLabelDialog.setText("Enviando termos locais para nuvem...");
                            detailsLabel.setText("Isso pode levar alguns minutos...");
                        });

                        uploadedCount = dbManager.fullSyncToCloud();
                    }

                    DatabaseManager.SyncResult finalDownloadResult = downloadResult;
                    int finalUploadedCount = uploadedCount;

                    Platform.runLater(() -> {
                        dialogStage.close();

                        String message = String.format(
                            "Sincronização bidirecional completa!\n\n" +
                            "📥 Novos do cloud: %d\n" +
                            "⬇️ Atualizados do cloud: %d\n" +
                            "⬆️ Enviados para cloud: %d\n\n" +
                            "Cloud e local estão sincronizados!",
                            finalDownloadResult.termsAdded,
                            finalDownloadResult.termsUpdated,
                            finalDownloadResult.cloudUpdated
                        );

                        // Update network monitor with success
                        if (networkMonitor != null) {
                            networkMonitor.setSyncSuccess(String.format(
                                "%d termos baixados, %d enviados",
                                finalDownloadResult.termsAdded,
                                finalUploadedCount
                            ));
                        }

                        showSyncResultDialog(message, finalDownloadResult.termsAdded > 0 || finalUploadedCount > 0);

                        // Refresh owner filter if new terms were added
                        if (finalDownloadResult.termsAdded > 0) {
                            uiService.refreshOwnerFilter();
                        }

                        // Refresh search if user is searching
                        if (!searchField.getText().trim().isEmpty()) {
                            searchService.performSearch();
                        }
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        dialogStage.close();
                        if (networkMonitor != null) {
                            networkMonitor.setSyncError("Erro: " + ex.getMessage());
                        }
                        showAlert("Erro de Sincronização", "Erro ao sincronizar:\n" + ex.getMessage());
                    });
                } finally {
                    Platform.runLater(() -> {
                        if (networkMonitor != null) {
                            networkMonitor.setSyncing(null);
                        }
                    });
                }
            });
            syncThread.setName("Sync-Thread");
            syncThread.setDaemon(true);
            syncThread.start();
        });

        dialogStage.showAndWait();
    }

    /**
     * Show sync result dialog after sync completes.
     */
    private void showSyncResultDialog(String message, boolean hasChanges) {
        Alert resultAlert = new Alert(hasChanges ? Alert.AlertType.INFORMATION : Alert.AlertType.INFORMATION);
        resultAlert.setTitle("Sincronização Completa");
        resultAlert.setHeaderText(hasChanges ? "Sincronização realizada com sucesso!" : "Já está sincronizado");
        resultAlert.setContentText(message);

        resultAlert.setOnShown(e -> {
            try {
                Stage alertStage = (Stage) resultAlert.getDialogPane().getScene().getWindow();
                alertStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/pramana.png")));
            } catch (Exception ex) {
                System.err.println("Warning: Could not load dialog icon: " + ex.getMessage());
            }
        });

        resultAlert.showAndWait();
    }

    /**
     * FXML Handler: Open compact view window.
     * This method manages the compact view window lifecycle and must remain in controller.
     */
    @FXML
    private void openCompactView() {
        if (compactViewStage != null && compactViewStage.isShowing()) {
            compactViewStage.toFront();
            compactViewStage.requestFocus();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("compact-view.fxml"));
            VBox compactRoot = loader.load();

            CompactViewController compactController = loader.getController();
            Stage mainStage = (Stage) searchField.getScene().getWindow();
            compactController.setMainStage(mainStage);
            compactController.setDatabaseManager(dbManager);
            // Set up bidirectional listeners
            // When compact view changes terms, refresh main window
            compactController.setTermChangeListener(() -> {
                Platform.runLater(() -> {
                    System.out.println("[Main] Compact view changed terms - refreshing");
                    refreshTermViews();
                    uiService.refreshOwnerFilter();
                });
            });

            // When main window changes terms, refresh compact view
            this.setTermChangeListener(() -> {
                Platform.runLater(() -> {
                    System.out.println("[Compact] Main window changed terms - refreshing");
                    compactController.refreshCurrentView();
                });
            });

            compactViewStage = new Stage();
            compactViewStage.setTitle("Glossário BUDA - Vista Compacta");
            compactViewStage.initStyle(StageStyle.DECORATED);
            compactViewStage.setAlwaysOnTop(true);

            try {
                Image icon = new Image(getClass().getResourceAsStream("/icons/pramana.png"));
                compactViewStage.getIcons().add(icon);
            } catch (Exception e) {
                System.err.println("Warning: Could not load compact view icon: " + e.getMessage());
            }

            Scene scene = new Scene(compactRoot);
            compactViewStage.setScene(scene);

            compactViewStage.setWidth(340);
            compactViewStage.setHeight(600);

            compactViewStage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Erro", "Não foi possível abrir a vista compacta:\n" + e.getMessage());
        }
    }

    // ============================================================================
    // PUBLIC API (Must be preserved - used by external components)
    // ============================================================================

    /**
     * Set the database manager (dependency injection).
     * @param dbManager The database manager instance
     */
    public void setDatabaseManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        // IMPORTANT: Reconnect network monitor and callbacks to the new SyncManager
        if (networkMonitor != null && dbManager != null) {
            SyncManager syncManager = dbManager.getSyncManager();
            if (syncManager != null) {
                syncManager.setNetworkMonitor(networkMonitor);

                // Setup owner change callback for cloud sync and realtime updates
                syncManager.setOwnerChangeCallback(new SyncManager.OwnerChangeCallback() {
                    @Override
                    public void onOwnerChanged() {
                        Platform.runLater(() -> {
                            System.out.println("[GlossarioController] Owner changed - refreshing dropdown");
                            if (uiService != null) {
                                uiService.refreshOwnerFilter();
                            }
                            refreshTermViews();
                            if (termChangeListener != null) {
                                termChangeListener.onTermsChanged();
                            }
                        });
                    }
                });
            }
        }
    }

    /**
     * Get the network monitor instance.
     * @return The network status monitor
     */
    public NetworkStatusMonitor getNetworkMonitor() {
        return networkMonitor;
    }

    /**
     * Notify that queued operations were processed.
     * Called by OfflineQueueManager after processing queued operations.
     * @param count Number of operations processed
     */
    public void notifyQueueProcessed(int count) {
        Platform.runLater(() -> {
            if (networkMonitor != null) {
                networkMonitor.setSyncSuccess(count + " operação(ões) sincronizada(s)");
            }
        });
    }

    /**
     * Refresh search results if user is currently searching.
     * Public API used by external components to trigger refresh.
     */
    public void refreshSearchIfNeeded() {
        Platform.runLater(() -> {
            if (searchField != null && !searchField.getText().trim().isEmpty()) {
                System.out.println("[GlossarioController] Refreshing search results...");
                searchService.performSearch();
            }
        });
    }

    /**
     * Cleanup resources before shutdown.
     * Stops network monitoring and closes database connection.
     */
    public void cleanup() {
        // Cleanup services
        if (searchService != null) {
            searchService.cleanup();
        }

        // Stop network monitoring
        if (networkMonitor != null) {
            networkMonitor.stopMonitoring();
        }

        // Close database connection
        if (dbManager != null) {
            dbManager.close();
        }
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    private void updateStatusBar(String connectionStatus, String operationStatus) {
        if (uiService != null) {
            uiService.updateStatusBar(connectionStatus, operationStatus);
        }

        // Keep the debounce logging logic if needed
        if (operationStatus != null && !operationStatus.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if (!operationStatus.equals(lastLoggedStatus) ||
                    (currentTime - lastStatusLogTime) > STATUS_LOG_DEBOUNCE_MS) {
                lastLoggedStatus = operationStatus;
                lastStatusLogTime = currentTime;
            }
        }
    }

    /**
     * Show alert dialog with title and message.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.setOnShown(e -> {
            try {
                Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
                alertStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/pramana.png")));
            } catch (Exception ex) {
                System.err.println("Warning: Could not load alert icon: " + ex.getMessage());
            }
        });

        alert.showAndWait();
    }
}
