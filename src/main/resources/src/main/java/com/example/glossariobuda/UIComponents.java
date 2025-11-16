package com.example.glossariobuda;

import javafx.scene.control.*;

/**
 * Parameter object for UI component references.
 * Groups UI components to reduce constructor parameter count.
 */
public class UIComponents {
    public final ComboBox<String> ownerFilter;
    public final TextField searchField;
    public final ListView<DatabaseManager.Term> resultsListView;
    public final Label operationStatusLabel;
    public final Label connectionStatusLabel;
    public final Button clearButton;
    public final Button addTermButton;
    public final Button editButton;
    public final Button deleteButton;
    public final Button exportButton;
    public final Button recentButton;
    public final Button loadXMLButton;
    public final Button loadMoreButton;
    public final DatabaseManager dbManager;

    public UIComponents(ComboBox<String> ownerFilter,
                        TextField searchField,
                        ListView<DatabaseManager.Term> resultsListView,
                        Label operationStatusLabel,
                        Label connectionStatusLabel,
                        Button clearButton,
                        Button addTermButton,
                        Button editButton,
                        Button deleteButton,
                        Button exportButton,
                        Button recentButton,
                        Button loadXMLButton,
                        Button loadMoreButton,
                        DatabaseManager dbManager) {
        this.ownerFilter = ownerFilter;
        this.searchField = searchField;
        this.resultsListView = resultsListView;
        this.operationStatusLabel = operationStatusLabel;
        this.connectionStatusLabel = connectionStatusLabel;
        this.clearButton = clearButton;
        this.addTermButton = addTermButton;
        this.editButton = editButton;
        this.deleteButton = deleteButton;
        this.exportButton = exportButton;
        this.recentButton = recentButton;
        this.loadXMLButton = loadXMLButton;
        this.loadMoreButton = loadMoreButton;
        this.dbManager = dbManager;
    }
}
