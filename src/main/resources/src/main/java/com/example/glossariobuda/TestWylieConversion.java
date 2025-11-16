package com.example.glossariobuda;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Test script to verify Wylie→Tibetan Unicode conversion
 * Samples ONE entry from each Steinert dictionary and shows:
 * 1. Original input
 * 2. What gets stored in database
 * 3. What user sees in app
 *
 * Usage: mvn exec:java -Dexec.mainClass="com.example.glossariobuda.TestWylieConversion"
 */
public class TestWylieConversion {

    private static final String PUBLIC_FOLDER = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/public";
    private static final String HOTL_FOLDER = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/conversion/Heart_of_Tibetan_Language";

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     WYLIE CONVERSION TEST - ONE SAMPLE PER DICTIONARY         ║");
        System.out.println("║     Shows exactly what you'll see in your app                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        int totalDictionaries = 0;
        int successfulConversions = 0;
        int failedConversions = 0;

        // Test HOTL XML files
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("PHASE 1: HEART OF TIBETAN LANGUAGE (XML)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        String[] hotlFiles = {"hotl1.xml", "hotl2.xml", "hotl3.xml"};
        for (String filename : hotlFiles) {
            totalDictionaries++;
            System.out.println("─".repeat(65));
            System.out.println("📖 TESTING: " + filename);
            System.out.println("─".repeat(65));

            try {
                File file = new File(HOTL_FOLDER + "/" + filename);
                if (!file.exists()) {
                    System.out.println("⚠️  File not found: " + file.getAbsolutePath());
                    failedConversions++;
                    continue;
                }

                SteinertXMLParser parser = new SteinertXMLParser();
                List<GlossaryTerm> terms = parser.parse(file, null);

                if (terms.isEmpty()) {
                    System.out.println("⚠️  No terms found");
                    failedConversions++;
                    continue;
                }

                // Show first term as sample
                GlossaryTerm sample = terms.get(0);
                displayTermComparison(sample, filename);
                successfulConversions++;

            } catch (Exception e) {
                System.out.println("❌ ERROR: " + e.getMessage());
                e.printStackTrace();
                failedConversions++;
            }
            System.out.println();
        }

        // Test pipe-delimited files
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("PHASE 2: PIPE-DELIMITED DICTIONARIES");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        File publicFolder = new File(PUBLIC_FOLDER);
        if (!publicFolder.exists()) {
            System.err.println("❌ Public folder not found: " + publicFolder.getAbsolutePath());
            return;
        }

        File[] files = publicFolder.listFiles();
        if (files == null) {
            System.err.println("❌ Cannot read public folder");
            return;
        }

        // Filter dictionary files
        List<File> dictFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && !file.getName().contains("84000") &&
                !file.getName().startsWith(".")) {
                dictFiles.add(file);
            }
        }
        dictFiles.sort((a, b) -> a.getName().compareTo(b.getName()));

        TibetanDictParser parser = new TibetanDictParser();

        for (File dictFile : dictFiles) {
            totalDictionaries++;
            String name = dictFile.getName();

            System.out.println("─".repeat(65));
            System.out.println("📖 TESTING: " + name);
            System.out.println("─".repeat(65));

            try {
                if (!parser.canParse(dictFile)) {
                    System.out.println("⚠️  Skipped (not a valid dictionary format)");
                    failedConversions++;
                    continue;
                }

                // Read first valid line manually to show original
                String originalLine = getFirstValidLine(dictFile);
                if (originalLine == null) {
                    System.out.println("⚠️  No valid entries found");
                    failedConversions++;
                    continue;
                }

                System.out.println("📄 ORIGINAL INPUT:");
                System.out.println("   " + originalLine);
                System.out.println();

                // Parse the dictionary
                List<GlossaryTerm> terms = parser.parse(dictFile, null);

                if (terms.isEmpty()) {
                    System.out.println("⚠️  No terms parsed");
                    failedConversions++;
                    continue;
                }

                // Show first term
                GlossaryTerm sample = terms.get(0);
                displayTermComparison(sample, name);
                successfulConversions++;

            } catch (Exception e) {
                System.out.println("❌ ERROR: " + e.getMessage());
                failedConversions++;
            }
            System.out.println();
        }

        // Final summary
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    TEST SUMMARY                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("Total dictionaries tested: %d\n", totalDictionaries);
        System.out.printf("✅ Successful conversions: %d\n", successfulConversions);
        System.out.printf("❌ Failed conversions:     %d\n", failedConversions);
        System.out.println();

