package com.buda.demografico;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * Controller para a tela de login
 */
public class LoginController implements Initializable {

    @FXML private TextField nomeUsuarioField;
    @FXML private TextField nomeCompletoField;
    @FXML private TextField emailField;
    @FXML private VBox novoUsuarioBox;
    @FXML private VBox emailBox;
    @FXML private Button loginButton;
    @FXML private Button novoUsuarioButton;
    @FXML private Label mensagemLabel;
    @FXML private Label ultimoUsuarioLabel;

    private AuthManager authManager;
    private boolean modoNovoUsuario = false;
    private Stage loginStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        authManager = new AuthManager();

        // Verificar se há último usuário
        String ultimoUsuario = authManager.getUltimoUsuario();
        if (ultimoUsuario != null) {
            ultimoUsuarioLabel.setText("Último usuário: " + ultimoUsuario);
            nomeUsuarioField.setText(ultimoUsuario);
        }

        configurarIconesBotoes();
    }

    public void setStage(Stage stage) {
        this.loginStage = stage;
    }

    @FXML
    private void onLogin() {
        String nomeUsuario = nomeUsuarioField.getText().trim();

        if (nomeUsuario.isEmpty()) {
            mostrarErro("Digite um nome de usuário!");
            return;
        }

        try {
            if (modoNovoUsuario) {
                // Registrar novo usuário
                registrarNovoUsuario(nomeUsuario);
            } else {
                // Login de usuário existente
                fazerLogin(nomeUsuario);
            }
        } catch (SQLException e) {
            mostrarErro("Erro ao acessar banco de dados: " + e.getMessage());
        }
    }

    private void fazerLogin(String nomeUsuario) throws SQLException {
        if (authManager.usuarioExiste(nomeUsuario)) {
            if (authManager.login(nomeUsuario)) {
                abrirJanelaPrincipal();
            } else {
                mostrarErro("Erro ao fazer login!");
            }
        } else {
            mostrarErro("Usuário não encontrado! Clique em 'Novo Usuário' para se registrar.");
        }
    }

    private void registrarNovoUsuario(String nomeUsuario) throws SQLException {
        String nomeCompleto = nomeCompletoField.getText().trim();
        String email = emailField.getText().trim();

        if (nomeCompleto.isEmpty()) {
            mostrarErro("Digite seu nome completo!");
            return;
        }

        // Registrar usuário
        if (authManager.registrarUsuario(nomeUsuario, nomeCompleto, email)) {
            // Fazer login automático
            if (authManager.login(nomeUsuario)) {
                abrirJanelaPrincipal();
            }
        } else {
            mostrarErro("Erro ao registrar usuário!");
        }
    }

    @FXML
    private void onToggleNovoUsuario() {
        modoNovoUsuario = !modoNovoUsuario;

        if (modoNovoUsuario) {
            // Mostrar campos de novo usuário
            novoUsuarioBox.setVisible(true);
            novoUsuarioBox.setManaged(true);
            emailBox.setVisible(true);
            emailBox.setManaged(true);
            loginButton.setText("Registrar");
            novoUsuarioButton.setText("Cancelar");
            mensagemLabel.setText("");
        } else {
            // Esconder campos de novo usuário
            novoUsuarioBox.setVisible(false);
            novoUsuarioBox.setManaged(false);
            emailBox.setVisible(false);
            emailBox.setManaged(false);
            loginButton.setText("Entrar");
            novoUsuarioButton.setText("Novo Usuário");
            nomeCompletoField.clear();
            emailField.clear();
            mensagemLabel.setText("");
        }
    }

    private void abrirJanelaPrincipal() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("main-view.fxml")
            );

            Scene scene = new Scene(loader.load());

            // Adicionar CSS
            try {
                String css = getClass().getResource("styles.css").toExternalForm();
                scene.getStylesheets().add(css);
            } catch (Exception e) {
                // CSS opcional
            }

            // Configurar controller principal com usuário logado
            MainController mainController = loader.getController();
            mainController.setAuthManager(authManager);
            mainController.initialize(null, null);

            Stage mainStage = new Stage();
            mainStage.setTitle("DemograficoBUDA - " + authManager.getUsuarioAtual().getNomeCompleto());
            mainStage.setScene(scene);
            mainStage.setResizable(false);
            mainStage.setWidth(780);
            mainStage.setHeight(640);
            mainStage.setMinWidth(780);
            mainStage.setMinHeight(640);
            mainStage.setMaxWidth(780);
            mainStage.setMaxHeight(640);
            UiStyleHelper.applyStageIcon(mainStage);

            // Handler para logout ao fechar
            mainStage.setOnCloseRequest(event -> {
                mainController.shutdown();
            });

            mainStage.show();

            // Fechar janela de login
            loginStage.close();

        } catch (Exception e) {
            mostrarErro("Erro ao abrir janela principal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void mostrarErro(String mensagem) {
        mensagemLabel.setText(mensagem);
        mensagemLabel.setStyle("-fx-text-fill: red;");
    }

    public void shutdown() {
        if (authManager != null) {
            authManager.fechar();
        }
    }

    private void configurarIconesBotoes() {
        UiStyleHelper.applyButtonIcon(loginButton, "lotus");
        UiStyleHelper.applyButtonIcon(novoUsuarioButton, "mala");
    }
}
