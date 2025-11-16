package com.example.glossariobuda;

import java.text.Normalizer;

/**
 * Utility class for handling diacritic normalization in searches.
 * Allows searching for "mahayana" to find "mahāyāna", etc.
 */
public class DiacriticUtils {

    /**
     * Remove diacritics from a string for comparison purposes.
     * Converts "mahāyāna" to "mahayana", "śamatha" to "samatha", etc.
     * Also handles special Sanskrit/Pali characters that don't decompose normally.
     *
     * @param text The text to normalize
     * @return Text without diacritics
     */
    public static String removeDiacritics(String text) {
        if (text == null) {
            return null;
        }

        // First, handle special characters that might not decompose properly
        String result = text;

        // Sanskrit/Pali consonants with diacritics
        result = result.replace("ś", "s").replace("Ś", "S");
        result = result.replace("ṣ", "s").replace("Ṣ", "S");
        result = result.replace("ṭ", "t").replace("Ṭ", "T");
        result = result.replace("ḍ", "d").replace("Ḍ", "D");
        result = result.replace("ṇ", "n").replace("Ṇ", "N");
        result = result.replace("ñ", "n").replace("Ñ", "N");
        result = result.replace("ṅ", "n").replace("Ṅ", "N");
        result = result.replace("ṃ", "m").replace("Ṃ", "M");
        result = result.replace("ṁ", "m").replace("Ṁ", "M");
        result = result.replace("ḥ", "h").replace("Ḥ", "H");
        result = result.replace("ḷ", "l").replace("Ḷ", "L");
        result = result.replace("ḹ", "l").replace("Ḹ", "L");
        result = result.replace("ṛ", "r").replace("Ṛ", "R");
        result = result.replace("ṝ", "r").replace("Ṝ", "R");

        // Use NFD (Canonical Decomposition) to separate base characters from remaining diacritics
        String normalized = Normalizer.normalize(result, Normalizer.Form.NFD);

        // Remove all diacritic marks (combining characters)
        // \p{M} matches any Unicode mark character (accents, tildes, etc.)
        return normalized.replaceAll("\\p{M}", "");
    }

    /**
     * Check if two strings match when diacritics are ignored.
     * Case-insensitive comparison.
     *
     * @param text1 First text
     * @param text2 Second text
     * @return true if texts match without diacritics
     */
    public static boolean matchesWithoutDiacritics(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return false;
        }

        String normalized1 = removeDiacritics(text1).toLowerCase();
        String normalized2 = removeDiacritics(text2).toLowerCase();

        return normalized1.equals(normalized2);
    }

    /**
     * Check if text contains pattern when diacritics are ignored.
     * Case-insensitive search.
     *
     * @param text The text to search in
     * @param pattern The pattern to search for
     * @return true if pattern is found without considering diacritics
     */
    public static boolean containsWithoutDiacritics(String text, String pattern) {
        if (text == null || pattern == null) {
            return false;
        }

        String normalizedText = removeDiacritics(text).toLowerCase();
        String normalizedPattern = removeDiacritics(pattern).toLowerCase();

        return normalizedText.contains(normalizedPattern);
    }
}
