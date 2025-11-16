package com.example.glossariobuda;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for real-time synchronization via WebSocket.
 * Handles INSERT, UPDATE, and DELETE events from Supabase realtime subscriptions.
 */
public class RealtimeSyncService {

    // Static singleton for realtime client (shared across all instances)
    private static SupabaseRealtimeClient realtimeClient;
    private static boolean realtimeInitialized = false;

    // OPTIMISTIC UI: Track recently modified IDs to ignore our own realtime events
    // Using ID tracking instead of hash tracking - 16x less memory
    private static final ConcurrentHashMap<Integer, Long> recentlyModifiedIds = new ConcurrentHashMap<>();
    private static final long MODIFICATION_WINDOW_MS = 5000; // 5 seconds for safety

    // Flag to pause realtime processing during bulk operations (like imports)
    private static volatile boolean realtimePaused = false;

    private final Connection localConnection;
    private final ScheduledExecutorService statusExecutor;
    private final Object dbLock;

    // Callback interface for handling term insertions from cloud
    public interface TermInsertionHandler {
        void insertTermFromCloudPublic(SupabaseClient.TermDTO term) throws SQLException;
        boolean updateTermFieldsFromCloud(SupabaseClient.TermDTO newTerm,
                                          SupabaseClient.TermDTO oldTerm) throws SQLException;
        boolean updateMetadataOnlyFromCloud(SupabaseClient.TermDTO term) throws SQLException;
        void onOwnerChanged(); // Notify when new owners are added
    }

    private TermInsertionHandler termHandler;

    public RealtimeSyncService(Connection localConnection,
                               ScheduledExecutorService statusExecutor,
                               Object dbLock) {
        this.localConnection = localConnection;
        this.statusExecutor = statusExecutor;
        this.dbLock = dbLock;
    }

    /**
     * Set the handler for term insertions/updates from cloud.
     */
    public void setTermInsertionHandler(TermInsertionHandler handler) {
        this.termHandler = handler;
    }

    /**
     * Pause realtime processing during bulk operations (e.g., import).
     * This prevents processing thousands of realtime notifications.
     */
    public static void pauseRealtime() {
        realtimePaused = true;
        System.out.println("[RealtimeSync] ⏸️ Realtime processing PAUSED (bulk operation in progress)");
    }

    /**
     * Resume realtime processing after bulk operations complete.
     */
    public static void resumeRealtime() {
        realtimePaused = false;
        System.out.println("[RealtimeSync] ▶️ Realtime processing RESUMED");
    }

    /**
     * Check if realtime is currently paused.
     */
    public static boolean isRealtimePaused() {
        return realtimePaused;
    }

    /**
     * Start real-time synchronization via WebSocket (singleton pattern).
     * NOTE: When RLS is enabled with restrictive policies, you'll need to pass
     * an authenticated user's JWT token instead of the anon key.
     */
    public void startRealtimeSync() {
        synchronized (RealtimeSyncService.class) {
            // Only initialize once globally
            if (realtimeInitialized) {
                System.out.println("[RealtimeSync] ℹ️ Realtime already initialized, skipping");
                return;
            }

            System.out.println("[RealtimeSync] 🔄 Starting real-time sync...");

            realtimeClient = new SupabaseRealtimeClient(new SupabaseRealtimeClient.RealtimeEventCallback() {
                @Override
                public void onInsert(SupabaseClient.TermDTO newRecord) {
                    handleRealtimeInsert(newRecord);
                }

                @Override
                public void onUpdate(SupabaseClient.TermDTO oldRecord, SupabaseClient.TermDTO newRecord) {
                    handleRealtimeUpdate(oldRecord, newRecord);
                }

                @Override
                public void onDelete(SupabaseClient.TermDTO oldRecord) {
                    handleRealtimeDelete(oldRecord);
                }

                @Override
                public void onError(String error) {
                    System.err.println("[RealtimeSync] ❌ Realtime error: " + error);
                }
            });

            realtimeClient.connect();
            realtimeInitialized = true;
            System.out.println("[RealtimeSync] ✅ Realtime singleton initialized");
        }
    }

    /**
     * OPTIMISTIC UI: Mark a term ID as recently modified by us (to ignore our own realtime events).
     * Call this AFTER updating local database.
     */
    public static void markAsRecentlyModified(int termId) {
        recentlyModifiedIds.put(termId, System.currentTimeMillis());
    }

