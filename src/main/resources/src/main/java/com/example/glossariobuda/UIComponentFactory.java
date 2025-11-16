package com.example.glossariobuda;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Simplified UI Component Factory for Glossário BUDA
 * Contains only essential styling methods without external dependencies
 */
public class UIComponentFactory {

    private static final Duration TOOLTIP_SHOW_DELAY = Duration.millis(300);
    private static final Duration TOOLTIP_HIDE_DELAY = Duration.millis(100);

    // Standard heights for consistency
    private static final double BUTTON_HEIGHT = 28.0;
    public static final double FIELD_HEIGHT = 29.0;
    public static final double FIELD_HEIGHT_2 = 29.0;

    // Spacing constants for consistent layout
    public static final double STANDARD_SPACING = 5.0;
    public static final double LARGE_SPACING = 10.0;
    public static final double BUTTON_SPACING = 10.0;
    public static final double COMPACT_SPACING = 2.0;
    public static final double NO_SPACING = 0.0;
    public static final double SUMMARY_SPACING = 15.0;

    // Color constants (simplified)
    public static final String SAVE_ACTION_COLOR = UIColorScheme.SAVE_ACTION_COLOR;
    public static final String CANCEL_ACTION_COLOR = UIColorScheme.CANCEL_ACTION_COLOR;
    public static final String PROCESS_ACTION_COLOR = UIColorScheme.PROCESS_ACTION_COLOR;
    public static final String NAVIGATION_COLOR = UIColorScheme.NAVIGATION_COLOR;
    public static final String FEATURE_MISSING_DAYS_COLOR = UIColorScheme.FEATURE_MISSING_DAYS_COLOR;
    public static final String FEATURE_ALL_MANTRAS_COLOR = UIColorScheme.FEATURE_ALL_MANTRAS_COLOR;
    public static final String FEATURE_SEM_FIZ_COLOR = UIColorScheme.FEATURE_SEM_FIZ_COLOR;
    public static final String UPDATE_COLOR = UIColorScheme.UPDATE_COLOR;
    public static final String UNDO_COLOR = UIColorScheme.UNDO_COLOR;
    public static final String INSERIR_MANTRA_COLOR = UIColorScheme.INSERIR_MANTRA_COLOR;

    public enum ButtonAlignment {
        LEFT, CENTER, RIGHT
    }

    public enum TextAreaState {
        NORMAL, PLACEHOLDER, SUCCESS, ERROR, INFO
    }

    public static Button createButton(String text, String tooltip) {
        Button button = new Button(text);
        addTooltip(button, tooltip);
        return button;
    }

    public static class ActionButtons {

        public static Button createSaveButton() {
            return createStyledButton("Salvar", "Save Changes", SAVE_ACTION_COLOR, null);
        }

        public static Button createCancelButton() {
            return createStyledButton("Cancelar", "Cancel", CANCEL_ACTION_COLOR, null);
        }

        public static Button createProcessButton() {
            return createStyledButton("Processar", "Process", PROCESS_ACTION_COLOR, null);
        }

        public static Button createNavigationButton() {
            return createStyledButton("Navegar", "Navigate", NAVIGATION_COLOR, null);
        }
    }

    public static class TextFields {

        public static TextField createTextField(String placeholder, String tooltip) {
            TextField field = new TextField();
            field.setPromptText(placeholder);
            addTooltip(field, tooltip);
            field.setStyle(UIColorScheme.getInputFieldStyle());

            field.setPrefHeight(FIELD_HEIGHT);
            field.setMinHeight(FIELD_HEIGHT);
            field.setMaxHeight(FIELD_HEIGHT);

            addInputFieldFocusEffect(field);
            return field;
        }

        public static TextField createSearchField() {
            return createTextField("Buscar...", "Search field");
        }

        public static TextField createEditLineField(String content) {
            TextField field = new TextField(content);
            field.setPromptText("Editar linha");
            addTooltip(field, "Editable content");
            field.setStyle(UIColorScheme.getInputFieldStyle());

            field.setPrefHeight(FIELD_HEIGHT);
            field.setMinHeight(FIELD_HEIGHT);
            field.setMaxHeight(FIELD_HEIGHT);

            addInputFieldFocusEffect(field);
            return field;
        }
    }

