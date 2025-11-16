package com.example.glossariobuda;

import com.example.glossariobuda.exceptions.CloudOperationException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for uploading data to cloud (Supabase).
 * Handles ADD and EDIT operations synchronization.
 */
public class CloudUploadService {

    private static final int BATCH_SIZE = 50;

    private final SupabaseClient supabaseClient;
    private final Connection localConnection;
    private final OfflineQueueManager offlineQueue;
    private final NetworkStatusMonitor networkMonitor;
    private final Object dbLock;

    // Callback interfaces
    public interface ConnectionChecker {
        boolean isOnline();
    }

    public interface HashTracker {
        String generateHash(String sourceTerm, String sourceLanguage,
                            String targetTerm, String targetLanguage,
                            String context, String contributor);
    }

    public interface QueueProcessor {
        void processQueueAsync();
    }

    private ConnectionChecker connectionChecker;
    private HashTracker hashTracker;
    private QueueProcessor queueProcessor;

    public CloudUploadService(SupabaseClient supabaseClient,
                               Connection localConnection,
                               OfflineQueueManager offlineQueue,
                               NetworkStatusMonitor networkMonitor,
                               Object dbLock) {
        this.supabaseClient = supabaseClient;
        this.localConnection = localConnection;
        this.offlineQueue = offlineQueue;
        this.networkMonitor = networkMonitor;
        this.dbLock = dbLock;
    }

    public void setConnectionChecker(ConnectionChecker checker) {
        this.connectionChecker = checker;
    }

    public void setHashTracker(HashTracker tracker) {
        this.hashTracker = tracker;
    }

    public void setQueueProcessor(QueueProcessor processor) {
        this.queueProcessor = processor;
    }

    /**
     * Sync new term to cloud (async).
     */
    public void syncToCloud(String sourceTerm, String sourceLanguage,
                            String targetTerm, String targetLanguage,
                            String context, String contributor,
                            String notes, String hash, String owner) {
        new Thread(() -> {
            try {
                if (connectionChecker != null && !connectionChecker.isOnline()) {
                    System.out.println("[CloudUpload] Offline - adicionando à fila: " + sourceTerm);
                    QueueOperationParams params = new QueueOperationParams("ADD", 0, sourceTerm, sourceLanguage,
                            targetTerm, targetLanguage, context, contributor, notes, "draft", owner, hash);
                    offlineQueue.addOperation(params);

                    if (networkMonitor != null) {
                        networkMonitor.setQueued(offlineQueue.getPendingCount());
                    }
                    return;
                }

                if (networkMonitor != null) {
                    networkMonitor.setSyncing("Adicionando termo...");
                }

                SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
                dto.source_term = sourceTerm;
                dto.source_language = sourceLanguage;
                dto.target_term = targetTerm;
                dto.target_language = targetLanguage;
                dto.context = context;
                dto.contributor = contributor;
                dto.notes = notes;
                dto.content_hash = hash;
                dto.verified_status = "draft";
                dto.date_added = Instant.now().toString();
                dto.owner = owner != null ? owner : "shared";

                boolean success = supabaseClient.insertTerm(dto);
                if (success) {
                    System.out.println("[CloudUpload] Termo sincronizado: " + sourceTerm);
                    try {
                        SupabaseClient.TermDTO created = supabaseClient.getTermByHash(hash);
                        if (created != null && created.id != null) {
                            updateLocalCloudId(hash, created.id);

                            // OPTIMISTIC UI: Track cloud_id to ignore our own realtime INSERT event
                            RealtimeSyncService.markAsRecentlyModified(created.id);
                        }
                    } catch (CloudOperationException e) {
                        System.err.println("[CloudUpload] Unable to fetch cloud id after insert: " + e.getMessage());
                    }
                    delay();
                    if (networkMonitor != null) {
                        networkMonitor.setSyncSuccess("Termo adicionado à nuvem");
                    }
                }
            } catch (Exception e) {
                System.err.println("[CloudUpload] Falha - adicionando à fila: " + e.getMessage());
                QueueOperationParams params = new QueueOperationParams("ADD", 0, sourceTerm, sourceLanguage,
                        targetTerm, targetLanguage, context, contributor, notes, "draft", owner, hash);
                offlineQueue.addOperation(params);

                if (networkMonitor != null) {
                    networkMonitor.setQueued(offlineQueue.getPendingCount());
                }

                // CRITICAL FIX: If we're online, immediately process queue
                // This prevents queue from growing forever when rate limited
                if (connectionChecker != null && connectionChecker.isOnline() && queueProcessor != null) {
                    System.out.println("[CloudUpload] Still online - triggering queue processing");
                    queueProcessor.processQueueAsync();
                }
            }
        }).start();
    }

