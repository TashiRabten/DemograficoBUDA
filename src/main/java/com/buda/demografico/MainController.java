package com.buda.demografico;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller principal da interface JavaFX
 */
public class MainController implements Initializable {

    // Campos do formulário - Dados Demográficos
    @FXML private TextField nomeField;
    @FXML private TextField idadeField;
    @FXML private ComboBox<String> sexoCombo;
    @FXML private ComboBox<String> tradicaoCombo;
    @FXML private TextField temploField;
    @FXML private TextField tempoPraticaField;

    // Campos do formulário - Dados sobre Leitura
    @FXML private ComboBox<String> tipoLeituraCombo;
    @FXML private TextField tituloObraField;
    @FXML private TextField autorField;
    @FXML private ComboBox<String> idiomaLeituraCombo;
    @FXML private ComboBox<String> frequenciaLeituraCombo;
    @FXML private TextArea observacoesArea;

    // Botões
    @FXML private Button salvarButton;
    @FXML private Button limparCamposLeituraButton;
    @FXML private Button limparTudoButton;
    @FXML private Button visualizarDadosButton;
    @FXML private Button sincronizarButton;
    @FXML private Button testarConexaoButton;

    // DatePicker
    @FXML private DatePicker dataEntrevistaPicker;

    // Labels de status e estatísticas
    @FXML private Label statusLabel;
    @FXML private Label totalLocalLabel;
    @FXML private Label naoSincLabel;
    @FXML private Label totalNuvemLabel;
    @FXML private Label mensagemLabel;

    // Lista de entrevistas removida - agora usa visualização separada
    // @FXML private ListView<String> entrevistasListView;

    // Gerenciadores
    private DatabaseManager dbManager;
    private SupabaseClient supabaseClient;
    private AuthManager authManager;

    // Cache de sessão para dados da pessoa atual
    private String cachedNome = null;
    private String cachedIdade = null;
    private String cachedSexo = null;
    private String cachedTradicao = null;
    private String cachedTemplo = null;
    private String cachedTempoPratica = null;

    public void setAuthManager(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Inicializar gerenciadores
        if (dbManager == null) {
            dbManager = new DatabaseManager();
        }
        if (supabaseClient == null) {
            supabaseClient = new SupabaseClient();
        }

        // Configurar ComboBoxes
        sexoCombo.setItems(FXCollections.observableArrayList(
            "Masculino",
            "Feminino",
            "Não-binário",
            "Prefiro não informar",
            "Outro"
        ));

        tradicaoCombo.setItems(FXCollections.observableArrayList(
            "Tibetana (Vajrayana)",
            "Zen",
            "Theravada",
            "Terra Pura",
            "Nichiren",
            "Budismo Engajado",
            "Outro"
        ));

        // Configurar ComboBoxes de Leitura
        tipoLeituraCombo.setItems(FXCollections.observableArrayList(
            "Livros (Dharma)",
            "Revistas Budistas",
            "Tratados/Comentários",
            "Sutras",
            "Artigos Online",
            "Textos de Meditação",
            "Biografias de Mestres"
        ));

        idiomaLeituraCombo.setItems(FXCollections.observableArrayList(
            "Português",
            "Inglês",
            "Tibetano",
            "Chinês",
            "Sânscrito",
            "Pali",
            "Outro"
        ));

        frequenciaLeituraCombo.setItems(FXCollections.observableArrayList(
            "Diária",
            "Semanal (2-3x por semana)",
            "Semanal (1x por semana)",
            "Quinzenal",
            "Mensal",
            "Esporádica",
            "Raramente"
        ));

        // Inicializar DatePicker com data de hoje
        dataEntrevistaPicker.setValue(LocalDate.now());

        // Carregar dados iniciais
        atualizarEstatisticas();

        // Testar conexão inicial
        testarConexaoInicial();

        configurarIconesBotoes();
    }

    private void testarConexaoInicial() {
        new Thread(() -> {
            boolean conectado = supabaseClient.testConnection();
            Platform.runLater(() -> {
                atualizarStatusConexao(conectado ? "Conectado" : "Offline", conectado);
            });
        }).start();
    }

    @FXML
    private void onTestarConexao() {
        mostrarMensagem("Testando conexão...", false);
        new Thread(() -> {
            boolean conectado = supabaseClient.testConnection();
            Platform.runLater(() -> {
                if (conectado) {
                    atualizarStatusConexao("Conectado", true);
                    mostrarMensagem("✅ Conexão estabelecida com sucesso!", false);
                } else {
                    atualizarStatusConexao("Offline", false);
                    mostrarMensagem("❌ Sem conexão. Os dados serão salvos localmente.", true);
                }
            });
        }).start();
    }

