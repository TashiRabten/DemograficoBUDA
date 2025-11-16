package com.example.glossariobuda;

import com.example.glossariobuda.exceptions.ImportException;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.Pair;

import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service responsible for administrative operations including duplicate cleanup and reset operations.
 * Extracted from GlossarioController to follow Single Responsibility Principle.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Finding and removing duplicate terms</li>
 *   <li>Admin authentication</li>
 *   <li>Reset configuration with date ranges</li>
 *   <li>Administrative reset operations with rollback capability</li>
 * </ul>
 *
 * @author Extracted from GlossarioController
 * @version 2.0
 */
public class AdminOperationsHandler {

    private static final Logger LOGGER = Logger.getLogger(AdminOperationsHandler.class.getName());

    // Constants
    private static final String ADMIN_USERNAME = System.getenv("GLOSSARIO_ADMIN_USER");
    private static final String ADMIN_PASSWORD = System.getenv("GLOSSARIO_ADMIN_PASS");
    private static final int BATCH_SIZE = 500;
    private static final int MAX_DUPLICATE_EXAMPLES = 5;
    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Dependencies
    private final DatabaseManager dbManager;
    private final NetworkStatusMonitor networkMonitor;
    private final DialogHelper dialogHelper;
    private final TermConverter termConverter;
    private Window ownerWindow;
    private AdminCallback callback;

    // ============================================================================
    // INTERFACES
    // ============================================================================

    /**
     * Callback interface for admin operation events
     */
    public interface AdminCallback {
        void onDuplicatesRemoved(int count);
        void onResetComplete(int deletedCount, int importedCount, int newVersion);
        void onResetProgress(String message);
        void onResetError(String error);
        default void onOwnerChanged(String newOwner) {}
    }

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================

    public AdminOperationsHandler(DatabaseManager dbManager, NetworkStatusMonitor networkMonitor) {
        this.dbManager = Objects.requireNonNull(dbManager, "DatabaseManager cannot be null");
        this.networkMonitor = networkMonitor;
        this.dialogHelper = new DialogHelper();
        this.termConverter = new TermConverter();
    }

    // ============================================================================
    // SETTERS
    // ============================================================================

    public void setAdminCallback(AdminCallback callback) {
        this.callback = callback;
    }

    // ============================================================================
    // PUBLIC API - DUPLICATE MANAGEMENT
    // ============================================================================

    /**
     * Find and remove duplicate terms from the database.
     * Shows a confirmation dialog with duplicate statistics before removal.
     */
    public void cleanDuplicates() {
        setStatusMessage("Procurando duplicados...");

        CompletableFuture.supplyAsync(dbManager::findDuplicates)
                .thenAccept(this::handleDuplicateResults)
                .exceptionally(ex -> {
                    handleDuplicateError(ex);
                    return null;
                });
    }

    private void handleDuplicateResults(List<List<DatabaseManager.Term>> duplicates) {
        Platform.runLater(() -> {
            if (duplicates.isEmpty()) {
                handleNoDuplicates();
                return;
            }

            clearStatusMessage();
            DuplicateReport report = new DuplicateReport(duplicates);

            if (dialogHelper.confirmDuplicateRemoval(report)) {
                performDuplicateRemoval();
            } else {
                clearStatusMessage();
            }
        });
    }

    private void handleNoDuplicates() {
        delayedStatusMessage("Nenhum duplicado encontrado", 1.5);
        dialogHelper.showInfo("Remover Duplicados",
                "Nenhum duplicado encontrado!",
                "Seu banco de dados está limpo.");
    }

    private void handleDuplicateError(Throwable ex) {
        LOGGER.severe("Error finding duplicates: " + ex.getMessage());
        Platform.runLater(() -> {
            clearStatusMessage();
            dialogHelper.showError("Erro",
                    "Erro ao procurar duplicados",
                    "Ocorreu um erro: " + ex.getMessage());
        });
    }

    private void performDuplicateRemoval() {
        setStatusMessage("Removendo duplicados...");

        CompletableFuture.supplyAsync(dbManager::removeDuplicates)
                .thenAccept(this::handleRemovalSuccess)
                .exceptionally(ex -> {
                    handleRemovalError(ex);
                    return null;
                });
    }

    private void handleRemovalSuccess(int removed) {
        Platform.runLater(() -> {
            dialogHelper.showInfo("Limpeza Completa",
                    "Duplicados removidos!",
                    String.format("Foram removidos %d termos duplicados.\n\n" +
                            "Agora você pode sincronizar com sucesso!", removed));

            delayedStatusMessage(String.format("Removidos %d termos duplicados!", removed), 1.5);
            notifyDuplicatesRemoved(removed);
        });
    }

    private void handleRemovalError(Throwable ex) {
        LOGGER.severe("Error removing duplicates: " + ex.getMessage());
        Platform.runLater(() -> {
            clearStatusMessage();
            dialogHelper.showError("Erro",
                    "Erro ao remover duplicados",
                    "Ocorreu um erro: " + ex.getMessage());
        });
    }

    // ============================================================================
    // PUBLIC API - ADMIN RESET
    // ============================================================================

    /**
     * Show the admin reset dialog flow.
     * First authenticates the user, then shows the reset configuration dialog.
     */
    public void showAdminResetDialog(Window ownerWindow) {
        this.ownerWindow = ownerWindow;

        if (validateAdminCredentials()) {
            if (authenticateAdmin()) {
                showResetConfigurationDialog(ownerWindow);
            }
        } else {
            dialogHelper.showError("Configuração Inválida",
                    "Credenciais não configuradas",
                    "As variáveis de ambiente GLOSSARIO_ADMIN_USER e GLOSSARIO_ADMIN_PASS não estão configuradas.");
        }
    }

    private boolean validateAdminCredentials() {
        return ADMIN_USERNAME != null && !ADMIN_USERNAME.isEmpty()
                && ADMIN_PASSWORD != null && !ADMIN_PASSWORD.isEmpty();
    }

