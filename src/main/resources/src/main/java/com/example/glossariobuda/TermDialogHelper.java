package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.List;

/**
 * Helper class for creating and managing term add/edit dialogs
 * Centralizes the dialog logic to avoid duplication between CompactViewController and GlossarioController
 */
public class TermDialogHelper {

    private DatabaseManager dbManager;



    public TermDialogHelper(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    public void showAddDialog() {
        // now dbManager is never null
        createTermEditGrid(null);
    }
    /**
     * Callback interface for notifying when a term is added or edited
     */
    public interface TermOperationCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    /**
     * Callback interface for delete operations with count of deleted items
     */
    public interface DeleteOperationCallback {
        void onSuccess(int deletedCount, String message);
        void onCancel();
    }

    /**
     * Show dialog to add a new term
     */
    public void showAddDialog(Window owner, DatabaseManager dbManager, TermOperationCallback callback) {
        showTermDialog(owner, dbManager, null, callback);
    }

    /**
     * Show dialog to edit an existing term
     */
    public void showEditDialog(Window owner, DatabaseManager dbManager, DatabaseManager.Term term, TermOperationCallback callback) {
        if (term == null) {
            callback.onError("Nenhum termo selecionado para editar");
            return;
        }
        showTermDialog(owner, dbManager, term, callback);
    }
    private void showTermDialog(Window owner, DatabaseManager dbManager, DatabaseManager.Term existingTerm, TermOperationCallback callback) {
        boolean isEditMode = (existingTerm != null);

        // Create non-modal Stage
        Stage dialogStage = new Stage();
        dialogStage.setTitle(isEditMode ? "Editar Termo" : "Adicionar Novo Termo");
        dialogStage.initModality(Modality.NONE);
        dialogStage.initOwner(owner);
        dialogStage.initStyle(StageStyle.DECORATED);
        dialogStage.setResizable(true);

        // Set application icon
        try {
            dialogStage.getIcons().add(new Image(TermDialogHelper.class.getResourceAsStream("/icons/pramana.png")));
        } catch (Exception e) {
            System.err.println("Warning: Could not load dialog icon: " + e.getMessage());
        }

        // Create content
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setFillWidth(true);

        Label headerLabel = new Label(isEditMode ? "Edite o termo selecionado" : "Adicione um novo termo ao glossário");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = createTermEditGrid(existingTerm, dbManager);

        // ✅ EXTRAIR CAMPOS DO GRID DE FORMA ROBUSTA
        TextField sourceTermField = null;
        ComboBox<String> sourceLangCombo = null;
        TextField targetTermField = null;
        ComboBox<String> targetLangCombo = null;
        TextArea contextArea = null;
        TextField contributorField = null;
        ComboBox<String> ownerCombo = null;
        TextField romanizationField = null;
        TextArea notesArea = null;
        ComboBox<String> statusCombo = null;

        // Iterar pelos children do grid para encontrar cada campo pela sua linha
        for (javafx.scene.Node node : grid.getChildren()) {
            Integer rowIndex = GridPane.getRowIndex(node);
            Integer colIndex = GridPane.getColumnIndex(node);

            if (rowIndex != null && colIndex != null && colIndex == 1) {
                switch (rowIndex) {
                    case 0: // Source term
                        if (node instanceof HBox) {
                            sourceTermField = (TextField) ((HBox) node).getChildren().get(0);
                        }
                        break;
                    case 1: // Source language
                        if (node instanceof HBox) {
                            sourceLangCombo = (ComboBox<String>) ((HBox) node).getChildren().get(0);
                        }
                        break;
                    case 2: // Target term
                        if (node instanceof HBox) {
                            targetTermField = (TextField) ((HBox) node).getChildren().get(0);
                        }
                        break;
                    case 3: // Target language
                        if (node instanceof HBox) {
                            targetLangCombo = (ComboBox<String>) ((HBox) node).getChildren().get(0);
                        }
                        break;
                    case 4: // Context
                        if (node instanceof HBox) {
                            contextArea = (TextArea) ((HBox) node).getChildren().get(0);
                        }
                        break;
                    case 5: // Contributor
                        if (node instanceof TextField) {
                            contributorField = (TextField) node;
                        }
                        break;
                    case 6: // Owner
                        if (node instanceof ComboBox) {
                            ownerCombo = (ComboBox<String>) node;
                        }
                        break;
                    case 7: // Romanization
                        if (node instanceof TextField) {
                            romanizationField = (TextField) node;
                        }
                        break;
                    case 8: // Notes
                        if (node instanceof HBox) {
                            notesArea = (TextArea) ((HBox) node).getChildren().get(0);
                        }
                        break;
                    case 9: // Status
                        if (node instanceof ComboBox) {
                            statusCombo = (ComboBox<String>) node;
                        }
                        break;
                }
            }
        }

        // Verificar se todos os campos foram encontrados
        if (sourceTermField == null || sourceLangCombo == null || targetTermField == null ||
                targetLangCombo == null || contextArea == null || contributorField == null ||
                ownerCombo == null || romanizationField == null || notesArea == null || statusCombo == null) {
            System.err.println("ERROR: Could not find all form fields in grid!");
            return;
        }

        // Campos finais para uso em lambda
        final TextField finalSourceTermField = sourceTermField;
        final ComboBox<String> finalSourceLangCombo = sourceLangCombo;
        final TextField finalTargetTermField = targetTermField;
        final ComboBox<String> finalTargetLangCombo = targetLangCombo;
        final TextArea finalContextArea = contextArea;
        final TextField finalContributorField = contributorField;
        final ComboBox<String> finalOwnerCombo = ownerCombo;
        final TextField finalRomanizationField = romanizationField;
        final TextArea finalNotesArea = notesArea;
        final ComboBox<String> finalStatusCombo = statusCombo;

        // Buttons
        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Button actionButton = new Button(isEditMode ? "Salvar" : "Adicionar");
        actionButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        actionButton.setDisable(!isEditMode); // Disable for add mode until source term is entered

        Button cancelButton = new Button("Cancelar");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelButton.setOnAction(e -> dialogStage.close());

        buttonBar.getChildren().addAll(actionButton, cancelButton);

        // Validation: require source term for add mode
        if (!isEditMode) {
            finalSourceTermField.textProperty().addListener((observable, oldValue, newValue) -> {
                actionButton.setDisable(newValue.trim().isEmpty());
            });
        }

        // Action button handler
        actionButton.setOnAction(e -> {
            String ownerValue = finalOwnerCombo.getValue();
            if (ownerValue == null || ownerValue.trim().isEmpty()) {
                ownerValue = "shared"; // Default
            }

            String structuredNotes = buildStructuredNotes(
                    finalRomanizationField.getText().trim(),
                    finalNotesArea.getText().trim()
            );

            if (isEditMode) {
                // Update existing term
                UpdateTermParams params = new UpdateTermParams(
                        finalSourceTermField.getText().trim(),
                        finalSourceLangCombo.getValue(),
                        finalTargetTermField.getText().trim(),
                        finalTargetLangCombo.getValue(),
                        finalContextArea.getText().trim(),
                        finalContributorField.getText().trim(),
                        structuredNotes,
                        finalStatusCombo.getValue(),
                        ownerValue
                );
                dbManager.updateTerm(existingTerm.getId(), params);
                callback.onSuccess("Termo atualizado com sucesso!");
            } else {
                // Add new term
                AddTermParams params = new AddTermParams(
                        finalSourceTermField.getText().trim(),
                        finalSourceLangCombo.getValue(),
                        finalTargetTermField.getText().trim(),
                        finalTargetLangCombo.getValue(),
                        finalContextArea.getText().trim(),
                        finalContributorField.getText().trim(),
                        structuredNotes,
                        ownerValue // Owner field
                );
                boolean success = dbManager.addTerm(params);

                if (success) {
                    callback.onSuccess("Termo adicionado com sucesso!");
                } else {
                    callback.onError("Erro ao adicionar termo (Duplicado?)!");
                }
            }

            dialogStage.close();
        });

        // Set grow priorities so content expands with window
        javafx.scene.layout.VBox.setVgrow(grid, javafx.scene.layout.Priority.ALWAYS);

        root.getChildren().addAll(headerLabel, grid, buttonBar);

        Scene scene = new Scene(root, 850, 650);
        dialogStage.setScene(scene);
        dialogStage.setMinWidth(700);
        dialogStage.setMinHeight(500);

        // Request focus on source term field
        Platform.runLater(finalSourceTermField::requestFocus);

        System.out.println("DEBUG [DIALOG] - Showing " + (isEditMode ? "EDIT" : "ADD") + " dialog");
        dialogStage.show();
    }

    // 5. MÉTODO createTermEditGrid() COMPLETO CORRIGIDO:
    private GridPane createTermEditGrid(DatabaseManager.Term term, DatabaseManager dbManager) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Set column constraints so column 1 (input fields) expands
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setHgrow(javafx.scene.layout.Priority.NEVER);

        javafx.scene.layout.ColumnConstraints col2 = new javafx.scene.layout.ColumnConstraints();
        col2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        col2.setFillWidth(true);

        grid.getColumnConstraints().addAll(col1, col2);

        // ========== ROW 0: SOURCE TERM ==========
        TextField sourceTermField = new TextField();
        sourceTermField.setPromptText("śamatha, mahāyāna, etc.");
        sourceTermField.setPrefWidth(300);
        sourceTermField.setMaxWidth(Double.MAX_VALUE);

        Button sourceInputButton = new Button("བོད་");
        sourceInputButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 5px 10px;");
        sourceInputButton.setTooltip(new Tooltip("Abrir entrada de teclado tibetano"));
        sourceInputButton.setOnAction(e -> TibetanInputHelper.openForTextField(sourceTermField));

        HBox sourceInputBox = new HBox(5);
        javafx.scene.layout.HBox.setHgrow(sourceTermField, javafx.scene.layout.Priority.ALWAYS);
        sourceInputBox.setMaxWidth(Double.MAX_VALUE);
        sourceInputBox.getChildren().addAll(sourceTermField, sourceInputButton);

        // ========== ROW 1: SOURCE LANGUAGE ==========
        ComboBox<String> sourceLangCombo = new ComboBox<>();
        HBox sourceLangBox = LanguageComboBoxHelper.createSourceLanguageComboBox(sourceLangCombo, "English");
        sourceLangBox.setMaxWidth(Double.MAX_VALUE);

        // ========== ROW 2: TARGET TERM ==========
        TextField targetTermField = new TextField();
        targetTermField.setPromptText("ཐེག་པ་ཆེན་པོ།");
        targetTermField.setPrefWidth(300);
        targetTermField.setMaxWidth(Double.MAX_VALUE);

        Button tibetanInputButton = new Button("བོད་");
        tibetanInputButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 5px 10px;");
        tibetanInputButton.setTooltip(new Tooltip("Abrir entrada de teclado tibetano"));
        tibetanInputButton.setOnAction(e -> TibetanInputHelper.openForTextField(targetTermField));

        HBox tibetanInputBox = new HBox(5);
        javafx.scene.layout.HBox.setHgrow(targetTermField, javafx.scene.layout.Priority.ALWAYS);
        tibetanInputBox.setMaxWidth(Double.MAX_VALUE);
        tibetanInputBox.getChildren().addAll(targetTermField, tibetanInputButton);

        // ========== ROW 3: TARGET LANGUAGE ==========
        ComboBox<String> targetLangCombo = new ComboBox<>();
        HBox targetLangBox = LanguageComboBoxHelper.createTargetLanguageComboBox(targetLangCombo, "Tibetan");
        targetLangBox.setMaxWidth(Double.MAX_VALUE);

        // ========== ROW 4: CONTEXT ==========
        TextArea contextArea = new TextArea();
        contextArea.setPromptText("Contexto ou definição do termo (opcional)");
        contextArea.setPrefRowCount(5);
        contextArea.setPrefWidth(300);
        contextArea.setMaxWidth(Double.MAX_VALUE);
        contextArea.setWrapText(true);

        Button contextInputButton = new Button("བོད་");
        contextInputButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 5px 10px;");
        contextInputButton.setTooltip(new Tooltip("Abrir entrada de teclado tibetano"));
        contextInputButton.setOnAction(e -> TibetanInputHelper.openForTextArea(contextArea));

        HBox contextInputBox = new HBox(5);
        javafx.scene.layout.HBox.setHgrow(contextArea, javafx.scene.layout.Priority.ALWAYS);
        contextInputBox.setMaxWidth(Double.MAX_VALUE);
        contextInputBox.getChildren().addAll(contextArea, contextInputButton);

        // ========== ROW 5: CONTRIBUTOR ==========
        TextField contributorField = new TextField();
        contributorField.setPromptText("Seu nome (opcional)");
        contributorField.setMaxWidth(Double.MAX_VALUE);

        // ========== ROW 6: OWNER ==========
        ComboBox<String> ownerCombo = new ComboBox<>();
        ownerCombo.setEditable(true);
        ownerCombo.setPromptText("Selecione ou digite o proprietário");
        ownerCombo.setMaxWidth(Double.MAX_VALUE);

        // Populate with existing owners
        List<String> owners = dbManager.getAllOwners();
        ownerCombo.getItems().add("shared");
        ownerCombo.getItems().addAll(owners);

        // Set default value
        if (term != null && term.getOwner() != null && !term.getOwner().isEmpty()) {
            ownerCombo.setValue(term.getOwner());
        } else {
            ownerCombo.setValue("shared");
        }

        // ========== ROW 7: ROMANIZATION ==========
        TextField romanizationField = new TextField();
        romanizationField.setPromptText("Ex: theg pa chen po, mahāyāna, dàshèng");
        romanizationField.setPrefWidth(400);
        romanizationField.setMaxWidth(Double.MAX_VALUE);

        // ========== ROW 8: NOTES ==========
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Notas adicionais (opcional)");
        notesArea.setPrefRowCount(4);
        notesArea.setPrefWidth(400);
        notesArea.setMaxWidth(Double.MAX_VALUE);
        notesArea.setWrapText(true);

        Button notesInputButton = new Button("བོད་");
        notesInputButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 5px 10px;");
        notesInputButton.setTooltip(new Tooltip("Abrir entrada de teclado tibetano"));
        notesInputButton.setOnAction(e -> TibetanInputHelper.openForTextArea(notesArea));

        HBox notesInputBox = new HBox(5);
        javafx.scene.layout.HBox.setHgrow(notesArea, javafx.scene.layout.Priority.ALWAYS);
        notesInputBox.setMaxWidth(Double.MAX_VALUE);
        notesInputBox.getChildren().addAll(notesArea, notesInputButton);

        // ========== ROW 9: STATUS ==========
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("draft", "reviewed", "verified", "approved");
        statusCombo.setValue("draft");

        // ========== POPULATE FIELDS IF EDITING ==========
        if (term != null) {
            sourceTermField.setText(term.getSourceTerm());
            sourceLangCombo.setValue(term.getSourceLanguage());
            targetTermField.setText(term.getTargetTerm());
            targetLangCombo.setValue(term.getTargetLanguage());

            // Handle List<String> for contexts
            java.util.List<String> contexts = term.getContexts();
            if (contexts != null && !contexts.isEmpty()) {
                contextArea.setText(String.join("\n", contexts));
            }

            // Handle List<String> for contributors
            java.util.List<String> contributors = term.getContributors();
            if (contributors != null && !contributors.isEmpty()) {
                contributorField.setText(String.join(", ", contributors));
            }

            // Parse structured notes to extract romanization
            String notes = term.getNotes();
            System.out.println("DEBUG [LOAD] - Original notes from DB: " + notes);
            if (notes != null && !notes.isEmpty()) {
                String romanizationContent = extractFieldFromNotes(notes, "Romanizado:");
                if (romanizationContent.isEmpty()) {
                    romanizationContent = extractFieldFromNotes(notes, "Romanization:");
                }

                System.out.println("DEBUG [LOAD] - Extracted romanization content: " + romanizationContent);
                romanizationField.setText(romanizationContent);

                String remainingNotes = notes
                        .replaceAll("Romanizado:[^\n]*\n?", "")
                        .replaceAll("Romanization:[^\n]*\n?", "")
                        .trim();
                System.out.println("DEBUG [LOAD] - Remaining notes: " + remainingNotes);
                notesArea.setText(remainingNotes);
            }

            statusCombo.setValue(term.getVerifiedStatus());
        }

        // ========== ADD ALL FIELDS TO GRID ==========
        grid.add(new Label("Termo Original:"), 0, 0);
        grid.add(sourceInputBox, 1, 0);

        grid.add(new Label("Idioma Original:"), 0, 1);
        grid.add(sourceLangBox, 1, 1);

        grid.add(new Label("Tradução:"), 0, 2);
        grid.add(tibetanInputBox, 1, 2);

        grid.add(new Label("Idioma da Tradução:"), 0, 3);
        grid.add(targetLangBox, 1, 3);

        grid.add(new Label("Contexto/Definição:"), 0, 4);
        grid.add(contextInputBox, 1, 4);

        grid.add(new Label("Contribuidor:"), 0, 5);
        grid.add(contributorField, 1, 5);

        grid.add(new Label("Proprietário:"), 0, 6);
        grid.add(ownerCombo, 1, 6);

        grid.add(new Label("Romanizado:"), 0, 7);
        grid.add(romanizationField, 1, 7);

        grid.add(new Label("Notas:"), 0, 8);
        grid.add(notesInputBox, 1, 8);

        grid.add(new Label("Status:"), 0, 9);
        grid.add(statusCombo, 1, 9);

        return grid;
    }


