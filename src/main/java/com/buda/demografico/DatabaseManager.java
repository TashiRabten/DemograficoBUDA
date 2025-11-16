package com.buda.demografico;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerenciador de banco de dados local SQLite
 * Mantém uma cópia local dos dados de leituras e sincroniza com o Supabase
 */
public class DatabaseManager {
    private static final Path STORAGE_DIR = Paths.get(System.getProperty("user.home"), "Documents", "DemograficoBUDA");
    private static final Path DB_PATH = STORAGE_DIR.resolve("leituras_buda.db");
    private Connection connection;

    public DatabaseManager() {
        try {
            prepararDiretorio();
            // Conectar ao banco SQLite
            String url = "jdbc:sqlite:" + DB_PATH.toAbsolutePath();
            connection = DriverManager.getConnection(url);
            criarTabelas();
            System.out.println("[DatabaseManager] ✅ Banco de dados local inicializado");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ❌ Erro ao inicializar banco: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cria as tabelas necessárias no banco local
     */
    private void criarTabelas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            int version = 0;
            try (ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
                if (rs.next()) {
                    version = rs.getInt(1);
                }
            }

            if (version < 2) {
                stmt.execute("DROP TABLE IF EXISTS leituras");
                stmt.execute("DROP TABLE IF EXISTS entrevistados");
                criarNovasTabelas(stmt);
                stmt.execute("PRAGMA user_version = 2");
            } else {
                criarNovasTabelas(stmt);
            }
        }
    }

