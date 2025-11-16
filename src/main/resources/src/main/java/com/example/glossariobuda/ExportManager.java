package com.example.glossariobuda;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ExportManager {
    private final DatabaseManager dbManager;

    public ExportManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Get the export directory path in Documents/.glossariobuda/exports
     */
    private Path getExportDirectory() {
        String userHome = System.getProperty("user.home");
        Path documentsDir = Paths.get(userHome, "Documents");
        Path appDir = documentsDir.resolve(".glossariobuda");
        Path exportDir = appDir.resolve("exports");

        try {
            // Create directories if they don't exist
            if (!Files.exists(exportDir)) {
                Files.createDirectories(exportDir);
                System.out.println("[ExportManager] Created export directory: " + exportDir);
            }
        } catch (IOException e) {
            System.err.println("[ExportManager] Error creating export directory: " + e.getMessage());
        }

        return exportDir;
    }


    public String exportRecentTermsForWhatsApp(int count) {
        List<DatabaseManager.Term> recentTerms = dbManager.getRecentTerms(count);

        if (recentTerms.isEmpty()) {
            return "📚 *Glossário BUDA* - Nenhum termo encontrado";
        }

        StringBuilder export = new StringBuilder();
        export.append("📚 *Glossário BUDA*\n");
        export.append("🗓️ Últimos ").append(recentTerms.size()).append(" termos recentes\n");
        export.append("═══════════════════════\n\n");

        String currentLanguagePair = "";
        for (DatabaseManager.Term term : recentTerms) {
            String languagePair = term.getSourceLanguage() + " → " + term.getTargetLanguage();

            if (!languagePair.equals(currentLanguagePair)) {
                if (!currentLanguagePair.isEmpty()) {
                    export.append("\n");
                }
                export.append("🌍 *").append(languagePair.toUpperCase()).append("*\n");
                export.append("───────────────\n");
                currentLanguagePair = languagePair;
            }

            export.append("• **").append(term.getSourceTerm()).append("**\n");
            export.append("  ➜ ").append(term.getTargetTerm()).append("\n");

            // ✅ FIXED: Handle List<String> for contexts
            List<String> contexts = term.getContexts();
            if (contexts != null && !contexts.isEmpty()) {
                export.append("  📝 _").append(String.join(" | ", contexts)).append("_\n");
            }

            // ✅ FIXED: Handle List<String> for contributors
            List<String> contributors = term.getContributors();
            if (contributors != null && !contributors.isEmpty()) {
                export.append("  👤 ").append(String.join(", ", contributors)).append("\n");
            }

            // Add formatted date
            if (term.getDateAdded() != null && !term.getDateAdded().isEmpty()) {
                export.append("  📅 ").append(formatDate(term.getDateAdded())).append("\n");
            }

            export.append("\n");
        }

        export.append("═══════════════════════\n");
        export.append("📊 Total: ").append(recentTerms.size()).append(" termos\n");
        export.append("🕐 Gerado em: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        return export.toString();
    }
    public String exportAllTermsForWhatsApp() {
        List<DatabaseManager.Term> allTerms = dbManager.getRecentTerms(1000); // Get up to 1000 terms

        if (allTerms.isEmpty()) {
            return "📚 *Glossário BUDA* - Nenhum termo no banco de dados";
        }

        StringBuilder export = new StringBuilder();
        export.append("📚 *GLOSSÁRIO COMPLETO BUDA*\n");
        export.append("═══════════════════════════\n\n");

        String currentLanguagePair = "";
        for (DatabaseManager.Term term : allTerms) {
            String languagePair = term.getSourceLanguage() + " → " + term.getTargetLanguage();

            if (!languagePair.equals(currentLanguagePair)) {
                if (!currentLanguagePair.isEmpty()) {
                    export.append("\n");
                }
                export.append("🌍 *").append(languagePair.toUpperCase()).append("*\n");
                export.append("───────────────────\n");
                currentLanguagePair = languagePair;
            }

            export.append("• **").append(term.getSourceTerm()).append("** → ").append(term.getTargetTerm());

            String status = getStatusIcon(term.getVerifiedStatus());
            export.append(" ").append(status).append("\n");

            // ✅ FIXED: Handle List<String> for contexts
            List<String> contexts = term.getContexts();
            if (contexts != null && !contexts.isEmpty()) {
                export.append("  📝 _").append(String.join(" | ", contexts)).append("_\n");
            }
        }

        export.append("\n═══════════════════════════\n");
        export.append("📊 Total: ").append(allTerms.size()).append(" termos\n");
        export.append("🕐 Gerado em: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        return export.toString();
    }

    public boolean saveExportToFile(String content, String filename) {
        try {
            Path exportDir = getExportDirectory();
            Path filePath = exportDir.resolve(filename);
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(content);
                System.out.println("[ExportManager] Saved export to: " + filePath);
                return true;
            }
        } catch (IOException e) {
            System.err.println("[ExportManager] Error saving export: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    public String exportTermsByContributor(String contributor) {
        List<DatabaseManager.Term> allTerms = dbManager.getRecentTerms(1000);

        // ✅ FIXED: Filter by checking if contributor is in the contributors list
        List<DatabaseManager.Term> contributorTerms = allTerms.stream()
                .filter(term -> {
                    List<String> contributors = term.getContributors();
                    return contributors != null && contributors.contains(contributor);
                })
                .toList();

        if (contributorTerms.isEmpty()) {
            return "📚 *Glossário BUDA* - Nenhum termo encontrado para " + contributor;
        }

        StringBuilder export = new StringBuilder();
        export.append("📚 *Glossário BUDA*\n");
        export.append("👤 Contribuições de: ").append(contributor).append("\n");
        export.append("═══════════════════════\n\n");

        for (DatabaseManager.Term term : contributorTerms) {
            export.append("• **").append(term.getSourceTerm()).append("**\n");
            export.append("  ➜ ").append(term.getTargetTerm()).append("\n");

            // ✅ FIXED: Handle List<String> for contexts
            List<String> contexts = term.getContexts();
            if (contexts != null && !contexts.isEmpty()) {
                export.append("  📝 _").append(String.join(" | ", contexts)).append("_\n");
            }

            // Add formatted date
            if (term.getDateAdded() != null && !term.getDateAdded().isEmpty()) {
                export.append("  📅 ").append(formatDate(term.getDateAdded())).append("\n");
            }

            export.append("\n");
        }

        export.append("═══════════════════════\n");
        export.append("📊 Total: ").append(contributorTerms.size()).append(" termos\n");
        export.append("🕐 Gerado em: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        return export.toString();
    }
    
    private String getStatusIcon(String status) {
        return switch (status != null ? status.toLowerCase() : "draft") {
            case "verified", "approved" -> "✅";
            case "reviewed" -> "🔍";
            case "draft" -> "📝";
            default -> "❓";
        };
    }
    
    public String generateWhatsAppMessage(String exportContent) {
        return exportContent + "\n\n" +
               "🔗 _Compartilhe este glossário com outros tradutores!_\n" +
               "💡 _Para sugerir correções ou novos termos, entre em contato._";
    }

    /**
     * Format date from ISO 8601 to friendly format
     */
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return "Data desconhecida";
        }

        try {
            // Parse ISO 8601 date (e.g., "2025-10-13T05:57:53.775698+00:00" or "2025-10-13 15:30:00")
            java.time.format.DateTimeFormatter inputFormatter;

            if (isoDate.contains("T")) {
                // ISO format with T separator
                inputFormatter = java.time.format.DateTimeFormatter.ISO_DATE_TIME;
            } else {
                // SQL format with space separator
                inputFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            }

            java.time.ZonedDateTime dateTime;
            if (isoDate.contains("T") && (isoDate.contains("+") || isoDate.contains("Z"))) {
                // Full ISO with timezone
                dateTime = java.time.ZonedDateTime.parse(isoDate, inputFormatter);
            } else {
                // No timezone, assume local
                java.time.LocalDateTime localDateTime = java.time.LocalDateTime.parse(isoDate, inputFormatter);
                dateTime = localDateTime.atZone(java.time.ZoneId.systemDefault());
            }

            // Format to friendly Brazilian Portuguese format
            java.time.format.DateTimeFormatter outputFormatter =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            return dateTime.format(outputFormatter);
        } catch (Exception e) {
            // If parsing fails, return the original date
            System.err.println("Error formatting date: " + isoDate + " - " + e.getMessage());
            return isoDate;
        }
    }
}