    /**
     * Create the term edit grid with all form fields
     */
    private GridPane createTermEditGrid(DatabaseManager.Term term) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Set column constraints so column 1 (input fields) expands
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setHgrow(javafx.scene.layout.Priority.NEVER);

        javafx.scene.layout.ColumnConstraints col2 = new javafx.scene.layout.ColumnConstraints();
        col2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        col2.setFillWidth(true);

        grid.getColumnConstraints().addAll(col1, col2);

        // Source term field
        TextField sourceTermField = new TextField();
        sourceTermField.setPromptText("śamatha, mahāyāna, etc.");
        sourceTermField.setPrefWidth(300);
        sourceTermField.setMaxWidth(Double.MAX_VALUE);

        Button sourceInputButton = new Button("བོད་");
        sourceInputButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 5px 10px;");
        sourceInputButton.setTooltip(new Tooltip("Abrir entrada de teclado tibetano"));
        sourceInputButton.setOnAction(e -> TibetanInputHelper.openForTextField(sourceTermField));

        HBox sourceInputBox = new HBox(5);
        javafx.scene.layout.HBox.setHgrow(sourceTermField, javafx.scene.layout.Priority.ALWAYS);
        sourceInputBox.setMaxWidth(Double.MAX_VALUE);
        sourceInputBox.getChildren().addAll(sourceTermField, sourceInputButton);