    private void prepararDiretorio() throws SQLException {
        try {
            if (!Files.exists(STORAGE_DIR)) {
                Files.createDirectories(STORAGE_DIR);
            }
            Path legado = Paths.get("leituras_buda.db");
            if (Files.exists(legado) && !Files.exists(DB_PATH)) {
                Files.copy(legado, DB_PATH, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            throw new SQLException("Não foi possível preparar diretório de dados em " + STORAGE_DIR, e);
        }
    }

    private void criarNovasTabelas(Statement stmt) throws SQLException {
        String entrevistadosSql = """
            CREATE TABLE IF NOT EXISTS entrevistados (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cloud_id INTEGER,
                nome TEXT NOT NULL,
                idade INTEGER NOT NULL,
                sexo TEXT NOT NULL,
                tradicao TEXT NOT NULL,
                templo TEXT,
                tempo_pratica INTEGER NOT NULL,
                usuario_coletor TEXT NOT NULL,
                data_entrevista TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT
            );
            """;

        String leiturasSql = """
            CREATE TABLE IF NOT EXISTS leituras (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cloud_id INTEGER,
                entrevistado_id INTEGER NOT NULL,
                tipo_leitura TEXT NOT NULL,
                titulo_obra TEXT,
                autor TEXT,
                idioma_leitura TEXT,
                frequencia_leitura TEXT,
                observacoes TEXT,
                sincronizado INTEGER DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT,
                FOREIGN KEY (entrevistado_id) REFERENCES entrevistados(id) ON DELETE CASCADE
            );
            """;

        stmt.execute(entrevistadosSql);
        stmt.execute(leiturasSql);
    }

    /**
     * Insere uma nova leitura no banco local
     */
    public long inserirLeitura(LeituraDTO leitura, boolean sincronizado) throws SQLException {
        String sql = """
            INSERT INTO leituras (
                entrevistado_id, tipo_leitura, titulo_obra, autor,
                idioma_leitura, frequencia_leitura, observacoes,
                sincronizado
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, leitura.entrevistado_id);
            pstmt.setString(2, leitura.tipo_leitura);
            pstmt.setString(3, leitura.titulo_obra);
            pstmt.setString(4, leitura.autor);
            pstmt.setString(5, leitura.idioma_leitura);
            pstmt.setString(6, leitura.frequencia_leitura);
            pstmt.setString(7, leitura.observacoes);
            pstmt.setInt(8, sincronizado ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return -1;
    }

    /**
     * Busca todas as leituras do banco local
     */
    public List<LeituraDTO> buscarTodasLeituras() throws SQLException {
        String sql = """
            SELECT l.id AS leitura_id, l.cloud_id AS leitura_cloud_id,
                   l.entrevistado_id, e.cloud_id AS entrevistado_cloud_id,
                   e.nome, e.idade, e.sexo, e.tradicao, e.templo, e.tempo_pratica,
                   e.usuario_coletor, e.data_entrevista,
                   l.tipo_leitura, l.titulo_obra, l.autor, l.idioma_leitura,
                   l.frequencia_leitura, l.observacoes, l.created_at
            FROM leituras l
            JOIN entrevistados e ON e.id = l.entrevistado_id
            ORDER BY l.created_at DESC
            """;
        List<LeituraDTO> leituras = new ArrayList<>();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                LeituraDTO dto = new LeituraDTO();
                dto.id = rs.getInt("leitura_id");
                dto.cloud_id = rs.getObject("leitura_cloud_id") != null ? rs.getInt("leitura_cloud_id") : null;
                dto.entrevistado_id = rs.getInt("entrevistado_id");
                dto.entrevistado_cloud_id = rs.getObject("entrevistado_cloud_id") != null ? rs.getInt("entrevistado_cloud_id") : null;
                dto.nome_entrevistado = rs.getString("nome");
                dto.idade = rs.getInt("idade");
                dto.sexo = rs.getString("sexo");
                dto.tradicao = rs.getString("tradicao");
                dto.templo = rs.getString("templo");
                dto.tempo_pratica = rs.getInt("tempo_pratica");
                dto.usuario_coletor = rs.getString("usuario_coletor");
                dto.data_entrevista = rs.getString("data_entrevista");
                dto.tipo_leitura = rs.getString("tipo_leitura");
                dto.titulo_obra = rs.getString("titulo_obra");
                dto.autor = rs.getString("autor");
                dto.idioma_leitura = rs.getString("idioma_leitura");
                dto.frequencia_leitura = rs.getString("frequencia_leitura");
                dto.observacoes = rs.getString("observacoes");
                dto.created_at = rs.getString("created_at");
                leituras.add(dto);
            }
        }

        return leituras;
    }

    /**
     * Busca leituras não sincronizadas
     */
    public List<LeituraDTO> buscarLeiturasNaoSincronizadas() throws SQLException {
        String sql = """
            SELECT l.id AS leitura_id, l.cloud_id AS leitura_cloud_id,
                   l.entrevistado_id, e.cloud_id AS entrevistado_cloud_id,
                   e.nome, e.idade, e.sexo, e.tradicao, e.templo, e.tempo_pratica,
                   e.usuario_coletor, e.data_entrevista,
                   l.tipo_leitura, l.titulo_obra, l.autor, l.idioma_leitura,
                   l.frequencia_leitura, l.observacoes, l.created_at
            FROM leituras l
            JOIN entrevistados e ON e.id = l.entrevistado_id
            WHERE l.sincronizado = 0 OR e.cloud_id IS NULL
            """;
        List<LeituraDTO> leituras = new ArrayList<>();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                LeituraDTO dto = new LeituraDTO();
                dto.id = rs.getInt("leitura_id");
                dto.cloud_id = rs.getObject("leitura_cloud_id") != null ? rs.getInt("leitura_cloud_id") : null;
                dto.entrevistado_id = rs.getInt("entrevistado_id");
                dto.entrevistado_cloud_id = rs.getObject("entrevistado_cloud_id") != null ? rs.getInt("entrevistado_cloud_id") : null;
                dto.nome_entrevistado = rs.getString("nome");
                dto.idade = rs.getInt("idade");
                dto.sexo = rs.getString("sexo");
                dto.tradicao = rs.getString("tradicao");
                dto.templo = rs.getString("templo");
                dto.tempo_pratica = rs.getInt("tempo_pratica");
                dto.usuario_coletor = rs.getString("usuario_coletor");
                dto.data_entrevista = rs.getString("data_entrevista");
                dto.tipo_leitura = rs.getString("tipo_leitura");
                dto.titulo_obra = rs.getString("titulo_obra");
                dto.autor = rs.getString("autor");
                dto.idioma_leitura = rs.getString("idioma_leitura");
                dto.frequencia_leitura = rs.getString("frequencia_leitura");
                dto.observacoes = rs.getString("observacoes");
                leituras.add(dto);
            }
        }

        return leituras;
    }

    public List<EntrevistadoDTO> buscarEntrevistadosComLeituras() throws SQLException {
        String sql = """
            SELECT
                e.id            AS e_id,
                e.cloud_id      AS e_cloud_id,
                e.nome,
                e.idade,
                e.sexo,
                e.tradicao,
                e.templo,
                e.tempo_pratica,
                e.usuario_coletor,
                e.data_entrevista,
                l.id            AS l_id,
                l.cloud_id      AS l_cloud_id,
                l.tipo_leitura,
                l.titulo_obra,
                l.autor,
                l.idioma_leitura,
                l.frequencia_leitura,
                l.observacoes,
                l.created_at    AS l_created_at
            FROM entrevistados e
            LEFT JOIN leituras l ON l.entrevistado_id = e.id
            ORDER BY e.created_at DESC, l.created_at DESC
            """;

        Map<Integer, EntrevistadoDTO> mapa = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int entId = rs.getInt("e_id");
                EntrevistadoDTO pessoa = mapa.get(entId);
                if (pessoa == null) {
                    pessoa = new EntrevistadoDTO();
                    pessoa.id = entId;
                    mapa.put(entId, pessoa);
                }
                pessoa.cloud_id = rs.getObject("e_cloud_id") != null ? rs.getInt("e_cloud_id") : null;
                pessoa.nome = rs.getString("nome");
                pessoa.idade = rs.getInt("idade");
                pessoa.sexo = rs.getString("sexo");
                pessoa.tradicao = rs.getString("tradicao");
                pessoa.templo = rs.getString("templo");
                pessoa.tempo_pratica = rs.getInt("tempo_pratica");
                pessoa.usuario_coletor = rs.getString("usuario_coletor");
                pessoa.data_entrevista = rs.getString("data_entrevista");

                int leituraId = rs.getInt("l_id");
                if (!rs.wasNull()) {
                    LeituraDTO leitura = new LeituraDTO();
                    leitura.id = leituraId;
                    leitura.cloud_id = rs.getObject("l_cloud_id") != null ? rs.getInt("l_cloud_id") : null;
                    leitura.entrevistado_id = pessoa.id;
                    leitura.entrevistado_cloud_id = pessoa.cloud_id;
                    leitura.tipo_leitura = rs.getString("tipo_leitura");
                    leitura.titulo_obra = rs.getString("titulo_obra");
                    leitura.autor = rs.getString("autor");
                    leitura.idioma_leitura = rs.getString("idioma_leitura");
                    leitura.frequencia_leitura = rs.getString("frequencia_leitura");
                    leitura.observacoes = rs.getString("observacoes");
                    leitura.created_at = rs.getString("l_created_at");
                    leitura.nome_entrevistado = pessoa.nome;
                    leitura.idade = pessoa.idade;
                    leitura.sexo = pessoa.sexo;
                    leitura.tradicao = pessoa.tradicao;
                    leitura.templo = pessoa.templo;
                    leitura.tempo_pratica = pessoa.tempo_pratica;
                    leitura.usuario_coletor = pessoa.usuario_coletor;
                    leitura.data_entrevista = pessoa.data_entrevista;
                    pessoa.leituras.add(leitura);
                }
            }
        }
        return new ArrayList<>(mapa.values());
    }

    /**
     * Marca uma leitura como sincronizada
     */
    public void marcarComoSincronizada(int id) throws SQLException {
        String sql = "UPDATE leituras SET sincronizado = 1 WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    /**
     * Conta o total de leituras
     */
    public int contarLeituras() throws SQLException {
        String sql = "SELECT COUNT(*) FROM leituras";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public int contarEntrevistados() throws SQLException {
        String sql = "SELECT COUNT(*) FROM entrevistados";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Conta leituras não sincronizadas
     */
    public int contarNaoSincronizadas() throws SQLException {
        String sql = "SELECT COUNT(*) FROM leituras WHERE sincronizado = 0";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Deleta uma leitura do banco local
     */
    public void deletarLeitura(int id) throws SQLException {
        String sql = "DELETE FROM leituras WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    /**
     * Atualiza dados específicos da leitura
     */
    public void atualizarDadosLeitura(LeituraDTO leitura) throws SQLException {
        String sql = """
            UPDATE leituras SET
                tipo_leitura = ?,
                titulo_obra = ?, autor = ?, idioma_leitura = ?,
                frequencia_leitura = ?, observacoes = ?, updated_at = datetime('now'),
                sincronizado = 0
            WHERE id = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, leitura.tipo_leitura);
            pstmt.setString(2, leitura.titulo_obra);
            pstmt.setString(3, leitura.autor);
            pstmt.setString(4, leitura.idioma_leitura);
            pstmt.setString(5, leitura.frequencia_leitura);
            pstmt.setString(6, leitura.observacoes);
            pstmt.setInt(7, leitura.id);
            pstmt.executeUpdate();
        }
    }

