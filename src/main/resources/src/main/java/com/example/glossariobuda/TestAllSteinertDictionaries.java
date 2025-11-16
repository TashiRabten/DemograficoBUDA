package com.example.glossariobuda;

import java.io.File;
import java.util.*;

/**
 * Comprehensive test for all Steinert dictionaries
 * Tests both HOTL XML and pipe-delimited formats
 *
 * Usage: mvn exec:java -Dexec.mainClass="com.example.glossariobuda.TestAllSteinertDictionaries"
 */
public class TestAllSteinertDictionaries {

    private static final String BASE_PATH = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries";
    private static final int ENTRIES_TO_TEST = 5;

    public static void main(String[] args) {
        System.out.println("=== Comprehensive Steinert Dictionary Test ===\n");

        int totalTested = 0;
        int totalSuccess = 0;
        int totalFailed = 0;

        // Test 1: HOTL XML files
        System.out.println("### Test 1: Heart of Tibetan Language (XML) ###\n");
        String hotlPath = BASE_PATH + "/conversion/Heart_of_Tibetan_Language";
        String[] hotlFiles = {"hotl1.xml", "hotl2.xml", "hotl3.xml"};

        for (String filename : hotlFiles) {
            String fullPath = hotlPath + "/" + filename;
            TestResult result = testXMLDictionary(fullPath, filename);
            printResult(result);

            totalTested++;
            if (result.success) totalSuccess++;
            else totalFailed++;
        }

        System.out.println("\n" + "=".repeat(70) + "\n");

        // Test 2: Pipe-delimited dictionaries
        System.out.println("### Test 2: Pipe-Delimited Dictionaries ###\n");
        String publicPath = BASE_PATH + "/public";
        File publicFolder = new File(publicPath);

        if (!publicFolder.exists()) {
            System.err.println("ERROR: Public folder not found: " + publicPath);
            System.exit(1);
        }

        File[] files = publicFolder.listFiles();
        if (files == null) {
            System.err.println("ERROR: Cannot read public folder");
            System.exit(1);
        }

        // Filter and sort files
        List<File> dictFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && !file.getName().contains("84000")) {
                dictFiles.add(file);
            }
        }
        dictFiles.sort(Comparator.comparing(File::getName));

        // Test each dictionary
        for (File dictFile : dictFiles) {
            TestResult result = testPipeDictionary(dictFile);
            printResult(result);

            totalTested++;
            if (result.success) totalSuccess++;
            else totalFailed++;
        }

        // Final summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("### FINAL SUMMARY ###\n");
        System.out.println("Total dictionaries tested: " + totalTested);
        System.out.println("✓ Successful: " + totalSuccess);
        System.out.println("✗ Failed: " + totalFailed);
        System.out.println("Success rate: " + String.format("%.1f%%", (totalSuccess * 100.0 / totalTested)));

        if (totalFailed == 0) {
            System.out.println("\n=== ALL TESTS PASSED ===");
        } else {
            System.out.println("\n=== SOME TESTS FAILED ===");
            System.exit(1);
        }
    }

    private static TestResult testXMLDictionary(String filePath, String filename) {
        File file = new File(filePath);
        TestResult result = new TestResult(filename, "XML (HOTL)");

        if (!file.exists()) {
            result.success = false;
            result.error = "File not found: " + filePath;
            return result;
        }

        result.fileExists = true;
        result.fileSize = file.length();

        try {
            SteinertXMLParser parser = new SteinertXMLParser();

            if (!parser.canParse(file)) {
                result.success = false;
                result.error = "Parser cannot handle this file";
                return result;
            }

            result.parserDetected = true;

            List<GlossaryTerm> terms = parser.parse(file, null);
            result.totalEntries = terms.size();

            // Show first ENTRIES_TO_TEST entries
            int entriesToShow = Math.min(ENTRIES_TO_TEST, terms.size());
            for (int i = 0; i < entriesToShow; i++) {
                result.sampleEntries.add(formatEntry(terms.get(i), i + 1));
            }

            result.success = terms.size() > 0;

        } catch (Exception e) {
            result.success = false;
            result.error = e.getMessage();
        }

        return result;
    }

    private static TestResult testPipeDictionary(File file) {
        TestResult result = new TestResult(file.getName(), "Pipe-delimited");
        result.fileExists = true;
        result.fileSize = file.length();

        try {
            TibetanDictParser parser = new TibetanDictParser();

            if (!parser.canParse(file)) {
                result.success = false;
                result.error = "Parser cannot handle this file";
                return result;
            }

            result.parserDetected = true;

            List<GlossaryTerm> terms = parser.parse(file, null);
            result.totalEntries = terms.size();

            // Show first ENTRIES_TO_TEST entries
            int entriesToShow = Math.min(ENTRIES_TO_TEST, terms.size());
            for (int i = 0; i < entriesToShow; i++) {
                result.sampleEntries.add(formatEntry(terms.get(i), i + 1));
            }

            result.success = terms.size() > 0;

        } catch (Exception e) {
            result.success = false;
            result.error = e.getMessage();
        }

        return result;
    }

    private static String formatEntry(GlossaryTerm term, int number) {
        StringBuilder sb = new StringBuilder();
        sb.append("  [").append(number).append("] ");
        sb.append(truncate(term.getSourceTerm(), 30));
        sb.append(" → ");
        sb.append(truncate(term.getTargetTerm(), 30));
        if (term.getWylie() != null && !term.getWylie().isEmpty()) {
            sb.append(" (").append(truncate(term.getWylie(), 20)).append(")");
        }
        return sb.toString();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "(null)";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    private static void printResult(TestResult result) {
        String status = result.success ? "✓" : "✗";
        System.out.println(status + " " + result.filename + " (" + result.format + ")");
        System.out.println("  Path exists: " + result.fileExists);
        if (result.fileExists) {
            System.out.println("  Size: " + formatSize(result.fileSize));
        }
        System.out.println("  Parser detected: " + result.parserDetected);

        if (result.success) {
            System.out.println("  Total entries: " + result.totalEntries);
            System.out.println("  Sample entries:");
            for (String entry : result.sampleEntries) {
                System.out.println(entry);
            }
        } else {
            System.out.println("  ERROR: " + result.error);
        }

        System.out.println();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static class TestResult {
        String filename;
        String format;
        boolean fileExists = false;
        long fileSize = 0;
        boolean parserDetected = false;
        int totalEntries = 0;
        List<String> sampleEntries = new ArrayList<>();
        boolean success = false;
        String error = null;

        TestResult(String filename, String format) {
            this.filename = filename;
            this.format = format;
        }
    }
}
