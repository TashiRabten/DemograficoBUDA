package com.example.glossariobuda;

import com.example.glossariobuda.exceptions.CloudOperationException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facade for Supabase operations.
 * This class delegates to specialized services for better organization and maintainability.
 *
 * Responsibilities:
 * - Connection management and testing
 * - Metadata operations (glossary version, etc.)
 * - Delegation to specialized services
 */
public class SupabaseClient {
    private static final String SUPABASE_URL = "https://ctbufeavhuypjdbqmwzb.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN0YnVmZWF2aHV5cGpkYnFtd3piIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAyODQ2MzYsImV4cCI6MjA3NTg2MDYzNn0.lkaQWS8u2QisnRENtOXKDAMbPwhRV1jS4euBDc1TUDg";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(10);
    private static final int CONNECTION_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;

    private final HttpClient httpClient;
    private final Gson gson;

    // Specialized services
    private final SupabaseHashService hashService;
    private final SupabaseCRUDService crudService;
    private final SupabaseQueryService queryService;
    private final SupabaseBatchService batchService;

    public SupabaseClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CLIENT_TIMEOUT)
                .build();
        this.gson = new Gson();

        // Initialize services
        this.hashService = new SupabaseHashService(httpClient, gson);
        this.crudService = new SupabaseCRUDService(httpClient, gson);
        this.queryService = new SupabaseQueryService(httpClient, gson);
        this.batchService = new SupabaseBatchService(httpClient, gson, hashService);
    }

    // ============================================================================
    // CONNECTION & INFRASTRUCTURE
    // ============================================================================

    /**
     * Test connection to Supabase with timeout.
     */
    public boolean testConnection() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SUPABASE_URL + "/rest/v1/");
            conn = (HttpURLConnection) url.openConnection();

            conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("apikey", SUPABASE_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);

            int responseCode = conn.getResponseCode();
            boolean online = responseCode > 0 && responseCode < 500;

            System.out.println("[SupabaseClient] Connection test: " +
                    (online ? "✅ ONLINE" : "❌ OFFLINE") +
                    " (HTTP " + responseCode + ")");

            return online;

        } catch (SocketTimeoutException e) {
            System.err.println("[SupabaseClient] ⏱️ Connection timed out - offline");
            return false;
        } catch (UnknownHostException e) {
            System.err.println("[SupabaseClient] 🌐 Unknown host - offline");
            return false;
        } catch (ConnectException e) {
            System.err.println("[SupabaseClient] 🔌 Connection refused - offline");
            return false;
        } catch (Exception e) {
            System.err.println("[SupabaseClient] ❌ Connection test failed: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    // ============================================================================
    // METADATA OPERATIONS
    // ============================================================================

    /**
     * Get glossary metadata (version, last reset date, etc.)
     */
    public GlossaryMetadata getGlossaryMetadata() throws CloudOperationException {
        try {
            String url = SUPABASE_URL + "/rest/v1/glossary_metadata?id=eq.1&select=*";

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
                    return mapToGlossaryMetadata(rawList.get(0));
                }
            }

            return new GlossaryMetadata(1, null, "Initial", 0);
        } catch (java.io.IOException | InterruptedException e) {
            throw new CloudOperationException("Failed to get glossary metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Increment glossary version (call this after doing a reset/reimport).
     */
    public int incrementGlossaryVersion(String reason, String startDate, String endDate) throws CloudOperationException {
        try {
            GlossaryMetadata currentMetadata = getGlossaryMetadata();
            int newVersion = currentMetadata.version + 1;

            String url = SUPABASE_URL + "/rest/v1/glossary_metadata";  // ❌ Remove the filter

            Map<String, Object> updateData = new HashMap<>();
            updateData.put("id", 1);  // ✅ ADD THIS - specify the ID
            updateData.put("version", newVersion);
            updateData.put("last_reset_date", Instant.now().toString());
            updateData.put("last_reset_reason", reason);
            updateData.put("reset_start_date", startDate);
            updateData.put("reset_end_date", endDate);

            String json = gson.toJson(updateData);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates")  // ✅ UPSERT mode
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))  // ✅ Use POST for upsert
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                System.out.println("[SupabaseClient] Version upserted: " + currentMetadata.version + " → " + newVersion);
                System.out.println("[SupabaseClient] Reset date range: " + startDate + " to " + endDate);
                return newVersion;
            } else {
                throw new CloudOperationException("Failed to increment version: " + response.statusCode() + " - " + response.body());
            }
        } catch (java.io.IOException | InterruptedException e) {
            throw new CloudOperationException("Failed to increment glossary version: " + e.getMessage(), e);
        }
    }

    // ============================================================================
    // HASH OPERATIONS - Delegated to SupabaseHashService
    // ============================================================================

    public static String generateHash(String sourceTerm, String sourceLanguage,
                                      String targetTerm, String targetLanguage,
                                      String context, String contributor) {
        return SupabaseHashService.generateHash(sourceTerm, sourceLanguage,
                                                 targetTerm, targetLanguage, context, contributor);
    }

    public boolean hashExists(String hash) throws CloudOperationException {
        try {
            return hashService.hashExists(hash);
        } catch (SupabaseHashService.SupabaseHashException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public boolean termExistsByHash(String hash) throws CloudOperationException {
        try {
            return hashService.termExistsByHash(hash);
        } catch (SupabaseHashService.SupabaseHashException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public Set<String> getAllTermHashes() {
        return hashService.getAllTermHashes();
    }

    public Set<String> getAllExistingHashes() throws CloudOperationException {
        try {
            return hashService.getAllExistingHashes();
        } catch (SupabaseHashService.SupabaseHashException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public Set<String> getExistingHashes(List<String> hashes) throws CloudOperationException {
        try {
            return hashService.getExistingHashes(hashes);
        } catch (SupabaseHashService.SupabaseHashException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public Map<String, Integer> getHashIdMap() throws CloudOperationException {
        try {
            return queryService.getHashIdMap();
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    // ============================================================================
    // CRUD OPERATIONS - Delegated to SupabaseCRUDService
    // ============================================================================

    public boolean insertTerm(TermDTO term) throws CloudOperationException {
        try {
            return crudService.insertTerm(term);
        } catch (SupabaseCRUDService.SupabaseCRUDException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public TermDTO getTermByHash(String hash) throws CloudOperationException {
        try {
            return crudService.getTermByHash(hash);
        } catch (SupabaseCRUDService.SupabaseCRUDException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public TermDTO findTermByContent(String sourceTerm, String targetTerm) throws CloudOperationException {
        try {
            return crudService.findTermByContent(sourceTerm, targetTerm);
        } catch (SupabaseCRUDService.SupabaseCRUDException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public boolean updateTermByHash(String hash, TermDTO term) throws CloudOperationException {
        try {
            return crudService.updateTermByHash(hash, term);
        } catch (SupabaseCRUDService.SupabaseCRUDException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    /**
     * Update term by cloud ID (more efficient than DELETE+INSERT for edits).
     * Use this for edit operations when you have the cloud_id.
     */
    public boolean updateTermById(int cloudId, TermDTO term) throws CloudOperationException {
        try {
            return crudService.updateTermById(cloudId, term);
        } catch (SupabaseCRUDService.SupabaseCRUDException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public boolean deleteTermByHash(String hash) throws CloudOperationException {
        try {
            return crudService.deleteTermByHash(hash);
        } catch (SupabaseCRUDService.SupabaseCRUDException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public boolean upsertTerm(TermDTO term) throws CloudOperationException {
        try {
            return crudService.upsertTerm(term);
        } catch (SupabaseCRUDService.SupabaseCRUDException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    // ============================================================================
    // QUERY OPERATIONS - Delegated to SupabaseQueryService
    // ============================================================================

    public List<TermDTO> getAllTerms() throws CloudOperationException {
        try {
            return queryService.getAllTerms();
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public List<TermDTO> getTermsSince(String timestamp) throws CloudOperationException {
        try {
            return queryService.getTermsSince(timestamp);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public List<TermDTO> getTermsInDateRange(String startDate, String endDate) throws CloudOperationException {
        try {
            return queryService.getTermsInDateRange(startDate, endDate);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public List<TermDTO> getTermsOutsideDateRange(String startDate, String endDate) throws CloudOperationException {
        try {
            return queryService.getTermsOutsideDateRange(startDate, endDate);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public List<TermDTO> getTermsModifiedAfter(String timestamp) throws CloudOperationException {
        try {
            return queryService.getTermsModifiedAfter(timestamp);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public int countTermsInDateRange(String startDate, String endDate) throws CloudOperationException {
        try {
            return queryService.countTermsInDateRange(startDate, endDate);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public int countTermsSince(String timestamp) throws CloudOperationException {
        try {
            return queryService.countTermsSince(timestamp);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public List<TermDTO> getTermsParallel(int workerCount) throws CloudOperationException {
        try {
            return queryService.getTermsParallel(workerCount);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    /**
     * Fetch terms from Supabase using parallel workers, starting from a specific cloud ID.
     * This enables smart resume that only fetches new terms above the local max cloud_id.
     *
     * @param startFromId The cloud_id to start fetching from (exclusive). Pass 0 to fetch all terms.
     * @param workerCount Number of parallel workers (typically 4)
     * @return List of term DTOs with cloud_id > startFromId
     */
    public List<TermDTO> getTermsParallelFrom(int startFromId, int workerCount) throws CloudOperationException {
        try {
            return queryService.getTermsParallelFrom(startFromId, workerCount);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public List<TermDTO> getTermsByHashes(List<String> hashes) throws CloudOperationException {
        try {
            return queryService.getTermsByHashes(hashes);
        } catch (SupabaseQueryService.SupabaseQueryException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    // ============================================================================
    // BATCH OPERATIONS - Delegated to SupabaseBatchService
    // ============================================================================

    public int batchInsertTerms(List<TermDTO> terms) throws CloudOperationException {
        try {
            return batchService.batchInsertTerms(terms);
        } catch (SupabaseBatchService.SupabaseBatchException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public int insertTermsBatchWithDuplicateCheck(List<TermDTO> terms) throws CloudOperationException {
        try {
            return batchService.insertTermsBatchWithDuplicateCheck(terms);
        } catch (SupabaseBatchService.SupabaseBatchException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public boolean upsertTermsBatch(List<TermDTO> terms) throws CloudOperationException {
        try {
            return batchService.upsertTermsBatch(terms);
        } catch (SupabaseBatchService.SupabaseBatchException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public int deleteTermsByDateRange(String startDate, String endDate) throws CloudOperationException {
        try {
            return batchService.deleteTermsByDateRange(startDate, endDate);
        } catch (SupabaseBatchService.SupabaseBatchException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    public int batchUpdateTermDatesByHash(Map<String, String> hashToDateMap) throws CloudOperationException {
        try {
            return batchService.batchUpdateTermDatesByHash(hashToDateMap);
        } catch (SupabaseBatchService.SupabaseBatchException e) {
            throw new CloudOperationException(e.getMessage(), e);
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private GlossaryMetadata mapToGlossaryMetadata(Map<String, Object> map) {
        Object versionObj = map.get("version");
        int version = 1;
        if (versionObj instanceof Double) {
            version = ((Double) versionObj).intValue();
        } else if (versionObj instanceof Integer) {
            version = (Integer) versionObj;
        }

        String lastResetDate = (String) map.get("last_reset_date");
        String lastResetReason = (String) map.get("last_reset_reason");
        String resetStartDate = (String) map.get("reset_start_date");
        String resetEndDate = (String) map.get("reset_end_date");

        Object totalTermsObj = map.get("total_terms");
        int totalTerms = 0;
        if (totalTermsObj instanceof Double) {
            totalTerms = ((Double) totalTermsObj).intValue();
        } else if (totalTermsObj instanceof Integer) {
            totalTerms = (Integer) totalTermsObj;
        }

        return new GlossaryMetadata(version, lastResetDate, lastResetReason, totalTerms, resetStartDate, resetEndDate);
    }

    // Helper method for JSON extraction
    public static String getJsonString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    // ============================================================================
    // INNER CLASSES - Data Transfer Objects
    // ============================================================================

    public static class TermDTO {
        public Integer id;
        public String source_term;
        public String source_language;
        public String target_term;
        public String target_language;
        public String context;
        public String contributor;
        public String date_added;
        public String verified_status;
        public String notes;
        public String content_hash;
        public String owner;
        public String updated_at;
    }

    public static class GlossaryMetadata {
        public int version;
        public String lastResetDate;
        public String lastResetReason;
        public int totalTerms;
        public String resetStartDate;
        public String resetEndDate;

        public GlossaryMetadata(int version, String lastResetDate, String lastResetReason, int totalTerms) {
            this.version = version;
            this.lastResetDate = lastResetDate;
            this.lastResetReason = lastResetReason;
            this.totalTerms = totalTerms;
            this.resetStartDate = null;
            this.resetEndDate = null;
        }

        public GlossaryMetadata(int version, String lastResetDate, String lastResetReason, int totalTerms,
                                String resetStartDate, String resetEndDate) {
            this.version = version;
            this.lastResetDate = lastResetDate;
            this.lastResetReason = lastResetReason;
            this.totalTerms = totalTerms;
            this.resetStartDate = resetStartDate;
            this.resetEndDate = resetEndDate;
        }
    }
}
