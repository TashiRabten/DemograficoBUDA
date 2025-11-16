package com.buda.demografico;

/**
 * Representa os dados demográficos do entrevistado
 */
import java.util.ArrayList;
import java.util.List;

public class EntrevistadoDTO {
    public Integer id;
    public Integer cloud_id;
    public String nome;
    public Integer idade;
    public String sexo;
    public String tradicao;
    public String templo;
    public Integer tempo_pratica;
    public String usuario_coletor;
    public String data_entrevista;
    public List<LeituraDTO> leituras = new ArrayList<>();

    public int getTotalLeituras() {
        return leituras.size();
    }

    public String getResumoLeituras() {
        if (leituras.isEmpty()) {
            return "Sem leituras";
        }
        List<String> titulos = leituras.stream()
            .map(l -> l.titulo_obra != null && !l.titulo_obra.isBlank() ? l.titulo_obra : "Sem título")
            .limit(3)
            .toList();
        String resumo = String.join(", ", titulos);
        if (leituras.size() > 3) {
            resumo += " +" + (leituras.size() - 3);
        }
        return resumo;
    }

    public EntrevistadoDTO() {}

    public EntrevistadoDTO copy() {
        EntrevistadoDTO c = new EntrevistadoDTO();
        c.id = this.id;
        c.cloud_id = this.cloud_id;
        c.nome = this.nome;
        c.idade = this.idade;
        c.sexo = this.sexo;
        c.tradicao = this.tradicao;
        c.templo = this.templo;
        c.tempo_pratica = this.tempo_pratica;
        c.usuario_coletor = this.usuario_coletor;
        c.data_entrevista = this.data_entrevista;
        c.leituras = new ArrayList<>(this.leituras);
        return c;
    }
}
