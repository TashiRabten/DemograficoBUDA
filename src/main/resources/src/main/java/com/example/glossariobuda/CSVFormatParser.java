package com.example.glossariobuda;

import com.example.glossariobuda.exceptions.ImportException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parser for CSV glossary files
 * Supports flexible column mapping with header detection
 *
 * Expected CSV formats:
 * 1. Simple format: source_term,target_term
 * 2. With languages: source_term,source_language,target_term,target_language
 * 3. Full format: source_term,source_language,target_term,target_language,context,contributor,notes
 * 4. Extended format: tibetan,wylie,translation,sanskrit,definition,type,references,contributor
 */
public class CSVFormatParser implements FormatParser {

    @Override
    public List<GlossaryTerm> parse(File file, LoadProgressCallback callback) throws Exception {
        List<GlossaryTerm> terms = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            // Read header line (may span multiple physical lines if fields contain newlines)
            String headerLine = readCsvRecord(reader);
            if (headerLine == null) {
                throw new ImportException("Arquivo CSV vazio");
            }

            // Detect column mapping from header
            String[] headers = parseCsvLine(headerLine);
            ColumnMapping mapping = detectColumnMapping(headers);

            if (callback != null) {
                callback.onProgress(0, 0, "Formato CSV detectado: " + mapping.getDescription());
            }

            // Parse data rows
            int recordNumber = 0;
            String record;
            while ((record = readCsvRecord(reader)) != null) {
                recordNumber++;

                // Skip empty records
                if (record.trim().isEmpty()) {
                    continue;
                }

                try {
                    String[] values = parseCsvLine(record);
                    GlossaryTerm term = parseTermFromCsv(values, mapping);
                    if (term != null) {
                        terms.add(term);
                    }
                } catch (Exception e) {
                    System.err.println("Erro no registro " + recordNumber + ": " + e.getMessage());
                }

                // Report progress every 100 records
                if (callback != null && recordNumber % 100 == 0) {
                    callback.onProgress(recordNumber, -1, "Processados " + recordNumber + " registros");
                }
            }
        }

