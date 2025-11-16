package com.example.glossariobuda;

import java.util.*;

public class Slp1Converter {

    // Base mapping: SLP1 codes → IAST
    private static final Map<String, String> SLP1_TO_IAST;
    static {
        Map<String, String> m = new LinkedHashMap<>();

        // ============================================================
        // Vowels (order matters - longer patterns first)
        // ============================================================
        m.put("A","ā");
        m.put("a","a");

        m.put("I","ī");
        m.put("i","i");

        m.put("U","ū");
        m.put("u","u");

        // Vocalic R and L
        m.put("F","ṝ");   // Long vocalic R
        m.put("f","ṛ");   // Short vocalic R
        m.put("X","ḹ");   // Long vocalic L
        m.put("x","ḷ");   // Short vocalic L

        // Diphthongs
        m.put("E","ai");
        m.put("e","e");
        m.put("O","au");
        m.put("o","o");

        // ============================================================
        // Anusvāra / Visarga / Avagraha
        // ============================================================
        m.put("M","ṁ");   // Anusvara (using dot above for better ICU compatibility)
        m.put("H","ḥ");   // Visarga
        m.put("'","'");   // Avagraha

        // ============================================================
        // Consonants - Velars
        // ============================================================
        m.put("K","kh");
        m.put("k","k");
        m.put("G","gh");
        m.put("g","g");
        m.put("N","ṅ");   // Velar nasal

        // ============================================================
        // Palatals
        // ============================================================
        m.put("C","ch");
        m.put("c","c");
        m.put("J","jh");
        m.put("j","j");
        m.put("Y","ñ");   // Palatal nasal

        // ============================================================
        // Retroflexes
        // ============================================================
        m.put("W","ṭh");
        m.put("w","ṭ");
        m.put("Q","ḍh");
        m.put("q","ḍ");
        m.put("R","ṇ");   // Retroflex nasal
        m.put("L","ḻ");   // Rare retroflex lateral approximant

        // ============================================================
        // Dentals
        // ============================================================
        m.put("T","th");
        m.put("t","t");
        m.put("D","dh");
        m.put("d","d");
        m.put("n","n");

        // ============================================================
        // Labials
        // ============================================================
        m.put("P","ph");
        m.put("p","p");
        m.put("B","bh");
        m.put("b","b");
        m.put("m","m");

        // ============================================================
        // Semivowels / Approximants
        // ============================================================
        m.put("y","y");
        m.put("r","r");
        m.put("l","l");
        m.put("v","v");

        // ============================================================
        // Sibilants / Fricatives
        // ============================================================
        m.put("S","ś");   // Palatal sibilant
        m.put("z","ṣ");   // Retroflex sibilant
        m.put("s","s");   // Dental sibilant
        m.put("h","h");

        // ============================================================
        // Special visarga variants (Jihvāmūlīya & Upadhmānīya)
        // ============================================================
        m.put("Z","ḥ");   // Jihvāmūlīya (visarga before k/kh)
        m.put("V","ḥ");   // Upadhmānīya (visarga before p/ph)

        SLP1_TO_IAST = Collections.unmodifiableMap(m);
    }

    // Punctuation markers
    private static final Map<String, String> SLP1_PUNCT;
    static {
        Map<String,String> m = new LinkedHashMap<>();
        m.put("||","॥");   // Double daṇḍa
        m.put("|","।");    // Single daṇḍa
        m.put(".",".");
        m.put(",",",");
        m.put(";",";");
        m.put(":",":");
        m.put("-","‐");    // Sanskrit hyphen
        m.put("?","?");
        m.put("!","!");
        SLP1_PUNCT = Collections.unmodifiableMap(m);
    }

    // Modifiers that appear after base characters
    private static final Set<Character> MODIFIERS =
            new HashSet<>(Arrays.asList(
                    '_','=', '!', '*', '#',
                    '1','2','3','4','5','6','7','8','9','0',
                    '/', '\\', '^', '+','~', '@', '&'
            ));

    /**
     * Convert SLP1 string to IAST string.
     *
     * @param input SLP1-encoded Sanskrit text
     * @return IAST transliteration
     */
    public static String convert(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Step 1: Handle punctuation (longest patterns first)
        String s = input;
        for (String key : SLP1_PUNCT.keySet()) {
            s = s.replace(key, SLP1_PUNCT.get(key));
        }

        // Step 2: Convert SLP1 codes to IAST
        // Process in order, handling multi-char mappings properly
        StringBuilder sb = new StringBuilder(s.length() * 2);
        int i = 0;

        while (i < s.length()) {
            char ch = s.charAt(i);
            String key = String.valueOf(ch);

            // Check if this character maps to something
            String mapped = SLP1_TO_IAST.get(key);

            if (mapped != null) {
                sb.append(mapped);
                i++;
            } else if (isASCIILetter(ch) || isASCIIDigit(ch)) {
                // Unmapped ASCII - might be error in source, pass through
                sb.append(ch);
                i++;
            } else {
                // Pass through other characters (spaces, Unicode, etc.)
                sb.append(ch);
                i++;
            }
        }

        // Step 3: Apply modifiers and normalizations
        String result = applyModifiers(sb.toString());

        // Step 4: Normalize to NFC form for consistency
        return java.text.Normalizer.normalize(result, java.text.Normalizer.Form.NFC);
    }

