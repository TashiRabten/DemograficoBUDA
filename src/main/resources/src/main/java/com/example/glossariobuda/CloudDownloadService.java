package com.example.glossariobuda;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service responsible for downloading data from cloud (Supabase).
 * Handles sync from cloud, missing terms download, and term insertion.
 */
public class CloudDownloadService {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SupabaseClient supabaseClient;
    private final Connection localConnection;
    private final NetworkStatusMonitor networkMonitor;
    private final Object dbLock;

    // Callback interface for external operations
    public interface CloudDownloadCallback {
        void showStatusTemporarily(String message, int durationMs);
        Set<String> loadAllLocalHashes();
        String getLastSyncTime();
        void updateLastSyncTime();
        int getLocalTermsCount();
        boolean updateTermFieldsFromCloud(SupabaseClient.TermDTO newTerm, SupabaseClient.TermDTO oldTerm) throws SQLException;
        boolean updateMetadataOnlyFromCloud(SupabaseClient.TermDTO term) throws SQLException;
        void onOwnerChanged(); // Notify when new owners are added
    }

    private CloudDownloadCallback callback;

    public CloudDownloadService(SupabaseClient supabaseClient,
                                 Connection localConnection,
                                 NetworkStatusMonitor networkMonitor,
                                 Object dbLock) {
        this.supabaseClient = supabaseClient;
        this.localConnection = localConnection;
        this.networkMonitor = networkMonitor;
        this.dbLock = dbLock;
    }

    public void setCallback(CloudDownloadCallback callback) {
        this.callback = callback;
    }

    /**
     * Sync from cloud (download new/updated terms).
     */
    public DatabaseManager.SyncResult syncFromCloud(boolean dryRun) {
        return syncFromCloud(dryRun, false);
    }

    /**
     * Sync from cloud with option to force full sync.
     * @param dryRun If true, only simulates the sync without making changes
     * @param forceFullSync If true, ignores last_sync_time and syncs all terms from beginning
     */
    public DatabaseManager.SyncResult syncFromCloud(boolean dryRun, boolean forceFullSync) {
        int added = 0;
        int duplicates = 0;

        try {
            printDryRunHeader(dryRun);
            String lastSync = forceFullSync ? "1970-01-01 00:00:00" : determineSyncStartTime();
            if (forceFullSync) {
                System.out.println("[CloudDownload] 🔄 FULL SYNC MODE - Syncing all terms from cloud");
            }
            List<SupabaseClient.TermDTO> cloudTerms = fetchCloudTerms(lastSync);

            if (cloudTerms.isEmpty()) {
                return handleNoTermsToSync();
            }

            Set<String> localHashes = loadLocalHashes();

            // Pause realtime during sync to avoid database lock contention
            if (!dryRun) {
                RealtimeSyncService.pauseRealtime();
            }

            try {
                if (dryRun) {
                    return performDryRun(cloudTerms, localHashes);
                } else {
                    DatabaseManager.SyncResult result = performRealSync(cloudTerms, localHashes);
                    // Resume realtime after successful sync
                    RealtimeSyncService.resumeRealtime();
                    return result;
                }
            } catch (Exception syncError) {
                // Resume realtime even if sync fails
                if (!dryRun) {
                    RealtimeSyncService.resumeRealtime();
                }
                throw syncError;
            }

        } catch (Exception e) {
            handleSyncError(e);
        }

        return new DatabaseManager.SyncResult(added, duplicates);
    }

    private void printDryRunHeader(boolean dryRun) {
        if (dryRun) {
            System.out.println("\n[CloudDownload] ═══════════════════════════════════");
            System.out.println("[CloudDownload] 🔍 DRY RUN MODE - NO CHANGES WILL BE MADE");
            System.out.println("[CloudDownload] ═══════════════════════════════════\n");
        }
    }

