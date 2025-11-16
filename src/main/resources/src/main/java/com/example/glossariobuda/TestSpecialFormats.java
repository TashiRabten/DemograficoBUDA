package com.example.glossariobuda;

import java.io.File;
import java.util.List;

/**
 * Test special dictionary formats:
 * - Sanskrit→Tibetan (Mahavyutpatti)
 * - Tibetan→Sanskrit (Hopkins, Negi)
 * - Tibetan→Tibetan (definitions)
 */
public class TestSpecialFormats {

    public static void main(String[] args) {
        System.out.println("=== Testing Special Dictionary Formats ===\n");

        TibetanDictParser parser = new TibetanDictParser();

        // Test 1: Sanskrit→Tibetan (REVERSE)
        System.out.println("### 1. Sanskrit→Tibetan (Mahavyutpatti) ###");
        testDictionary(parser, "21-Mahavyutpatti-Skt", "Sanskrit", "Tibetan");

        // Test 2: Tibetan→Sanskrit
        System.out.println("\n### 2. Tibetan→Sanskrit ###");
        testDictionary(parser, "15-Hopkins-Skt2015", "Tibetan", "Sanskrit");
        testDictionary(parser, "50-NegiSkt", "Tibetan", "Sanskrit");

        // Test 3: Tibetan→Tibetan
        System.out.println("\n### 3. Tibetan→Tibetan (Monolingual) ###");
        testDictionary(parser, "25-tshig-mdzod-chen-mo-Tib", "Tibetan", "Tibetan");

        // Test 4: Standard Tibetan→English
        System.out.println("\n### 4. Tibetan→English (Standard) ###");
        testDictionary(parser, "02-RangjungYeshe", "Tibetan", "English");

        System.out.println("\n=== All Tests Complete ===");
    }

    private static void testDictionary(TibetanDictParser parser, String filename,
                                       String expectedSource, String expectedTarget) {
        String path = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/public/" + filename;
        File file = new File(path);

        System.out.printf("Testing: %s\n", filename);
        System.out.printf("  Expected: %s → %s\n", expectedSource, expectedTarget);

        if (!file.exists()) {
            System.out.println("  ✗ File not found\n");
            return;
        }

        try {
            List<GlossaryTerm> terms = parser.parse(file, null);

            if (terms.isEmpty()) {
                System.out.println("  ✗ No terms parsed\n");
                return;
            }

            // Check first 3 entries
            boolean allCorrect = true;
            for (int i = 0; i < Math.min(3, terms.size()); i++) {
                GlossaryTerm term = terms.get(i);
                String actualSource = term.getSourceLanguage();
                String actualTarget = term.getTargetLanguage();

                boolean sourceOk = expectedSource.equals(actualSource);
                boolean targetOk = expectedTarget.equals(actualTarget);

                String sourceTerm = term.getSourceTerm();
                String targetTerm = term.getTargetTerm();
                if (sourceTerm.length() > 30) sourceTerm = sourceTerm.substring(0, 27) + "...";
                if (targetTerm.length() > 30) targetTerm = targetTerm.substring(0, 27) + "...";

                System.out.printf("    [%d] %s → %s (%s → %s) %s\n",
                    i + 1,
                    sourceTerm, targetTerm,
                    actualSource, actualTarget,
                    (sourceOk && targetOk) ? "✓" : "✗");

                if (!sourceOk || !targetOk) allCorrect = false;
            }

            System.out.printf("  Result: %s (%d total terms)\n\n",
                allCorrect ? "✓ PASS" : "✗ FAIL", terms.size());

        } catch (Exception e) {
            System.out.println("  ✗ ERROR: " + e.getMessage() + "\n");
        }
    }
}
