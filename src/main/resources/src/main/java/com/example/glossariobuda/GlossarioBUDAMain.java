package com.example.glossariobuda;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class GlossarioBUDAMain extends Application {
    private GlossarioController controller;
    private DatabaseManager dbManager;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Cria a tela de inicialização (splash screen)
        Stage splashStage = createSplashScreen();
        splashStage.show();

        // Obtém os elementos da tela de inicialização para atualizações
        VBox splashLayout = (VBox) splashStage.getScene().getRoot();
        Label statusLabel = (Label) splashLayout.getChildren().get(1);
        ProgressBar progressBar = (ProgressBar) splashLayout.getChildren().get(2);

        // Inicializa o banco de dados em segundo plano
        Thread initThread = new Thread(() -> {
            try {
                dbManager = new DatabaseManager();

                // Define o callback de progresso
                dbManager.setInitializationCallback(new SyncManager.InitializationCallback() {
                    @Override
                    public void onProgress(String message, int current, int total) {
                        Platform.runLater(() -> {
                            statusLabel.setText(message);
                            if (total > 0) {
                                progressBar.setProgress((double) current / total);
                            }
                        });
                    }

                    @Override
                    public void onComplete() {
                        Platform.runLater(() -> {
                            try {
                                loadMainUI(primaryStage, splashStage);
                            } catch (Exception e) {
                                e.printStackTrace();
                                splashStage.close();
                                statusLabel.setText("Falha ao carregar a interface: " + e.getMessage());
                            }
                        });
                    }
                });

                // Inicializa o banco de dados (gera hashes, se necessário)
                dbManager.initializeDatabase();

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("Falha na inicialização: " + e.getMessage());
                });
            }
        });

        initThread.setDaemon(false);
        initThread.start();
    }

    /**
     * Cria a tela de inicialização
     */
    private Stage createSplashScreen() {
        Stage splashStage = new Stage();
        splashStage.initStyle(StageStyle.UNDECORATED);

        VBox splashLayout = new VBox(20);
        splashLayout.setAlignment(Pos.CENTER);
        splashLayout.setStyle("-fx-background-color: white; -fx-padding: 40; -fx-border-color: #2196F3; -fx-border-width: 2;");

        Label titleLabel = new Label("Glossário BUDA");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");

        Label statusLabel = new Label("Inicializando base de dados...");
        statusLabel.setStyle("-fx-font-size: 14px;");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        splashLayout.getChildren().addAll(titleLabel, statusLabel, progressBar);

        Scene splashScene = new Scene(splashLayout, 400, 200);
        splashStage.setScene(splashScene);

        // Define o ícone
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icons/pramana.png"));
            splashStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Aviso: não foi possível carregar o ícone da tela inicial: " + e.getMessage());
        }

        return splashStage;
    }

    /**
     * Carrega a interface principal
     */
    private void loadMainUI(Stage primaryStage, Stage splashStage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("glossario-main.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 780, 600);

        controller = fxmlLoader.getController();
        controller.setDatabaseManager(dbManager);

        // Configure network monitor callback for connection restoration
        if (controller.getNetworkMonitor() != null && dbManager.getSyncManager() != null) {
            controller.getNetworkMonitor().setOnlineCallback(() -> {
                System.out.println("[App] 🌐 Conexão restaurada - verificando atualizações...");
                handleConnectionRestored();
            });
        }

        // Define o ícone do aplicativo
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icons/pramana.png"));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Aviso: não foi possível carregar o ícone do aplicativo: " + e.getMessage());
        }

        primaryStage.setTitle("Glossário BUDA - Sistema de Terminologia Budista");
        primaryStage.setScene(scene);

        // Define o comportamento ao fechar a janela
        primaryStage.setOnCloseRequest(e -> {
            shutdown();
        });

        // Fecha a tela de inicialização e mostra a janela principal
        splashStage.close();
        primaryStage.show();

        // Verifica atualizações na inicialização (em segundo plano)
        AutoUpdater.checkForUpdates();

        // CRITICAL: Process offline queue on startup if we're online
        // This handles the case where queue has pending operations from previous session
        processQueueOnStartup();
    }

    /**
     * Process offline queue on app startup if we're online and queue has pending operations.
     * This ensures the queue doesn't sit full forever if user never goes offline.
     */
    private void processQueueOnStartup() {
        if (dbManager == null || dbManager.getSyncManager() == null) {
            return;
        }

        new Thread(() -> {
            try {
                // Small delay to let network monitor initialize
                Thread.sleep(2000);

                // Check if we're online and have pending operations
                NetworkStatusMonitor networkMonitor = controller != null ? controller.getNetworkMonitor() : null;
                boolean isOnline = networkMonitor != null && networkMonitor.isOnline();
                int pendingCount = dbManager.getSyncManager().getOfflineQueue().getPendingCount();

                if (isOnline && pendingCount > 0) {
                    System.out.println("[App] ═══════════════════════════════════");
                    System.out.println("[App] 📤 PROCESSING QUEUE ON STARTUP");
                    System.out.println("[App] Found " + pendingCount + " pending operations");
                    System.out.println("[App] ═══════════════════════════════════");

                    int processed = dbManager.getSyncManager().processOfflineQueue();

                    System.out.println("[App] ═══════════════════════════════════");
                    System.out.println("[App] ✅ Startup queue processing complete: " + processed + " operations");
                    System.out.println("[App] ═══════════════════════════════════");

                    // Refresh UI if needed
                    if (processed > 0 && controller != null) {
                        Platform.runLater(() -> controller.refreshSearchIfNeeded());
                    }
                } else if (pendingCount > 0) {
                    System.out.println("[App] ⚠️ Queue has " + pendingCount + " pending operations but we're offline");
                    System.out.println("[App] Will process when connection is restored");
                }
            } catch (Exception e) {
                System.err.println("[App] Error processing queue on startup: " + e.getMessage());
                e.printStackTrace();
            }
        }, "QueueProcessor-Startup").start();
    }

    private void handleConnectionRestored() {
        if (dbManager == null || dbManager.getSyncManager() == null) {
            System.err.println("[App] DatabaseManager não inicializado!");
            return;
        }

        // Run in background thread
        Thread reconnectionThread = new Thread(() -> {
            try {
                System.out.println("[App] ═══════════════════════════════════");
                System.out.println("[App] 🔄 PROCESSANDO RECONEXÃO");
                System.out.println("[App] ═══════════════════════════════════");

                // CHECK: If reset is already in progress, skip
                if (dbManager.getSyncManager().isResetInProgress()) {
                    System.out.println("[App] ⚠️ Reset já em andamento - ignorando callback de reconexão");
                    System.out.println("[App] ═══════════════════════════════════");
                    return;
                }

                // STEP 1: Check for glossary reset
                System.out.println("[App] Step 1: Verificando reset no servidor...");
                boolean resetOccurred = dbManager.getSyncManager().checkAndHandleGlossaryReset();

                if (resetOccurred) {
                    System.out.println("[App] ⚠️ Reset foi executado e fila processada");
                    System.out.println("[App] ═══════════════════════════════════");

                    // Refresh UI if needed
                    Platform.runLater(() -> {
                        if (controller != null) {
                            controller.refreshSearchIfNeeded();
                        }
                    });
                    return;
                }

                // STEP 2: Process offline queue (only if no reset)
                System.out.println("[App] Step 2: Processando fila offline...");

                // ADICIONE ISTO AQUI:
                int syncedCount = dbManager.getSyncManager().processOfflineQueue();
                System.out.println("[App] ✅ Fila processada: " + syncedCount + " operações sincronizadas");

                // STEP 3: Refresh UI
                System.out.println("[App] Step 3: Atualizando interface...");
                Platform.runLater(() -> {
                    if (controller != null) {
                        controller.refreshSearchIfNeeded();
                    }
                });

                System.out.println("[App] ═══════════════════════════════════");
                System.out.println("[App] ✅ RECONEXÃO PROCESSADA COM SUCESSO");
                System.out.println("[App] ═══════════════════════════════════");

            } catch (Exception e) {
                System.err.println("[App] ❌ Erro ao processar reconexão: " + e.getMessage());
                e.printStackTrace();
                System.out.println("[App] ═══════════════════════════════════");
            }
        });

        reconnectionThread.setDaemon(true);
        reconnectionThread.start();
    }


    /**
     * Encerra tudo de forma segura
     */
    private void shutdown() {
        System.out.println("[App] Encerrando...");

        try {
            // Limpa o controlador
            if (controller != null) {
                controller.cleanup();
            }

            // Encerra o gerenciador do banco de dados (fecha o SyncManager e a conexão)
            if (dbManager != null) {
                dbManager.shutdown();
            }

            // Encerra o executor do auto-updater
            AutoUpdater.shutdown();

        } catch (Exception ex) {
            System.err.println("[App] Erro ao encerrar: " + ex.getMessage());
            ex.printStackTrace();
        }

        Platform.exit();
        System.exit(0);
    }

    @Override
    public void stop() throws Exception {
        System.out.println("[App] Método stop() chamado");
        shutdown();
        super.stop();
    }

    public static void main(String[] args) {
        if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            printUsage();
            System.exit(0);
        }

        launch(args);
    }

    /**
     * Exibe as informações de uso
     */
    private static void printUsage() {
        System.out.println("Glossário BUDA - Sistema de Terminologia Budista");
        System.out.println();
        System.out.println("Uso: java -jar GlossarioBUDA.jar");
        System.out.println();
        System.out.println("Um aplicativo de glossário budista com:");
        System.out.println("  - Gerenciamento multilíngue de termos (Tibetano, Sânscrito, Pali, Inglês, Português)");
        System.out.println("  - Suporte a entrada com teclado tibetano");
        System.out.println("  - Importação de glossário em XML");
        System.out.println("  - Exportação de termos via WhatsApp");
        System.out.println("  - Modo compacto sempre visível (always-on-top)");
    }
}