    private String determineSyncStartTime() {
        String lastSync = callback != null ? callback.getLastSyncTime() :
                LocalDateTime.now().minusDays(30).format(DATETIME_FORMATTER);
        boolean isFirstSync = lastSync.equals(LocalDateTime.now().minusDays(30).format(DATETIME_FORMATTER));

        if (isFirstSync) {
            int localCount = callback != null ? callback.getLocalTermsCount() : 0;
            System.out.println("[CloudDownload] First sync detected. Local terms: " + localCount);

            if (localCount > 0) {
                System.out.println("[CloudDownload] Database has " + localCount + " terms - limiting sync to last 24 hours to avoid mass updates");
                return LocalDateTime.now().minusDays(1).format(DATETIME_FORMATTER);
            }
        }

        // SAFEGUARD: For very large databases, use more conservative sync windows
        // to prevent timeout when cloud has mass imports
        int localCount = callback != null ? callback.getLocalTermsCount() : 0;

        if (localCount > 500000) {
            // For databases with >500k terms, be extremely conservative
            LocalDateTime lastSyncTime = LocalDateTime.parse(lastSync, DATETIME_FORMATTER);
            long minutesSinceSync = java.time.Duration.between(lastSyncTime, LocalDateTime.now()).toMinutes();

            // If last sync was more than 30 minutes ago, only sync last 30 minutes
            // This prevents timeout when cloud has continuous mass imports
            if (minutesSinceSync > 30) {
                System.out.println("[CloudDownload] ⚠️ Large database (" + localCount + " terms) + sync gap of " +
                    (minutesSinceSync / 60.0) + " hours detected");
                System.out.println("[CloudDownload] Limiting sync to last 30 minutes to prevent timeout");
                System.out.println("[CloudDownload] Run sync multiple times to catch up, or use 'Force Full Sync'");
                return LocalDateTime.now().minusMinutes(30).format(DATETIME_FORMATTER);
            }
        } else if (localCount > 10000) {
            // For databases with 10k-500k terms, check if last sync is very old
            LocalDateTime lastSyncTime = LocalDateTime.parse(lastSync, DATETIME_FORMATTER);
            LocalDateTime threshold = LocalDateTime.now().minusDays(7);

            if (lastSyncTime.isBefore(threshold)) {
                System.out.println("[CloudDownload] ⚠️ Last sync was " +
                    java.time.Duration.between(lastSyncTime, LocalDateTime.now()).toDays() +
                    " days ago, but database has " + localCount + " terms");
                System.out.println("[CloudDownload] Limiting incremental sync to last 24 hours to prevent mass metadata updates");
                System.out.println("[CloudDownload] Use 'Force Full Sync' if you need to sync older changes");
                return LocalDateTime.now().minusDays(1).format(DATETIME_FORMATTER);
            }
        }

        return lastSync;
    }

