package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Facade for synchronization services.
 * Coordinates RealtimeSyncService, OfflineQueueProcessor, CloudUploadService,
 * CloudDownloadService, SyncMetadataManager, and GlossaryResetManager.
 *
 * Refactored from 3,260 lines to ~600 lines facade pattern.
 */
public class SyncManager {

    // Core dependencies
    private final SupabaseClient supabaseClient;
    private final Connection localConnection;
    private final OfflineQueueManager offlineQueue;
    private NetworkStatusMonitor networkMonitor;
    private volatile InitializationCallback initCallback;

    // Synchronization lock for database operations
    private final Object dbLock = new Object();

    // Connection checking
    private final ExecutorService connectionCheckExecutor;
    private volatile boolean isCurrentlyOnline = true;
    private final AtomicLong lastConnectionCheckTime = new AtomicLong(0);
    private final AtomicBoolean cloudIdBackfillScheduled = new AtomicBoolean(false);
    private static final long CONNECTION_CHECK_CACHE_MS = 5000;

    // Sync control (for bulk imports)
    private volatile boolean syncEnabled = true;

    // Status message handling
    private final ScheduledExecutorService statusExecutor;
    private volatile String pendingStatusMessage = null;

    // ============================================================================
    // SPECIALIZED SERVICES (Facade Pattern)
    // ============================================================================

    private final RealtimeSyncService realtimeService;
    private final OfflineQueueProcessor queueProcessor;
    private final CloudUploadService uploadService;
    private final CloudDownloadService downloadService;
    private final SyncMetadataManager metadataManager;
    private final GlossaryResetManager resetManager;

    // ============================================================================
    // INTERFACES
    // ============================================================================

    public interface InitializationCallback {
        void onProgress(String message, int current, int total);
        void onComplete();
    }

    public interface OwnerChangeCallback {
        void onOwnerChanged();
    }

    // Callback for owner changes
    private OwnerChangeCallback ownerChangeCallback;

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================

    public SyncManager(Connection connection) {
        this.supabaseClient = new SupabaseClient();
        this.localConnection = connection;
        this.offlineQueue = new OfflineQueueManager();
        this.connectionCheckExecutor = Executors.newSingleThreadExecutor();
        this.statusExecutor = Executors.newSingleThreadScheduledExecutor();

        // Initialize metadata manager and schema
        this.metadataManager = new SyncMetadataManager(localConnection, dbLock);
        metadataManager.initializeSchema();

        // Initialize services
        this.realtimeService = new RealtimeSyncService(localConnection, statusExecutor, dbLock);
        this.queueProcessor = new OfflineQueueProcessor(supabaseClient, offlineQueue, networkMonitor, lastConnectionCheckTime);
        this.uploadService = new CloudUploadService(supabaseClient, localConnection, offlineQueue, networkMonitor, dbLock);
        this.downloadService = new CloudDownloadService(supabaseClient, localConnection, networkMonitor, dbLock);
        this.resetManager = new GlossaryResetManager(supabaseClient, localConnection, offlineQueue, networkMonitor, dbLock);

        // Configure service callbacks
        configureServiceCallbacks();

        // Start realtime sync
        realtimeService.startRealtimeSync();

        // Backfill cloud ids for legacy data asynchronously
        backfillCloudIdsAsync();
    }