    public void atualizarCloudId(int id, int cloudId) throws SQLException {
        String sql = "UPDATE leituras SET cloud_id = ?, sincronizado = 1 WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, cloudId);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        }
    }

    public void marcarComoNaoSincronizada(int id) throws SQLException {
        String sql = "UPDATE leituras SET sincronizado = 0 WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public EntrevistadoDTO findOrCreateEntrevistado(EntrevistadoDTO dto) throws SQLException {
        EntrevistadoDTO existente = buscarEntrevistadoPorChave(dto);
        if (existente != null) {
            existente.templo = dto.templo;
            existente.tempo_pratica = dto.tempo_pratica;
            existente.usuario_coletor = dto.usuario_coletor;
            existente.data_entrevista = dto.data_entrevista;
            atualizarEntrevistado(existente);
            return existente;
        }
        String sql = """
            INSERT INTO entrevistados (
                nome, idade, sexo, tradicao, templo, tempo_pratica,
                usuario_coletor, data_entrevista
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, dto.nome);
            pstmt.setInt(2, dto.idade);
            pstmt.setString(3, dto.sexo);
            pstmt.setString(4, dto.tradicao);
            pstmt.setString(5, dto.templo);
            pstmt.setInt(6, dto.tempo_pratica != null ? dto.tempo_pratica : 0);
            pstmt.setString(7, dto.usuario_coletor);
            pstmt.setString(8, dto.data_entrevista);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    dto.id = rs.getInt(1);
                }
            }
        }
        return dto;
    }

    private EntrevistadoDTO buscarEntrevistadoPorChave(EntrevistadoDTO dto) throws SQLException {
        String sql = """
            SELECT * FROM entrevistados
            WHERE nome = ? AND idade = ? AND sexo = ? AND tradicao = ?
              AND IFNULL(templo,'') = IFNULL(?, '')
              AND tempo_pratica = ?
            LIMIT 1
            """;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, dto.nome);
            pstmt.setInt(2, dto.idade);
            pstmt.setString(3, dto.sexo);
            pstmt.setString(4, dto.tradicao);
            pstmt.setString(5, dto.templo);
            pstmt.setInt(6, dto.tempo_pratica != null ? dto.tempo_pratica : 0);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    EntrevistadoDTO existente = new EntrevistadoDTO();
                    existente.id = rs.getInt("id");
                    existente.cloud_id = rs.getObject("cloud_id") != null ? rs.getInt("cloud_id") : null;
                    existente.nome = rs.getString("nome");
                    existente.idade = rs.getInt("idade");
                    existente.sexo = rs.getString("sexo");
                    existente.tradicao = rs.getString("tradicao");
                    existente.templo = rs.getString("templo");
                    existente.tempo_pratica = rs.getInt("tempo_pratica");
                    existente.usuario_coletor = rs.getString("usuario_coletor");
                    existente.data_entrevista = rs.getString("data_entrevista");
                    return existente;
                }
            }
        }
        return null;
    }

    public EntrevistadoDTO buscarEntrevistadoPorId(int id) throws SQLException {
        String sql = "SELECT * FROM entrevistados WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    EntrevistadoDTO dto = new EntrevistadoDTO();
                    dto.id = rs.getInt("id");
                    dto.cloud_id = rs.getObject("cloud_id") != null ? rs.getInt("cloud_id") : null;
                    dto.nome = rs.getString("nome");
                    dto.idade = rs.getInt("idade");
                    dto.sexo = rs.getString("sexo");
                    dto.tradicao = rs.getString("tradicao");
                    dto.templo = rs.getString("templo");
                    dto.tempo_pratica = rs.getInt("tempo_pratica");
                    dto.usuario_coletor = rs.getString("usuario_coletor");
                    dto.data_entrevista = rs.getString("data_entrevista");
                    return dto;
                }
            }
        }
        return null;
    }

    public void atualizarEntrevistado(EntrevistadoDTO dto) throws SQLException {
        String sql = """
            UPDATE entrevistados SET
                nome = ?, idade = ?, sexo = ?, tradicao = ?, templo = ?,
                tempo_pratica = ?, usuario_coletor = ?, data_entrevista = ?, updated_at = datetime('now')
            WHERE id = ?
            """;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, dto.nome);
            pstmt.setInt(2, dto.idade);
            pstmt.setString(3, dto.sexo);
            pstmt.setString(4, dto.tradicao);
            pstmt.setString(5, dto.templo);
            pstmt.setInt(6, dto.tempo_pratica != null ? dto.tempo_pratica : 0);
            pstmt.setString(7, dto.usuario_coletor);
            pstmt.setString(8, dto.data_entrevista);
            pstmt.setInt(9, dto.id);
            pstmt.executeUpdate();
        }
    }

    public void atualizarEntrevistadoCloudId(int id, int cloudId) throws SQLException {
        String sql = "UPDATE entrevistados SET cloud_id = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, cloudId);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        }
    }

    public List<LeituraDTO> buscarLeiturasPorEntrevistado(int entrevistadoId) throws SQLException {
        String sql = """
            SELECT l.id AS leitura_id, l.cloud_id AS leitura_cloud_id,
                   l.entrevistado_id, e.cloud_id AS entrevistado_cloud_id,
                   e.nome, e.idade, e.sexo, e.tradicao, e.templo, e.tempo_pratica,
                   e.usuario_coletor, e.data_entrevista,
                   l.tipo_leitura, l.titulo_obra, l.autor, l.idioma_leitura,
                   l.frequencia_leitura, l.observacoes, l.created_at
            FROM leituras l
            JOIN entrevistados e ON e.id = l.entrevistado_id
            WHERE e.id = ?
            ORDER BY l.created_at DESC
            """;
        List<LeituraDTO> lista = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, entrevistadoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LeituraDTO dto = new LeituraDTO();
                    dto.id = rs.getInt("leitura_id");
                    dto.cloud_id = rs.getObject("leitura_cloud_id") != null ? rs.getInt("leitura_cloud_id") : null;
                    dto.entrevistado_id = rs.getInt("entrevistado_id");
                    dto.entrevistado_cloud_id = rs.getObject("entrevistado_cloud_id") != null ? rs.getInt("entrevistado_cloud_id") : null;
                    dto.nome_entrevistado = rs.getString("nome");
                    dto.idade = rs.getInt("idade");
                    dto.sexo = rs.getString("sexo");
                    dto.tradicao = rs.getString("tradicao");
                    dto.templo = rs.getString("templo");
                    dto.tempo_pratica = rs.getInt("tempo_pratica");
                    dto.usuario_coletor = rs.getString("usuario_coletor");
                    dto.data_entrevista = rs.getString("data_entrevista");
                    dto.tipo_leitura = rs.getString("tipo_leitura");
                    dto.titulo_obra = rs.getString("titulo_obra");
                    dto.autor = rs.getString("autor");
                    dto.idioma_leitura = rs.getString("idioma_leitura");
                    dto.frequencia_leitura = rs.getString("frequencia_leitura");
                    dto.observacoes = rs.getString("observacoes");
                    dto.created_at = rs.getString("created_at");
                    lista.add(dto);
                }
            }
        }
        return lista;
    }

    public void deletarEntrevistado(int id) throws SQLException {
        String sql = "DELETE FROM entrevistados WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public void removerEntrevistadoSemLeituras() throws SQLException {
        String sql = """
            DELETE FROM entrevistados
            WHERE id IN (
                SELECT e.id FROM entrevistados e
                LEFT JOIN leituras l ON l.entrevistado_id = e.id
                GROUP BY e.id
                HAVING COUNT(l.id) = 0
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public boolean possuiLeiturasParaEntrevistado(int entrevistadoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM leituras WHERE entrevistado_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, entrevistadoId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Fecha a conexão com o banco
     */
    public void fechar() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DatabaseManager] Conexão fechada");
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Erro ao fechar conexão: " + e.getMessage());
        }
    }
}
