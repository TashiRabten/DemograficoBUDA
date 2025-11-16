package com.buda.demografico;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente para comunicação com o Supabase
 * Gerencia operações CRUD na tabela leituras_budistas
 */
public class SupabaseClient {
    private static final String SUPABASE_URL = "https://cmfoaozuhvptlmojjqxx.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNtZm9hb3p1aHZwdGxtb2pqcXh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjMyMzc1MDksImV4cCI6MjA3ODgxMzUwOX0.1oqSLa9G1ZVOCj9Yh10O7G5-kI2Si4HSUmhgPU9iaWU";
    private static final String ENTREVISTADOS_TABLE = "entrevistados";
    private static final String LEITURAS_TABLE = "leituras";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final Gson gson;

    public SupabaseClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CLIENT_TIMEOUT)
                .build();
        this.gson = new Gson();
    }

    /**
     * Testa a conexão com o Supabase
     */
    public boolean testConnection() {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + LEITURAS_TABLE + "?limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            System.out.println("[SupabaseClient] Teste de conexão: " +
                             (success ? "✅ SUCESSO" : "❌ FALHOU") +
                             " (HTTP " + response.statusCode() + ")");
            return success;

        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao testar conexão: " + e.getMessage());
            return false;
        }
    }

    /**
     * Insere um novo registro de leitura no Supabase
     * @return ID gerado na nuvem ou null se falhar
     */
    public Integer inserirLeitura(LeituraDTO leitura, int entrevistadoCloudId) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + LEITURAS_TABLE;

            String json = gson.toJson(montarPayloadLeitura(leitura, entrevistadoCloudId));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                List<LeituraDTO> criadas = gson.fromJson(
                    response.body(),
                    new TypeToken<List<LeituraDTO>>(){}.getType()
                );
                if (criadas != null && !criadas.isEmpty() && criadas.get(0).id != null) {
                    System.out.println("[SupabaseClient] ✅ Leitura inserida com sucesso (cloud_id=" + criadas.get(0).id + ")");
                    return criadas.get(0).id;
                }
                return null;
            }

            if (response.statusCode() == 409) {
                System.out.println("[SupabaseClient] ℹ️ Registro duplicado detectado, buscando leitura existente...");
                LeituraDTO existente = buscarLeituraPorChave(entrevistadoCloudId, leitura);
                if (existente != null && existente.id != null) {
                    atualizarLeitura(existente.id, leitura, entrevistadoCloudId);
                    return existente.id;
                }
            }

            System.err.println("[SupabaseClient] ❌ Falha ao inserir: HTTP " +
                                 response.statusCode() + " - " + response.body());
            throw new Exception("Falha ao inserir leitura: HTTP " + response.statusCode());

        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao inserir leitura: " + e.getMessage());
            throw e;
        }
    }

    public boolean atualizarLeitura(int cloudId, LeituraDTO leitura, int entrevistadoCloudId) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + LEITURAS_TABLE + "?id=eq." + cloudId;
            String json = gson.toJson(montarPayloadLeitura(leitura, entrevistadoCloudId));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .timeout(REQUEST_TIMEOUT)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("[SupabaseClient] ✅ Leitura " + cloudId + " atualizada");
                return true;
            }
            throw new Exception("Falha ao atualizar leitura: HTTP " + response.statusCode());
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao atualizar leitura: " + e.getMessage());
            throw e;
        }
    }

    public boolean deletarLeitura(int cloudId) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + LEITURAS_TABLE + "?id=eq." + cloudId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("[SupabaseClient] ✅ Leitura " + cloudId + " deletada");
                return true;
            }
            throw new Exception("Falha ao deletar leitura: HTTP " + response.statusCode());
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao deletar leitura: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Busca todas as leituras
     */
    public List<LeituraDTO> buscarTodasLeituras() throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + LEITURAS_TABLE + "?select=*&order=created_at.desc";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<LeituraDTO> leituras = gson.fromJson(
                    response.body(),
                    new TypeToken<List<LeituraDTO>>(){}.getType()
                );
                System.out.println("[SupabaseClient] ✅ Buscadas " + leituras.size() + " leituras");
                return leituras;
            } else {
                throw new Exception("Falha ao buscar leituras: HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao buscar leituras: " + e.getMessage());
            throw e;
        }
    }

    public List<LeituraDTO> buscarLeiturasPorEntrevistadoCloud(int entrevistadoCloudId) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + LEITURAS_TABLE + "?select=*&entrevistado_id=eq." + entrevistadoCloudId;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return gson.fromJson(response.body(), new TypeToken<List<LeituraDTO>>(){}.getType());
            }
            throw new Exception("Falha ao buscar leituras da pessoa: HTTP " + response.statusCode());
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao buscar leituras do entrevistado: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Conta o total de leituras
     */
    public int contarLeituras() throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + LEITURAS_TABLE + "?select=id";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Prefer", "count=exact")
                    .timeout(REQUEST_TIMEOUT)
                    .HEAD()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String contentRange = response.headers().firstValue("content-range").orElse("0-0/0");
            String[] parts = contentRange.split("/");
            int count = Integer.parseInt(parts[parts.length - 1]);

            System.out.println("[SupabaseClient] Total de leituras: " + count);
            return count;

        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao contar leituras: " + e.getMessage());
            return 0;
        }
    }

    public int contarEntrevistados() throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + ENTREVISTADOS_TABLE + "?select=id";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Prefer", "count=exact")
                .timeout(REQUEST_TIMEOUT)
                .HEAD()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String contentRange = response.headers().firstValue("content-range").orElse("0-0/0");
            String[] parts = contentRange.split("/");
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao contar entrevistados: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Busca estatísticas por tipo de leitura
     */
    public Map<String, Integer> buscarEstatisticasPorTipo() throws Exception {
        try {
            List<LeituraDTO> todas = buscarTodasLeituras();
            Map<String, Integer> estatisticas = new HashMap<>();

            for (LeituraDTO l : todas) {
                estatisticas.merge(l.tipo_leitura, 1, Integer::sum);
            }

            return estatisticas;
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao buscar estatísticas: " + e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> montarPayloadLeitura(LeituraDTO leitura, int entrevistadoCloudId) {
        Map<String, Object> data = new HashMap<>();
        data.put("entrevistado_id", entrevistadoCloudId);
        data.put("tipo_leitura", leitura.tipo_leitura);
        data.put("titulo_obra", leitura.titulo_obra);
        data.put("autor", leitura.autor);
        data.put("idioma_leitura", leitura.idioma_leitura);
        data.put("frequencia_leitura", leitura.frequencia_leitura);
        data.put("observacoes", leitura.observacoes);
        return data;
    }

    private LeituraDTO buscarLeituraPorChave(int entrevistadoCloudId, LeituraDTO leitura) throws Exception {
        List<LeituraDTO> leituras = buscarLeiturasPorEntrevistadoCloud(entrevistadoCloudId);
        String tipo = normalizar(leitura.tipo_leitura);
        String titulo = normalizar(leitura.titulo_obra);
        String autor = normalizar(leitura.autor);

        for (LeituraDTO dto : leituras) {
            if (normalizar(dto.tipo_leitura).equals(tipo)
                && normalizar(dto.titulo_obra).equals(titulo)
                && normalizar(dto.autor).equals(autor)) {
                return dto;
            }
        }
        return null;
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    public Integer inserirEntrevistado(EntrevistadoDTO entrevistado) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + ENTREVISTADOS_TABLE;
            String json = gson.toJson(montarPayloadEntrevistado(entrevistado));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                List<EntrevistadoDTO> criados = gson.fromJson(
                    response.body(),
                    new TypeToken<List<EntrevistadoDTO>>(){}.getType()
                );
                if (criados != null && !criados.isEmpty() && criados.get(0).id != null) {
                    return criados.get(0).id;
                }
            } else {
                throw new Exception("Falha ao criar entrevistado: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao inserir entrevistado: " + e.getMessage());
            throw e;
        }
        return null;
    }

    public boolean atualizarEntrevistado(int cloudId, EntrevistadoDTO entrevistado) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + ENTREVISTADOS_TABLE + "?id=eq." + cloudId;
            String json = gson.toJson(montarPayloadEntrevistado(entrevistado));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            throw new Exception("Falha ao atualizar entrevistado: HTTP " + response.statusCode());
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao atualizar entrevistado: " + e.getMessage());
            throw e;
        }
    }

    public boolean deletarEntrevistado(int cloudId) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + ENTREVISTADOS_TABLE + "?id=eq." + cloudId;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .timeout(REQUEST_TIMEOUT)
                .DELETE()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao deletar entrevistado: " + e.getMessage());
            throw e;
        }
    }

    public boolean deletarLeiturasPorEntrevistado(int entrevistadoCloudId) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + LEITURAS_TABLE + "?entrevistado_id=eq." + entrevistadoCloudId;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .timeout(REQUEST_TIMEOUT)
                .DELETE()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Erro ao deletar leituras do entrevistado: " + e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> montarPayloadEntrevistado(EntrevistadoDTO entrevistado) {
        Map<String, Object> data = new HashMap<>();
        data.put("nome", entrevistado.nome);
        data.put("idade", entrevistado.idade);
        data.put("sexo", entrevistado.sexo);
        data.put("tradicao", entrevistado.tradicao);
        data.put("templo", entrevistado.templo);
        data.put("tempo_pratica", entrevistado.tempo_pratica);
        data.put("usuario_coletor", entrevistado.usuario_coletor);
        data.put("data_entrevista", entrevistado.data_entrevista);
        return data;
    }
}
