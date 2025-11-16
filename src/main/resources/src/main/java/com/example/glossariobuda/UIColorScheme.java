package com.example.glossariobuda;

public class UIColorScheme {
    // Background colors
    public static final String INPUT_BACKGROUND = "#FFFFFF";         // White for input fields
    public static final String INPUT_HEADER_BG = "#C8DCF0";          // Very light blue for headers
    public static final String RESULTS_BACKGROUND = "#A8C4DC";       // Medium light blue for results
    public static final String MAIN_BACKGROUND = "#88A9CC";          // Your main blue

    //Button Colors
    public static final String SAVE_ACTION_COLOR = "#BFAF7C";
    public static final String CANCEL_ACTION_COLOR = "#D16A5F";
    public static final String PROCESS_ACTION_COLOR = "#BFAF7C";
    public static final String NAVIGATION_COLOR = "#3E7EBE";
    public static final String FEATURE_MISSING_DAYS_COLOR = "#3E7EBE";
    public static final String FEATURE_ALL_MANTRAS_COLOR = "#7F5DA3";
    public static final String FEATURE_SEM_FIZ_COLOR = "#E08232";
    public static final String UPDATE_COLOR = "#8C6D98";
    public static final String UNDO_COLOR = "#3E7EBE";
    public static final String INSERIR_MANTRA_COLOR = "#4CAF50"; // Tara green color

    // Border colors
    public static final String BORDER_DEFAULT = "#B8C8D8";
    public static final String BORDER_FOCUSED = "#5A7A9A";
    public static final String BORDER_ACCENT = "#5A7A9A";

    // Text colors
    public static final String TEXT_PRIMARY = "#2C3E50";
    public static final String TEXT_HEADER = "#34495E";
    public static final String TEXT_PLACEHOLDER = "#7A8B9C";
    public static final String TEXT_SUCCESS = "#27AE60";
    public static final String TEXT_ERROR = "#E74C3C";
    public static final String TEXT_INFO = "#5DADE2";

    // Button states
    public static final String BUTTON_DISABLED = "#D5DDE5";
    public static final String BUTTON_DISABLED_TEXT = "#7A8B9C";

    // Accent colors
    public static final String ACCENT_LIGHT = "#A8B8C8";
    public static final String HOVER_ACCENT = "#7A9ABF";

    // Header Colors
    public static final String HEADER_TITLE_PRIMARY = "#2C3E50";    // Deep charcoal blue - excellent contrast
    public static final String HEADER_TITLE_SECONDARY = "#34495E";  // Slightly lighter charcoal
    public static final String HEADER_SUBTITLE = "#4A5F7A";        // Medium blue-gray for subtitles

    // Text Fill-in Fields (Input backgrounds that work with blue-gray)
    public static final String INPUT_HEADER_ALT_BG = "#F8FAFB";     // Very light blue-white
    public static final String INPUT_PLACEHOLDER_TEXT = "#6B7B8A";  // Medium gray-blue for placeholders

    // Menu Colors (Gray menu options)
    public static final String MENU_BACKGROUND = "#E8EDF2";         // Light gray-blue for menu backgrounds
    public static final String MENU_ITEM_DEFAULT = "#5A6B7A";       // Medium gray-blue for menu items
    public static final String MENU_ITEM_HOVER = "#4A5A69";         // Darker on hover
    public static final String MENU_ITEM_SELECTED = "#FFFFFF";      // White text when selected
    public static final String MENU_SELECTED_BG = "#5A7A9A";        // Darker blue-gray for selected background

    // Accent Colors that work with your blue-gray theme
    public static final String ACCENT_WARM = "#D4A574";             // Warm beige/tan - complementary
    public static final String ACCENT_CORAL = "#E08A7B";            // Soft coral - warm complement
    public static final String ACCENT_CREAM = "#F5F2E8";            // Warm cream for highlights

    // Border Colors for Header Elements
    public static final String HEADER_BORDER_LIGHT = "#C8D4E0";     // Light blue-gray border
    public static final String HEADER_BORDER_MEDIUM = "#A8B8C8";    // Medium border
    public static final String HEADER_BORDER_DARK = "#7A8B9C";      // Darker border for definition

    // Main background
    public static String getMainBackgroundStyle() {
        return "-fx-background-color: " + MAIN_BACKGROUND + ";";
    }

