package com.buda.demografico;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilitário central para estilização compartilhada (ícones e temas)
 */
public final class UiStyleHelper {
    private static final String BUTTON_CONFIG = "/images/button-config.properties";
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Properties BUTTON_ICONS = new Properties();
    private static final Image PRAMANA_ICON;

    static {
        try (InputStream input = UiStyleHelper.class.getResourceAsStream(BUTTON_CONFIG)) {
            if (input != null) {
                BUTTON_ICONS.load(input);
            } else {
                System.err.println("[UiStyleHelper] button-config.properties não encontrado");
            }
        } catch (IOException e) {
            System.err.println("[UiStyleHelper] Erro ao carregar configuração de botões: " + e.getMessage());
        }

        PRAMANA_ICON = loadImage("/icons/pramana.png");
    }

    private UiStyleHelper() {
    }

    public static void applyStageIcon(Stage stage) {
        if (stage == null || PRAMANA_ICON == null) {
            return;
        }
        if (!stage.getIcons().contains(PRAMANA_ICON)) {
            stage.getIcons().add(PRAMANA_ICON);
        }
    }

    public static void applyButtonIcon(Button button, String key) {
        applyButtonIcon(button, key, 18);
    }

    public static void applyButtonIcon(Button button, String key, double size) {
        if (button == null || key == null) {
            return;
        }
        String path = BUTTON_ICONS.getProperty(key);
        if (path == null) {
            path = BUTTON_ICONS.getProperty("default");
        }
        if (path == null) {
            return;
        }

        Image image = loadImage(path);
        if (image == null) {
            return;
        }

        ImageView icon = new ImageView(image);
        icon.setFitWidth(size);
        icon.setFitHeight(size);
        icon.setPreserveRatio(true);

        button.setGraphic(icon);
        button.setGraphicTextGap(8);
        button.setContentDisplay(ContentDisplay.LEFT);
    }

    private static Image loadImage(String path) {
        if (path == null) {
            return null;
        }
        Image cached = IMAGE_CACHE.get(path);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = UiStyleHelper.class.getResourceAsStream(path)) {
            if (stream != null) {
                Image image = new Image(stream);
                IMAGE_CACHE.put(path, image);
                return image;
            }
        } catch (IOException e) {
            System.err.println("[UiStyleHelper] Erro ao carregar imagem " + path + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[UiStyleHelper] Erro inesperado ao carregar imagem " + path + ": " + e.getMessage());
        }
        return null;
    }
}
