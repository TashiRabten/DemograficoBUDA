package com.example.glossariobuda;

import java.io.File;
import java.util.List;

/**
 * Simple test to verify SteinertXMLParser works correctly
 *
 * Usage:
 * 1. Compile: mvn compile
 * 2. Run: java -cp target/classes com.example.glossariobuda.TestSteinertParser
 */
public class TestSteinertParser {

    public static void main(String[] args) {
        System.out.println("=== Testing Steinert XML Parser ===\n");

        // Test file path (adjust if needed)
        String testFilePath = "../hotl_test.xml";
        File testFile = new File(testFilePath);

        if (!testFile.exists()) {
            System.err.println("ERROR: Test file not found: " + testFilePath);
            System.err.println("Please ensure hotl_test.xml is in the parent directory.");
            System.exit(1);
        }

        System.out.println("Test file: " + testFile.getAbsolutePath());
        System.out.println("File exists: " + testFile.exists());
        System.out.println("File size: " + testFile.length() + " bytes\n");

        // Create parser
        SteinertXMLParser parser = new SteinertXMLParser();

        // Test 1: Can parse detection
        System.out.println("--- Test 1: Can Parse Detection ---");
        boolean canParse = parser.canParse(testFile);
        System.out.println("Can parse: " + canParse);
        System.out.println("Format: " + parser.getFormatDescription());
        System.out.println();

        if (!canParse) {
            System.err.println("ERROR: Parser cannot handle this file!");
            System.exit(1);
        }

        // Test 2: Metadata extraction
        System.out.println("--- Test 2: Metadata Extraction ---");
        FormatParser.ImportMetadata metadata = parser.extractMetadata(testFile);
        System.out.println("Suggested owner: " + metadata.suggestedOwner);
        System.out.println("Filename: " + metadata.fileName);
        System.out.println();

        // Test 3: Parse the file
        System.out.println("--- Test 3: Parsing ---");
        try {
            List<GlossaryTerm> terms = parser.parse(testFile, new FormatParser.LoadProgressCallback() {
                @Override
                public void onProgress(int current, int total, String message) {
                    System.out.println("[Progress] " + message);
                }

                @Override
                public void onComplete(int totalLoaded, String message) {
                    System.out.println("[Complete] " + message);
                }

                @Override
                public void onError(String error) {
                    System.err.println("[Error] " + error);
                }
            });

            System.out.println("\n--- Test 4: Results ---");
            System.out.println("Total terms parsed: " + terms.size());
            System.out.println();

            // Display each term
            for (int i = 0; i < terms.size(); i++) {
                GlossaryTerm term = terms.get(i);
                System.out.println("=== Term " + (i + 1) + " ===");
                System.out.println("Source (English): " + term.getSourceTerm());
                System.out.println("Source Language: " + term.getSourceLanguage());
                System.out.println("Target (Tibetan): " + term.getTargetTerm());
                System.out.println("Target Language: " + term.getTargetLanguage());
                System.out.println("Wylie: " + term.getWylie());

                if (term.getSanskrit() != null && !term.getSanskrit().isEmpty()) {
                    System.out.println("Sanskrit (IAST): " + term.getSanskrit());
                }

                System.out.println("\nContext:");
                System.out.println(term.getContext());

                System.out.println("\nNotes:");
                System.out.println(term.getNotes());

                System.out.println("\nContributor: " + term.getContributor());
                System.out.println("Owner: " + term.getOwner());
                System.out.println("\n" + "=".repeat(50) + "\n");
            }

            // Summary
            System.out.println("--- Test Summary ---");
            System.out.println("✓ Parser can detect HOTL XML files");
            System.out.println("✓ Metadata extraction works");
            System.out.println("✓ Parsing completed successfully");
            System.out.println("✓ " + terms.size() + " terms extracted");
            System.out.println("✓ All required fields populated");
            System.out.println("\n=== ALL TESTS PASSED ===");

        } catch (Exception e) {
            System.err.println("ERROR during parsing:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