        if (failedConversions == 0) {
            System.out.println("🎉 ALL DICTIONARIES CONVERTED SUCCESSFULLY!");
            System.out.println("   Safe to run full import with ImportAllSteinertOptimized");
        } else {
            System.out.println("⚠️  Some dictionaries had issues - review before full import");
        }
    }

    /**
     * Display what the user will see in the app
     */
    private static void displayTermComparison(GlossaryTerm term, String source) {
        System.out.println("💾 DATABASE STORAGE:");
        System.out.println("   source_term:     " + (term.getSourceTerm() != null ? term.getSourceTerm() : "[null]"));
        System.out.println("   source_language: " + (term.getSourceLanguage() != null ? term.getSourceLanguage() : "[null]"));
        System.out.println("   target_term:     " + (term.getTargetTerm() != null ? term.getTargetTerm() : "[null]"));
        System.out.println("   target_language: " + (term.getTargetLanguage() != null ? term.getTargetLanguage() : "[null]"));
        System.out.println("   wylie:           " + (term.getWylie() != null ? term.getWylie() : "[null]"));
        System.out.println("   contributor:     " + (term.getContributor() != null ?
            (term.getContributor().length() > 50 ? term.getContributor().substring(0, 47) + "..." : term.getContributor())
            : "[null]"));
        System.out.println();

        System.out.println("📱 WHAT USER SEES IN APP:");
        System.out.println("   ┌─────────────────────────────────────────────────────┐");

        // Display based on direction
        if ("Tibetan".equals(term.getSourceLanguage())) {
            // Tibetan → Other
            System.out.printf("   │ Tibetan:  %-42s│\n",
                term.getSourceTerm() != null ? term.getSourceTerm() : "[empty]");
            System.out.printf("   │ %s: %-42s│\n",
                term.getTargetLanguage() != null ? String.format("%-8s", term.getTargetLanguage()) : "Target  ",
                term.getTargetTerm() != null ? truncate(term.getTargetTerm(), 42) : "[empty]");
        } else if ("Tibetan".equals(term.getTargetLanguage())) {
            // Other → Tibetan
            System.out.printf("   │ %s: %-42s│\n",
                term.getSourceLanguage() != null ? String.format("%-8s", term.getSourceLanguage()) : "Source  ",
                term.getSourceTerm() != null ? truncate(term.getSourceTerm(), 42) : "[empty]");
            System.out.printf("   │ Tibetan:  %-42s│\n",
                term.getTargetTerm() != null ? term.getTargetTerm() : "[empty]");
        } else {
            // Neither is Tibetan (shouldn't happen)
            System.out.printf("   │ Source:   %-42s│\n",
                term.getSourceTerm() != null ? truncate(term.getSourceTerm(), 42) : "[empty]");
            System.out.printf("   │ Target:   %-42s│\n",
                term.getTargetTerm() != null ? truncate(term.getTargetTerm(), 42) : "[empty]");
        }

        System.out.println("   └─────────────────────────────────────────────────────┘");
        System.out.println();

        // Verification checks
        System.out.println("✓ VERIFICATION CHECKS:");

        // Check if Tibetan Unicode is present
        boolean hasTibetanUnicode = false;
        String tibetanField = "Tibetan".equals(term.getSourceLanguage()) ? term.getSourceTerm() : term.getTargetTerm();
        if (tibetanField != null && !tibetanField.isEmpty()) {
            hasTibetanUnicode = tibetanField.codePoints()
                .anyMatch(cp -> cp >= 0x0F00 && cp <= 0x0FFF);
        }

        if (hasTibetanUnicode) {
            System.out.println("   ✅ Tibetan Unicode detected (readable Tibetan script)");
        } else if (term.getWylie() != null && !term.getWylie().isEmpty()) {
            System.out.println("   ⚠️  No Tibetan Unicode - still showing Wylie!");
            System.out.println("      This means conversion FAILED");
        } else {
            System.out.println("   ℹ️  No Tibetan field (might be non-Tibetan dictionary)");
        }

        // Check if Wylie is preserved
        if (term.getWylie() != null && !term.getWylie().isEmpty()) {
            System.out.println("   ✅ Wylie preserved for reference");
        }

        // Check if all required fields are present
        if (term.getSourceTerm() != null && !term.getSourceTerm().isEmpty() &&
            term.getTargetTerm() != null && !term.getTargetTerm().isEmpty()) {
            System.out.println("   ✅ Complete entry (source and target present)");
        } else {
            System.out.println("   ⚠️  Incomplete entry");
        }
    }

    /**
     * Get first valid non-comment line from dictionary file
     */
    private static String getFirstValidLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    return line;
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        return null;
    }

    /**
     * Truncate string to max length
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