    private List<SupabaseClient.TermDTO> fetchCloudTerms(String lastSync) throws Exception {
        System.out.println("[CloudDownload] Fetching terms since: " + lastSync);

        // SAFEGUARD: Check count before fetching to avoid timeout on mass imports
        // This prevents HTTP 500 timeout when cloud had recent mass import (e.g., 700k terms yesterday)
        int localCount = callback != null ? callback.getLocalTermsCount() : 0;

        // Only check count if we have a substantial local database
        // This prevents unnecessary COUNT queries for new/small databases
        if (localCount > 100000) {
            System.out.println("[CloudDownload] Checking term count before fetch (local database has " + localCount + " terms)...");

            try {
                int cloudCount = supabaseClient.countTermsSince(lastSync);
                System.out.println("[CloudDownload] Cloud query would return " + cloudCount + " terms");

                // If fetching would return >10k terms and local database already has >100k terms,
                // it's likely a mass import or cloud reset - skip to avoid timeout
                if (cloudCount > 10000) {
                    System.out.println("[CloudDownload] ⚠️ PREVENTING TIMEOUT: Query would return " + cloudCount + " terms");
                    System.out.println("[CloudDownload] Skipping automatic sync to prevent HTTP 500 timeout");
                    System.out.println("[CloudDownload] This usually means cloud had a recent mass import");
                    System.out.println("[CloudDownload] Your local database already has " + localCount + " terms");
                    System.out.println("[CloudDownload] Use 'Force Full Sync' if you need to sync these changes");

                    // Show user-friendly message
                    if (networkMonitor != null && callback != null) {
                        callback.showStatusTemporarily("Sync ignorado para evitar timeout (" + cloudCount + " termos)", 5000);
                    }

                    return new java.util.ArrayList<>();
                }
            } catch (Exception e) {
                // Check if COUNT failed due to timeout or HTTP 500
                String errorMsg = e.getMessage();
                boolean isTimeout = errorMsg != null &&
                    (errorMsg.contains("57014") ||
                     errorMsg.contains("statement timeout") ||
                     errorMsg.contains("HTTP 500"));

                // For very large databases (>1M terms), automatically use parallel workers
                // If COUNT fails, it means there's too much data for sequential fetch
                if (localCount > 1000000 || isTimeout) {
                    System.err.println("[CloudDownload] ⚠️ COUNT check failed for large database (" + localCount + " terms)");
                    System.err.println("[CloudDownload] Error: " + errorMsg);
                    System.err.println("[CloudDownload] Automatically switching to smart parallel workers");

                    updateNetworkStatus("Usando workers paralelos inteligentes...");

                    // Show user-friendly message
                    if (callback != null) {
                        callback.showStatusTemporarily("Sincronizando apenas termos novos...", 3000);
                    }

                    try {
                        // SMART PARALLEL WORKERS: Only fetch NEW terms based on local max cloud_id
                        // This prevents fetching 1M duplicates on every startup
                        SyncMetadataManager metadataManager = new SyncMetadataManager(localConnection, dbLock);
                        int localMaxCloudId = metadataManager.getLocalMaxCloudId();

                        System.out.println("[CloudDownload] Local max cloud_id: " + localMaxCloudId);
                        System.out.println("[CloudDownload] Smart parallel workers will fetch only IDs > " + localMaxCloudId);

                        // Use parallel workers (4 workers) to fetch only NEW terms
                        // startFromId is the highest cloud_id we already have locally
                        List<SupabaseClient.TermDTO> parallelResults = supabaseClient.getTermsParallelFrom(localMaxCloudId, 4);

                        if (parallelResults.isEmpty()) {
                            System.out.println("[CloudDownload] ✅ Already up to date - no new terms to fetch");
                            updateNetworkStatus("Já sincronizado - sem novos termos");

                            if (callback != null) {
                                callback.showStatusTemporarily("Já sincronizado - sem novos termos", 2000);
                            }

                            return new java.util.ArrayList<>();
                        }

                        System.out.println("[CloudDownload] Smart parallel fetch complete: " + parallelResults.size() + " NEW terms fetched");
                        System.out.println("[CloudDownload] (Skipped " + localMaxCloudId + " terms already in local database)");

                        updateNetworkStatus("Processando " + parallelResults.size() + " novo(s) termo(s)...");
                        return parallelResults;
                    } catch (Exception parallelError) {
                        System.err.println("[CloudDownload] Smart parallel fetch failed: " + parallelError.getMessage());
                        System.err.println("[CloudDownload] Network may be unstable or cloud is unavailable");

                        if (networkMonitor != null && callback != null) {
                            callback.showStatusTemporarily("Falha na sincronização: " + parallelError.getMessage(), 5000);
                        }

                        return new java.util.ArrayList<>();
                    }
                }

                // For smaller databases with non-timeout errors, log warning and try to proceed
                System.err.println("[CloudDownload] Warning: COUNT check failed, attempting fetch anyway: " + e.getMessage());
            }
        }

        // Proceed with normal fetch
        updateNetworkStatus("Baixando novos termos...");
        List<SupabaseClient.TermDTO> cloudTerms = supabaseClient.getTermsSince(lastSync);
        System.out.println("[CloudDownload] Found " + cloudTerms.size() + " new terms in cloud");

        updateNetworkStatus("Processando " + cloudTerms.size() + " novo(s) termo(s)...");
        return cloudTerms;
    }

