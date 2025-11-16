package com.example.glossariobuda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for basic CRUD operations with Supabase.
 * Handles create, read, update, and delete operations for individual terms.
 */
public class SupabaseCRUDService {
    private static final String SUPABASE_URL = "https://ctbufeavhuypjdbqmwzb.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN0YnVmZWF2aHV5cGpkYnFtd3piIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAyODQ2MzYsImV4cCI6MjA3NTg2MDYzNn0.lkaQWS8u2QisnRENtOXKDAMbPwhRV1jS4euBDc1TUDg";
    private static final String TABLE_NAME = "glossario_terms";  // Changed from "terms" for exclusive access
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final Gson gson;

    public SupabaseCRUDService(HttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    /**
     * Insert a term into Supabase.
     */
    public boolean insertTerm(SupabaseClient.TermDTO term) throws SupabaseCRUDException {
        try {
            String json = gson.toJson(term);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/rest/v1/" + TABLE_NAME))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                return true;
            } else if (response.statusCode() == 409) {
                System.out.println("[SupabaseCRUD] Duplicate term detected (hash exists)");
                return false;
            } else {
                throw new SupabaseCRUDException("Insert failed: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            throw new SupabaseCRUDException("Error inserting term: " + e.getMessage(), e);
        }
    }

    /**
     * Get a term by its content hash.
     */
    public SupabaseClient.TermDTO getTermByHash(String hash) throws SupabaseCRUDException {
        try {
            String encodedHash = URLEncoder.encode(hash, StandardCharsets.UTF_8);
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?content_hash=eq." + encodedHash + "&select=*&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> rawList = gson.fromJson(
                        response.body(),
                        new TypeToken<List<Map<String, Object>>>(){}.getType()
                );

                if (!rawList.isEmpty()) {
                    return mapToTermDTO(rawList.get(0));
                }
            }

            return null;
        } catch (Exception e) {
            throw new SupabaseCRUDException("Error getting term by hash: " + e.getMessage(), e);
        }
    }

    /**
     * Find a term by source and target content.
     */
    public SupabaseClient.TermDTO findTermByContent(String sourceTerm, String targetTerm) throws SupabaseCRUDException {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?source_term=eq." +
                    URLEncoder.encode(sourceTerm, StandardCharsets.UTF_8) +
                    "&target_term=eq." + URLEncoder.encode(targetTerm, StandardCharsets.UTF_8) +
                    "&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> rawList = gson.fromJson(
                        response.body(),
                        new TypeToken<List<Map<String, Object>>>(){}.getType()
                );

                if (!rawList.isEmpty()) {
                    return mapToTermDTO(rawList.get(0));
                }
            }
            return null;
        } catch (Exception e) {
            throw new SupabaseCRUDException("Error finding term by content: " + e.getMessage(), e);
        }
    }

    /**
     * Update a term by its content hash.
     */
    public boolean updateTermByHash(String hash, SupabaseClient.TermDTO term) throws SupabaseCRUDException {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?content_hash=eq." + hash;

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            conn.setRequestProperty("apikey", SUPABASE_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
            conn.setDoOutput(true);

            JSONObject json = new JSONObject();
            json.put("source_term", term.source_term);
            json.put("source_language", term.source_language);
            json.put("target_term", term.target_term);
            json.put("target_language", term.target_language);
            json.put("context", term.context != null ? term.context : JSONObject.NULL);
            json.put("contributor", term.contributor != null ? term.contributor : JSONObject.NULL);
            json.put("notes", term.notes != null ? term.notes : JSONObject.NULL);
            json.put("verified_status", term.verified_status);
            json.put("owner", term.owner != null ? term.owner : "shared");
            json.put("date_added", term.date_added);
            json.put("updated_at", Instant.now().toString());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            System.out.println("[SupabaseCRUD] Update response: " + responseCode);

            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            throw new SupabaseCRUDException("Error updating term: " + e.getMessage(), e);
        }
    }

    /**
     * Update term by cloud ID (more efficient for edits than DELETE+INSERT).
     * Use this when you have the cloud_id from the local database.
     */
    public boolean updateTermById(int cloudId, SupabaseClient.TermDTO term) throws SupabaseCRUDException {
        try {
            // Build JSON payload
            JSONObject json = new JSONObject();
            json.put("source_term", term.source_term);
            json.put("source_language", term.source_language);
            json.put("target_term", term.target_term);
            json.put("target_language", term.target_language);
            json.put("context", term.context != null ? term.context : JSONObject.NULL);
            json.put("contributor", term.contributor != null ? term.contributor : JSONObject.NULL);
            json.put("notes", term.notes != null ? term.notes : JSONObject.NULL);
            json.put("verified_status", term.verified_status);
            json.put("content_hash", term.content_hash);
            json.put("owner", term.owner != null ? term.owner : "shared");
            json.put("date_added", term.date_added);
            json.put("updated_at", Instant.now().toString());

            // Use HttpClient with proper PATCH method
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?id=eq." + cloudId;

            System.out.println("[SupabaseCRUD] 🔍 DEBUG: PATCH URL: " + url);
            System.out.println("[SupabaseCRUD] 🔍 DEBUG: Payload: " + json.toString());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .timeout(REQUEST_TIMEOUT)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();
            System.out.println("[SupabaseCRUD] 🔍 DEBUG: Response status: " + statusCode);
            System.out.println("[SupabaseCRUD] 🔍 DEBUG: Response body: " + responseBody);

            // Check if update was successful
            if (statusCode == 200 || statusCode == 204) {
                // 204 No Content = success but no body returned
                if (statusCode == 204) {
                    System.out.println("[SupabaseCRUD] ✅ PATCH successful (204 No Content)");
                    return true;
                }

                // 200 OK = success with body - check if rows were updated
                if (responseBody != null && !responseBody.trim().isEmpty() && !responseBody.equals("[]")) {
                    System.out.println("[SupabaseCRUD] ✅ PATCH successful (200 OK)");
                    return true;
                }

                System.err.println("[SupabaseCRUD] ⚠️ PATCH returned 200 but no rows updated for cloud_id=" + cloudId);
                return false;
            }

            // 201 Created means the record didn't exist and Supabase created a new one (upsert behavior)
            if (statusCode == 201) {
                System.err.println("[SupabaseCRUD] ⚠️ PATCH returned 201 Created - this means record didn't exist!");
                System.err.println("[SupabaseCRUD] Response: " + responseBody);
                return false;
            }

            System.err.println("[SupabaseCRUD] ❌ PATCH failed with status: " + statusCode);
            return false;

        } catch (Exception e) {
            throw new SupabaseCRUDException("Error updating term by ID: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a term by its content hash.
     */
    public boolean deleteTermByHash(String hash) throws SupabaseCRUDException {
        try {
            String encodedHash = URLEncoder.encode(hash, StandardCharsets.UTF_8);
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?content_hash=eq." + encodedHash;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Prefer", "return=representation")
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if ((response.statusCode() == 204 || response.statusCode() == 200) &&
                    (response.body() == null || response.body().trim().equals("[]"))) {
                System.err.println("[SupabaseCRUD] No rows deleted for hash: " + hash);
                return false;
            }

            return response.statusCode() == 204 || response.statusCode() == 200;
        } catch (Exception e) {
            throw new SupabaseCRUDException("Error deleting term: " + e.getMessage(), e);
        }
    }

    /**
     * Upsert a term (insert if new, update if exists).
     * Perfect for admin reset where we want to replace existing data.
     */
    public boolean upsertTerm(SupabaseClient.TermDTO term) throws SupabaseCRUDException {
        try {
            String json = gson.toJson(term);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/rest/v1/" + TABLE_NAME))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 201 || response.statusCode() == 200;
        } catch (Exception e) {
            throw new SupabaseCRUDException("Error upserting term: " + e.getMessage(), e);
        }
    }

    /**
     * Map raw data to TermDTO.
     */
    private SupabaseClient.TermDTO mapToTermDTO(Map<String, Object> map) {
        SupabaseClient.TermDTO term = new SupabaseClient.TermDTO();

        Object idObj = map.get("id");
        if (idObj instanceof Double) {
            term.id = ((Double) idObj).intValue();
        } else if (idObj instanceof Integer) {
            term.id = (Integer) idObj;
        }

        term.source_term = (String) map.get("source_term");
        term.source_language = (String) map.get("source_language");
        term.target_term = (String) map.get("target_term");
        term.target_language = (String) map.get("target_language");
        term.context = (String) map.get("context");
        term.contributor = (String) map.get("contributor");
        term.date_added = (String) map.get("date_added");
        term.verified_status = (String) map.get("verified_status");
        term.notes = (String) map.get("notes");
        term.content_hash = (String) map.get("content_hash");
        term.updated_at = (String) map.get("updated_at");
        term.owner = (String) map.get("owner");

        return term;
    }

    /**
     * Custom exception for CRUD operations.
     */
    public static class SupabaseCRUDException extends Exception {
        public SupabaseCRUDException(String message) {
            super(message);
        }

        public SupabaseCRUDException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