        // Source language
        ComboBox<String> sourceLangCombo = new ComboBox<>();
        HBox sourceLangBox = LanguageComboBoxHelper.createSourceLanguageComboBox(sourceLangCombo, "English");
        sourceLangBox.setMaxWidth(Double.MAX_VALUE);

        // Target term field
        TextField targetTermField = new TextField();
        targetTermField.setPromptText("ཐེག་པ་ཆེན་པོ།");
        targetTermField.setPrefWidth(300);
        targetTermField.setMaxWidth(Double.MAX_VALUE);

        Button tibetanInputButton = new Button("བོད་");
        tibetanInputButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 5px 10px;");
        tibetanInputButton.setTooltip(new Tooltip("Abrir entrada de teclado tibetano"));
        tibetanInputButton.setOnAction(e -> TibetanInputHelper.openForTextField(targetTermField));

        HBox tibetanInputBox = new HBox(5);
        javafx.scene.layout.HBox.setHgrow(targetTermField, javafx.scene.layout.Priority.ALWAYS);
        tibetanInputBox.setMaxWidth(Double.MAX_VALUE);
        tibetanInputBox.getChildren().addAll(targetTermField, tibetanInputButton);

        // Target language
        ComboBox<String> targetLangCombo = new ComboBox<>();
        HBox targetLangBox = LanguageComboBoxHelper.createTargetLanguageComboBox(targetLangCombo, "Tibetan");
        targetLangBox.setMaxWidth(Double.MAX_VALUE);