    /**
     * Sync term edit to cloud (async).
     */
    public void syncEditToCloud(int localId, String sourceTerm, String sourceLanguage,
                                String targetTerm, String targetLanguage,
                                String context, String contributor, String notes,
                                String oldHash, String verifiedStatus,
                                String oldSourceTerm, String oldTargetTerm) {
        new Thread(() -> {
            String owner = fetchTermOwner(localId);

            try {
                if (shouldQueueOffline()) {
                    queueEditOperation(localId, sourceTerm, sourceLanguage, targetTerm, targetLanguage,
                            context, contributor, notes, verifiedStatus, owner, oldHash);
                    return;
                }

                performEditSync(localId, sourceTerm, sourceLanguage, targetTerm, targetLanguage,
                        context, contributor, notes, oldHash, verifiedStatus, oldSourceTerm, oldTargetTerm, owner);

            } catch (Exception e) {
                handleEditSyncFailure(e, localId, sourceTerm, sourceLanguage, targetTerm, targetLanguage,
                        context, contributor, notes, verifiedStatus, owner, oldHash);
            }
        }).start();
    }

    private String fetchTermOwner(int localId) {
        try {
            String ownerQuery = "SELECT owner FROM terms WHERE id = ?";
            try (PreparedStatement pstmt = localConnection.prepareStatement(ownerQuery)) {
                pstmt.setInt(1, localId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String owner = rs.getString("owner");
                    return owner != null ? owner : "shared";
                }
            }
        } catch (SQLException e) {
            System.err.println("[CloudUpload] Error fetching owner for edit: " + e.getMessage());
        }
        return "shared";
    }

    private boolean shouldQueueOffline() {
        return connectionChecker != null && !connectionChecker.isOnline();
    }

    private void queueEditOperation(int localId, String sourceTerm, String sourceLanguage,
                                   String targetTerm, String targetLanguage, String context,
                                   String contributor, String notes, String verifiedStatus,
                                   String owner, String oldHash) {
        System.out.println("[CloudUpload] Offline - queueing edit for term ID " + localId);
        System.out.println("[CloudUpload] Storing old hash for later cloud delete: " + oldHash);
        QueueOperationParams params = new QueueOperationParams("EDIT", localId, sourceTerm, sourceLanguage,
                targetTerm, targetLanguage, context, contributor, notes, verifiedStatus, owner, oldHash);
        offlineQueue.addOperation(params);

        if (networkMonitor != null) {
            networkMonitor.setQueued(offlineQueue.getPendingCount());
        }
    }

    private void performEditSync(int localId, String sourceTerm, String sourceLanguage,
                                 String targetTerm, String targetLanguage, String context,
                                 String contributor, String notes, String oldHash,
                                 String verifiedStatus, String oldSourceTerm, String oldTargetTerm,
                                 String owner) throws Exception {
        if (networkMonitor != null) {
            networkMonitor.setSyncing("Updating term...");
        }

        String newHash = calculateNewHash(sourceTerm, sourceLanguage, targetTerm, targetLanguage, context, contributor);
        logEditSyncDetails(oldHash, newHash, oldSourceTerm, oldTargetTerm, sourceTerm, targetTerm);

        // OPTIMISTIC UI: Get cloud_id and use UPDATE instead of DELETE+INSERT
        Integer cloudId = getCloudIdFromLocalDB(localId);
        if (cloudId != null && cloudId > 0) {
            // Use efficient UPDATE by ID
            updateCloudRecordById(localId, cloudId, sourceTerm, sourceLanguage, targetTerm,
                    targetLanguage, context, contributor, notes, newHash, verifiedStatus);
        } else {
            // Fallback to old method if no cloud_id (shouldn't happen often)
            System.out.println("[CloudUpload] No cloud_id found, using fallback DELETE+INSERT");
            deleteOldCloudRecord(oldHash);
            SupabaseClient.TermDTO dto = createUpdatedTermDTO(localId, sourceTerm, sourceLanguage, targetTerm,
                    targetLanguage, context, contributor, notes, newHash, verifiedStatus);
            insertUpdatedTerm(dto, newHash);
        }
    }