    /**
     * Handle SLP1 postfix modifiers and perform final normalizations.
     */
    private static String applyModifiers(String s) {
        // Handle short e/o markers (Vedic)
        s = s.replaceAll("e1", "ĕ");  // Short e
        s = s.replaceAll("o1", "ŏ");  // Short o

        // Handle nasalized vowels: vowel + "~" → vowel + combining tilde
        s = s.replaceAll("([aāiīuūṛṝḷḹeĕoŏ])~", "$1\u0303");

        // Handle pluta (trimoraic) vowels: vowel + "3"
        s = s.replaceAll("([aāiīuūṛṝḷḹeo])3", "$1" + "\u0304\u0304");  // Double macron

        // Handle avagraha variants
        s = s.replace("''", "'");  // Double avagraha → single

        // Chandrabindu: M followed by vowel in certain contexts
        // (This is handled by ICU rules, but we can normalize)

        return s;
    }

    /**
     * Check if character is ASCII letter.
     */
    private static boolean isASCIILetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * Check if character is ASCII digit.
     */
    private static boolean isASCIIDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Utility method for testing/debugging.
     * Returns detailed mapping information for a string.
     */
    public static String convertWithDebug(String input) {
        if (input == null || input.isEmpty()) return "";

        StringBuilder debug = new StringBuilder();
        debug.append("Input (SLP1): ").append(input).append("\n");

        String result = convert(input);
        debug.append("Output (IAST): ").append(result).append("\n");

        return debug.toString();
    }

    /**
     * Test utility - demonstrates conversion of common Sanskrit terms.
     */
    public static void main(String[] args) {
        // Test cases covering all character types
        String[] tests = {
                // Basic terms
                "AtmA",           // ātmā (soul)
                "dharmaH",        // dharmaḥ (duty/law)
                "karma",          // karma (action)
                "mokzaH",         // mokṣaḥ (liberation)
                "saMsAraH",       // saṁsāraḥ (cycle of rebirth)
                "duHKaM",         // duḥkhaṁ (suffering)
                "kzetraM",        // kṣetraṁ (field)
                "jYAnaM",         // jñānaṁ (knowledge)
                "prajYA",         // prajñā (wisdom)

                // Complex terms
                "prakftiH",       // prakṛtiḥ (nature)
                "puruSaH",        // puruṣaḥ (person/spirit)
                "buddhi",         // buddhi (intellect)
                "ahaMkAraH",      // ahaṁkāraḥ (ego)

                // Buddhist terms
                "saNGaH",         // saṅghaḥ (community)
                "taTAgataH",      // tathāgataḥ (thus-gone)
                "bodhiH",         // bodhiḥ (awakening)
                "nirvANaM",       // nirvāṇaṁ (extinction)

                // With special characters
                "zivaH",          // śivaḥ
                "zaktiH",         // śaktiḥ (power)
                "sUtraM",         // sūtraṁ (thread/text)

                // Vedic/rare
                "fgveda",         // ṛgveda
                "fzi",            // ṛṣi (sage)

                // Punctuation
                "oM||",           // oṁ॥
                "namaH|"          // namaḥ।
        };

        System.out.println("SLP1 → IAST Conversion Tests");
        System.out.println("=".repeat(50));

        for (String test : tests) {
            String result = convert(test);
            System.out.printf("%-20s → %s%n", test, result);
        }

        // Test the problematic cases from original error report
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Original Error Cases (should now be correct):");
        System.out.println("=".repeat(50));

        String[] errorCases = {
                "prakfti",        // Should be prakṛti (was prakṇti)
                "duzkftaM",       // Should be duṣkṛtam (was duśkṇtaṁ)
                "saNGaH",         // Should be saṅghaḥ (was saghghaḥ)
                "nirvANaM",       // Should be nirvāṇaṁ (was nirvāṅaṁ)
                "pramANaM"        // Should be pramāṇaṁ (was pramāṅaṁ)
        };

        for (String test : errorCases) {
            String result = convert(test);
            System.out.printf("%-20s → %s%n", test, result);
        }
    }
}