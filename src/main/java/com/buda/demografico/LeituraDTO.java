package com.buda.demografico;

/**
 * Data Transfer Object para dados sobre leituras budistas
 * Coleta informações sobre o que praticantes budistas leem
 */
public class LeituraDTO {
    public Integer id;
    public Integer cloud_id;
    public Integer entrevistado_id;
    public Integer entrevistado_cloud_id;
    public String nome_entrevistado;
    public Integer idade;
    public String sexo;
    public String tradicao;
    public String templo;
    public Integer tempo_pratica;

    // Campos específicos sobre leitura
    public String tipo_leitura;        // Livros, Revistas, Tratados, Sutras, etc.
    public String titulo_obra;         // Título do que está lendo
    public String autor;               // Autor da obra
    public String idioma_leitura;      // Português, Tibetano, Inglês, etc.
    public String frequencia_leitura;  // Diária, Semanal, Mensal, etc.

    public String observacoes;
    public String usuario_coletor;
    public String data_entrevista;
    public String created_at;
    public String updated_at;

    public LeituraDTO() {
    }

    public LeituraDTO(String nomeEntrevistado, Integer idade, String sexo,
                     String tradicao, String templo, Integer tempoPratica,
                     String tipoLeitura, String tituloObra, String autor,
                     String idiomaLeitura, String frequenciaLeitura,
                     String observacoes, String usuarioColetor) {
        this.nome_entrevistado = nomeEntrevistado;
        this.idade = idade;
        this.sexo = sexo;
        this.tradicao = tradicao;
        this.templo = templo;
        this.tempo_pratica = tempoPratica;
        this.tipo_leitura = tipoLeitura;
        this.titulo_obra = tituloObra;
        this.autor = autor;
        this.idioma_leitura = idiomaLeitura;
        this.frequencia_leitura = frequenciaLeitura;
        this.observacoes = observacoes;
        this.usuario_coletor = usuarioColetor;
    }

    // Getters para JavaFX PropertyValueFactory
    public Integer getId() { return id; }
    public Integer getCloud_id() { return cloud_id; }
    public Integer getEntrevistado_id() { return entrevistado_id; }
    public Integer getEntrevistado_cloud_id() { return entrevistado_cloud_id; }
    public String getNome_entrevistado() { return nome_entrevistado; }
    public Integer getIdade() { return idade; }
    public String getSexo() { return sexo; }
    public String getTradicao() { return tradicao; }
    public String getTemplo() { return templo; }
    public Integer getTempo_pratica() { return tempo_pratica; }
    public String getTipo_leitura() { return tipo_leitura; }
    public String getTitulo_obra() { return titulo_obra; }
    public String getAutor() { return autor; }
    public String getIdioma_leitura() { return idioma_leitura; }
    public String getFrequencia_leitura() { return frequencia_leitura; }
    public String getObservacoes() { return observacoes; }
    public String getUsuario_coletor() { return usuario_coletor; }
    public String getCreated_at() { return created_at; }

    @Override
    public String toString() {
        return "LeituraDTO{" +
                "id=" + id +
                ", nome='" + nome_entrevistado + '\'' +
                ", idade=" + idade +
                ", tipo_leitura='" + tipo_leitura + '\'' +
                ", titulo='" + titulo_obra + '\'' +
                ", autor='" + autor + '\'' +
                ", idioma='" + idioma_leitura + '\'' +
                '}';
    }
}
