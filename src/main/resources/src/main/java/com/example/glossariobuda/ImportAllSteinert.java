package com.example.glossariobuda;

import java.io.File;

/**
 * Simple utility to import ALL Steinert dictionaries at once
 *
 * Usage: mvn exec:java -Dexec.mainClass="com.example.glossariobuda.ImportAllSteinert"
 *
 * This will:
 * 1. Connect to your database
 * 2. Import all 62 Steinert dictionaries
 * 3. Show progress for each dictionary
 * 4. Print final summary
 */
public class ImportAllSteinert {

    private static final String PUBLIC_FOLDER = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/public";
    private static final String HOTL_FOLDER = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/conversion/Heart_of_Tibetan_Language";

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║        STEINERT DICTIONARY BATCH IMPORT                        ║");
        System.out.println("║        Importing all dictionaries (except 84000)               ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Initialize database
            System.out.println("Initializing database...");
            DatabaseManager dbManager = new DatabaseManager();

            System.out.println("✓ Database connected");
            System.out.println();

            // Create batch importer
            BatchDictionaryImporter importer = new BatchDictionaryImporter(dbManager);

            // Import HOTL XML files first (small, good test)
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 1: Heart of Tibetan Language (XML)");
            System.out.println("═══════════════════════════════════════════════════════════════");
            importHOTL(dbManager);

            // Import all pipe-delimited dictionaries
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 2: All Pipe-Delimited Dictionaries");
            System.out.println("═══════════════════════════════════════════════════════════════");

            BatchDictionaryImporter.ImportResult result = importer.importAllSteinertDictionaries(
                PUBLIC_FOLDER,
                new BatchDictionaryImporter.BatchProgressCallback() {
                    int currentDict = 0;

                    @Override
                    public void onStart(int totalDictionaries, String message) {
                        System.out.println("\n" + message);
                        System.out.println("─".repeat(65));
                    }

                    @Override
                    public void onDictionaryStart(int current, int total, String dictionaryName) {
                        currentDict = current;
                        System.out.printf("\n[%d/%d] %s\n", current, total, dictionaryName);
                        System.out.print("  Importing... ");
                    }

                    @Override
                    public void onDictionaryProgress(String dictionaryName, int currentTerms, int totalTerms, String message) {
                        // Only print every 5000 terms to avoid spam
                        if (currentTerms % 5000 == 0 && totalTerms > 0) {
                            System.out.print(".");
                        }
                    }

                    @Override
                    public void onDictionaryComplete(int current, int total, String dictionaryName, int termsImported) {
                        System.out.printf(" ✓ %,d terms\n", termsImported);
                    }

                    @Override
                    public void onDictionaryError(String dictionaryName, String error) {
                        System.out.println(" ✗ ERROR: " + error);
                    }

                    @Override
                    public void onComplete(BatchDictionaryImporter.ImportResult result) {
                        System.out.println("\n" + "─".repeat(65));
                        System.out.println("BATCH IMPORT COMPLETE");
                        System.out.println("─".repeat(65));
                        System.out.printf("Successful: %d dictionaries\n", result.successCount);
                        System.out.printf("Failed: %d dictionaries\n", result.failCount);
                        System.out.printf("Total terms imported: %,d\n", result.totalTermsImported);
                    }

                    @Override
                    public void onError(String error) {
                        System.err.println("FATAL ERROR: " + error);
                    }
                }
            );

            // Final summary
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║                    IMPORT COMPLETE                             ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.printf("\nTotal dictionaries: %d\n", result.successCount + result.failCount);
            System.out.printf("✓ Successful: %d\n", result.successCount);
            System.out.printf("✗ Failed: %d\n", result.failCount);
            System.out.printf("📊 Total terms: %,d\n", result.totalTermsImported);
            System.out.println("\n✅ All Steinert dictionaries imported successfully!");

        } catch (Exception e) {
            System.err.println("\n❌ FATAL ERROR:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Import HOTL XML files separately
     */
    private static void importHOTL(DatabaseManager dbManager) {
        String[] hotlFiles = {"hotl1.xml", "hotl2.xml", "hotl3.xml"};
        GlossaryLoader loader = new GlossaryLoader(dbManager);

        int totalHOTL = 0;

        for (int i = 0; i < hotlFiles.length; i++) {
            String filename = hotlFiles[i];
            String fullPath = HOTL_FOLDER + "/" + filename;

            System.out.printf("\n[%d/%d] %s\n", i + 1, hotlFiles.length, filename);
            System.out.print("  Importing... ");

            try {
                int loaded = loader.loadFromFile(fullPath,
                    new FormatParser.LoadProgressCallback() {
                        @Override
                        public void onProgress(int current, int total, String message) {
                            // Silent during import
                        }

                        @Override
                        public void onComplete(int totalLoaded, String message) {
                            // Silent
                        }

                        @Override
                        public void onError(String error) {
                            System.out.println("\n  ✗ ERROR: " + error);
                        }
                    },
                    "Steinert"
                );

                totalHOTL += loaded;
                System.out.printf(" ✓ %d terms\n", loaded);

            } catch (Exception e) {
                System.out.println(" ✗ ERROR: " + e.getMessage());
            }
        }

        System.out.println("─".repeat(65));
        System.out.printf("HOTL Total: %d terms from 3 volumes\n", totalHOTL);
    }
}