        // Context area
        TextArea contextArea = new TextArea();
        contextArea.setPromptText("Contexto ou definição do termo (opcional)");
        contextArea.setPrefRowCount(5);
        contextArea.setPrefWidth(300);
        contextArea.setMaxWidth(Double.MAX_VALUE);
        contextArea.setWrapText(true);

        Button contextInputButton = new Button("བོད་");
        contextInputButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 5px 10px;");
        contextInputButton.setTooltip(new Tooltip("Abrir entrada de teclado tibetano"));
        contextInputButton.setOnAction(e -> TibetanInputHelper.openForTextArea(contextArea));

        HBox contextInputBox = new HBox(5);
        javafx.scene.layout.HBox.setHgrow(contextArea, javafx.scene.layout.Priority.ALWAYS);
        contextInputBox.setMaxWidth(Double.MAX_VALUE);
        contextInputBox.getChildren().addAll(contextArea, contextInputButton);

        // Contributor field
        TextField contributorField = new TextField();
        contributorField.setPromptText("Seu nome (opcional)");
        contributorField.setMaxWidth(Double.MAX_VALUE);

        // Add this after the contributor field and before romanization field

// Owner field (ComboBox with editable option)
        ComboBox<String> ownerCombo = new ComboBox<>();
        ownerCombo.setEditable(true); // Allow typing new owner names
        ownerCombo.setPromptText("Selecione ou digite o proprietário");
        ownerCombo.setMaxWidth(Double.MAX_VALUE);

// Populate with existing owners
        List<String> owners = dbManager.getAllOwners();
        ownerCombo.getItems().add("shared"); // Always include shared option
        ownerCombo.getItems().addAll(owners);

// Set default value
        if (term != null && term.getOwner() != null && !term.getOwner().isEmpty()) {
            ownerCombo.setValue(term.getOwner());
        } else {
            ownerCombo.setValue("shared"); // Default to shared
        }

