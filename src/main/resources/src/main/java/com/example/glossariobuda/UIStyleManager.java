package com.example.glossariobuda;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * UI Style Manager for Glossário BUDA
 * Provides programmatic styling utilities based on MantraCount approach
 */
public class UIStyleManager {

    private static final Duration TOOLTIP_SHOW_DELAY = Duration.millis(300);
    private static final Duration TOOLTIP_HIDE_DELAY = Duration.millis(100);

    // CSS Class Constants
    public static final String MAIN_BACKGROUND = "main-background";
    public static final String HEADER_CONTAINER = "header-container";
    public static final String SECTION_CONTAINER = "section-container";
    public static final String RESULTS_CONTAINER = "results-container";
    
    public static final String HEADER_TITLE = "header-title";
    public static final String SECTION_TITLE = "section-title";
    public static final String FIELD_LABEL = "field-label";
    public static final String FIELD_LABEL_SMALL = "field-label-small";
    public static final String STATUS_TEXT = "status-text";
    public static final String SUCCESS_TEXT = "success-text";
    public static final String ERROR_TEXT = "error-text";
    
    public static final String INPUT_FIELD = "input-field";
    public static final String INPUT_FIELD_SMALL = "input-field-small";
    public static final String HEADER_INPUT_FIELD = "header-input-field";
    public static final String TEXT_AREA = "text-area";
    public static final String RESULTS_AREA = "results-area";
    public static final String LIST_VIEW = "list-view";
    
    public static final String BUTTON = "button";
    public static final String BUTTON_SAVE = "button-save";
    public static final String BUTTON_CANCEL = "button-cancel";
    public static final String BUTTON_PROCESS = "button-process";
    public static final String BUTTON_NAVIGATION = "button-navigation";
    public static final String BUTTON_UPDATE = "button-update";
    public static final String BUTTON_INSERIR = "button-inserir";
    public static final String BUTTON_DEFAULT = "button-default";

    /**
     * Apply CSS class to a node
     */
    public static void applyStyleClass(Node node, String... styleClasses) {
        if (node != null && styleClasses != null) {
            for (String styleClass : styleClasses) {
                if (styleClass != null && !styleClass.isEmpty()) {
                    node.getStyleClass().add(styleClass);
                }
            }
        }
    }

    /**
     * Remove CSS class from a node
     */
    public static void removeStyleClass(Node node, String styleClass) {
        if (node != null && styleClass != null) {
            node.getStyleClass().remove(styleClass);
        }
    }

    /**
     * Set CSS classes, clearing existing ones first
     */
    public static void setStyleClasses(Node node, String... styleClasses) {
        if (node != null) {
            node.getStyleClass().clear();
            applyStyleClass(node, styleClasses);
        }
    }

    /**
     * Create a styled button with specific function
     */
    public static Button createStyledButton(String text, ButtonType type) {
        Button button = new Button(text);
        
        switch (type) {
            case SAVE -> applyStyleClass(button, BUTTON, BUTTON_SAVE);
            case CANCEL -> applyStyleClass(button, BUTTON, BUTTON_CANCEL);
            case PROCESS -> applyStyleClass(button, BUTTON, BUTTON_PROCESS);
            case NAVIGATION -> applyStyleClass(button, BUTTON, BUTTON_NAVIGATION);
            case UPDATE -> applyStyleClass(button, BUTTON, BUTTON_UPDATE);
            case INSERT -> applyStyleClass(button, BUTTON, BUTTON_INSERIR);
            default -> applyStyleClass(button, BUTTON, BUTTON_DEFAULT);
        }
        
        return button;
    }

    /**
     * Create a styled text field
     */
    public static TextField createStyledTextField(String promptText, TextFieldType type) {
        TextField textField = new TextField();
        textField.setPromptText(promptText);
        
        switch (type) {
            case HEADER -> applyStyleClass(textField, HEADER_INPUT_FIELD);
            case SMALL -> applyStyleClass(textField, INPUT_FIELD_SMALL);
            default -> applyStyleClass(textField, INPUT_FIELD);
        }
        
        return textField;
    }

    /**
     * Create a styled text area
     */
    public static TextArea createStyledTextArea(TextAreaType type) {
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);
        
        switch (type) {
            case RESULTS -> applyStyleClass(textArea, RESULTS_AREA);
            default -> applyStyleClass(textArea, TEXT_AREA);
        }
        
        return textArea;
    }

    /**
     * Create a styled label
     */
    public static Label createStyledLabel(String text, LabelType type) {
        Label label = new Label(text);
        
        switch (type) {
            case HEADER_TITLE -> applyStyleClass(label, HEADER_TITLE);
            case SECTION_TITLE -> applyStyleClass(label, SECTION_TITLE);
            case FIELD_LABEL -> applyStyleClass(label, FIELD_LABEL);
            case FIELD_LABEL_SMALL -> applyStyleClass(label, FIELD_LABEL_SMALL);
            case STATUS -> applyStyleClass(label, STATUS_TEXT);
            case SUCCESS -> applyStyleClass(label, SUCCESS_TEXT);
            case ERROR -> applyStyleClass(label, ERROR_TEXT);
        }
        
        return label;
    }

    /**
     * Add tooltip to any node
     */
    public static void addTooltip(Node node, String tooltipText) {
        if (node != null && tooltipText != null && !tooltipText.isEmpty()) {
            Tooltip tooltip = new Tooltip(tooltipText);
            tooltip.setShowDelay(TOOLTIP_SHOW_DELAY);
            tooltip.setHideDelay(TOOLTIP_HIDE_DELAY);
            Tooltip.install(node, tooltip);
        }
    }

    /**
     * Update label state (for status messages, etc.)
     */
    public static void updateLabelState(Label label, LabelState state, String text) {
        if (label != null) {
            label.setText(text);
            
            // Clear existing state classes
            removeStyleClass(label, SUCCESS_TEXT);
            removeStyleClass(label, ERROR_TEXT);
            removeStyleClass(label, STATUS_TEXT);
            
            // Apply new state class
            switch (state) {
                case SUCCESS -> applyStyleClass(label, SUCCESS_TEXT);
                case ERROR -> applyStyleClass(label, ERROR_TEXT);
                case NORMAL -> applyStyleClass(label, STATUS_TEXT);
            }
        }
    }

    /**
     * Apply container styling
     */
    public static void styleAsContainer(Region region, ContainerType type) {
        if (region != null) {
            switch (type) {
                case MAIN_BACKGROUND -> applyStyleClass(region, MAIN_BACKGROUND);
                case HEADER -> applyStyleClass(region, HEADER_CONTAINER);
                case SECTION -> applyStyleClass(region, SECTION_CONTAINER);
                case RESULTS -> applyStyleClass(region, RESULTS_CONTAINER);
            }
        }
    }

    // Enum definitions for type safety
    public enum ButtonType {
        SAVE, CANCEL, PROCESS, NAVIGATION, UPDATE, INSERT, DEFAULT
    }

    public enum TextFieldType {
        NORMAL, HEADER, SMALL
    }

    public enum TextAreaType {
        NORMAL, RESULTS
    }

    public enum LabelType {
        HEADER_TITLE, SECTION_TITLE, FIELD_LABEL, FIELD_LABEL_SMALL, STATUS, SUCCESS, ERROR
    }

    public enum LabelState {
        NORMAL, SUCCESS, ERROR
    }

    public enum ContainerType {
        MAIN_BACKGROUND, HEADER, SECTION, RESULTS
    }
}