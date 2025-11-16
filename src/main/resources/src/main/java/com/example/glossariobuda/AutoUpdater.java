package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AutoUpdater for Glossário BUDA
 * Adapted from MantraCount AutoUpdater
 */
public class AutoUpdater {
    private static boolean manualCheck = false;
    private static final String CURRENT_VERSION = getCurrentVersion();
    private static final String GITHUB_RELEASES_API = "https://glossariobudacompact.netlify.app/.netlify/functions/releases";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Constants
    private static final String VERSION_PROPERTIES_PATH = "/version.properties";
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String DOWNLOADS_FOLDER = "Downloads";
    private static final String EXE_EXTENSION = ".exe";
    private static final String DMG_EXTENSION = ".dmg";
    private static final String PKG_EXTENSION = ".pkg";

    public static void checkForUpdatesManually() {
        System.out.println("🔧 Manual update check initiated");
        manualCheck = true;
        checkForUpdates();
    }

    public static void checkForUpdates() {
        System.out.println("🔢 Current version: " + CURRENT_VERSION);

        // Enable HTTP redirect following globally
        HttpURLConnection.setFollowRedirects(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                System.out.println("🌐 Connecting to GitHub API: " + GITHUB_RELEASES_API);
                HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_RELEASES_API).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setInstanceFollowRedirects(true);  // Enable redirect following
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();
                System.out.println("📡 GitHub API response code: " + responseCode);
                if (responseCode != 200)
                    throw new IOException("HTTP " + responseCode);

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();

                System.out.println("📦 GitHub API response received");
                return sb.toString();
            }
        };

        task.setOnSucceeded(e -> {
            System.out.println("✅ Task succeeded, processing result...");
            String jsonResponse = task.getValue();
            if (jsonResponse != null && !jsonResponse.isEmpty()) {
                processUpdateResponse(jsonResponse);
            }
            manualCheck = false;
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("❌ Update check task failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            ex.printStackTrace();
            if (manualCheck) {
                Platform.runLater(() -> UIUtils.showError(
                        "❌ Connection to Update Failed: " + ex.getMessage(),
                        "❌ Conexão de Atualização Falhou: " + ex.getMessage()
                ));
            } else {
                System.err.println("⚠️ Auto-update check failed: " + ex.getMessage());
            }
            manualCheck = false;
        });

        executor.submit(task);
    }

    private static void processUpdateResponse(String jsonResponse) {
        try {
            // Filter for releases from "compact-version" branch
            String branchRelease = extractBranchRelease(jsonResponse, "compact-version");

            if (branchRelease == null) {
                System.out.println("ℹ️ No releases found for compact-version branch");
                if (manualCheck) {
                    Platform.runLater(() -> UIUtils.showInfo("✔ App is up-to-date", "✔ Aplicativo está atualizado"));
                }
                return;
            }

            // Simple JSON parsing - find first release from compact-version branch
            String latestVersion = extractVersionFromResponse(branchRelease);
            String downloadUrl = extractDownloadUrlFromResponse(branchRelease);
            String releaseUrl = extractReleaseUrlFromResponse(branchRelease);
            String releaseNotes = extractReleaseNotesFromResponse(branchRelease);

            if (latestVersion != null && isNewerVersion(latestVersion, CURRENT_VERSION)) {
                System.out.println("✅ Update available: " + latestVersion + " > " + CURRENT_VERSION);
                Platform.runLater(() -> showUpdateDialog(latestVersion, downloadUrl, releaseUrl, releaseNotes));
            } else {
                System.out.println("ℹ️ No update needed: " + CURRENT_VERSION + " >= " + latestVersion);
                if (manualCheck) {
                    Platform.runLater(() -> UIUtils.showInfo("✔ App is up-to-date", "✔ Aplicativo está atualizado"));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to process update response: " + e.getMessage());
            if (manualCheck) {
                Platform.runLater(() -> UIUtils.showError("❌ Failed to parse update information", "❌ Falha ao analisar informações de atualização"));
            }
        }
    }

    private static void showUpdateDialog(String latestVersion, String downloadUrl, String releaseUrl, String releaseNotes) {
        System.out.println("🚀 Showing update dialog...");
        
        if (latestVersion == null || latestVersion.isEmpty()) {
            System.err.println("❌ No version information found");
            return;
        }

        if (downloadUrl == null) {
            System.err.println("❌ No installer URL found in release assets");
            UIUtils.showError(
                    "❌ No installer found for your platform",
                    "❌ Nenhum instalador encontrado para sua plataforma"
            );
            return;
        }

        Stage stage = createUpdateDialogStage();
        VBox root = createDialogContent(latestVersion, releaseUrl, releaseNotes, downloadUrl, stage);
        
        stage.setScene(new Scene(root, 500, 450));
        stage.show();
    }

    private static Stage createUpdateDialogStage() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Update Available / Atualização Disponível");
        return stage;
    }

    private static VBox createDialogContent(String latestVersion, String releaseUrl, String releaseNotes, String downloadUrl, Stage stage) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label title = new Label(String.format("🔄 A new version (%s) is available!\n🔄 Uma nova versão (%s) está disponível!", latestVersion, latestVersion));
        title.getStyleClass().add("header-title");

        TextArea notes = createNotesArea(releaseNotes != null ? releaseNotes : "No release notes available / Notas de lançamento não disponíveis");
        
        Hyperlink releaseLink = createReleaseLink(releaseUrl);
        
        ProgressBar bar = new ProgressBar(0);
        bar.setVisible(false);
        Label progress = new Label("");
        progress.setVisible(false);

        HBox buttons = createButtons(downloadUrl, bar, progress, stage);
        
        root.getChildren().addAll(title, notes, releaseLink, bar, progress, buttons);
        return root;
    }

    private static Hyperlink createReleaseLink(String releaseUrl) {
        Hyperlink releaseLink = new
                Hyperlink("🔗 View Release Notes / Ver Notas de Lançamento");
        releaseLink.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(URI.create(releaseUrl));
            } catch (IOException ex) {
                UIUtils.showError("❌ Failed to open browser", "❌ Falha ao abrir navegador");
            }
        });
        return releaseLink;
    }

    private static TextArea createNotesArea(String releaseNotes) {
        TextArea notes = new TextArea(releaseNotes);
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefHeight(200);
        notes.getStyleClass().add("text-area");
        return notes;
    }

    private static HBox createButtons(String downloadUrl, ProgressBar bar, Label progress, Stage stage) {
        Button download = UIStyleManager.createStyledButton("💾 Download & Install / Baixar e Instalar", UIStyleManager.ButtonType.PROCESS);
        Button cancel = UIStyleManager.createStyledButton("❌ Cancel / Cancelar", UIStyleManager.ButtonType.CANCEL);

        HBox buttons = new HBox(10, download, cancel);
        buttons.setAlignment(Pos.CENTER);

        download.setOnAction(e -> handleDownloadAction(downloadUrl, bar, progress, download, stage));
        cancel.setOnAction(e -> stage.close());
        
        return buttons;
    }

    private static void handleDownloadAction(String downloadUrl, ProgressBar bar, Label progress, Button download, Stage stage) {
        bar.setVisible(true);
        progress.setVisible(true);
        download.setDisable(true);

        Task<Void> installTask = createInstallTask(downloadUrl);
        
        bar.progressProperty().bind(installTask.progressProperty());
        progress.textProperty().bind(installTask.messageProperty());

        installTask.setOnSucceeded(ev -> stage.close());
        installTask.setOnFailed(ev -> {
            Throwable ex = installTask.getException();
            progress.textProperty().unbind();
            progress.setText("❌ Error: " + ex.getMessage() + "\n❌ Erro: " + ex.getMessage());
            download.setDisable(false);
        });

        executor.submit(installTask);
    }

    private static Task<Void> createInstallTask(String downloadUrl) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("⬇ Downloading installer...\n⬇ Baixando instalador...");
                Path userDownloads = downloadInstaller(downloadUrl);
                Path cleanupScript = createCleanupScript();
                executeCleanupScript(cleanupScript);
                launchInstaller(userDownloads, cleanupScript, this::updateMessage);
                return null;
            }
        };
    }

    private static Path downloadInstaller(String url) throws Exception {
        Path tempDir = Files.createTempDirectory("glossario-update");
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        // Extract filename from query parameters if present (for proxy URLs)
        if (url.contains("filename=")) {
            int filenameIndex = url.indexOf("filename=");
            int ampersandIndex = url.indexOf("&", filenameIndex);
            if (ampersandIndex == -1) {
                fileName = java.net.URLDecoder.decode(url.substring(filenameIndex + 9), "UTF-8");
            } else {
                fileName = java.net.URLDecoder.decode(url.substring(filenameIndex + 9, ampersandIndex), "UTF-8");
            }
        }
        Path tempOutput = tempDir.resolve(fileName);

        // Enable redirect following for download
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);  // Enable redirect following
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tempOutput, StandardCopyOption.REPLACE_EXISTING);
        }

        Path userDownloads = Paths.get(System.getProperty(USER_HOME_PROPERTY), DOWNLOADS_FOLDER, fileName);
        Files.copy(tempOutput, userDownloads, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(tempOutput);

        return userDownloads;
    }

    private static Path createCleanupScript() throws Exception {
        Path tempDir = Files.createTempDirectory("glossario-update");
        boolean isWindows = isWindowsOS();
        Path cleanupScript = tempDir.resolve("cleanup" + (isWindows ? ".bat" : ".sh"));
        
        String scriptContent = isWindows ? createWindowsCleanupScript(cleanupScript) : createUnixCleanupScript();
        Files.writeString(cleanupScript, scriptContent);
        
        if (!isWindows) {
            cleanupScript.toFile().setExecutable(true);
        }
        
        return cleanupScript;
    }

    private static String createWindowsCleanupScript(Path cleanupScript) {
        return "@echo off\n" +
               "timeout /t 5 /nobreak > nul 2>&1\n" +
               "start /min cmd /c \"timeout /t 2 /nobreak > nul 2>&1 & del \\\"" +
               cleanupScript.toString() + "\\\"\"\n";
    }

    private static String createUnixCleanupScript() {
        return "#!/bin/bash\n" +
               "sleep 5\n" +
               "(sleep 2; rm \"$0\") &\n";
    }

    private static void executeCleanupScript(Path cleanupScript) throws Exception {
        if (isWindowsOS()) {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "/min", "/b", cleanupScript.toString()});
        } else {
            Runtime.getRuntime().exec(cleanupScript.toString());
        }
    }

    private static void launchInstaller(Path userDownloads, Path cleanupScript, MessageUpdater messageUpdater) throws Exception {
        messageUpdater.updateMessage("🚀 Opening installer...\n🚀 Abrindo instalador...");
        try {
            Desktop.getDesktop().open(userDownloads.toFile());
            executeCleanupInBackground(cleanupScript);
            messageUpdater.updateMessage("🚀 Installer launched...\n🚀 Instalador lançado...");
            Thread.sleep(1500);
            Platform.exit();
            System.exit(0);
        } catch (Exception ex) {
            messageUpdater.updateMessage("❌ Could not open installer\n❌ Não foi possível abrir o instalador");
            tryOpenFileExplorer(userDownloads);
        }
    }

    @FunctionalInterface
    private interface MessageUpdater {
        void updateMessage(String message);
    }

    private static void executeCleanupInBackground(Path cleanupScript) throws Exception {
        if (isWindowsOS()) {
            Runtime.getRuntime().exec("cmd /c start " + cleanupScript.toString());
        } else {
            Runtime.getRuntime().exec(cleanupScript.toString());
        }
    }

    private static void tryOpenFileExplorer(Path userDownloads) {
        try {
            Runtime.getRuntime().exec(new String[]{"open", "-R", userDownloads.toString()});
        } catch (Exception explorerEx) {
            // Failed to open file explorer, continue silently
        }
    }

    private static boolean isWindowsOS() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    // Simple JSON parsing methods (avoiding org.json dependency)

    /**
     * Extract the first release from a specific branch
     */
    private static String extractBranchRelease(String jsonResponse, String branchName) {
        try {
            // The JSON response is an array of releases
            // Each release has a "target_commitish" field indicating the branch
            int currentIndex = 0;
            int depth = 0;

            while (true) {
                // Find the next release object start
                int releaseStart = jsonResponse.indexOf("{", currentIndex);
                if (releaseStart == -1) break;

                // Find the matching closing brace by counting depth
                depth = 0;
                int i = releaseStart;
                int releaseEnd = -1;

                while (i < jsonResponse.length()) {
                    char c = jsonResponse.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            releaseEnd = i + 1;
                            break;
                        }
                    }
                    i++;
                }

                if (releaseEnd == -1) break;

                String release = jsonResponse.substring(releaseStart, releaseEnd);

                // Check if this release is from the target branch
                int targetCommitishIndex = release.indexOf("\"target_commitish\":");
                if (targetCommitishIndex != -1) {
                    int branchStart = release.indexOf("\"", targetCommitishIndex + 19);
                    int branchEnd = release.indexOf("\"", branchStart + 1);

                    if (branchStart != -1 && branchEnd != -1) {
                        String releaseBranch = release.substring(branchStart + 1, branchEnd);

                        if (branchName.equals(releaseBranch)) {
                            System.out.println("✅ Found release from branch: " + branchName);
                            return release;
                        }
                    }
                }

                // Move to next release
                currentIndex = releaseEnd;
            }

            return null; // No release found for the specified branch
        } catch (Exception e) {
            System.err.println("❌ Error extracting branch release: " + e.getMessage());
            return null;
        }
    }

    private static String extractVersionFromResponse(String jsonResponse) {
        try {
            // Find first "tag_name" in the JSON array
            int tagIndex = jsonResponse.indexOf("\"tag_name\":");
            if (tagIndex == -1) return null;
            
            int startQuote = jsonResponse.indexOf("\"", tagIndex + 11);
            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
            
            if (startQuote == -1 || endQuote == -1) return null;
            
            String tagName = jsonResponse.substring(startQuote + 1, endQuote);
            return tagName.startsWith("v.") ? tagName.replace("v.", "") :
                   tagName.startsWith("v") ? tagName.replace("v", "") : tagName;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractDownloadUrlFromResponse(String jsonResponse) {
        try {
            boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            System.out.println("🖥️ Platform: " + (isWindows ? "Windows" : isMac ? "macOS" : "Other"));

            // Look for assets array and find appropriate download URL
            int assetsIndex = jsonResponse.indexOf("\"assets\":");
            if (assetsIndex == -1) {
                System.err.println("❌ No 'assets' field found in JSON");
                return null;
            }

            String assetsSection = jsonResponse.substring(assetsIndex);
            int assetsEnd = assetsSection.indexOf("]");
            if (assetsEnd == -1) {
                System.err.println("❌ No closing bracket for assets array");
                return null;
            }

            String assets = assetsSection.substring(0, assetsEnd);
            System.out.println("📦 Assets section length: " + assets.length() + " characters");

            // Find download URL for current platform
            String searchExtension = isWindows ? EXE_EXTENSION : isMac ? PKG_EXTENSION : EXE_EXTENSION;
            System.out.println("🔎 Searching for extension: " + searchExtension);

            int nameIndex = assets.indexOf("\"name\":");
            
            while (nameIndex != -1) {
                int nameStart = assets.indexOf("\"", nameIndex + 7);
                int nameEnd = assets.indexOf("\"", nameStart + 1);

                if (nameStart != -1 && nameEnd != -1) {
                    String fileName = assets.substring(nameStart + 1, nameEnd);
                    System.out.println("🔍 Found asset: " + fileName + " (looking for: " + searchExtension + ")");

                    if (fileName.toLowerCase().endsWith(searchExtension)) {
                        // Find corresponding browser_download_url
                        int urlIndex = assets.indexOf("\"browser_download_url\":", nameEnd);
                        if (urlIndex != -1) {
                            int urlStart = assets.indexOf("\"", urlIndex + 23);
                            int urlEnd = assets.indexOf("\"", urlStart + 1);
                            if (urlStart != -1 && urlEnd != -1) {
                                String url = assets.substring(urlStart + 1, urlEnd);
                                System.out.println("✅ Found installer URL: " + url);
                                return url;
                            }
                        }
                    }
                }

                nameIndex = assets.indexOf("\"name\":", nameEnd);
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractReleaseUrlFromResponse(String jsonResponse) {
        try {
            int htmlUrlIndex = jsonResponse.indexOf("\"html_url\":");
            if (htmlUrlIndex == -1) return "https://github.com/TashiRabten/GlossarioBUDA/releases";
            
            int startQuote = jsonResponse.indexOf("\"", htmlUrlIndex + 11);
            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
            
            if (startQuote == -1 || endQuote == -1) return "https://github.com/TashiRabten/GlossarioBUDA/releases";
            
            return jsonResponse.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            return "https://github.com/TashiRabten/GlossarioBUDA/releases";
        }
    }

    private static String extractReleaseNotesFromResponse(String jsonResponse) {
        try {
            int bodyIndex = jsonResponse.indexOf("\"body\":");
            if (bodyIndex == -1) return "No release notes available";

            int startQuote = jsonResponse.indexOf("\"", bodyIndex + 7);
            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);

            // Handle escaped quotes in JSON
            while (endQuote != -1 && jsonResponse.charAt(endQuote - 1) == '\\') {
                endQuote = jsonResponse.indexOf("\"", endQuote + 1);
            }

            if (startQuote == -1 || endQuote == -1) return "No release notes available";

            String notes = jsonResponse.substring(startQuote + 1, endQuote)
                .replace("\\n", "\n")       // Convert escaped newlines
                .replace("\\r", "")         // Remove escaped carriage returns
                .replace("\r", "")          // Remove any actual carriage returns
                .replace("\\\"", "\"")      // Convert escaped quotes
                .replace("\\t", "    ");    // Convert tabs to spaces

            // Convert markdown bullets to Unicode bullets for better display
            notes = notes.replaceAll("(?m)^[*-]\\s+", "• ");        // Start of line bullets
            notes = notes.replaceAll("(?m)^\\s+[*-]\\s+", "  • ");  // Indented bullets

            return notes;
        } catch (Exception e) {
            return "No release notes available";
        }
    }

    private static String getCurrentVersion() {
        try (InputStream in = AutoUpdater.class.getResourceAsStream(VERSION_PROPERTIES_PATH)) {
            if (in == null) {
                System.err.println("❌ version.properties not found in resources.");
                return "0.0.0";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version", "0.0.0").trim();
        } catch (IOException e) {
            System.err.println("❌ Could not load version from properties: " + e.getMessage());
            return "0.0.0";
        }
    }

    private static boolean isNewerVersion(String latest, String current) {
        if (latest == null || latest.isBlank() || current == null || current.isBlank()) {
            System.err.println("⚠️ Version string is blank. Skipping update check.\n⚠️ String de versão está em branco. Pulando verificação de atualização.");
            return false;
        }

        String[] lv = latest.split("\\.");
        String[] cv = current.split("\\.");

        try {
            for (int i = 0; i < Math.max(lv.length, cv.length); i++) {
                int l = i < lv.length ? Integer.parseInt(lv[i].trim()) : 0;
                int c = i < cv.length ? Integer.parseInt(cv[i].trim()) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
        } catch (NumberFormatException e) {
            System.err.println("🚫 Invalid version format: " + latest + " or " + current + "\n🚫 Formato de versão inválido: " + latest + " ou " + current);
            return false;
        }

        return false;
    }

    public static void shutdown() {
        executor.shutdown();
    }
}