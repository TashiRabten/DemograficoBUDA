package com.example.glossariobuda;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal glossary loader that supports multiple file formats
 * Automatically detects the format and uses the appropriate parser
 */
public class GlossaryLoader {

    private DatabaseManager dbManager;
    private List<FormatParser> parsers;

    public GlossaryLoader(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.parsers = new ArrayList<>();
        registerDefaultParsers();
    }

    /**
     * Register default parsers for standard formats
     */
    private void registerDefaultParsers() {
        parsers.addAll(getDefaultParsers());
    }

    /**
     * Get default parser list in correct priority order.
     * This ensures ALL import operations (regular import, admin reset, batch import)
     * use the SAME parser chain with ALL the latest fixes.
     *
     * ORDER MATTERS:
     * - SteinertXMLParser before XMLFormatParser (more specific XML format)
     * - TibetanDictParser for pipe-delimited Steinert dictionaries
     *
     * @return List of parsers in priority order
     */
    public static List<FormatParser> getDefaultParsers() {
        List<FormatParser> parsers = new ArrayList<>();
        parsers.add(new SteinertXMLParser());  // Steinert HOTL must be first (more specific XML)
        parsers.add(new TibetanDictParser());  // Steinert pipe-delimited dictionaries
        parsers.add(new MW72Parser());         // MW72 specific XML format
        parsers.add(new XMLFormatParser());    // Generic XML
        parsers.add(new CSVFormatParser());    // CSV/TSV
        parsers.add(new JSONFormatParser());   // JSON
        return parsers;
    }

