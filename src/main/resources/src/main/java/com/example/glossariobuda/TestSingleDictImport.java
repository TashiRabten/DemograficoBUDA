package com.example.glossariobuda;

import java.io.File;
import java.util.List;

/**
 * Quick test to verify TibetanDictParser is working with latest fixes
 */
public class TestSingleDictImport {

    public static void main(String[] args) {
        System.out.println("=== Testing TibetanDictParser ===\n");

        // Test file path
        String testFile = "tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/public/34-dung-dkar-tshig-mdzod-chen-mo-Tib";

        File file = new File(testFile);

        if (!file.exists()) {
            System.err.println("❌ Test file not found: " + testFile);
            System.err.println("Current directory: " + new File(".").getAbsolutePath());
            return;
        }

        System.out.println("✓ Test file found: " + file.getName());
        System.out.println("✓ File size: " + file.length() + " bytes\n");

        try {
            TibetanDictParser parser = new TibetanDictParser();

            System.out.println("Testing parser.canParse()...");
            boolean canParse = parser.canParse(file);
            System.out.println("  Result: " + (canParse ? "✓ CAN parse" : "✗ CANNOT parse") + "\n");

            if (!canParse) {
                System.err.println("❌ Parser rejected the file!");
                return;
            }

            System.out.println("Parsing file...");
            List<GlossaryTerm> terms = parser.parse(file, new FormatParser.LoadProgressCallback() {
                @Override
                public void onProgress(int current, int total, String message) {
                    if (current % 100 == 0) {
                        System.out.println("  Progress: " + current + " terms");
                    }
                }

                @Override
                public void onComplete(int totalLoaded, String message) {
                    System.out.println("  " + message);
                }

                @Override
                public void onError(String error) {
                    System.err.println("  ❌ Error: " + error);
                }
            });

            System.out.println("\n=== Parse Results ===");
            System.out.println("Total terms parsed: " + terms.size());

            if (terms.isEmpty()) {
                System.err.println("❌ NO TERMS PARSED!");
                return;
            }

            System.out.println("\n=== Testing Fixes ===\n");

            // Test 1: Apostrophes
            System.out.println("Test 1: Apostrophe conversion");
            GlossaryTerm testTerm = null;
            for (GlossaryTerm term : terms) {
                if (term.getWylie() != null && term.getWylie().equals("gna'")) {
                    testTerm = term;
                    break;
                }
            }

            if (testTerm != null) {
                System.out.println("  Found entry: wylie=gna'");
                System.out.println("  Source: " + testTerm.getSourceTerm());
                System.out.println("  Expected: གནའ");
                boolean hasApostrophe = testTerm.getSourceTerm().contains("'");
                System.out.println("  Status: " + (hasApostrophe ? "❌ FAIL (contains apostrophe)" : "✓ PASS"));
            } else {
                System.out.println("  ⚠ Could not find test entry with wylie='gna''");
            }

            // Test 2: Tsheg spacing
            System.out.println("\nTest 2: Tsheg spacing in target");
            for (GlossaryTerm term : terms) {
                if (term.getWylie() != null && term.getWylie().equals("ka ka ni")) {
                    System.out.println("  Found entry: wylie='ka ka ni'");
                    System.out.println("  Target: " + term.getTargetTerm());
                    boolean hasTsheg = term.getTargetTerm().contains("་");
                    boolean hasSpace = term.getTargetTerm().contains(" ");
                    System.out.println("  Has tshegs (་): " + hasTsheg);
                    System.out.println("  Has spaces: " + hasSpace);
                    System.out.println("  Status: " + (hasTsheg && !hasSpace ? "✓ PASS" : "❌ FAIL"));
                    break;
                }
            }

            // Show first 5 entries
            System.out.println("\n=== First 5 Entries ===");
            for (int i = 0; i < Math.min(5, terms.size()); i++) {
                GlossaryTerm t = terms.get(i);
                System.out.println((i+1) + ". " + t.getWylie() + " → " + t.getSourceTerm());
                System.out.println("   Target: " + t.getTargetTerm().substring(0, Math.min(50, t.getTargetTerm().length())) + "...");
                System.out.println();
            }

            System.out.println("=== Test Complete ===");

        } catch (Exception e) {
            System.err.println("\n❌ ERROR during parsing:");
            e.printStackTrace();
        }
    }
}