    /**
     * Check if we recently modified this term (within the modification window).
     * Auto-cleans up old entries.
     */
    private static boolean isOurRecentChange(int termId) {
        Long timestamp = recentlyModifiedIds.get(termId);
        if (timestamp == null) {
            System.out.println("[RealtimeSync] 🔍 DEBUG: cloud_id=" + termId + " NOT in recentlyModifiedIds");
            return false;
        }

        long age = System.currentTimeMillis() - timestamp;
        System.out.println("[RealtimeSync] 🔍 DEBUG: cloud_id=" + termId + " age=" + age + "ms (window=" + MODIFICATION_WINDOW_MS + "ms)");

        if (age > MODIFICATION_WINDOW_MS) {
            recentlyModifiedIds.remove(termId); // Cleanup old entries
            System.out.println("[RealtimeSync] 🔍 DEBUG: cloud_id=" + termId + " EXPIRED (age > window)");
            return false;
        }
        return true;
    }

    /**
     * Cleanup method for batch operations.
     */
    public static void clearRecentModifications() {
        recentlyModifiedIds.clear();
    }

    /**
     * Handle INSERT event from realtime subscription.
     * OPTIMISTIC UI: Simple ID-based check to ignore our own changes.
     */
    private void handleRealtimeInsert(SupabaseClient.TermDTO newTerm) {
        try {
            // Skip processing if realtime is paused (bulk operation in progress)
            if (realtimePaused) {
                return; // Silently skip without logging during bulk operations
            }

            System.out.println("[RealtimeSync] 📥 Realtime INSERT: " + newTerm.source_term);

            // Store cloud_id for future reference
            String hash = ensureContentHash(newTerm);
            storeCloudIdIfNeeded(newTerm, hash);

            // OPTIMISTIC UI: Check if we just added this term
            // We track by cloud_id (from the INSERT event)
            if (newTerm.id != null && isOurRecentChange(newTerm.id)) {
                System.out.println("[RealtimeSync] ⏭️ Skipping our own INSERT (id=" + newTerm.id + ")");
                return;
            }

            // Check if term already exists locally by hash (deduplication)
            String checkSQL = "SELECT id FROM terms WHERE content_hash = ?";
            try (PreparedStatement checkStmt = localConnection.prepareStatement(checkSQL)) {
                checkStmt.setString(1, hash);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    System.out.println("[RealtimeSync] Term already exists locally, skipping insert");
                    return;
                }
            }

            // Insert new term via handler
            if (termHandler != null) {
                termHandler.insertTermFromCloudPublic(newTerm);
                System.out.println("[RealtimeSync] ✅ Realtime INSERT successful");

                // Notify owner change
                termHandler.onOwnerChanged();
            } else {
                System.err.println("[RealtimeSync] ⚠️ No term insertion handler set!");
            }

        } catch (SQLException e) {
            System.err.println("[RealtimeSync] ❌ Failed to handle realtime INSERT: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleRealtimeUpdate(SupabaseClient.TermDTO oldRecord, SupabaseClient.TermDTO newRecord) {
        try {
            // Skip processing if realtime is paused (bulk operation in progress)
            if (realtimePaused) {
                return; // Silently skip without logging during bulk operations
            }

            System.out.println("[RealtimeSync] 📝 Realtime UPDATE: " + newRecord.source_term);

            // Store cloud_id for future reference
            String newHash = ensureContentHash(newRecord);
            storeCloudIdIfNeeded(newRecord, newHash);

            // OPTIMISTIC UI: Check if we just updated this term
            if (newRecord.id != null && isOurRecentChange(newRecord.id)) {
                System.out.println("[RealtimeSync] ⏭️ Skipping our own UPDATE (id=" + newRecord.id + ")");
                return;
            }

            // Update the term via handler
            if (termHandler != null) {
                boolean updated = termHandler.updateTermFieldsFromCloud(newRecord, oldRecord);

                if (updated) {
                    System.out.println("[RealtimeSync] ✅ Realtime UPDATE successful");
                    termHandler.onOwnerChanged();
                } else {
                    System.out.println("[RealtimeSync] ⚠️ No term found to update with hash: " + newRecord.content_hash);
                }
            } else {
                System.err.println("[RealtimeSync] ⚠️ No term update handler set!");
            }

        } catch (SQLException e) {
            System.err.println("[RealtimeSync] ❌ Failed to handle realtime UPDATE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle DELETE event from realtime subscription.
     * OPTIMISTIC UI: Simple ID-based check to ignore our own changes.
     *
     * NOTE: With RLS enabled, Supabase only provides the cloud ID in DELETE events (security feature).
     */
    private void handleRealtimeDelete(SupabaseClient.TermDTO deletedTerm) {
        try {
            // Skip processing if realtime is paused (bulk operation in progress)
            if (realtimePaused) {
                return; // Silently skip without logging during bulk operations
            }

            String termInfo = deletedTerm.id != null ? "id=" + deletedTerm.id : "unknown";
            if (deletedTerm.source_term != null) {
                termInfo = deletedTerm.source_term;
            }
            System.out.println("[RealtimeSync] 🗑️ Realtime DELETE: " + termInfo);

            // OPTIMISTIC UI: Check if we just deleted this term
            if (deletedTerm.id != null && isOurRecentChange(deletedTerm.id)) {
                System.out.println("[RealtimeSync] ⏭️ Skipping our own DELETE (id=" + deletedTerm.id + ")");
                return;
            }

            boolean deleted = false;

            // Try to delete by cloud_id first (most reliable)
            if (deletedTerm.id != null) {
                String deleteByCloudIdSQL = "DELETE FROM terms WHERE cloud_id = ?";
                try (PreparedStatement deleteStmt = localConnection.prepareStatement(deleteByCloudIdSQL)) {
                    deleteStmt.setInt(1, deletedTerm.id);

                    synchronized (dbLock) {
                        deleted = deleteStmt.executeUpdate() > 0;
                    }
                }

                if (deleted) {
                    System.out.println("[RealtimeSync] ✅ Realtime DELETE successful (cloud_id=" + deletedTerm.id + ")");
                    if (termHandler != null) {
                        termHandler.onOwnerChanged();
                    }
                    return;
                }
            }

            // Fallback: Try to delete by content_hash (for backward compatibility)
            String primaryHash = ensureContentHash(deletedTerm);

            if (primaryHash != null && !primaryHash.isEmpty()) {
                String deleteSQL = "DELETE FROM terms WHERE content_hash = ?";
                try (PreparedStatement deleteStmt = localConnection.prepareStatement(deleteSQL)) {
                    deleteStmt.setString(1, primaryHash);

                    synchronized (dbLock) {
                        deleted = deleteStmt.executeUpdate() > 0;
                    }
                }
            }

            if (deleted) {
                System.out.println("[RealtimeSync] ✅ Realtime DELETE successful");
                if (termHandler != null) {
                    termHandler.onOwnerChanged();
                }
            } else {
                System.out.println("[RealtimeSync] ⚠️ Realtime DELETE could not find matching term (hash=" +
                        primaryHash + ", cloud_id=" + deletedTerm.id + ")");
            }

        } catch (SQLException e) {
            System.err.println("[RealtimeSync] ❌ Failed to handle realtime DELETE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Shutdown realtime sync gracefully.
     */
    public void shutdown() {
        synchronized (RealtimeSyncService.class) {
            if (realtimeClient != null) {
                try {
                    realtimeClient.disconnect();
                    System.out.println("[RealtimeSync] 🔌 Realtime client disconnected");
                } catch (Exception e) {
                    System.err.println("[RealtimeSync] ❌ Error disconnecting realtime client: " + e.getMessage());
                }
                realtimeClient = null;
                realtimeInitialized = false;
            }
        }
    }

    /**
     * Check if realtime sync is currently initialized.
     */
    public static boolean isRealtimeInitialized() {
        return realtimeInitialized;
    }

    private void storeCloudIdIfNeeded(SupabaseClient.TermDTO term, String hash) throws SQLException {
        if (term == null || term.id == null || hash == null || hash.isEmpty()) {
            return;
        }

        String updateSQL = """
            UPDATE terms SET cloud_id = ?
            WHERE content_hash = ?
              AND (cloud_id IS NULL OR cloud_id = 0 OR cloud_id != ?)
        """;

        try (PreparedStatement stmt = localConnection.prepareStatement(updateSQL)) {
            stmt.setInt(1, term.id);
            stmt.setString(2, hash);
            stmt.setInt(3, term.id);

            synchronized (dbLock) {
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    System.out.println("[RealtimeSync] 🔁 Stored cloud_id " + term.id + " for hash " + hash);
                }
            }
        }
    }

    private String ensureContentHash(SupabaseClient.TermDTO term) {
        if (term == null) {
            return null;
        }

        if (term.content_hash == null || term.content_hash.isEmpty()) {
            if (!hasTermContent(term)) {
                return null;
            }
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

    private boolean hasTermContent(SupabaseClient.TermDTO term) {
        return (term.source_term != null && !term.source_term.isEmpty()) ||
                (term.source_language != null && !term.source_language.isEmpty()) ||
                (term.target_term != null && !term.target_term.isEmpty()) ||
                (term.target_language != null && !term.target_language.isEmpty()) ||
                (term.context != null && !term.context.isEmpty()) ||
                (term.contributor != null && !term.contributor.isEmpty());
    }
}