    @FXML
    private void onSalvar() {
        // Validar campos obrigatórios
        if (nomeField.getText().trim().isEmpty()) {
            mostrarMensagem("❌ Nome é obrigatório!", true);
            return;
        }

        // Validar tipo de leitura (pode ser selecionado ou digitado)
        String tipoLeitura = tipoLeituraCombo.getValue();
        if (tipoLeitura == null || tipoLeitura.trim().isEmpty()) {
            mostrarMensagem("❌ Selecione ou digite o tipo de leitura!", true);
            return;
        }

        try {
            String usuarioColetor = authManager != null && authManager.isLogado()
                ? authManager.getUsuarioAtual().getNomeUsuario()
                : "anonimo";

            EntrevistadoDTO entrevistado = new EntrevistadoDTO();
            entrevistado.nome = nomeField.getText().trim();
            entrevistado.idade = Integer.parseInt(idadeField.getText().trim());
            entrevistado.sexo = sexoCombo.getValue();
            entrevistado.tradicao = tradicaoCombo.getValue();
            entrevistado.templo = temploField.getText().trim().isEmpty() ? null : temploField.getText().trim();
            entrevistado.tempo_pratica = Integer.parseInt(tempoPraticaField.getText().trim());
            entrevistado.usuario_coletor = usuarioColetor;
            entrevistado.data_entrevista = dataEntrevistaPicker.getValue() != null
                ? dataEntrevistaPicker.getValue().toString()
                : null;

            LeituraDTO leitura = new LeituraDTO();
            leitura.nome_entrevistado = entrevistado.nome;
            leitura.idade = entrevistado.idade;
            leitura.sexo = entrevistado.sexo;
            leitura.tradicao = entrevistado.tradicao;
            leitura.templo = entrevistado.templo;
            leitura.tempo_pratica = entrevistado.tempo_pratica;
            leitura.usuario_coletor = usuarioColetor;
            leitura.data_entrevista = entrevistado.data_entrevista;
            leitura.tipo_leitura = tipoLeitura.trim();
            leitura.titulo_obra = tituloObraField.getText().trim().isEmpty() ? null : tituloObraField.getText().trim();
            leitura.autor = autorField.getText().trim().isEmpty() ? null : autorField.getText().trim();
            leitura.idioma_leitura = idiomaLeituraCombo.getValue();
            leitura.frequencia_leitura = frequenciaLeituraCombo.getValue();
            leitura.observacoes = observacoesArea.getText().trim().isEmpty() ? null : observacoesArea.getText().trim();

            salvarButton.setDisable(true);
            mostrarMensagem("Salvando...", false);

            // Salvar em background
            new Thread(() -> {
                try {
                    EntrevistadoDTO persistido = dbManager.findOrCreateEntrevistado(entrevistado);
                    leitura.entrevistado_id = persistido.id;
                    leitura.entrevistado_cloud_id = persistido.cloud_id;

                    long localId = dbManager.inserirLeitura(leitura, false);
                    leitura.id = (int) localId;

                    boolean sincronizado = false;
                    Integer cloudId = null;
                    try {
                        if (supabaseClient.testConnection()) {
                            if (persistido.cloud_id == null) {
                                Integer pessoaCloud = supabaseClient.inserirEntrevistado(persistido);
                                if (pessoaCloud != null) {
                                    persistido.cloud_id = pessoaCloud;
                                    dbManager.atualizarEntrevistadoCloudId(persistido.id, pessoaCloud);
                                }
                            } else {
                                supabaseClient.atualizarEntrevistado(persistido.cloud_id, persistido);
                            }

                            if (persistido.cloud_id != null) {
                                leitura.entrevistado_cloud_id = persistido.cloud_id;
                                cloudId = supabaseClient.inserirLeitura(leitura, persistido.cloud_id);
                                sincronizado = cloudId != null;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao sincronizar: " + e.getMessage());
                    }

                    if (sincronizado) {
                        try {
                            dbManager.atualizarCloudId((int) localId, cloudId);
                            dbManager.marcarComoSincronizada((int) localId);
                            leitura.cloud_id = cloudId;
                            leitura.entrevistado_cloud_id = persistido.cloud_id;
                        } catch (SQLException e) {
                            System.err.println("Erro ao atualizar status sincronizado: " + e.getMessage());
                        }
                    } else {
                        try {
                            dbManager.marcarComoNaoSincronizada((int) localId);
                        } catch (SQLException e) {
                            System.err.println("Erro ao marcar como não sincronizado: " + e.getMessage());
                        }
                    }

                    boolean finalSincronizado = sincronizado;
                    Platform.runLater(() -> {
                        if (finalSincronizado) {
                            mostrarMensagem("✅ Leitura salva e sincronizada! Pode adicionar mais leituras.", false);
                        } else {
                            mostrarMensagem("✅ Leitura salva localmente. Pode adicionar mais leituras.", false);
                        }

                        // Salvar dados da pessoa no cache
                        salvarNoCache();

                        // Adicionar valores digitados aos ComboBoxes para reutilização
                        adicionarValorAoComboSeNovo(tipoLeituraCombo, tipoLeitura);
                        if (leitura.idioma_leitura != null) {
                            adicionarValorAoComboSeNovo(idiomaLeituraCombo, leitura.idioma_leitura);
                        }
                        if (leitura.frequencia_leitura != null) {
                            adicionarValorAoComboSeNovo(frequenciaLeituraCombo, leitura.frequencia_leitura);
                        }
                        adicionarValorAoComboSeNovo(sexoCombo, leitura.sexo);
                        adicionarValorAoComboSeNovo(tradicaoCombo, leitura.tradicao);

                        // Limpar apenas campos específicos da leitura para facilitar múltiplas entradas
                        limparCamposLeituraParcial();

                        atualizarEstatisticas();
                        salvarButton.setDisable(false);
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        mostrarMensagem("❌ Erro ao salvar: " + e.getMessage(), true);
                        salvarButton.setDisable(false);
                    });
                }
            }).start();

        } catch (NumberFormatException e) {
            mostrarMensagem("❌ Idade e Tempo de Prática devem ser números!", true);
        }
    }

    @FXML
    private void onLimparCamposLeitura() {
        limparCamposLeituraTotal();
        mostrarMensagem("Campos de leitura limpos. Dados da pessoa mantidos.", false);
    }

    @FXML
    private void onLimparTudo() {
        limparTudo();
        limparCache();
        mostrarMensagem("Todos os campos limpos", false);
    }

    @FXML
    private void onVisualizarDados() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("visualizar-view.fxml")
            );
            Scene scene = new Scene(loader.load());

            Stage stage = new Stage();
            stage.setTitle("DemograficoBUDA - Visualizar Dados");
            stage.setScene(scene);
            UiStyleHelper.applyStageIcon(stage);
            stage.show();

        } catch (IOException e) {
            mostrarMensagem("❌ Erro ao abrir visualização: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    @FXML
    private void onSincronizar() {
        sincronizarButton.setDisable(true);
        mostrarMensagem("Sincronizando...", false);

        new Thread(() -> {
            try {
                if (!supabaseClient.testConnection()) {
                    Platform.runLater(() -> {
                        mostrarMensagem("❌ Sem conexão com a internet!", true);
                        sincronizarButton.setDisable(false);
                    });
                    return;
                }

                List<LeituraDTO> naoSincronizadas = dbManager.buscarLeiturasNaoSincronizadas();

                if (naoSincronizadas.isEmpty()) {
                    Platform.runLater(() -> {
                        mostrarMensagem("✅ Não há entrevistas para sincronizar", false);
                        sincronizarButton.setDisable(false);
                    });
                    return;
                }

                int sincronizadas = 0;
                for (LeituraDTO dto : naoSincronizadas) {
                    try {
                        EntrevistadoDTO entrevistado = dbManager.buscarEntrevistadoPorId(dto.entrevistado_id);
                        if (entrevistado == null) {
                            continue;
                        }
                        if (entrevistado.cloud_id == null) {
                            Integer cloudEntrevistado = supabaseClient.inserirEntrevistado(entrevistado);
                            if (cloudEntrevistado != null) {
                                entrevistado.cloud_id = cloudEntrevistado;
                                dbManager.atualizarEntrevistadoCloudId(entrevistado.id, cloudEntrevistado);
                            } else {
                                continue;
                            }
                        } else {
                            supabaseClient.atualizarEntrevistado(entrevistado.cloud_id, entrevistado);
                        }

                        Integer cloudId = supabaseClient.inserirLeitura(dto, entrevistado.cloud_id);
                        if (cloudId != null) {
                            dbManager.atualizarCloudId(dto.id, cloudId);
                            dbManager.marcarComoSincronizada(dto.id);
                            sincronizadas++;
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao sincronizar ID " + dto.id + ": " + e.getMessage());
                    }
                }

                int finalSincronizadas = sincronizadas;
                Platform.runLater(() -> {
                    mostrarMensagem("✅ " + finalSincronizadas + " entrevista(s) sincronizada(s)!", false);
                    atualizarEstatisticas();
                    sincronizarButton.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarMensagem("❌ Erro ao sincronizar: " + e.getMessage(), true);
                    sincronizarButton.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    private void onAtualizarEstatisticas() {
        atualizarEstatisticas();
        mostrarMensagem("Estatísticas atualizadas", false);
    }

    /**
     * Limpa apenas campos específicos da leitura para reutilizar seleções
     */
    private void limparCamposLeituraParcial() {
        tituloObraField.clear();
        autorField.clear();
        observacoesArea.clear();
    }

    private void limparCamposLeituraTotal() {
        limparCamposLeituraParcial();
        tipoLeituraCombo.setValue(null);
        idiomaLeituraCombo.setValue(null);
        frequenciaLeituraCombo.setValue(null);
    }

    /**
     * Limpa todos os campos do formulário
     */
    private void limparTudo() {
        nomeField.clear();
        idadeField.clear();
        sexoCombo.setValue(null);
        tradicaoCombo.setValue(null);
        temploField.clear();
        tempoPraticaField.clear();
        limparCamposLeituraTotal();
    }

    /**
     * Salva os dados demográficos da pessoa no cache
     */
    private void salvarNoCache() {
        cachedNome = nomeField.getText().trim();
        cachedIdade = idadeField.getText().trim();
        cachedSexo = sexoCombo.getValue();
        cachedTradicao = tradicaoCombo.getValue();
        cachedTemplo = temploField.getText().trim();
        cachedTempoPratica = tempoPraticaField.getText().trim();
    }

    /**
     * Limpa o cache de dados da pessoa
     */
    private void limparCache() {
        cachedNome = null;
        cachedIdade = null;
        cachedSexo = null;
        cachedTradicao = null;
        cachedTemplo = null;
        cachedTempoPratica = null;
    }

    /**
     * Adiciona um valor ao ComboBox se não existir ainda
     * Mantém as opções personalizadas durante a sessão
     */
    private void adicionarValorAoComboSeNovo(ComboBox<String> combo, String valor) {
        if (valor != null && !valor.trim().isEmpty()) {
            if (!combo.getItems().contains(valor)) {
                combo.getItems().add(valor);
            }
        }
    }

    private void atualizarEstatisticas() {
        try {
            int totalLocal = dbManager.contarEntrevistados();
            int naoSinc = dbManager.contarNaoSincronizadas();

            totalLocalLabel.setText(String.valueOf(totalLocal));
            naoSincLabel.setText(String.valueOf(naoSinc));

            // Buscar total na nuvem em background
            new Thread(() -> {
                try {
                    int totalNuvem = supabaseClient.contarEntrevistados();
                    Platform.runLater(() -> totalNuvemLabel.setText(String.valueOf(totalNuvem)));
                } catch (Exception e) {
                    Platform.runLater(() -> totalNuvemLabel.setText("?"));
                }
            }).start();

        } catch (SQLException e) {
            System.err.println("Erro ao atualizar estatísticas: " + e.getMessage());
        }
    }

    private void atualizarStatusConexao(String texto, boolean conectado) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(texto);
        statusLabel.getStyleClass().removeAll("status-online", "status-offline");
        statusLabel.getStyleClass().add(conectado ? "status-online" : "status-offline");
    }

    private void configurarIconesBotoes() {
        UiStyleHelper.applyButtonIcon(testarConexaoButton, "lotus");
        UiStyleHelper.applyButtonIcon(salvarButton, "save");
        UiStyleHelper.applyButtonIcon(limparCamposLeituraButton, "broom");
        UiStyleHelper.applyButtonIcon(limparTudoButton, "cancel");
        UiStyleHelper.applyButtonIcon(visualizarDadosButton, "wheel");
        UiStyleHelper.applyButtonIcon(sincronizarButton, "update");
    }

    private void mostrarMensagem(String mensagem, boolean erro) {
        mensagemLabel.setText(mensagem);
        mensagemLabel.setStyle(erro ? "-fx-text-fill: red;" : "-fx-text-fill: gray;");
    }

    public void shutdown() {
        if (dbManager != null) {
            dbManager.fechar();
        }
    }
}