        // Romanization field
        TextField romanizationField = new TextField();
        romanizationField.setPromptText("Ex: theg pa chen po, mahāyāna, dàshèng");
        romanizationField.setPrefWidth(400);
        romanizationField.setMaxWidth(Double.MAX_VALUE);

        // Notes area
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Notas adicionais (opcional)");
        notesArea.setPrefRowCount(4);
        notesArea.setPrefWidth(400);
        notesArea.setMaxWidth(Double.MAX_VALUE);
        notesArea.setWrapText(true);

        Button notesInputButton = new Button("བོད་");
        notesInputButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 5px 10px;");
        notesInputButton.setTooltip(new Tooltip("Abrir entrada de teclado tibetano"));
        notesInputButton.setOnAction(e -> TibetanInputHelper.openForTextArea(notesArea));

        HBox notesInputBox = new HBox(5);
        javafx.scene.layout.HBox.setHgrow(notesArea, javafx.scene.layout.Priority.ALWAYS);
        notesInputBox.setMaxWidth(Double.MAX_VALUE);
        notesInputBox.getChildren().addAll(notesArea, notesInputButton);

        // Status combo
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("draft", "reviewed", "verified", "approved");
        statusCombo.setValue("draft");

        // Populate fields if editing existing term
        if (term != null) {
            sourceTermField.setText(term.getSourceTerm());
            sourceLangCombo.setValue(term.getSourceLanguage());
            targetTermField.setText(term.getTargetTerm());
            targetLangCombo.setValue(term.getTargetLanguage());

            // ✅ FIXED: Handle List<String> for contexts
            java.util.List<String> contexts = term.getContexts();
            if (contexts != null && !contexts.isEmpty()) {
                // Join multiple contexts with newlines
                contextArea.setText(String.join("\n", contexts));
            }

            // ✅ FIXED: Handle List<String> for contributors
            java.util.List<String> contributors = term.getContributors();
            if (contributors != null && !contributors.isEmpty()) {
                // Join multiple contributors with commas
                contributorField.setText(String.join(", ", contributors));
            }

            // Parse structured notes to extract romanization content
            String notes = term.getNotes();
            System.out.println("DEBUG [LOAD] - Original notes from DB: " + notes);
            if (notes != null && !notes.isEmpty()) {
                String romanizationContent = extractFieldFromNotes(notes, "Romanizado:");
                if (romanizationContent.isEmpty()) {
                    romanizationContent = extractFieldFromNotes(notes, "Romanization:");
                }

                System.out.println("DEBUG [LOAD] - Extracted romanization content: " + romanizationContent);
                romanizationField.setText(romanizationContent);

                String remainingNotes = notes
                        .replaceAll("Romanizado:[^\n]*\n?", "")
                        .replaceAll("Romanization:[^\n]*\n?", "")
                        .trim();
                System.out.println("DEBUG [LOAD] - Remaining notes: " + remainingNotes);
                notesArea.setText(remainingNotes);
            }

            statusCombo.setValue(term.getVerifiedStatus());
        }

