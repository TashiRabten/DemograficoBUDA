package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.*;

/**
 * Controller for the compact view window
 * Provides quick access to glossary terms for copy-paste purposes
 */
public class CompactViewController implements Initializable {
    @FXML private ComboBox<String> ownerFilterCompact;
    @FXML private TextField searchField;
    @FXML private Button clearButton;
    @FXML private Button addButton;
    @FXML private Button expandButton;
    @FXML private ListView<String> resultsListView;
    @FXML private Button toggleDiacriticsButton;
    @FXML private VBox diacriticBox;
    @FXML private TextField diacriticSearchField;
    @FXML private FlowPane diacriticPanel;
    @FXML private ToggleButton sanskritToggle;
    @FXML private ToggleButton chineseToggle;
    @FXML private HBox sourceBox;
    @FXML private Label sourceLabel;
    @FXML private Button copySourceButton;
    @FXML private HBox targetBox;
    @FXML private Label targetLabel;
    @FXML private Button copyTargetButton;
    @FXML private Label translationLabel;
    @FXML private TextArea detailsTextArea;
    @FXML private Button toggleDetailsButton;
    @FXML private VBox detailsBox;
    @FXML private Label statusLabel;
    @FXML private Button loadMoreButton;
    @FXML private Button tibetanSearchButton;
    @FXML private Button openLinkButton;


    private DatabaseManager dbManager;
    private String currentOwner = "ALL"; // Filtro de proprietário
    TermDialogHelper dialogHelper = new TermDialogHelper(dbManager);
    private List<DatabaseManager.Term> currentSearchResults;
    private DatabaseManager.Term selectedTerm;
    private boolean detailsVisible = false;
    private boolean diacriticsVisible = false;
    private boolean sanskritMode = true; // Default to Sanskrit
    private Stage mainStage; // Reference to main window
    private String currentMediaLink = null; // Store the current link URL

    // Pagination state
    private String lastSearchText = "";
    private int currentOffset = 0;
    private static final int RESULTS_PER_PAGE = 50;

    // Debounce for search
    private java.util.Timer searchTimer;
    private static final int SEARCH_DELAY_MS = 300; // 300ms delay

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        setupEventHandlers();
        initializeResultsList();
        initializeDiacriticHelper();

        // Setup owner change callback for cloud sync and realtime updates
        if (dbManager != null) {
            SyncManager syncManager = dbManager.getSyncManager();
            if (syncManager != null) {
                syncManager.setOwnerChangeCallback(new SyncManager.OwnerChangeCallback() {
                    @Override
                    public void onOwnerChanged() {
                        Platform.runLater(() -> {
                            System.out.println("[CompactViewController] Owner changed - refreshing dropdown");
                            refreshOwnerFilter();
                        });
                    }
                });
                System.out.println("[CompactViewController] Owner change callback connected to SyncManager");
            }
        }
    }

    private void setupEventHandlers() {
        // Enable multiple selection with Shift/Ctrl
        resultsListView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        // Search with debounce to avoid performance issues
        searchField.setOnKeyReleased(e -> performSearchWithDebounce());

        // Select term when clicked in list (only for single selection)
        resultsListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                // Only update term display if it's a single selection
                // This prevents breaking multi-selection with Shift
                if (newValue != null && resultsListView.getSelectionModel().getSelectedIndices().size() == 1) {
                    selectTermFromList();
                }
            }
        );

        // Add double-click to edit
        resultsListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && selectedTerm != null) {
                editSelectedTerm();
            }
        });

        // Add Delete key handler for multiple deletion
        resultsListView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.DELETE) {
                deleteSelectedTerms();
            }
        });

        // Diacritic search
        diacriticSearchField.setOnKeyReleased(e -> updateDiacriticPanel());
    }

    private void setupOwnerFilter() {
        if (ownerFilterCompact == null) {
            System.out.println("[CompactView] Owner filter not found in FXML - skipping");
            return;
        }

        refreshOwnerFilter();

        ownerFilterCompact.setOnAction(e -> {
            updateCurrentOwnerFromCombo();

            System.out.println("[CompactView] Filtro mudado para: " + currentOwner);

            // Refazer busca se houver texto
            if (!searchField.getText().trim().isEmpty()) {
                currentOffset = 0;
                performSearch();
            }
        });
    }

