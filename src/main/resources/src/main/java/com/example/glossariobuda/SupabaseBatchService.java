package com.example.glossariobuda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for batch operations with Supabase.
 * Handles bulk inserts, updates, deletes, and upserts for improved performance.
 */
public class SupabaseBatchService {
    private static final String SUPABASE_URL = "https://ctbufeavhuypjdbqmwzb.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN0YnVmZWF2aHV5cGpkYnFtd3piIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAyODQ2MzYsImV4cCI6MjA3NTg2MDYzNn0.lkaQWS8u2QisnRENtOXKDAMbPwhRV1jS4euBDc1TUDg";
    private static final String TABLE_NAME = "glossario_terms";  // Changed from "terms" for exclusive access
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LONG_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final Gson gson;
    private final SupabaseHashService hashService;

    public SupabaseBatchService(HttpClient httpClient, Gson gson, SupabaseHashService hashService) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.hashService = hashService;
    }

    /**
     * Batch insert terms - inserts up to 500 terms in a single request.
     */
    public int batchInsertTerms(List<SupabaseClient.TermDTO> terms) throws SupabaseBatchException {
        if (terms == null || terms.isEmpty()) {
            return 0;
        }

        try {
            String json = gson.toJson(terms);
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=ignore-duplicates")
                    .timeout(LONG_REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                return terms.size();
            } else if (response.statusCode() == 409) {
                System.out.println("[SupabaseBatch] Some duplicates detected (409)");
                return terms.size(); // Optimistic - assume most were inserted
            } else {
                throw new SupabaseBatchException("Batch insert failed: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            throw new SupabaseBatchException("Error during batch insert: " + e.getMessage(), e);
        }
    }

    /**
     * Batch insert with duplicate filtering - checks all hashes in one query.
     */
    public int insertTermsBatchWithDuplicateCheck(List<SupabaseClient.TermDTO> terms) throws SupabaseBatchException {
        if (terms == null || terms.isEmpty()) {
            return 0;
        }

        try {
            System.out.println("[SupabaseBatch] Checking " + terms.size() + " terms for duplicates...");

            // Step 1: Get all hashes we want to insert
            List<String> hashesToCheck = terms.stream()
                    .map(t -> t.content_hash)
                    .collect(Collectors.toList());

            // Step 2: Check which hashes already exist (in ONE batch query)
            Set<String> existingHashes = hashService.getExistingHashes(hashesToCheck);

            System.out.println("[SupabaseBatch] Found " + existingHashes.size() + " existing hashes");

            // Step 3: Filter out duplicates
            List<SupabaseClient.TermDTO> termsToInsert = new ArrayList<>();
            for (SupabaseClient.TermDTO term : terms) {
                if (!existingHashes.contains(term.content_hash)) {
                    termsToInsert.add(term);
                }
            }

            System.out.println("[SupabaseBatch] Inserting " + termsToInsert.size() + " new terms (skipped " + existingHashes.size() + " duplicates)");

            if (termsToInsert.isEmpty()) {
                return 0;
            }

            // Step 4: Batch insert the filtered terms
            String json = gson.toJson(termsToInsert);

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
                System.out.println("[SupabaseBatch] Successfully inserted " + termsToInsert.size() + " terms");
                return termsToInsert.size();
            } else {
                throw new SupabaseBatchException("Batch insert failed: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            throw new SupabaseBatchException("Error during batch insert with duplicate check: " + e.getMessage(), e);
        }
    }

    /**
     * Batch UPSERT for admin reset - updates ALL matching hashes regardless of date.
     */
    public boolean upsertTermsBatch(List<SupabaseClient.TermDTO> terms) throws SupabaseBatchException {
        if (terms == null || terms.isEmpty()) {
            return true;
        }

        try {
            System.out.println("[SupabaseBatch] Upserting " + terms.size() + " terms (will update existing hashes)");

            int inserted = 0;
            int updated = 0;
            int failed = 0;

            for (SupabaseClient.TermDTO term : terms) {
                try {
                    if (hashService.termExistsByHash(term.content_hash)) {
                        // UPDATE existing term by hash
                        SupabaseCRUDService crudService = new SupabaseCRUDService(httpClient, gson);
                        if (crudService.updateTermByHash(term.content_hash, term)) {
                            updated++;
                        } else {
                            failed++;
                        }
                    } else {
                        // INSERT new term
                        SupabaseCRUDService crudService = new SupabaseCRUDService(httpClient, gson);
                        if (crudService.insertTerm(term)) {
                            inserted++;
                        } else {
                            failed++;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[SupabaseBatch] Error upserting term " + term.source_term + ": " + e.getMessage());
                    failed++;
                }
            }

            System.out.println("[SupabaseBatch] Upsert complete: " + inserted + " inserted, " + updated + " updated, " + failed + " failed");
            return (inserted + updated) > 0;
        } catch (Exception e) {
            throw new SupabaseBatchException("Error during batch upsert: " + e.getMessage(), e);
        }
    }

    /**
     * Delete all terms in a date range with a single batch operation.
     * MUCH faster than deleting one by one.
     */
    public int deleteTermsByDateRange(String startDate, String endDate) throws SupabaseBatchException {
        try {
            StringBuilder queryBuilder = new StringBuilder();

            if (startDate != null && endDate != null) {
                queryBuilder.append("date_added=gte.").append(startDate)
                        .append("&date_added=lte.").append(endDate);
            } else if (startDate != null) {
                queryBuilder.append("date_added=gte.").append(startDate);
            } else if (endDate != null) {
                queryBuilder.append("date_added=lte.").append(endDate);
            } else {
                throw new IllegalArgumentException("At least one date (start or end) must be provided");
            }

            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?" + queryBuilder.toString();

            System.out.println("[SupabaseBatch] Batch deleting terms in date range");
            System.out.println("[SupabaseBatch]   Start: " + startDate);
            System.out.println("[SupabaseBatch]   End: " + endDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Prefer", "return=representation")
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204 || response.statusCode() == 200) {
                String responseBody = response.body();

                if (responseBody != null && !responseBody.trim().isEmpty() && !responseBody.equals("[]")) {
                    List<Map<String, Object>> deletedRecords = gson.fromJson(
                            responseBody,
                            new TypeToken<List<Map<String, Object>>>(){}.getType()
                    );
                    int count = deletedRecords != null ? deletedRecords.size() : 0;
                    System.out.println("[SupabaseBatch] Batch deleted " + count + " terms");
                    return count;
                } else {
                    System.out.println("[SupabaseBatch] Batch delete returned 204 (no body)");
                    return -1;
                }
            } else if (response.statusCode() == 409) {
                System.err.println("[SupabaseBatch] Conflict - no rows matched the criteria");
                return 0;
            } else {
                throw new SupabaseBatchException("Batch delete failed: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            throw new SupabaseBatchException("Error during batch delete: " + e.getMessage(), e);
        }
    }

    /**
     * Batch update dates for multiple terms at once.
     */
    public int batchUpdateTermDatesByHash(Map<String, String> hashToDateMap) throws SupabaseBatchException {
        if (hashToDateMap == null || hashToDateMap.isEmpty()) {
            return 0;
        }

        try {
            System.out.println("[SupabaseBatch] Batch updating " + hashToDateMap.size() + " term dates");

            int updated = 0;
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME;

            for (Map.Entry<String, String> entry : hashToDateMap.entrySet()) {
                String hash = entry.getKey();
                String newDate = entry.getValue();

                Map<String, String> updateData = new HashMap<>();
                updateData.put("date_added", newDate);

                String json = gson.toJson(updateData);
                String encodedHash = URLEncoder.encode(hash, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "?content_hash=eq." + encodedHash))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .timeout(REQUEST_TIMEOUT)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204 || response.statusCode() == 200) {
                    updated++;
                } else {
                    System.err.println("[SupabaseBatch] Failed to update hash " + hash + ": " + response.statusCode());
                }
            }

            System.out.println("[SupabaseBatch] Batch date update complete: " + updated + "/" + hashToDateMap.size());
            return updated;
        } catch (Exception e) {
            throw new SupabaseBatchException("Error during batch date update: " + e.getMessage(), e);
        }
    }

    /**
     * Escape SQL special characters.
     */
    private String escapeSql(String input) {
        if (input == null) return "";
        return input.replace("'", "''").replace("\\", "\\\\");
    }

    /**
     * Custom exception for batch operations.
     */
    public static class SupabaseBatchException extends Exception {
        public SupabaseBatchException(String message) {
            super(message);
        }

        public SupabaseBatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