    public static void applyStandardFieldHeight(TextField field) {
        field.setPrefHeight(FIELD_HEIGHT);
        field.setMinHeight(FIELD_HEIGHT);
        field.setMaxHeight(FIELD_HEIGHT);
    }

    public static class DatePickers {

        public static DatePicker createStartDatePicker() {
            return createStandardDatePicker("Data Início", "Start Date");
        }

        public static DatePicker createEndDatePicker() {
            return createStandardDatePicker("Data Fim", "End Date");
        }

        private static DatePicker createStandardDatePicker(String promptText, String tooltip) {
            DatePicker datePicker = new DatePicker();
            datePicker.setEditable(true);
            datePicker.setStyle(UIColorScheme.getDatePickerStyle());

            datePicker.setPrefHeight(FIELD_HEIGHT);
            datePicker.setMinHeight(FIELD_HEIGHT);
            datePicker.setMaxHeight(FIELD_HEIGHT);

            datePicker.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    datePicker.setStyle(UIColorScheme.getDatePickerFocusedStyle());
                } else {
                    datePicker.setStyle(UIColorScheme.getDatePickerStyle());
                }
            });
            datePicker.setPromptText(promptText);
            addTooltip(datePicker, tooltip);
            return datePicker;
        }
    }

    public static class Layouts {

        public static HBox createButtonLayout(ButtonAlignment alignment, Node... nodes) {
            HBox layout = new HBox(BUTTON_SPACING, nodes);
            switch (alignment) {
                case LEFT -> layout.setAlignment(Pos.CENTER_LEFT);
                case CENTER -> layout.setAlignment(Pos.CENTER);
                case RIGHT -> layout.setAlignment(Pos.CENTER_RIGHT);
            }
            layout.setPadding(new Insets(10, 0, 0, 0));
            return layout;
        }

        public static HBox createMainActionLayout(Node... nodes) {
            return createButtonLayout(ButtonAlignment.LEFT, nodes);
        }

        public static HBox createDialogActionLayout(Node... nodes) {
            return createButtonLayout(ButtonAlignment.RIGHT, nodes);
        }
    }

    private static void addInputFieldFocusEffect(TextField field) {
        field.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                field.setStyle(UIColorScheme.getInputFieldFocusedStyle());
            } else {
                field.setStyle(UIColorScheme.getInputFieldStyle());
            }
        });
    }

    public static TextArea createResultsArea() {
        TextArea resultsArea = new TextArea();
        resultsArea.setText("Resultados");
        resultsArea.setPrefRowCount(8);
        resultsArea.setMinHeight(150);
        resultsArea.setMaxHeight(300);
        resultsArea.setEditable(false);
        resultsArea.setWrapText(true);

        resultsArea.setStyle(UIColorScheme.getResultsAreaStyle());

        addTooltip(resultsArea, "Results area");
        return resultsArea;
    }

    public static TextArea createSummaryArea(String initialText) {
        TextArea summaryArea = new TextArea();
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        summaryArea.setPrefRowCount(8);
        summaryArea.setMinHeight(150);
        summaryArea.setMaxHeight(300);
        summaryArea.setText(initialText);

        summaryArea.setStyle(UIColorScheme.getResultsAreaStyle());

        addTooltip(summaryArea, "Summary area");
        return summaryArea;
    }

    public static Label createHeaderLabel(String text, String englishTooltip) {
        Label header = new Label(text);
        header.setStyle(UIColorScheme.getHeaderLabelStyle());
        if (englishTooltip != null) {
            addTooltip(header, englishTooltip);
        }
        return header;
    }

    public static Label createPlaceholderLabel(String text, String englishTooltip) {
        Label placeholder = new Label(text);
        placeholder.setStyle(UIColorScheme.getPlaceholderLabelStyle());
        if (englishTooltip != null) {
            addTooltip(placeholder, englishTooltip);
        }
        return placeholder;
    }

    public static ScrollPane createStyledScrollPane(VBox content, double prefHeight) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(prefHeight);

        scrollPane.setStyle(
                "-fx-background: " + UIColorScheme.RESULTS_BACKGROUND + "; " +
                "-fx-control-inner-background: " + UIColorScheme.RESULTS_BACKGROUND + "; " +
                "-fx-background-color: " + UIColorScheme.RESULTS_BACKGROUND + "; " +
                "-fx-border-color: " + UIColorScheme.BORDER_ACCENT + "; " +
                "-fx-border-width: 2px; " +
                "-fx-border-radius: 4px;"
        );

        return scrollPane;
    }

    public static ScrollPane createStyledScrollPane(VBox content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);

        scrollPane.setStyle(
                "-fx-background: " + UIColorScheme.RESULTS_BACKGROUND + "; " +
                "-fx-control-inner-background: " + UIColorScheme.RESULTS_BACKGROUND + "; " +
                "-fx-background-color: " + UIColorScheme.RESULTS_BACKGROUND + "; " +
                "-fx-border-color: " + UIColorScheme.BORDER_ACCENT + "; " +
                "-fx-border-width: 2px; " +
                "-fx-border-radius: 4px;"
        );

        return scrollPane;
    }

    public static Button createStyledButton(String text, String tooltip, String color, String iconKey) {
        Button button = createButton(text, tooltip);
        if (color != null) {
            String simpleStyle = String.format(
                    "-fx-base: %s; -fx-text-fill: white; -fx-min-height: %s;",
                    color, BUTTON_HEIGHT
            );
            button.setStyle(simpleStyle);
            addHoverEffect(button, color);
        }
        return button;
    }

    public static ProgressIndicator createProgressIndicator() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(50, 50);
        progress.setVisible(false);
        addTooltip(progress, "Progress indicator");
        return progress;
    }

    public static CheckBox createExactWordCheckBox() {
        CheckBox checkBox = new CheckBox("Palavra exata");
        checkBox.setStyle(UIColorScheme.getCheckboxStyle());
        addTooltip(checkBox, "Exact word search");
        return checkBox;
    }

    public static Label createInfoBadge(String text, String englishTooltip) {
        Label badge = new Label(text);
        badge.setPadding(new Insets(2, 8, 2, 8));
        badge.setStyle(UIColorScheme.getInfoBadgeStyle());
        badge.setMinWidth(150);
        if (englishTooltip != null) {
            addTooltip(badge, englishTooltip);
        }
        return badge;
    }

    public static Label createTypeBadge(String type) {
        Label badge = new Label(type);
        badge.setPadding(new Insets(2, 8, 2, 8));
        badge.setStyle(UIColorScheme.getTypeBadgeStyle());
        badge.setPrefWidth(120);
        addTooltip(badge, "Type badge");
        return badge;
    }

    public static void setTextAreaState(TextArea textArea, TextAreaState state, String content) {
        textArea.setText(content);

        String baseStyle = UIColorScheme.getResultsAreaStyle();

        switch (state) {
            case NORMAL -> textArea.setStyle(baseStyle);
            case PLACEHOLDER -> textArea.setStyle(baseStyle);
            case SUCCESS -> textArea.setStyle(baseStyle.replace("#000000", UIColorScheme.TEXT_SUCCESS));
            case ERROR -> textArea.setStyle(baseStyle.replace("#000000", UIColorScheme.TEXT_ERROR));
            case INFO -> textArea.setStyle(baseStyle.replace("#000000", UIColorScheme.TEXT_INFO));
        }
    }

    public static void addTooltip(Control control, String tooltipText) {
        if (tooltipText != null && !tooltipText.isEmpty()) {
            Tooltip tooltip = new Tooltip(tooltipText);
            tooltip.setShowDelay(TOOLTIP_SHOW_DELAY);
            tooltip.setHideDelay(TOOLTIP_HIDE_DELAY);
            Tooltip.install(control, tooltip);
        }
    }

    public static void addTooltip(Node node, String tooltipText) {
        if (tooltipText != null && !tooltipText.isEmpty()) {
            Tooltip tooltip = new Tooltip(tooltipText);
            tooltip.setShowDelay(TOOLTIP_SHOW_DELAY);
            tooltip.setHideDelay(TOOLTIP_HIDE_DELAY);
            Tooltip.install(node, tooltip);
        }
    }

    public static void addHoverEffect(Button button, String originalColor) {
        button.setOnMouseEntered(e -> {
            if (!button.isDisabled()) {
                button.setStyle(String.format(
                        "-fx-base: %s; -fx-text-fill: white; -fx-min-height: %s;",
                        originalColor, BUTTON_HEIGHT
                ));
            }
        });
        button.setOnMouseExited(e -> {
            button.setStyle(String.format(
                    "-fx-base: %s; -fx-text-fill: white; -fx-min-height: %s;",
                    originalColor, BUTTON_HEIGHT
            ));
        });
    }
}