    private DatabaseManager.SyncResult handleNoTermsToSync() {
        System.out.println("[CloudDownload] No new terms to sync");

        if (networkMonitor != null && callback != null) {
            callback.showStatusTemporarily("Nenhum termo novo para baixar", 2000);
        }

        return new DatabaseManager.SyncResult(0, 0);
    }

    private Set<String> loadLocalHashes() {
        System.out.println("[CloudDownload] Loading local hashes for duplicate detection...");
        Set<String> localHashes = callback != null ? callback.loadAllLocalHashes() : new HashSet<>();
        System.out.println("[CloudDownload] Loaded " + localHashes.size() + " local hashes");
        return localHashes;
    }

    private DatabaseManager.SyncResult performDryRun(List<SupabaseClient.TermDTO> cloudTerms, Set<String> localHashes) {
        int added = 0;
        int duplicates = 0;

        for (SupabaseClient.TermDTO term : cloudTerms) {
            String hash = ensureContentHash(term);

            if (hash != null && localHashes.contains(hash)) {
                duplicates++;
                if (duplicates <= 5) {
                    System.out.println("[DRY RUN] SKIP duplicate: " + term.source_term + " → " + term.target_term);
                }
            } else {
                added++;
                if (added <= 10) {
                    System.out.println("[DRY RUN] WOULD ADD: " + term.source_term + " → " + term.target_term);
                }
                if (added % 100 == 0) {
                    System.out.println("[DRY RUN] Would download " + added + " new terms so far...");
                }
            }
        }

        System.out.println("[CloudDownload] Sync complete: " + added + " added, " + duplicates + " duplicates skipped");
        return new DatabaseManager.SyncResult(added, duplicates);
    }

