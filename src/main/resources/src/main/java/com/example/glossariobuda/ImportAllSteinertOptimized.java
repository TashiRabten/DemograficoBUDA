package com.example.glossariobuda;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * OPTIMIZED batch import for all Steinert dictionaries
 *
 * Key optimization: Import all dictionaries to LOCAL database first,
 * then sync everything to cloud in ONE batch at the end.
 *
 * This avoids 62 separate cloud uploads and hundreds of print messages.
 *
 * Usage: mvn exec:java -Dexec.mainClass="com.example.glossariobuda.ImportAllSteinertOptimized"
 */
public class ImportAllSteinertOptimized {

    private static final String PUBLIC_FOLDER = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/public";
    private static final String HOTL_FOLDER = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/conversion/Heart_of_Tibetan_Language";

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     STEINERT DICTIONARY OPTIMIZED BATCH IMPORT                 ║");
        System.out.println("║     Import locally → Sync to cloud ONCE at end                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Initialize database
            System.out.println("Initializing database...");
            DatabaseManager dbManager = new DatabaseManager();
            System.out.println("✓ Database connected");
            System.out.println();

            // CRITICAL: Disable cloud sync during import
            System.out.println("⚠️  Disabling cloud sync during import (will sync at end)");
            SyncManager syncManager = dbManager.getSyncManager();
            boolean wasOnline = syncManager.isOnline();

            // Disable individual term syncing during import
            syncManager.setSyncEnabled(false);
            System.out.println("   Cloud sync disabled - terms will be synced in batch at end");
            System.out.println();

            List<SupabaseClient.TermDTO> allImportedDTOs = new ArrayList<>();
            int totalTerms = 0;
            int totalDictionaries = 0;

            // PHASE 1: Import HOTL XML files
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 1: Heart of Tibetan Language (XML) - LOCAL IMPORT");
            System.out.println("═══════════════════════════════════════════════════════════════");

            ImportResult hotlResult = importHOTLLocal(dbManager);
            totalTerms += hotlResult.termsImported;
            totalDictionaries += hotlResult.dictionariesImported;
            allImportedDTOs.addAll(hotlResult.dtosImported);

            // PHASE 2: Import pipe-delimited dictionaries
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 2: Pipe-Delimited Dictionaries - LOCAL IMPORT");
            System.out.println("═══════════════════════════════════════════════════════════════");

            ImportResult pipeResult = importPipeDelimitedLocal(dbManager);
            totalTerms += pipeResult.termsImported;
            totalDictionaries += pipeResult.dictionariesImported;
            allImportedDTOs.addAll(pipeResult.dtosImported);

            // PHASE 3: Sync everything to cloud in ONE batch
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 3: SYNCING TO CLOUD");
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.printf("Total terms to sync: %,d\n", allImportedDTOs.size());
            System.out.printf("Total dictionaries: %d\n", totalDictionaries);
            System.out.println();

            if (wasOnline && !allImportedDTOs.isEmpty()) {
                syncToCloudInBatches(syncManager, allImportedDTOs);
            } else if (!wasOnline) {
                System.out.println("⚠️  Offline mode - terms saved locally");
                System.out.println("   They will sync automatically when you go online");
            }

            // Re-enable sync for normal operations
            syncManager.setSyncEnabled(true);

            // Final summary
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║                    IMPORT COMPLETE                             ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.printf("\n✅ Successfully imported %d dictionaries\n", totalDictionaries);
            System.out.printf("📊 Total terms: %,d\n", totalTerms);
            System.out.println("\nAll Steinert dictionaries are now available!");

        } catch (Exception e) {
            System.err.println("\n❌ FATAL ERROR:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Import HOTL files locally (no cloud sync)
     */
    private static ImportResult importHOTLLocal(DatabaseManager dbManager) {
        String[] hotlFiles = {"hotl1.xml", "hotl2.xml", "hotl3.xml"};
        ImportResult result = new ImportResult();

        for (int i = 0; i < hotlFiles.length; i++) {
            String filename = hotlFiles[i];
            String fullPath = HOTL_FOLDER + "/" + filename;

            System.out.printf("\n[%d/%d] %s", i + 1, hotlFiles.length, filename);
            System.out.print(" → ");

            try {
                // Parse file and insert to local DB only
                SteinertXMLParser parser = new SteinertXMLParser();
                List<GlossaryTerm> terms = parser.parse(new File(fullPath), null);

                int inserted = 0;
                for (GlossaryTerm term : terms) {
                    term.setOwner("Steinert");

                    // Convert to DTO for later cloud sync
                    SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
                    dto.source_term = term.getSourceTerm();
                    dto.source_language = term.getSourceLanguage();
                    dto.target_term = term.getTargetTerm();
                    dto.target_language = term.getTargetLanguage();
                    dto.context = term.getContext();
                    dto.contributor = term.getContributor();
                    dto.notes = term.getNotes();
                    dto.owner = "Steinert";
                    dto.verified_status = "draft";
                    dto.content_hash = dbManager.getSyncManager().generateHash(
                        dto.source_term, dto.source_language, dto.target_term,
                        dto.target_language, dto.context, dto.contributor);
                    dto.date_added = java.time.Instant.now().toString();

                    // Insert to local DB
                    AddTermParams params = new AddTermParams(
                        dto.source_term, dto.source_language, dto.target_term,
                        dto.target_language, dto.context, dto.contributor,
                        dto.notes, dto.owner
                    );

                    if (dbManager.addTerm(params)) {
                        result.dtosImported.add(normalizeDTO(dto));
                        inserted++;
                    }
                }

                result.termsImported += inserted;
                result.dictionariesImported++;
                System.out.printf("✓ %d terms\n", inserted);

            } catch (Exception e) {
                System.out.println("✗ ERROR: " + e.getMessage());
            }
        }

        System.out.println("─".repeat(65));
        System.out.printf("HOTL Total: %,d terms\n", result.termsImported);
        return result;
    }

    /**
     * Import pipe-delimited files locally (no cloud sync)
     */
    private static ImportResult importPipeDelimitedLocal(DatabaseManager dbManager) {
        ImportResult result = new ImportResult();
        File publicFolder = new File(PUBLIC_FOLDER);

        if (!publicFolder.exists()) {
            System.err.println("ERROR: Public folder not found");
            return result;
        }

        File[] files = publicFolder.listFiles();
        if (files == null) {
            return result;
        }

        // Filter and count
        List<File> dictFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && !file.getName().contains("84000") &&
                !file.getName().startsWith(".")) {
                dictFiles.add(file);
            }
        }

        dictFiles.sort((a, b) -> a.getName().compareTo(b.getName()));
        int total = dictFiles.size();

        TibetanDictParser parser = new TibetanDictParser();

        for (int i = 0; i < dictFiles.size(); i++) {
            File dictFile = dictFiles.get(i);
            String name = dictFile.getName();

            System.out.printf("\r[%d/%d] %-40s", i + 1, total,
                name.length() > 40 ? name.substring(0, 37) + "..." : name);

            try {
                if (!parser.canParse(dictFile)) {
                    System.out.println(" ✗ SKIP");
                    continue;
                }

                List<GlossaryTerm> terms = parser.parse(dictFile, null);
                int inserted = 0;

                for (GlossaryTerm term : terms) {
                    term.setOwner("Steinert");

                    // Convert to DTO
                    SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
                    dto.source_term = term.getSourceTerm();
                    dto.source_language = term.getSourceLanguage();
                    dto.target_term = term.getTargetTerm();
                    dto.target_language = term.getTargetLanguage();
                    dto.context = term.getContext();
                    dto.contributor = term.getContributor();
                    dto.notes = term.getNotes();
                    dto.owner = "Steinert";
                    dto.verified_status = "draft";
                    dto.content_hash = dbManager.getSyncManager().generateHash(
                        dto.source_term, dto.source_language, dto.target_term,
                        dto.target_language, dto.context, dto.contributor);
                    dto.date_added = java.time.Instant.now().toString();

                    // Insert to local DB
                    AddTermParams params = new AddTermParams(
                        dto.source_term, dto.source_language, dto.target_term,
                        dto.target_language, dto.context, dto.contributor,
                        dto.notes, dto.owner
                    );

                    if (dbManager.addTerm(params)) {
                        result.dtosImported.add(normalizeDTO(dto));
                        inserted++;
                    }
                }

                result.termsImported += inserted;
                result.dictionariesImported++;
                System.out.printf(" ✓ %,d\n", inserted);

            } catch (Exception e) {
                System.out.println(" ✗ ERROR");
            }
        }

        System.out.println("─".repeat(65));
        System.out.printf("Pipe-delimited Total: %,d terms\n", result.termsImported);
        return result;
    }

