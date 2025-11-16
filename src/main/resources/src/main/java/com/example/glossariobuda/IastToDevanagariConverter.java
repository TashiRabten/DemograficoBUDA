package com.example.glossariobuda;

import java.util.*;

/**
 * IAST to Devanāgarī converter following the Cologne Digital Sanskrit Lexicon pattern.
 *
 * This implementation mimics the state machine approach used in slp1_deva.xml:
 * - INIT state: Start of word or after vowel (outputs standalone vowels)
 * - SKT state: After consonant (outputs vowel matras)
 *
 * Based on the proven XML transliteration rules from sanskrit-lexicon.uni-koeln.de
 */
public class IastToDevanagariConverter {

    // State enum to track whether we're after a consonant
    private enum State {
        INIT,  // Start of word or after vowel
        SKT    // After consonant
    }

    // Vowel set for lookahead checking
    private static final Set<Character> VOWELS = new HashSet<>(Arrays.asList(
            'a', 'ā', 'i', 'ī', 'u', 'ū', 'ṛ', 'ṝ', 'ḷ', 'ḹ', 'e', 'o',
            'A', 'I', 'U'  // Allow uppercase variants
    ));

    /**
     * Convert IAST text to Devanāgarī.
     *
     * @param iast Input text in IAST romanization
     * @return Devanāgarī text
     */
    public static String convert(String iast) {
        if (iast == null || iast.isEmpty()) {
            return "";
        }

        // Normalize to NFC form
        String normalized = java.text.Normalizer.normalize(iast, java.text.Normalizer.Form.NFC);

        StringBuilder result = new StringBuilder();
        State state = State.INIT;
        int i = 0;

        while (i < normalized.length()) {
            String processed = null;
            int charsConsumed = 0;

            // Try multi-character patterns first (longest match)
            if (i + 2 < normalized.length()) {
                String threeChar = normalized.substring(i, i + 3);
                processed = processThreeChar(threeChar, state);
                if (processed != null) {
                    charsConsumed = 3;
                    state = getNextState(threeChar, processed);
                }
            }

            if (processed == null && i + 1 < normalized.length()) {
                String twoChar = normalized.substring(i, i + 2);
                processed = processTwoChar(twoChar, state);
                if (processed != null) {
                    charsConsumed = 2;
                    state = getNextState(twoChar, processed);
                }
            }

            if (processed == null) {
                char ch = normalized.charAt(i);
                processed = processOneChar(ch, state, i, normalized);
                charsConsumed = 1;
                state = getNextState(String.valueOf(ch), processed);
            }

            if (processed != null) {
                result.append(processed);
            }
            i += charsConsumed;
        }

        return result.toString();
    }

    /**
     * Process three-character sequences (e.g., kṣṇ, ṣṭr)
     */
    private static String processThreeChar(String s, State state) {
        switch (s) {
            // Special conjuncts
            case "kṣṇ": return "क्ष्ण";
            case "ṣṭr": return "ष्ट्र";
            case "str": return "स्त्र";
            case "ttr": return "त्त्र";
            case "ddr": return "द्द्र";
            case "ṅkh": return "ङ्ख";
            case "ṅgh": return "ङ्घ";
            case "ñch": return "ञ्छ";
            case "ñjh": return "ञ्झ";
            case "ṇṭh": return "ण्ठ";
            case "ṇḍh": return "ण्ढ";
            case "ndh": return "न्ध";
            case "nth": return "न्थ";
            case "mph": return "म्फ";
            case "mbh": return "म्भ";
            case "ghr": return "घ्र";
            case "khr": return "ख्र";
            case "phr": return "फ्र";
            case "bhr": return "भ्र";
            case "sth": return "स्थ";
            case "skh": return "स्ख";
            case "ṣṭh": return "ष्ठ";
            case "dhv": return "ध्व";
            case "dhy": return "ध्य";
            case "dhm": return "ध्म";
            case "dhn": return "ध्न";
            case "chv": return "छ्व";
            case "kkh": return "क्ख";
            case "ggh": return "ग्घ";
            case "cch": return "च्छ";
            case "jjh": return "ज्झ";
            case "ṭṭh": return "ट्ठ";
            case "ḍḍh": return "ड्ढ";
            case "tth": return "त्थ";
            case "ddh": return "द्ध";
            case "nnh": return "न्ह";
            case "pph": return "प्फ";
            case "bbh": return "ब्भ";
            default: return null;
        }
    }