    private DatabaseManager.SyncResult performRealSync(List<SupabaseClient.TermDTO> cloudTerms, Set<String> localHashes) throws Exception {
        int added = 0;
        int duplicates = 0;

        String insertSQL = """
            INSERT OR IGNORE INTO terms (source_term, source_language, target_term,
                target_language, context, contributor, date_added, verified_status,
                notes, content_hash, owner, updated_at, cloud_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        beginTransaction();

        try (PreparedStatement pstmt = localConnection.prepareStatement(insertSQL)) {
            for (SupabaseClient.TermDTO term : cloudTerms) {
                String hash = ensureContentHash(term);

                if (hash != null && localHashes.contains(hash)) {
                    duplicates += handleDuplicateTerm(term);
                    continue;
                }

                insertTermToBatch(pstmt, term);
                if (hash != null) {
                    localHashes.add(hash);
                }
                added += executeBatchIfNeeded(pstmt, added, cloudTerms.size());
            }

            added += executeFinalBatch(pstmt);
            commitTransaction();
        }

        finalizeSyncWithCallback(added, duplicates);
        showSyncCompletionStatus(added, duplicates);

        return new DatabaseManager.SyncResult(added, duplicates);
    }

    private void beginTransaction() throws SQLException {
        synchronized (dbLock) {
            localConnection.setAutoCommit(false);
        }
    }

    private void commitTransaction() throws SQLException {
        synchronized (dbLock) {
            localConnection.commit();
            localConnection.setAutoCommit(true);
        }
        if (callback != null) {
            callback.updateLastSyncTime();
        }
    }

    private int handleDuplicateTerm(SupabaseClient.TermDTO term) {
        ensureContentHash(term);

        // For duplicates (hash matches), only update metadata if cloud_id is missing locally
        // This prevents 700k unnecessary updates when content is identical
        try {
            if (callback != null && term.id != null) {
                boolean updated = callback.updateMetadataOnlyFromCloud(term);
                if (updated) {
                    // Only log occasionally to avoid spam
                    if (Math.random() < 0.01) {  // Log ~1% of metadata updates
                        System.out.println("[CloudDownload] Updated cloud_id for existing term");
                    }
                }
                return 0;  // Not counted as duplicate in stats
            }
        } catch (SQLException e) {
            System.err.println("[CloudDownload] Error updating metadata: " + e.getMessage());
        }
        return 1;  // Count as duplicate
    }

    private void insertTermToBatch(PreparedStatement pstmt, SupabaseClient.TermDTO term) throws SQLException {
        String hash = ensureContentHash(term);

        pstmt.setString(1, term.source_term);
        pstmt.setString(2, term.source_language);
        pstmt.setString(3, term.target_term);
        pstmt.setString(4, term.target_language);
        pstmt.setString(5, term.context);
        pstmt.setString(6, term.contributor);
        pstmt.setString(7, term.date_added);
        pstmt.setString(8, term.verified_status);
        pstmt.setString(9, term.notes);
        pstmt.setString(10, hash);
        pstmt.setString(11, term.owner != null ? term.owner : "shared");
        pstmt.setString(12, term.updated_at != null ? term.updated_at : term.date_added);
        if (term.id != null) {
            pstmt.setInt(13, term.id);
        } else {
            pstmt.setNull(13, Types.INTEGER);
        }
        pstmt.addBatch();
    }

    private int executeBatchIfNeeded(PreparedStatement pstmt, int currentCount, int totalCount) throws SQLException {
        if ((currentCount + 1) % 100 == 0) {
            int added = countBatchResults(pstmt.executeBatch());
            synchronized (dbLock) {
                localConnection.commit();
            }
            System.out.println("[CloudDownload] Downloaded " + (currentCount + added) + " new terms so far...");
            updateNetworkStatus("Baixados " + (currentCount + added) + "/" + totalCount + " termos...");
            return added;
        }
        return 0;
    }

    private int executeFinalBatch(PreparedStatement pstmt) throws SQLException {
        int added = countBatchResults(pstmt.executeBatch());
        synchronized (dbLock) {
            localConnection.commit();
        }
        return added;
    }

    private int countBatchResults(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result > 0) count++;
        }
        return count;
    }

    private void finalizeSyncWithCallback(int added, int duplicates) {
        // Call onOwnerChanged if ANY terms were added or updated
        // This ensures owner dropdown refreshes even when only metadata (like owner) changed
        if ((added > 0 || duplicates > 0) && callback != null) {
            callback.onOwnerChanged();
        }
    }

    private void showSyncCompletionStatus(int added, int duplicates) {
        System.out.println("[CloudDownload] Sync complete: " + added + " added, " + duplicates + " duplicates skipped");

        if (networkMonitor != null && callback != null) {
            if (added > 0) {
                callback.showStatusTemporarily("Baixados " + added + " novo(s) termo(s)", 3000);
            } else {
                callback.showStatusTemporarily("Nenhum termo novo para baixar", 2000);
            }
        }
    }

    private void updateNetworkStatus(String message) {
        if (networkMonitor != null) {
            networkMonitor.setSyncing(message);
        }
    }

    private void handleSyncError(Exception e) {
        try {
            synchronized (dbLock) {
                localConnection.rollback();
                localConnection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            System.err.println("[CloudDownload] Rollback failed: " + ex.getMessage());
        }
        System.err.println("[CloudDownload] Sync from cloud failed: " + e.getMessage());
        e.printStackTrace();
    }

    /**
     * Download terms that exist in cloud but not locally.
     */
    public int downloadMissingTermsFromCloud() {
        int downloaded = 0;

        try {
            System.out.println("[CloudDownload] Fetching all cloud hashes...");

            // Get all hashes from cloud
            Set<String> cloudHashes = supabaseClient.getAllTermHashes();

            if (cloudHashes == null || cloudHashes.isEmpty()) {
                System.err.println("[CloudDownload] ⚠️ Could not fetch cloud hashes - skipping download");
                return 0;
            }

            System.out.println("[CloudDownload] Found " + cloudHashes.size() + " terms in cloud");

            // Get all local hashes
            System.out.println("[CloudDownload] Loading local hashes...");
            Set<String> localHashes = new HashSet<>();

            String sql = "SELECT content_hash FROM terms WHERE content_hash IS NOT NULL";
            try (PreparedStatement pstmt = localConnection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String hash = rs.getString("content_hash");
                    if (hash != null && !hash.trim().isEmpty()) {
                        localHashes.add(hash);
                    }
                }
            }

            System.out.println("[CloudDownload] Found " + localHashes.size() + " terms locally");

            // Find hashes in cloud that are NOT in local
            Set<String> missingHashes = new HashSet<>(cloudHashes);
            missingHashes.removeAll(localHashes);

            if (missingHashes.isEmpty()) {
                System.out.println("[CloudDownload] ✅ No missing terms to download");
                return 0;
            }

            System.out.println("[CloudDownload] 📥 Found " + missingHashes.size() + " missing terms to download");

            if (networkMonitor != null) {
                networkMonitor.setSyncing("Baixando " + missingHashes.size() + " termos ausentes...");
            }

            // Fetch missing terms from cloud in batches
            int batchSize = 100;
            List<String> missingList = new ArrayList<>(missingHashes);

            for (int i = 0; i < missingList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, missingList.size());
                List<String> batch = missingList.subList(i, end);

                System.out.println("[CloudDownload] Downloading batch " + ((i/batchSize) + 1) +
                        " (" + batch.size() + " terms)...");

                // Fetch terms by hashes
                List<SupabaseClient.TermDTO> terms = supabaseClient.getTermsByHashes(batch);

                if (terms == null || terms.isEmpty()) {
                    System.err.println("[CloudDownload] Failed to fetch batch");
                    continue;
                }

                // Insert each term locally
                for (SupabaseClient.TermDTO dto : terms) {
                    try {
                        insertTermFromCloud(dto);
                        downloaded++;

                        if (downloaded % 50 == 0) {
                            System.out.println("[CloudDownload] Downloaded " + downloaded + " terms so far...");
                            if (networkMonitor != null) {
                                networkMonitor.setSyncing("Baixados " + downloaded + " de " + missingHashes.size() + " termos...");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[CloudDownload] Error inserting term: " + e.getMessage());
                    }
                }

                // Small delay between batches
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("[CloudDownload] ✅ Downloaded " + downloaded + " missing terms from cloud");

            // Notify owner change if terms were downloaded
            if (downloaded > 0 && callback != null) {
                callback.onOwnerChanged();
            }

        } catch (Exception e) {
            System.err.println("[CloudDownload] Error downloading missing terms: " + e.getMessage());
            e.printStackTrace();
        }

        return downloaded;
    }

    /**
     * Insert a term from cloud DTO into local database.
     */
    public void insertTermFromCloud(SupabaseClient.TermDTO dto) throws SQLException {
        String hash = ensureContentHash(dto);

        String sql = """
        INSERT INTO terms (source_term, source_language, target_term, target_language,
                          context, contributor, notes, verified_status, date_added,
                          content_hash, owner, updated_at, cloud_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """; 

        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, dto.source_term);
            pstmt.setString(2, dto.source_language);
            pstmt.setString(3, dto.target_term);
            pstmt.setString(4, dto.target_language);
            pstmt.setString(5, dto.context);
            pstmt.setString(6, dto.contributor);
            pstmt.setString(7, dto.notes);
            pstmt.setString(8, dto.verified_status);
            pstmt.setString(9, dto.date_added);
            pstmt.setString(10, hash);
            pstmt.setString(11, dto.owner);
            pstmt.setString(12, dto.updated_at != null ? dto.updated_at : dto.date_added);
            if (dto.id != null) {
                pstmt.setInt(13, dto.id);
            } else {
                pstmt.setNull(13, Types.INTEGER);
            }

            pstmt.executeUpdate();
        }
    }

    /**
     * Public version of insertTermFromCloud for external use.
     */
    public void insertTermFromCloudPublic(SupabaseClient.TermDTO term) throws SQLException {
        insertTermFromCloud(term);
    }

    private String ensureContentHash(SupabaseClient.TermDTO term) {
        if (term == null) {
            return null;
        }

        if (term.content_hash == null || term.content_hash.isEmpty()) {
            term.content_hash = SupabaseHashService.generateHash(
                    term.source_term,
                    term.source_language,
                    term.target_term,
                    term.target_language,
                    term.context,
                    term.contributor
            );
        }
        return term.content_hash;
    }
}
