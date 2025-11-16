package com.example.glossariobuda;

import java.sql.*;

/**
 * Diagnostic tool to check and fix glossary version mismatches
 * Run this when you're experiencing unwanted reset triggers
 */
public class VersionDiagnosticTool {

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("GLOSSARY VERSION DIAGNOSTIC TOOL");
        System.out.println("=".repeat(70));

        try {
            // Check local version
            int localVersion = checkLocalVersion();
            System.out.println("\n📍 Local version: " + localVersion);

            // Check cloud version
            SupabaseClient supabase = new SupabaseClient();
            SupabaseClient.GlossaryMetadata cloudMeta = supabase.getGlossaryMetadata();
            System.out.println("☁️  Cloud version: " + cloudMeta.version);
            System.out.println("    Last reset date: " + (cloudMeta.lastResetDate != null ? cloudMeta.lastResetDate : "NULL"));
            System.out.println("    Reset reason: " + (cloudMeta.lastResetReason != null ? cloudMeta.lastResetReason : "NULL"));
            System.out.println("    Reset start date: " + (cloudMeta.resetStartDate != null ? cloudMeta.resetStartDate : "NULL"));
            System.out.println("    Reset end date: " + (cloudMeta.resetEndDate != null ? cloudMeta.resetEndDate : "NULL"));

            // Check if versions match
            System.out.println("\n" + "=".repeat(70));
            if (cloudMeta.version > localVersion) {
                System.out.println("⚠️  MISMATCH DETECTED!");
                System.out.println("Cloud version (" + cloudMeta.version + ") > Local version (" + localVersion + ")");
                System.out.println("This will trigger a reset on next app start!");
                System.out.println("\n🔧 To fix this, run: fixVersionMismatch(" + cloudMeta.version + ")");
            } else if (cloudMeta.version < localVersion) {
                System.out.println("⚠️  REVERSE MISMATCH!");
                System.out.println("Local version (" + localVersion + ") > Cloud version (" + cloudMeta.version + ")");
                System.out.println("Your local version is ahead of cloud - this is unusual.");
            } else {
                System.out.println("✅ Versions are in sync!");
                System.out.println("No reset will be triggered.");
            }
            System.out.println("=".repeat(70));

            // Ask user if they want to fix
            if (cloudMeta.version != localVersion) {
                System.out.println("\nWould you like to sync versions? (y/n)");
                System.out.println("Note: This will update your LOCAL version to match the CLOUD version");

                // Automatically fix (for now)
                System.out.println("\nAutomatically syncing versions...");
                fixVersionMismatch(cloudMeta.version);
            }

        } catch (Exception e) {
            System.err.println("❌ Error running diagnostic: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check the current local glossary version
     */
    private static int checkLocalVersion() {
        String dbPath = getDatabasePath();
        String sql = "SELECT value FROM sync_metadata WHERE key = 'glossary_version'";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("Warning: Could not read local version: " + e.getMessage());
        }

        return 1; // Default
    }

    /**
     * Fix version mismatch by updating local version to match cloud
     */
    private static void fixVersionMismatch(int targetVersion) {
        String dbPath = getDatabasePath();
        String sql = "INSERT OR REPLACE INTO sync_metadata (key, value) VALUES ('glossary_version', ?)";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, String.valueOf(targetVersion));
            pstmt.executeUpdate();

            System.out.println("✅ Successfully updated local version to: " + targetVersion);
            System.out.println("The reset will no longer be triggered on next app start.");

        } catch (SQLException e) {
            System.err.println("❌ Failed to update local version: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get database path (same logic as DatabaseManager)
     */
    private static String getDatabasePath() {
        java.io.File devDbFile = new java.io.File("src/main/resources/glossario_terms.db");

        if (devDbFile.exists()) {
            return "src/main/resources/glossario_terms.db";
        } else {
            String userHome = System.getProperty("user.home");
            return userHome + "/Documents/.glossariobuda/glossario_terms.db";
        }
    }

    /**
     * Reset both cloud and local versions to 0 (disables reset checking)
     */
    public static void resetVersionsToZero() {
        try {
            // Reset local
            fixVersionMismatch(0);

            System.out.println("\n⚠️  You also need to run this SQL in Supabase:");
            System.out.println("UPDATE glossary_metadata SET version = 0, last_reset_date = NULL, ");
            System.out.println("last_reset_reason = NULL, reset_start_date = NULL, reset_end_date = NULL WHERE id = 1;");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