    /**
     * Process two-character sequences (aspirated consonants, diphthongs, conjuncts)
     */
    private static String processTwoChar(String s, State state) {
        // Diphthongs (vowels)
        if (s.equals("ai")) {
            return state == State.INIT ? "ऐ" : "ै";
        }
        if (s.equals("au")) {
            return state == State.INIT ? "औ" : "ौ";
        }

        // Aspirated consonants
        switch (s) {
            case "kh": return "ख्";
            case "gh": return "घ्";
            case "ch": return "छ्";
            case "jh": return "झ्";
            case "ṭh": return "ठ्";
            case "ḍh": return "ढ्";
            case "th": return "थ्";
            case "dh": return "ध्";
            case "ph": return "फ्";
            case "bh": return "भ्";
        }

        // Common conjuncts
        switch (s) {
            case "kṣ": return "क्ष";
            case "jñ": return "ज्ञ";
            case "ṅk": return "ङ्क";
            case "ṅg": return "ङ्ग";
            case "ñc": return "ञ्च";
            case "ñj": return "ञ्ज";
            case "ṇṭ": return "ण्ट";
            case "ṇḍ": return "ण्ड";
            case "tr": return "त्र";
            case "dr": return "द्र";
            case "gr": return "ग्र";
            case "kr": return "क्र";
            case "pr": return "प्र";
            case "br": return "ब्र";
            case "śr": return "श्र";
            case "ṣr": return "ष्र";
            case "sr": return "स्र";
            case "hr": return "ह्र";
            case "st": return "स्त";
            case "sk": return "स्क";
            case "sp": return "स्प";
            case "sv": return "स्व";
            case "sm": return "स्म";
            case "sn": return "स्न";
            case "sy": return "स्य";
            case "ṣṭ": return "ष्ट";
            case "ṣp": return "ष्प";
            case "ṣṇ": return "ष्ण";
            case "tv": return "त्व";
            case "ty": return "त्य";
            case "dv": return "द्व";
            case "dy": return "द्य";
            case "nv": return "न्व";
            case "ny": return "न्य";
            case "nd": return "न्द";
            case "nt": return "न्त";
            case "mp": return "म्प";
            case "mb": return "म्ब";
            case "hn": return "ह्न";
            case "hm": return "ह्म";
            case "hṇ": return "ह्ण";
            case "hy": return "ह्य";
            case "hl": return "ह्ल";
            case "hv": return "ह्व";
            case "py": return "प्य";
            case "by": return "ब्य";
            case "my": return "म्य";
            case "vy": return "व्य";
            case "śy": return "श्य";
            case "ly": return "ल्य";
            case "kk": return "क्क";
            case "gg": return "ग्ग";
            case "cc": return "च्च";
            case "jj": return "ज्ज";
            case "ṭṭ": return "ट्ट";
            case "ḍḍ": return "ड्ड";
            case "tt": return "त्त";
            case "dd": return "द्द";
            case "nn": return "न्न";
            case "pp": return "प्प";
            case "bb": return "ब्ब";
            case "mm": return "म्म";
            case "yy": return "य्य";
            case "ll": return "ल्ल";
            case "vv": return "व्व";
            case "śś": return "श्श";
            case "ṣṣ": return "ष्ष";
            case "ss": return "स्स";
            case "hh": return "ह्";
        }

        return null;
    }

