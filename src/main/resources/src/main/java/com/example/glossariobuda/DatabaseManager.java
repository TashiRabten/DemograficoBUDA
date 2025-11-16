package com.example.glossariobuda;

import com.example.glossariobuda.exceptions.GlossaryRuntimeException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DatabaseManager {
    private Connection connection;
    private SyncManager syncManager;
    private boolean resetWasHandled = false;

    public DatabaseManager() {
        initializeDatabaseConnection();
    }

    /**
     * Check if a glossary reset was handled during initialization
     */
    public boolean wasResetHandled() {
        return resetWasHandled;
    }
    /**
     * Get recent terms filtered by owner with cursor-based pagination (modern approach).
     * Uses ID cursor instead of OFFSET for O(1) performance on large datasets.
     *
     * @param owner Owner filter (ALL, shared, or specific owner)
     * @param limit Number of terms to fetch
     * @param beforeId Cursor: fetch terms with ID less than this (0 = start from newest)
     * @return List of terms ordered by date_added DESC
     */
    public List<Term> getRecentTermsByOwner(String owner, int limit, int beforeId) {
        List<Term> results = new ArrayList<>();

        // Build query with cursor-based pagination
        // Instead of OFFSET (O(n)), use WHERE id < beforeId (O(1) with index)
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM terms WHERE 1=1"
        );

        // Add cursor filter (skip if beforeId == 0, meaning first page)
        if (beforeId > 0) {
            sql.append(" AND id < ?");
        }

        // Add owner filter
        if (owner != null && !owner.equals("ALL")) {
            if (owner.equals("shared")) {
                sql.append(" AND (owner = 'shared' OR owner IS NULL)");
            } else {
                sql.append(" AND owner = ?");
            }
        }

        // Order by date_added DESC (newest first), then ID DESC for cursor stability
        sql.append(" ORDER BY date_added DESC, id DESC LIMIT ?");

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            int paramIndex = 1;

            // Set cursor parameter if paginating
            if (beforeId > 0) {
                pstmt.setInt(paramIndex++, beforeId);
            }

            // Set owner parameter if needed
            if (owner != null && !owner.equals("ALL") && !owner.equals("shared")) {
                pstmt.setString(paramIndex++, owner);
            }

            // Set limit
            pstmt.setInt(paramIndex, limit);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Convert to Term
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : new ArrayList<>();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : new ArrayList<>();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner")
                );

                results.add(term);
            }
        } catch (SQLException e) {
            System.err.println("Error getting recent terms by owner: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Get recent terms filtered by owner (convenience method for first page).
     * Delegates to cursor-based pagination method with beforeId = 0.
     */
    public List<Term> getRecentTermsByOwner(String owner, int limit) {
        return getRecentTermsByOwner(owner, limit, 0);
    }
    /**
     * Get terms added BEFORE a specific date/time, filtered by owner (for pagination)
     */
    public List<Term> getTermsAddedBeforeByOwner(String dateTime, int limit, String owner) {
        List<Term> results = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT * FROM terms WHERE datetime(date_added) < datetime(?)"
        );

        // Add owner filter
        if (owner != null && !owner.equals("ALL")) {
            if (owner.equals("shared")) {
                sql.append(" AND (owner = 'shared' OR owner IS NULL)");
            } else {
                sql.append(" AND owner = ?");
            }
        }

        sql.append(" ORDER BY date_added DESC LIMIT ?");

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            pstmt.setString(paramIndex++, dateTime);

            // Set owner parameter if needed
            if (owner != null && !owner.equals("ALL") && !owner.equals("shared")) {
                pstmt.setString(paramIndex++, owner);
            }

            pstmt.setInt(paramIndex, limit);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Same conversion logic as getRecentTermsByOwner
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : new ArrayList<>();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : new ArrayList<>();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner")
                );
                results.add(term);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }
    /**
     * Get the database file path. If the app is packaged, store in Documents folder.
     * If running from IDE, use the development path.
     */
    private String getDatabasePath() {
        // Check if running from packaged app (no src/main/resources directory)
        File devDbFile = new File("src/main/resources/glossario_terms.db");

        if (devDbFile.exists()) {
            // Development mode - use the file in src/main/resources
            System.out.println("[DatabaseManager] Running in development mode");
            return "src/main/resources/glossario_terms.db";
        } else {
            // Production mode - use Documents/.glossariobuda directory
            System.out.println("[DatabaseManager] Running in production mode");
            String userHome = System.getProperty("user.home");
            Path documentsDir = Paths.get(userHome, "Documents");
            Path appDir = documentsDir.resolve(".glossariobuda");
            Path dbPath = appDir.resolve("glossario_terms.db");

            try {
                // Create directory if it doesn't exist
                if (!Files.exists(appDir)) {
                    Files.createDirectories(appDir);
                    System.out.println("[DatabaseManager] Created app directory: " + appDir);
                }

                // If database doesn't exist in user directory, try to copy from resources
                if (!Files.exists(dbPath)) {
                    System.out.println("[DatabaseManager] Database not found, creating new one");
                    // Try to copy from bundled resources
                    try (InputStream is = getClass().getResourceAsStream("/glossario_terms.db")) {
                        if (is != null) {
                            Files.copy(is, dbPath, StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("[DatabaseManager] Copied database from resources");
                        } else {
                            System.out.println("[DatabaseManager] No bundled database found, will create new one");
                        }
                    } catch (IOException e) {
                        System.out.println("[DatabaseManager] Could not copy database from resources: " + e.getMessage());
                    }
                }

                return dbPath.toString();
            } catch (IOException e) {
                System.err.println("[DatabaseManager] Error setting up database directory: " + e.getMessage());
                e.printStackTrace();
                // Fallback to current directory
                return "glossario_terms.db";
            }
        }
    }

    private void initializeSyncManager() {
        try {
            syncManager = new SyncManager(connection);
        } catch (Exception e) {
            System.err.println("[DatabaseManager] Could not initialize sync manager: " + e.getMessage());
        }
    }

    public void setInitializationCallback(SyncManager.InitializationCallback callback) {
        if (syncManager != null) {
            syncManager.setInitializationCallback(callback);
        }
    }

    /**
     * Perform database initialization with progress reporting.
     * Database schema setup (tables, migrations, indexes, FTS5) was already done in constructor.
     * This method handles hash generation and checking for glossary resets.
     * Called by splash screen after the initialization callback is set.
     */
    public void initializeDatabase() {
        if (syncManager == null) {
            System.err.println("[DatabaseManager] SyncManager not initialized!");
            return;
        }

        System.out.println("[DatabaseManager] Starting database initialization...");

        // Step 1: Generate hashes for existing terms (with detailed progress reporting)
        notifyProgress("Inicializando gerenciador de sincronização...", 1, 2);
        syncManager.initializeDatabase();

        // Step 2: Check for glossary reset at startup
        notifyProgress("Verificando atualizações do glossário...", 2, 2);
        System.out.println("[DatabaseManager] Checking for glossary reset...");
        resetWasHandled = syncManager.checkAndHandleGlossaryReset();

        if (resetWasHandled) {
            System.out.println("[DatabaseManager] Reset was handled - auto-sync will be skipped");
        }

        System.out.println("[DatabaseManager] Database initialization complete");
    }

    public int fullSyncToCloud() {
        if (syncManager != null) {
            return syncManager.fullLocalToCloudSync();
        }
        return 0;
    }

    /**
     * Process offline queue - syncs operations that were queued while offline
     * Returns number of operations processed
     */
    public int processOfflineQueue() {
        if (syncManager != null) {
            return syncManager.processOfflineQueue();
        }
        return 0;
    }

    public boolean addTerm(AddTermParams params) {
        if (syncManager != null) {
            return syncManager.addTermWithSync(params);
        } else {
            // Fallback to original method if sync not available
            String sql = """
            INSERT INTO terms (source_term, source_language, target_term, target_language,
                             context, contributor, notes, owner,
                             source_term_normalized, target_term_normalized, context_normalized)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, params.sourceTerm);
                pstmt.setString(2, params.sourceLanguage);
                pstmt.setString(3, params.targetTerm);
                pstmt.setString(4, params.targetLanguage);
                pstmt.setString(5, params.context);
                pstmt.setString(6, params.contributor);
                pstmt.setString(7, params.notes);
                pstmt.setString(8, params.owner);

                // Populate normalized columns
                String sourceNormalized = params.sourceTerm != null ?
                    DiacriticUtils.removeDiacritics(params.sourceTerm).toLowerCase() : null;
                String targetNormalized = params.targetTerm != null ?
                    DiacriticUtils.removeDiacritics(params.targetTerm).toLowerCase() : null;
                String contextNormalized = params.context != null ?
                    DiacriticUtils.removeDiacritics(params.context).toLowerCase() : null;

                pstmt.setString(9, sourceNormalized);
                pstmt.setString(10, targetNormalized);
                pstmt.setString(11, contextNormalized);

                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public List<Term> searchTerms(String searchText) {
        return searchTerms(searchText, 100);
    }

    public List<Term> searchTerms(String searchText, int maxResults) {
        return searchTerms(searchText, maxResults, 0);
    }
    // Helper class to track term with its best score
    private static class TermWithScore {
        Term term;
        int score;

        TermWithScore(Term term, int score) {
            this.term = term;
            this.score = score;
        }
    }

    public List<Term> searchTerms(String searchText, int maxResults, int offset, String owner) {
        // Try FTS5 first for better performance on large datasets
        try {
            return searchTermsFTS5(searchText, maxResults, offset, owner);
        } catch (SQLException e) {
            // Fallback to regular search if FTS5 not available
            System.err.println("[DatabaseManager] FTS5 search failed: " + e.getMessage() + " - falling back to regular search");
            return searchTermsLegacy(searchText, maxResults, offset, owner);
        }
    }

    /**
     * Fast FTS5-based search using MATCH operator (30% faster for 1M+ records).
     */
    private List<Term> searchTermsFTS5(String searchText, int maxResults, int offset, String owner) throws SQLException {
        List<Term> results = new ArrayList<>();

        // Normalize search text for diacritic-insensitive search
        String searchNormalized = DiacriticUtils.removeDiacritics(searchText).toLowerCase();

        // Use FTS5 MATCH for full-text search with ranking
        // IMPORTANT: MATCH operator requires the actual table name, not the alias
        // See: https://sqlite.org/fts5.html (table aliases don't work with MATCH)
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT t.*,
                CASE
                    -- Priority 1: Exact match in source/target
                    WHEN LOWER(TRIM(t.source_term)) = LOWER(?) THEN 1
                    WHEN LOWER(TRIM(t.target_term)) = LOWER(?) THEN 1

                    -- Priority 2: Starts with match
                    WHEN LOWER(t.source_term) LIKE LOWER(?) THEN 2
                    WHEN LOWER(t.target_term) LIKE LOWER(?) THEN 2

                    -- Priority 3: FTS5 relevance (contains match)
                    ELSE 3
                END AS priority,
                fts.rank
            FROM terms t
            INNER JOIN terms_fts fts ON t.id = fts.rowid
            WHERE terms_fts MATCH ?
        """);

        // Add owner filter
        if (owner != null && !owner.equals("ALL")) {
            if (owner.equals("shared")) {
                sqlBuilder.append(" AND t.owner = 'shared'");
            } else {
                sqlBuilder.append(" AND t.owner = ?");
            }
        }

        sqlBuilder.append(" ORDER BY priority ASC, fts.rank ASC, t.date_added DESC LIMIT ? OFFSET ?");

        try (PreparedStatement pstmt = connection.prepareStatement(sqlBuilder.toString())) {
            int paramIndex = 1;

            // Priority checks
            pstmt.setString(paramIndex++, searchText);
            pstmt.setString(paramIndex++, searchText);
            pstmt.setString(paramIndex++, searchText + "%");
            pstmt.setString(paramIndex++, searchText + "%");

            // FTS5 MATCH query - searches all indexed columns
            // Use normalized search for diacritic-insensitive matching
            String ftsQuery = searchNormalized + "*";  // Prefix search
            pstmt.setString(paramIndex++, ftsQuery);

            // Owner parameter if needed
            if (owner != null && !owner.equals("ALL") && !owner.equals("shared")) {
                pstmt.setString(paramIndex++, owner);
            }

            // Limit and offset
            pstmt.setInt(paramIndex++, maxResults);
            pstmt.setInt(paramIndex, offset);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : List.of();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : List.of();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner")
                );
                results.add(term);
            }
        }

        return results;
    }

    /**
     * Legacy LIKE-based search (fallback if FTS5 not available).
     */
    private List<Term> searchTermsLegacy(String searchText, int maxResults, int offset, String owner) {
        List<Term> results = new ArrayList<>();

        // Normalize search text for diacritic-insensitive search
        String searchNormalized = DiacriticUtils.removeDiacritics(searchText).toLowerCase();

        // Build unified optimized SQL query using indexes on normalized columns
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT *,
                CASE
                    -- Priority 1: Exact match in source/target (case-insensitive)
                    WHEN LOWER(TRIM(source_term)) = LOWER(?) THEN 1
                    WHEN LOWER(TRIM(target_term)) = LOWER(?) THEN 1

                    -- Priority 2: Exact match in normalized source/target (diacritic-insensitive)
                    WHEN source_term_normalized = ? THEN 2
                    WHEN target_term_normalized = ? THEN 2

                    -- Priority 3: Starts with in source/target (exact)
                    WHEN LOWER(source_term) LIKE LOWER(?) THEN 3
                    WHEN LOWER(target_term) LIKE LOWER(?) THEN 3

                    -- Priority 4: Starts with in normalized source/target (diacritic-insensitive)
                    WHEN source_term_normalized LIKE ? THEN 4
                    WHEN target_term_normalized LIKE ? THEN 4

                    -- Priority 5: Contains in source/target (exact)
                    WHEN LOWER(source_term) LIKE LOWER(?) THEN 5
                    WHEN LOWER(target_term) LIKE LOWER(?) THEN 5

                    -- Priority 6: Contains in normalized source/target (diacritic-insensitive)
                    WHEN source_term_normalized LIKE ? THEN 6
                    WHEN target_term_normalized LIKE ? THEN 6

                    -- Priority 7: Contains in context/notes (normalized)
                    WHEN context_normalized LIKE ? THEN 7
                    WHEN LOWER(notes) LIKE LOWER(?) THEN 7

                    ELSE 8
                END AS priority
            FROM terms
            WHERE (
                LOWER(source_term) LIKE LOWER(?)
                OR LOWER(target_term) LIKE LOWER(?)
                OR source_term_normalized LIKE ?
                OR target_term_normalized LIKE ?
                OR context_normalized LIKE ?
                OR LOWER(notes) LIKE LOWER(?)
            )
        """);

        // Add owner filter
        if (owner != null && !owner.equals("ALL")) {
            if (owner.equals("shared")) {
                sqlBuilder.append(" AND owner = 'shared'");
            } else {
                sqlBuilder.append(" AND owner = ?");
            }
        }

        sqlBuilder.append(" ORDER BY priority ASC, date_added DESC LIMIT ? OFFSET ?");

        try (PreparedStatement pstmt = connection.prepareStatement(sqlBuilder.toString())) {
            String searchPattern = "%" + searchText + "%";
            String searchPatternNorm = "%" + searchNormalized + "%";
            String startsWithPattern = searchText + "%";
            String startsWithPatternNorm = searchNormalized + "%";

            int paramIndex = 1;

            // Priority 1: Exact matches
            pstmt.setString(paramIndex++, searchText);
            pstmt.setString(paramIndex++, searchText);

            // Priority 2: Exact normalized matches
            pstmt.setString(paramIndex++, searchNormalized);
            pstmt.setString(paramIndex++, searchNormalized);

            // Priority 3: Starts with (exact)
            pstmt.setString(paramIndex++, startsWithPattern);
            pstmt.setString(paramIndex++, startsWithPattern);

            // Priority 4: Starts with (normalized) - Can use index!
            pstmt.setString(paramIndex++, startsWithPatternNorm);
            pstmt.setString(paramIndex++, startsWithPatternNorm);

            // Priority 5: Contains (exact)
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);

            // Priority 6: Contains (normalized)
            pstmt.setString(paramIndex++, searchPatternNorm);
            pstmt.setString(paramIndex++, searchPatternNorm);

            // Priority 7: Context/notes
            pstmt.setString(paramIndex++, searchPatternNorm);
            pstmt.setString(paramIndex++, searchPattern);

            // WHERE clause patterns
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPatternNorm);
            pstmt.setString(paramIndex++, searchPatternNorm);
            pstmt.setString(paramIndex++, searchPatternNorm);
            pstmt.setString(paramIndex++, searchPattern);

            // Owner parameter if needed
            if (owner != null && !owner.equals("ALL") && !owner.equals("shared")) {
                pstmt.setString(paramIndex++, owner);
            }

            // Limit and offset
            pstmt.setInt(paramIndex++, maxResults);
            pstmt.setInt(paramIndex, offset);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : new ArrayList<>();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : new ArrayList<>();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner"));
                results.add(term);
            }
        } catch (SQLException e) {
            System.err.println("Error searching terms: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Calculate priority for a term based on search text
     * Lower priority = better match
     */
    private int calculateTermPriority(Term term, String searchText, boolean diacriticInsensitive) {
        String search = diacriticInsensitive ?
            DiacriticUtils.removeDiacritics(searchText).toLowerCase() :
            searchText.toLowerCase();

        String source = term.getSourceTerm() != null ? term.getSourceTerm() : "";
        String target = term.getTargetTerm() != null ? term.getTargetTerm() : "";
        String context = term.getContexts() != null && !term.getContexts().isEmpty() ?
            term.getContexts().get(0) : "";

        if (diacriticInsensitive) {
            source = DiacriticUtils.removeDiacritics(source).toLowerCase();
            target = DiacriticUtils.removeDiacritics(target).toLowerCase();
            context = DiacriticUtils.removeDiacritics(context).toLowerCase();
        } else {
            source = source.toLowerCase();
            target = target.toLowerCase();
            context = context.toLowerCase();
        }

        // Priority 1: Exact match in source/target
        if (source.trim().equals(search) || target.trim().equals(search)) {
            return 1;
        }

        // Priority 2: Exact match in context (whole word)
        if (context.trim().equals(search) ||
            context.matches(".*\\b" + java.util.regex.Pattern.quote(search) + "\\b.*")) {
            return 2;
        }

        // Priority 3: Starts with in source/target
        if (source.startsWith(search) || target.startsWith(search)) {
            return 3;
        }

        // Priority 4: Starts with in context
        if (context.startsWith(search)) {
            return 4;
        }

        // Priority 5: Contains in source/target
        if (source.contains(search) || target.contains(search)) {
            return 5;
        }

        // Priority 6: Contains in context
        if (context.contains(search)) {
            return 6;
        }

        return 7;
    }

    // Keep backward compatibility
    public List<Term> searchTerms(String searchText, int maxResults, int offset) {
        return searchTerms(searchText, maxResults, offset, "ALL");
    }
    private List<Term> searchTermsSQL(String searchText, int maxResults, int offset, String owner) {
        List<Term> results = new ArrayList<>();

        // Build SQL with improved ranking that prioritizes exact matches across all fields
        StringBuilder sqlBuilder = new StringBuilder("""
        SELECT *,
            CASE
                -- Priority 1: Exact match in primary fields (source_term, target_term)
                WHEN LOWER(TRIM(source_term)) = LOWER(?) THEN 1
                WHEN LOWER(TRIM(target_term)) = LOWER(?) THEN 1

                -- Priority 2: Exact match in context (full context or standalone word)
                WHEN LOWER(TRIM(context)) = LOWER(?) THEN 2
                WHEN LOWER(context) LIKE LOWER(?) THEN 2

                -- Priority 3: Starts with in primary fields
                WHEN LOWER(source_term) LIKE LOWER(?) THEN 3
                WHEN LOWER(target_term) LIKE LOWER(?) THEN 3

                -- Priority 4: Starts with in context
                WHEN LOWER(context) LIKE LOWER(?) THEN 4

                -- Priority 5: Contains in primary fields
                WHEN LOWER(source_term) LIKE LOWER(?) THEN 5
                WHEN LOWER(target_term) LIKE LOWER(?) THEN 5

                -- Priority 6: Contains in secondary fields
                WHEN LOWER(context) LIKE LOWER(?) THEN 6
                WHEN LOWER(notes) LIKE LOWER(?) THEN 6
                WHEN LOWER(contributor) LIKE LOWER(?) THEN 6

                ELSE 7
            END AS priority
        FROM terms
        WHERE (LOWER(source_term) LIKE LOWER(?)
           OR LOWER(target_term) LIKE LOWER(?)
           OR LOWER(context) LIKE LOWER(?)
           OR LOWER(notes) LIKE LOWER(?)
           OR LOWER(contributor) LIKE LOWER(?))
    """);

        // Add owner filter if not "ALL"
        if (owner != null && !owner.equals("ALL")) {
            if (owner.equals("shared")) {
                sqlBuilder.append(" AND owner = 'shared'");
            } else {
                sqlBuilder.append(" AND owner = ?");
            }
        }

        sqlBuilder.append(" ORDER BY priority ASC, date_added DESC LIMIT ? OFFSET ?");

        try (PreparedStatement pstmt = connection.prepareStatement(sqlBuilder.toString())) {
            String searchPattern = "%" + searchText + "%";
            String startsWithPattern = searchText + "%";
            String exactContextPattern = "% " + searchText + " %";  // Exact word in context

            int paramIndex = 1;
            // Priority 1: Exact matches in primary fields
            pstmt.setString(paramIndex++, searchText);
            pstmt.setString(paramIndex++, searchText);

            // Priority 2: Exact matches in context
            pstmt.setString(paramIndex++, searchText);
            pstmt.setString(paramIndex++, exactContextPattern);

            // Priority 3: Starts with in primary fields
            pstmt.setString(paramIndex++, startsWithPattern);
            pstmt.setString(paramIndex++, startsWithPattern);

            // Priority 4: Starts with in context
            pstmt.setString(paramIndex++, startsWithPattern);

            // Priority 5: Contains in primary fields
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);

            // Priority 6: Contains in secondary fields
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);

            // WHERE clause patterns
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);
            pstmt.setString(paramIndex++, searchPattern);

            // Add owner parameter if filtering
            if (owner != null && !owner.equals("ALL") && !owner.equals("shared")) {
                pstmt.setString(paramIndex++, owner);
            }

            pstmt.setInt(paramIndex++, maxResults);
            pstmt.setInt(paramIndex, offset);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : new ArrayList<>();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : new ArrayList<>();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner") // ADD THIS
                );
                results.add(term);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }

    // Keep backward compatibility
    private List<Term> searchTermsSQL(String searchText, int maxResults, int offset) {
        return searchTermsSQL(searchText, maxResults, offset, "ALL");
    }


    /**
     * Search for terms ignoring diacritics - WITH OWNER FILTER
     * Linha ~380 no DatabaseManager.java
     */
    private List<Term> searchWithoutDiacritics(String searchText, int maxResults, int offset, String owner) {
        class TermWithPriority {
            Term term;
            int priority;

            TermWithPriority(Term term, int priority) {
                this.term = term;
                this.priority = priority;
            }
        }

        List<TermWithPriority> resultsWithPriority = new ArrayList<>();
        String normalizedSearch = DiacriticUtils.removeDiacritics(searchText).toLowerCase();

        // ✅ CORRIGIDO: Adicionar filtro de owner na query SQL
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM terms WHERE 1=1");

        // Add owner filter
        if (owner != null && !owner.equals("ALL")) {
            if (owner.equals("shared")) {
                sqlBuilder.append(" AND owner = 'shared'");
            } else {
                sqlBuilder.append(" AND owner = ?");
            }
        }

        try (PreparedStatement pstmt = connection.prepareStatement(sqlBuilder.toString())) {
            // Set owner parameter if needed
            if (owner != null && !owner.equals("ALL") && !owner.equals("shared")) {
                pstmt.setString(1, owner);
            }

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String sourceTerm = rs.getString("source_term");
                String targetTerm = rs.getString("target_term");
                String context = rs.getString("context");
                String notes = rs.getString("notes");

                int priority = -1;

                // Check source term
                if (sourceTerm != null) {
                    String normalizedSource = DiacriticUtils.removeDiacritics(sourceTerm).toLowerCase();
                    if (normalizedSource.equals(normalizedSearch)) {
                        priority = 1;
                    } else if (normalizedSource.startsWith(normalizedSearch)) {
                        priority = priority == -1 ? 2 : Math.min(priority, 2);
                    } else if (normalizedSource.contains(normalizedSearch)) {
                        priority = priority == -1 ? 3 : Math.min(priority, 3);
                    }
                }

                // Check target term
                if (targetTerm != null) {
                    String normalizedTarget = DiacriticUtils.removeDiacritics(targetTerm).toLowerCase();
                    if (normalizedTarget.equals(normalizedSearch)) {
                        priority = 1;
                    } else if (normalizedTarget.startsWith(normalizedSearch) && priority == -1) {
                        priority = 2;
                    } else if (normalizedTarget.contains(normalizedSearch) && priority == -1) {
                        priority = 3;
                    }
                }

                // Check context
                if (priority == -1 && context != null) {
                    String normalizedContext = DiacriticUtils.removeDiacritics(context).toLowerCase();
                    if (normalizedContext.contains(normalizedSearch)) {
                        priority = 4;
                    }
                }

                // Check notes
                if (priority == -1 && notes != null) {
                    String normalizedNotes = DiacriticUtils.removeDiacritics(notes).toLowerCase();
                    if (normalizedNotes.contains(normalizedSearch)) {
                        priority = 4;
                    }
                }

                // If we found a match, add it
                if (priority != -1) {
                    // Convert single strings to Lists
                    String contextStr = rs.getString("context");
                    List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                            ? List.of(contextStr)
                            : new ArrayList<>();

                    String contributorStr = rs.getString("contributor");
                    List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                            ? List.of(contributorStr)
                            : new ArrayList<>();

                    Term term = new Term(
                            rs.getInt("id"),
                            sourceTerm,
                            rs.getString("source_language"),
                            targetTerm,
                            rs.getString("target_language"),
                            contexts,
                            contributors,
                            rs.getString("date_added"),
                            rs.getString("verified_status"),
                            notes,
                            rs.getString("owner"));
                    resultsWithPriority.add(new TermWithPriority(term, priority));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Sort by priority
        resultsWithPriority.sort((a, b) -> Integer.compare(a.priority, b.priority));

        // Extract terms and apply limit
        List<Term> results = new ArrayList<>();
        for (TermWithPriority twp : resultsWithPriority) {
            if (results.size() >= maxResults) {
                break;
            }
            results.add(twp.term);
        }

        return results;
    }

   public SyncResult syncWithCloud() {
        if (syncManager == null) {
            System.err.println("[DatabaseManager] SyncManager not initialized");
            return new SyncResult(0, 0);
        }

        SyncResult result = syncManager.syncFromCloud(false);
        return new SyncResult(result.termsAdded, result.duplicatesSkipped, result.termsUpdated);
    }

    public SyncResult fullBidirectionalSync() {
        if (syncManager != null) {
            return syncManager.fullBidirectionalSync();
        }
        return new SyncResult(0, 0, 0);}

    public List<Term> getRecentTerms(int limit) {
        List<Term> results = new ArrayList<>();
        String sql = "SELECT * FROM terms ORDER BY date_added DESC LIMIT ?";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(terms)");
            while (rs.next()) {
                System.out.println(rs.getString("name"));
            }
        } catch (SQLException e) {
            throw new GlossaryRuntimeException("Database query failed", e);
        }

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Convert single strings to Lists
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : new ArrayList<>();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : new ArrayList<>();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner"));
                results.add(term);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }



    /**
     * Get terms added AFTER a specific date/time (for sync - newer terms)
     */
    public List<Term> getTermsAddedAfter(String dateTime) {
        List<Term> results = new ArrayList<>();
        String sql = "SELECT * FROM terms WHERE datetime(date_added) > datetime(?) ORDER BY date_added DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, dateTime);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Convert single strings to Lists
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : new ArrayList<>();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : new ArrayList<>();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner"));
                results.add(term);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Get terms added BEFORE a specific date/time (for pagination - older terms)
     */
    public List<Term> getTermsAddedBefore(String dateTime, int limit) {
        List<Term> results = new ArrayList<>();
        String sql = "SELECT * FROM terms WHERE datetime(date_added) < datetime(?) ORDER BY date_added DESC LIMIT ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, dateTime);
            pstmt.setInt(2, limit);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Convert single strings to Lists
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : new ArrayList<>();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : new ArrayList<>();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner"));
                results.add(term);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }

    public void updateTermStatus(int termId, String status) {
        String sql = "UPDATE terms SET verified_status = ? WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, termId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateTerm(int termId, UpdateTermParams params) {
        // Delegate to original method signature for backwards compatibility during transition
        updateTermInternal(termId, params.sourceTerm, params.sourceLanguage,
                          params.targetTerm, params.targetLanguage, params.context,
                          params.contributor, params.notes, params.status, params.owner);
    }

    private void updateTermInternal(int termId, String sourceTerm, String sourceLanguage,
                           String targetTerm, String targetLanguage, String context,
                           String contributor, String notes, String status, String owner) {

        // Get OLD hash and term details BEFORE updating
        String oldHash = null;
        String oldSourceTerm = null;
        String oldTargetTerm = null;

        if (syncManager != null) {
            String getDetailsSQL = "SELECT content_hash, source_term, target_term FROM terms WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(getDetailsSQL)) {
                pstmt.setInt(1, termId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    oldHash = rs.getString("content_hash");
                    oldSourceTerm = rs.getString("source_term");
                    oldTargetTerm = rs.getString("target_term");
                }
            } catch (SQLException e) {
                System.err.println("Error getting old term details: " + e.getMessage());
            }
        }

        // Calculate NEW hash
        String newHash = null;
        if (syncManager != null) {
            newHash = syncManager.generateHash(sourceTerm, sourceLanguage, targetTerm,
                    targetLanguage, context, contributor);
        }

        // Update local database with NEW hash and normalized columns
        String sql = """
        UPDATE terms SET
            source_term = ?, source_language = ?, target_term = ?, target_language = ?,
            context = ?, contributor = ?, notes = ?, verified_status = ?, owner = ?,
            content_hash = ?,
            source_term_normalized = ?, target_term_normalized = ?, context_normalized = ?
        WHERE id = ?
    """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sourceTerm);
            pstmt.setString(2, sourceLanguage);
            pstmt.setString(3, targetTerm);
            pstmt.setString(4, targetLanguage);
            pstmt.setString(5, context);
            pstmt.setString(6, contributor);
            pstmt.setString(7, notes);
            pstmt.setString(8, status);
            pstmt.setString(9, owner);
            pstmt.setString(10, newHash);  // NEW hash

            // Populate normalized columns
            String sourceNormalized = sourceTerm != null ?
                DiacriticUtils.removeDiacritics(sourceTerm).toLowerCase() : null;
            String targetNormalized = targetTerm != null ?
                DiacriticUtils.removeDiacritics(targetTerm).toLowerCase() : null;
            String contextNormalized = context != null ?
                DiacriticUtils.removeDiacritics(context).toLowerCase() : null;

            pstmt.setString(11, sourceNormalized);
            pstmt.setString(12, targetNormalized);
            pstmt.setString(13, contextNormalized);
            pstmt.setInt(14, termId);

            int rowsUpdated = pstmt.executeUpdate();

            if (rowsUpdated > 0) {
                System.out.println("[DatabaseManager] Updated term ID " + termId + " locally");

                // Sync to cloud
                if (syncManager != null && oldHash != null) {
                    EditSyncParams editParams = new EditSyncParams(
                            termId,
                            sourceTerm,
                            sourceLanguage,
                            targetTerm,
                            targetLanguage,
                            context,
                            contributor,
                            notes,
                            oldHash,        // Pass OLD hash to find cloud record
                            status,
                            oldSourceTerm,  // Pass old content for logging
                            oldTargetTerm
                    );
                    syncManager.syncEditToCloud(editParams);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error updating term: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateContentHash(int id, String hash) {
        String sql = "UPDATE terms SET content_hash = ? WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, hash);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating content hash: " + e.getMessage());
        }
    }

    /**
     * Show duplicate report in console
     */
    public void reportDuplicates() {
        List<List<Term>> duplicates = findDuplicates();

        if (duplicates.isEmpty()) {
            System.out.println("[DatabaseManager] No duplicates found!");
            return;
        }

        System.out.println("[DatabaseManager] Found " + duplicates.size() + " groups of duplicates:");

        for (List<Term> group : duplicates) {
            System.out.println("\nDuplicate group (" + group.size() + " copies):");
            for (Term term : group) {
                System.out.println("  ID " + term.getId() + ": " +
                        term.getSourceTerm() + " → " + term.getTargetTerm() +
                        " (added: " + term.getDateAdded() + ")");
            }
        }
    }

    public void deleteTerm(int termId, boolean deleteFromCloud) {
        String hash = null;
        String sourceTerm = null;
        String targetTerm = null;
        Integer cloudId = null;

        if (deleteFromCloud && syncManager != null) {
            String getDetailsSQL = "SELECT content_hash, source_term, target_term, cloud_id FROM terms WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(getDetailsSQL)) {
                pstmt.setInt(1, termId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    hash = rs.getString("content_hash");
                    sourceTerm = rs.getString("source_term");
                    targetTerm = rs.getString("target_term");
                    int cid = rs.getInt("cloud_id");
                    cloudId = cid > 0 ? cid : null;
                }
            } catch (SQLException e) {
                System.err.println("Error getting term details for deletion: " + e.getMessage());
            }
        }

        String sql = "DELETE FROM terms WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, termId);
            pstmt.executeUpdate();
            System.out.println("[DatabaseManager] Deleted term ID " + termId + " from local database");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error deleting term: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (deleteFromCloud && syncManager != null && hash != null) {
            // OPTIMISTIC UI: Mark cloud_id as recently modified to ignore realtime DELETE
            if (cloudId != null) {
                RealtimeSyncService.markAsRecentlyModified(cloudId);
            }

            syncManager.deleteFromCloud(hash, termId, sourceTerm, targetTerm);
        }
    }

    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static class Term {
        private final int id;
        private final String sourceTerm;
        private final String sourceLanguage;
        private final String targetTerm;
        private final String targetLanguage;
        private final List<String> contexts;
        private final List<String> contributors;
        private final String dateAdded;
        private final String verifiedStatus;
        private final String notes;
        private final String owner; // ADD THIS


        public Term(int id, String sourceTerm, String sourceLanguage, String targetTerm,
                    String targetLanguage, List<String> contexts, List<String> contributors,
                    String dateAdded, String verifiedStatus, String notes, String owner) {
            this.id = id;
            this.sourceTerm = sourceTerm;
            this.sourceLanguage = sourceLanguage;
            this.targetTerm = targetTerm;
            this.targetLanguage = targetLanguage;
            this.contexts = contexts != null ? new ArrayList<>(contexts) : new ArrayList<>();
            this.contributors = contributors != null ? new ArrayList<>(contributors) : new ArrayList<>();
            this.dateAdded = dateAdded;
            this.verifiedStatus = verifiedStatus;
            this.notes = notes;
            this.owner = owner; // ADD THIS

        }

        /**
         * Builder pattern for constructing Term objects with reduced parameter count
         */
        public static class Builder {
            private int id;
            private String sourceTerm;
            private String sourceLanguage;
            private String targetTerm;
            private String targetLanguage;
            private List<String> contexts;
            private List<String> contributors;
            private String dateAdded;
            private String verifiedStatus;
            private String notes;
            private String owner;

            public Builder id(int id) {
                this.id = id;
                return this;
            }

            public Builder sourceTerm(String sourceTerm) {
                this.sourceTerm = sourceTerm;
                return this;
            }

            public Builder sourceLanguage(String sourceLanguage) {
                this.sourceLanguage = sourceLanguage;
                return this;
            }

            public Builder targetTerm(String targetTerm) {
                this.targetTerm = targetTerm;
                return this;
            }

            public Builder targetLanguage(String targetLanguage) {
                this.targetLanguage = targetLanguage;
                return this;
            }

            public Builder contexts(List<String> contexts) {
                this.contexts = contexts;
                return this;
            }

            public Builder contributors(List<String> contributors) {
                this.contributors = contributors;
                return this;
            }

            public Builder dateAdded(String dateAdded) {
                this.dateAdded = dateAdded;
                return this;
            }

            public Builder verifiedStatus(String verifiedStatus) {
                this.verifiedStatus = verifiedStatus;
                return this;
            }

            public Builder notes(String notes) {
                this.notes = notes;
                return this;
            }

            public Builder owner(String owner) {
                this.owner = owner;
                return this;
            }

            public Term build() {
                return new Term(id, sourceTerm, sourceLanguage, targetTerm, targetLanguage,
                              contexts, contributors, dateAdded, verifiedStatus, notes, owner);
            }
        }

        public int getId() {
            return id;
        }

        public String getSourceTerm() {
            return sourceTerm;
        }

        public String getSourceLanguage() {
            return sourceLanguage;
        }

        public String getTargetTerm() {
            return targetTerm;
        }

        public String getTargetLanguage() {
            return targetLanguage;
        }

        public List<String> getContexts() {
            return new ArrayList<>(contexts);
        }

        public List<String> getContributors() {
            return new ArrayList<>(contributors);
        }

        public String getDateAdded() {
            return dateAdded;
        }

        public String getVerifiedStatus() {
            return verifiedStatus;
        }

        public String getNotes() {
            return notes;
        }
        public String getOwner() { return owner; } // ADD THIS

        @Override
        public String toString() {
            return sourceTerm + " → " + targetTerm;
        }
    }

    public static class SyncResult {
        public final int termsAdded;
        public final int duplicatesSkipped;
        public final int termsUpdated;
        public final int cloudUpdated;

        public SyncResult(int added, int duplicates) {
            this.termsAdded = added;
            this.duplicatesSkipped = duplicates;
            this.termsUpdated = 0;
            this.cloudUpdated = 0;
        }

        public SyncResult(int added, int duplicates, int updated) {
            this.termsAdded = added;
            this.duplicatesSkipped = duplicates;
            this.termsUpdated = updated;
            this.cloudUpdated = 0;
        }

        public SyncResult(int added, int duplicates, int updated, int cloudUpdated) {
            this.termsAdded = added;
            this.duplicatesSkipped = duplicates;
            this.termsUpdated = updated;
            this.cloudUpdated = cloudUpdated;
        }
    }


    /**
     * Find duplicate terms based on content_hash
     */
    public List<List<Term>> findDuplicates() {
        List<List<Term>> duplicateGroups = new ArrayList<>();

        String sql = """
        SELECT content_hash, COUNT(*) as dup_count
        FROM terms
        WHERE content_hash IS NOT NULL
        GROUP BY content_hash
        HAVING dup_count > 1
    """;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String hash = rs.getString("content_hash");
                List<Term> group = getTermsByHash(hash);

                if (group.size() > 1) {
                    duplicateGroups.add(group);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error finding duplicates: " + e.getMessage());
        }

        return duplicateGroups;
    }

    /**
     * Helper method to get all terms with a specific hash
     */
    private List<Term> getTermsByHash(String hash) {
        List<Term> terms = new ArrayList<>();

        String sql = "SELECT * FROM terms WHERE content_hash = ? ORDER BY id ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, hash);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Convert single strings to Lists
                String contextStr = rs.getString("context");
                List<String> contexts = (contextStr != null && !contextStr.isEmpty())
                        ? List.of(contextStr)
                        : new ArrayList<>();

                String contributorStr = rs.getString("contributor");
                List<String> contributors = (contributorStr != null && !contributorStr.isEmpty())
                        ? List.of(contributorStr)
                        : new ArrayList<>();

                Term term = new Term(
                        rs.getInt("id"),
                        rs.getString("source_term"),
                        rs.getString("source_language"),
                        rs.getString("target_term"),
                        rs.getString("target_language"),
                        contexts,
                        contributors,
                        rs.getString("date_added"),
                        rs.getString("verified_status"),
                        rs.getString("notes"),
                        rs.getString("owner"));
                terms.add(term);
            }
        } catch (SQLException e) {
            System.err.println("Error getting terms by hash: " + e.getMessage());
        }

        return terms;
    }

       /**
     * Remove duplicate terms based on content_hash
     * Keeps the term with lowest ID (oldest)
     */
    public int removeDuplicates() {
        int removed = 0;

        String sql = """
    DELETE FROM terms
    WHERE id NOT IN (
        SELECT MIN(id)
        FROM terms
        WHERE content_hash IS NOT NULL
        GROUP BY content_hash
    )
    AND content_hash IS NOT NULL
""";

        try (Statement stmt = connection.createStatement()) {
            removed = stmt.executeUpdate(sql);
            System.out.println("[DatabaseManager] Removed " + removed + " duplicate terms");
        } catch (SQLException e) {
            System.err.println("Error removing duplicates: " + e.getMessage());
        }

        return removed;
    }


// ============================================================================
// ADICIONAR NO DatabaseManager.java - Migration para coluna OWNER na tabela TERMS
// ============================================================================

    /**
     * Check and add owner column if it doesn't exist in TERMS table
     * Call this in initializeDatabaseConnection() after createTables()
     */
    private void migrateOwnerColumn() {
        try {
            // Check if owner column exists in TERMS table
            String checkQuery = "PRAGMA table_info(terms)";
            boolean ownerExists = false;

            try (PreparedStatement stmt = connection.prepareStatement(checkQuery);
                 ResultSet rs = stmt.executeQuery()) {

                System.out.println("[DatabaseManager] Checking columns in TERMS table:");
                while (rs.next()) {
                    String columnName = rs.getString("name");
                    System.out.println("  - " + columnName);
                    if ("owner".equals(columnName)) {
                        ownerExists = true;
                    }
                }
            }

            if (!ownerExists) {
                System.out.println("[DatabaseManager] ⚠️ Owner column does not exist. Adding it now...");

                // Add owner column with default value 'shared'
                String alterQuery = "ALTER TABLE terms ADD COLUMN owner TEXT DEFAULT 'shared'";
                try (PreparedStatement stmt = connection.prepareStatement(alterQuery)) {
                    stmt.executeUpdate();
                    System.out.println("[DatabaseManager] ✅ Added 'owner' column to terms table");
                }

                // Update existing rows to have 'shared' as owner
                String updateQuery = "UPDATE terms SET owner = 'shared' WHERE owner IS NULL OR owner = ''";
                try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
                    int updated = stmt.executeUpdate();
                    System.out.println("[DatabaseManager] ✅ Updated " + updated + " existing terms with owner='shared'");
                }

                System.out.println("[DatabaseManager] ✅ Owner column migration completed!");
            } else {
                System.out.println("[DatabaseManager] ✅ Owner column already exists in terms table");
            }

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ❌ Error during owner column migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Migrate to add normalized search columns for performance optimization.
     * Adds source_term_normalized, target_term_normalized, context_normalized.
     */
    private void migrateNormalizedColumns() {
        try {
            String checkQuery = "PRAGMA table_info(terms)";
            boolean hasSourceNormalized = false;
            boolean hasTargetNormalized = false;
            boolean hasContextNormalized = false;
            boolean hasContentHash = false;

            try (PreparedStatement stmt = connection.prepareStatement(checkQuery);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String columnName = rs.getString("name");
                    if ("source_term_normalized".equals(columnName)) {
                        hasSourceNormalized = true;
                    } else if ("target_term_normalized".equals(columnName)) {
                        hasTargetNormalized = true;
                    } else if ("context_normalized".equals(columnName)) {
                        hasContextNormalized = true;
                    } else if ("content_hash".equals(columnName)) {
                        hasContentHash = true;
                    }
                }
            }

            // Add missing columns
            if (!hasContentHash) {
                System.out.println("[DatabaseManager] Adding content_hash column...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE terms ADD COLUMN content_hash TEXT");
                    System.out.println("[DatabaseManager] ✅ Added content_hash column");
                }
            }

            if (!hasSourceNormalized) {
                System.out.println("[DatabaseManager] Adding source_term_normalized column...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE terms ADD COLUMN source_term_normalized TEXT");
                    System.out.println("[DatabaseManager] ✅ Added source_term_normalized column");
                }
            }

            if (!hasTargetNormalized) {
                System.out.println("[DatabaseManager] Adding target_term_normalized column...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE terms ADD COLUMN target_term_normalized TEXT");
                    System.out.println("[DatabaseManager] ✅ Added target_term_normalized column");
                }
            }

            if (!hasContextNormalized) {
                System.out.println("[DatabaseManager] Adding context_normalized column...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE terms ADD COLUMN context_normalized TEXT");
                    System.out.println("[DatabaseManager] ✅ Added context_normalized column");
                }
            }

            // Populate normalized columns for existing rows if any column was just added
            if (!hasSourceNormalized || !hasTargetNormalized || !hasContextNormalized) {
                System.out.println("[DatabaseManager] Populating normalized columns for existing terms...");
                populateNormalizedColumns();
            }

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ❌ Error during normalized columns migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Populate normalized columns for all existing rows.
     */
    private void populateNormalizedColumns() {
        try {
            String selectSQL = "SELECT id, source_term, target_term, context FROM terms";
            String updateSQL = "UPDATE terms SET source_term_normalized = ?, target_term_normalized = ?, context_normalized = ? WHERE id = ?";

            int updated = 0;
            try (Statement selectStmt = connection.createStatement();
                 ResultSet rs = selectStmt.executeQuery(selectSQL);
                 PreparedStatement updateStmt = connection.prepareStatement(updateSQL)) {

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String sourceTerm = rs.getString("source_term");
                    String targetTerm = rs.getString("target_term");
                    String context = rs.getString("context");

                    String sourceNormalized = sourceTerm != null ? DiacriticUtils.removeDiacritics(sourceTerm).toLowerCase() : null;
                    String targetNormalized = targetTerm != null ? DiacriticUtils.removeDiacritics(targetTerm).toLowerCase() : null;
                    String contextNormalized = context != null ? DiacriticUtils.removeDiacritics(context).toLowerCase() : null;

                    updateStmt.setString(1, sourceNormalized);
                    updateStmt.setString(2, targetNormalized);
                    updateStmt.setString(3, contextNormalized);
                    updateStmt.setInt(4, id);
                    updateStmt.executeUpdate();
                    updated++;
                }
            }

            System.out.println("[DatabaseManager] ✅ Populated normalized columns for " + updated + " terms");

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ❌ Error populating normalized columns: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create indexes for optimized search performance.
     */
    private void createSearchIndexes() {
        try {
            // Check existing indexes
            Set<String> existingIndexes = new HashSet<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index'")) {
                while (rs.next()) {
                    existingIndexes.add(rs.getString("name"));
                }
            }

            // Create indexes if they don't exist
            if (!existingIndexes.contains("idx_source_normalized")) {
                System.out.println("[DatabaseManager] Creating index on source_term_normalized...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("CREATE INDEX idx_source_normalized ON terms(source_term_normalized COLLATE NOCASE)");
                    System.out.println("[DatabaseManager] ✅ Created idx_source_normalized");
                }
            }

            if (!existingIndexes.contains("idx_target_normalized")) {
                System.out.println("[DatabaseManager] Creating index on target_term_normalized...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("CREATE INDEX idx_target_normalized ON terms(target_term_normalized COLLATE NOCASE)");
                    System.out.println("[DatabaseManager] ✅ Created idx_target_normalized");
                }
            }

            if (!existingIndexes.contains("idx_context_normalized")) {
                System.out.println("[DatabaseManager] Creating index on context_normalized...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("CREATE INDEX idx_context_normalized ON terms(context_normalized COLLATE NOCASE)");
                    System.out.println("[DatabaseManager] ✅ Created idx_context_normalized");
                }
            }

            if (!existingIndexes.contains("idx_content_hash")) {
                System.out.println("[DatabaseManager] Creating unique index on content_hash...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("CREATE UNIQUE INDEX idx_content_hash ON terms(content_hash)");
                    System.out.println("[DatabaseManager] ✅ Created idx_content_hash");
                }
            }

            if (!existingIndexes.contains("idx_recents")) {
                System.out.println("[DatabaseManager] Creating composite index for recents page (date_added DESC, id DESC)...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("CREATE INDEX idx_recents ON terms(date_added DESC, id DESC)");
                    System.out.println("[DatabaseManager] ✅ Created idx_recents - Recents page will now load instantly!");
                }
            }

            if (!existingIndexes.contains("idx_recents_by_owner")) {
                System.out.println("[DatabaseManager] Creating owner-first composite index for filtered recents (owner, date_added DESC, id DESC)...");
                System.out.println("[DatabaseManager] This will make owner-filtered recents MUCH faster (5s → <100ms)");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("CREATE INDEX idx_recents_by_owner ON terms(owner, date_added DESC, id DESC)");
                    System.out.println("[DatabaseManager] ✅ Created idx_recents_by_owner - Owner-filtered recents now instant!");
                }
            }

            System.out.println("[DatabaseManager] ✅ Search indexes verified/created");

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ❌ Error creating search indexes: " + e.getMessage());
            e.printStackTrace();
        }
    }

// ============================================================================
// FTS5 FULL-TEXT SEARCH OPTIMIZATION (for 1M+ records)
// ============================================================================

    /**
     * Create FTS5 virtual table for full-text search.
     * FTS5 is 30% faster than regular LIKE queries on large datasets (1M+ records).
     */
    private void createFTS5SearchIndex() {
        try {
            // Check if FTS5 table already exists
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='terms_fts'")) {
                if (rs.next()) {
                    System.out.println("[DatabaseManager] FTS5 search index already exists");
                    return;
                }
            }

            System.out.println("[DatabaseManager] Creating FTS5 full-text search index...");

            // Create FTS5 virtual table with all searchable columns
            // Using external content table to avoid data duplication
            String createFTS5 = """
                CREATE VIRTUAL TABLE terms_fts USING fts5(
                    source_term,
                    target_term,
                    context,
                    notes,
                    source_term_normalized,
                    target_term_normalized,
                    context_normalized,
                    content='terms',
                    content_rowid='id',
                    tokenize='porter unicode61'
                )
            """;

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createFTS5);
                System.out.println("[DatabaseManager] ✅ Created FTS5 virtual table");

                // Populate FTS5 table with existing data
                System.out.println("[DatabaseManager] Populating FTS5 index with existing data...");
                stmt.execute("INSERT INTO terms_fts(terms_fts) VALUES('rebuild')");
                System.out.println("[DatabaseManager] ✅ FTS5 index populated");
            }

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ❌ Error creating FTS5 index: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create triggers to keep FTS5 index in sync with main terms table.
     */
    private void createFTS5Triggers() {
        try {
            System.out.println("[DatabaseManager] Creating FTS5 sync triggers...");

            // Check if triggers already exist
            boolean triggersExist = false;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='trigger' AND name='terms_ai'")) {
                if (rs.next()) {
                    triggersExist = true;
                }
            }

            if (triggersExist) {
                System.out.println("[DatabaseManager] FTS5 triggers already exist");
                return;
            }

            try (Statement stmt = connection.createStatement()) {
                // INSERT trigger
                stmt.execute("""
                    CREATE TRIGGER terms_ai AFTER INSERT ON terms BEGIN
                        INSERT INTO terms_fts(rowid, source_term, target_term, context, notes,
                                             source_term_normalized, target_term_normalized, context_normalized)
                        VALUES (new.id, new.source_term, new.target_term, new.context, new.notes,
                               new.source_term_normalized, new.target_term_normalized, new.context_normalized);
                    END
                """);

                // DELETE trigger
                stmt.execute("""
                    CREATE TRIGGER terms_ad AFTER DELETE ON terms BEGIN
                        INSERT INTO terms_fts(terms_fts, rowid, source_term, target_term, context, notes,
                                             source_term_normalized, target_term_normalized, context_normalized)
                        VALUES('delete', old.id, old.source_term, old.target_term, old.context, old.notes,
                               old.source_term_normalized, old.target_term_normalized, old.context_normalized);
                    END
                """);

                // UPDATE trigger
                stmt.execute("""
                    CREATE TRIGGER terms_au AFTER UPDATE ON terms BEGIN
                        INSERT INTO terms_fts(terms_fts, rowid, source_term, target_term, context, notes,
                                             source_term_normalized, target_term_normalized, context_normalized)
                        VALUES('delete', old.id, old.source_term, old.target_term, old.context, old.notes,
                               old.source_term_normalized, old.target_term_normalized, old.context_normalized);
                        INSERT INTO terms_fts(rowid, source_term, target_term, context, notes,
                                             source_term_normalized, target_term_normalized, context_normalized)
                        VALUES (new.id, new.source_term, new.target_term, new.context, new.notes,
                               new.source_term_normalized, new.target_term_normalized, new.context_normalized);
                    END
                """);

                System.out.println("[DatabaseManager] ✅ Created FTS5 sync triggers");
            }

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ❌ Error creating FTS5 triggers: " + e.getMessage());
            e.printStackTrace();
        }
    }

// ============================================================================
// MODIFICAR O MÉTODO initializeDatabaseConnection() PARA CHAMAR A MIGRATION
// ============================================================================

    private void initializeDatabaseConnection() {
        try {
            // Explicitly load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Get database path in user's home directory for packaged app
            String dbPath = getDatabasePath();
            System.out.println("[DatabaseManager] Using database at: " + dbPath);

            String dbUrl = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(dbUrl);

            // IMPORTANT: Run database setup immediately to ensure tables/indexes exist
            // These operations are lightweight and must complete before any queries
            createTables();
            migrateOwnerColumn();
            migrateNormalizedColumns();
            createSearchIndexes();
            createFTS5SearchIndex();
            createFTS5Triggers();

        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseManager] SQLite JDBC driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Database connection error!");
            e.printStackTrace();
        }

        // Initialize sync manager
        initializeSyncManager();
    }

    /**
     * Notify initialization progress through the callback.
     */
    private void notifyProgress(String message, int current, int total) {
        if (syncManager != null) {
            SyncManager.InitializationCallback callback = syncManager.getInitializationCallback();
            if (callback != null) {
                callback.onProgress(message, current, total);
            }
        }
    }

// ============================================================================
// ATUALIZAR createTables() PARA INCLUIR OWNER (para novos bancos)
// ============================================================================

    private void createTables() throws SQLException {
        String createTermsTable = """
        CREATE TABLE IF NOT EXISTS terms (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_term TEXT NOT NULL,
            source_language TEXT NOT NULL,
            target_term TEXT NOT NULL,
            target_language TEXT NOT NULL,
            context TEXT,
            contributor TEXT,
            date_added DATETIME DEFAULT CURRENT_TIMESTAMP,
            verified_status TEXT DEFAULT 'draft',
            notes TEXT,
            owner TEXT DEFAULT 'shared',
            cloud_id INTEGER,
            content_hash TEXT,
            source_term_normalized TEXT,
            target_term_normalized TEXT,
            context_normalized TEXT
        )
    """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTermsTable);
            System.out.println("[DatabaseManager] Terms table ensured.");
        }
    }

// ============================================================================
// ATUALIZAR getAllOwners() PARA SER MAIS ROBUSTO
// ============================================================================

    /**
     * Get all unique owner names from the database
     */
    public List<String> getAllOwners() {
        List<String> owners = new ArrayList<>();

        try {
            // First verify the owner column exists
            String checkQuery = "PRAGMA table_info(terms)";
            boolean ownerExists = false;

            try (PreparedStatement stmt = connection.prepareStatement(checkQuery);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String columnName = rs.getString("name");
                    if ("owner".equals(columnName)) {
                        ownerExists = true;
                        break;
                    }
                }
            }

            if (!ownerExists) {
                System.err.println("[DatabaseManager] Owner column doesn't exist - running migration...");
                migrateOwnerColumn();
                return owners; // Return empty list, will work after migration
            }

            // Now safely query the owner column
            String sql = "SELECT DISTINCT owner FROM terms WHERE owner IS NOT NULL AND owner != '' ORDER BY owner";

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    String owner = rs.getString("owner");
                    // Don't include 'shared' in the list (it's the default)
                    if (owner != null && !owner.trim().isEmpty() && !owner.equals("shared")) {
                        owners.add(owner);
                    }
                }
                System.out.println("[DatabaseManager] Found " + owners.size() + " unique owners (excluding 'shared')");
            }

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error getting owners: " + e.getMessage());
            e.printStackTrace();
        }

        return owners;
    }

    /**
     * Get unique owner names (alias for getAllOwners)
     */
    public List<String> getUniqueOwners() {
        return getAllOwners();
    }

    /**
     * Shutdown the database manager cleanly
     */
    public void shutdown() {
        System.out.println("[DatabaseManager] Shutting down...");

        try {
            if (syncManager != null) {
                syncManager.shutdown();
                System.out.println("[DatabaseManager] Sync manager shut down");
            }

            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DatabaseManager] Database connection closed");
            }

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error closing database: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[DatabaseManager] Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get the sync manager
     */
    public SyncManager getSyncManager() {
        return syncManager;
    }

    /**
     * Get the database connection
     */
    public Connection getConnection() {
        return connection;
    }
}
