package com.example.glossariobuda;

import java.io.File;
import java.util.List;

/**
 * Test plain text format with ALL features:
 * - Wylie conversion (with apostrophes)
 * - Audio links [sound:...]
 * - Newline splitting
 * - Curly braces {content}
 * - Mixed content
 * - Already Tibetan Unicode
 *
 * Usage: mvn exec:java -Dexec.mainClass="com.example.glossariobuda.TestAllFeaturesPlainText"
 */
public class TestAllFeaturesPlainText {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     PLAIN TEXT FORMAT - ALL FEATURES TEST                      ║");
        System.out.println("║     TibetanDictParser with Audio, Wylie, Newlines, etc.       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        testComprehensiveFile();
        testAudioFile();

        System.out.println("\n" + "═".repeat(70));
        System.out.println("✓ ALL TESTS COMPLETE");
        System.out.println("═".repeat(70));
        System.out.println("\nNow import these files in your UI to verify:");
        System.out.println("1. TEST-ALL-FEATURES-PLAINTEXT");
        System.out.println("2. 67-TEST-HOTL-AUDIO");
        System.out.println("\nYou should see:");
        System.out.println("  ✓ Wylie converted to Tibetan Unicode");
        System.out.println("  ✓ 🔗 Link buttons appear for audio entries");
        System.out.println("  ✓ Newlines split correctly (first line in main, rest in context)");
        System.out.println("  ✓ Curly braces converted with tshegs");
        System.out.println("  ✓ Apostrophes preserved ('di → འདི, gna' → གནའ)");
    }

    private static void testComprehensiveFile() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 1: Comprehensive Features (TEST-ALL-FEATURES-PLAINTEXT)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        try {
            File file = new File("TEST-ALL-FEATURES-PLAINTEXT");
            if (!file.exists()) {
                System.out.println("⚠️  File not found: " + file.getAbsolutePath());
                return;
            }

            TibetanDictParser parser = new TibetanDictParser();
            List<GlossaryTerm> terms = parser.parse(file, new FormatParser.LoadProgressCallback() {
                @Override
                public void onProgress(int current, int total, String message) {
                    // Silent
                }
                @Override
                public void onComplete(int totalLoaded, String message) {
                    // Silent
                }
                @Override
                public void onError(String error) {
                    System.err.println("❌ ERROR: " + error);
                }
            });

            System.out.println("✓ Parsed " + terms.size() + " terms\n");

            // Show selected test cases
            showTestCase(terms, "thugs rje che", "TEST 1: Audio with Curly Braces");
            showTestCase(terms, "'di ga re red", "TEST 2: Leading Apostrophe");
            showTestCase(terms, "gna' rabs", "TEST 3: Trailing Apostrophe");
            showTestCase(terms, "sangs rgyas", "TEST 4: Basic Wylie");
            showTestCase(terms, "byang chub sems dpa'", "TEST 5: Complex Wylie");
            showTestCase(terms, "srid pa", "TEST 7: Newline Splitting");
            showTestCase(terms, "mya ngan las 'das pa", "TEST 8: Curly Braces");

        } catch (Exception e) {
            System.err.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testAudioFile() {
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 2: Audio Links (67-TEST-HOTL-AUDIO)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        try {
            File file = new File("67-TEST-HOTL-AUDIO");
            if (!file.exists()) {
                System.out.println("⚠️  File not found: " + file.getAbsolutePath());
                return;
            }

            TibetanDictParser parser = new TibetanDictParser();
            List<GlossaryTerm> terms = parser.parse(file, null);

            System.out.println("✓ Parsed " + terms.size() + " terms with AUDIO\n");

            // Show first 3 audio examples
            for (int i = 0; i < Math.min(3, terms.size()); i++) {
                GlossaryTerm term = terms.get(i);
                System.out.println("─".repeat(70));
                System.out.println("Entry " + (i + 1) + ":");
                System.out.println("  Source (Wylie): " + (term.getWylie() != null ? term.getWylie() : "N/A"));
                System.out.println("  Target (Tibetan): " + term.getTargetTerm());
                System.out.println("  Definition: " + term.getSourceTerm());

                // Check for audio link in notes
                String notes = term.getNotes();
                if (notes != null && notes.contains("href=")) {
                    System.out.println("  ✓ Audio Link: " + notes);
                    System.out.println("  → Link button SHOULD appear in UI");
                } else {
                    System.out.println("  ⚠️  No audio link found");
                }
                System.out.println();
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void showTestCase(List<GlossaryTerm> terms, String wylie, String testName) {
        System.out.println("─".repeat(70));
        System.out.println(testName);
        System.out.println("─".repeat(70));

        GlossaryTerm term = findTermByWylie(terms, wylie);
        if (term == null) {
            System.out.println("⚠️  Term not found: " + wylie);
            return;
        }

        System.out.println("Input Wylie: " + wylie);
        System.out.println("Converted:   " + (term.getTargetTerm() != null ? term.getTargetTerm() : "N/A"));
        System.out.println("Definition:  " + (term.getSourceTerm() != null ? term.getSourceTerm().substring(0, Math.min(60, term.getSourceTerm().length())) + "..." : "N/A"));

        if (term.getContext() != null && !term.getContext().isEmpty()) {
            System.out.println("Context:     " + term.getContext().substring(0, Math.min(60, term.getContext().length())) + "...");
        }

        if (term.getNotes() != null && !term.getNotes().isEmpty()) {
            System.out.println("Notes:       " + term.getNotes().substring(0, Math.min(60, term.getNotes().length())) + "...");
        }

        System.out.println();
    }

    private static GlossaryTerm findTermByWylie(List<GlossaryTerm> terms, String wylie) {
        for (GlossaryTerm term : terms) {
            if (term.getWylie() != null && term.getWylie().contains(wylie)) {
                return term;
            }
            // Also check target for already-Tibetan entries
            if (term.getTargetTerm() != null && term.getTargetTerm().contains(wylie)) {
                return term;
            }
        }
        return null;
    }
}
