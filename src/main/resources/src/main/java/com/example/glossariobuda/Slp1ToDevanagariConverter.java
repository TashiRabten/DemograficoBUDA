package com.example.glossariobuda;

import java.util.*;

/**
 * Direct SLP1 to Devanagari converter following the Cologne Digital Sanskrit Lexicon pattern.
 *
 * This implementation replicates the FSM (Finite State Machine) approach from mw72web1/web/utilities/transcoder/slp1_deva.xml
 * The web application uses this exact approach for converting Monier-Williams dictionary entries.
 *
 * States:
 * - INIT: Start of word or after vowel (outputs standalone vowel characters)
 * - SKT: After consonant (outputs vowel matras/diacritics)
 *
 * Based on: https://www.sanskrit-lexicon.uni-koeln.de/
 */
public class Slp1ToDevanagariConverter {

    private enum State {
        INIT,  // Initial state or after vowel
        SKT    // After consonant
    }

    // SLP1 vowel characters for lookahead checking
    private static final Set<Character> SLP1_VOWELS = new HashSet<>(Arrays.asList(
            'a', 'A', 'i', 'I', 'u', 'U', 'f', 'F', 'x', 'X', 'e', 'E', 'o', 'O'
    ));

    /**
     * Convert SLP1 text directly to Devanagari.
     *
     * @param slp1 Input text in SLP1 encoding
     * @return Devanagari text
     */
    public static String convert(String slp1) {
        if (slp1 == null || slp1.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        State state = State.INIT;
        int i = 0;

        while (i < slp1.length()) {
            String processed = null;
            int charsConsumed = 0;

            // Try two-character patterns first (longer matches take precedence)
            if (i + 1 < slp1.length()) {
                String twoChar = slp1.substring(i, i + 2);
                processed = processTwoChar(twoChar, state);
                if (processed != null) {
                    charsConsumed = 2;
                    state = getNextState(twoChar);
                }
            }

            // Try single character
            if (processed == null) {
                char ch = slp1.charAt(i);
                processed = processOneChar(ch, state, i, slp1);
                if (processed != null) {
                    charsConsumed = 1;
                    state = getNextState(String.valueOf(ch));
                }
            }

            if (processed != null) {
                result.append(processed);
                i += charsConsumed;
            } else {
                // Unknown character, pass through and reset to INIT
                result.append(slp1.charAt(i));
                state = State.INIT;
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Process two-character SLP1 sequences
     */
    private static String processTwoChar(String s, State state) {
        // Double danda
        if (s.equals("..")) {
            return "\u0965";  // ॥
        }

        // Accent combinations (from XML lines 153-158)
        switch (s) {
            case "\\H": return "\u0903\u0952";  // visarga + anudatta
            case "\\M": return "\u0902\u0952";  // anusvara + anudatta
            case "/H": return "\u0903\u0951";   // visarga + udatta
            case "/M": return "\u0902\u0951";   // anusvara + udatta
            case "^H": return "\u0903\u1ce0";   // visarga + svarita
            case "^M": return "\u0902\u1ce0";   // anusvara + svarita
            case "o~": return "\u0950";         // OM symbol
        }

        return null;
    }

    /**
     * Process single character SLP1 codes
     * Based on slp1_deva.xml from the Cologne Digital Sanskrit Lexicon
     */
    private static String processOneChar(char ch, State state, int pos, String input) {

        // Vowels - depend on state
        switch (ch) {
            case 'a':
                return (state == State.INIT) ? "\u0905" : "";  // अ or inherent 'a' (nothing)
            case 'A':
                return (state == State.INIT) ? "\u0906" : "\u093e";  // आ or ा
            case 'i':
                return (state == State.INIT) ? "\u0907" : "\u093f";  // इ or ि
            case 'I':
                return (state == State.INIT) ? "\u0908" : "\u0940";  // ई or ी
            case 'u':
                return (state == State.INIT) ? "\u0909" : "\u0941";  // उ or ु
            case 'U':
                return (state == State.INIT) ? "\u090a" : "\u0942";  // ऊ or ू
            case 'f':  // Vocalic R (ṛ)
                return (state == State.INIT) ? "\u090b" : "\u0943";  // ऋ or ृ
            case 'F':  // Long vocalic R (ṝ)
                return (state == State.INIT) ? "\u0960" : "\u0944";  // ॠ or ॄ
            case 'x':  // Vocalic L (ḷ)
                return (state == State.INIT) ? "\u090c" : "\u0962";  // ऌ or ॢ
            case 'X':  // Long vocalic L (ḹ)
                return (state == State.INIT) ? "\u0961" : "\u0963";  // ॡ or ॣ
            case 'e':
                return (state == State.INIT) ? "\u090f" : "\u0947";  // ए or े
            case 'E':  // ai
                return (state == State.INIT) ? "\u0910" : "\u0948";  // ऐ or ै
            case 'o':
                return (state == State.INIT) ? "\u0913" : "\u094b";  // ओ or ो
            case 'O':  // au
                return (state == State.INIT) ? "\u0914" : "\u094c";  // औ or ौ
        }

        // Anusvara, Visarga, Avagraha (lines 111-115)
        switch (ch) {
            case 'M':
                return "\u0902";  // ं (anusvara)
            case 'H':
                return "\u0903";  // ः (visarga)
            case '\'':
                return "\u093d";  // ऽ (avagraha)
        }

        // Consonants - need lookahead to determine if virama is needed
        String consonant = getConsonant(ch);
        if (consonant != null) {
            boolean needsVirama = !isFollowedByVowel(pos, input);
            return needsVirama ? consonant + "\u094d" : consonant;
        }

        // Punctuation and special characters
        switch (ch) {
            case '.':
                return "\u0964";  // । (danda)
            case '~':
                return "\u0901";  // ँ (candrabindu/anunasika)
            case '|':
                return "\u0933\u094d\u0939";  // ळ्ह (special conjunct from line 104)
            case 'Z':
                return "\u1cf2";  // Jihvamuliya (line 150)
            case 'V':
                return "\u1cf2";  // Upadhmaniya (line 151)
        }

        // Devanagari digits (0-9) - lines 116-125
        if (ch >= '0' && ch <= '9') {
            return String.valueOf((char)('\u0966' + (ch - '0')));
        }

        // Whitespace and other characters pass through (lines 126-131)
        if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '-') {
            return String.valueOf(ch);
        }

        // Accents (Vedic) - lines 133-137
        switch (ch) {
            case '\\':
                return "\u0952";  // Anudatta accent
            case '/':
                return "\u0951";  // Udatta accent
            case '^':
                return "\u1ce0";  // Svarita accent
        }

        // Special spacing candrabindu (line 161)
        if (ch == '£') {
            return "\ua8f2";
        }

        return null;  // Unknown character
    }

    /**
     * Get Devanagari consonant (without virama)
     * Maps SLP1 consonant codes to Devanagari base consonants
     * Based on slp1_deva.xml lines 39-109
     */
    private static String getConsonant(char ch) {
        switch (ch) {
            // Velars (ka-varga) - lines 74-78
            case 'k': return "\u0915";  // क
            case 'K': return "\u0916";  // ख
            case 'g': return "\u0917";  // ग
            case 'G': return "\u0918";  // घ
            case 'N': return "\u0919";  // ङ

            // Palatals (ca-varga) - lines 79-83
            case 'c': return "\u091a";  // च
            case 'C': return "\u091b";  // छ
            case 'j': return "\u091c";  // ज
            case 'J': return "\u091d";  // झ
            case 'Y': return "\u091e";  // ञ

            // Retroflexes (ṭa-varga) - lines 84-88
            case 'w': return "\u091f";  // ट
            case 'W': return "\u0920";  // ठ
            case 'q': return "\u0921";  // ड
            case 'Q': return "\u0922";  // ढ
            case 'R': return "\u0923";  // ण

            // Dentals (ta-varga) - lines 89-93
            case 't': return "\u0924";  // त
            case 'T': return "\u0925";  // थ
            case 'd': return "\u0926";  // द
            case 'D': return "\u0927";  // ध
            case 'n': return "\u0928";  // न

            // Labials (pa-varga) - lines 94-98
            case 'p': return "\u092a";  // प
            case 'P': return "\u092b";  // फ
            case 'b': return "\u092c";  // ब
            case 'B': return "\u092d";  // भ
            case 'm': return "\u092e";  // म

            // Semivowels - lines 99-102
            case 'y': return "\u092f";  // य
            case 'r': return "\u0930";  // र
            case 'l': return "\u0932";  // ल
            case 'L': return "\u0933";  // ळ (retroflex lateral)

            // Semivowel v - line 105
            case 'v': return "\u0935";  // व

            // Sibilants - lines 106-108
            case 'S': return "\u0936";  // श (palatal)
            case 'z': return "\u0937";  // ष (retroflex)
            case 's': return "\u0938";  // स (dental)

            // Aspirate - line 109
            case 'h': return "\u0939";  // ह

            default: return null;
        }
    }

    /**
     * Check if position is followed by a vowel in SLP1
     * This determines whether a consonant needs virama
     *
     * Based on the regex pattern from slp1_deva.xml line 39:
     * /^([^aAiIuUfFxXeEoO^/\\])
     *
     * A consonant needs virama if NOT followed by a vowel or accent
     */
    private static boolean isFollowedByVowel(int pos, String input) {
        if (pos + 1 >= input.length()) {
            return false;  // End of string, needs virama
        }

        char next = input.charAt(pos + 1);

        // Check if next character is a vowel or accent
        // Vowels: aAiIuUfFxXeEoO
        // Accents: ^/\ (from the regex pattern)
        return SLP1_VOWELS.contains(next) || next == '^' || next == '/' || next == '\\';
    }

    /**
     * Determine next state based on what character was just processed
     */
    private static State getNextState(String input) {
        if (input == null || input.isEmpty()) {
            return State.INIT;
        }

        char first = input.charAt(0);

        // After consonant, go to SKT state
        if (getConsonant(first) != null) {
            return State.SKT;
        }

        // After vowel, M, H, or other characters, go to INIT state
        return State.INIT;
    }

    /**
     * Test the converter with common Sanskrit terms
     */
    public static void main(String[] args) {
        String[] tests = {
                // Basic terms (SLP1 format from MW72)
                "AtmA",           // ātman (soul)
                "Darma",          // dharma (duty)
                "karma",          // karma (action)
                "mokza",          // mokṣa (liberation)
                "saMsAra",        // saṁsāra (cycle)
                "duHKa",          // duḥkha (suffering)
                "kzetra",         // kṣetra (field)
                "jYAna",          // jñāna (knowledge)
                "prajYA",         // prajñā (wisdom)
                "prakfti",        // prakṛti (nature)
                "puruza",         // puruṣa (person)
                "budDi",          // buddhi (intellect)
                "ahaMkAra",       // ahaṁkāra (ego)
                "saNGa",          // saṅgha (community)
                "taTAgata",       // tathāgata
                "boDi",           // bodhi
                "nirvANa",        // nirvāṇa
                "Siva",           // śiva
                "Sakti",          // śakti
                "sUtra",          // sūtra
                "fgveda",         // ṛgveda
                "fzi",            // ṛṣi (sage)
                "namaH",          // namaḥ
                "oM"              // oṁ
        };

        System.out.println("SLP1 → Devanāgarī Direct Conversion Test");
        System.out.println("(Based on Cologne Digital Sanskrit Lexicon rules)");
        System.out.println("=".repeat(60));

        for (String test : tests) {
            String result = convert(test);
            System.out.printf("%-20s → %s%n", test, result);
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Testing problematic cases:");
        System.out.println("=".repeat(60));

        String[] problemCases = {
                "prakfti",        // Should be प्रकृति
                "duzkfta",        // Should be दुष्कृत
                "saNGa",          // Should be सङ्घ
                "nirvANa",        // Should be निर्वाण
                "pramANa"         // Should be प्रमाण
        };

        for (String test : problemCases) {
            String result = convert(test);
            System.out.printf("%-20s → %s%n", test, result);
        }
    }
}