// 4. ADICIONAR método refreshOwnerFilter():

    private void refreshOwnerFilter() {
        if (ownerFilterCompact == null) {
            return;
        }

        String currentSelection = ownerFilterCompact.getValue();

        // Temporarily disable handler to prevent interference during refresh
        ownerFilterCompact.setOnAction(null);

        ownerFilterCompact.getItems().clear();
        ownerFilterCompact.getItems().add("Todos");
        ownerFilterCompact.getItems().add("Compartilhados");

        // Get actual owners from database
        List<String> owners = dbManager.getAllOwners();
        ownerFilterCompact.getItems().addAll(owners);

        // Restore selection or default
        if (currentSelection != null && ownerFilterCompact.getItems().contains(currentSelection)) {
            ownerFilterCompact.setValue(currentSelection);
        } else {
            ownerFilterCompact.setValue("Todos");
        }

        // Sync currentOwner
        updateCurrentOwnerFromCombo();

        // Re-enable handler
        ownerFilterCompact.setOnAction(e -> {
            updateCurrentOwnerFromCombo();
            System.out.println("[CompactView] Filtro mudado para: " + currentOwner);

            // Refazer busca se houver texto
            if (!searchField.getText().trim().isEmpty()) {
                currentOffset = 0;
                performSearch();
            }
        });

        System.out.println("[CompactView] Owner filter refreshed. Found " + owners.size() + " owners");
    }

// 5. ADICIONAR método updateCurrentOwnerFromCombo():

    private void updateCurrentOwnerFromCombo() {
        if (ownerFilterCompact == null) {
            return;
        }

        String selected = ownerFilterCompact.getValue();

        if (selected == null || selected.equals("Todos")) {
            currentOwner = "ALL";
        } else if (selected.equals("Compartilhados")) {
            currentOwner = "shared";
        } else {
            currentOwner = selected;
        }

        System.out.println("[CompactView] updateCurrentOwnerFromCombo: '" + selected + "' → currentOwner: '" + currentOwner + "'");
    }

    // 6. ATUALIZAR showAddTermDialog() para refresh:

    @FXML
    private void showAddTermDialog() {
        dialogHelper.showAddDialog(searchField.getScene().getWindow(), dbManager, new TermDialogHelper.TermOperationCallback() {
            @Override
            public void onSuccess(String message) {
                statusLabel.setText(message);

                // ✅ ADICIONAR: Refresh owner filter
                refreshOwnerFilter();

                // Cancel any pending debounced search
                if (searchTimer != null) {
                    searchTimer.cancel();
                    searchTimer = null;
                }
                resultsListView.getItems().clear();
                currentSearchResults = null;
                lastSearchText = "";
                currentOffset = 0;
                performSearch();
                notifyTermsChanged();

                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        Platform.runLater(() -> statusLabel.setText("Pronto"));
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }

            @Override
            public void onError(String message) {
                statusLabel.setText(message);
            }
        });
    }