    private String calculateNewHash(String sourceTerm, String sourceLanguage, String targetTerm,
                                    String targetLanguage, String context, String contributor) {
        return hashTracker != null ?
                hashTracker.generateHash(sourceTerm, sourceLanguage, targetTerm, targetLanguage, context, contributor) : "";
    }

    private void logEditSyncDetails(String oldHash, String newHash, String oldSourceTerm,
                                    String oldTargetTerm, String sourceTerm, String targetTerm) {
        System.out.println("[CloudUpload] Syncing edit to cloud:");
        System.out.println("  Old hash: " + oldHash);
        System.out.println("  New hash: " + newHash);
        System.out.println("  Old term: " + oldSourceTerm + " -> " + oldTargetTerm);
        System.out.println("  New term: " + sourceTerm + " -> " + targetTerm);
    }

    private void deleteOldCloudRecord(String oldHash) throws Exception {
        boolean oldRecordExists = supabaseClient.termExistsByHash(oldHash);

        if (oldRecordExists) {
            System.out.println("[CloudUpload] Found existing cloud record with old hash");

            // OPTIMISTIC UI: No hash tracking needed - using cloud_id tracking now

            boolean deleted = supabaseClient.deleteTermByHash(oldHash);
            if (!deleted) {
                throw new CloudOperationException("Failed to delete old cloud record");
            }
            System.out.println("[CloudUpload] Deleted old cloud record");
            delay();
        } else {
            System.out.println("[CloudUpload] Warning: Old record not found in cloud (hash: " + oldHash + ")");
        }
    }

    private SupabaseClient.TermDTO createUpdatedTermDTO(int localId, String sourceTerm, String sourceLanguage,
                                                        String targetTerm, String targetLanguage, String context,
                                                        String contributor, String notes, String newHash,
                                                        String verifiedStatus) {
        SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
        dto.source_term = sourceTerm;
        dto.source_language = sourceLanguage;
        dto.target_term = targetTerm;
        dto.target_language = targetLanguage;
        dto.context = context;
        dto.contributor = contributor;
        dto.notes = notes;
        dto.content_hash = newHash;
        dto.verified_status = verifiedStatus;

        String originalDate = getTermDateFromLocalDB(localId);
        dto.date_added = originalDate != null ? originalDate : Instant.now().toString();

        String originalOwner = getTermOwnerFromLocalDB(localId);
        dto.owner = originalOwner != null ? originalOwner : "shared";

        return dto;
    }

    private void insertUpdatedTerm(SupabaseClient.TermDTO dto, String newHash) throws Exception {
        // OPTIMISTIC UI: No hash tracking needed - using cloud_id tracking now

        boolean success = supabaseClient.insertTerm(dto);
        if (success) {
            System.out.println("[CloudUpload] Edit synced successfully");
            delay();
            if (networkMonitor != null) {
                networkMonitor.setSyncSuccess("Termo atualizado na nuvem");
            }
        } else {
            throw new CloudOperationException("Failed to insert updated term");
        }
    }

    private void handleEditSyncFailure(Exception e, int localId, String sourceTerm, String sourceLanguage,
                                      String targetTerm, String targetLanguage, String context,
                                      String contributor, String notes, String verifiedStatus,
                                      String owner, String oldHash) {
        System.err.println("[CloudUpload] Edit sync failed - queuing: " + e.getMessage());
        QueueOperationParams params = new QueueOperationParams("EDIT", localId, sourceTerm, sourceLanguage,
                targetTerm, targetLanguage, context, contributor, notes, verifiedStatus, owner, oldHash);
        offlineQueue.addOperation(params);

        if (networkMonitor != null) {
            networkMonitor.setQueued(offlineQueue.getPendingCount());
        }

        // CRITICAL FIX: If we're online, immediately process queue
        // This prevents queue from growing forever when rate limited
        if (connectionChecker != null && connectionChecker.isOnline() && queueProcessor != null) {
            System.out.println("[CloudUpload] Still online - triggering queue processing");
            queueProcessor.processQueueAsync();
        }
    }