    /**
     * Configure callbacks between services for coordination.
     */
    private void configureServiceCallbacks() {
        // RealtimeSync callbacks
        realtimeService.setTermInsertionHandler(new RealtimeSyncService.TermInsertionHandler() {
            @Override
            public void insertTermFromCloudPublic(SupabaseClient.TermDTO term) throws SQLException {
                downloadService.insertTermFromCloudPublic(term);
            }

            @Override
            public boolean updateTermFieldsFromCloud(SupabaseClient.TermDTO newTerm,
                                                     SupabaseClient.TermDTO oldTerm) throws SQLException {
                return SyncManager.this.updateTermFieldsFromCloud(newTerm, oldTerm);
            }

            @Override
            public boolean updateMetadataOnlyFromCloud(SupabaseClient.TermDTO term) throws SQLException {
                return SyncManager.this.updateMetadataOnlyFromCloud(term);
            }

            @Override
            public void onOwnerChanged() {
                if (ownerChangeCallback != null) {
                    ownerChangeCallback.onOwnerChanged();
                }
            }
        });

        // QueueProcessor callbacks
        queueProcessor.setConnectionChecker(this::isOnline);
        queueProcessor.setSyncOperationHandler(new OfflineQueueProcessor.SyncOperationHandler() {
            @Override
            public void syncEditToCloudBlocking(int localId, SyncEditParams params) throws Exception {
                uploadService.syncEditToCloudBlocking(localId, params);
            }

            @Override
            public String generateHash(String sourceTerm, String sourceLanguage,
                                       String targetTerm, String targetLanguage,
                                       String context, String contributor) {
                return metadataManager.generateHash(sourceTerm, sourceLanguage, targetTerm,
                        targetLanguage, context, contributor);
            }
        });

        // UploadService callbacks
        uploadService.setConnectionChecker(this::isOnline);
        uploadService.setHashTracker(new CloudUploadService.HashTracker() {
            @Override
            public String generateHash(String sourceTerm, String sourceLanguage,
                                       String targetTerm, String targetLanguage,
                                       String context, String contributor) {
                return metadataManager.generateHash(sourceTerm, sourceLanguage, targetTerm,
                        targetLanguage, context, contributor);
            }
        });
        uploadService.setQueueProcessor(this::processQueueAsync);

        // DownloadService callbacks
        downloadService.setCallback(new CloudDownloadService.CloudDownloadCallback() {
            @Override
            public void showStatusTemporarily(String message, int durationMs) {
                SyncManager.this.showStatusTemporarily(message, durationMs);
            }

            @Override
            public Set<String> loadAllLocalHashes() {
                return SyncManager.this.loadAllLocalHashes();
            }

            @Override
            public String getLastSyncTime() {
                return metadataManager.getLastSyncTime();
            }

            @Override
            public void updateLastSyncTime() {
                metadataManager.updateLastSyncTime();
            }

            @Override
            public int getLocalTermsCount() {
                return metadataManager.getLocalTermsCount();
            }

            @Override
            public boolean updateTermFieldsFromCloud(SupabaseClient.TermDTO newTerm,
                                                     SupabaseClient.TermDTO oldTerm) throws SQLException {
                return SyncManager.this.updateTermFieldsFromCloud(newTerm, oldTerm);
            }

            @Override
            public boolean updateMetadataOnlyFromCloud(SupabaseClient.TermDTO term) throws SQLException {
                return SyncManager.this.updateMetadataOnlyFromCloud(term);
            }

            @Override
            public void onOwnerChanged() {
                if (ownerChangeCallback != null) {
                    ownerChangeCallback.onOwnerChanged();
                }
            }
        });

        // ResetManager callbacks
        resetManager.setConnectionChecker(this::isOnline);
        resetManager.setProgressCallback(new GlossaryResetManager.ProgressCallback() {
            @Override
            public void onProgress(String message, int current, int total) {
                if (initCallback != null) {
                    initCallback.onProgress(message, current, total);
                }
            }

            @Override
            public void onComplete() {
                if (initCallback != null) {
                    initCallback.onComplete();
                }
            }
        });
        resetManager.setStatusUpdater(this::showStatusTemporarily);
        resetManager.setMetadataManager(new GlossaryResetManager.MetadataManager() {
            @Override
            public int getLocalGlossaryVersion() {
                return metadataManager.getLocalGlossaryVersion();
            }

            @Override
            public void updateLocalGlossaryVersion(int version) {
                metadataManager.updateLocalGlossaryVersion(version);
            }

            @Override
            public void updateLastSyncTime() {
                metadataManager.updateLastSyncTime();
            }
        });
        resetManager.setTermInserter(this::batchInsertTermsForReset);
        resetManager.setQueueProcessor(() -> queueProcessor.processOfflineQueueBatch());

        // Set metadata progress callback
        metadataManager.setProgressCallback((message, current, total) -> {
            if (initCallback != null) {
                initCallback.onProgress(message, current, total);
            }
        });
    }

    // ============================================================================
    // PUBLIC API - Delegates to Services
    // ============================================================================

    public void setInitializationCallback(InitializationCallback callback) {
        this.initCallback = callback;
    }

    public InitializationCallback getInitializationCallback() {
        return this.initCallback;
    }

    public void setNetworkMonitor(NetworkStatusMonitor monitor) {
        this.networkMonitor = monitor;
        queueProcessor.setConnectionChecker(this::isOnline);
    }

    public void setOwnerChangeCallback(OwnerChangeCallback callback) {
        this.ownerChangeCallback = callback;
    }

