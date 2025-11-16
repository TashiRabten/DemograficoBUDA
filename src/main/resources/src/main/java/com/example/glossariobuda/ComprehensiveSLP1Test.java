package com.example.glossariobuda;

import java.util.*;

/**
 * Comprehensive test suite for SLP1 conversions
 * Tests all characters, conjuncts, and edge cases
 */
public class ComprehensiveSLP1Test {

    public static void main(String[] args) {
        System.out.println("═".repeat(100));
        System.out.println("COMPREHENSIVE SLP1 CONVERSION TEST SUITE");
        System.out.println("═".repeat(100));

        int totalTests = 0;
        int passed = 0;
        int failed = 0;

        // Run all test categories
        TestResult result;

        result = testVowels();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testConsonants();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testConsonantClusters();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testConjuncts();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testSpecialCharacters();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testBuddhistTerms();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testHinduTerms();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testPhilosophicalTerms();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testComplexWords();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        result = testEdgeCases();
        totalTests += result.total;
        passed += result.passed;
        failed += result.failed;

        // Print summary
        System.out.println("\n" + "═".repeat(100));
        System.out.println("FINAL RESULTS");
        System.out.println("═".repeat(100));
        System.out.printf("Total Tests: %d%n", totalTests);
        System.out.printf("Passed: %d (%.1f%%)%n", passed, (passed * 100.0 / totalTests));
        System.out.printf("Failed: %d (%.1f%%)%n", failed, (failed * 100.0 / totalTests));

        if (failed == 0) {
            System.out.println("\n🎉 ALL TESTS PASSED! 🎉");
        } else {
            System.out.println("\n⚠️  Some tests failed. Review output above for details.");
        }
    }

    static TestResult testVowels() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: VOWELS (Standalone and Matras)");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            // Short vowels
            new TestCase("a", "a", "अ"),
            new TestCase("i", "i", "इ"),
            new TestCase("u", "u", "उ"),
            new TestCase("f", "ṛ", "ऋ"),
            new TestCase("x", "ḷ", "ऌ"),

            // Long vowels
            new TestCase("A", "ā", "आ"),
            new TestCase("I", "ī", "ई"),
            new TestCase("U", "ū", "ऊ"),
            new TestCase("F", "ṝ", "ॠ"),
            new TestCase("X", "ḹ", "ॡ"),

            // Diphthongs
            new TestCase("e", "e", "ए"),
            new TestCase("E", "ai", "ऐ"),
            new TestCase("o", "o", "ओ"),
            new TestCase("O", "au", "औ"),

