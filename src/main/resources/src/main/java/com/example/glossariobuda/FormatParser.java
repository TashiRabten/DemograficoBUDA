package com.example.glossariobuda;

import java.io.File;
import java.util.List;

/**
 * Interface for parsing different glossary file formats (XML, CSV, JSON, etc.)
 */
public interface FormatParser {

    /**
     * Parse a glossary file and return a list of terms
     * @param file The file to parse
     * @param callback Progress callback (optional)
     * @return List of parsed glossary terms
     * @throws Exception if parsing fails
     */
    List<GlossaryTerm> parse(File file, LoadProgressCallback callback) throws Exception;

    /**
     * Check if this parser can handle the given file
     * @param file The file to check
     * @return true if this parser can handle the file
     */
    boolean canParse(File file);

    /**
     * Get the file extension this parser handles (e.g., "xml", "csv", "json")
     * @return file extension without the dot
     */
    String getFileExtension();

    /**
     * Get a description of this format for UI display
     * @return human-readable format description
     */
    String getFormatDescription();

    /**
     * Extract file-level metadata (owner, etc.) without fully parsing
     * Used for smart defaults in import dialog
     * @param file The file to inspect
     * @return Metadata object with suggested owner (can be null)
     */
    default ImportMetadata extractMetadata(File file) {
        return new ImportMetadata(null, file.getName());
    }

    /**
     * Metadata extracted from import file
     */
    class ImportMetadata {
        public String suggestedOwner;  // Owner extracted from file metadata
        public String fileName;         // Original filename for smart defaults

        public ImportMetadata(String suggestedOwner, String fileName) {
            this.suggestedOwner = suggestedOwner;
            this.fileName = fileName;
        }
    }

    /**
     * Interface for progress callbacks during file loading
     */
    interface LoadProgressCallback {
        void onProgress(int current, int total, String message);
        void onComplete(int totalLoaded, String message);
        void onError(String error);
    }
}