        // Add fields to grid
        grid.add(new Label("Termo Original:"), 0, 0);
        grid.add(sourceInputBox, 1, 0);
        grid.add(new Label("Idioma Original:"), 0, 1);
        grid.add(sourceLangBox, 1, 1);
        grid.add(new Label("Tradução:"), 0, 2);
        grid.add(tibetanInputBox, 1, 2);
        grid.add(new Label("Idioma da Tradução:"), 0, 3);
        grid.add(targetLangBox, 1, 3);
        grid.add(new Label("Contexto/Definição:"), 0, 4);
        grid.add(contextInputBox, 1, 4);
        grid.add(new Label("Contribuidor:"), 0, 5);
        grid.add(contributorField, 1, 5);
        grid.add(new Label("Proprietário:"), 0, 6);
        grid.add(ownerCombo, 1, 6);
        grid.add(new Label("Romanizado:"), 0, 6);
        grid.add(romanizationField, 1, 6);
        grid.add(new Label("Notas:"), 0, 7);
        grid.add(notesInputBox, 1, 7);
        grid.add(new Label("Status:"), 0, 8);
        grid.add(statusCombo, 1, 8);

        return grid;
    }
    /**
     * Build structured notes from romanization and additional notes
     */
    private static String buildStructuredNotes(String romanization, String additionalNotes) {
        System.out.println("DEBUG [SAVE] - Input romanization field: " + romanization);
        System.out.println("DEBUG [SAVE] - Input additional notes: " + additionalNotes);

        StringBuilder notes = new StringBuilder();

        // Add romanization if present
        if (romanization != null && !romanization.isEmpty()) {
            notes.append("Romanizado: ").append(romanization).append("\n");
        }

        // Add additional notes if present
        if (additionalNotes != null && !additionalNotes.isEmpty()) {
            if (notes.length() > 0) {
                notes.append("\n");
            }
            notes.append(additionalNotes);
        }

        String finalNotes = notes.toString().trim();
        System.out.println("DEBUG [SAVE] - Final notes to DB: " + finalNotes);
        return finalNotes;
    }

    /**
     * Extract a field value from structured notes
     */
    private static String extractFieldFromNotes(String notes, String fieldName) {
        if (notes == null) return "";
        int startIndex = notes.indexOf(fieldName);
        if (startIndex == -1) return "";

        startIndex += fieldName.length();
        int endIndex = notes.indexOf("\n", startIndex);

        if (endIndex == -1) {
            endIndex = notes.length();
        }

        String extracted = notes.substring(startIndex, endIndex).trim();
        System.out.println("DEBUG [EXTRACT] - Extracting '" + fieldName + "' from position " + startIndex + ": '" + extracted + "'");
        return extracted;
    }

    /**
     * Show confirmation dialog and delete selected terms
     * Supports both single and multiple term deletion
     * Always deletes from cloud (permanent deletion)
     */
    public static void showDeleteDialog(Window owner, DatabaseManager dbManager,
                                       java.util.List<DatabaseManager.Term> termsToDelete,
                                       DeleteOperationCallback callback) {
        if (termsToDelete == null || termsToDelete.isEmpty()) {
            callback.onCancel();
            return;
        }

        // Build term description for warning
        String termDescription;
        if (termsToDelete.size() == 1) {
            DatabaseManager.Term term = termsToDelete.get(0);
            termDescription = "'" + term.getSourceTerm() + "' → '" + term.getTargetTerm() + "'";
        } else {
            termDescription = termsToDelete.size() + " termos selecionados";
        }

        // Build warning message
        String headerText = termsToDelete.size() == 1
                ? "Deletar termo permanentemente?"
                : "Deletar " + termsToDelete.size() + " termos permanentemente?";

        String contentText = "⚠️ ATENÇÃO: Esta ação NÃO pode ser desfeita!\n\n" +
                "Você está prestes a deletar:\n" +
                termDescription + "\n\n" +
                "O" + (termsToDelete.size() > 1 ? "s termo" + (termsToDelete.size() > 1 ? "s" : "") + " será" + (termsToDelete.size() > 1 ? "ão" : "") : " termo será") + " removido" + (termsToDelete.size() > 1 ? "s" : "") + ":\n" +
                "• Do banco de dados local\n" +
                "• Do banco de dados na nuvem (Supabase)\n" +
                "• De todos os outros dispositivos após sincronização\n\n" +
                "Tem CERTEZA que deseja continuar?";

        Alert confirmAlert = new Alert(Alert.AlertType.WARNING);
        confirmAlert.setTitle("⚠️ DELETAR PERMANENTEMENTE");
        confirmAlert.setHeaderText(headerText);
        confirmAlert.setContentText(contentText);

        ButtonType confirmDelete = new ButtonType("Sim, Deletar Permanentemente", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        confirmAlert.getButtonTypes().setAll(confirmDelete, cancel);

        // Set icon if available
        confirmAlert.setOnShown(e -> {
            try {
                Stage alertStage = (Stage) confirmAlert.getDialogPane().getScene().getWindow();
                alertStage.getIcons().add(new javafx.scene.image.Image(
                    TermDialogHelper.class.getResourceAsStream("/icons/pramana.png")));
            } catch (Exception ex) {
                System.err.println("Warning: Could not load dialog icon: " + ex.getMessage());
            }
        });

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response != confirmDelete) {
                callback.onCancel();
                return;
            }

            // Always delete from cloud (permanent deletion)
            boolean deleteFromCloud = true;

            // Delete each term
            int deletedCount = 0;
            for (DatabaseManager.Term term : termsToDelete) {
                try {
                    dbManager.deleteTerm(term.getId(), deleteFromCloud);
                    deletedCount++;
                } catch (Exception e) {
                    System.err.println("Error deleting term " + term.getId() + ": " + e.getMessage());
                }
            }

            // Build success message
            String feedback = deletedCount == 1
                    ? "Termo deletado permanentemente"
                    : deletedCount + " termos deletados permanentemente";

            callback.onSuccess(deletedCount, feedback);
        });
    }
}
