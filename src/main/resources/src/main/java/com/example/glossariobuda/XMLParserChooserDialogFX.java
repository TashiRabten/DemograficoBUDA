package com.example.glossariobuda;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;

/**
 * JavaFX dialog for choosing which XML parser to use for importing glossary files
 */
public class XMLParserChooserDialogFX {

    private FormatParser selectedParser = null;

    /**
     * Show parser selection dialog and return chosen parser
     */
    public static FormatParser chooseParser(Window owner, File xmlFile) {
        XMLParserChooserDialogFX dialog = new XMLParserChooserDialogFX();
        return dialog.showDialog(owner, xmlFile);
    }

    private FormatParser showDialog(Window owner, File xmlFile) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Choose Import Format");

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // Header
        Label titleLabel = new Label("Select the format of your file:");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label fileLabel = new Label("File: " + xmlFile.getName());
        fileLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        VBox header = new VBox(5, titleLabel, fileLabel);

        // Parser options
        VBox options = new VBox(15);

        // Option 1: Buddhist Glossary
        VBox buddhist = createParserOption(
                "Buddhist Glossary (84000 Format)",
                "Use this for XML files from 84000.co or similar Buddhist glossaries",
                "• Has <term> elements with <tibetan>, <wylie>, <sanskrit>, <translation>\n" +
                        "• Contains <ref> elements with Toh numbers and titles",
                new XMLFormatParser(),
                stage
        );

        // Option 2: Monier-Williams
        VBox monier = createParserOption(
                "Monier-Williams Sanskrit Dictionary (MW72)",
                "Use this for Monier-Williams dictionary XML from Cologne Digital Sanskrit Lexicon",
                "• Has <H1> entries with <key1> in SLP1 transliteration\n" +
                        "• Automatically converts SLP1 (aMSu) to IAST (aṃśu)",
                new MW72Parser(),
                stage
        );

        // Option 3: Steinert HOTL
        VBox steinert = createParserOption(
                "Heart of Tibetan Language (Steinert/Oertle XML)",
                "Use this for Heart of Tibetan Language XML files by Franziska Oertle",
                "• Has <entries>/<entry>/<field name=\"...\"> structure\n" +
                        "• Contains Tibetan script, Wylie, English, examples, morphemes\n" +
                        "• Files: hotl1.xml, hotl2.xml, hotl3.xml",
                new SteinertXMLParser(),
                stage
        );

        // Option 4: Tibetan Dict Plain Text
        VBox tibetan = createParserOption(
                "Tibetan Dictionary (Plain Text/TSV Format)",
                "Use this for pipe-delimited Tibetan dictionaries (Steinert collection, HOTL plain text)",
                "• Plain text with PIPE (|) separators\n" +
                        "• Wylie → Tibetan Unicode conversion\n" +
                        "• Audio links: [sound:...] → clickable buttons\n" +
                        "• Files: 67-hotl1, 01-Hopkins2015, *.txt, no extension",
                new TibetanDictParser(),
                stage
        );

        options.getChildren().addAll(buddhist, monier, steinert, tibetan);

        // Cancel button
        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelButton.setOnAction(e -> stage.close());

        HBox buttonBar = new HBox(cancelButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(header, new Separator(), options, buttonBar);

        Scene scene = new Scene(root, 650, 750);  // Increased height for 4 options
        stage.setScene(scene);
        stage.showAndWait();

        return selectedParser;
    }

    private VBox createParserOption(String title, String description, String features,
                                    FormatParser parser, Stage stage) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-width: 1; " +
                "-fx-border-radius: 5; -fx-background-radius: 5;");

        // Title
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        // Description
        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 11px;");

        // Features
        Label featuresLabel = new Label(features);
        featuresLabel.setWrapText(true);
        featuresLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 10px;");

        // Button
        Button selectButton = new Button("Use This Format");
        selectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        selectButton.setOnAction(e -> {
            selectedParser = parser;
            stage.close();
        });

        HBox buttonBox = new HBox(selectButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(titleLabel, descLabel, featuresLabel, buttonBox);

        // Hover effect
        box.setOnMouseEntered(e ->
                box.setStyle("-fx-background-color: white; -fx-border-color: #4682B4; -fx-border-width: 2; " +
                        "-fx-border-radius: 5; -fx-background-radius: 5;")
        );
        box.setOnMouseExited(e ->
                box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-width: 1; " +
                        "-fx-border-radius: 5; -fx-background-radius: 5;")
        );

        return box;
    }
}