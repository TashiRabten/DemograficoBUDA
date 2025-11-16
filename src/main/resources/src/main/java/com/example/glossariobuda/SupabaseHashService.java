package com.example.glossariobuda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Service responsible for hash-related operations with Supabase.
 * Handles content hash generation, validation, and batch hash checking.
 */
public class SupabaseHashService {
    private static final String SUPABASE_URL = "https://ctbufeavhuypjdbqmwzb.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN0YnVmZWF2aHV5cGpkYnFtd3piIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAyODQ2MzYsImV4cCI6MjA3NTg2MDYzNn0.lkaQWS8u2QisnRENtOXKDAMbPwhRV1jS4euBDc1TUDg";
    private static final String TABLE_NAME = "glossario_terms";  // Changed from "terms" for exclusive access
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final Gson gson;

    public SupabaseHashService(HttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    /**
     * Generate SHA-256 hash for term content.
     * Static method for use across the application.
     */
    public static String generateHash(String sourceTerm, String sourceLanguage,
                                      String targetTerm, String targetLanguage,
                                      String context, String contributor) {
        try {
            String content = (sourceTerm != null ? sourceTerm : "") + "|" +
                    (sourceLanguage != null ? sourceLanguage : "") + "|" +
                    (targetTerm != null ? targetTerm : "") + "|" +
                    (targetLanguage != null ? targetLanguage : "") + "|" +
                    (context != null ? context : "") + "|" +
                    (contributor != null ? contributor : "");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[SupabaseHashService] Error generating hash: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Check if a hash exists in the database.
     */
    public boolean hashExists(String hash) throws SupabaseHashException {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?content_hash=eq." + hash + "&select=content_hash";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> results = gson.fromJson(
                        response.body(),
                        new TypeToken<List<Map<String, Object>>>(){}.getType()
                );
                return !results.isEmpty();
            } else {
                throw new SupabaseHashException("Hash check failed: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            throw new SupabaseHashException("Error checking hash existence: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a term with the given hash exists.
     * Alias for hashExists() for semantic clarity.
     */
    public boolean termExistsByHash(String hash) throws SupabaseHashException {
        return hashExists(hash);
    }

    /**
     * Get ALL term hashes from the database (select-only query).
     * Returns hashes with their associated updated_at timestamps.
     */
    public Set<String> getAllTermHashes() {
        Set<String> allHashes = new HashSet<>();

        try {
            int offset = 0;
            int limit = 1000;
            boolean hasMore = true;

            while (hasMore) {
                String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?select=content_hash&limit=" + limit + "&offset=" + offset;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Map<String, Object>> results = gson.fromJson(
                            response.body(),
                            new TypeToken<List<Map<String, Object>>>(){}.getType()
                    );

                    for (Map<String, Object> result : results) {
                        String hash = (String) result.get("content_hash");
                        if (hash != null && !hash.isEmpty()) {
                            allHashes.add(hash);
                        }
                    }

                    if (results.size() < limit) {
                        hasMore = false;
                    } else {
                        offset += limit;
                        System.out.println("[SupabaseHashService] Fetched " + allHashes.size() + " hashes so far...");
                    }
                } else {
                    System.err.println("[SupabaseHashService] Failed to fetch hashes: HTTP " + response.statusCode());
                    break;
                }
            }

            System.out.println("[SupabaseHashService] Total hashes retrieved: " + allHashes.size());
        } catch (Exception e) {
            System.err.println("[SupabaseHashService] Error fetching hashes: " + e.getMessage());
            e.printStackTrace();
        }

        return allHashes;
    }

    /**
     * Get ALL existing hashes from cloud in one efficient query.
     * Much faster than checking each hash individually.
     *
     * @return Set of all content hashes currently in cloud
     */
    public Set<String> getAllExistingHashes() throws SupabaseHashException {
        Set<String> allHashes = new HashSet<>();
        int offset = 0;
        int limit = 1000;
        boolean hasMore = true;

        System.out.println("[SupabaseHashService] Fetching all existing hashes from cloud...");

        try {
            while (hasMore) {
                String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?select=content_hash&limit=" + limit + "&offset=" + offset;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Map<String, Object>> results = gson.fromJson(
                            response.body(),
                            new TypeToken<List<Map<String, Object>>>(){}.getType()
                    );

                    for (Map<String, Object> result : results) {
                        String hash = (String) result.get("content_hash");
                        if (hash != null && !hash.isEmpty()) {
                            allHashes.add(hash);
                        }
                    }

                    if (results.size() < limit) {
                        hasMore = false;
                    } else {
                        offset += limit;
                        System.out.println("[SupabaseHashService] Fetched " + allHashes.size() + " hashes so far...");
                    }
                } else {
                    throw new SupabaseHashException("Failed to fetch hashes: HTTP " + response.statusCode());
                }
            }

            System.out.println("[SupabaseHashService] Total hashes in cloud: " + allHashes.size());
        } catch (Exception e) {
            throw new SupabaseHashException("Error fetching all existing hashes: " + e.getMessage(), e);
        }

        return allHashes;
    }

    /**
     * Check which hashes already exist in database (batch query).
     * Queries in batches to avoid URL length limits.
     */
    public Set<String> getExistingHashes(List<String> hashes) throws SupabaseHashException {
        if (hashes == null || hashes.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> existingHashes = new HashSet<>();

        try {
            // Query in batches to avoid URL length limits
            int batchSize = 100;
            for (int i = 0; i < hashes.size(); i += batchSize) {
                int end = Math.min(i + batchSize, hashes.size());
                List<String> batch = hashes.subList(i, end);

                // Build IN query: content_hash.in.(hash1,hash2,...)
                String hashList = String.join(",", batch);
                String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?select=content_hash&content_hash=in.(" + hashList + ")";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Map<String, Object>> results = gson.fromJson(
                            response.body(),
                            new TypeToken<List<Map<String, Object>>>(){}.getType()
                    );

                    for (Map<String, Object> result : results) {
                        existingHashes.add((String) result.get("content_hash"));
                    }
                }

                if ((i + batchSize) % 1000 == 0) {
                    System.out.println("[SupabaseHashService] Checked " + (i + batchSize) + "/" + hashes.size() + " hashes...");
                }
            }
        } catch (Exception e) {
            throw new SupabaseHashException("Error checking existing hashes: " + e.getMessage(), e);
        }

        return existingHashes;
    }

    /**
     * Custom exception for hash-related operations
     */
    public static class SupabaseHashException extends Exception {
        public SupabaseHashException(String message) {
            super(message);
        }

        public SupabaseHashException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
