package com.example.glossariobuda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service responsible for complex query operations with Supabase.
 * Handles various term queries including date ranges, timestamps, and pagination.
 */
public class SupabaseQueryService {
    private static final String SUPABASE_URL = "https://ctbufeavhuypjdbqmwzb.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN0YnVmZWF2aHV5cGpkYnFtd3piIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAyODQ2MzYsImV4cCI6MjA3NTg2MDYzNn0.lkaQWS8u2QisnRENtOXKDAMbPwhRV1jS4euBDc1TUDg";
    private static final String TABLE_NAME = "glossario_terms";  // Changed from "terms" for exclusive access
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);  // Increased from 10s to handle large queries

    private final HttpClient httpClient;
    private final Gson gson;

    public SupabaseQueryService(HttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    /**
     * Get ALL terms from cloud (for full sync).
     */
    public List<SupabaseClient.TermDTO> getAllTerms() throws SupabaseQueryException {
        List<SupabaseClient.TermDTO> allTerms = new ArrayList<>();
        int limit = 1000;
        int offset = 0;
        boolean hasMore = true;

        try {
            while (hasMore) {
                String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?order=date_added.asc&limit=" + limit + "&offset=" + offset;

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

                    for (Map<String, Object> raw : rawList) {
                        allTerms.add(mapToTermDTO(raw));
                    }

                    if (rawList.size() < limit) {
                        hasMore = false;
                    } else {
                        offset += limit;
                    }
                } else {
                    throw new SupabaseQueryException("Query failed: HTTP " + response.statusCode() + " - " + response.body());
                }
            }
        } catch (Exception e) {
            throw new SupabaseQueryException("Error getting all terms: " + e.getMessage(), e);
        }

        return allTerms;
    }

    /**
     * Get terms added or updated after a specific timestamp with pagination support.
     * For initial sync (very old timestamp), uses optimized query without WHERE clause.
     */
    public List<SupabaseClient.TermDTO> getTermsSince(String timestamp) throws SupabaseQueryException {
        List<SupabaseClient.TermDTO> allTerms = new ArrayList<>();
        int offset = 0;
        boolean hasMore = true;

        try {
            // Detect initial sync (timestamp before 2020 = fresh download)
            boolean isInitialSync = timestamp.startsWith("19") || timestamp.startsWith("200") || timestamp.startsWith("201");

            // For full sync, use parallel workers for maximum performance
            // This gives 4-5x speedup by fetching multiple ranges simultaneously
            if (isInitialSync) {
                System.out.println("[SupabaseQuery] Initial sync detected - using parallel workers");
                return getTermsParallel(4);  // Use 4 parallel workers
            }

            // For incremental sync, use sequential cursor pagination
            // Use larger batch size for full sync to reduce network round trips
            // Incremental sync: 1000 records/batch (typical case is <1000 total)
            int limit = 1000;

            // Use cursor-based pagination (id > lastId) instead of OFFSET for all sync types
            // This is O(1) instead of O(n) and prevents timeouts on large datasets
            int lastId = 0;

            while (hasMore) {
                // Incremental sync: Use ONLY date_added for simplicity and performance
                // This catches all new terms. Updated terms will be handled by realtime sync.
                // Using single-column filter allows PostgreSQL to use index efficiently (no OR overhead)
                String encodedTimestamp = URLEncoder.encode(timestamp, StandardCharsets.UTF_8);
                String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME +
                        "?id=gt." + lastId +
                        "&date_added=gte." + encodedTimestamp +
                        "&order=id.asc&limit=" + limit;

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

                    if (rawList.isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    for (Map<String, Object> raw : rawList) {
                        allTerms.add(mapToTermDTO(raw));
                        // Update cursor to last processed ID
                        Object idObj = raw.get("id");
                        if (idObj instanceof Number) {
                            lastId = ((Number) idObj).intValue();
                        }
                    }

                    if (rawList.size() < limit) {
                        hasMore = false;
                    } else {
                        System.out.println("[SupabaseQuery] Fetched " + allTerms.size() + " terms so far, continuing...");
                    }
                } else {
                    throw new SupabaseQueryException("Query failed: HTTP " + response.statusCode() + " - " + response.body());
                }
            }
        } catch (Exception e) {
            throw new SupabaseQueryException("Error getting terms since timestamp: " + e.getMessage(), e);
        }

        return allTerms;
    }

    /**
     * Fetch all terms using parallel workers for maximum performance.
     * Divides the ID range into partitions and fetches each partition in parallel.
     * Used for full sync operations where we need to fetch all records efficiently.
     */
    public List<SupabaseClient.TermDTO> getTermsParallel(int workerCount) throws SupabaseQueryException {
        return getTermsParallelFrom(0, workerCount);
    }

    /**
     * Fetch terms starting from a specific ID using parallel workers.
     * Used for smart incremental sync that only fetches NEW terms.
     *
     * @param startFromId Only fetch terms with cloud_id > this value (0 = fetch all)
     * @param workerCount Number of parallel workers to use
     */
    public List<SupabaseClient.TermDTO> getTermsParallelFrom(int startFromId, int workerCount) throws SupabaseQueryException {
        try {
            System.out.println("[SupabaseQuery] 🚀 Starting parallel fetch with " + workerCount + " workers");

            // Get the maximum ID to determine total range
            int maxId = getMaxId();
            if (maxId == 0) {
                System.out.println("[SupabaseQuery] No records in cloud, skipping parallel fetch");
                return new ArrayList<>();
            }

            System.out.println("[SupabaseQuery] Max ID in cloud: " + maxId);

            // If startFromId is specified, only fetch NEW terms
            if (startFromId > 0) {
                System.out.println("[SupabaseQuery] Smart resume: fetching only IDs > " + startFromId);

                if (startFromId >= maxId) {
                    System.out.println("[SupabaseQuery] Local database is already up to date (local max: " +
                        startFromId + ", cloud max: " + maxId + ")");
                    return new ArrayList<>();
                }
            }

            // Calculate range per worker (only for the NEW portion)
            int effectiveStart = startFromId;
            int effectiveRange = maxId - effectiveStart;
            int rangePerWorker = (int) Math.ceil((double) effectiveRange / workerCount);

            // Create thread pool for parallel execution
            java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(workerCount);

            List<java.util.concurrent.Future<List<SupabaseClient.TermDTO>>> futures = new ArrayList<>();

            // Submit worker tasks
            for (int i = 0; i < workerCount; i++) {
                final int workerIndex = i;
                // Start from effectiveStart (local max ID), not from 0
                final int startId = effectiveStart + (i * rangePerWorker);
                final int endId = Math.min(effectiveStart + ((i + 1) * rangePerWorker), maxId);

                System.out.println("[SupabaseQuery] Worker " + workerIndex + " assigned range: " +
                    startId + " to " + endId);

                java.util.concurrent.Future<List<SupabaseClient.TermDTO>> future =
                    executor.submit(() -> fetchRange(workerIndex, startId, endId));

                futures.add(future);
            }

            // Collect results from all workers and track failures
            List<SupabaseClient.TermDTO> allTerms = new ArrayList<>();
            List<Integer> failedWorkers = new ArrayList<>();

            for (int i = 0; i < futures.size(); i++) {
                try {
                    List<SupabaseClient.TermDTO> workerResults = futures.get(i).get();
                    allTerms.addAll(workerResults);
                    System.out.println("[SupabaseQuery] Worker " + i + " completed: " +
                        workerResults.size() + " terms fetched");
                } catch (Exception e) {
                    failedWorkers.add(i);
                    System.err.println("[SupabaseQuery] Worker " + i + " failed: " + e.getMessage());
                }
            }

            // Shutdown initial executor
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }

            // RETRY LOGIC: Retry failed workers with exponential backoff
            if (!failedWorkers.isEmpty()) {
                int maxRetries = 3;
                int retryDelay = 2000; // Start with 2 seconds

                for (int retryAttempt = 1; retryAttempt <= maxRetries && !failedWorkers.isEmpty(); retryAttempt++) {
                    System.out.println("[SupabaseQuery] 🔄 Retry attempt " + retryAttempt + " for " +
                        failedWorkers.size() + " failed worker(s) after " + (retryDelay / 1000) + "s delay");

                    // Wait before retrying
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    // Create new executor for retry
                    java.util.concurrent.ExecutorService retryExecutor =
                        java.util.concurrent.Executors.newFixedThreadPool(failedWorkers.size());

                    List<java.util.concurrent.Future<List<SupabaseClient.TermDTO>>> retryFutures = new ArrayList<>();
                    List<Integer> retryingWorkers = new ArrayList<>(failedWorkers);
                    failedWorkers.clear();

                    // Submit retry tasks
                    for (int workerIndex : retryingWorkers) {
                        final int startId = effectiveStart + (workerIndex * rangePerWorker);
                        final int endId = Math.min(effectiveStart + ((workerIndex + 1) * rangePerWorker), maxId);

                        System.out.println("[SupabaseQuery] Retrying Worker " + workerIndex +
                            " (range: " + startId + " to " + endId + ")");

                        java.util.concurrent.Future<List<SupabaseClient.TermDTO>> future =
                            retryExecutor.submit(() -> fetchRange(workerIndex, startId, endId));

                        retryFutures.add(future);
                    }

                    // Collect retry results
                    for (int i = 0; i < retryFutures.size(); i++) {
                        int workerIndex = retryingWorkers.get(i);
                        try {
                            List<SupabaseClient.TermDTO> workerResults = retryFutures.get(i).get();
                            allTerms.addAll(workerResults);
                            System.out.println("[SupabaseQuery] ✅ Worker " + workerIndex +
                                " succeeded on retry: " + workerResults.size() + " terms fetched");
                        } catch (Exception e) {
                            failedWorkers.add(workerIndex);
                            System.err.println("[SupabaseQuery] Worker " + workerIndex +
                                " failed retry attempt " + retryAttempt + ": " + e.getMessage());
                        }
                    }

                    // Shutdown retry executor
                    retryExecutor.shutdown();
                    try {
                        if (!retryExecutor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                            retryExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        retryExecutor.shutdownNow();
                        break;
                    }

                    // Exponential backoff: double the delay for next retry
                    retryDelay *= 2;
                }

                // SEQUENTIAL FALLBACK: Try one more time with smaller batches
                if (!failedWorkers.isEmpty()) {
                    System.err.println("[SupabaseQuery] ⚠️ " + failedWorkers.size() +
                        " worker(s) failed after " + maxRetries + " retries");
                    System.out.println("[SupabaseQuery] 🔄 Attempting sequential fallback with smaller batches...");

                    List<Integer> stillFailedWorkers = new ArrayList<>();

                    for (int workerIndex : failedWorkers) {
                        final int startId = effectiveStart + (workerIndex * rangePerWorker);
                        final int endId = Math.min(effectiveStart + ((workerIndex + 1) * rangePerWorker), maxId);

                        try {
                            System.out.println("[SupabaseQuery] Sequential fallback for Worker " + workerIndex +
                                " (range: " + startId + " to " + endId + ")");

                            // Try sequential fetch with smaller 500-record batches (less load)
                            List<SupabaseClient.TermDTO> sequentialResults =
                                fetchRangeSequential(workerIndex, startId, endId);

                            allTerms.addAll(sequentialResults);
                            System.out.println("[SupabaseQuery] ✅ Sequential fallback succeeded for Worker " +
                                workerIndex + ": " + sequentialResults.size() + " terms fetched");

                        } catch (Exception e) {
                            stillFailedWorkers.add(workerIndex);
                            System.err.println("[SupabaseQuery] Sequential fallback also failed for Worker " +
                                workerIndex + ": " + e.getMessage());
                        }
                    }

                    // Report final status
                    if (!stillFailedWorkers.isEmpty()) {
                        System.err.println("[SupabaseQuery] ⚠️ " + stillFailedWorkers.size() +
                            " worker(s) permanently failed even after sequential fallback");
                        System.err.println("[SupabaseQuery] Failed workers: " + stillFailedWorkers);
                        System.err.println("[SupabaseQuery] These ranges will be retried on next sync");
                    }
                }
            }

            System.out.println("[SupabaseQuery] ✅ Parallel fetch complete: " + allTerms.size() +
                " total terms from " + workerCount + " workers");

            return allTerms;

        } catch (Exception e) {
            throw new SupabaseQueryException("Parallel fetch failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch a range of IDs sequentially with smaller batches and delays.
     * Used as fallback when parallel fetch fails due to rate limiting.
     */
    private List<SupabaseClient.TermDTO> fetchRangeSequential(int workerIndex, int startId, int endId)
            throws SupabaseQueryException {
        List<SupabaseClient.TermDTO> rangeTerms = new ArrayList<>();
        int lastId = startId;
        // Smaller batch size to reduce load on Supabase
        int batchSize = 500;
        int requestCount = 0;

        try {
            while (lastId < endId) {
                // Add 200ms delay every 5 requests to avoid rate limiting
                if (requestCount > 0 && requestCount % 5 == 0) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME +
                    "?id=gt." + lastId +
                    "&id=lte." + endId +
                    "&order=id.asc&limit=" + batchSize;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Map<String, Object>> rawList = gson.fromJson(
                            response.body(),
                            new TypeToken<List<Map<String, Object>>>(){}.getType()
                    );

                    if (rawList.isEmpty()) {
                        break;  // No more records in this range
                    }

                    for (Map<String, Object> raw : rawList) {
                        rangeTerms.add(mapToTermDTO(raw));
                        Object idObj = raw.get("id");
                        if (idObj instanceof Number) {
                            lastId = ((Number) idObj).intValue();
                        }
                    }

                    requestCount++;

                    // Log progress less frequently (every 5k records)
                    if (rangeTerms.size() % 5000 == 0) {
                        System.out.println("[SupabaseQuery] Sequential Worker " + workerIndex +
                            " progress: " + rangeTerms.size() + " terms");
                    }

                } else {
                    throw new SupabaseQueryException("Sequential Worker " + workerIndex +
                        " query failed: HTTP " + response.statusCode());
                }
            }

            return rangeTerms;

        } catch (Exception e) {
            throw new SupabaseQueryException("Sequential Worker " + workerIndex +
                " error: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch a range of IDs using cursor pagination.
     * Called by worker threads in parallel fetch.
     */
    private List<SupabaseClient.TermDTO> fetchRange(int workerIndex, int startId, int endId)
            throws SupabaseQueryException {
        List<SupabaseClient.TermDTO> rangeTerms = new ArrayList<>();
        int lastId = startId;
        // Supabase REST API caps at 1000 records per request
        int batchSize = 1000;

        try {
            while (lastId < endId) {
                String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME +
                    "?id=gt." + lastId +
                    "&id=lte." + endId +
                    "&order=id.asc&limit=" + batchSize;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Map<String, Object>> rawList = gson.fromJson(
                            response.body(),
                            new TypeToken<List<Map<String, Object>>>(){}.getType()
                    );

                    if (rawList.isEmpty()) {
                        // No more records in this range
                        break;
                    }

                    for (Map<String, Object> raw : rawList) {
                        rangeTerms.add(mapToTermDTO(raw));
                        Object idObj = raw.get("id");
                        if (idObj instanceof Number) {
                            lastId = ((Number) idObj).intValue();
                        }
                    }

                    // Log progress every 10k records per worker
                    if (rangeTerms.size() % 10000 == 0) {
                        System.out.println("[SupabaseQuery] Worker " + workerIndex +
                            " progress: " + rangeTerms.size() + " terms");
                    }

                    // IMPORTANT: Don't check if rawList.size() < batchSize
                    // Supabase caps at 1000, so this would always break after first batch
                    // Instead, continue until we get empty results or reach endId
                } else {
                    throw new SupabaseQueryException("Worker " + workerIndex +
                        " query failed: HTTP " + response.statusCode());
                }
            }

            return rangeTerms;

        } catch (Exception e) {
            throw new SupabaseQueryException("Worker " + workerIndex + " error: " + e.getMessage(), e);
        }
    }

    /**
     * Get all deleted term hashes since a given timestamp.
     */
    public Set<String> getDeletedHashesSince(String timestamp) throws SupabaseQueryException {
        try {
            String encodedTimestamp = URLEncoder.encode(timestamp, StandardCharsets.UTF_8);
            String url = SUPABASE_URL + "/rest/v1/deleted_terms_log?deleted_at=gte." + encodedTimestamp + "&select=content_hash";

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

                Set<String> hashes = new HashSet<>();
                for (Map<String, Object> map : rawList) {
                    String hash = (String) map.get("content_hash");
                    if (hash != null) {
                        hashes.add(hash);
                    }
                }

                System.out.println("[SupabaseQuery] Found " + hashes.size() + " deleted hashes since " + timestamp);
                return hashes;
            }

            return new HashSet<>();
        } catch (Exception e) {
            throw new SupabaseQueryException("Error getting deleted hashes: " + e.getMessage(), e);
        }
    }

    /**
     * Get terms within a date range (inclusive).
     */
    public List<SupabaseClient.TermDTO> getTermsInDateRange(String startDate, String endDate) throws SupabaseQueryException {
        List<SupabaseClient.TermDTO> allTerms = new ArrayList<>();
        int limit = 1000;
        int offset = 0;
        boolean hasMore = true;

        try {
            StringBuilder urlBuilder = new StringBuilder(SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?");

            if (startDate != null && endDate != null) {
                String encodedStart = URLEncoder.encode(startDate, StandardCharsets.UTF_8);
                String encodedEnd = URLEncoder.encode(endDate, StandardCharsets.UTF_8);
                urlBuilder.append("date_added=gte.").append(encodedStart)
                        .append("&date_added=lte.").append(encodedEnd);
            } else if (startDate != null) {
                String encodedStart = URLEncoder.encode(startDate, StandardCharsets.UTF_8);
                urlBuilder.append("date_added=gte.").append(encodedStart);
            } else if (endDate != null) {
                String encodedEnd = URLEncoder.encode(endDate, StandardCharsets.UTF_8);
                urlBuilder.append("date_added=lte.").append(encodedEnd);
            } else {
                throw new IllegalArgumentException("At least one date (start or end) must be provided");
            }

            String baseQuery = urlBuilder.toString();

            while (hasMore) {
                String url = baseQuery + "&order=date_added.asc&limit=" + limit + "&offset=" + offset;

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

                    for (Map<String, Object> raw : rawList) {
                        allTerms.add(mapToTermDTO(raw));
                    }

                    if (rawList.size() < limit) {
                        hasMore = false;
                    } else {
                        offset += limit;
                        System.out.println("[SupabaseQuery] Fetched " + allTerms.size() + " terms in range so far...");
                    }
                } else {
                    throw new SupabaseQueryException("Query failed: HTTP " + response.statusCode() + " - " + response.body());
                }
            }
        } catch (Exception e) {
            throw new SupabaseQueryException("Error getting terms in date range: " + e.getMessage(), e);
        }

        return allTerms;
    }

    /**
     * Get terms OUTSIDE a date range (for protected terms).
     */
    public List<SupabaseClient.TermDTO> getTermsOutsideDateRange(String startDate, String endDate) throws SupabaseQueryException {
        try {
            StringBuilder queryBuilder = new StringBuilder();

            if (startDate != null && endDate != null) {
                queryBuilder.append("or=(date_added.lt.").append(startDate)
                        .append(",date_added.gt.").append(endDate).append(")");
            } else if (startDate != null) {
                queryBuilder.append("date_added=lt.").append(startDate);
            } else if (endDate != null) {
                queryBuilder.append("date_added=gt.").append(endDate);
            } else {
                return getAllTerms();
            }

            String queryParams = queryBuilder.toString();
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?" + queryParams + "&order=date_added.asc";

            System.out.println("[SupabaseQuery] Getting terms outside range: " + startDate + " to " + endDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<SupabaseClient.TermDTO> terms = gson.fromJson(
                        response.body(),
                        new TypeToken<List<SupabaseClient.TermDTO>>(){}.getType()
                );
                System.out.println("[SupabaseQuery] Found " + terms.size() + " terms outside range");
                return terms != null ? terms : new ArrayList<>();
            } else {
                throw new SupabaseQueryException("Query failed: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            throw new SupabaseQueryException("Error getting terms outside date range: " + e.getMessage(), e);
        }
    }

    /**
     * Get terms modified after a timestamp (for detecting changes).
     */
    public List<SupabaseClient.TermDTO> getTermsModifiedAfter(String timestamp) throws SupabaseQueryException {
        List<SupabaseClient.TermDTO> terms = new ArrayList<>();

        try {
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME +
                    "?updated_at=gt." + timestamp +
                    "&order=updated_at.asc" +
                    "&limit=1000";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", SUPABASE_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new SupabaseQueryException("Query failed: HTTP " + responseCode);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JSONArray jsonArray = new JSONArray(response.toString());
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    SupabaseClient.TermDTO term = jsonToTermDTO(obj);
                    terms.add(term);
                }
            }
        } catch (Exception e) {
            throw new SupabaseQueryException("Error getting modified terms: " + e.getMessage(), e);
        }

        return terms;
    }

    /**
     * Fetch multiple terms by their content hashes.
     */
    public List<SupabaseClient.TermDTO> getTermsByHashes(List<String> hashes) throws SupabaseQueryException {
        List<SupabaseClient.TermDTO> terms = new ArrayList<>();

        if (hashes == null || hashes.isEmpty()) {
            return terms;
        }

        try {
            // Build query with IN clause
            StringBuilder hashList = new StringBuilder();
            for (int i = 0; i < hashes.size(); i++) {
                if (i > 0) hashList.append(",");
                hashList.append(hashes.get(i));
            }

            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?content_hash=in.(" + hashList.toString() + ")";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> rawList = gson.fromJson(
                        response.body(),
                        new TypeToken<List<Map<String, Object>>>(){}.getType()
                );

                for (Map<String, Object> raw : rawList) {
                    terms.add(mapToTermDTO(raw));
                }
            } else {
                throw new SupabaseQueryException("Failed to get terms by hashes: HTTP " + response.statusCode());
            }

            return terms;
        } catch (Exception e) {
            throw new SupabaseQueryException("Error getting terms by hashes: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch mapping of content_hash to Supabase cloud id for all terms.
     */
    public Map<String, Integer> getHashIdMap() throws SupabaseQueryException {
        Map<String, Integer> hashIdMap = new HashMap<>();
        int lastId = 0;
        int limit = 5000;  // Increased from 1000 since cursor-based pagination is much faster
        boolean hasMore = true;

        try {
            while (hasMore) {
                // Use cursor-based pagination (WHERE id > lastId) instead of OFFSET
                // This is O(1) instead of O(n) for large datasets
                String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME +
                        "?select=id,content_hash&id=gt." + lastId + "&order=id.asc&limit=" + limit;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new SupabaseQueryException("Query failed: HTTP " + response.statusCode() +
                            " - Body: " + response.body());
                }

                List<Map<String, Object>> rawList = gson.fromJson(
                        response.body(),
                        new TypeToken<List<Map<String, Object>>>(){}.getType()
                );

                if (rawList.isEmpty()) {
                    hasMore = false;
                    break;
                }

                for (Map<String, Object> raw : rawList) {
                    Object hashObj = raw.get("content_hash");
                    Object idObj = raw.get("id");

                    if (hashObj instanceof String hash && hash != null && !hash.isEmpty() && idObj instanceof Number number) {
                        hashIdMap.put(hash, number.intValue());
                        lastId = number.intValue();  // Update cursor to last processed ID
                    }
                }

                if (rawList.size() < limit) {
                    hasMore = false;
                }
            }
        } catch (SupabaseQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new SupabaseQueryException("Error fetching hash/id map: " + e.getMessage(), e);
        }

        return hashIdMap;
    }

    /**
     * Get the maximum ID from the cloud table.
     * Used to determine range for parallel worker distribution.
     */
    public int getMaxId() throws SupabaseQueryException {
        try {
            // Use Supabase's max aggregation with order and limit
            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME +
                "?select=id&order=id.desc&limit=1";

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
                    Object idObj = rawList.get(0).get("id");
                    if (idObj instanceof Number) {
                        return ((Number) idObj).intValue();
                    }
                }
                return 0;
            } else {
                throw new SupabaseQueryException("Failed to get max ID: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            throw new SupabaseQueryException("Error getting max ID: " + e.getMessage(), e);
        }
    }

    /**
     * Count terms added or updated since a specific timestamp.
     * Used to check if a query would return too many results before fetching.
     */
    public int countTermsSince(String timestamp) throws SupabaseQueryException {
        try {
            // Detect initial sync (timestamp before 2020 = fresh download)
            boolean isInitialSync = timestamp.startsWith("19") || timestamp.startsWith("200") || timestamp.startsWith("201");

            String url;
            if (isInitialSync) {
                // For initial sync, count all terms
                url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?select=id";
            } else {
                // For incremental sync, count terms with date_added >= timestamp
                String encodedTimestamp = URLEncoder.encode(timestamp, StandardCharsets.UTF_8);
                url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME +
                      "?date_added=gte." + encodedTimestamp + "&select=id";
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Prefer", "count=exact")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String contentRange = response.headers().firstValue("Content-Range").orElse(null);
                if (contentRange != null) {
                    String[] parts = contentRange.split("/");
                    if (parts.length == 2) {
                        return Integer.parseInt(parts[1]);
                    }
                }
                return 0;
            } else {
                throw new SupabaseQueryException("Count query failed: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            throw new SupabaseQueryException("Error counting terms since timestamp: " + e.getMessage(), e);
        }
    }

    /**
     * Count terms in a date range.
     */
    public int countTermsInDateRange(String startDate, String endDate) throws SupabaseQueryException {
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

            String url = SUPABASE_URL + "/rest/v1/" + TABLE_NAME + "?" + queryBuilder.toString() + "&select=id";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Prefer", "count=exact")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String contentRange = response.headers().firstValue("Content-Range").orElse(null);
                if (contentRange != null) {
                    String[] parts = contentRange.split("/");
                    if (parts.length == 2) {
                        return Integer.parseInt(parts[1]);
                    }
                }
                return 0;
            } else {
                throw new SupabaseQueryException("Count query failed: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            throw new SupabaseQueryException("Error counting terms: " + e.getMessage(), e);
        }
    }

    /**
     * Convert JSON object to TermDTO.
     */
    private SupabaseClient.TermDTO jsonToTermDTO(JSONObject obj) {
        SupabaseClient.TermDTO term = new SupabaseClient.TermDTO();

        term.id = obj.optInt("id", 0);
        term.source_term = obj.optString("source_term", null);
        term.source_language = obj.optString("source_language", null);
        term.target_term = obj.optString("target_term", null);
        term.target_language = obj.optString("target_language", null);
        term.context = obj.optString("context", null);
        term.contributor = obj.optString("contributor", null);
        term.notes = obj.optString("notes", null);
        term.verified_status = obj.optString("verified_status", null);
        term.content_hash = obj.optString("content_hash", null);
        term.date_added = obj.optString("date_added", null);
        term.owner = obj.optString("owner", "shared");
        term.updated_at = obj.optString("updated_at", null);

        return term;
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
     * Custom exception for query operations.
     */
    public static class SupabaseQueryException extends Exception {
        public SupabaseQueryException(String message) {
            super(message);
        }

        public SupabaseQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
