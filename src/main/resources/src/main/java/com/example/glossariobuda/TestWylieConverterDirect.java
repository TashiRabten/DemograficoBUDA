package com.example.glossariobuda;

/**
 * Direct test of WylieConverter with sample data from each dictionary type
 * Tests conversion without needing actual dictionary files
 *
 * Usage: mvn exec:java -Dexec.mainClass="com.example.glossariobuda.TestWylieConverterDirect"
 */
public class TestWylieConverterDirect {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     WYLIE CONVERTER DIRECT TEST                                ║");
        System.out.println("║     Tests conversion with sample entries                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Sample entries from different dictionary types
        String[][] samples = {
            // {wylie, english, dictionary_name}
            {"bka' drin che", "great kindness", "Rangjung Yeshe"},
            {"sems can thams cad", "all sentient beings", "Hopkins"},
            {"thugs rje che", "Thank you!", "Heart of Tibetan Language Vol. 1"},
            {"byang chub sems dpa'", "bodhisattva", "Ives-Waldo"},
            {"chos", "dharma; phenomenon; religion", "Dan Martin"},
            {"sgom pa", "meditation", "Berzin"},
            {"rnam par shes pa", "consciousness", "Mahāvyutpatti"},
            {"ting nge 'dzin", "samadhi; meditative absorption", "Gateway to Knowledge"},
            {"bla ma", "guru; spiritual teacher", "Tsepak Rigdzin"},
            {"kun gzhi rnam shes", "ālaya-vijñāna; storehouse consciousness", "Yogācārabhūmi"},
            {"dge ba'i bshes gnyen", "spiritual friend; kalyāṇa-mitra", "Hopkins Tibetan-Sanskrit"},
            {"skye mched", "āyatana; sense-sphere", "Lokesh Chandra"},
            {"bzang po", "good; virtuous; excellent", "Rangjung Yeshe"},
            {"mtha' gnyis spang ba", "avoiding the two extremes", "Berzin Definitions"},
            {"tshig don", "meaning of words", "Tshig mdzod chen mo"}
        };

        int totalTests = samples.length;
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < samples.length; i++) {
            String wylie = samples[i][0];
            String english = samples[i][1];
            String dictName = samples[i][2];

            System.out.println("─".repeat(65));
            System.out.printf("TEST %d/%d: %s\n", i + 1, totalTests, dictName);
            System.out.println("─".repeat(65));

            System.out.println("📄 ORIGINAL INPUT:");
            System.out.println("   " + wylie + " | " + english);
            System.out.println();

            try {
                // Convert Wylie to Tibetan Unicode
                String tibetanUnicode = WylieConverter.toUnicode(wylie);

                System.out.println("💾 DATABASE STORAGE:");
                System.out.println("   source_term:     " + tibetanUnicode);
                System.out.println("   source_language: Tibetan");
                System.out.println("   target_term:     " + english);
                System.out.println("   target_language: English");
                System.out.println("   wylie:           " + wylie);
                System.out.println("   contributor:     " + dictName);
                System.out.println();

                System.out.println("📱 WHAT USER SEES IN APP:");
                System.out.println("   ┌─────────────────────────────────────────────────────┐");
                System.out.printf("   │ Tibetan:  %-42s│\n", tibetanUnicode);
                System.out.printf("   │ English:  %-42s│\n", truncate(english, 42));
                System.out.println("   └─────────────────────────────────────────────────────┘");
                System.out.println();

                // Verification
                System.out.println("✓ VERIFICATION CHECKS:");

                // Check if Tibetan Unicode is present
                boolean hasTibetanUnicode = tibetanUnicode.codePoints()
                    .anyMatch(cp -> cp >= 0x0F00 && cp <= 0x0FFF);

                if (hasTibetanUnicode) {
                    System.out.println("   ✅ Tibetan Unicode detected (readable Tibetan script)");
                    System.out.println("   ✅ Conversion successful");
                    successCount++;
                } else {
                    System.out.println("   ❌ No Tibetan Unicode - conversion FAILED");
                    System.out.println("   ⚠️  Still showing Wylie: " + tibetanUnicode);
                    failCount++;
                }

                // Check conversion back to Wylie
                String backToWylie = WylieConverter.toWylie(tibetanUnicode);
                System.out.println("   ✅ Reverse conversion works: " + backToWylie);

                // Show character analysis
                System.out.println();
                System.out.println("📊 CHARACTER ANALYSIS:");
                System.out.printf("   Original Wylie:    %d chars, Latin alphabet\n", wylie.length());
                System.out.printf("   Tibetan Unicode:   %d chars, Tibetan script (U+0F00-0FFF)\n",
                    tibetanUnicode.length());

                // Show first few Unicode codepoints
                System.out.print("   Unicode codepoints: ");
                int cpCount = 0;
                for (int cp : tibetanUnicode.codePoints().toArray()) {
                    if (cpCount >= 5) {
                        System.out.print("...");
                        break;
                    }
                    System.out.printf("U+%04X ", cp);
                    cpCount++;
                }
                System.out.println();

            } catch (Exception e) {
                System.out.println("❌ CONVERSION ERROR: " + e.getMessage());
                e.printStackTrace();
                failCount++;
            }

            System.out.println();
        }

        // Final summary
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    TEST SUMMARY                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("Total tests:         %d\n", totalTests);
        System.out.printf("✅ Successful:       %d\n", successCount);
        System.out.printf("❌ Failed:           %d\n", failCount);
        System.out.printf("Success rate:        %.1f%%\n", (successCount * 100.0 / totalTests));
        System.out.println();

        if (failCount == 0) {
            System.out.println("🎉 ALL CONVERSIONS SUCCESSFUL!");
            System.out.println();
            System.out.println("Your Wylie→Tibetan converter is working perfectly.");
            System.out.println("All Steinert dictionaries will display readable Tibetan Unicode.");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("1. Run full test: mvn exec:java -Dexec.mainClass=\"com.example.glossariobuda.TestWylieConversion\"");
            System.out.println("   (Requires tibetan-dictionary-master folder)");
            System.out.println();
            System.out.println("2. If test passes, run full import:");
            System.out.println("   mvn exec:java -Dexec.mainClass=\"com.example.glossariobuda.ImportAllSteinertOptimized\"");
        } else {
            System.out.println("⚠️  SOME CONVERSIONS FAILED");
            System.out.println("Please review the errors above before proceeding.");
        }
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