    /**
     * Sync edit to cloud (blocking version for queue processing).
     */
    public void syncEditToCloudBlocking(int localId, SyncEditParams params) throws Exception {

        // Calculate NEW hash
        String newHash = hashTracker != null ?
                hashTracker.generateHash(params.sourceTerm, params.sourceLanguage, params.targetTerm,
                        params.targetLanguage, params.context, params.contributor) : "";

        System.out.println("[CloudUpload] Blocking sync - Old hash: " + params.originalHash + ", New hash: " + newHash);

        // Check if OLD record exists
        boolean oldRecordExists = supabaseClient.termExistsByHash(params.originalHash);

        if (oldRecordExists) {
            // Delete old record
            boolean deleted = supabaseClient.deleteTermByHash(params.originalHash);
            if (!deleted) {
                throw new CloudOperationException("Failed to delete old cloud record");
            }
            System.out.println("[CloudUpload] Deleted old cloud record (blocking)");
        }

        // Insert new record with NEW hash
        SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
        dto.source_term = params.sourceTerm;
        dto.source_language = params.sourceLanguage;
        dto.target_term = params.targetTerm;
        dto.target_language = params.targetLanguage;
        dto.context = params.context;
        dto.contributor = params.contributor;
        dto.notes = params.notes;
        dto.content_hash = newHash;  // Use NEW hash
        dto.verified_status = params.status;

        String originalDate = getTermDateFromLocalDB(localId);
        dto.date_added = originalDate != null ? originalDate : Instant.now().toString();

        // Preserve original owner
        String originalOwner = getTermOwnerFromLocalDB(localId);
        dto.owner = originalOwner != null ? originalOwner : "shared";

        boolean success = supabaseClient.insertTerm(dto);

        if (!success) {
            throw new CloudOperationException("Failed to insert updated term");
        }

        System.out.println("[CloudUpload] Successfully synced edit (blocking)");
    }

    /**
     * Full local-to-cloud sync (uploads all local terms).
     */
    public int fullLocalToCloudSync() {
        String sql = "SELECT * FROM terms ORDER BY id";

        int syncedCount = 0;
        int skippedCount = 0;
        int duplicateCount = 0;

        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<SupabaseClient.TermDTO> batch = new ArrayList<>();

            while (rs.next()) {
                int termId = rs.getInt("id");
                String sourceTerm = rs.getString("source_term");
                String sourceLanguage = rs.getString("source_language");
                String targetTerm = rs.getString("target_term");
                String targetLanguage = rs.getString("target_language");
                String context = rs.getString("context");
                String contributor = rs.getString("contributor");
                String notes = rs.getString("notes");
                String verifiedStatus = rs.getString("verified_status");
                String hash = rs.getString("content_hash");
                String dateAdded = rs.getString("date_added");

                if (hash == null || hash.isEmpty()) {
                    hash = hashTracker != null ?
                            hashTracker.generateHash(sourceTerm, sourceLanguage, targetTerm,
                                    targetLanguage, context, contributor) : "";

                    try (PreparedStatement pstmt = localConnection.prepareStatement(
                            "UPDATE terms SET content_hash = ? WHERE id = ?")) {
                        pstmt.setString(1, hash);
                        pstmt.setInt(2, termId);
                        synchronized (dbLock) {
                            pstmt.executeUpdate();
                        }
                    } catch (SQLException e) {
                        if (e.getMessage().contains("UNIQUE constraint failed")) {
                            duplicateCount++;
                            System.out.println("[CloudUpload] Skipping local duplicate ID " + termId +
                                    ": " + sourceTerm + " → " + targetTerm);
                            continue;
                        } else {
                            throw e;
                        }
                    }
                }

                SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
                dto.source_term = sourceTerm;
                dto.source_language = sourceLanguage;
                dto.target_term = targetTerm;
                dto.target_language = targetLanguage;
                dto.context = context;
                dto.contributor = contributor;
                dto.notes = notes;
                dto.content_hash = hash;
                dto.verified_status = verifiedStatus != null ? verifiedStatus : "draft";
                dto.date_added = dateAdded != null ? dateAdded : Instant.now().toString();

                batch.add(dto);

                if (batch.size() >= BATCH_SIZE) {
                    try {
                        if (networkMonitor != null) {
                            networkMonitor.setSyncing("Enviando lote " + (syncedCount/BATCH_SIZE + 1) + "...");
                        }

                        int inserted = supabaseClient.batchInsertTerms(batch);

                        syncedCount += inserted;
                        System.out.println("[CloudUpload] Synced " + syncedCount + " terms so far...");

                        if (networkMonitor != null) {
                            networkMonitor.setSyncSuccess(syncedCount + " termos sincronizados");
                        }

                        if (inserted < batch.size()) {
                            skippedCount += (batch.size() - inserted);
                        }

                    } catch (Exception e) {
                        System.err.println("[CloudUpload] Batch upload failed: " + e.getMessage());
                        skippedCount += batch.size();
                    }
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                try {
                    int inserted = supabaseClient.batchInsertTerms(batch);
                    syncedCount += inserted;
                    if (inserted < batch.size()) {
                        skippedCount += (batch.size() - inserted);
                    }
                } catch (Exception e) {
                    System.err.println("[CloudUpload] Final batch upload failed: " + e.getMessage());
                    skippedCount += batch.size();
                }
            }

            System.out.println("[CloudUpload] Full sync complete:");
            System.out.println("  Synced: " + syncedCount);
            System.out.println("  Skipped: " + skippedCount);
            System.out.println("  Duplicates: " + duplicateCount);

            if (networkMonitor != null) {
                networkMonitor.setSyncSuccess("Sync completo: " + syncedCount + " termos");
            }

        } catch (SQLException e) {
            System.err.println("[CloudUpload] Full sync failed: " + e.getMessage());
            e.printStackTrace();
        }

        return syncedCount;
    }

