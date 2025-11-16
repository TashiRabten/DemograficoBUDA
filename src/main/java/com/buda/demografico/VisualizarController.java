package com.buda.demografico;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Tela de visualização dos entrevistados (listagem por pessoa)
 */
public class VisualizarController implements Initializable {

    private static final List<String> SEXO_OPCOES = List.of(
        "Masculino", "Feminino", "Não-binário", "Prefiro não informar", "Outro"
    );
    private static final List<String> TRADICAO_OPCOES = List.of(
        "Tibetana (Vajrayana)", "Zen", "Theravada", "Terra Pura", "Nichiren",
        "Budismo Engajado", "Outro"
    );
    private static final List<String> TIPO_LEITURA_OPCOES = List.of(
        "Livros (Dharma)", "Revistas Budistas", "Tratados/Comentários",
        "Sutras", "Artigos Online", "Textos de Meditação", "Biografias de Mestres"
    );
    private static final List<String> IDIOMA_OPCOES = List.of(
        "Português", "Inglês", "Tibetano", "Chinês", "Sânscrito", "Pali", "Outro"
    );
    private static final List<String> FREQUENCIA_OPCOES = List.of(
        "Diária", "Semanal", "Mensal", "Esporádica", "Somente em retiros"
    );

    @FXML private DatePicker dataInicioPicker;
    @FXML private DatePicker dataFimPicker;
    @FXML private Button filtrarButton;
    @FXML private Button limparFiltroButton;
    @FXML private Button fecharButton;
    @FXML private Button exportarButton;
    @FXML private Button atualizarButton;
    @FXML private Button editarButton;
    @FXML private Button deletarButton;

    @FXML private TableView<EntrevistadoDTO> entrevistadosTable;
    @FXML private TableColumn<EntrevistadoDTO, Integer> colPessoaId;
    @FXML private TableColumn<EntrevistadoDTO, String> colNome;
    @FXML private TableColumn<EntrevistadoDTO, Integer> colIdade;
    @FXML private TableColumn<EntrevistadoDTO, String> colSexo;
    @FXML private TableColumn<EntrevistadoDTO, String> colTradicao;
    @FXML private TableColumn<EntrevistadoDTO, String> colTemplo;
    @FXML private TableColumn<EntrevistadoDTO, Integer> colTempoPratica;
    @FXML private TableColumn<EntrevistadoDTO, Integer> colTotalLeituras;
    @FXML private TableColumn<EntrevistadoDTO, String> colResumoLeituras;

    @FXML private Label totalRegistrosLabel;
    @FXML private Label mensagemLabel;

    private DatabaseManager dbManager;
    private SupabaseClient supabaseClient;
    private ObservableList<EntrevistadoDTO> entrevistados;
    private List<EntrevistadoDTO> todosEntrevistados;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dbManager = new DatabaseManager();
        supabaseClient = new SupabaseClient();
        entrevistados = FXCollections.observableArrayList();
        todosEntrevistados = new ArrayList<>();