    /**
     * Register a custom parser
     */
    public void registerParser(FormatParser parser) {
        parsers.add(parser);
    }
    /**
     * Load glossary from file, auto-detecting the format
     * @param filePath Path to the glossary file
     * @param callback Progress callback (can be null)
     * @param currentUser Current user name for owner selection
     * @return Number of terms successfully loaded
     */
    public int loadFromFile(String filePath, FormatParser.LoadProgressCallback callback, String currentUser) {
        File file = new File(filePath);

        if (!file.exists()) {
            if (callback != null) {
                callback.onError("Arquivo não encontrado: " + filePath);
            }
            return 0;
        }

        // Find appropriate parser
        FormatParser parser = findParser(file);
        if (parser == null) {
            if (callback != null) {
                callback.onError("Formato de arquivo não suportado: " + getFileExtension(file));
            }
            return 0;
        }

        if (callback != null) {
            callback.onProgress(0, 0, "Carregando arquivo " + parser.getFormatDescription() + "...");
        }

        try {
            // STEP 1: Extract file metadata for smart defaults
            FormatParser.ImportMetadata metadata = parser.extractMetadata(file);

            // STEP 2: Do a quick parse to count terms for dialog
            List<GlossaryTerm> terms = parser.parse(file, null);  // Parse without progress callback first
            int termCount = terms.size();

            // STEP 3: Show owner selection dialog
            String selectedOwner = ImportOwnerDialog.show(
                file.getName(),
                metadata.suggestedOwner,
                currentUser != null && !currentUser.isEmpty() ? currentUser : "user",
                termCount
            );

            // User cancelled the import
            if (selectedOwner == null) {
                if (callback != null) {
                    callback.onError("Importação cancelada pelo usuário");
                }
                return 0;
            }

            // STEP 4: Apply owner to all terms
            for (GlossaryTerm term : terms) {
                term.setOwner(selectedOwner);
            }

            if (callback != null) {
                callback.onProgress(0, termCount, "Encontrados " + termCount + " termos (Owner: " + selectedOwner + ")");
            }

            // Pause realtime during bulk import to avoid processing 55k+ notifications
            RealtimeSyncService.pauseRealtime();

            // Add terms to database using batch insertion
            int loadedCount = 0;
            int skippedCount = 0;
            int duplicatesSkipped = 0; // NEW: Initialize duplicate counter
            int totalTerms = terms.size();

            // Track all successfully imported DTOs for cloud sync
            List<SupabaseClient.TermDTO> allImportedDTOs = new ArrayList<>();

            List<SupabaseClient.TermDTO> batch = new ArrayList<>();
            for (int i = 0; i < terms.size(); i++) {
                GlossaryTerm term = terms.get(i);

                if (isValidTerm(term)) {
                    SupabaseClient.TermDTO dto = convertTermToDTO(term);
                    batch.add(dto);
                } else {
                    skippedCount++;
                }

                // Insert batch every 500 terms
                if (batch.size() >= 500) {
                    int batchSize = batch.size(); // Capture current batch size
                    List<SupabaseClient.TermDTO> insertedDTOs = batchInsertTerms(batch);
                    int inserted = insertedDTOs.size();
                    loadedCount += inserted;

                    // Track successfully inserted DTOs for cloud sync (exact ones that were inserted)
                    allImportedDTOs.addAll(insertedDTOs);

                    // CORE CHANGE: Calculate duplicates. Total processed in batch - actual inserts
                    duplicatesSkipped += (batchSize - inserted);

                    batch.clear();

                    if (callback != null) {
                        callback.onProgress(i + 1, totalTerms,
                                String.format("Processados %d de %d termos (carregados: %d, ignorados: %d, duplicados: %d)", // Updated format string
                                        i + 1, totalTerms, loadedCount, skippedCount, duplicatesSkipped)); // Passed duplicatesSkipped
                    }
                }
            }

            // Insert remaining terms
            if (!batch.isEmpty()) {
                int batchSize = batch.size(); // Capture remaining batch size
                List<SupabaseClient.TermDTO> insertedDTOs = batchInsertTerms(batch);
                int inserted = insertedDTOs.size();
                loadedCount += inserted;

                // Track successfully inserted DTOs for cloud sync (exact ones that were inserted)
                allImportedDTOs.addAll(insertedDTOs);

                // CORE CHANGE: Calculate remaining duplicates
                duplicatesSkipped += (batchSize - inserted);
            }

            if (callback != null) {
                // Updated final message to use the new counter
                callback.onComplete(loadedCount,
                        String.format("Importação completa! %d termos carregados, %d ignorados, %d duplicados.",
                                loadedCount, skippedCount, duplicatesSkipped));
            }

            // Sync imported terms to cloud (only the ones we just imported)
            if (loadedCount > 0 && !allImportedDTOs.isEmpty()) {
                syncImportedTermsToCloud(allImportedDTOs, loadedCount);
            }

            // Resume realtime after import completes
            RealtimeSyncService.resumeRealtime();

            return loadedCount;

        } catch (Exception e) {
            // Resume realtime even if import fails
            RealtimeSyncService.resumeRealtime();

            if (callback != null) {
                callback.onError("Erro ao processar arquivo: " + e.getMessage());
            }
            e.printStackTrace();
            return 0;
        }
    }

    private SupabaseClient.TermDTO convertTermToDTO(GlossaryTerm term) {
        // Extract source and target terms with language detection
        TermMapping mapping = extractSourceAndTarget(term);

        // Build context and notes
        String context = buildContext(term);
        String notes = buildNotes(term, mapping.sourceLanguage);
        String contributor = term.getContributor() != null ? term.getContributor() : "84000 Glossary";

        // Create and populate DTO
        SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
        dto.source_term = mapping.sourceTerm;
        dto.source_language = mapping.sourceLanguage;
        dto.target_term = mapping.targetTerm;
        dto.target_language = mapping.targetLanguage;
        dto.context = context;
        dto.contributor = contributor;
        dto.notes = notes;
        dto.verified_status = "draft";
        dto.content_hash = dbManager.getSyncManager().generateHash(
                mapping.sourceTerm, mapping.sourceLanguage, mapping.targetTerm,
                mapping.targetLanguage, context, contributor);
        dto.date_added = java.time.Instant.now().toString();
        dto.owner = term.getOwner();

        return dto;
    }