    // Section containers (light background with border)
    public static String getSectionContainerStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 5px; " +
                        "-fx-background-radius: 5px;",
                INPUT_BACKGROUND, BORDER_DEFAULT
        );
    }

    // Section subtitles (smaller, lighter)
    public static String getSectionSubtitleStyle() {
        return String.format(
                "-fx-font-size: 12px; " +
                        "-fx-text-fill: #000000;",
                TEXT_PLACEHOLDER
        );
    }

    public static String getHeaderSubtitleStyle() {
        return String.format(
                "-fx-font-size: 14px; " +
                        "-fx-font-weight: normal; " +
                        "-fx-text-fill: #000000;",
                HEADER_SUBTITLE
        );
    }

    public static String getHeaderInputFieldStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-prompt-text-fill: #606060; " +
                        "-fx-padding: 6px 8px;",
                INPUT_HEADER_BG, HEADER_BORDER_MEDIUM
        );
    }

    public static String getHeaderInputFocusedStyle() {
        return getHeaderInputFieldStyle()
                .replace(HEADER_BORDER_MEDIUM, HEADER_BORDER_DARK)
                .replace("1px", "2px");
    }

    public static String getMenuItemStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-padding: 8px 12px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px;",
                MENU_BACKGROUND
        );
    }

    public static String getMenuItemHoverStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-padding: 8px 12px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px;",
                HEADER_BORDER_MEDIUM
        );
    }

    public static String getMenuItemSelectedStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 8px 12px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px;",
                MENU_SELECTED_BG
        );
    }

    public static String getHeaderTitleStyle() {
        return String.format(
                "-fx-font-size: 20px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #000000;",
                HEADER_TITLE_PRIMARY
        );
    }

    public static String getSectionTitleStyle() {
        return String.format(
                "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #000000;",
                HEADER_TITLE_PRIMARY
        );
    }

    // Header Container Style
    public static String getHeaderContainerStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-padding: 15px; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 0 0 2px 0;",  // Bottom border only
                MAIN_BACKGROUND, HEADER_BORDER_DARK
        );
    }

    public static String getTextAreaStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-text-fill: #000000;",
                RESULTS_BACKGROUND, BORDER_DEFAULT
        );
    }

    public static String getHeaderLabelStyle() {
        return String.format(
                "-fx-font-weight: bold; " +
                        "-fx-text-fill: #000000;",
                TEXT_HEADER
        );
    }

    public static String getScrollPaneStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 4px;",
                INPUT_BACKGROUND, BORDER_ACCENT
        );
    }
    public static String getMismatchedTitleDropdownStyle() {
        return String.format("-fx-background-color: %s; "  +
                "-fx-text-fill: black;" +
                "-fx-border-color: %s; " +
                "-fx-border-width: 2px; ",
                NAVIGATION_COLOR, NAVIGATION_COLOR);}

    public static String getInputFieldStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-control-inner-background: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-prompt-text-fill: #C0C0C0; " +
                        "-fx-padding: 6px 8px;",
                INPUT_BACKGROUND, INPUT_BACKGROUND, BORDER_FOCUSED
        );
    }

    public static String getInputFieldStyleSmall() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-control-inner-background: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-prompt-text-fill: #C0C0C0; " +
                        "-fx-padding: 6px 8px; " +
                        "-fx-font-size: 12px;",
                INPUT_BACKGROUND, INPUT_BACKGROUND, BORDER_FOCUSED
        );
    }

    public static String getComboBoxStyleSmall() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-control-inner-background: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-font-size: 12px; ",
                INPUT_BACKGROUND, INPUT_BACKGROUND, BORDER_FOCUSED
        );
    }