    /**
     * Sync all terms to cloud in batches (500 at a time to avoid timeouts)
     */
    private static void syncToCloudInBatches(SyncManager syncManager,
                                             List<SupabaseClient.TermDTO> allDTOs) {
        System.out.println("Starting cloud sync...");

        int batchSize = 500;  // Sync 500 terms at a time (prevents Supabase timeout)
        int totalSynced = 0;
        int totalBatches = (allDTOs.size() + batchSize - 1) / batchSize;

        for (int i = 0; i < allDTOs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allDTOs.size());
            List<SupabaseClient.TermDTO> batch = allDTOs.subList(i, end);
            int batchNum = (i / batchSize) + 1;

            System.out.printf("[Batch %d/%d] Syncing %,d terms... ",
                batchNum, totalBatches, batch.size());

            try {
                SupabaseClient supabaseClient = syncManager.getSupabaseClient();
                int synced = supabaseClient.batchInsertTerms(batch);
                totalSynced += synced;
                System.out.println("✓");
            } catch (Exception e) {
                System.out.println("✗ ERROR: " + e.getMessage());
            }
        }

        System.out.println("─".repeat(65));
        System.out.printf("Cloud sync complete: %,d terms uploaded\n", totalSynced);
    }

    /**
     * Normalize DTO fields to ensure consistency for batch operations.
     * Converts null to empty string to ensure all DTOs have same field keys.
     */
    private static SupabaseClient.TermDTO normalizeDTO(SupabaseClient.TermDTO dto) {
        if (dto.source_term == null) dto.source_term = "";
        if (dto.source_language == null) dto.source_language = "";
        if (dto.target_term == null) dto.target_term = "";
        if (dto.target_language == null) dto.target_language = "";
        if (dto.context == null) dto.context = "";
        if (dto.contributor == null) dto.contributor = "";
        if (dto.notes == null) dto.notes = "";
        if (dto.owner == null) dto.owner = "";
        if (dto.verified_status == null) dto.verified_status = "draft";
        if (dto.content_hash == null) dto.content_hash = "";
        if (dto.date_added == null) dto.date_added = java.time.Instant.now().toString();
        return dto;
    }

    static class ImportResult {
        int termsImported = 0;
        int dictionariesImported = 0;
        List<SupabaseClient.TermDTO> dtosImported = new ArrayList<>();
    }
}