    private TermMapping extractSourceAndTarget(GlossaryTerm term) {
        TermMapping mapping = new TermMapping();

        if (term.getSourceTerm() != null && !term.getSourceTerm().isEmpty()) {
            mapping.sourceTerm = term.getSourceTerm();
            mapping.sourceLanguage = term.getSourceLanguage() != null ? term.getSourceLanguage() : "English";
            mapping.targetTerm = term.getTargetTerm();
            mapping.targetLanguage = term.getTargetLanguage() != null ? term.getTargetLanguage() : "Tibetan";
        } else {
            mapping.sourceTerm = determineSourceTerm(term);
            mapping.sourceLanguage = determineSanskritOrEnglish(term);
            mapping.targetTerm = term.getTibetan();
            mapping.targetLanguage = "Tibetan";
        }

        return mapping;
    }

    private String determineSourceTerm(GlossaryTerm term) {
        return (term.getSanskrit() != null && !term.getSanskrit().isEmpty())
                ? term.getSanskrit()
                : term.getTranslation();
    }

    private String determineSanskritOrEnglish(GlossaryTerm term) {
        return (term.getSanskrit() != null && !term.getSanskrit().isEmpty())
                ? "Sanskrit"
                : "English";
    }

    private String buildContext(GlossaryTerm term) {
        StringBuilder context = new StringBuilder();

        String definition = term.getContext() != null ? term.getContext() : term.getDefinition();
        if (definition != null && !definition.isEmpty()) {
            context.append(definition);
        }

        appendFilteredReferences(context, term.getReferences());

        return context.toString();
    }

    private void appendFilteredReferences(StringBuilder context, List<String> references) {
        if (references == null || references.isEmpty()) {
            return;
        }

        List<String> filteredRefs = filterReferences(references);
        if (filteredRefs.isEmpty()) {
            return;
        }

        if (context.length() > 0) {
            context.append("\n\n");
        }

        context.append("Referências:\n");
        for (String ref : filteredRefs) {
            context.append("• ").append(ref).append("\n");
        }
    }

    private List<String> filterReferences(List<String> references) {
        List<String> filteredRefs = new ArrayList<>();
        for (String ref : references) {
            if (ref == null) continue;

            String trimmed = ref.trim();
            if (trimmed.isEmpty() || isGenericLanguageReference(trimmed)) {
                continue;
            }

            filteredRefs.add(trimmed);
        }
        return filteredRefs;
    }

    private boolean isGenericLanguageReference(String ref) {
        String lower = ref.toLowerCase();
        return lower.equals("chinese") || lower.equals("chinês")
                || lower.equals("tibetano") || lower.equals("tibetan")
                || lower.equals("inglês") || lower.equals("english")
                || lower.equals("sânscrito") || lower.equals("sanskrit");
    }

    private String buildNotes(GlossaryTerm term, String sourceLanguage) {
        StringBuilder notes = new StringBuilder();

        appendIfNotEmpty(notes, "Wylie: ", term.getWylie());

        if (term.getSanskrit() != null && !term.getSanskrit().isEmpty()
                && !sourceLanguage.equals("Sanskrit")) {
            appendIfNotEmpty(notes, "Sanskrit: ", term.getSanskrit());
        }

        appendIfNotEmpty(notes, "Type: ", term.getType());

        if (term.getNotes() != null && !term.getNotes().isEmpty()) {
            if (notes.length() > 0) {
                notes.append("\n");
            }
            notes.append(term.getNotes());
        }

        return notes.toString();
    }

