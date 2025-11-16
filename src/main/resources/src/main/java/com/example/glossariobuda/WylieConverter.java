package com.example.glossariobuda;

import io.bdrc.ewtsconverter.EwtsConverter;

/**
 * Utility class for converting between Wylie transliteration and Tibetan Unicode
 * Uses the BDRC (Buddhist Digital Resource Center) EWTS Converter library
 *
 * Thread-safe: Multiple threads can share the same instance
 */
public class WylieConverter {

    private static final EwtsConverter converter = new EwtsConverter();

    /**
     * Convert Extended Wylie Transliteration System (EWTS) to Tibetan Unicode
     *
     * @param wylie Wylie transliteration (e.g., "bka' drin che")
     * @return Tibetan Unicode (e.g., "བཀའ་དྲིན་ཆེ།")
     */
    public static String toUnicode(String wylie) {
        if (wylie == null || wylie.trim().isEmpty()) {
            return "";
        }

        try {
            return converter.toUnicode(wylie.trim());
        } catch (Exception e) {
            System.err.println("[WylieConverter] Failed to convert Wylie to Unicode: " + wylie);
            System.err.println("[WylieConverter] Error: " + e.getMessage());
            return wylie; // Return original if conversion fails
        }
    }

    /**
     * Convert Tibetan Unicode to Extended Wylie Transliteration System (EWTS)
     *
     * @param unicode Tibetan Unicode (e.g., "བཀའ་དྲིན་ཆེ།")
     * @return Wylie transliteration (e.g., "bka' drin che")
     */
    public static String toWylie(String unicode) {
        if (unicode == null || unicode.trim().isEmpty()) {
            return "";
        }

        try {
            return converter.toWylie(unicode.trim());
        } catch (Exception e) {
            System.err.println("[WylieConverter] Failed to convert Unicode to Wylie: " + unicode);
            System.err.println("[WylieConverter] Error: " + e.getMessage());
            return unicode; // Return original if conversion fails
        }
    }

    /**
     * Check if a string is likely Wylie transliteration
     * Simple heuristic: contains Latin characters with apostrophes or no Tibetan Unicode
     *
     * @param text Text to check
     * @return true if likely Wylie, false otherwise
     */
    public static boolean isWylie(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Check if contains Tibetan Unicode characters (U+0F00 to U+0FFF)
        boolean hasTibetan = text.codePoints()
            .anyMatch(cp -> cp >= 0x0F00 && cp <= 0x0FFF);

        if (hasTibetan) {
            return false; // Already has Tibetan Unicode
        }

        // Check if contains typical Wylie patterns (apostrophes, Latin letters)
        boolean hasLatinLetters = text.codePoints()
            .anyMatch(cp -> (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z'));

        return hasLatinLetters; // Wylie uses Latin letters
    }

    /**
     * Convert Wylie to Unicode only if the input is Wylie
     * Safe conversion that checks first
     *
     * @param text Text that might be Wylie
     * @return Unicode if Wylie, original text otherwise
     */
    public static String convertIfWylie(String text) {
        if (isWylie(text)) {
            return toUnicode(text);
        }
        return text;
    }
}