// 7. ATUALIZAR editSelectedTerm() para refresh:

    private void editSelectedTerm() {
        if (selectedTerm == null) {
            statusLabel.setText("❌ Nenhum termo selecionado");
            return;
        }

        dialogHelper.showEditDialog(searchField.getScene().getWindow(), dbManager, selectedTerm, new TermDialogHelper.TermOperationCallback() {
            @Override
            public void onSuccess(String message) {
                statusLabel.setText(message);
                System.out.println("DEBUG [EDIT] - Edit succeeded, refreshing search");

                // ✅ ADICIONAR: Refresh owner filter
                refreshOwnerFilter();

                if (searchTimer != null) {
                    searchTimer.cancel();
                    searchTimer = null;
                }
                resultsListView.getItems().clear();
                currentSearchResults = null;
                lastSearchText = "";
                currentOffset = 0;
                performSearch();
                notifyTermsChanged();

                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        Platform.runLater(() -> statusLabel.setText("Pronto"));
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }

            @Override
            public void onError(String message) {
                statusLabel.setText(message);
            }
        });
    }


    /**
     * Inject shared DatabaseManager from main window
     */
    public void setDatabaseManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        // Recreate dialogHelper with injected dbManager
        this.dialogHelper = new TermDialogHelper(dbManager);
        // Setup owner filter NOW that we have the dbManager
        setupOwnerFilter();
        // Setup owner change callback NOW that we have the dbManager
        setupOwnerChangeCallback();
    }

    private void setupOwnerChangeCallback() {
        if (dbManager != null) {
            SyncManager syncManager = dbManager.getSyncManager();
            if (syncManager != null) {
                syncManager.setOwnerChangeCallback(new SyncManager.OwnerChangeCallback() {
                    @Override
                    public void onOwnerChanged() {
                        Platform.runLater(() -> {
                            System.out.println("[CompactViewController] Owner changed - refreshing dropdown");
                            refreshOwnerFilter();
                        });
                    }
                });
                System.out.println("[CompactViewController] Owner change callback connected to SHARED SyncManager");
            }
        }
    }

    public interface TermChangeListener {
        void onTermsChanged();
    }

    private TermChangeListener termChangeListener;

    public void setTermChangeListener(TermChangeListener listener) {
        this.termChangeListener = listener;
    }

    // Notify listener when terms change
    private void notifyTermsChanged() {
        if (termChangeListener != null) {
            termChangeListener.onTermsChanged();
        }
    }

    // Refresh current view (called from main window)
    public void refreshCurrentView() {
        if (!searchField.getText().trim().isEmpty()) {
            // Reset and refresh search
            currentOffset = 0;
            performSearch();
        }
        // Always refresh owner filter
        refreshOwnerFilter();
    }

    /**
     * Perform search with debounce to avoid performance issues when typing fast
     */
    private void performSearchWithDebounce() {
        // Cancel previous timer if exists
        if (searchTimer != null) {
            searchTimer.cancel();
        }

        // Create new timer
        searchTimer = new java.util.Timer();
        searchTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> performSearch());
            }
        }, SEARCH_DELAY_MS);
    }

    private void initializeDiacriticHelper() {
        // Populate diacritics on first load (but panel is hidden)
        updateDiacriticPanel();
    }

    @FXML
    private void toggleDiacritics() {
        diacriticsVisible = !diacriticsVisible;
        diacriticBox.setVisible(diacriticsVisible);
        diacriticBox.setManaged(diacriticsVisible);
        toggleDiacriticsButton.setText(diacriticsVisible ? "Ocultar Diacríticos" : "Mostrar Diacríticos");
    }

    @FXML
    private void toggleSanskritMode() {
        if (sanskritToggle.isSelected()) {
            sanskritMode = true;
            chineseToggle.setSelected(false);
            updateDiacriticPanel();
        } else {
            // Don't allow deselecting if it's the only one selected
            sanskritToggle.setSelected(true);
        }
    }

    @FXML
    private void toggleChineseMode() {
        if (chineseToggle.isSelected()) {
            sanskritMode = false;
            sanskritToggle.setSelected(false);
            updateDiacriticPanel();
        } else {
            // Don't allow deselecting if it's the only one selected
            chineseToggle.setSelected(true);
        }
    }

    private void updateDiacriticPanel() {
        String searchLetter = diacriticSearchField.getText().toLowerCase().trim();
        diacriticPanel.getChildren().clear();

        if (sanskritMode) {
            // Sanskrit/Pali diacritic variants
            Map<String, List<String>> diacritics = new HashMap<>();
            diacritics.put("a", Arrays.asList("ā", "á", "à", "ă", "ǎ", "â", "ä", "ã"));
            diacritics.put("i", Arrays.asList("ī", "í", "ì", "ĭ", "î", "ï"));
            diacritics.put("u", Arrays.asList("ū", "ú", "ù", "ŭ", "û", "ü"));
            diacritics.put("e", Arrays.asList("ē", "é", "è", "ĕ", "ě", "ê", "ë"));
            diacritics.put("o", Arrays.asList("ō", "ó", "ò", "ŏ", "ô", "ö", "õ"));
            diacritics.put("r", Arrays.asList("ṛ", "ṝ", "ř", "ŕ"));
            diacritics.put("l", Arrays.asList("ḷ", "ḹ", "ł", "ĺ"));
            diacritics.put("m", Arrays.asList("ṃ", "ṁ"));
            diacritics.put("n", Arrays.asList("ṅ", "ñ", "ṇ", "ń", "ň"));
            diacritics.put("s", Arrays.asList("ś", "ṣ", "š", "ṡ"));
            diacritics.put("t", Arrays.asList("ṭ", "ť", "ṯ"));
            diacritics.put("d", Arrays.asList("ḍ", "ḏ", "ď"));
            diacritics.put("h", Arrays.asList("ḥ", "ḫ"));
            diacritics.put("c", Arrays.asList("ć", "č", "ç"));
            diacritics.put("z", Arrays.asList("ž", "ź", "ż"));

            // If search is empty, show most common ones
            if (searchLetter.isEmpty()) {
                String[] common = {"ā", "ī", "ū", "ṃ", "ṅ", "ñ", "ṇ", "ś", "ṣ", "ṭ", "ḍ", "ḥ", "ṛ", "ḷ"};
                for (String c : common) {
                    addDiacriticButton(c);
                }
            } else {
                // Show variants for the typed letter
                List<String> variants = diacritics.get(searchLetter);
                if (variants != null) {
                    for (String variant : variants) {
                        addDiacriticButton(variant);
                    }
                }
            }
        } else {
            // Chinese Pinyin with tone marks
            Map<String, List<String>> chinesePinyin = new HashMap<>();
            chinesePinyin.put("a", Arrays.asList("ā", "á", "ǎ", "à"));
            chinesePinyin.put("e", Arrays.asList("ē", "é", "ě", "è"));
            chinesePinyin.put("i", Arrays.asList("ī", "í", "ǐ", "ì"));
            chinesePinyin.put("o", Arrays.asList("ō", "ó", "ǒ", "ò"));
            chinesePinyin.put("u", Arrays.asList("ū", "ú", "ǔ", "ù", "ü", "ǖ", "ǘ", "ǚ", "ǜ"));
            chinesePinyin.put("v", Arrays.asList("ü", "ǖ", "ǘ", "ǚ", "ǜ")); // v = ü for easier typing

            // If search is empty, show all tones for common vowels
            if (searchLetter.isEmpty()) {
                String[] common = {"ā", "á", "ǎ", "à", "ē", "é", "ě", "è", "ī", "í", "ǐ", "ì",
                                   "ō", "ó", "ǒ", "ò", "ū", "ú", "ǔ", "ù", "ü", "ǖ", "ǘ", "ǚ", "ǜ"};
                for (String c : common) {
                    addDiacriticButton(c);
                }
            } else {
                // Show tone variants for the typed letter
                List<String> variants = chinesePinyin.get(searchLetter);
                if (variants != null) {
                    for (String variant : variants) {
                        addDiacriticButton(variant);
                    }
                }
            }
        }
    }

    private void addDiacriticButton(String character) {
        Button btn = new Button(character);
        btn.setStyle("-fx-font-size: 16px; -fx-padding: 5px 10px;");
        btn.getStyleClass().add("compact-button");

        // Copy to clipboard when clicked
        btn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(character);
            clipboard.setContent(content);

            statusLabel.setText("✓ Copiado: " + character);

            // Reset status after 2 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> statusLabel.setText("Pronto"));
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }).start();
        });

        diacriticPanel.getChildren().add(btn);
    }

    private void initializeResultsList() {
        resultsListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                }
            }
        });
    }

    @FXML
    private void clearSearch() {
        searchField.clear();
        resultsListView.getItems().clear();
        clearTermDisplay();
        statusLabel.setText("Digite um termo para buscar");
        lastSearchText = "";
        currentOffset = 0;
        loadMoreButton.setVisible(false);
    }

    @FXML
    private void openTibetanSearchInput() {
        TibetanInputHelper.openForTextField(searchField);
    }

    @FXML
    private void expandToFullView() {
        // Open or focus the main window
        if (mainStage != null) {
            // If minimized to taskbar, restore it
            if (mainStage.isIconified()) {
                mainStage.setIconified(false);
            }

            // Show the window if it's hidden
            mainStage.show();

            // Bring to front and request focus
            mainStage.toFront();
            mainStage.requestFocus();
        }
    }

    @FXML
    private void copySource() {
        if (selectedTerm != null) {
            String textToCopy = selectedTerm.getSourceTerm();
            String language = selectedTerm.getSourceLanguage();
            copyToClipboard(textToCopy, language != null ? language : "Termo");
        }
    }

    @FXML
    private void copyTarget() {
        if (selectedTerm != null) {
            String textToCopy = selectedTerm.getTargetTerm();
            String language = selectedTerm.getTargetLanguage();
            copyToClipboard(textToCopy, language != null ? language : "Tradução");
        }
    }

    private void copyToClipboard(String text, String label) {
        if (text != null && !text.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);

            // Show brief feedback
            String displayText = text.length() > 30 ? text.substring(0, 30) + "..." : text;
            statusLabel.setText("✓ " + label + " copiado: " + displayText);

            // Reset status after 2 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> statusLabel.setText("Pronto"));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    @FXML
    private void toggleDetails() {
        detailsVisible = !detailsVisible;
        detailsBox.setVisible(detailsVisible);
        detailsBox.setManaged(detailsVisible);
        toggleDetailsButton.setText(detailsVisible ? "Ocultar detalhes" : "Ver mais detalhes");
    }

    private void performSearch() {
        String searchText = searchField.getText().trim();
        System.out.println("DEBUG [SEARCH] - performSearch() called with: '" + searchText + "'");

        if (searchText.isEmpty()) {
            resultsListView.getItems().clear();
            clearTermDisplay();
            statusLabel.setText("Digite um termo para buscar");
            lastSearchText = "";
            currentOffset = 0;
            loadMoreButton.setVisible(false);
            return;
        }

        if (!searchText.equals(lastSearchText)) {
            System.out.println("DEBUG [SEARCH] - Search text changed from '" + lastSearchText + "' to '" + searchText + "'");
            currentOffset = 0;
            resultsListView.getItems().clear();
            System.out.println("DEBUG [SEARCH] - ListView cleared, size now: " + resultsListView.getItems().size());
        }

        lastSearchText = searchText;

        // ✅ CORRIGIDO: Adicionar parâmetro currentOwner
        System.out.println("DEBUG [SEARCH] - Calling dbManager.searchTerms with offset=" + currentOffset + ", owner=" + currentOwner);
        List<DatabaseManager.Term> results = dbManager.searchTerms(searchText, RESULTS_PER_PAGE, currentOffset, currentOwner);
        System.out.println("DEBUG [SEARCH] - Got " + results.size() + " results from database");
        currentSearchResults = results;

        if (currentOffset == 0) {
            System.out.println("DEBUG [SEARCH] - Offset is 0, clearing ListView to ensure fresh results");
            resultsListView.getItems().clear();
        }

        System.out.println("DEBUG [SEARCH] - ListView size before adding: " + resultsListView.getItems().size());
        for (DatabaseManager.Term term : results) {
            String displayText = term.getSourceTerm();
            if (term.getTargetTerm() != null && !term.getTargetTerm().isEmpty()) {
                displayText += " → " + term.getTargetTerm();
            }
            System.out.println("DEBUG [SEARCH] - Adding to ListView: '" + displayText + "' (ID: " + term.getId() + ")");
            resultsListView.getItems().add(displayText);
        }
        System.out.println("DEBUG [SEARCH] - ListView size after adding: " + resultsListView.getItems().size());

        if (results.size() >= RESULTS_PER_PAGE) {
            loadMoreButton.setVisible(true);
        } else {
            loadMoreButton.setVisible(false);
        }

        int totalShown = resultsListView.getItems().size();
        if (results.size() >= RESULTS_PER_PAGE) {
            statusLabel.setText(totalShown + " resultados. Clique 'Carregar mais'.");
        } else if (currentOffset > 0) {
            statusLabel.setText("Todos os " + totalShown + " resultados");
        } else {
            statusLabel.setText("Encontrados " + results.size() + " resultados");
        }

        if (!results.isEmpty() && currentOffset == 0) {
            resultsListView.getSelectionModel().select(0);
            selectTermFromList();
        } else if (results.isEmpty()) {
            clearTermDisplay();
        }
    }


    @FXML
    private void loadMoreResults() {
        if (lastSearchText.isEmpty()) {
            return;
        }

        System.out.println("DEBUG [LOADMORE] - Loading more results. currentOffset=" + currentOffset + ", currentSearchResults.size()=" + currentSearchResults.size());

        currentOffset += RESULTS_PER_PAGE;

        // ✅ CORRIGIDO: Adicionar parâmetro currentOwner
        List<DatabaseManager.Term> results = dbManager.searchTerms(
                lastSearchText,
                RESULTS_PER_PAGE,
                currentOffset,
                currentOwner  // ← ADICIONAR ESTE PARÂMETRO
        );

        System.out.println("DEBUG [LOADMORE] - Got " + results.size() + " more results from database");

        if (!results.isEmpty()) {
            for (DatabaseManager.Term term : results) {
                String displayText = term.getSourceTerm();
                if (term.getTargetTerm() != null && !term.getTargetTerm().isEmpty()) {
                    displayText += " → " + term.getTargetTerm();
                }
                System.out.println("DEBUG [LOADMORE] - Adding to ListView: '" + displayText + "' (ID: " + term.getId() + ")");
                resultsListView.getItems().add(displayText);
            }
            System.out.println("DEBUG [LOADMORE] - WARNING: currentSearchResults NOT UPDATED! Still has " + currentSearchResults.size() + " items, but ListView has " + resultsListView.getItems().size());

            if (results.size() < RESULTS_PER_PAGE) {
                loadMoreButton.setVisible(false);
            }

            int totalShown = resultsListView.getItems().size();
            statusLabel.setText("Mostrando " + totalShown + " resultados");
        } else {
            loadMoreButton.setVisible(false);
            int totalShown = resultsListView.getItems().size();
            statusLabel.setText("Todos os " + totalShown + " resultados");
        }
    }


    private void selectTermFromList() {
        int selectedIndex = resultsListView.getSelectionModel().getSelectedIndex();

        if (selectedIndex >= 0 && selectedIndex < currentSearchResults.size()) {
            selectedTerm = currentSearchResults.get(selectedIndex);
            displayTermDetails(selectedTerm);
        }
    }

    private void displayTermDetails(DatabaseManager.Term term) {
        if (term == null) {
            clearTermDisplay();
            return;
        }

        // Show source term (any language)
        if (term.getSourceTerm() != null && !term.getSourceTerm().isEmpty()) {
            sourceLabel.setText(term.getSourceTerm());
            sourceBox.setVisible(true);
            sourceBox.setManaged(true);
            // Update button text to show language
        } else {
            sourceBox.setVisible(false);
            sourceBox.setManaged(false);
        }

        // Show target term (any language)
        if (term.getTargetTerm() != null && !term.getTargetTerm().isEmpty()) {
            // Convert literal \n to actual newlines for display
            String displayTarget = convertNewlinesToActual(term.getTargetTerm());
            targetLabel.setText(displayTarget);
            targetBox.setVisible(true);
            targetBox.setManaged(true);
            // Update button text to show language
        } else {
            targetBox.setVisible(false);
            targetBox.setManaged(false);
        }

        // Display translation info
        String translation = term.getSourceLanguage() + " → " + term.getTargetLanguage();
        translationLabel.setText(translation);

        // Display additional details
        updateDetailLabels(term);
    }
    private void updateDetailLabels(DatabaseManager.Term term) {
        // Build combined details text with all information
        StringBuilder detailsText = new StringBuilder();

        // ✅ FIXED: Handle List<String> for contributors
        List<String> contributors = term.getContributors();
        if (contributors != null && !contributors.isEmpty()) {
            detailsText.append("👤 Contribuidor");
            if (contributors.size() > 1) {
                detailsText.append("es");
            }
            detailsText.append(": ").append(String.join(", ", contributors)).append("\n\n");
        }

        // ✅ FIXED: Handle List<String> for contexts
        List<String> contexts = term.getContexts();
        if (contexts != null && !contexts.isEmpty()) {
            detailsText.append("📖 Contexto");
            if (contexts.size() > 1) {
                detailsText.append("s");
            }
            detailsText.append(":\n");
            for (int i = 0; i < contexts.size(); i++) {
                if (contexts.size() > 1) {
                    detailsText.append((i + 1)).append(". ");
                }
                // Convert literal \n to actual newlines for display
                String displayContext = convertNewlinesToActual(contexts.get(i));
                detailsText.append(displayContext);
                if (i < contexts.size() - 1) {
                    detailsText.append("\n");
                }
            }
            detailsText.append("\n\n");
        }

        // Show full notes field if available
        String notes = term.getNotes();
        if (notes != null && !notes.trim().isEmpty()) {
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
                    detailsText.append("📌 Notas: Aperte o Botão de Áudio");
                } else if (extractedUrl.contains(".pdf")) {
                    openLinkButton.setText("📄 Abrir PDF");
                    detailsText.append("📌 Notas: Aperte o Botão de PDF");
                } else {
                    openLinkButton.setText("🔗 Abrir Link");
                    detailsText.append("📌 Notas: Aperte o Botão de Link");
                }

                // Add cleaned notes content if there's any
                if (!cleanedNotes.trim().isEmpty()) {
                    String displayNotes = convertNewlinesToActual(cleanedNotes);
                    detailsText.append("\n").append(displayNotes);
                }
                detailsText.append("\n");
            } else {
                // No link found, show notes as-is
                currentMediaLink = null;
                openLinkButton.setVisible(false);
                openLinkButton.setManaged(false);
                // Convert literal \n to actual newlines for display
                String displayNotes = convertNewlinesToActual(notes);
                detailsText.append("📌 Notas:\n").append(displayNotes);
            }
        } else {
            currentMediaLink = null;
            openLinkButton.setVisible(false);
            openLinkButton.setManaged(false);
        }

        // Set all combined text to single text area
        if (detailsText.length() > 0) {
            detailsTextArea.setText(detailsText.toString().trim());
            detailsTextArea.setVisible(true);
            detailsTextArea.setManaged(true);
        } else {
            detailsTextArea.setVisible(false);
            detailsTextArea.setManaged(false);
        }
    }

    private void clearTermDisplay() {
        sourceLabel.setText("");
        sourceBox.setVisible(false);
        sourceBox.setManaged(false);
        targetLabel.setText("");
        targetBox.setVisible(false);
        targetBox.setManaged(false);
        translationLabel.setText("");
        detailsTextArea.setText("");
        detailsTextArea.setVisible(false);
        detailsTextArea.setManaged(false);
        selectedTerm = null;
        currentMediaLink = null;
        openLinkButton.setVisible(false);
        openLinkButton.setManaged(false);
    }

       /**
     * Set reference to main stage for expand functionality
     */
    public void setMainStage(Stage mainStage) {
        this.mainStage = mainStage;
    }

    /**
     * Delete selected terms (supports multiple selection)
     */
    private void deleteSelectedTerms() {
        // Get all selected indices
        var selectedIndices = resultsListView.getSelectionModel().getSelectedIndices();

        if (selectedIndices.isEmpty()) {
            statusLabel.setText("❌ Nenhum termo selecionado");
            return;
        }

        // Get the corresponding terms
        List<DatabaseManager.Term> termsToDelete = new ArrayList<>();
        for (int index : selectedIndices) {
            if (index >= 0 && index < currentSearchResults.size()) {
                termsToDelete.add(currentSearchResults.get(index));
            }
        }

        if (termsToDelete.isEmpty()) {
            return;
        }

        // Use centralized delete dialog
        dialogHelper.showDeleteDialog(searchField.getScene().getWindow(), dbManager, termsToDelete,
            new TermDialogHelper.DeleteOperationCallback() {
                @Override
                public void onSuccess(int deletedCount, String message) {
                    statusLabel.setText(message);

                    // Cancel any pending debounced search
                    if (searchTimer != null) {
                        searchTimer.cancel();
                        searchTimer = null;
                    }
                    // Refresh search results - force clear by resetting state
                    resultsListView.getItems().clear();
                    currentSearchResults = null;
                    lastSearchText = "";
                    currentOffset = 0;
                    performSearch();
                    notifyTermsChanged();

                    // Reset status after 3 seconds
                    new Thread(() -> {
                        try {
                            Thread.sleep(3000);
                            Platform.runLater(() -> statusLabel.setText("Pronto"));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }

                @Override
                public void onCancel() {
                    // Do nothing on cancel
                }
            });
    }

    /**
     * Convert literal \n string sequences to actual newline characters
     * Dictionary files contain the two-character sequence \n (backslash-n)
     * which needs to be converted to actual newlines for proper display
     *
     * @param text Text that may contain literal \n sequences
     * @return Text with actual newline characters
     */
    private String convertNewlinesToActual(String text) {
        if (text == null) {
            return null;
        }
        // Replace literal \n (two characters) with actual newline (one character)
        return text.replace("\\n", "\n");
    }

    /**
     * Extract URL from HTML anchor tag
     * Parses HTML like: <a href="URL">text</a>
     *
     * @param html HTML text that may contain anchor tags
     * @return The extracted URL, or null if no link found
     */
    private String extractUrlFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        // Look for <a href="..."> pattern
        // Pattern: <a href="URL" ...>
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

        // Extract the URL
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
     * Open the media link (PDF or Audio) in the default browser
     * Called when the "Open Link" button is clicked
     */
    @FXML
    private void openMediaLink() {
        if (currentMediaLink == null || currentMediaLink.isEmpty()) {
            statusLabel.setText("❌ Nenhum link disponível");
            return;
        }

        try {
            // Use JavaFX HostServices to open link in default browser
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(new java.net.URI(currentMediaLink));
                statusLabel.setText("✓ Link aberto no navegador");
            } else {
                statusLabel.setText("❌ Não foi possível abrir o link");
            }
        } catch (Exception e) {
            statusLabel.setText("❌ Erro ao abrir link: " + e.getMessage());
            e.printStackTrace();
        }

        // Reset status after 3 seconds
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                Platform.runLater(() -> statusLabel.setText("Pronto"));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