public static String getMismatchedAreaStyle(){
        return String.format(
            "-fx-background: " + RESULTS_BACKGROUND + "; " +
                    "-fx-border-width: 2px; " +
                    "-fx-background-color: " + RESULTS_BACKGROUND + ";" +
                "-fx-border-color: " + BORDER_FOCUSED + ";"

        );}


    public static String getResultsAreaStyle(){
        return String.format("-fx-background-color: %s; " +
            "-fx-control-inner-background: %s; " +
            "-fx-text-fill: #000000; " +
            "-fx-font-style: normal; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 2px; " +
            "-fx-border-radius: 3px; " +
            "-fx-background-radius: 3px;",
                RESULTS_BACKGROUND, RESULTS_BACKGROUND, BORDER_FOCUSED

        );}

    public static String getResultsContainerStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-control-inner-background: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px;",
                RESULTS_BACKGROUND, RESULTS_BACKGROUND, BORDER_DEFAULT
        );
    }

    public static String getInputFieldFocusedStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-control-inner-background: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-prompt-text-fill: #C0C0C0; " +
                        "-fx-padding: 5px 7px;",
                INPUT_BACKGROUND, INPUT_BACKGROUND, BORDER_FOCUSED
        );
    }

    public static String getDatePickerStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-control-inner-background: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-prompt-text-fill: #C0C0C0;",
                INPUT_BACKGROUND, INPUT_BACKGROUND, BORDER_FOCUSED
        );
    }

    public static String getDatePickerFocusedStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-control-inner-background: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-prompt-text-fill: #C0C0C0;",
                INPUT_BACKGROUND, INPUT_BACKGROUND, BORDER_FOCUSED
        );
    }

    public static String getFieldLabelStyle() {
        return String.format(
                "-fx-font-weight: bold; " +
                        "-fx-text-fill: #000000;"
        );
    }

    public static String getFieldLabelStyleSmall() {
        return String.format(
                "-fx-font-weight: bold; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-font-size: 12px;"
        );
    }

    public static String getPlaceholderLabelStyle() {
        return String.format(
                "-fx-text-fill: #000000; " +
                        "-fx-font-style: normal;"
        );
    }

    // Badge and Label Styles
    public static String getTypeBadgeStyle() {
        return String.format(
                "-fx-background-color: #E3F2FD; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #1565C0;"
        );
    }

    public static String getInfoBadgeStyle() {
        return String.format(
                "-fx-background-color: #FFE0B2; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-font-size: 11px; " +
                        "-fx-text-fill: #E65100;"
        );
    }

    public static String getSuccessLabelStyle() {
        return String.format(
                "-fx-text-fill: %s; " +
                        "-fx-font-style: normal;",
                TEXT_SUCCESS
        );
    }

    // Interactive Element Styles
    public static String getSearchHighlightStyle() {
        return String.format(
                "-fx-background-color: #FFFF99; " +
                        "-fx-border-color: #FF6B6B; " +
                        "-fx-border-width: 2px;"
        );
    }

    public static String getSearchUnhighlightStyle() {
        return String.format(
                "-fx-background-color: white; " +
                        "-fx-border-color: %s;",
                NAVIGATION_COLOR
        );
    }

    public static String getHoverEffectStyle() {
        return String.format(
                "-fx-background-color: #BBDEFB; " +
                        "-fx-background-radius: 10px;"
        );
    }

    public static String getHoverEffectExitStyle() {
        return String.format(
                "-fx-background-color: #E3F2FD; " +
                        "-fx-background-radius: 10px;"
        );
    }

    public static String getElementHighlightStyle() {
        return "-fx-background-color: #FFFF99;";
    }

    public static String getClearHighlightStyle() {
        return "";
    }

    // Text State Styles
    public static String getItalicGrayTextStyle() {
        return String.format(
                "-fx-text-fill: %s; " +
                        "-fx-font-style: italic;",
                TEXT_PLACEHOLDER
        );
    }

    public static String getBoldTextStyle() {
        return "-fx-font-weight: bold;";
    }

    public static String getBoldBlackTextStyle() {
        return "-fx-font-weight: bold; -fx-text-fill: #000000;";
    }

    public static String getDisabledTextStyle() {
        return String.format(
                "-fx-text-fill: %s;",
                BUTTON_DISABLED_TEXT
        );
    }

    public static String getCheckboxStyle() {
        return "-fx-text-fill: #000000;";
    }

    // Container Enhancement Styles
    public static String getSummaryContainerStyle() {
        return String.format(
                "%s-fx-alignment: center; " +
                        "-fx-text-alignment: center; " +
                        "-fx-padding: 10px;",
                getResultsAreaStyle()
        );
    }

    public static String getCenteredContainerStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-alignment: center; " +
                        "-fx-padding: 10px;",
                RESULTS_BACKGROUND, RESULTS_BACKGROUND
        );
    }

    public static String getPaddedContainerStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-padding: 10px;",
                RESULTS_BACKGROUND, RESULTS_BACKGROUND
        );
    }

    public static String getResultsHeaderStyle() {
        return String.format(
                "-fx-font-weight: bold; " +
                        "-fx-font-size: 14px; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-padding: 10px;"
        );
    }

    public static String getEditableFieldStyle() {
        return String.format(
                "-fx-background-color: white; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-color: %s;",
                NAVIGATION_COLOR
        );
    }

    public static String getTitleRegionStyle() {
        return String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: white;",
                NAVIGATION_COLOR
        );
    }

    public static String getTransparentBorderStyle() {
        return "-fx-text-fill: #000000; -fx-border-color: transparent;";
    }
}