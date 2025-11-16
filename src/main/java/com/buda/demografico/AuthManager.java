package com.buda.demografico;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.prefs.Preferences;

/**
 * Gerenciador de autenticação local
 * Armazena perfis de usuário localmente e gerencia sessão
 */
public class AuthManager {
    private static final Path STORAGE_DIR = Paths.get(System.getProperty("user.home"), "Documents", "DemograficoBUDA");
    private static final Path DB_PATH = STORAGE_DIR.resolve("demografico_users.db");
    private Connection connection;
    private Usuario usuarioAtual;
    private Preferences prefs;

    public AuthManager() {
        prefs = Preferences.userNodeForPackage(AuthManager.class);
        try {
            prepararDiretorio();
            String url = "jdbc:sqlite:" + DB_PATH.toAbsolutePath();
            connection = DriverManager.getConnection(url);
            criarTabelaUsuarios();
            System.out.println("[AuthManager] ✅ Sistema de autenticação inicializado");
        } catch (SQLException e) {
            System.err.println("[AuthManager] ❌ Erro ao inicializar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void prepararDiretorio() throws SQLException {
        try {
            if (!Files.exists(STORAGE_DIR)) {
                Files.createDirectories(STORAGE_DIR);
            }
            Path legado = Paths.get("demografico_users.db");
            if (Files.exists(legado) && !Files.exists(DB_PATH)) {
                Files.copy(legado, DB_PATH, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            throw new SQLException("Não foi possível preparar diretório de autenticação em " + STORAGE_DIR, e);
        }
    }

    private void criarTabelaUsuarios() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS usuarios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome_usuario TEXT UNIQUE NOT NULL,
                nome_completo TEXT NOT NULL,
                email TEXT,
                ultimo_login TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            );
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Registra ou atualiza um usuário local
     */
    public boolean registrarUsuario(String nomeUsuario, String nomeCompleto, String email) throws SQLException {
        String sql = """
            INSERT INTO usuarios (nome_usuario, nome_completo, email)
            VALUES (?, ?, ?)
            ON CONFLICT(nome_usuario) DO UPDATE SET
                nome_completo = excluded.nome_completo,
                email = excluded.email
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nomeUsuario.trim().toLowerCase());
            pstmt.setString(2, nomeCompleto);
            pstmt.setString(3, email);
            pstmt.executeUpdate();
            System.out.println("[AuthManager] ✅ Usuário registrado: " + nomeUsuario);
            return true;
        }
    }

    /**
     * Faz login local (sem senha por enquanto - sistema simples)
     */
    public boolean login(String nomeUsuario) throws SQLException {
        String sql = """
            SELECT nome_usuario, nome_completo, email
            FROM usuarios
            WHERE nome_usuario = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nomeUsuario.trim().toLowerCase());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    usuarioAtual = new Usuario(
                        rs.getString("nome_usuario"),
                        rs.getString("nome_completo"),
                        rs.getString("email")
                    );
                    usuarioAtual.setLogado(true);

                    // Atualizar último login
                    atualizarUltimoLogin(nomeUsuario);

                    // Salvar sessão
                    salvarSessao(nomeUsuario);

                    System.out.println("[AuthManager] ✅ Login bem-sucedido: " + usuarioAtual.getNomeCompleto());
                    return true;
                }
            }
        }

        System.out.println("[AuthManager] ❌ Usuário não encontrado: " + nomeUsuario);
        return false;
    }

    /**
     * Faz logout
     */
    public void logout() {
        usuarioAtual = null;
        prefs.remove("ultimo_usuario");
        System.out.println("[AuthManager] Logout realizado");
    }

    /**
     * Retorna o usuário atual logado
     */
    public Usuario getUsuarioAtual() {
        return usuarioAtual;
    }

    /**
     * Verifica se há usuário logado
     */
    public boolean isLogado() {
        return usuarioAtual != null && usuarioAtual.isLogado();
    }

    /**
     * Salva a sessão do usuário
     */
    private void salvarSessao(String nomeUsuario) {
        prefs.put("ultimo_usuario", nomeUsuario);
    }

    /**
     * Recupera último usuário logado
     */
    public String getUltimoUsuario() {
        return prefs.get("ultimo_usuario", null);
    }

    /**
     * Tenta restaurar sessão anterior
     */
    public boolean restaurarSessao() {
        String ultimoUsuario = getUltimoUsuario();
        if (ultimoUsuario != null) {
            try {
                return login(ultimoUsuario);
            } catch (SQLException e) {
                System.err.println("[AuthManager] Erro ao restaurar sessão: " + e.getMessage());
            }
        }
        return false;
    }

    private void atualizarUltimoLogin(String nomeUsuario) throws SQLException {
        String sql = "UPDATE usuarios SET ultimo_login = datetime('now') WHERE nome_usuario = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nomeUsuario);
            pstmt.executeUpdate();
        }
    }

    /**
     * Verifica se um usuário existe
     */
    public boolean usuarioExiste(String nomeUsuario) throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE nome_usuario = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nomeUsuario.trim().toLowerCase());

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Fecha a conexão
     */
    public void fechar() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[AuthManager] Conexão fechada");
            }
        } catch (SQLException e) {
            System.err.println("[AuthManager] Erro ao fechar conexão: " + e.getMessage());
        }
    }
}
