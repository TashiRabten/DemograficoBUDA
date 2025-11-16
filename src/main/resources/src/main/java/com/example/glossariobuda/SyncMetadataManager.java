package com.example.glossariobuda;

import com.example.glossariobuda.exceptions.GlossaryRuntimeException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Service responsible for managing sync metadata, database schema, and content hashing.
 * Handles database column creation, metadata storage, and hash generation for terms.
 */
public class SyncMetadataManager {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Connection localConnection;
    private final Object dbLock;

    // Callback interface for progress tracking
    public interface ProgressCallback {
        void onProgress(String message, int current, int total);
    }

    private ProgressCallback progressCallback;

    public SyncMetadataManager(Connection localConnection, Object dbLock) {
        this.localConnection = localConnection;
        this.dbLock = dbLock;
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    /**
     * Initialize database schema - add necessary columns and tables.
     * Should be called during SyncManager initialization.
     */
    public void initializeSchema() {
        addContentHashColumn();
        addUpdatedAtColumn();
        addCloudIdColumn();
        createSyncMetadataTable();
    }

    /**
     * Add content_hash column to terms table if it doesn't exist.
     */
    public void addContentHashColumn() {
        String checkColumnSQL = "PRAGMA table_info(terms)";
        boolean hasHashColumn = false;

        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(checkColumnSQL)) {

            while (rs.next()) {
                if ("content_hash".equals(rs.getString("name"))) {
                    hasHashColumn = true;
                    break;
                }
            }

            if (!hasHashColumn) {
                String alterSQL = "ALTER TABLE terms ADD COLUMN content_hash TEXT";
                stmt.execute(alterSQL);
                String indexSQL = "CREATE UNIQUE INDEX IF NOT EXISTS idx_content_hash ON terms(content_hash) WHERE content_hash IS NOT NULL";
                stmt.execute(indexSQL);
                System.out.println("[MetadataManager] content_hash column added");
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error with content_hash column: " + e.getMessage());
        }
    }

    /**
     * Add updated_at column to terms table if it doesn't exist.
     */
    public void addUpdatedAtColumn() {
        String checkColumnSQL = "PRAGMA table_info(terms)";
        boolean hasUpdatedAtColumn = false;

        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(checkColumnSQL)) {

            while (rs.next()) {
                if ("updated_at".equals(rs.getString("name"))) {
                    hasUpdatedAtColumn = true;
                    break;
                }
            }

            if (!hasUpdatedAtColumn) {
                String alterSQL = "ALTER TABLE terms ADD COLUMN updated_at TEXT";
                stmt.execute(alterSQL);
                // Initialize updated_at to date_added for existing rows
                String initSQL = "UPDATE terms SET updated_at = date_added WHERE updated_at IS NULL";
                stmt.execute(initSQL);
                System.out.println("[MetadataManager] updated_at column added and initialized");
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error with updated_at column: " + e.getMessage());
        }
    }

    /**
     * Add cloud_id column to terms table if it doesn't exist.
     */
    public void addCloudIdColumn() {
        String checkColumnSQL = "PRAGMA table_info(terms)";
        boolean hasCloudIdColumn = false;

        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(checkColumnSQL)) {

            while (rs.next()) {
                if ("cloud_id".equals(rs.getString("name"))) {
                    hasCloudIdColumn = true;
                    break;
                }
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error checking cloud_id column: " + e.getMessage());
            return;
        }

        if (!hasCloudIdColumn) {
            try (Statement alterStmt = localConnection.createStatement()) {
                alterStmt.execute("ALTER TABLE terms ADD COLUMN cloud_id INTEGER");
                alterStmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_terms_cloud_id ON terms(cloud_id) WHERE cloud_id IS NOT NULL");
                System.out.println("[MetadataManager] cloud_id column added and indexed");
            } catch (SQLException e) {
                System.err.println("[MetadataManager] Error adding cloud_id column: " + e.getMessage());
            }
        }
    }

    /**
     * Create sync_metadata table if it doesn't exist.
     * Stores key-value pairs for sync state (last_sync, glossary_version, etc.)
     */
    public void createSyncMetadataTable() {
        String createSQL = "CREATE TABLE IF NOT EXISTS sync_metadata (key TEXT PRIMARY KEY, value TEXT)";
        try (Statement stmt = localConnection.createStatement()) {
            stmt.execute(createSQL);

            String checkSQL = "SELECT value FROM sync_metadata WHERE key = 'glossary_version'";
            ResultSet rs = stmt.executeQuery(checkSQL);

            if (!rs.next()) {
                String insertSQL = "INSERT INTO sync_metadata (key, value) VALUES ('glossary_version', '0')";
                stmt.execute(insertSQL);
                System.out.println("[MetadataManager] Initialized glossary_version to 0");
            }
            rs.close();

        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error creating sync_metadata: " + e.getMessage());
        }
    }

    /**
     * Generate SHA-256 hash from term content fields.
     * Used for duplicate detection and synchronization.
     */
    public String generateHash(String sourceTerm, String sourceLanguage,
                               String targetTerm, String targetLanguage,
                               String context, String contributor) {
        try {
            String content = sourceTerm + "|" + sourceLanguage + "|" + targetTerm + "|" +
                    targetLanguage + "|" + (context != null ? context : "") + "|" +
                    (contributor != null ? contributor : "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new GlossaryRuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Generate hashes for all existing terms that don't have one.
     * Called during initialization to backfill missing hashes.
     */
    public void generateHashesForExistingTerms() {
        String countSQL = "SELECT COUNT(*) as total FROM terms WHERE content_hash IS NULL";
        String selectSQL = "SELECT * FROM terms WHERE content_hash IS NULL";
        String updateSQL = "UPDATE terms SET content_hash = ? WHERE id = ?";

        try {
            int totalToHash = 0;
            try (Statement countStmt = localConnection.createStatement();
                 ResultSet countRs = countStmt.executeQuery(countSQL)) {
                if (countRs.next()) {
                    totalToHash = countRs.getInt("total");
                }
            }

            if (totalToHash == 0) {
                System.out.println("[MetadataManager] All terms have hashes");
                return;
            }

            System.out.println("[MetadataManager] Generating hashes for " + totalToHash + " terms...");

            if (progressCallback != null) {
                progressCallback.onProgress("Gerando hashes de conteúdo...", 0, totalToHash);
            }

            try (Statement selectStmt = localConnection.createStatement();
                 PreparedStatement updateStmt = localConnection.prepareStatement(updateSQL);
                 ResultSet rs = selectStmt.executeQuery(selectSQL)) {

                int count = 0;
                while (rs.next()) {
                    String hash = generateHash(
                            rs.getString("source_term"),
                            rs.getString("source_language"),
                            rs.getString("target_term"),
                            rs.getString("target_language"),
                            rs.getString("context"),
                            rs.getString("contributor")
                    );

                    try {
                        updateStmt.setString(1, hash);
                        updateStmt.setInt(2, rs.getInt("id"));
                        synchronized (dbLock) {
                            updateStmt.executeUpdate();
                        }
                        count++;

                        if (progressCallback != null && count % 100 == 0) {
                            progressCallback.onProgress("Gerados " + count + " hashes...", count, totalToHash);
                        }
                    } catch (SQLException e) {
                        if (e.getMessage().contains("UNIQUE constraint failed")) {
                            System.out.println("[MetadataManager] Skipping duplicate ID: " + rs.getInt("id"));
                        } else {
                            throw e;
                        }
                    }
                }
                System.out.println("[MetadataManager] Generated " + count + " hashes");
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error generating hashes: " + e.getMessage());
        }
    }

    /**
     * Update the content hash for a specific term.
     */
    public void updateLocalHash(int termId, String newHash) {
        if (newHash == null || newHash.isEmpty()) {
            System.err.println("[MetadataManager] Cannot update with null/empty hash");
            return;
        }

        String sql = "UPDATE terms SET content_hash = ? WHERE id = ?";
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, newHash);
            pstmt.setInt(2, termId);
            synchronized (dbLock) {
                pstmt.executeUpdate();
            }
            System.out.println("[MetadataManager] Updated local hash for term ID " + termId);
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Failed to update local hash: " + e.getMessage());
        }
    }

    /**
     * Get the last successful sync timestamp.
     * Returns a timestamp 30 days ago if never synced.
     */
    public String getLastSyncTime() {
        String sql = "SELECT value FROM sync_metadata WHERE key = 'last_sync'";

        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error getting last sync time: " + e.getMessage());
        }

        return LocalDateTime.now().minusDays(30).format(DATETIME_FORMATTER);
    }

    /**
     * Update the last sync timestamp to current time.
     */
    public void updateLastSyncTime() {
        String sql = "INSERT OR REPLACE INTO sync_metadata (key, value) VALUES ('last_sync', ?)";

        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, LocalDateTime.now().format(DATETIME_FORMATTER));
            synchronized (dbLock) {
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error updating last sync time: " + e.getMessage());
        }
    }

    /**
     * Persist a specific last sync timestamp.
     */
    public void saveLastSyncTimestamp(String timestamp) {
        String formatted = formatTimestampForStorage(timestamp);
        String sql = "INSERT OR REPLACE INTO sync_metadata (key, value) VALUES ('last_sync', ?)";

        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, formatted);
            synchronized (dbLock) {
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error saving last sync timestamp: " + e.getMessage());
        }
    }