    private boolean authenticateAdmin() {
        int attempts = 0;
        final int maxAttempts = 3;

        while (attempts < maxAttempts) {
            AuthResult result = performLogin();

            if (result == AuthResult.SUCCESS) {
                return true;
            }

            if (result == AuthResult.CANCELLED) {
                return false;
            }

            attempts++;
            if (attempts < maxAttempts) {
                dialogHelper.showError("Credenciais Incorretas",
                        "Acesso Negado",
                        String.format("Usuário ou senha incorretos. Tentativa %d de %d.",
                                attempts, maxAttempts));
            } else {
                dialogHelper.showError("Acesso Bloqueado",
                        "Número máximo de tentativas excedido",
                        "Por favor, tente novamente mais tarde.");
            }
        }
        return false;
    }

    private AuthResult performLogin() {
        Optional<Pair<String, String>> credentials = showLoginDialog();

        if (!credentials.isPresent()) {
            return AuthResult.CANCELLED;
        }

        boolean valid = validateCredentials(credentials.get());
        return valid ? AuthResult.SUCCESS : AuthResult.FAILED;
    }

    private Optional<Pair<String, String>> showLoginDialog() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Autenticação Administrativa");
        dialog.setHeaderText("Digite as credenciais de administrador");

        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }

        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = createLoginGrid();
        dialog.getDialogPane().setContent(grid);

        TextField username = (TextField) grid.getChildren().get(1);
        PasswordField password = (PasswordField) grid.getChildren().get(3);

        // Enable login button only when both fields have content
        Button loginButton = (Button) dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

        username.textProperty().addListener((obs, oldVal, newVal) ->
                loginButton.setDisable(newVal.trim().isEmpty() || password.getText().trim().isEmpty()));
        password.textProperty().addListener((obs, oldVal, newVal) ->
                loginButton.setDisable(newVal.trim().isEmpty() || username.getText().trim().isEmpty()));

        Platform.runLater(username::requestFocus);

        dialog.setResultConverter(button ->
                button == loginButtonType ? new Pair<>(username.getText(), password.getText()) : null
        );

        return dialog.showAndWait();
    }

    private GridPane createLoginGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField username = new TextField();
        username.setPromptText("Usuário");

        PasswordField password = new PasswordField();
        password.setPromptText("Senha");

        grid.add(new Label("Usuário:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Senha:"), 0, 1);
        grid.add(password, 1, 1);

        return grid;
    }

    private boolean validateCredentials(Pair<String, String> credentials) {
        return ADMIN_USERNAME.equals(credentials.getKey()) &&
                ADMIN_PASSWORD.equals(credentials.getValue());
    }

    // ============================================================================
    // PRIVATE METHODS - STATUS MANAGEMENT
    // ============================================================================

    private void setStatusMessage(String message) {
        if (networkMonitor != null) {
            networkMonitor.setSyncing(message);
        }
    }

    private void clearStatusMessage() {
        if (networkMonitor != null) {
            networkMonitor.clearOperationMessage();
        }
    }

    private void delayedStatusMessage(String message, double seconds) {
        if (networkMonitor == null) return;

        PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
        pause.setOnFinished(e -> networkMonitor.setSyncSuccess(message));
        pause.play();
    }

    private void notifyDuplicatesRemoved(int count) {
        if (callback != null) {
            callback.onDuplicatesRemoved(count);
        }
    }

    // ============================================================================
    // RESET CONFIGURATION DIALOG
    // ============================================================================

    private void showResetConfigurationDialog(Window ownerWindow) {
        ResetDialogContext ctx = buildResetDialog(ownerWindow);
        configureFilePicker(ctx);
        loadCloudMetadata(ctx);

        Runnable updatePreview = createPreviewUpdater(ctx);
        attachPreviewListeners(ctx, updatePreview);

        ctx.executeButton.setOnAction(e -> handleExecuteAction(ctx, ownerWindow));
        ctx.cancelButton.setOnAction(e -> ctx.stage.close());

        ctx.stage.setScene(new Scene(ctx.root));
        ctx.stage.showAndWait();
    }

    private ResetDialogContext buildResetDialog(Window ownerWindow) {
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Reset Administrativo do Glossário");
        dialogStage.initOwner(ownerWindow);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setWidth(750);
        dialogStage.setHeight(650);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        Label titleLabel = new Label("Configurar Reset Customizado");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label explanationLabel = new Label("Selecione o intervalo de datas dos termos a DELETAR:");
        explanationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        HBox startDateBox = createDateBox(true);
        HBox endDateBox = createDateBox(false);

        Label examplesLabel = new Label(
                "Exemplos:\n" +
                        "• Apenas Data/Hora Final = Deleta tudo ANTES do momento especificado\n" +
                        "• Apenas Data/Hora Inicial = Deleta tudo DEPOIS do momento especificado\n" +
                        "• Ambas = Deleta tudo ENTRE os dois momentos (inclusivo)"
        );
        examplesLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-padding: 5;");

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        Label fileLabel = new Label("Glossário corrigido:");
        TextField filePathField = new TextField();
        filePathField.setPromptText("Selecione o arquivo CSV/XML/JSON...");
        filePathField.setEditable(false);
        filePathField.setPrefWidth(300);
        Button browseButton = new Button("Procurar...");
        browseButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        fileBox.getChildren().addAll(fileLabel, filePathField, browseButton);

        Label cloudInfoLabel = new Label("Carregando informações do cloud...");
        cloudInfoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

        TextArea previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(8);
        previewArea.setText("Selecione uma data para ver o preview...");

        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER);
        Button executeButton = new Button("Executar Reset Automático");
        executeButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        Button cancelButton = new Button("Cancelar");
        buttonBar.getChildren().addAll(executeButton, cancelButton);

        root.getChildren().addAll(titleLabel, explanationLabel, startDateBox, endDateBox, examplesLabel,
                new Separator(), fileBox, cloudInfoLabel,
                new Label("Preview:"), previewArea, buttonBar);

        // Extract components from boxes
        CheckBox startDateCheck = (CheckBox) startDateBox.getChildren().get(0);
        DatePicker startDatePicker = (DatePicker) startDateBox.getChildren().get(1);
        @SuppressWarnings("unchecked")
        Spinner<Integer> startHourSpinner = (Spinner<Integer>) startDateBox.getChildren().get(2);
        @SuppressWarnings("unchecked")
        Spinner<Integer> startMinuteSpinner = (Spinner<Integer>) startDateBox.getChildren().get(4);

        CheckBox endDateCheck = (CheckBox) endDateBox.getChildren().get(0);
        DatePicker endDatePicker = (DatePicker) endDateBox.getChildren().get(1);
        @SuppressWarnings("unchecked")
        Spinner<Integer> endHourSpinner = (Spinner<Integer>) endDateBox.getChildren().get(2);
        @SuppressWarnings("unchecked")
        Spinner<Integer> endMinuteSpinner = (Spinner<Integer>) endDateBox.getChildren().get(4);

        return new ResetDialogContext(dialogStage, root, startDateCheck, startDatePicker, startHourSpinner,
                startMinuteSpinner, endDateCheck, endDatePicker, endHourSpinner, endMinuteSpinner,
                cloudInfoLabel, previewArea, executeButton, cancelButton, browseButton, filePathField);
    }

    private HBox createDateBox(boolean isStart) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);

        CheckBox dateCheck = new CheckBox(isStart ? "Data inicial:" : "Data final:");
        dateCheck.setSelected(false);

        DatePicker datePicker = new DatePicker();
        datePicker.setDisable(true);
        datePicker.setPromptText(isStart ? "Deletar A PARTIR desta data" : "Deletar ATÉ esta data");

        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, isStart ? 0 : 23);
        hourSpinner.setPrefWidth(70);
        hourSpinner.setDisable(true);

        Label timeLabel = new Label(":");

        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, isStart ? 0 : 59);
        minuteSpinner.setPrefWidth(70);
        minuteSpinner.setDisable(true);

        Label utcLabel = new Label("(hora local)");
        utcLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");

        box.getChildren().addAll(dateCheck, datePicker, hourSpinner, timeLabel, minuteSpinner, utcLabel);

        return box;
    }

    private void configureFilePicker(ResetDialogContext ctx) {
        ctx.browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Selecionar Glossário Corrigido");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All Files", "*.*", "*"),
                    new FileChooser.ExtensionFilter("Todos os Formatos", "*.xml", "*.csv", "*.json", "*.txt", "*.tsv"),
                    new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                    new FileChooser.ExtensionFilter("CSV Files", "*.csv", "*.txt", "*.tsv"),
                    new FileChooser.ExtensionFilter("JSON Files", "*.json")
            );

            File file = fileChooser.showOpenDialog(ctx.stage);
            if (file != null) {
                ctx.selectedFile = file;
                ctx.filePathField.setText(file.getName());
            }
        });
    }

    private void loadCloudMetadata(ResetDialogContext ctx) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return dbManager.getSyncManager().getGlossaryMetadata();
            } catch (Exception ex) {
                LOGGER.severe("Error loading metadata: " + ex.getMessage());
                throw new RuntimeException(ex);
            }
        }).thenAccept(metadata -> Platform.runLater(() ->
                ctx.cloudInfoLabel.setText(String.format(
                        "Versão Cloud: %d | Termos no Cloud: %d | Último reset: %s",
                        metadata.version, metadata.totalTerms,
                        metadata.lastResetDate != null ? metadata.lastResetDate : "N/A"
                ))
        )).exceptionally(ex -> {
            Platform.runLater(() -> ctx.cloudInfoLabel.setText("Erro ao carregar metadata: " + ex.getMessage()));
            return null;
        });
    }

    private Runnable createPreviewUpdater(ResetDialogContext ctx) {
        return () -> {
            DateRange range = extractDateRange(ctx);

            if (range.isEmpty()) {
                ctx.previewArea.setText("Selecione pelo menos uma data para ver o preview...");
                return;
            }

            ctx.previewArea.setText("Carregando preview...");

            CompletableFuture.supplyAsync(() -> fetchTermsInRange(range))
                    .thenAccept(terms -> Platform.runLater(() ->
                            updatePreviewArea(ctx.previewArea, range, terms)))
                    .exceptionally(ex -> {
                        Platform.runLater(() ->
                                ctx.previewArea.setText("Erro ao buscar termos: " + ex.getMessage()));
                        return null;
                    });
        };
    }

    private DateRange extractDateRange(ResetDialogContext ctx) {
        String startDateStr = null;
        if (ctx.startDateCheck.isSelected() && ctx.startDatePicker.getValue() != null) {
            LocalDateTime localStart = LocalDateTime.of(
                    ctx.startDatePicker.getValue(),
                    LocalTime.of(ctx.startHourSpinner.getValue(), ctx.startMinuteSpinner.getValue())
            );
            ZonedDateTime utcStart = localStart.atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneOffset.UTC);
            startDateStr = utcStart.format(DateTimeFormatter.ISO_INSTANT);
        }

        String endDateStr = null;
        if (ctx.endDateCheck.isSelected() && ctx.endDatePicker.getValue() != null) {
            LocalDateTime localEnd = LocalDateTime.of(
                    ctx.endDatePicker.getValue(),
                    LocalTime.of(ctx.endHourSpinner.getValue(), ctx.endMinuteSpinner.getValue())
            );
            ZonedDateTime utcEnd = localEnd.atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneOffset.UTC);
            endDateStr = utcEnd.format(DateTimeFormatter.ISO_INSTANT);
        }

        return new DateRange(startDateStr, endDateStr);
    }

    private List<SupabaseClient.TermDTO> fetchTermsInRange(DateRange range) {
        try {
            SupabaseClient client = new SupabaseClient();
            return client.getTermsInDateRange(range.start, range.end);
        } catch (Exception ex) {
            LOGGER.severe("Error fetching terms: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private void updatePreviewArea(TextArea previewArea, DateRange range, List<SupabaseClient.TermDTO> terms) {
        StringBuilder preview = new StringBuilder();
        preview.append("Termos que serão DELETADOS:\n");
        preview.append(range.formatDescription()).append("\n\n");

        if (terms.isEmpty()) {
            preview.append("Nenhum termo encontrado neste intervalo.");
        } else {
            preview.append("Primeiros termos:\n");
            for (int i = 0; i < Math.min(5, terms.size()); i++) {
                SupabaseClient.TermDTO term = terms.get(i);
                preview.append(String.format("%d. %s → %s (%s)\n",
                        i + 1,
                        term.source_term,
                        term.target_term,
                        term.date_added != null ? term.date_added.substring(0, 10) : "sem data"));
            }
        }

        previewArea.setText(preview.toString());
    }

    private void attachPreviewListeners(ResetDialogContext ctx, Runnable updatePreview) {
        ctx.startDatePicker.setOnAction(e -> updatePreview.run());
        ctx.endDatePicker.setOnAction(e -> updatePreview.run());

        ctx.startHourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());
        ctx.startMinuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());
        ctx.endHourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());
        ctx.endMinuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());

        ctx.startDateCheck.setOnAction(e -> {
            boolean selected = ctx.startDateCheck.isSelected();
            ctx.startDatePicker.setDisable(!selected);
            ctx.startHourSpinner.setDisable(!selected);
            ctx.startMinuteSpinner.setDisable(!selected);
            updatePreview.run();
        });

        ctx.endDateCheck.setOnAction(e -> {
            boolean selected = ctx.endDateCheck.isSelected();
            ctx.endDatePicker.setDisable(!selected);
            ctx.endHourSpinner.setDisable(!selected);
            ctx.endMinuteSpinner.setDisable(!selected);
            updatePreview.run();
        });
    }

    private void handleExecuteAction(ResetDialogContext ctx, Window ownerWindow) {
        // Validation
        ValidationResult validation = validateResetConfiguration(ctx);
        if (!validation.isValid()) {
            dialogHelper.showWarning(validation.title, validation.header, validation.content);
            return;
        }

        DateRange range = extractDateRange(ctx);
        String dateRangeDesc = range.formatDescriptionForConfirmation();

        if (confirmResetOperation(ctx.selectedFile, dateRangeDesc)) {
            ctx.stage.close();
            performAdminReset(range.start, range.end, ctx.selectedFile, ownerWindow);
        }
    }

    private ValidationResult validateResetConfiguration(ResetDialogContext ctx) {
        if (ctx.selectedFile == null) {
            return ValidationResult.invalid(
                    "Arquivo não selecionado",
                    "Selecione um arquivo de glossário",
                    "Por favor, selecione o arquivo CSV/XML/JSON que contém o glossário corrigido."
            );
        }

        if (!ctx.startDateCheck.isSelected() && !ctx.endDateCheck.isSelected()) {
            return ValidationResult.invalid(
                    "Data não selecionada",
                    "Selecione pelo menos uma data",
                    "Por favor, selecione pelo menos uma data (inicial ou final) para definir o intervalo de reset."
            );
        }

        if (ctx.startDateCheck.isSelected() && ctx.startDatePicker.getValue() == null) {
            return ValidationResult.invalid(
                    "Data inicial inválida",
                    "Selecione a data inicial",
                    "Defina a data inicial ou desmarque a opção correspondente."
            );
        }

        if (ctx.endDateCheck.isSelected() && ctx.endDatePicker.getValue() == null) {
            return ValidationResult.invalid(
                    "Data final inválida",
                    "Selecione a data final",
                    "Defina a data final ou desmarque a opção correspondente."
            );
        }

        return ValidationResult.valid();
    }

    private boolean confirmResetOperation(File file, String dateRangeDesc) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmar Reset Administrativo");
        confirmAlert.setHeaderText("Tem certeza que deseja fazer reset?");
        confirmAlert.setContentText(
                "Isto irá executar AUTOMATICAMENTE:\n\n" +
                        "PASSO 1: Deletar termos no Supabase\n" +
                        "           " + dateRangeDesc + "\n\n" +
                        "PASSO 2: Incrementar versão do glossário\n\n" +
                        "PASSO 3: Importar arquivo: " + file.getName() + "\n\n" +
                        "Todos os clientes farão:\n" +
                        "   • Backup da base local\n" +
                        "   • Deletar termos no mesmo intervalo\n" +
                        "   • Download dos termos atualizados\n" +
                        "   • Preservar termos fora do intervalo\n\n" +
                        "Deseja continuar?"
        );
        dialogHelper.applyIcon(confirmAlert);

        return confirmAlert.showAndWait()
                .map(response -> response == ButtonType.OK)
                .orElse(false);
    }

    // ============================================================================
    // RESET EXECUTION
    // ============================================================================

    private void performAdminReset(String startDate, String endDate, File glossaryFile, Window ownerWindow) {
        ResetProgressDialog progressDialog = new ResetProgressDialog(ownerWindow);
        progressDialog.show();

        CompletableFuture.runAsync(() -> executeResetOperation(startDate, endDate, glossaryFile, progressDialog))
                .exceptionally(ex -> {
                    handleResetFailure(ex, progressDialog);
                    return null;
                });
    }

    private void executeResetOperation(String startDate, String endDate, File glossaryFile,
                                       ResetProgressDialog progressDialog) {
        ResetOperation operation = new ResetOperation(startDate, endDate, glossaryFile, progressDialog);
        operation.execute();
    }

    private void handleResetFailure(Throwable ex, ResetProgressDialog progressDialog) {
        LOGGER.severe("Reset operation failed: " + ex.getMessage());
        ex.printStackTrace();

        Platform.runLater(() -> {
            progressDialog.close();
            dialogHelper.showError("Erro no Reset",
                    "O reset foi cancelado",
                    "Erro: " + ex.getMessage() + "\n\nAs mudanças foram revertidas.");

            if (callback != null) {
                callback.onResetError(ex.getMessage());
            }
        });
    }

    // ============================================================================
    // HELPER CLASSES
    // ============================================================================

    private enum AuthResult {
        SUCCESS, FAILED, CANCELLED
    }

    private static class DateRange {
        final String start;
        final String end;

        DateRange(String start, String end) {
            this.start = start;
            this.end = end;
        }

        boolean isEmpty() {
            return start == null && end == null;
        }

        String formatDescription() {
            if (start != null && end != null) {
                ZonedDateTime startLocal = Instant.parse(start).atZone(ZoneId.systemDefault());
                ZonedDateTime endLocal = Instant.parse(end).atZone(ZoneId.systemDefault());
                return String.format("Intervalo: %s - %s (hora local)",
                        startLocal.format(LOCAL_FORMATTER),
                        endLocal.format(LOCAL_FORMATTER));
            } else if (start != null) {
                ZonedDateTime startLocal = Instant.parse(start).atZone(ZoneId.systemDefault());
                return "A partir de: " + startLocal.format(LOCAL_FORMATTER) + " (hora local)";
            } else {
                ZonedDateTime endLocal = Instant.parse(end).atZone(ZoneId.systemDefault());
                return "Até: " + endLocal.format(LOCAL_FORMATTER) + " (hora local)";
            }
        }

        String formatDescriptionForConfirmation() {
            if (start != null && end != null) {
                ZonedDateTime startLocal = Instant.parse(start).atZone(ZoneId.systemDefault());
                ZonedDateTime endLocal = Instant.parse(end).atZone(ZoneId.systemDefault());
                return String.format("De: %s (hora local)\n                           Até: %s (hora local)",
                        startLocal.format(LOCAL_FORMATTER),
                        endLocal.format(LOCAL_FORMATTER));
            } else if (start != null) {
                ZonedDateTime startLocal = Instant.parse(start).atZone(ZoneId.systemDefault());
                return "A PARTIR de: " + startLocal.format(LOCAL_FORMATTER) + " (hora local)";
            } else {
                ZonedDateTime endLocal = Instant.parse(end).atZone(ZoneId.systemDefault());
                return "ATÉ: " + endLocal.format(LOCAL_FORMATTER) + " (hora local)";
            }
        }
    }

    private static class ValidationResult {
        final boolean valid;
        final String title;
        final String header;
        final String content;

        private ValidationResult(boolean valid, String title, String header, String content) {
            this.valid = valid;
            this.title = title;
            this.header = header;
            this.content = content;
        }

        static ValidationResult valid() {
            return new ValidationResult(true, null, null, null);
        }

        static ValidationResult invalid(String title, String header, String content) {
            return new ValidationResult(false, title, header, content);
        }

        boolean isValid() {
            return valid;
        }
    }

    private static class DuplicateReport {
        private final List<List<DatabaseManager.Term>> duplicateGroups;
        private final int totalDuplicates;

        DuplicateReport(List<List<DatabaseManager.Term>> duplicateGroups) {
            this.duplicateGroups = duplicateGroups;
            this.totalDuplicates = calculateTotalDuplicates(duplicateGroups);
        }

        private int calculateTotalDuplicates(List<List<DatabaseManager.Term>> groups) {
            return groups.stream()
                    .mapToInt(group -> group.size() - 1)
                    .sum();
        }

        String formatReport() {
            StringBuilder report = new StringBuilder();
            report.append("Encontrados ").append(duplicateGroups.size())
                    .append(" grupos de termos duplicados.\n\n");
            report.append("Total de duplicados a remover: ").append(totalDuplicates).append("\n\n");
            report.append("Exemplos:\n");

            appendExamples(report);
            appendFooter(report);

            return report.toString();
        }

        private void appendExamples(StringBuilder report) {
            int examples = Math.min(5, duplicateGroups.size());
            for (int i = 0; i < examples; i++) {
                List<DatabaseManager.Term> group = duplicateGroups.get(i);
                DatabaseManager.Term first = group.get(0);
                report.append("• ").append(first.getSourceTerm())
                        .append(" → ").append(first.getTargetTerm())
                        .append(" (").append(group.size()).append(" cópias)\n");
            }

            if (duplicateGroups.size() > 5) {
                report.append("... e mais ").append(duplicateGroups.size() - 5).append(" grupos\n");
            }
        }

        private void appendFooter(StringBuilder report) {
            report.append("\nDeseja remover os duplicados?\n");
            report.append("(O termo mais antigo de cada grupo será mantido)");
        }
    }

    private class DialogHelper {

        void showInfo(String title, String header, String content) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            applyIcon(alert);
            alert.showAndWait();
        }

        void showError(String title, String header, String content) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            applyIcon(alert);
            alert.showAndWait();
        }

        void showWarning(String title, String header, String content) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            applyIcon(alert);
            alert.showAndWait();
        }

        boolean confirmDuplicateRemoval(DuplicateReport report) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Remover Duplicados");
            alert.setHeaderText("Duplicados Encontrados");
            alert.setContentText(report.formatReport());
            applyIcon(alert);

            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        }

        void applyIcon(Dialog<?> dialog) {
            dialog.setOnShown(e -> {
                try {
                    Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                    stage.getIcons().add(new Image(
                            getClass().getResourceAsStream("/icons/pramana.png")));
                } catch (Exception ex) {
                    LOGGER.warning("Could not load dialog icon: " + ex.getMessage());
                }
            });
        }
    }

    private static class ResetDialogContext {
        final Stage stage;
        final VBox root;
        final CheckBox startDateCheck;
        final DatePicker startDatePicker;
        final Spinner<Integer> startHourSpinner;
        final Spinner<Integer> startMinuteSpinner;
        final CheckBox endDateCheck;
        final DatePicker endDatePicker;
        final Spinner<Integer> endHourSpinner;
        final Spinner<Integer> endMinuteSpinner;
        final Label cloudInfoLabel;
        final TextArea previewArea;
        final Button executeButton;
        final Button cancelButton;
        final Button browseButton;
        final TextField filePathField;
        File selectedFile;

        ResetDialogContext(Stage stage, VBox root, CheckBox startDateCheck, DatePicker startDatePicker,
                           Spinner<Integer> startHourSpinner, Spinner<Integer> startMinuteSpinner,
                           CheckBox endDateCheck, DatePicker endDatePicker,
                           Spinner<Integer> endHourSpinner, Spinner<Integer> endMinuteSpinner,
                           Label cloudInfoLabel, TextArea previewArea, Button executeButton,
                           Button cancelButton, Button browseButton, TextField filePathField) {
            this.stage = stage;
            this.root = root;
            this.startDateCheck = startDateCheck;
            this.startDatePicker = startDatePicker;
            this.startHourSpinner = startHourSpinner;
            this.startMinuteSpinner = startMinuteSpinner;
            this.endDateCheck = endDateCheck;
            this.endDatePicker = endDatePicker;
            this.endHourSpinner = endHourSpinner;
            this.endMinuteSpinner = endMinuteSpinner;
            this.cloudInfoLabel = cloudInfoLabel;
            this.previewArea = previewArea;
            this.executeButton = executeButton;
            this.cancelButton = cancelButton;
            this.browseButton = browseButton;
            this.filePathField = filePathField;
            this.selectedFile = null;
        }
    }

    private class ResetProgressDialog {
        private final Stage stage;
        private final Label statusLabel;
        private final Label detailsLabel;

        ResetProgressDialog(Window owner) {
            this.stage = new Stage();
            stage.setTitle("Reset Administrativo");
            stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);

            VBox root = new VBox(20);
            root.setPadding(new Insets(30));
            root.setAlignment(Pos.CENTER);

            statusLabel = new Label("Iniciando reset...");
            statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

            ProgressIndicator progress = new ProgressIndicator();
            progress.setPrefSize(60, 60);

            detailsLabel = new Label("");
            detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

            root.getChildren().addAll(statusLabel, progress, detailsLabel);

            Scene scene = new Scene(root, 500, 250);
            stage.setScene(scene);
            stage.setOnCloseRequest(event -> event.consume());
        }

        void show() {
            Platform.runLater(stage::show);
        }

        void close() {
            Platform.runLater(stage::close);
        }

        void updateStatus(String status) {
            Platform.runLater(() -> statusLabel.setText(status));
        }

        void updateDetails(String details) {
            Platform.runLater(() -> detailsLabel.setText(details));
        }
    }

    private class ResetOperation {
        private final String startDate;
        private final String endDate;
        private final File glossaryFile;
        private final ResetProgressDialog progressDialog;
        private final SupabaseClient client;

        private int originalVersion = 0;
        private List<SupabaseClient.TermDTO> deletedTerms = new ArrayList<>();

        ResetOperation(String startDate, String endDate, File glossaryFile, ResetProgressDialog progressDialog) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.glossaryFile = glossaryFile;
            this.progressDialog = progressDialog;
            this.client = new SupabaseClient();
        }

        void execute() {
            try {
                ResetResult result = performReset();
                handleSuccess(result);
            } catch (Exception ex) {
                handleFailure(ex);
            }
        }

        private ResetResult performReset() throws Exception {
            originalVersion = getCurrentVersion();
            int deleted = deleteTermsInRange();
            int newVersion = incrementVersion();
            List<GlossaryTerm> parsedTerms = parseGlossaryFile();
            List<SupabaseClient.TermDTO> termsToImport = convertTerms(parsedTerms);
            DuplicateFilterResult filterResult = filterDuplicates(termsToImport);

            // Pause realtime during batch import to avoid triggering onOwnerChanged() for each term
            RealtimeSyncService.pauseRealtime();
            System.out.println("[AdminReset] ⏸️ Realtime paused for batch import");

            try {
                ImportResult importResult = batchImportTerms(filterResult.uniqueTerms);

                // Resume realtime after successful import
                RealtimeSyncService.resumeRealtime();
                System.out.println("[AdminReset] ▶️ Realtime resumed after batch import");

                return new ResetResult(deleted, importResult.imported, newVersion,
                        filterResult.cloudDuplicates, filterResult.internalDuplicates,
                        importResult.totalBatches, importResult.failedBatches);
            } catch (Exception ex) {
                // Resume realtime even if import fails
                RealtimeSyncService.resumeRealtime();
                System.out.println("[AdminReset] ▶️ Realtime resumed after error");
                throw ex;
            }
        }

        private int getCurrentVersion() throws Exception {
            progressDialog.updateStatus("PASSO 1: Preparando...");
            progressDialog.updateDetails("Obtendo versão atual...");

            SupabaseClient.GlossaryMetadata currentMeta = client.getGlossaryMetadata();
            originalVersion = currentMeta.version;
            LOGGER.info("Current version: " + originalVersion);
            return originalVersion;
        }

        private int deleteTermsInRange() throws Exception {
            progressDialog.updateStatus("PASSO 1: Deletando termos do Supabase");
            progressDialog.updateDetails("Buscando termos para deletar...");

            deletedTerms = client.getTermsInDateRange(startDate, endDate);
            LOGGER.info("Found " + deletedTerms.size() + " terms to delete");

            if (deletedTerms.isEmpty()) {
                throw new ImportException("Nenhum termo encontrado no intervalo especificado");
            }

            progressDialog.updateDetails("Deletando " + deletedTerms.size() + " termos...");
            int deleted = client.deleteTermsByDateRange(startDate, endDate);
            if (deleted == -1) deleted = deletedTerms.size();
            LOGGER.info("Deleted " + deleted + " terms from Supabase");
            return deleted;
        }

        private int incrementVersion() throws Exception {
            progressDialog.updateStatus("PASSO 2: Incrementando versão");
            progressDialog.updateDetails("Atualizando metadata...");

            String resetReason = buildResetReason();
            int newVersion = client.incrementGlossaryVersion(resetReason, startDate, endDate);
            LOGGER.info("New glossary version: " + newVersion);
            return newVersion;
        }

        private String buildResetReason() {
            if (startDate != null && endDate != null) {
                return "Admin Reset - intervalo: " + startDate.substring(0,10) + " até " + endDate.substring(0,10);
            } else if (startDate != null) {
                return "Admin Reset - a partir de: " + startDate.substring(0,10);
            } else {
                return "Admin Reset - até: " + endDate.substring(0,10);
            }
        }

        private List<GlossaryTerm> parseGlossaryFile() throws ImportException {
            progressDialog.updateStatus("PASSO 3: Validando arquivo de glossário");
            progressDialog.updateDetails("Parseando arquivo " + glossaryFile.getName() + "...");

            // CRITICAL: Use the SAME parser chain as regular imports
            // This ensures admin reset benefits from ALL parser improvements:
            // - TibetanDictParser with apostrophe/tsheg/newline fixes
            // - SteinertXMLParser with audio link support
            // - MW72Parser for Monier-Williams Sanskrit dictionary
            List<FormatParser> parsers = GlossaryLoader.getDefaultParsers();

            FormatParser parser = parsers.stream()
                    .filter(p -> p.canParse(glossaryFile))
                    .findFirst()
                    .orElseThrow(() -> new ImportException("Formato de arquivo não suportado: " + glossaryFile.getName()));

            LOGGER.info("Using parser: " + parser.getClass().getSimpleName() + " for file: " + glossaryFile.getName());

            List<GlossaryTerm> terms = null;
            try {
                terms = parser.parse(glossaryFile, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            LOGGER.info("Parsed " + terms.size() + " terms from file");

            if (terms.isEmpty()) {
                throw new ImportException("Arquivo não contém nenhum termo válido");
            }

            return terms;
        }

        private List<SupabaseClient.TermDTO> convertTerms(List<GlossaryTerm> terms) {
            progressDialog.updateStatus("PASSO 3: Preparando termos para importação");
            progressDialog.updateDetails("Convertendo " + terms.size() + " termos...");

            List<SupabaseClient.TermDTO> dtos = terms.stream()
                    .map(termConverter::convertToTermDTO)
                    .collect(Collectors.toList());

            LOGGER.info("Converted " + dtos.size() + " terms to DTOs");
            return dtos;
        }

        private DuplicateFilterResult filterDuplicates(List<SupabaseClient.TermDTO> terms) throws Exception {
            progressDialog.updateStatus("PASSO 3: Verificando duplicatas");
            progressDialog.updateDetails("Carregando hashes existentes...");

            Set<String> existingCloudHashes = client.getAllExistingHashes();
            LOGGER.info("Found " + existingCloudHashes.size() + " existing hashes in cloud");

            AtomicInteger cloudDuplicates = new AtomicInteger(0);
            List<SupabaseClient.TermDTO> newTermsOnly = terms.stream()
                    .filter(dto -> {
                        if (existingCloudHashes.contains(dto.content_hash)) {
                            cloudDuplicates.incrementAndGet();
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            LOGGER.info("After cloud duplicate check: " + newTermsOnly.size() +
                    " new terms, " + cloudDuplicates.get() + " cloud duplicates skipped");

            progressDialog.updateDetails("Verificando duplicatas no arquivo...");

            Map<String, SupabaseClient.TermDTO> uniqueByHash = new LinkedHashMap<>();
            AtomicInteger internalDuplicates = new AtomicInteger(0);

            newTermsOnly.forEach(dto -> {
                if (uniqueByHash.containsKey(dto.content_hash)) {
                    int count = internalDuplicates.incrementAndGet();
                    if (count <= MAX_DUPLICATE_EXAMPLES) {
                        LOGGER.info("Internal duplicate removed: " + dto.source_term + " → " + dto.target_term);
                    }
                } else {
                    uniqueByHash.put(dto.content_hash, dto);
                }
            });

            List<SupabaseClient.TermDTO> uniqueTerms = new ArrayList<>(uniqueByHash.values());
            LOGGER.info("Internal duplicates removed: " + internalDuplicates.get());
            LOGGER.info("Final unique terms to insert: " + uniqueTerms.size());

            return new DuplicateFilterResult(uniqueTerms, cloudDuplicates.get(), internalDuplicates.get());
        }

        private ImportResult batchImportTerms(List<SupabaseClient.TermDTO> terms) throws Exception {
            progressDialog.updateStatus("PASSO 3: Importando glossário para Supabase");
            progressDialog.updateDetails("Importando " + terms.size() + " termos únicos...");
            LOGGER.info("Batch inserting " + terms.size() + " unique terms...");

            int totalBatches = (int) Math.ceil((double) terms.size() / BATCH_SIZE);
            AtomicInteger imported = new AtomicInteger(0);
            AtomicInteger failedBatches = new AtomicInteger(0);

            for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
                int start = batchNum * BATCH_SIZE;
                int end = Math.min(start + BATCH_SIZE, terms.size());
                List<SupabaseClient.TermDTO> batch = terms.subList(start, end);

                int currentBatch = batchNum + 1;
                progressDialog.updateDetails(String.format(
                        "Batch %d/%d: Inserindo termos %d-%d...",
                        currentBatch, totalBatches, start + 1, end
                ));

                int batchImported = processBatch(batch, currentBatch, totalBatches);
                imported.addAndGet(batchImported);

                if (batchImported < batch.size()) {
                    failedBatches.incrementAndGet();
                }

                progressDialog.updateDetails(String.format(
                        "Progresso: %d/%d batches (%d termos importados)",
                        currentBatch, totalBatches, imported.get()
                ));

                if (callback != null) {
                    int finalCurrentBatch = currentBatch;
                    Platform.runLater(() -> callback.onResetProgress(
                            String.format("Batch %d/%d completo", finalCurrentBatch, totalBatches)));
                }
            }

            logImportSummary(imported.get(), terms.size(), totalBatches, failedBatches.get());
            return new ImportResult(imported.get(), totalBatches, failedBatches.get());
        }

        private int processBatch(List<SupabaseClient.TermDTO> batch, int batchNum, int totalBatches) {
            try {
                int batchImported = client.batchInsertTerms(batch);
                LOGGER.info(String.format("Batch %d/%d: %d/%d terms inserted successfully",
                        batchNum, totalBatches, batchImported, batch.size()));
                return batchImported;
            } catch (Exception e) {
                LOGGER.severe("Batch " + batchNum + " failed: " + e.getMessage());
                return retryBatchOneByOne(batch, batchNum);
            }
        }

        private int retryBatchOneByOne(List<SupabaseClient.TermDTO> batch, int batchNum) {
            LOGGER.info("Retrying batch " + batchNum + " one-by-one...");

            int recovered = 0;
            for (SupabaseClient.TermDTO term : batch) {
                try {
                    if (client.insertTerm(term)) recovered++;
                } catch (Exception ex) {
                    // Silent failure
                }
            }

            LOGGER.info("Batch " + batchNum + " one-by-one: " + recovered + "/" + batch.size() + " recovered");
            return recovered;
        }

        private void logImportSummary(int imported, int expected, int totalBatches, int failedBatches) {
            LOGGER.info("\n═══════════════════════════════════");
            LOGGER.info("BATCH IMPORT COMPLETE:");
            LOGGER.info("  Total batches: " + totalBatches);
            LOGGER.info("  Failed batches: " + failedBatches);
            LOGGER.info("  Successfully imported: " + imported + "/" + expected);
            LOGGER.info("  Success rate: " + String.format("%.1f%%", (100.0 * imported / expected)));
            LOGGER.info("═══════════════════════════════════\n");
        }

        private void handleSuccess(ResetResult result) {
            Platform.runLater(() -> {
                progressDialog.close();
                showSuccessDialog(result);

                if (callback != null) {
                    callback.onResetComplete(result.deleted, result.imported, result.newVersion);
                }
            });
        }

        private void showSuccessDialog(ResetResult result) {
            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Reset Administrativo Completo");
            successAlert.setHeaderText("Reset executado com sucesso!");

            TextArea textArea = new TextArea(result.formatSummary());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefRowCount(20);
            textArea.setPrefColumnCount(60);

            successAlert.getDialogPane().setContent(textArea);
            successAlert.getDialogPane().setPrefWidth(650);
            successAlert.getDialogPane().setPrefHeight(550);

            dialogHelper.applyIcon(successAlert);
            successAlert.showAndWait();
        }

        private void handleFailure(Exception ex) {
            LOGGER.severe("ERROR - ROLLING BACK: " + ex.getMessage());
            ex.printStackTrace();

            performRollback();

            Platform.runLater(() -> {
                progressDialog.close();
                dialogHelper.showError("Erro no Reset",
                        "O reset foi cancelado",
                        "Erro: " + ex.getMessage() + "\n\nAs mudanças foram revertidas.");

                if (callback != null) {
                    callback.onResetError(ex.getMessage());
                }
            });
        }

        private void performRollback() {
            try {
                LOGGER.info("ROLLBACK: Restoring version " + originalVersion);
                client.incrementGlossaryVersion("ROLLBACK - Previous reset failed", null, null);

                if (!deletedTerms.isEmpty()) {
                    LOGGER.info("ROLLBACK: Restoring " + deletedTerms.size() + " deleted terms");
                    int restored = 0;
                    for (SupabaseClient.TermDTO term : deletedTerms) {
                        try {
                            if (client.insertTerm(term)) restored++;
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    LOGGER.info("ROLLBACK: Restored " + restored + " terms");
                }
            } catch (Exception rollbackEx) {
                LOGGER.severe("ROLLBACK FAILED: " + rollbackEx.getMessage());
            }
        }
    }

    private static class DuplicateFilterResult {
        final List<SupabaseClient.TermDTO> uniqueTerms;
        final int cloudDuplicates;
        final int internalDuplicates;

        DuplicateFilterResult(List<SupabaseClient.TermDTO> uniqueTerms,
                              int cloudDuplicates, int internalDuplicates) {
            this.uniqueTerms = uniqueTerms;
            this.cloudDuplicates = cloudDuplicates;
            this.internalDuplicates = internalDuplicates;
        }
    }

    private static class ImportResult {
        final int imported;
        final int totalBatches;
        final int failedBatches;

        ImportResult(int imported, int totalBatches, int failedBatches) {
            this.imported = imported;
            this.totalBatches = totalBatches;
            this.failedBatches = failedBatches;
        }
    }

    private static class ResetResult {
        final int deleted;
        final int imported;
        final int newVersion;
        final int cloudDuplicates;
        final int internalDuplicates;
        final int totalBatches;
        final int failedBatches;

        ResetResult(int deleted, int imported, int newVersion,
                    int cloudDuplicates, int internalDuplicates,
                    int totalBatches, int failedBatches) {
            this.deleted = deleted;
            this.imported = imported;
            this.newVersion = newVersion;
            this.cloudDuplicates = cloudDuplicates;
            this.internalDuplicates = internalDuplicates;
            this.totalBatches = totalBatches;
            this.failedBatches = failedBatches;
        }

        String formatSummary() {
            StringBuilder content = new StringBuilder();
            content.append("✅ RESET COMPLETO:\n\n");
            content.append("PASSO 1 - Deleção:\n");
            content.append("• Termos deletados: ").append(deleted).append("\n\n");
            content.append("PASSO 2 - Versão:\n");
            content.append("• Nova versão: ").append(newVersion).append("\n\n");
            content.append("PASSO 3 - Importação (Batch):\n");
            content.append("• Termos importados: ").append(imported).append("\n");
            content.append("• Duplicados no cloud (preservados): ").append(cloudDuplicates).append("\n");
            content.append("• Duplicados no arquivo (removidos): ").append(internalDuplicates).append("\n");
            content.append("• Batches processados: ").append(totalBatches).append("\n");

            if (failedBatches > 0) {
                content.append("• ⚠️ Batches com problemas: ").append(failedBatches).append("\n");
            }

            return content.toString();
        }
    }

    private static class TermConverter {
        SupabaseClient.TermDTO convertToTermDTO(GlossaryTerm term) {
            SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();

            if (term.getSourceTerm() != null && !term.getSourceTerm().isEmpty()) {
                dto.source_term = term.getSourceTerm();
                dto.source_language = term.getSourceLanguage() != null ? term.getSourceLanguage() : "English";
                dto.target_term = term.getTargetTerm();
                dto.target_language = term.getTargetLanguage() != null ? term.getTargetLanguage() : "Tibetan";
            } else {
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

            dto.context = buildContext(term);
            dto.notes = buildNotes(term, dto.source_language);
            dto.contributor = term.getContributor() != null ? term.getContributor() : "84000 Glossary";
            dto.verified_status = "unverified";
            dto.content_hash = SupabaseClient.generateHash(
                    dto.source_term, dto.source_language,
                    dto.target_term, dto.target_language,
                    dto.context, dto.contributor
            );
            dto.date_added = Instant.now().toString();

            return dto;
        }

        private String buildContext(GlossaryTerm term) {
            StringBuilder context = new StringBuilder();
            String definition = term.getContext() != null ? term.getContext() : term.getDefinition();

            if (definition != null && !definition.isEmpty()) {
                context.append(definition);
            }

            if (term.getReferences() != null && !term.getReferences().isEmpty()) {
                if (context.length() > 0) context.append("\n\n");
                context.append("Referências:\n");
                term.getReferences().forEach(ref ->
                        context.append("• ").append(ref).append("\n"));
            }

            return context.toString();
        }

        private String buildNotes(GlossaryTerm term, String sourceLanguage) {
            StringBuilder notes = new StringBuilder();

            if (term.getWylie() != null && !term.getWylie().isEmpty()) {
                notes.append("Wylie: ").append(term.getWylie()).append("\n");
            }

            if (term.getSanskrit() != null && !term.getSanskrit().isEmpty() &&
                    !sourceLanguage.equals("Sanskrit")) {
                notes.append("Sanskrit: ").append(term.getSanskrit()).append("\n");
            }

            if (term.getType() != null && !term.getType().isEmpty()) {
                notes.append("Type: ").append(term.getType()).append("\n");
            }

            if (term.getNotes() != null && !term.getNotes().isEmpty()) {
                if (notes.length() > 0) notes.append("\n");
                notes.append(term.getNotes());
            }

            return notes.toString();
        }
    }
}