    /**
     * Process single characters (vowels and single consonants)
     */
    private static String processOneChar(char ch, State state, int pos, String input) {
        // Check if consonant needs virama (not followed by vowel)
        boolean needsVirama = false;
        if (isConsonant(ch)) {
            needsVirama = !isFollowedByVowel(pos, input);
        }

        // Vowels
        switch (ch) {
            case 'a':
                if (state == State.INIT) return "अ";
                else return "";  // Inherent 'a' - output nothing
            case 'ā':
            case 'A':
                return state == State.INIT ? "आ" : "ा";
            case 'i':
                return state == State.INIT ? "इ" : "ि";
            case 'ī':
            case 'I':
                return state == State.INIT ? "ई" : "ी";
            case 'u':
                return state == State.INIT ? "उ" : "ु";
            case 'ū':
            case 'U':
                return state == State.INIT ? "ऊ" : "ू";
            case 'ṛ':
                return state == State.INIT ? "ऋ" : "ृ";
            case 'ṝ':
                return state == State.INIT ? "ॠ" : "ॄ";
            case 'ḷ':
                return state == State.INIT ? "ऌ" : "ॢ";
            case 'ḹ':
                return state == State.INIT ? "ॡ" : "ॣ";
            case 'e':
                return state == State.INIT ? "ए" : "े";
            case 'o':
                return state == State.INIT ? "ओ" : "ो";
        }

        // Consonants - output with virama if needed
        String consonant = getConsonant(ch);
        if (consonant != null) {
            return needsVirama ? consonant + "\u094d" : consonant;
        }

        // Anusvara, visarga, avagraha
        switch (ch) {
            case 'ṃ':
            case 'ṁ': return "ं";
            case 'ḥ': return "ः";
            case '\'': return "ऽ";
        }

        // Punctuation
        if (ch == '|') return "।";

        // Numbers
        if (ch >= '0' && ch <= '9') {
            return String.valueOf((char)('\u0966' + (ch - '0')));
        }

        // Pass through spaces and other characters
        return String.valueOf(ch);
    }

    /**
     * Get Devanāgarī consonant (without virama)
     */
    private static String getConsonant(char ch) {
        switch (ch) {
            case 'k': return "क";
            case 'g': return "ग";
            case 'ṅ': return "ङ";
            case 'c': return "च";
            case 'j': return "ज";
            case 'ñ': return "ञ";
            case 'ṭ': return "ट";
            case 'ḍ': return "ड";
            case 'ṇ': return "ण";
            case 't': return "त";
            case 'd': return "द";
            case 'n': return "न";
            case 'p': return "प";
            case 'b': return "ब";
            case 'm': return "म";
            case 'y': return "य";
            case 'r': return "र";
            case 'l': return "ल";
            case 'v': return "व";
            case 'ś': return "श";
            case 'ṣ': return "ष";
            case 's': return "स";
            case 'h': return "ह";
            default: return null;
        }
    }

    /**
     * Check if character is a consonant
     */
    private static boolean isConsonant(char ch) {
        return getConsonant(ch) != null;
    }

    /**
     * Check if position is followed by a vowel
     */
    private static boolean isFollowedByVowel(int pos, String input) {
        if (pos + 1 >= input.length()) return false;

        char next = input.charAt(pos + 1);

        // Check for diphthongs
        if (pos + 2 < input.length()) {
            String twoChar = input.substring(pos + 1, pos + 3);
            if (twoChar.equals("ai") || twoChar.equals("au")) {
                return true;
            }
        }

        return VOWELS.contains(next);
    }

    /**
     * Determine next state based on what was just processed
     */
    private static State getNextState(String input, String output) {
        // If we just output a consonant (with or without virama), go to SKT state
        if (output != null && !output.isEmpty()) {
            char firstOut = output.charAt(0);
            // Check if output starts with Devanagari consonant range
            if (firstOut >= '\u0915' && firstOut <= '\u0939') {
                return State.SKT;
            }
        }

        // Otherwise return to INIT
        return State.INIT;
    }

    /**
     * Test the converter
     */
    public static void main(String[] args) {
        String[] tests = {
                "ātmā",
                "dharmaḥ",
                "karma",
                "saṁsāraḥ",
                "duḥkham",
                "prakṛti",
                "puruṣaḥ",
                "jñānam",
                "kṣetraṁ",
                "buddhiḥ"
        };

        System.out.println("IAST → Devanāgarī Test");
        System.out.println("=".repeat(50));

        for (String test : tests) {
            String result = convert(test);
            System.out.printf("%-20s → %s%n", test, result);
        }
    }
}