package com.example.glossariobuda;

import java.io.File;
import java.util.*;

/**
 * Utility to batch import all Steinert dictionaries
 *
 * Usage:
 * BatchDictionaryImporter importer = new BatchDictionaryImporter(dbManager);
 * importer.importAllSteinertDictionaries("/path/to/public/folder", callback);
 */
public class BatchDictionaryImporter {

    private DatabaseManager dbManager;
    private GlossaryLoader loader;

    public BatchDictionaryImporter(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.loader = new GlossaryLoader(dbManager);
    }

    /**
     * Import all dictionaries from Steinert's public folder
     * Excludes 84000 dictionaries as requested
     */
    public ImportResult importAllSteinertDictionaries(String publicFolderPath,
                                                      BatchProgressCallback callback) {
        File publicFolder = new File(publicFolderPath);

        if (!publicFolder.exists() || !publicFolder.isDirectory()) {
            if (callback != null) {
                callback.onError("Pasta não encontrada: " + publicFolderPath);
            }
            return new ImportResult(0, 0, 0);
        }

        // Get all dictionary files, excluding 84000
        File[] allFiles = publicFolder.listFiles();
        if (allFiles == null) {
            if (callback != null) {
                callback.onError("Não foi possível ler a pasta");
            }
            return new ImportResult(0, 0, 0);
        }

        List<File> dictionaryFiles = new ArrayList<>();
        for (File file : allFiles) {
            if (file.isFile() && !file.getName().contains("84000")) {
                dictionaryFiles.add(file);
            }
        }

        // Sort by size (import smaller ones first for faster feedback)
        dictionaryFiles.sort(Comparator.comparingLong(File::length));

        int totalDictionaries = dictionaryFiles.size();
        int successCount = 0;
        int failCount = 0;
        int totalTermsImported = 0;

        if (callback != null) {
            callback.onStart(totalDictionaries, "Iniciando importação de " + totalDictionaries + " dicionários...");
        }

        // Import each dictionary
        for (int i = 0; i < dictionaryFiles.size(); i++) {
            File dictFile = dictionaryFiles.get(i);
            String dictName = dictFile.getName();

            if (callback != null) {
                callback.onDictionaryStart(i + 1, totalDictionaries, dictName);
            }

            try {
                int termsLoaded = loader.loadFromFile(
                    dictFile.getAbsolutePath(),
                    new FormatParser.LoadProgressCallback() {
                        @Override
                        public void onProgress(int current, int total, String message) {
                            if (callback != null) {
                                callback.onDictionaryProgress(dictName, current, total, message);
                            }
                        }

                        @Override
                        public void onComplete(int totalLoaded, String message) {
                            // Handled in outer scope
                        }

                        @Override
                        public void onError(String error) {
                            if (callback != null) {
                                callback.onDictionaryError(dictName, error);
                            }
                        }
                    },
                    "Steinert"  // Owner for all Steinert dictionaries
                );

                if (termsLoaded > 0) {
                    successCount++;
                    totalTermsImported += termsLoaded;

                    if (callback != null) {
                        callback.onDictionaryComplete(i + 1, totalDictionaries, dictName, termsLoaded);
                    }
                } else {
                    failCount++;
                    if (callback != null) {
                        callback.onDictionaryError(dictName, "Nenhum termo importado");
                    }
                }

            } catch (Exception e) {
                failCount++;
                if (callback != null) {
                    callback.onDictionaryError(dictName, e.getMessage());
                }
            }
        }

        ImportResult result = new ImportResult(successCount, failCount, totalTermsImported);

        if (callback != null) {
            callback.onComplete(result);
        }

        return result;
    }

    /**
     * Result of batch import
     */
    public static class ImportResult {
        public final int successCount;
        public final int failCount;
        public final int totalTermsImported;

        public ImportResult(int successCount, int failCount, int totalTermsImported) {
            this.successCount = successCount;
            this.failCount = failCount;
            this.totalTermsImported = totalTermsImported;
        }

        @Override
        public String toString() {
            return String.format("Importados: %d dicionários com sucesso, %d falharam. Total: %d termos",
                successCount, failCount, totalTermsImported);
        }
    }

    /**
     * Callback for batch import progress
     */
    public interface BatchProgressCallback {
        void onStart(int totalDictionaries, String message);
        void onDictionaryStart(int current, int total, String dictionaryName);
        void onDictionaryProgress(String dictionaryName, int currentTerms, int totalTerms, String message);
        void onDictionaryComplete(int current, int total, String dictionaryName, int termsImported);
        void onDictionaryError(String dictionaryName, String error);
        void onComplete(ImportResult result);
        void onError(String error);
    }
}