        colPessoaId.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(data.getValue().id));
        colNome.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(data.getValue().nome));
        colIdade.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(data.getValue().idade));
        colSexo.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(data.getValue().sexo));
        colTradicao.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(data.getValue().tradicao));
        colTemplo.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(
            data.getValue().templo != null ? data.getValue().templo : ""));
        colTempoPratica.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(data.getValue().tempo_pratica));
        colTotalLeituras.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(data.getValue().getTotalLeituras()));
        colResumoLeituras.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(data.getValue().getResumoLeituras()));

        entrevistadosTable.setItems(entrevistados);
        configurarFiltros();
        configurarIconesBotoes();
        carregarTodosOsDados();
    }

    @FXML
    private void onFiltrar() {
        LocalDate inicio = dataInicioPicker.getValue();
        LocalDate fim = dataFimPicker.getValue();

        if (inicio == null && fim == null) {
            mostrarMensagem("Selecione pelo menos uma data", true);
            return;
        }

        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            mostrarMensagem("❌ Data inicial não pode ser maior que data final!", true);
            return;
        }

        filtrarButton.setDisable(true);
        mostrarMensagem("Filtrando...", false);

        new Thread(() -> {
            try {
                List<EntrevistadoDTO> resultado = new ArrayList<>(todosEntrevistados);
                List<EntrevistadoDTO> filtrado = resultado.stream()
                    .filter(p -> filtrarPorData(p, inicio, fim))
                    .toList();
                Platform.runLater(() -> {
                    atualizarTabela(filtrado);
                    mostrarMensagem("✅ Filtro aplicado: " + filtrado.size() + " registros", false);
                    filtrarButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarMensagem("❌ Erro ao filtrar: " + e.getMessage(), true);
                    filtrarButton.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    private void onLimparFiltro() {
        dataInicioPicker.setValue(null);
        dataFimPicker.setValue(null);
        carregarTodosOsDados();
        mostrarMensagem("Filtro removido", false);
    }

    @FXML
    private void onAtualizar() {
        carregarTodosOsDados();
    }

    @FXML
    private void onExportar() {
        if (entrevistados.isEmpty()) {
            mostrarMensagem("❌ Não há dados para exportar!", true);
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvar CSV");
        fileChooser.setInitialFileName("leituras_budistas_" +
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        Stage stage = (Stage) exportarButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            exportarButton.setDisable(true);
            mostrarMensagem("Exportando...", false);

            new Thread(() -> {
                try (FileWriter writer = new FileWriter(file)) {
                    writer.append("Entrevistado,Idade,Sexo,Tradição,Templo,Tempo Prática,");
                    writer.append("Tipo Leitura,Título,Autor,Idioma,Frequência,Observações,Data\n");

                    for (EntrevistadoDTO pessoa : entrevistados) {
                        if (pessoa.leituras.isEmpty()) {
                            writer.append(escaparCSV(pessoa.nome)).append(",");
                            writer.append(formatNullable(pessoa.idade)).append(",");
                            writer.append(escaparCSV(pessoa.sexo)).append(",");
                            writer.append(escaparCSV(pessoa.tradicao)).append(",");
                            writer.append(escaparCSV(pessoa.templo)).append(",");
                            writer.append(formatNullable(pessoa.tempo_pratica)).append(",");
                            writer.append(",,,,,,");
                            writer.append(escaparCSV(pessoa.data_entrevista)).append("\n");
                            continue;
                        }
                        for (LeituraDTO l : pessoa.leituras) {
                            writer.append(escaparCSV(pessoa.nome)).append(",");
                            writer.append(formatNullable(pessoa.idade)).append(",");
                            writer.append(escaparCSV(pessoa.sexo)).append(",");
                            writer.append(escaparCSV(pessoa.tradicao)).append(",");
                            writer.append(escaparCSV(pessoa.templo)).append(",");
                            writer.append(formatNullable(pessoa.tempo_pratica)).append(",");
                            writer.append(escaparCSV(l.tipo_leitura)).append(",");
                            writer.append(escaparCSV(l.titulo_obra)).append(",");
                            writer.append(escaparCSV(l.autor)).append(",");
                            writer.append(escaparCSV(l.idioma_leitura)).append(",");
                            writer.append(escaparCSV(l.frequencia_leitura)).append(",");
                            writer.append(escaparCSV(l.observacoes)).append(",");
                            writer.append(escaparCSV(pessoa.data_entrevista)).append("\n");
                        }
                    }

                    Platform.runLater(() -> {
                        mostrarMensagem("✅ CSV exportado com sucesso!", false);
                        exportarButton.setDisable(false);
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        mostrarMensagem("❌ Erro ao exportar: " + e.getMessage(), true);
                        exportarButton.setDisable(false);
                    });
                }
            }).start();
        }
    }

    @FXML
    private void onEditar() {
        EntrevistadoDTO selecionado = entrevistadosTable.getSelectionModel().getSelectedItem();
        if (selecionado == null) {
            mostrarMensagem("Selecione uma pessoa para editar", true);
            return;
        }
        abrirDialogoEdicao(selecionado);
    }

    @FXML
    private void onDeletar() {
        EntrevistadoDTO selecionado = entrevistadosTable.getSelectionModel().getSelectedItem();
        if (selecionado == null) {
            mostrarMensagem("Selecione uma pessoa para deletar", true);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Excluir pessoa");
        alert.setHeaderText("Confirmar exclusão de " + selecionado.nome);
        alert.setContentText("Todos os dados e leituras vinculados serão removidos localmente e na nuvem.");
        alert.initOwner(entrevistadosTable.getScene().getWindow());
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        deletarButton.setDisable(true);
        mostrarMensagem("Removendo registro...", false);

        new Thread(() -> {
            try {
                if (selecionado.cloud_id != null) {
                    supabaseClient.deletarLeiturasPorEntrevistado(selecionado.cloud_id);
                    supabaseClient.deletarEntrevistado(selecionado.cloud_id);
                }
                dbManager.deletarEntrevistado(selecionado.id);
                Platform.runLater(() -> {
                    mostrarMensagem("✅ Pessoa removida", false);
                    deletarButton.setDisable(false);
                    carregarTodosOsDados();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarMensagem("❌ Erro ao deletar: " + e.getMessage(), true);
                    deletarButton.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    private void onFechar(ActionEvent event) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.close();
    }

    private void abrirDialogoEdicao(EntrevistadoDTO pessoa) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Visualizar / Editar");
        dialog.initOwner(entrevistadosTable.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/com/buda/demografico/demografico-styles.css").toExternalForm());
        pane.getStyleClass().add("main-background");
        pane.setPrefSize(760, 620);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setPrefViewportHeight(520);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox container = new VBox(14);
        container.setPadding(new Insets(15));
        container.setFillWidth(true);
        scrollPane.setContent(container);

        Label pessoaHeader = new Label("Dados do Entrevistado");
        pessoaHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        GridPane pessoaGrid = criarGridPadrao();

        TextField nomeField = criarTextField(pessoa.nome);
        TextField idadeField = criarTextField(pessoa.idade != null ? pessoa.idade.toString() : "");
        ComboBox<String> sexoCombo = criarComboTexto(SEXO_OPCOES, pessoa.sexo);
        ComboBox<String> tradicaoCombo = criarComboTexto(TRADICAO_OPCOES, pessoa.tradicao);
        TextField temploField = criarTextField(pessoa.templo);
        TextField tempoPraticaField = criarTextField(pessoa.tempo_pratica != null ? pessoa.tempo_pratica.toString() : "");
        TextField usuarioColetorField = criarTextField(pessoa.usuario_coletor);

        DatePicker dataEntrevistaPicker = new DatePicker();
        dataEntrevistaPicker.setConverter(criarDateConverter());
        LocalDate dataEntrevista = parseData(pessoa.data_entrevista);
        dataEntrevistaPicker.setValue(dataEntrevista);
        dataEntrevistaPicker.setMaxWidth(Double.MAX_VALUE);

        adicionarCampoGrid(pessoaGrid, "Nome:", nomeField, 0);
        adicionarCampoGrid(pessoaGrid, "Idade:", idadeField, 1);
        adicionarCampoGrid(pessoaGrid, "Sexo/Gênero:", sexoCombo, 2);
        adicionarCampoGrid(pessoaGrid, "Tradição Budista:", tradicaoCombo, 3);
        adicionarCampoGrid(pessoaGrid, "Templo / Centro:", temploField, 4);
        adicionarCampoGrid(pessoaGrid, "Tempo de Prática (anos):", tempoPraticaField, 5);
        adicionarCampoGrid(pessoaGrid, "Usuário Coletor:", usuarioColetorField, 6);
        adicionarCampoGrid(pessoaGrid, "Data da Entrevista:", dataEntrevistaPicker, 7);

        Label leituraHeader = new Label("Obras Registradas");
        leituraHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        ComboBox<LeituraDTO> leituraCombo = new ComboBox<>();
        leituraCombo.setMaxWidth(Double.MAX_VALUE);
        leituraCombo.setItems(FXCollections.observableArrayList(pessoa.leituras));
        leituraCombo.setPromptText(pessoa.leituras.isEmpty() ? "Sem leituras cadastradas" : "Selecione a obra");
        leituraCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(LeituraDTO leitura) {
                if (leitura == null) {
                    return "";
                }
                String titulo = (leitura.titulo_obra != null && !leitura.titulo_obra.isBlank())
                    ? leitura.titulo_obra : "Sem título";
                String tipo = leitura.tipo_leitura != null ? leitura.tipo_leitura : "Tipo não informado";
                return titulo + " • " + tipo;
            }

            @Override
            public LeituraDTO fromString(String string) {
                return null;
            }
        });
        if (!pessoa.leituras.isEmpty()) {
            leituraCombo.getSelectionModel().selectFirst();
        } else {
            leituraCombo.setDisable(true);
        }

        GridPane leituraGrid = criarGridPadrao();
        ComboBox<String> tipoLeituraCombo = criarComboTexto(TIPO_LEITURA_OPCOES, null);
        TextField tituloField = criarTextField(null);
        TextField autorField = criarTextField(null);
        ComboBox<String> idiomaCombo = criarComboTexto(IDIOMA_OPCOES, null);
        ComboBox<String> frequenciaCombo = criarComboTexto(FREQUENCIA_OPCOES, null);
        TextArea observacoesArea = new TextArea();
        observacoesArea.setWrapText(true);
        observacoesArea.setPrefRowCount(3);

        adicionarCampoGrid(leituraGrid, "Tipo de Leitura:", tipoLeituraCombo, 0);
        adicionarCampoGrid(leituraGrid, "Título da Obra:", tituloField, 1);
        adicionarCampoGrid(leituraGrid, "Autor:", autorField, 2);
        adicionarCampoGrid(leituraGrid, "Idioma:", idiomaCombo, 3);
        adicionarCampoGrid(leituraGrid, "Frequência:", frequenciaCombo, 4);
        adicionarCampoGrid(leituraGrid, "Observações:", observacoesArea, 5);

        Label dialogStatus = new Label();
        dialogStatus.setStyle("-fx-text-fill: gray;");

        Button excluirLeituraButton = new Button("Excluir Leitura Selecionada");
        excluirLeituraButton.setMaxWidth(Double.MAX_VALUE);
        excluirLeituraButton.getStyleClass().add("button-cancel");
        excluirLeituraButton.setDisable(pessoa.leituras.isEmpty());
        excluirLeituraButton.setOnAction(evt ->
            excluirLeituraSelecionada(pessoa, leituraCombo, dialogStatus, tipoLeituraCombo,
                tituloField, autorField, idiomaCombo, frequenciaCombo, observacoesArea, excluirLeituraButton)
        );

        container.getChildren().addAll(
            pessoaHeader, pessoaGrid,
            new Separator(),
            leituraHeader,
            leituraCombo,
            leituraGrid,
            dialogStatus,
            excluirLeituraButton
        );

        leituraCombo.valueProperty().addListener((obs, anterior, selecionada) ->
            preencherCamposLeitura(selecionada, tipoLeituraCombo, tituloField, autorField, idiomaCombo, frequenciaCombo, observacoesArea)
        );
        if (!pessoa.leituras.isEmpty()) {
            preencherCamposLeitura(leituraCombo.getValue(), tipoLeituraCombo, tituloField, autorField, idiomaCombo, frequenciaCombo, observacoesArea);
        } else {
            limparCamposLeitura(tipoLeituraCombo, tituloField, autorField, idiomaCombo, frequenciaCombo, observacoesArea);
        }

        pane.setContent(scrollPane);

        ButtonType salvarType = new ButtonType("Salvar Alterações", ButtonBar.ButtonData.OK_DONE);
        ButtonType fecharType = new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(salvarType, fecharType);

        dialog.setOnShown(event ->
            UiStyleHelper.applyStageIcon((Stage) dialog.getDialogPane().getScene().getWindow())
        );

        Button salvarButton = (Button) dialog.getDialogPane().lookupButton(salvarType);
        salvarButton.getStyleClass().add("primary-button");
        salvarButton.setMaxWidth(Double.MAX_VALUE);
        salvarButton.addEventFilter(ActionEvent.ACTION, actionEvent -> {
            actionEvent.consume();
            salvarAlteracoes(dialog, pessoa, nomeField, idadeField, sexoCombo, tradicaoCombo,
                temploField, tempoPraticaField, usuarioColetorField, dataEntrevistaPicker,
                leituraCombo, tipoLeituraCombo, tituloField, autorField, idiomaCombo,
                frequenciaCombo, observacoesArea, dialogStatus, salvarButton);
        });

        Button fecharButtonDialog = (Button) dialog.getDialogPane().lookupButton(fecharType);
        fecharButtonDialog.getStyleClass().add("button-default");

        dialog.showAndWait();
    }

    private void salvarAlteracoes(
        Dialog<ButtonType> dialog,
        EntrevistadoDTO pessoa,
        TextField nomeField,
        TextField idadeField,
        ComboBox<String> sexoCombo,
        ComboBox<String> tradicaoCombo,
        TextField temploField,
        TextField tempoPraticaField,
        TextField usuarioColetorField,
        DatePicker dataPicker,
        ComboBox<LeituraDTO> leituraCombo,
        ComboBox<String> tipoLeituraCombo,
        TextField tituloField,
        TextField autorField,
        ComboBox<String> idiomaCombo,
        ComboBox<String> frequenciaCombo,
        TextArea observacoesArea,
        Label statusLabel,
        Button salvarButton
    ) {
        String nome = limparTexto(nomeField.getText());
        String idadeTexto = idadeField.getText() != null ? idadeField.getText().trim() : "";
        String sexo = limparTexto(sexoCombo.getValue());
        String tradicao = limparTexto(tradicaoCombo.getValue());
        String templo = limparTexto(temploField.getText());
        String tempoTexto = tempoPraticaField.getText() != null ? tempoPraticaField.getText().trim() : "";
        String usuarioColetor = limparTexto(usuarioColetorField.getText());

        if (nome == null) {
            statusLabel.setText("Informe o nome da pessoa.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        Integer idade;
        Integer tempoPratica;
        try {
            idade = idadeTexto.isEmpty() ? null : Integer.parseInt(idadeTexto);
        } catch (NumberFormatException e) {
            statusLabel.setText("Idade inválida.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        try {
            tempoPratica = tempoTexto.isEmpty() ? null : Integer.parseInt(tempoTexto);
        } catch (NumberFormatException e) {
            statusLabel.setText("Tempo de prática inválido.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        LocalDate dataEntrevista = dataPicker.getValue();
        pessoa.nome = nome;
        pessoa.idade = idade;
        pessoa.sexo = sexo;
        pessoa.tradicao = tradicao;
        pessoa.templo = templo;
        pessoa.tempo_pratica = tempoPratica;
        pessoa.usuario_coletor = usuarioColetor;
        pessoa.data_entrevista = dataEntrevista != null ? dataEntrevista.toString() : null;

        LeituraDTO leituraSelecionada = leituraCombo.isDisabled() ? null : leituraCombo.getValue();
        if (leituraSelecionada != null) {
            String tipoLeitura = limparTexto(tipoLeituraCombo.getValue());
            if (tipoLeitura == null) {
                statusLabel.setText("Selecione o tipo de leitura.");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }
            leituraSelecionada.tipo_leitura = tipoLeitura;
            leituraSelecionada.titulo_obra = limparTexto(tituloField.getText());
            leituraSelecionada.autor = limparTexto(autorField.getText());
            leituraSelecionada.idioma_leitura = limparTexto(idiomaCombo.getValue());
            leituraSelecionada.frequencia_leitura = limparTexto(frequenciaCombo.getValue());
            leituraSelecionada.observacoes = limparTexto(observacoesArea.getText());
        }

        salvarButton.setDisable(true);
        statusLabel.setText("Salvando alterações...");
        statusLabel.setStyle("-fx-text-fill: gray;");

        new Thread(() -> {
            try {
                dbManager.atualizarEntrevistado(pessoa);
                if (pessoa.cloud_id != null) {
                    supabaseClient.atualizarEntrevistado(pessoa.cloud_id, pessoa);
                }
                if (leituraSelecionada != null) {
                    dbManager.atualizarDadosLeitura(leituraSelecionada);
                    if (leituraSelecionada.cloud_id != null && pessoa.cloud_id != null) {
                        supabaseClient.atualizarLeitura(leituraSelecionada.cloud_id, leituraSelecionada, pessoa.cloud_id);
                    }
                }

                Platform.runLater(() -> {
                    statusLabel.setText("✅ Alterações salvas");
                    statusLabel.setStyle("-fx-text-fill: green;");
                    salvarButton.setDisable(false);
                    dialog.close();
                    carregarTodosOsDados();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("❌ Erro ao salvar: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                    salvarButton.setDisable(false);
                });
            }
        }).start();
    }

    private void excluirLeituraSelecionada(
        EntrevistadoDTO pessoa,
        ComboBox<LeituraDTO> leituraCombo,
        Label statusLabel,
        ComboBox<String> tipoLeituraCombo,
        TextField tituloField,
        TextField autorField,
        ComboBox<String> idiomaCombo,
        ComboBox<String> frequenciaCombo,
        TextArea observacoesArea,
        Button excluirButton
    ) {
        LeituraDTO leitura = leituraCombo.getValue();
        if (leitura == null) {
            statusLabel.setText("Nenhuma leitura selecionada.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Excluir leitura");
        confirm.setHeaderText("Excluir \"" + (leitura.titulo_obra != null ? leitura.titulo_obra : "Sem título") + "\"?");
        confirm.setContentText("A leitura será removida localmente e da nuvem.");
        confirm.initOwner(entrevistadosTable.getScene().getWindow());
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        statusLabel.setText("Removendo leitura...");
        statusLabel.setStyle("-fx-text-fill: gray;");

        new Thread(() -> {
            try {
                if (leitura.cloud_id != null) {
                    supabaseClient.deletarLeitura(leitura.cloud_id);
                }
                dbManager.deletarLeitura(leitura.id);
                Platform.runLater(() -> {
                    statusLabel.setText("✅ Leitura excluída");
                    statusLabel.setStyle("-fx-text-fill: green;");
                    pessoa.leituras.remove(leitura);
                    leituraCombo.getItems().remove(leitura);
                    if (leituraCombo.getItems().isEmpty()) {
                        leituraCombo.setDisable(true);
                        excluirButton.setDisable(true);
                        limparCamposLeitura(tipoLeituraCombo, tituloField, autorField, idiomaCombo, frequenciaCombo, observacoesArea);
                    } else {
                        leituraCombo.getSelectionModel().selectFirst();
                    }
                    carregarTodosOsDados();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("❌ Erro ao excluir: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        }).start();
    }

    private void carregarTodosOsDados() {
        mostrarMensagem("Carregando registros...", false);
        new Thread(() -> {
            try {
                List<EntrevistadoDTO> dados = dbManager.buscarEntrevistadosComLeituras();
                Platform.runLater(() -> {
                    todosEntrevistados.clear();
                    todosEntrevistados.addAll(dados);
                    atualizarTabela(dados);
                    mostrarMensagem("✅ " + dados.size() + " registros carregados", false);
                });
            } catch (SQLException e) {
                Platform.runLater(() -> mostrarMensagem("❌ Erro ao carregar: " + e.getMessage(), true));
            }
        }).start();
    }

    private void atualizarTabela(List<EntrevistadoDTO> dados) {
        entrevistados.setAll(dados);
        totalRegistrosLabel.setText("Total: " + dados.size() + " registros");
    }

    private void configurarFiltros() {
        StringConverter<LocalDate> converter = criarDateConverter();
        dataInicioPicker.setConverter(converter);
        dataFimPicker.setConverter(converter);
    }

    private void configurarIconesBotoes() {
        UiStyleHelper.applyButtonIcon(filtrarButton, "calendar");
        UiStyleHelper.applyButtonIcon(limparFiltroButton, "broom");
        UiStyleHelper.applyButtonIcon(exportarButton, "save");
        UiStyleHelper.applyButtonIcon(atualizarButton, "update");
        UiStyleHelper.applyButtonIcon(editarButton, "wheel");
        UiStyleHelper.applyButtonIcon(deletarButton, "cancel");
        UiStyleHelper.applyButtonIcon(fecharButton, "cancel");
    }

    private void preencherCamposLeitura(
        LeituraDTO leitura,
        ComboBox<String> tipoLeituraCombo,
        TextField tituloField,
        TextField autorField,
        ComboBox<String> idiomaCombo,
        ComboBox<String> frequenciaCombo,
        TextArea observacoesArea
    ) {
        if (leitura == null) {
            limparCamposLeitura(tipoLeituraCombo, tituloField, autorField, idiomaCombo, frequenciaCombo, observacoesArea);
            return;
        }
        tipoLeituraCombo.setValue(leitura.tipo_leitura);
        tituloField.setText(leitura.titulo_obra != null ? leitura.titulo_obra : "");
        autorField.setText(leitura.autor != null ? leitura.autor : "");
        idiomaCombo.setValue(leitura.idioma_leitura);
        frequenciaCombo.setValue(leitura.frequencia_leitura);
        observacoesArea.setText(leitura.observacoes != null ? leitura.observacoes : "");
    }

    private void limparCamposLeitura(
        ComboBox<String> tipoLeituraCombo,
        TextField tituloField,
        TextField autorField,
        ComboBox<String> idiomaCombo,
        ComboBox<String> frequenciaCombo,
        TextArea observacoesArea
    ) {
        tipoLeituraCombo.setValue(null);
        tituloField.clear();
        autorField.clear();
        idiomaCombo.setValue(null);
        frequenciaCombo.setValue(null);
        observacoesArea.clear();
    }

    private boolean filtrarPorData(EntrevistadoDTO pessoa, LocalDate inicio, LocalDate fim) {
        if (inicio == null && fim == null) {
            return true;
        }
        LocalDate data = parseData(pessoa.data_entrevista);
        if (data == null) {
            return false;
        }
        if (inicio != null && data.isBefore(inicio)) {
            return false;
        }
        if (fim != null && data.isAfter(fim)) {
            return false;
        }
        return true;
    }

    private LocalDate parseData(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(valor, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private void mostrarMensagem(String mensagem, boolean erro) {
        mensagemLabel.setText(mensagem);
        mensagemLabel.setStyle(erro ? "-fx-text-fill: red;" : "-fx-text-fill: #1f2f44;");
    }

    private String escaparCSV(String valor) {
        if (valor == null) {
            return "";
        }
        String escaped = valor.replace("\"", "\"\"");
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String formatNullable(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private GridPane criarGridPadrao() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(160);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);
        return grid;
    }

    private void adicionarCampoGrid(GridPane grid, String rotulo, Node campo, int linha) {
        Label label = new Label(rotulo);
        label.setStyle("-fx-font-weight: bold;");
        grid.add(label, 0, linha);
        grid.add(campo, 1, linha);
        GridPane.setHgrow(campo, Priority.ALWAYS);
    }

    private ComboBox<String> criarComboTexto(List<String> opcoes, String valorInicial) {
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(opcoes));
        combo.setEditable(true);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setValue(valorInicial);
        return combo;
    }

    private TextField criarTextField(String valor) {
        TextField field = new TextField();
        field.setText(valor != null ? valor : "");
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private StringConverter<LocalDate> criarDateConverter() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : formatter.format(date);
            }

            @Override
            public LocalDate fromString(String string) {
                if (string == null || string.isBlank()) {
                    return null;
                }
                return LocalDate.parse(string, formatter);
            }
        };
    }

    private String limparTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String trimmed = valor.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