    /**
     * Get term date from local database.
     */
    private String getTermDateFromLocalDB(int termId) {
        String sql = "SELECT date_added FROM terms WHERE id = ?";
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setInt(1, termId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("date_added");
            }
        } catch (SQLException e) {
            System.err.println("[CloudUpload] Error fetching term date: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get term owner from local database.
     */
    private String getTermOwnerFromLocalDB(int termId) {
        String sql = "SELECT owner FROM terms WHERE id = ?";
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setInt(1, termId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("owner");
            }
        } catch (SQLException e) {
            System.err.println("[CloudUpload] Error fetching term owner: " + e.getMessage());
        }
        return null;
    }

    private void updateLocalCloudId(String hash, int cloudId) {
        String sql = "UPDATE terms SET cloud_id = ? WHERE content_hash = ? AND (cloud_id IS NULL OR cloud_id != ?)";

        try (PreparedStatement stmt = localConnection.prepareStatement(sql)) {
            stmt.setInt(1, cloudId);
            stmt.setString(2, hash);
            stmt.setInt(3, cloudId);

            synchronized (dbLock) {
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    System.out.println("[CloudUpload] Stored cloud id " + cloudId + " for hash " + hash);
                }
            }
        } catch (SQLException e) {
            System.err.println("[CloudUpload] Failed to update local cloud id: " + e.getMessage());
        }
    }

    /**
     * Get cloud_id from local database for a given term.
     */
    private Integer getCloudIdFromLocalDB(int localId) {
        String sql = "SELECT cloud_id FROM terms WHERE id = ?";
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setInt(1, localId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int cloudId = rs.getInt("cloud_id");
                return cloudId > 0 ? cloudId : null;
            }
        } catch (SQLException e) {
            System.err.println("[CloudUpload] Error fetching cloud_id: " + e.getMessage());
        }
        return null;
    }

    /**
     * OPTIMISTIC UI: Update cloud record using UPDATE (not DELETE+INSERT).
     * Tracks the cloud_id in recentlyModifiedIds to ignore the realtime UPDATE event.
     */
    private void updateCloudRecordById(int localId, int cloudId, String sourceTerm, String sourceLanguage,
                                       String targetTerm, String targetLanguage, String context,
                                       String contributor, String notes, String newHash,
                                       String verifiedStatus) throws Exception {
        SupabaseClient.TermDTO dto = createUpdatedTermDTO(localId, sourceTerm, sourceLanguage, targetTerm,
                targetLanguage, context, contributor, notes, newHash, verifiedStatus);

        System.out.println("[CloudUpload] Updating cloud record with ID: " + cloudId);

        // Track this cloud_id so we ignore the realtime UPDATE event
        RealtimeSyncService.markAsRecentlyModified(cloudId);

        boolean success = supabaseClient.updateTermById(cloudId, dto);
        if (!success) {
            System.err.println("[CloudUpload] ⚠️ Update by ID failed - cloud record not found (id=" + cloudId + ")");
            System.err.println("[CloudUpload] This likely means the cloud record was deleted or doesn't exist");

            // Clear the tracked ID since update failed
            RealtimeSyncService.clearRecentModifications();

            throw new CloudOperationException("Failed to update cloud record - record not found");
        }

        System.out.println("[CloudUpload] ✅ Edit synced successfully (UPDATE by ID)");
        delay();

        if (networkMonitor != null) {
            networkMonitor.setSyncSuccess("Termo atualizado na nuvem");
        }
    }

    /**
     * Small delay to allow realtime events to process.
     */
    private void delay() {
        try {
            TimeUnit.MILLISECONDS.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
