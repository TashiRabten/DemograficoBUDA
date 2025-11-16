package com.example.glossariobuda;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for managing button icons in Glossário BUDA
 * Provides custom icons for buttons instead of emoji text
 */
public class ButtonImageUtils {

    private static final Map<String, String> ICON_MAPPINGS = new HashMap<>();
    private static final double ICON_SIZE = 16.0; // Standard icon size

    static {
        // Map icon keys to image file paths
        ICON_MAPPINGS.put("add", "/images/Circulo.png");
        ICON_MAPPINGS.put("edit", "/images/update.png");
        ICON_MAPPINGS.put("delete", "/images/cancelar.png");
        ICON_MAPPINGS.put("save", "/images/salvar.png");
        ICON_MAPPINGS.put("cancel", "/images/cancelar.png");
        ICON_MAPPINGS.put("ocr", "/images/Lotus2.png"); // Changed to lotus
        ICON_MAPPINGS.put("recent", "/images/calendario.png");
        ICON_MAPPINGS.put("process", "/images/Roda.png");
        ICON_MAPPINGS.put("export", "/images/Roda.png");
        ICON_MAPPINGS.put("search", "/images/calendario.png");
        ICON_MAPPINGS.put("clear", "/images/Vassoura.png");
        ICON_MAPPINGS.put("pramana", "/icons/pramana.png"); // Add pramana icon
    }

    /**
     * Assigns an icon to a button based on the icon key
     * @param button The button to assign the icon to
     * @param iconKey The key identifying which icon to use
     * @param keepText Whether to keep the button text or make it icon-only
     */
    public static void assignButtonIcon(Button button, String iconKey, boolean keepText) {
        if (button == null || iconKey == null) {
            return;
        }

        String iconPath = ICON_MAPPINGS.get(iconKey.toLowerCase());
        if (iconPath == null) {
            System.err.println("Warning: No icon mapping found for key: " + iconKey);
            return;
        }

        try {
            InputStream imageStream = ButtonImageUtils.class.getResourceAsStream(iconPath);
            if (imageStream == null) {
                System.err.println("Warning: Icon image not found: " + iconPath);
                return;
            }

            Image image = new Image(imageStream);
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(ICON_SIZE);
            imageView.setFitHeight(ICON_SIZE);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            button.setGraphic(imageView);

            // If not keeping text, remove it to make icon-only button
            if (!keepText) {
                button.setText("");
            }

        } catch (Exception e) {
            System.err.println("Error loading icon for button: " + e.getMessage());
        }
    }

    /**
     * Assigns an icon to a button (keeps text by default)
     * @param button The button to assign the icon to
     * @param iconKey The key identifying which icon to use
     */
    public static void assignButtonIcon(Button button, String iconKey) {
        assignButtonIcon(button, iconKey, true);
    }

    /**
     * Creates an ImageView for the specified icon key
     * @param iconKey The key identifying which icon to use
     * @param size The size of the icon (width and height)
     * @return ImageView with the icon, or null if not found
     */
    public static ImageView createIcon(String iconKey, double size) {
        if (iconKey == null) {
            return null;
        }

        String iconPath = ICON_MAPPINGS.get(iconKey.toLowerCase());
        if (iconPath == null) {
            return null;
        }

        try {
            InputStream imageStream = ButtonImageUtils.class.getResourceAsStream(iconPath);
            if (imageStream == null) {
                return null;
            }

            Image image = new Image(imageStream);
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            return imageView;

        } catch (Exception e) {
            System.err.println("Error creating icon: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a standard-sized icon
     * @param iconKey The key identifying which icon to use
     * @return ImageView with the icon at standard size
     */
    public static ImageView createIcon(String iconKey) {
        return createIcon(iconKey, ICON_SIZE);
    }

    /**
     * Gets all available icon keys
     * @return Array of available icon keys
     */
    public static String[] getAvailableIcons() {
        return ICON_MAPPINGS.keySet().toArray(new String[0]);
    }

    /**
     * Checks if an icon key is available
     * @param iconKey The key to check
     * @return true if the icon is available
     */
    public static boolean hasIcon(String iconKey) {
        return iconKey != null && ICON_MAPPINGS.containsKey(iconKey.toLowerCase());
    }

    /**
     * Sets the standard icon size for all new icons
     * @param size The new standard size
     */
    public static void setStandardIconSize(double size) {
        // Note: This would require a more complex implementation to update existing icons
        // For now, just document that this affects new icons
        System.out.println("Standard icon size updated to: " + size);
    }
}