package com.example.glossariobuda;

import java.io.File;
import java.util.List;

/**
 * Test language detection in pipe-delimited dictionaries
 * Verifies that Tibetan-only dictionaries are correctly identified
 */
public class TestLanguageDetection {

    public static void main(String[] args) {
        System.out.println("=== Language Detection Test ===\n");

        TibetanDictParser parser = new TibetanDictParser();

        // Test English dictionary
        testDictionary(parser, "01-Hopkins2015", "English");

        // Test Tibetan-only dictionaries
        testDictionary(parser, "18-Hopkins-TibetanDefinitions2015", "Tibetan");
        testDictionary(parser, "25-tshig-mdzod-chen-mo-Tib", "Tibetan");
        testDictionary(parser, "34-dung-dkar-tshig-mdzod-chen-mo-Tib", "Tibetan");

        // Test mixed
        testDictionary(parser, "02-RangjungYeshe", "English");
        testDictionary(parser, "48-TibTermProject", "English");

        System.out.println("\n=== Test Complete ===");
    }

    private static void testDictionary(TibetanDictParser parser, String filename, String expectedLang) {
        String path = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/public/" + filename;
        File file = new File(path);

        System.out.printf("Testing: %s\n", filename);
        System.out.printf("  Expected: %s\n", expectedLang);

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

            // Check first 10 terms
            int tibetanCount = 0;
            int englishCount = 0;

            int samplesToCheck = Math.min(10, terms.size());
            for (int i = 0; i < samplesToCheck; i++) {
                GlossaryTerm term = terms.get(i);
                String lang = term.getTargetLanguage();

                if ("Tibetan".equals(lang)) {
                    tibetanCount++;
                } else if ("English".equals(lang)) {
                    englishCount++;
                }

                if (i < 3) {
                    // Show first 3 entries
                    String target = term.getTargetTerm();
                    if (target.length() > 50) {
                        target = target.substring(0, 47) + "...";
                    }
                    System.out.printf("    [%d] %s → %s (%s)\n",
                        i + 1, term.getSourceTerm(), target, lang);
                }
            }

            String detectedLang = tibetanCount > englishCount ? "Tibetan" : "English";
            boolean correct = detectedLang.equals(expectedLang);

            System.out.printf("  Detected: %s (Tib:%d, Eng:%d) %s\n\n",
                detectedLang, tibetanCount, englishCount,
                correct ? "✓" : "✗ WRONG!");

        } catch (Exception e) {
            System.out.println("  ✗ ERROR: " + e.getMessage() + "\n");
        }
    }
}