    private void appendIfNotEmpty(StringBuilder sb, String prefix, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(prefix).append(value).append("\n");
        }
    }

    /**
     * Helper class to hold term mapping result
     */
    private static class TermMapping {
        String sourceTerm;
        String sourceLanguage;
        String targetTerm;
        String targetLanguage;
    }
    /**
     * Batch insert terms into database using SyncManager's batch method
     * Returns list of successfully inserted DTOs
     */
    private List<SupabaseClient.TermDTO> batchInsertTerms(List<SupabaseClient.TermDTO> batch) {
        if (batch.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // Use SyncManager's batch insert method
            return dbManager.getSyncManager().batchInsertTermsForReset(batch);
        } catch (SQLException e) {
            System.err.println("[GlossaryLoader] Batch insert failed: " + e.getMessage());

            // Try inserting one by one as fallback
            System.out.println("[GlossaryLoader] Attempting one-by-one insertion as fallback...");
            List<SupabaseClient.TermDTO> inserted = new ArrayList<>();
            for (SupabaseClient.TermDTO term : batch) {
                try {
                    AddTermParams params = new AddTermParams(
                            term.source_term,
                            term.source_language,
                            term.target_term,
                            term.target_language,
                            term.context,
                            term.contributor,
                            term.notes,
                            term.owner
                    );
                    boolean success = dbManager.addTerm(params);
                    if (success) inserted.add(term);
                } catch (Exception singleError) {
                    System.err.println("[GlossaryLoader] Failed to insert: " + term.source_term);
                }
            }
            System.out.println("[GlossaryLoader] Fallback completed: " + inserted.size() + "/" + batch.size() + " terms inserted");
            return inserted;
        } catch (Exception e) {
            System.err.println("[GlossaryLoader] Unexpected error during batch insert: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Find the appropriate parser for the file
     */
    private FormatParser findParser(File file) {
        for (FormatParser parser : parsers) {
            if (parser.canParse(file)) {
                return parser;
            }
        }
        return null;
    }

    /**
     * Get file extension
     */
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }

    private boolean isValidTerm(GlossaryTerm term) {
        // Must have at least source and target terms
        String source = term.getSourceTerm() != null ? term.getSourceTerm() : term.getTranslation();
        return source != null && !source.isEmpty();
    }

    /**
     * Get list of supported formats
     */
    public List<String> getSupportedFormats() {
        List<String> formats = new ArrayList<>();
        for (FormatParser parser : parsers) {
            formats.add(parser.getFormatDescription());
        }
        return formats;
    }

    /**
     * Get file extension filters for file chooser
     */
    public List<String> getFileExtensions() {
        List<String> extensions = new ArrayList<>();
        for (FormatParser parser : parsers) {
            extensions.add("*." + parser.getFileExtension());
        }
        return extensions;
    }

    /**
     * Sync imported terms to cloud in background
     * Only syncs the specific terms that were just imported, not all database terms
     * If offline, queues them for later sync
     */
    private void syncImportedTermsToCloud(List<SupabaseClient.TermDTO> importedDTOs, int termCount) {
        Thread syncThread = new Thread(() -> {
            try {
                SyncManager syncManager = dbManager.getSyncManager();
                boolean isOnline = syncManager.isOnline();

                if (isOnline) {
                    // ONLINE: Sync immediately to cloud
                    System.out.println("[GlossaryLoader] Syncing " + importedDTOs.size() + " imported terms to cloud...");

                    SupabaseClient supabaseClient = syncManager.getSupabaseClient();
                    int synced = supabaseClient.batchInsertTerms(importedDTOs);

                    System.out.println("[GlossaryLoader] Successfully synced " + synced + " imported terms to cloud");
                } else {
                    // OFFLINE: Queue for later sync
                    System.out.println("[GlossaryLoader] Offline - queueing " + importedDTOs.size() + " imported terms for later sync");

                    OfflineQueueManager queue = syncManager.getOfflineQueue();
                    int queued = 0;

                    for (SupabaseClient.TermDTO dto : importedDTOs) {
                        QueueOperationParams params = new QueueOperationParams(
                            "ADD",
                            0, // No local ID yet for imported terms
                            dto.source_term,
                            dto.source_language,
                            dto.target_term,
                            dto.target_language,
                            dto.context != null ? dto.context : "",
                            dto.contributor != null ? dto.contributor : "",
                            dto.notes != null ? dto.notes : "",
                            dto.verified_status != null ? dto.verified_status : "draft",
                            dto.owner != null ? dto.owner : "shared",
                            dto.content_hash != null ? dto.content_hash : ""
                        );
                        queue.addOperation(params);
                        queued++;
                    }

                    System.out.println("[GlossaryLoader] Queued " + queued + " imported terms. Will sync when online.");
                }

            } catch (Exception e) {
                System.err.println("[GlossaryLoader] Error syncing to cloud: " + e.getMessage());
                e.printStackTrace();
                // Don't fail the import - terms are stored locally
            }
        });
        syncThread.setName("ImportCloudSync");
        syncThread.setDaemon(true);
        syncThread.start();
    }
}