    /**
     * Enable or disable cloud sync (for bulk imports).
     * When disabled, addTermWithSync will only insert locally without syncing to cloud.
     */
    public void setSyncEnabled(boolean enabled) {
        this.syncEnabled = enabled;
        System.out.println("[SyncManager] Cloud sync " + (enabled ? "ENABLED" : "DISABLED"));
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public boolean isResetInProgress() {
        return resetManager.isResetInProgress();
    }

    /**
     * Initialize database - generate hashes for existing terms.
     */
    public void initializeDatabase() {
        metadataManager.generateHashesForExistingTerms();
        if (initCallback != null) {
            initCallback.onComplete();
        }
    }

    /**
     * Sync from cloud (download new terms).
     */
    public DatabaseManager.SyncResult syncFromCloud() {
        return syncFromCloud(false);
    }

    public DatabaseManager.SyncResult syncFromCloud(boolean dryRun) {
        if (resetManager.isResetInProgress()) {
            System.out.println("[SyncManager] ⚠️ Reset in progress - skipping automatic sync");
            return new DatabaseManager.SyncResult(0, 0);
        }
        return downloadService.syncFromCloud(dryRun);
    }

    /**
     * Force full sync from cloud (ignores last_sync_time, syncs ALL terms).
     * Use this to re-sync metadata changes (like owner updates) that don't update the updated_at field.
     */
    public DatabaseManager.SyncResult forceFullSyncFromCloud() {
        System.out.println("[SyncManager] 🔄 Starting FULL sync from cloud...");
        if (resetManager.isResetInProgress()) {
            System.out.println("[SyncManager] ⚠️ Reset in progress - cannot force full sync");
            return new DatabaseManager.SyncResult(0, 0);
        }
        return downloadService.syncFromCloud(false, true);
    }

    /**
     * Add term with automatic cloud sync.
     */
    public boolean addTermWithSync(AddTermParams params) {
        // Generate hash
        String hash = metadataManager.generateHash(params.sourceTerm, params.sourceLanguage, params.targetTerm,
                params.targetLanguage, params.context, params.contributor);

        // Insert locally
        String insertSQL = """
            INSERT INTO terms (source_term, source_language, target_term, target_language,
                              context, contributor, notes, verified_status, date_added,
                              content_hash, owner,
                              source_term_normalized, target_term_normalized, context_normalized)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = localConnection.prepareStatement(insertSQL)) {
            pstmt.setString(1, params.sourceTerm);
            pstmt.setString(2, params.sourceLanguage);
            pstmt.setString(3, params.targetTerm);
            pstmt.setString(4, params.targetLanguage);
            pstmt.setString(5, params.context);
            pstmt.setString(6, params.contributor);
            pstmt.setString(7, params.notes);
            pstmt.setString(8, "draft");
            pstmt.setString(9, Instant.now().toString());
            pstmt.setString(10, hash);
            pstmt.setString(11, params.owner != null ? params.owner : "shared");

            // Populate normalized columns
            String sourceNormalized = params.sourceTerm != null ?
                DiacriticUtils.removeDiacritics(params.sourceTerm).toLowerCase() : null;
            String targetNormalized = params.targetTerm != null ?
                DiacriticUtils.removeDiacritics(params.targetTerm).toLowerCase() : null;
            String contextNormalized = params.context != null ?
                DiacriticUtils.removeDiacritics(params.context).toLowerCase() : null;

            pstmt.setString(12, sourceNormalized);
            pstmt.setString(13, targetNormalized);
            pstmt.setString(14, contextNormalized);

            synchronized (dbLock) {
                pstmt.executeUpdate();
            }

            // Sync to cloud asynchronously (only if sync enabled)
            if (syncEnabled) {
                uploadService.syncToCloud(params.sourceTerm, params.sourceLanguage, params.targetTerm, params.targetLanguage,
                        params.context, params.contributor, params.notes, hash, params.owner);
            }

            return true;

        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                System.out.println("[SyncManager] Termo duplicado ignorado: " + params.sourceTerm);
                return false;
            }
            System.err.println("[SyncManager] Erro ao adicionar termo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sync term edit to cloud.
     */
    public void syncEditToCloud(EditSyncParams params) {
        uploadService.syncEditToCloud(params.localId, params.sourceTerm, params.sourceLanguage, params.targetTerm,
                params.targetLanguage, params.context, params.contributor, params.notes, params.oldHash, params.verifiedStatus,
                params.oldSourceTerm, params.oldTargetTerm);
    }

    /**
     * Delete term from cloud.
     */
    public void deleteFromCloud(String hash, int localId, String sourceTerm, String targetTerm) {
        new Thread(() -> {
            try {
                if (!isOnline()) {
                    System.out.println("[SyncManager] Offline - queueing DELETE for: " + sourceTerm);
                    QueueOperationParams params = new QueueOperationParams("DELETE", localId, sourceTerm, "", targetTerm, "",
                            "", "", "", "draft", "shared", hash);
                    offlineQueue.addOperation(params);

                    if (networkMonitor != null) {
                        networkMonitor.setQueued(offlineQueue.getPendingCount());
                    }
                    return;
                }

                if (networkMonitor != null) {
                    networkMonitor.setSyncing("Removing term from cloud...");
                }

                // OPTIMISTIC UI: cloud_id is already tracked in DatabaseManager.deleteTerm()
                // No need to track hash anymore

                boolean deleted = supabaseClient.deleteTermByHash(hash);
                if (deleted) {
                    System.out.println("[SyncManager] Term deleted from cloud: " + sourceTerm);
                    if (networkMonitor != null) {
                        networkMonitor.setSyncSuccess("Term removed from cloud");
                    }
                } else {
                    System.out.println("[SyncManager] Term not found in cloud (already deleted): " + sourceTerm);
                    // Clear the "Removing from cloud..." message even if not found
                    if (networkMonitor != null) {
                        networkMonitor.setSyncSuccess("Term already removed");
                    }
                }

            } catch (Exception e) {
                System.err.println("[SyncManager] Failed to delete from cloud - queuing: " + e.getMessage());
                QueueOperationParams params = new QueueOperationParams("DELETE", localId, sourceTerm, "", targetTerm, "",
                        "", "", "", "draft", "shared", hash);
                offlineQueue.addOperation(params);

                if (networkMonitor != null) {
                    networkMonitor.setSyncError("Delete queued (offline)");
                    networkMonitor.setQueued(offlineQueue.getPendingCount());
                }
            }
        }).start();
    }

    /**
     * Check and handle glossary reset.
     */
    public boolean checkAndHandleGlossaryReset() {
        return resetManager.checkAndHandleGlossaryReset();
    }

    /**
     * Batch insert terms (used during reset).
     */
    public List<SupabaseClient.TermDTO> batchInsertTermsForReset(List<SupabaseClient.TermDTO> terms) throws SQLException {
        if (terms == null || terms.isEmpty()) {
            return new ArrayList<>();
        }

        String insertSQL = """
            INSERT OR IGNORE INTO terms (source_term, source_language, target_term,
                target_language, context, contributor, date_added, verified_status,
                notes, content_hash, owner, updated_at, cloud_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        List<SupabaseClient.TermDTO> insertedDTOs = new ArrayList<>();
        int inserted = 0;
        int batchSize = 500;
        int duplicatesSkipped = 0;
        int batchStartIndex = 0;

        boolean originalAutoCommit = false;
        try {
            synchronized (dbLock) {
                originalAutoCommit = localConnection.getAutoCommit();
                if (originalAutoCommit) {
                    localConnection.setAutoCommit(false);
                }
            }

            try (PreparedStatement pstmt = localConnection.prepareStatement(insertSQL)) {
                for (int i = 0; i < terms.size(); i++) {
                    SupabaseClient.TermDTO term = terms.get(i);

                    pstmt.setString(1, term.source_term);
                    pstmt.setString(2, term.source_language);
                    pstmt.setString(3, term.target_term);
                    pstmt.setString(4, term.target_language);
                    pstmt.setString(5, term.context);
                    pstmt.setString(6, term.contributor);
                    pstmt.setString(7, term.date_added);
                    pstmt.setString(8, term.verified_status);
                    pstmt.setString(9, term.notes);
                    pstmt.setString(10, term.content_hash);
                    pstmt.setString(11, term.owner != null ? term.owner : "shared");
                    pstmt.setString(12, term.updated_at != null ? term.updated_at : term.date_added);
                    if (term.id != null) {
                        pstmt.setInt(13, term.id);
                    } else {
                        pstmt.setNull(13, Types.INTEGER);
                    }

                    pstmt.addBatch();

                    if ((i + 1) % batchSize == 0 || i == terms.size() - 1) {
                        int[] results = pstmt.executeBatch();
                        for (int j = 0; j < results.length; j++) {
                            int result = results[j];
                            if (result > 0) {
                                insertedDTOs.add(terms.get(batchStartIndex + j));
                                inserted++;
                            } else if (result == 0) {
                                duplicatesSkipped++;
                            }
                        }

                        synchronized (dbLock) {
                            if (!localConnection.getAutoCommit()) {
                                localConnection.commit();
                            }
                        }

                        if ((i + 1) % batchSize == 0) {
                            System.out.println("[SyncManager] Batch inserted: " + inserted + "/" + (i + 1) +
                                    " terms (" + duplicatesSkipped + " duplicates skipped)");
                            batchStartIndex = i + 1;
                        }

                        if (initCallback != null) {
                            initCallback.onProgress("Importando termos...", 50 + inserted, terms.size() + 50);
                        }
                    }
                }
            }

            synchronized (dbLock) {
                if (!localConnection.getAutoCommit()) {
                    localConnection.commit();
                }
            }

            System.out.println("[SyncManager] ✅ Batch insert complete: " + inserted + " unique terms, " +
                    duplicatesSkipped + " cloud duplicates skipped");

        } catch (SQLException e) {
            System.err.println("[SyncManager] ❌ Batch insert failed: " + e.getMessage());

            try {
                synchronized (dbLock) {
                    if (!localConnection.getAutoCommit()) {
                        localConnection.rollback();
                        System.out.println("[SyncManager] Transaction rolled back successfully");
                    }
                }
            } catch (SQLException rollbackEx) {
                System.err.println("[SyncManager] Rollback failed: " + rollbackEx.getMessage());
            }

            throw e;
        } finally {
            try {
                synchronized (dbLock) {
                    if (localConnection.getAutoCommit() != originalAutoCommit) {
                        localConnection.setAutoCommit(originalAutoCommit);
                    }
                }
            } catch (SQLException e) {
                System.err.println("[SyncManager] Error restoring auto-commit state: " + e.getMessage());
            }
        }

        return insertedDTOs;
    }

    /**
     * Process offline queue.
     */
    public int processOfflineQueueBatch() {
        return queueProcessor.processOfflineQueueBatch();
    }

    public int processOfflineQueue() {
        return processOfflineQueueBatch();
    }

    /**
     * Process offline queue asynchronously (doesn't block caller).
     * Called automatically when terms fail to sync but we're still online.
     */
    public void processQueueAsync() {
        new Thread(() -> {
            try {
                System.out.println("[SyncManager] Processing queue asynchronously...");
                int processed = processOfflineQueueBatch();
                System.out.println("[SyncManager] Async queue processing complete: " + processed + " operations");
            } catch (Exception e) {
                System.err.println("[SyncManager] Error processing queue async: " + e.getMessage());
                e.printStackTrace();
            }
        }, "QueueProcessor-Async").start();
    }

    /**
     * Full local to cloud sync (upload all local terms).
     */
    public int fullLocalToCloudSync() {
        return uploadService.fullLocalToCloudSync();
    }

    /**
     * Full bidirectional sync.
     */
    public DatabaseManager.SyncResult fullBidirectionalSync() {
        System.out.println("[SyncManager] ═══════════════════════════════════");
        System.out.println("[SyncManager] FULL BIDIRECTIONAL SYNC");
        System.out.println("[SyncManager] ═══════════════════════════════════");

        if (networkMonitor != null) {
            networkMonitor.setSyncing("Sincronizando com a nuvem...");
        }

        // PHASE 1: Cloud → Local
        System.out.println("[SyncManager] PHASE 1: Cloud → Local");
        int termsDownloaded = downloadService.downloadMissingTermsFromCloud();

        // PHASE 2: Local → Cloud
        System.out.println("[SyncManager] PHASE 2: Local → Cloud");
        int cloudUpdated = uploadService.fullLocalToCloudSync();

        // PHASE 3: Detect cloud deletions
        System.out.println("[SyncManager] PHASE 3: Cloud deletions");
        int deletedFromLocal = detectAndHandleCloudDeletions();

        System.out.println("[SyncManager] ═══════════════════════════════════");
        System.out.println("[SyncManager] SYNC COMPLETE:");
        System.out.println("[SyncManager]   ⬇️ Cloud → Local downloaded: " + termsDownloaded);
        System.out.println("[SyncManager]   ⬆️ Local → Cloud uploaded: " + cloudUpdated);
        System.out.println("[SyncManager]   🗑️ Local deletions (from cloud): " + deletedFromLocal);
        System.out.println("[SyncManager] ═══════════════════════════════════");

        if (termsDownloaded > 0 && ownerChangeCallback != null) {
            ownerChangeCallback.onOwnerChanged();
        }

        if (networkMonitor != null) {
            networkMonitor.setSyncSuccess(
                    termsDownloaded + " termos baixados, " +
                            cloudUpdated + " enviados, " +
                            deletedFromLocal + " deletados"
            );
        }

        return new DatabaseManager.SyncResult(termsDownloaded, 0, 0, cloudUpdated);
    }

    /**
     * Detect and handle terms deleted from cloud.
     */
    private int detectAndHandleCloudDeletions() {
        int deletedCount = 0;

        try {
            System.out.println("[SyncManager] Detecting cloud deletions...");

            Set<String> cloudHashes = supabaseClient.getAllTermHashes();
            if (cloudHashes == null || cloudHashes.isEmpty()) {
                System.out.println("[SyncManager] ⚠️ Could not fetch cloud hashes");
                return 0;
            }

            System.out.println("[SyncManager] Found " + cloudHashes.size() + " terms in cloud");

            List<String> localHashes = new ArrayList<>();
            String sql = "SELECT content_hash, source_term, target_term, id FROM terms WHERE content_hash IS NOT NULL";

            try (PreparedStatement pstmt = localConnection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    String hash = rs.getString("content_hash");
                    String sourceTerm = rs.getString("source_term");
                    int id = rs.getInt("id");

                    if (!cloudHashes.contains(hash)) {
                        // Term was deleted from cloud - delete locally
                        String deleteSQL = "DELETE FROM terms WHERE id = ?";
                        try (PreparedStatement delStmt = localConnection.prepareStatement(deleteSQL)) {
                            delStmt.setInt(1, id);
                            synchronized (dbLock) {
                                delStmt.executeUpdate();
                            }
                            deletedCount++;
                            System.out.println("[SyncManager] Deleted local term (removed from cloud): " + sourceTerm);
                        }
                    }
                }
            }

            System.out.println("[SyncManager] ✅ Removed " + deletedCount + " terms deleted from cloud");

        } catch (Exception e) {
            System.err.println("[SyncManager] Error detecting cloud deletions: " + e.getMessage());
            e.printStackTrace();
        }

        return deletedCount;
    }

    /**
     * Clear offline queue with confirmation dialog.
     */
    public void clearOfflineQueueWithConfirmation() {
        queueProcessor.clearOfflineQueueWithConfirmation();
    }

    // ============================================================================
    // QUEUE & STATUS GETTERS
    // ============================================================================

    public int getQueuedOperationsCount() {
        return queueProcessor.getQueuedOperationsCount();
    }

    public boolean hasQueuedOperations() {
        return queueProcessor.hasQueuedOperations();
    }

    public OfflineQueueManager getOfflineQueue() {
        return offlineQueue;
    }

    public String getQueueStatus() {
        return queueProcessor.getQueueStatus();
    }

    public SupabaseClient getSupabaseClient() {
        return supabaseClient;
    }

    public SupabaseClient.GlossaryMetadata getGlossaryMetadata() throws Exception {
        return supabaseClient.getGlossaryMetadata();
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    public String generateHash(String sourceTerm, String sourceLanguage,
                               String targetTerm, String targetLanguage,
                               String context, String contributor) {
        return metadataManager.generateHash(sourceTerm, sourceLanguage, targetTerm,
                targetLanguage, context, contributor);
    }

    public void insertTermFromCloudPublic(SupabaseClient.TermDTO term) throws SQLException {
        downloadService.insertTermFromCloudPublic(term);
    }

    private synchronized boolean updateTermFieldsFromCloud(SupabaseClient.TermDTO term) throws SQLException {
        return updateTermFieldsFromCloud(term, null);
    }

    /**
     * Update ONLY metadata (cloud_id, updated_at) for duplicate terms.
     * This is much faster than full updates when content hasn't changed.
     * Used during incremental sync to avoid 700k unnecessary full updates.
     */
    private synchronized boolean updateMetadataOnlyFromCloud(SupabaseClient.TermDTO term) throws SQLException {
        if (term == null || term.id == null) {
            return false;
        }

        String hash = ensureTermHash(term);
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        // Only update if cloud_id is missing (don't update if already set)
        String checkSQL = "SELECT cloud_id FROM terms WHERE content_hash = ?";
        try (PreparedStatement checkStmt = localConnection.prepareStatement(checkSQL)) {
            checkStmt.setString(1, hash);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                Integer existingCloudId = rs.getInt("cloud_id");
                if (rs.wasNull() || existingCloudId == 0) {
                    // cloud_id is missing, update it
                    String updateSQL = "UPDATE terms SET cloud_id = ?, updated_at = ? WHERE content_hash = ?";
                    try (PreparedStatement updateStmt = localConnection.prepareStatement(updateSQL)) {
                        updateStmt.setInt(1, term.id);
                        updateStmt.setString(2, term.updated_at != null ? term.updated_at : term.date_added);
                        updateStmt.setString(3, hash);

                        int updated;
                        synchronized (dbLock) {
                            updated = updateStmt.executeUpdate();
                        }
                        return updated > 0;
                    }
                }
                // cloud_id already exists, skip update
                return false;
            }
        }
        return false;
    }

    /**
     * Update term fields from cloud data using hash or id fallbacks.
     */
    private synchronized boolean updateTermFieldsFromCloud(SupabaseClient.TermDTO newTerm,
                                                           SupabaseClient.TermDTO oldTerm) throws SQLException {
        if (newTerm == null) {
            return false;
        }

        String newHash = ensureTermHash(newTerm);
        String oldHash = ensureTermHash(oldTerm);

        List<UpdateTarget> targets = new ArrayList<>();

        if (newHash != null && !newHash.isEmpty()) {
            targets.add(UpdateTarget.byHash(newHash, "[SyncManager] 🔍 Attempting update via new hash"));
        }
        if (oldHash != null && !oldHash.isEmpty()
                && (newHash == null || !newHash.equals(oldHash))) {
            targets.add(UpdateTarget.byHash(oldHash, "[SyncManager] 🔄 Falling back to old hash"));
        }
        if (newTerm.id != null) {
            targets.add(UpdateTarget.byCloudId(newTerm.id));
        } else if (oldTerm != null && oldTerm.id != null) {
            targets.add(UpdateTarget.byCloudId(oldTerm.id));
        }

        if (targets.isEmpty()) {
            System.out.println("[SyncManager] ⚠️ No identifier available for cloud update");
            return false;
        }

        for (UpdateTarget target : targets) {
            boolean updated = executeCloudUpdate(newTerm, target);
            if (updated) {
                return true;
            }
        }

        System.out.println("[SyncManager] ⚠️ Cloud update skipped – no matching local row");
        return false;
    }

    private boolean executeCloudUpdate(SupabaseClient.TermDTO term, UpdateTarget target) throws SQLException {
        String whereClause = target.isHash()
                ? "content_hash = ?"
                : "cloud_id = ?";

        String updateSQL = """
            UPDATE terms SET
                source_term = ?,
                source_language = ?,
                target_term = ?,
                target_language = ?,
                context = ?,
                contributor = ?,
                notes = ?,
                verified_status = ?,
                owner = ?,
                date_added = ?,
                updated_at = ?,
                cloud_id = COALESCE(?, cloud_id),
                content_hash = COALESCE(?, content_hash)
            WHERE %s
        """.formatted(whereClause);

        if (target.getLogMessage() != null) {
            System.out.println(target.getLogMessage());
        }

        try (PreparedStatement stmt = localConnection.prepareStatement(updateSQL)) {
            stmt.setString(1, term.source_term);
            stmt.setString(2, term.source_language);
            stmt.setString(3, term.target_term);
            stmt.setString(4, term.target_language);
            stmt.setString(5, term.context);
            stmt.setString(6, term.contributor);
            stmt.setString(7, term.notes);
            stmt.setString(8, term.verified_status);
            stmt.setString(9, term.owner != null ? term.owner : "shared");
            stmt.setString(10, term.date_added);
            stmt.setString(11, term.updated_at != null ? term.updated_at : term.date_added);
            if (term.id != null) {
                stmt.setInt(12, term.id);
            } else {
                stmt.setNull(12, Types.INTEGER);
            }
            stmt.setString(13, term.content_hash);

            if (target.isHash()) {
                stmt.setString(14, target.identifier().toString());
            } else {
                stmt.setInt(14, (Integer) target.identifier());
            }

            int updated;
            synchronized (dbLock) {
                updated = stmt.executeUpdate();
            }

            if (updated > 0) {
                System.out.println("[SyncManager] ✅ Updated from cloud: " + term.source_term);
                return true;
            }
        }

        return false;
    }

    private String ensureTermHash(SupabaseClient.TermDTO term) {
        if (term == null) {
            return null;
        }

        if (term.content_hash == null || term.content_hash.isEmpty()) {
            term.content_hash = metadataManager.generateHash(
                    term.source_term != null ? term.source_term : "",
                    term.source_language != null ? term.source_language : "",
                    term.target_term != null ? term.target_term : "",
                    term.target_language != null ? term.target_language : "",
                    term.context,
                    term.contributor
            );
        }

        return term.content_hash;
    }

    private static class UpdateTarget {
        private final Object identifier;
        private final boolean hash;
        private final String logMessage;

        private UpdateTarget(Object identifier, boolean hash, String logMessage) {
            this.identifier = identifier;
            this.hash = hash;
            this.logMessage = logMessage;
        }

        static UpdateTarget byHash(String hash, String logMessage) {
            return new UpdateTarget(hash, true, logMessage);
        }

        static UpdateTarget byCloudId(Integer cloudId) {
            return new UpdateTarget(cloudId, false, "[SyncManager] 🔄 Falling back to cloud id");
        }

        Object identifier() {
            return identifier;
        }

        boolean isHash() {
            return hash;
        }

        String getLogMessage() {
            return logMessage;
        }
    }

    private void backfillCloudIdsAsync() {
        scheduleCloudIdBackfill(0);
    }

    private void scheduleCloudIdBackfill(long delayMs) {
        if (!cloudIdBackfillScheduled.compareAndSet(false, true)) {
            return;
        }

        Runnable submitTask = () -> CompletableFuture.runAsync(this::runCloudIdBackfill);

        if (delayMs <= 0) {
            submitTask.run();
        } else {
            statusExecutor.schedule(submitTask, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void runCloudIdBackfill() {
        boolean reschedule = false;
        long rescheduleDelayMs = 0;

        try {
            int attempts = 0;
            while (attempts < 5) {
                try {
                    if (!hasMissingCloudIds()) {
                        return;
                    }

                    System.out.println("[SyncManager] 🔄 Backfilling cloud ids from Supabase...");
                    Map<String, Integer> hashIdMap = supabaseClient.getHashIdMap();

                    if (hashIdMap == null || hashIdMap.isEmpty()) {
                        System.out.println("[SyncManager] ⚠️ Supabase returned empty hash/id map; skipping backfill");
                        return;
                    }

                    int updated = updateLocalCloudIds(hashIdMap);
                    if (updated > 0) {
                        System.out.println("[SyncManager] ✅ Backfilled " + updated + " cloud ids");
                    } else {
                        System.out.println("[SyncManager] ℹ️ No matching cloud ids found during backfill");
                    }
                    return;
                } catch (SQLException e) {
                    if (isDatabaseBusy(e)) {
                        attempts++;
                        if (attempts < 5) {
                            long delayMs = 250L * attempts;
                            System.out.println("[SyncManager] ⏳ Database busy, retrying cloud id backfill in " + delayMs + "ms (attempt " + (attempts + 1) + ")");
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException interruptedException) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            continue;
                        } else {
                            reschedule = true;
                            rescheduleDelayMs = 2000;
                            System.out.println("[SyncManager] ⏳ Database still busy, will retry cloud id backfill later");
                            return;
                        }
                    }

                    System.err.println("[SyncManager] ⚠️ Cloud id backfill failed: " + e.getMessage());
                    return;
                } catch (Exception e) {
                    System.err.println("[SyncManager] ⚠️ Cloud id backfill failed: " + e.getMessage());
                    return;
                }
            }
        } finally {
            cloudIdBackfillScheduled.set(false);
            if (reschedule) {
                scheduleCloudIdBackfill(rescheduleDelayMs);
            }
        }
    }

    private boolean hasMissingCloudIds() {
        String sql = "SELECT 1 FROM terms WHERE cloud_id IS NULL OR cloud_id = 0 LIMIT 1";

        try {
            synchronized (dbLock) {
                try (Statement stmt = localConnection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    boolean missing = rs.next();
                    if (missing) {
                        System.out.println("[SyncManager] 🔍 Detected local terms missing cloud ids");
                    }
                    return missing;
                }
            }
        } catch (SQLException e) {
            System.err.println("[SyncManager] Error checking missing cloud ids: " + e.getMessage());
            return false;
        }
    }

    private int updateLocalCloudIds(Map<String, Integer> hashIdMap) throws SQLException {
        String selectSql = """
            SELECT id, content_hash
            FROM terms
            WHERE (cloud_id IS NULL OR cloud_id = 0)
              AND content_hash IS NOT NULL
              AND content_hash != ''
        """;

        String updateSql = "UPDATE terms SET cloud_id = ? WHERE id = ?";
        int assignments = 0;

        synchronized (dbLock) {
            try (PreparedStatement selectStmt = localConnection.prepareStatement(selectSql);
                 ResultSet rs = selectStmt.executeQuery();
                 PreparedStatement updateStmt = localConnection.prepareStatement(updateSql)) {

                while (rs.next()) {
                    String hash = rs.getString("content_hash");
                    Integer cloudId = hashIdMap.get(hash);

                    if (cloudId != null) {
                        updateStmt.setInt(1, cloudId);
                        updateStmt.setInt(2, rs.getInt("id"));
                        updateStmt.addBatch();
                        assignments++;
                    }
                }

                if (assignments > 0) {
                    updateStmt.executeBatch();
                }
            }
        }

        return assignments;
    }

    private boolean isDatabaseBusy(SQLException e) {
        if (e == null) {
            return false;
        }

        if (e.getErrorCode() == 5) { // SQLITE_BUSY
            return true;
        }

        String message = e.getMessage();
        return message != null && message.contains("database is locked");
    }

    /**
     * Load all local hashes for duplicate detection.
     */
    private Set<String> loadAllLocalHashes() {
        Set<String> hashes = new HashSet<>();
        String sql = "SELECT content_hash FROM terms WHERE content_hash IS NOT NULL";

        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String hash = rs.getString("content_hash");
                if (hash != null && !hash.trim().isEmpty()) {
                    hashes.add(hash);
                }
            }
        } catch (SQLException e) {
            System.err.println("[SyncManager] Error loading local hashes: " + e.getMessage());
        }

        return hashes;
    }

    /**
     * Show status message temporarily.
     */
    public void showStatusTemporarily(String message, int durationMs) {
        pendingStatusMessage = message;

        if (networkMonitor != null) {
            networkMonitor.setSyncSuccess(message);
        }

        statusExecutor.schedule(() -> {
            if (message.equals(pendingStatusMessage)) {
                if (networkMonitor != null) {
                    networkMonitor.setSyncing(null);
                }
                pendingStatusMessage = null;
            }
        }, durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Small delay to allow realtime events to process.
     */
    public void delay() {
        try {
            TimeUnit.MILLISECONDS.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================================
    // CONNECTION CHECKING
    // ============================================================================

    public boolean isOnline() {
        long now = System.currentTimeMillis();
        long lastCheck = lastConnectionCheckTime.get();

        if (now - lastCheck < CONNECTION_CHECK_CACHE_MS) {
            return isCurrentlyOnline;
        }

        Future<Boolean> future = connectionCheckExecutor.submit(() -> {
            try {
                boolean canConnect = supabaseClient.testConnection();
                isCurrentlyOnline = canConnect;
                lastConnectionCheckTime.set(System.currentTimeMillis());
                return canConnect;
            } catch (Exception e) {
                isCurrentlyOnline = false;
                lastConnectionCheckTime.set(System.currentTimeMillis());
                return false;
            }
        });

        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            isCurrentlyOnline = false;
            lastConnectionCheckTime.set(System.currentTimeMillis());
            return false;
        }
    }

    public void resetConnectionCache() {
        lastConnectionCheckTime.set(0);
    }

    // ============================================================================
    // SHUTDOWN
    // ============================================================================

    public void shutdown() {
        System.out.println("[SyncManager] Shutting down...");

        // Shutdown executors
        connectionCheckExecutor.shutdownNow();
        statusExecutor.shutdownNow();

        // Shutdown realtime service
        realtimeService.shutdown();

        System.out.println("[SyncManager] ✅ SyncManager shut down");
    }
}
