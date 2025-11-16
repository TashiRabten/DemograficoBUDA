package com.example.glossariobuda;

import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class to create editable language combo boxes with default options
 */
public class LanguageComboBoxHelper {

    // Default main languages
    private static final List<String> DEFAULT_SOURCE_LANGUAGES = Arrays.asList(
        "Sanskrit", "English", "Tibetan", "Pali", "Chinese", "Portuguese", "Spanish"
    );

    private static final List<String> DEFAULT_TARGET_LANGUAGES = Arrays.asList(
        "Tibetan", "English", "Portuguese", "Spanish", "Sanskrit", "Pali", "Chinese"
    );

    /**
     * Create an editable source language combo box
     * Users can select from defaults or type custom languages directly
     */
    public static HBox createSourceLanguageComboBox(ComboBox<String> comboBox, String defaultValue) {
        comboBox.getItems().addAll(DEFAULT_SOURCE_LANGUAGES);
        comboBox.setValue(defaultValue);
        comboBox.setEditable(true);
        comboBox.setPromptText("Selecione ou digite um idioma");

        HBox hbox = new HBox(5);
        hbox.getChildren().add(comboBox);

        return hbox;
    }

    /**
     * Create an editable target language combo box
     * Users can select from defaults or type custom languages directly
     */
    public static HBox createTargetLanguageComboBox(ComboBox<String> comboBox, String defaultValue) {
        comboBox.getItems().addAll(DEFAULT_TARGET_LANGUAGES);
        comboBox.setValue(defaultValue);
        comboBox.setEditable(true);
        comboBox.setPromptText("Selecione ou digite um idioma");

        HBox hbox = new HBox(5);
        hbox.getChildren().add(comboBox);

        return hbox;
    }
}