    private String formatTimestampForStorage(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return LocalDateTime.now().format(DATETIME_FORMATTER);
        }

        try {
            Instant instant = Instant.parse(timestamp);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATETIME_FORMATTER);
        } catch (Exception ignored) {
            // Fall through and try alternative parsing
        }

        try {
            LocalDateTime parsed = LocalDateTime.parse(timestamp, DATETIME_FORMATTER);
            return parsed.format(DATETIME_FORMATTER);
        } catch (Exception ignored) {
            // Return original if parsing fails
        }

        return timestamp;
    }

    /**
     * Get the local glossary version number.
     * Used to detect when cloud glossary has been reset.
     */
    public int getLocalGlossaryVersion() {
        String sql = "SELECT value FROM sync_metadata WHERE key = 'glossary_version'";

        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("[MetadataManager] Error getting local glossary version: " + e.getMessage());
        }

        return 1;
    }

    /**
     * Update the local glossary version.
     * Called after successful reset synchronization.
     */
    public void updateLocalGlossaryVersion(int version) {
        String sql = "INSERT OR REPLACE INTO sync_metadata (key, value) VALUES ('glossary_version', ?)";

        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(version));
            synchronized (dbLock) {
                pstmt.executeUpdate();
            }
            System.out.println("[MetadataManager] Updated local glossary version to: " + version);
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error updating local glossary version: " + e.getMessage());
        }
    }

    /**
     * Get count of local terms.
     */
    public int getLocalTermsCount() {
        String sql = "SELECT COUNT(*) as count FROM terms";
        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error counting local terms: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Get a metadata value by key.
     */
    public String getMetadata(String key) {
        String sql = "SELECT value FROM sync_metadata WHERE key = ?";
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error getting metadata for key " + key + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Set a metadata value by key.
     */
    public void setMetadata(String key, String value) {
        String sql = "INSERT OR REPLACE INTO sync_metadata (key, value) VALUES (?, ?)";
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            synchronized (dbLock) {
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error setting metadata for key " + key + ": " + e.getMessage());
        }
    }

    /**
     * Get the last successfully synced cloud ID from parallel fetch.
     * Used to resume parallel sync from where it left off if previous sync was incomplete.
     * Returns 0 if no previous sync recorded.
     */
    public int getLastSyncedCloudId() {
        String value = getMetadata("last_synced_cloud_id");
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.err.println("[MetadataManager] Invalid last_synced_cloud_id value: " + value);
            }
        }
        return 0;
    }

    /**
     * Update the last successfully synced cloud ID.
     * Called after successful parallel fetch to enable resume on next sync.
     */
    public void updateLastSyncedCloudId(int cloudId) {
        setMetadata("last_synced_cloud_id", String.valueOf(cloudId));
        System.out.println("[MetadataManager] Updated last synced cloud ID to: " + cloudId);
    }

    /**
     * Clear the last synced cloud ID marker.
     * Called after a complete successful sync to reset for next time.
     */
    public void clearLastSyncedCloudId() {
        setMetadata("last_synced_cloud_id", "0");
    }

    /**
     * Get the maximum cloud_id currently stored in local database.
     * Used by smart parallel workers to resume from where local DB left off.
     * Returns 0 if no cloud_ids found.
     */
    public int getLocalMaxCloudId() {
        String sql = "SELECT MAX(cloud_id) as max_id FROM terms WHERE cloud_id IS NOT NULL";
        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int maxId = rs.getInt("max_id");
                if (!rs.wasNull()) {
                    return maxId;
                }
            }
        } catch (SQLException e) {
            System.err.println("[MetadataManager] Error getting local max cloud_id: " + e.getMessage());
        }
        return 0;
    }
}
