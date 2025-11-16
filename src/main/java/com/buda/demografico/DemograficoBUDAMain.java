package com.buda.demografico;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Classe principal da aplicação DemograficoBUDA
 * Programa de coleta de dados demográficos sobre práticas budistas
 */
public class DemograficoBUDAMain extends Application {

    private LoginController loginController;

    @Override
    public void start(Stage stage) throws Exception {
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║     DemograficoBUDA - Pesquisa Budista        ║");
        System.out.println("║   Associação BUDA - Coleta de Dados           ║");
        System.out.println("╚════════════════════════════════════════════════╝");

        // Carregar tela de login
        FXMLLoader fxmlLoader = new FXMLLoader(
            DemograficoBUDAMain.class.getResource("login-view.fxml")
        );

        Scene scene = new Scene(fxmlLoader.load());

        // Adicionar CSS se existir
        try {
            String css = DemograficoBUDAMain.class.getResource("styles.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            System.out.println("Arquivo CSS não encontrado, usando estilos padrão");
        }

        loginController = fxmlLoader.getController();
        loginController.setStage(stage);

        stage.setTitle("DemograficoBUDA - Login");
        stage.setScene(scene);
        stage.setResizable(false);
        UiStyleHelper.applyStageIcon(stage);

        // Handler para fechar recursos ao sair
        stage.setOnCloseRequest(event -> {
            if (loginController != null) {
                loginController.shutdown();
            }
        });

        stage.show();

        AutoUpdater.checkForUpdates();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (loginController != null) {
            loginController.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