            // Vowels with consonants (matras)
            new TestCase("ka", "ka", "क"),
            new TestCase("kA", "kā", "का"),
            new TestCase("ki", "ki", "कि"),
            new TestCase("kI", "kī", "की"),
            new TestCase("ku", "ku", "कु"),
            new TestCase("kU", "kū", "कू"),
            new TestCase("kf", "kṛ", "कृ"),
            new TestCase("kF", "kṝ", "कॄ"),
            new TestCase("kx", "kḷ", "कॢ"),
            new TestCase("kX", "kḹ", "कॣ"),
            new TestCase("ke", "ke", "के"),
            new TestCase("kE", "kai", "कै"),
            new TestCase("ko", "ko", "को"),
            new TestCase("kO", "kau", "कौ"),
        };

        return runTests(tests);
    }

    static TestResult testConsonants() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: CONSONANTS (All 33 Sanskrit Consonants)");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            // Velars (ka-varga)
            new TestCase("ka", "ka", "क"),
            new TestCase("Ka", "kha", "ख"),
            new TestCase("ga", "ga", "ग"),
            new TestCase("Ga", "gha", "घ"),
            new TestCase("Na", "ṅa", "ङ"),

            // Palatals (ca-varga)
            new TestCase("ca", "ca", "च"),
            new TestCase("Ca", "cha", "छ"),
            new TestCase("ja", "ja", "ज"),
            new TestCase("Ja", "jha", "झ"),
            new TestCase("Ya", "ña", "ञ"),

            // Retroflexes (ṭa-varga)
            new TestCase("wa", "ṭa", "ट"),
            new TestCase("Wa", "ṭha", "ठ"),
            new TestCase("qa", "ḍa", "ड"),
            new TestCase("Qa", "ḍha", "ढ"),
            new TestCase("Ra", "ṇa", "ण"),

            // Dentals (ta-varga)
            new TestCase("ta", "ta", "त"),
            new TestCase("Ta", "tha", "थ"),
            new TestCase("da", "da", "द"),
            new TestCase("Da", "dha", "ध"),
            new TestCase("na", "na", "न"),

            // Labials (pa-varga)
            new TestCase("pa", "pa", "प"),
            new TestCase("Pa", "pha", "फ"),
            new TestCase("ba", "ba", "ब"),
            new TestCase("Ba", "bha", "भ"),
            new TestCase("ma", "ma", "म"),

            // Semivowels
            new TestCase("ya", "ya", "य"),
            new TestCase("ra", "ra", "र"),
            new TestCase("la", "la", "ल"),
            new TestCase("La", "ḻa", "ळ"),
            new TestCase("va", "va", "व"),

            // Sibilants
            new TestCase("Sa", "śa", "श"),
            new TestCase("za", "ṣa", "ष"),
            new TestCase("sa", "sa", "स"),

            // Aspirate
            new TestCase("ha", "ha", "ह"),
        };

        return runTests(tests);
    }

    static TestResult testConsonantClusters() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: CONSONANT CLUSTERS");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            // Simple clusters
            new TestCase("kta", "kta", "क्त"),
            new TestCase("kra", "kra", "क्र"),
            new TestCase("kla", "kla", "क्ल"),
            new TestCase("kva", "kva", "क्व"),
            new TestCase("kya", "kya", "क्य"),

            // Nasal + consonant
            new TestCase("aNka", "aṅka", "अङ्क"),
            new TestCase("aNga", "aṅga", "अङ्ग"),
            new TestCase("aYca", "añca", "अञ्च"),
            new TestCase("aYja", "añja", "अञ्ज"),
            new TestCase("aRwa", "aṇṭa", "अण्ट"),
            new TestCase("aRqa", "aṇḍa", "अण्ड"),
            new TestCase("anta", "anta", "अन्त"),
            new TestCase("anda", "anda", "अन्द"),
            new TestCase("ampa", "ampa", "अम्प"),
            new TestCase("amba", "amba", "अम्ब"),

            // Complex clusters
            new TestCase("stra", "stra", "स्त्र"),
            new TestCase("skra", "skra", "स्क्र"),
            new TestCase("sKa", "stha", "स्थ"),
            new TestCase("sTa", "stha", "स्थ"),
            new TestCase("spa", "spa", "स्प"),
            new TestCase("sta", "sta", "स्त"),
            new TestCase("ska", "ska", "स्क"),

            // Double consonants
            new TestCase("kka", "kka", "क्क"),
            new TestCase("gga", "gga", "ग्ग"),
            new TestCase("cca", "cca", "च्च"),
            new TestCase("jja", "jja", "ज्ज"),
            new TestCase("wwa", "ṭṭa", "ट्ट"),
            new TestCase("qqa", "ḍḍa", "ड्ड"),
            new TestCase("tta", "tta", "त्त"),
            new TestCase("dda", "dda", "द्द"),
            new TestCase("ppa", "ppa", "प्प"),
            new TestCase("bba", "bba", "ब्ब"),
            new TestCase("mma", "mma", "म्म"),
        };

        return runTests(tests);
    }

    static TestResult testConjuncts() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: SPECIAL CONJUNCTS");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            // kṣa and jña
            new TestCase("kza", "kṣa", "क्ष"),
            new TestCase("kzetra", "kṣetra", "क्षेत्र"),
            new TestCase("jYa", "jña", "ज्ञ"),
            new TestCase("jYAna", "jñāna", "ज्ञान"),

            // tra combinations
            new TestCase("tra", "tra", "त्र"),
            new TestCase("ttra", "ttra", "त्त्र"),
            new TestCase("dra", "dra", "द्र"),
            new TestCase("ddra", "ddra", "द्द्र"),

            // Special r-combinations
            new TestCase("kra", "kra", "क्र"),
            new TestCase("Kra", "khra", "ख्र"),
            new TestCase("gra", "gra", "ग्र"),
            new TestCase("Gra", "ghra", "घ्र"),
            new TestCase("pra", "pra", "प्र"),
            new TestCase("Pra", "phra", "फ्र"),
            new TestCase("bra", "bra", "ब्र"),
            new TestCase("Bra", "bhra", "भ्र"),

            // śr, ṣr combinations
            new TestCase("Sra", "śra", "श्र"),
            new TestCase("zra", "ṣra", "ष्र"),
            new TestCase("sra", "sra", "स्र"),
        };

        return runTests(tests);
    }

    static TestResult testSpecialCharacters() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: SPECIAL CHARACTERS (Anusvāra, Visarga, etc.)");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            // Anusvara
            new TestCase("aM", "aṁ", "अं"),
            new TestCase("kaM", "kaṁ", "कं"),
            new TestCase("saMskfta", "saṁskṛta", "संस्कृत"),

            // Visarga
            new TestCase("aH", "aḥ", "अः"),
            new TestCase("kaH", "kaḥ", "कः"),
            new TestCase("namaH", "namaḥ", "नमः"),
            new TestCase("duHKa", "duḥkha", "दुःख"),

            // Avagraha
            new TestCase("a'a", "a'a", "अऽअ"),

            // Candrabindu
            new TestCase("a~", "ã", "अँ"),
            new TestCase("oM", "oṁ", "ओं"),

            // Danda
            new TestCase("a.", "a.", "अ।"),
            new TestCase("a..", "a..", "अ॥"),

            // Numbers
            new TestCase("0", "0", "०"),
            new TestCase("1", "1", "१"),
            new TestCase("2", "2", "२"),
            new TestCase("3", "3", "३"),
            new TestCase("4", "4", "४"),
            new TestCase("5", "5", "५"),
            new TestCase("6", "6", "६"),
            new TestCase("7", "7", "७"),
            new TestCase("8", "8", "८"),
            new TestCase("9", "9", "९"),
        };

        return runTests(tests);
    }

    static TestResult testBuddhistTerms() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: BUDDHIST TERMS");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            new TestCase("budDa", "buddha", "बुद्ध"),
            new TestCase("boDi", "bodhi", "बोधि"),
            new TestCase("Darma", "dharma", "धर्म"),
            new TestCase("saNGa", "saṅgha", "सङ्घ"),
            new TestCase("karma", "karma", "कर्म"),
            new TestCase("duHKa", "duḥkha", "दुःख"),
            new TestCase("nirvARa", "nirvāṇa", "निर्वाण"),
            new TestCase("taTAgata", "tathāgata", "तथागत"),
            new TestCase("prajYA", "prajñā", "प्रज्ञा"),
            new TestCase("jYAna", "jñāna", "ज्ञान"),
            new TestCase("kzetra", "kṣetra", "क्षेत्र"),
            new TestCase("samADi", "samādhi", "समाधि"),
            new TestCase("DyAna", "dhyāna", "ध्यान"),
            new TestCase("karuRA", "karuṇā", "करुणा"),
            new TestCase("muditA", "muditā", "मुदिता"),
            new TestCase("upekKA", "upekṣā", "उपेक्षा"),
            new TestCase("SIla", "śīla", "शील"),
            new TestCase("vinaya", "vinaya", "विनय"),
            new TestCase("aBiDarma", "abhidharma", "अभिधर्म"),
            new TestCase("mahAyAna", "mahāyāna", "महायान"),
            new TestCase("vajrayAna", "vajrayāna", "वज्रयान"),
            new TestCase("SUnyatA", "śūnyatā", "शून्यता"),
            new TestCase("taTatA", "tathatā", "तथता"),
        };

        return runTests(tests);
    }

    static TestResult testHinduTerms() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: HINDU TERMS");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            new TestCase("AtmA", "ātmā", "आत्मा"),
            new TestCase("brahman", "brahman", "ब्रह्मन्"),
            new TestCase("yoga", "yoga", "योग"),
            new TestCase("guru", "guru", "गुरु"),
            new TestCase("deva", "deva", "देव"),
            new TestCase("devI", "devī", "देवी"),
            new TestCase("Siva", "śiva", "शिव"),
            new TestCase("vizRu", "viṣṇu", "विष्णु"),
            new TestCase("Sakti", "śakti", "शक्ति"),
            new TestCase("mAyA", "māyā", "माया"),
            new TestCase("sUtra", "sūtra", "सूत्र"),
            new TestCase("mantra", "mantra", "मन्त्र"),
            new TestCase("yantra", "yantra", "यन्त्र"),
            new TestCase("tantra", "tantra", "तन्त्र"),
            new TestCase("veda", "veda", "वेद"),
            new TestCase("upanizad", "upaniṣad", "उपनिषद्"),
            new TestCase("purARa", "purāṇa", "पुराण"),
            new TestCase("SAstra", "śāstra", "शास्त्र"),
            new TestCase("satya", "satya", "सत्य"),
            new TestCase("ahiMsA", "ahiṁsā", "अहिंसा"),
            new TestCase("tapas", "tapas", "तपस्"),
            new TestCase("yajYa", "yajña", "यज्ञ"),
            new TestCase("pUjA", "pūjā", "पूजा"),
        };

        return runTests(tests);
    }

    static TestResult testPhilosophicalTerms() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: PHILOSOPHICAL TERMS");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            new TestCase("prakfti", "prakṛti", "प्रकृति"),
            new TestCase("puruza", "puruṣa", "पुरुष"),
            new TestCase("budDi", "buddhi", "बुद्धि"),
            new TestCase("ahaMkAra", "ahaṁkāra", "अहंकार"),
            new TestCase("manas", "manas", "मनस्"),
            new TestCase("sattva", "sattva", "सत्त्व"),
            new TestCase("rajas", "rajas", "रजस्"),
            new TestCase("tamas", "tamas", "तमस्"),
            new TestCase("guRa", "guṇa", "गुण"),
            new TestCase("dravya", "dravya", "द्रव्य"),
            new TestCase("kAraNa", "kāraṇa", "कारण"),
            new TestCase("kArya", "kārya", "कार्य"),
            new TestCase("pramARa", "pramāṇa", "प्रमाण"),
            new TestCase("pratyakza", "pratyakṣa", "प्रत्यक्ष"),
            new TestCase("anumAna", "anumāna", "अनुमान"),
            new TestCase("Sabda", "śabda", "शब्द"),
            new TestCase("sAMAnya", "sāmānya", "सामान्य"),
            new TestCase("viSeza", "viśeṣa", "विशेष"),
            new TestCase("samavAya", "samavāya", "समवाय"),
            new TestCase("aBAva", "abhāva", "अभाव"),
        };

        return runTests(tests);
    }

    static TestResult testComplexWords() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: COMPLEX COMPOUND WORDS");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            new TestCase("mahAtmA", "mahātmā", "महात्मा"),
            new TestCase("mahABArata", "mahābhārata", "महाभारत"),
            new TestCase("rAmAyaRa", "rāmāyaṇa", "रामायण"),
            new TestCase("BagavadgItA", "bhagavadgītā", "भगवद्गीता"),
            new TestCase("yogasUtra", "yogasūtra", "योगसूत्र"),
            new TestCase("brahmasUtra", "brahmasūtra", "ब्रह्मसूत्र"),
            new TestCase("sAMKyakArikA", "sāṁkhyakārikā", "सांख्यकारिका"),
            new TestCase("paramAtmA", "paramātmā", "परमात्मा"),
            new TestCase("parameSvara", "parameśvara", "परमेश्वर"),
            new TestCase("sarvajYa", "sarvajña", "सर्वज्ञ"),
            new TestCase("sarvatra", "sarvatra", "सर्वत्र"),
            new TestCase("pratItyasamutpAda", "pratītyasamutpāda", "प्रतीत्यसमुत्पाद"),
            new TestCase("saMyaksaMbudDa", "samyaksaṁbuddha", "सम्यक्संबुद्ध"),
        };

        return runTests(tests);
    }

    static TestResult testEdgeCases() {
        System.out.println("\n" + "─".repeat(100));
        System.out.println("TEST CATEGORY: EDGE CASES");
        System.out.println("─".repeat(100));

        TestCase[] tests = {
            // Empty and spaces
            new TestCase("", "", ""),
            new TestCase(" ", " ", " "),
            new TestCase("a a", "a a", "अ अ"),
            new TestCase("ka ka", "ka ka", "क क"),

            // Ending with consonants (virama)
            new TestCase("k", "k", "क्"),
            new TestCase("sat", "sat", "सत्"),
            new TestCase("Bat", "bhat", "भत्"),
            new TestCase("namH", "namh", "नम्ह्"),

            // Repeated characters
            new TestCase("aaa", "aaa", "अअअ"),
            new TestCase("AAA", "āāā", "आआआ"),
            new TestCase("kkk", "kkk", "क्क्क्"),

            // Mixed with numbers and punctuation
            new TestCase("ka1", "ka1", "क१"),
            new TestCase("2ka", "2ka", "२क"),
            new TestCase("namaH.", "namaḥ.", "नमः।"),
            new TestCase("oM..", "oṁ..", "ओं॥"),

            // Hyphens and dashes
            new TestCase("a-a", "a-a", "अ-अ"),
            new TestCase("ka-ka", "ka-ka", "क-क"),

            // Long conjuncts
            new TestCase("kstra", "kstra", "क्स्त्र"),
            new TestCase("ksatra", "ksatra", "क्सत्र"),
        };

        return runTests(tests);
    }

    static TestResult runTests(TestCase[] tests) {
        int passed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        System.out.printf("%-20s %-25s %-25s %-20s %-20s %s%n",
            "SLP1", "Expected IAST", "Got IAST", "Expected Deva", "Got Deva", "Status");
        System.out.println("·".repeat(100));

        for (TestCase tc : tests) {
            String gotIast = Slp1Converter.convert(tc.slp1);
            String gotDeva = Slp1ToDevanagariConverter.convert(tc.slp1);

            boolean iastMatch = gotIast.equals(tc.expectedIast);
            boolean devaMatch = gotDeva.equals(tc.expectedDeva);
            boolean success = iastMatch && devaMatch;

            if (success) {
                passed++;
                System.out.printf("%-20s %-25s %-25s %-20s %-20s %s%n",
                    tc.slp1, tc.expectedIast, gotIast, tc.expectedDeva, gotDeva, "✓");
            } else {
                failed++;
                System.out.printf("%-20s %-25s %-25s %-20s %-20s %s%n",
                    tc.slp1, tc.expectedIast, gotIast, tc.expectedDeva, gotDeva, "✗");

                if (!iastMatch) {
                    failures.add(String.format("  IAST: '%s' expected '%s' got '%s'",
                        tc.slp1, tc.expectedIast, gotIast));
                }
                if (!devaMatch) {
                    failures.add(String.format("  Deva: '%s' expected '%s' got '%s'",
                        tc.slp1, tc.expectedDeva, gotDeva));
                }
            }
        }

        if (!failures.isEmpty()) {
            System.out.println("\nFailure Details:");
            for (String failure : failures) {
                System.out.println(failure);
            }
        }

        System.out.printf("\nCategory Result: %d passed, %d failed (%.1f%% pass rate)%n",
            passed, failed, (passed * 100.0 / (passed + failed)));

        return new TestResult(passed + failed, passed, failed);
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

    static class TestResult {
        int total;
        int passed;
        int failed;

        TestResult(int total, int passed, int failed) {
            this.total = total;
            this.passed = passed;
            this.failed = failed;
        }
    }
}