        return terms;
    }

    /**
     * Read a complete CSV record (may span multiple physical lines if fields contain newlines)
     * Handles quoted fields with embedded newlines correctly
     */
    private String readCsvRecord(BufferedReader reader) throws Exception {
        StringBuilder record = new StringBuilder();
        String line;
        boolean inQuotes = false;

        while ((line = reader.readLine()) != null) {
            // Add line to record
            if (record.length() > 0) {
                record.append('\n'); // Preserve newline that was inside quoted field
            }
            record.append(line);

            // Count quotes to determine if we're inside a quoted field
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    // Check if it's an escaped quote
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        i++; // Skip the second quote
                    } else {
                        inQuotes = !inQuotes;
                    }
                }
            }

            // If we're not inside quotes, the record is complete
            if (!inQuotes) {
                return record.toString();
            }
            // Otherwise, continue reading next line as part of this record
        }

        // End of file
        if (record.length() > 0) {
            return record.toString(); // Return last incomplete record
        }
        return null;
    }

    @Override
    public boolean canParse(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".txt");
    }

    @Override
    public String getFileExtension() {
        return "csv";
    }

    @Override
    public String getFormatDescription() {
        return "CSV (Comma-Separated Values)";
    }

    /**
     * Extract owner metadata from CSV comments or filename
     */
    @Override
    public ImportMetadata extractMetadata(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            // Check first 10 lines for comment-based metadata
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 10) {
                lineCount++;
                line = line.trim();

                // Check for comment lines with metadata
                if (line.startsWith("#") || line.startsWith("//")) {
                    // Remove comment marker
                    String content = line.substring(line.indexOf("#") >= 0 ? line.indexOf("#") + 1 : 2).trim();

                    // Look for owner metadata
                    if (content.toLowerCase().startsWith("owner:") ||
                        content.toLowerCase().startsWith("maintainer:")) {
                        String owner = content.substring(content.indexOf(":") + 1).trim();
                        if (!owner.isEmpty()) {
                            return new ImportMetadata(owner, file.getName());
                        }
                    }
                }
            }

            return new ImportMetadata(null, file.getName());
        } catch (Exception e) {
            System.err.println("Error extracting metadata from CSV: " + e.getMessage());
            return new ImportMetadata(null, file.getName());
        }
    }

    /**
     * Parse a CSV line handling quoted fields properly
     * Handles:
     * - Quoted fields: "value"
     * - Escaped quotes: "" becomes "
     * - Commas inside quotes: "O Buda, ser desperto"
     * - Removes outer quotes but preserves content
     */
    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // Check for escaped quote ("")
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Double quote - add single quote to field
                    currentField.append('"');
                    i++; // Skip next quote
                } else {
                    // Toggle quote state (but don't add quote to field)
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // Field separator - save current field
                result.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                // Regular character - add to field
                currentField.append(c);
            }
        }

        // Add last field
        result.add(currentField.toString().trim());

        return result.toArray(new String[0]);
    }

    /**
     * Detect column mapping from headers
     * Supports multilingual and flexible column names
     */
    private ColumnMapping detectColumnMapping(String[] headers) {
        ColumnMapping mapping = new ColumnMapping();

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim();

            // Remove common punctuation/formatting
            header = header.replace("_", " ").replace("-", " ");

            // Match common header names (multilingual support)
            // Target term (Tibetan, Chinese, Pali, etc.)
            if (matches(header, "tibetan", "tibetano", "target term", "termo alvo", "chinese", "chines",
                    "pali", "thai", "burmese", "mongolian")) {
                mapping.tibetanIndex = i;
            }
            // Wylie/Romanization
            else if (matches(header, "wylie", "romanization", "romanizado", "romanisation", "pinyin")) {
                mapping.wylieIndex = i;
            }
            // Source term (usually English, Sanskrit, etc.)
            else if (matches(header, "translation", "english", "ingles", "source term", "termo origem",
                    "term", "word", "palavra")) {
                mapping.translationIndex = i;
            }
            // Sanskrit/Pali (alternative romanization)
            else if (matches(header, "sanskrit", "sanscrito", "skt", "pali")) {
                mapping.sanskritIndex = i;
            }
            // Definition/Context
            else if (matches(header, "definition", "definicao", "context", "contexto", "meaning",
                    "significado", "description", "descricao")) {
                mapping.definitionIndex = i;
            }
            // Type/Category
            else if (matches(header, "type", "tipo", "category", "categoria")) {
                mapping.typeIndex = i;
            }
            // References
            else if (matches(header, "references", "referencias", "refs", "ref", "source", "fonte")) {
                mapping.referencesIndex = i;
            }
            // Contributor/Author
            else if (matches(header, "contributor", "contribuidor", "autor", "author", "translator", "tradutor")) {
                mapping.contributorIndex = i;
            }
            // Source language
            else if (matches(header, "source language", "idioma origem", "from language", "from lang")) {
                mapping.sourceLanguageIndex = i;
            }
            // Target language
            else if (matches(header, "target language", "idioma alvo", "to language", "to lang")) {
                mapping.targetLanguageIndex = i;
            }
            // Notes
            else if (matches(header, "notes", "notas", "note", "nota", "comments", "comentarios", "remarks")) {
                mapping.notesIndex = i;
            }
        }

        // If no explicit mapping found, try positional mapping
        if (mapping.isEmpty()) {
            if (headers.length >= 2) {
                mapping.translationIndex = 0;
                mapping.tibetanIndex = 1;
            }
            if (headers.length >= 4) {
                mapping.sourceLanguageIndex = 1;
                mapping.targetLanguageIndex = 3;
            }
            if (headers.length >= 5) {
                mapping.definitionIndex = 4;
            }
            if (headers.length >= 6) {
                mapping.contributorIndex = 5;
            }
            if (headers.length >= 7) {
                mapping.notesIndex = 6;
            }
        }

        return mapping;
    }

    /**
     * Check if header matches any of the given patterns
     */
    private boolean matches(String header, String... patterns) {
        for (String pattern : patterns) {
            if (header.equals(pattern) || header.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse a term from CSV values using the column mapping
     */
    /**
     * Helper method to extract and set a field from CSV values.
     * Reduces NPath complexity by encapsulating the repeated pattern of:
     * - Check index validity
     * - Extract value
     * - Check if value is non-empty
     * - Set value
     */
    private void setFieldFromCsv(String[] values, int index, Consumer<String> setter) {
        if (index >= 0 && index < values.length) {
            String value = values[index];
            if (value != null && !value.isEmpty()) {
                setter.accept(value);
            }
        }
    }

    private GlossaryTerm parseTermFromCsv(String[] values, ColumnMapping mapping) {
        GlossaryTerm term = new GlossaryTerm();

        // Extract simple fields using helper method
        setFieldFromCsv(values, mapping.translationIndex, term::setSourceTerm);
        setFieldFromCsv(values, mapping.tibetanIndex, term::setTargetTerm);
        setFieldFromCsv(values, mapping.wylieIndex, term::setWylie);
        setFieldFromCsv(values, mapping.sanskritIndex, term::setSanskrit);
        setFieldFromCsv(values, mapping.definitionIndex, term::setContext);
        setFieldFromCsv(values, mapping.typeIndex, term::setType);
        setFieldFromCsv(values, mapping.contributorIndex, term::setContributor);
        setFieldFromCsv(values, mapping.notesIndex, term::setNotes);

        // Extract references (special handling for splitting)
        extractReferences(values, mapping.referencesIndex, term);

        // Extract language fields with auto-detection fallback
        extractSourceLanguage(values, mapping, term);
        extractTargetLanguage(values, mapping, term);

        // Only return term if we have at least a source or target
        return hasValidTermData(term) ? term : null;
    }

    private void extractReferences(String[] values, int index, GlossaryTerm term) {
        if (index >= 0 && index < values.length) {
            String refs = values[index];
            if (refs != null && !refs.isEmpty()) {
                String[] refArray = refs.split("[;\\n]");
                List<String> refList = new ArrayList<>();
                for (String ref : refArray) {
                    String trimmed = ref.trim();
                    if (!trimmed.isEmpty()) {
                        refList.add(trimmed);
                    }
                }
                if (!refList.isEmpty()) {
                    term.setReferences(refList);
                }
            }
        }
    }

    private void extractSourceLanguage(String[] values, ColumnMapping mapping, GlossaryTerm term) {
        if (mapping.sourceLanguageIndex >= 0 && mapping.sourceLanguageIndex < values.length) {
            String sourceLang = values[mapping.sourceLanguageIndex];
            term.setSourceLanguage(sourceLang != null && !sourceLang.isEmpty() ? sourceLang : null);
        } else {
            autoDetectSourceLanguage(term);
        }
    }

    private void autoDetectSourceLanguage(GlossaryTerm term) {
        String sourceTerm = term.getSourceTerm();
        if (sourceTerm != null && !sourceTerm.isEmpty()) {
            String detected = LanguageDetector.detectLanguage(sourceTerm);
            if (!detected.isEmpty()) {
                term.setSourceLanguage(detected);
            }
        }
    }

    private void extractTargetLanguage(String[] values, ColumnMapping mapping, GlossaryTerm term) {
        if (mapping.targetLanguageIndex >= 0 && mapping.targetLanguageIndex < values.length) {
            String targetLang = values[mapping.targetLanguageIndex];
            term.setTargetLanguage(targetLang != null && !targetLang.isEmpty() ? targetLang : null);
        } else {
            autoDetectTargetLanguage(term);
        }
    }

    private void autoDetectTargetLanguage(GlossaryTerm term) {
        String targetTerm = term.getTargetTerm();
        if (targetTerm != null && !targetTerm.isEmpty()) {
            String detected = LanguageDetector.detectLanguage(targetTerm);
            if (!detected.isEmpty()) {
                term.setTargetLanguage(detected);
            }
        }
    }

    private boolean hasValidTermData(GlossaryTerm term) {
        return (term.getSourceTerm() != null && !term.getSourceTerm().isEmpty()) ||
               (term.getTargetTerm() != null && !term.getTargetTerm().isEmpty());
    }

    /**
     * Column mapping configuration
     */
    private static class ColumnMapping {
        int tibetanIndex = -1;
        int wylieIndex = -1;
        int translationIndex = -1;
        int sanskritIndex = -1;
        int definitionIndex = -1;
        int typeIndex = -1;
        int referencesIndex = -1;
        int contributorIndex = -1;
        int sourceLanguageIndex = -1;
        int targetLanguageIndex = -1;
        int notesIndex = -1;

        boolean isEmpty() {
            return tibetanIndex == -1 && translationIndex == -1;
        }

        String getDescription() {
            if (tibetanIndex >= 0 && translationIndex >= 0) {
                return "Glossário com termos bilíngues";
            }
            return "Formato CSV genérico";
        }
    }
}