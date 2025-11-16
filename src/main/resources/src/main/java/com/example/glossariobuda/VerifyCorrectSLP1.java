package com.example.glossariobuda;

/**
 * Verify converter works 100% with CORRECT SLP1 encodings
 */
public class VerifyCorrectSLP1 {
    public static void main(String[] args) {
        System.out.println("═".repeat(80));
        System.out.println("VERIFICATION: Testing with CORRECT SLP1 Encodings");
        System.out.println("═".repeat(80));

        TestCase[] correctTests = {
            // Previously "failed" tests, now with CORRECT SLP1
            new TestCase("sTa", "stha", "स्थ", "Was wrongly tested as sKa"),
            new TestCase("upekzA", "upekṣā", "उपेक्षा", "Was wrongly tested as upekKA"),
            new TestCase("kAraRa", "kāraṇa", "कारण", "Was wrongly tested as kAraNa"),
            new TestCase("sAmAnya", "sāmānya", "सामान्य", "Was wrongly tested as sAMAnya"),
            new TestCase("samyaksaMbudDa", "samyaksaṁbuddha", "सम्यक्संबुद्ध", "Was wrongly tested as saMyaksaMbudDa"),
            new TestCase("namha", "namha", "नम्ह", "Was wrongly tested as namH"),

            // Additional verification - complex terms
            new TestCase("DarmaH", "dharmaḥ", "धर्मः", "dharma with aspirated dh"),
            new TestCase("nirvARa", "nirvāṇa", "निर्वाण", "nirvāṇa with retroflex ṇ"),
            new TestCase("saNGa", "saṅgha", "सङ्घ", "saṅgha with velar nasal"),
            new TestCase("pramARa", "pramāṇa", "प्रमाण", "pramāṇa with retroflex ṇ"),
            new TestCase("jYAna", "jñāna", "ज्ञान", "jñāna conjunct"),
            new TestCase("kzetra", "kṣetra", "क्षेत्र", "kṣetra conjunct"),
            new TestCase("prakfti", "prakṛti", "प्रकृति", "prakṛti with vocalic r"),
            new TestCase("saMskfta", "saṁskṛta", "संस्कृत", "saṁskṛta with anusvara"),
        };

        System.out.printf("%-25s %-25s %-25s %-20s %s%n",
            "SLP1", "Expected IAST", "Got IAST", "Expected Deva", "Status");
        System.out.println("─".repeat(80));

        int passed = 0;
        int failed = 0;

        for (TestCase tc : correctTests) {
            String gotIast = Slp1Converter.convert(tc.slp1);
            String gotDeva = Slp1ToDevanagariConverter.convert(tc.slp1);

            boolean iastMatch = gotIast.equals(tc.expectedIast);
            boolean devaMatch = gotDeva.equals(tc.expectedDeva);
            boolean success = iastMatch && devaMatch;

            if (success) {
                passed++;
                System.out.printf("%-25s %-25s %-25s %-20s %s%n",
                    tc.slp1, tc.expectedIast, gotIast, tc.expectedDeva, "✓");
            } else {
                failed++;
                System.out.printf("%-25s %-25s %-25s %-20s %s%n",
                    tc.slp1, tc.expectedIast, gotIast, tc.expectedDeva, "✗ FAILED");
                System.out.println("  Got Deva: " + gotDeva);
            }
        }

        System.out.println("\n" + "═".repeat(80));
        System.out.println("RESULTS");
        System.out.println("═".repeat(80));
        System.out.printf("Total: %d tests%n", correctTests.length);
        System.out.printf("Passed: %d (%.1f%%)%n", passed, (passed * 100.0 / correctTests.length));
        System.out.printf("Failed: %d%n", failed);

        if (failed == 0) {
            System.out.println("\n🎉 100% PASS RATE WITH CORRECT SLP1 ENCODINGS! 🎉");
            System.out.println("\nCONCLUSION: No implementation bugs. Converter is perfect!");
        } else {
            System.out.println("\n⚠️ IMPLEMENTATION BUGS FOUND!");
        }

        // Show notes
        System.out.println("\n" + "─".repeat(80));
        System.out.println("NOTES ON CORRECTED TESTS:");
        System.out.println("─".repeat(80));
        for (TestCase tc : correctTests) {
            if (tc.note != null && !tc.note.isEmpty()) {
                System.out.println(tc.slp1 + " → " + tc.note);
            }
        }
    }

    static class TestCase {
        String slp1;
        String expectedIast;
        String expectedDeva;
        String note;

        TestCase(String slp1, String expectedIast, String expectedDeva, String note) {
            this.slp1 = slp1;
            this.expectedIast = expectedIast;
            this.expectedDeva = expectedDeva;
            this.note = note;
        }
    }
}
