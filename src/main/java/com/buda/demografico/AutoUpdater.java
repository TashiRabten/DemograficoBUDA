package com.buda.demografico;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Responsável por checar atualizações publicadas nas releases do GitHub.
 * Inspirado no AutoUpdater do GlossarioBUDA, porém simplificado para o DemograficoBUDA.
 */
public final class AutoUpdater {

    private static final String VERSION_FILE = "/version.properties";
    private static final String VERSION_KEY = "app.version";
    private static final String GITHUB_RELEASE_API = "https://api.github.com/repos/TashiRabten/DemograficoBUDA/releases/latest";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final String CURRENT_VERSION = carregarVersaoLocal();

    private AutoUpdater() {
    }

    public static void checkForUpdates() {
        checkForUpdates(false);
    }

    public static void checkForUpdatesManually() {
        checkForUpdates(true);
    }

    private static void checkForUpdates(boolean manual) {
        Task<ReleaseInfo> task = new Task<>() {
            @Override
            protected ReleaseInfo call() throws Exception {
                return buscarUltimaRelease();
            }
        };

        task.setOnSucceeded(evt -> {
            ReleaseInfo info = task.getValue();
            if (info == null) {
                if (manual) {
                    Platform.runLater(() -> mostrarAlerta("Aplicativo atualizado",
                        "Você já está utilizando a versão mais recente.",
                        null, null));
                }
                return;
            }

            if (isNovaVersao(info.version(), CURRENT_VERSION)) {
                Platform.runLater(() -> mostrarDialogoAtualizacao(info));
            } else if (manual) {
                Platform.runLater(() -> mostrarAlerta("Aplicativo atualizado",
                    "Você já está utilizando a versão mais recente.",
                    info.releaseUrl(), null));
            }
        });

        task.setOnFailed(evt -> {
            if (manual) {
                Throwable ex = task.getException();
                Platform.runLater(() -> mostrarAlerta("Falha ao verificar atualização",
                    ex != null ? ex.getMessage() : "Erro desconhecido",
                    null, null));
            } else {
                System.err.println("[AutoUpdater] Falha ao consultar atualizações: " + evt.getSource().getException());
            }
        });

        Thread thread = new Thread(task, "auto-update-checker");
        thread.setDaemon(true);
        thread.start();
    }

    private static ReleaseInfo buscarUltimaRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GITHUB_RELEASE_API))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DemograficoBUDA-Updater")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            System.out.println("[AutoUpdater] Nenhuma release encontrada no repositório.");
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub API retornou HTTP " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String tagName = extrairTexto(json, "tag_name");
        String releaseUrl = extrairTexto(json, "html_url");
        String notes = extrairTexto(json, "body");
        String assetUrl = extrairPrimeiroAsset(json);

        if (tagName == null || tagName.isBlank()) {
            return null;
        }
        return new ReleaseInfo(tagName.trim(), releaseUrl, assetUrl, notes);
    }

    private static String extrairTexto(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static String extrairPrimeiroAsset(JsonObject json) {
        JsonArray assets = json.getAsJsonArray("assets");
        if (assets == null || assets.isEmpty()) {
            return null;
        }
        JsonObject asset = assets.get(0).getAsJsonObject();
        return extrairTexto(asset, "browser_download_url");
    }

    private static void mostrarDialogoAtualizacao(ReleaseInfo info) {
        String titulo = "Nova versão disponível";
        String mensagem = String.format("Uma nova versão (%s) está disponível. Você está usando %s.",
            info.version(), CURRENT_VERSION);
        mostrarAlerta(titulo, mensagem, info.releaseUrl(), info.notes());
    }

    private static void mostrarAlerta(String titulo, String mensagem, String link, String notas) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Atualizações do DemograficoBUDA");
        alert.setHeaderText(titulo);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        TextArea body = new TextArea(mensagem + (notas != null && !notas.isBlank() ? "\n\nNotas da versão:\n" + notas : ""));
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(10);
        VBox.setVgrow(body, Priority.ALWAYS);
        content.getChildren().add(body);

        if (link != null && !link.isBlank()) {
            Hyperlink hyperlink = new Hyperlink("Abrir página da release");
            hyperlink.setOnAction(e -> abrirLink(link));
            content.getChildren().add(hyperlink);
        }

        alert.getDialogPane().setContent(content);

        if (link != null) {
            ButtonType abrirBtn = new ButtonType("Abrir no navegador");
            alert.getButtonTypes().add(abrirBtn);
            Button abrir = (Button) alert.getDialogPane().lookupButton(abrirBtn);
            if (abrir != null) {
                abrir.setOnAction(e -> abrirLink(link));
            }
        }

        alert.show();
    }

    private static void abrirLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            System.err.println("[AutoUpdater] Não foi possível abrir o link: " + e.getMessage());
        }
    }

    private static boolean isNovaVersao(String remota, String local) {
        if (remota == null || local == null) {
            return false;
        }
        String cleanRemote = normalizarSemVer(remota);
        String cleanLocal = normalizarSemVer(local);

        String[] remoteParts = cleanRemote.split("\\.");
        String[] localParts = cleanLocal.split("\\.");
        int max = Math.max(remoteParts.length, localParts.length);
        for (int i = 0; i < max; i++) {
            int remoteVal = i < remoteParts.length ? parseInt(remoteParts[i]) : 0;
            int localVal = i < localParts.length ? parseInt(localParts[i]) : 0;
            if (remoteVal != localVal) {
                return remoteVal > localVal;
            }
        }
        return false;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalizarSemVer(String value) {
        return value.replaceFirst("^[vV]", "").trim();
    }

    private static String carregarVersaoLocal() {
        try (InputStream in = AutoUpdater.class.getResourceAsStream(VERSION_FILE)) {
            if (in == null) {
                return "0.0.0";
            }
            Properties props = new Properties();
            props.load(in);
            return Optional.ofNullable(props.getProperty(VERSION_KEY))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse("0.0.0");
        } catch (IOException e) {
            System.err.println("[AutoUpdater] Não foi possível ler a versão local: " + e.getMessage());
            return "0.0.0";
        }
    }

    private record ReleaseInfo(String version, String releaseUrl, String downloadUrl, String notes) {
    }
}
