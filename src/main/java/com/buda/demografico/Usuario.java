package com.buda.demografico;

/**
 * Modelo de usuário para login local
 */
public class Usuario {
    private String nomeUsuario;
    private String nomeCompleto;
    private String email;
    private boolean logado;

    public Usuario() {
    }

    public Usuario(String nomeUsuario, String nomeCompleto, String email) {
        this.nomeUsuario = nomeUsuario;
        this.nomeCompleto = nomeCompleto;
        this.email = email;
        this.logado = false;
    }

    public String getNomeUsuario() {
        return nomeUsuario;
    }

    public void setNomeUsuario(String nomeUsuario) {
        this.nomeUsuario = nomeUsuario;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public void setNomeCompleto(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isLogado() {
        return logado;
    }

    public void setLogado(boolean logado) {
        this.logado = logado;
    }

    @Override
    public String toString() {
        return "Usuario{" +
                "nomeUsuario='" + nomeUsuario + '\'' +
                ", nomeCompleto='" + nomeCompleto + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
