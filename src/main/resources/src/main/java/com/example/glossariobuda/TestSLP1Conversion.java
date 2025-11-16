package com.example.glossariobuda;

import java.io.*;
import java.util.*;

/**
 * Test SLP1 conversions using the IAST-SLP1 test.txt file
 */
public class TestSLP1Conversion {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing SLP1 → IAST → Devanagari Conversion");
        System.out.println("=".repeat(80));

        // Test key Buddhist and Hindu terms
        String[] keyTerms = {
            // Buddhist terms
            "budDaH", "bodhiH", "nirvANaM", "saNGaH", "taTAgataH",
            "duHKaM", "kzetraM", "jYAnaM", "prajYA", "samADiH",
            "DyAnaM", "karuNA", "zUnyatA", "pramARa",

            // Hindu terms
            "AtmA", "dharmaH", "karma", "yogaH", "guruH",
            "zivaH", "viRNuH", "brahman", "zaktiH", "mAyA",
            "sUtraM", "mantraH", "vedaH", "upanizad", "puRANa",

            // Philosophical terms
            "prakfti", "puruza", "buddhi", "ahaMkAra", "manas",
            "sattva", "rajas", "tamas", "guRa", "dravya",

            // Complex consonant clusters
            "kzatraH", "trayaH", "pravftti", "saMskfta",
            "svaBAvaH", "svarUpaM", "sTAnaM", "snAna"
        };

        System.out.printf("%-25s %-30s %s%n", "SLP1", "IAST", "Devanagari");
        System.out.println("-".repeat(80));

        for (String slp1 : keyTerms) {
            try {
                String iast = Slp1Converter.convert(slp1);
                String deva = Slp1ToDevanagariConverter.convert(slp1);
                System.out.printf("%-25s %-30s %s%n", slp1, iast, deva);
            } catch (Exception e) {
                System.out.printf("%-25s ERROR: %s%n", slp1, e.getMessage());
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("Comparison with Web App Expected Results:");
        System.out.println("=".repeat(80));

        // These should match the web app behavior
        TestCase[] testCases = {
            new TestCase("prakfti", "prakṛti", "प्रकृति"),
            new TestCase("nirvANaM", "nirvāṇaṁ", "निर्वाणं"),
            new TestCase("saNGaH", "saṅghaḥ", "सङ्घः"),
            new TestCase("pramARa", "pramāṇa", "प्रमाण"),
            new TestCase("jYAnaM", "jñānaṁ", "ज्ञानं"),
            new TestCase("kzetraM", "kṣetraṁ", "क्षेत्रं"),
            new TestCase("duHKaM", "duḥkhaṁ", "दुःखं"),
            new TestCase("AtmA", "ātmā", "आत्मा"),
            new TestCase("dharmaH", "dharmaḥ", "धर्मः")
        };

        System.out.printf("%-15s %-20s %-15s %-20s %-15s%n",
            "SLP1", "Expected IAST", "Got IAST", "Expected Deva", "Got Deva");
        System.out.println("-".repeat(90));

        int passed = 0;
        int failed = 0;

        for (TestCase tc : testCases) {
            String gotIast = Slp1Converter.convert(tc.slp1);
            String gotDeva = Slp1ToDevanagariConverter.convert(tc.slp1);

            boolean iastMatch = gotIast.equals(tc.expectedIast);
            boolean devaMatch = gotDeva.equals(tc.expectedDeva);

            String status = (iastMatch && devaMatch) ? "✓" : "✗";
            if (iastMatch && devaMatch) passed++;
            else failed++;

            System.out.printf("%-15s %-20s %-15s %-20s %-15s %s%n",
                tc.slp1, tc.expectedIast, gotIast, tc.expectedDeva, gotDeva, status);
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
    }

    static class TestCase {
        String slp1;
        String expectedIast;
        String expectedDeva;

        TestCase(String slp1, String expectedIast, String expectedDeva) {
            this.slp1 = slp1;
            this.expectedIast = expectedIast;
            this.expectedDeva = expectedDeva;
        }
    }
}
