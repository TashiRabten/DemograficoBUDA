package com.example.glossariobuda;

import java.util.Set;

/**
 * Utility class for detecting languages in glossary terms
 * Handles ambiguous cases like romanized Sanskrit vs English
 */
public class LanguageDetector {

    /**
     * Detect language from text content using multi-tier approach
     */
    public static String detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        char firstChar = text.charAt(0);

        // TIER 1: Unicode script detection (highest confidence)

        // Tibetan: U+0F00 to U+0FFF
        if (firstChar >= '\u0F00' && firstChar <= '\u0FFF') {
            return "Tibetan";
        }

        // Thai: U+0E00 to U+0E7F
        if (firstChar >= '\u0E00' && firstChar <= '\u0E7F') {
            return "Thai";
        }

        // Chinese/CJK: U+4E00 to U+9FFF and U+3400 to U+4DBF
        if ((firstChar >= '\u4E00' && firstChar <= '\u9FFF') ||
                (firstChar >= '\u3400' && firstChar <= '\u4DBF')) {
            return "Chinese";
        }

        // Mongolian: U+1800 to U+18AF
        if (firstChar >= '\u1800' && firstChar <= '\u18AF') {
            return "Mongolian";
        }

        // Devanagari (Sanskrit): U+0900 to U+097F
        if (firstChar >= '\u0900' && firstChar <= '\u097F') {
            return "Sanskrit";
        }

        // TIER 2: For Latin script, check for Sanskrit diacritics (very high confidence)
        if (hasSanskritDiacritics(text)) {
            return "Sanskrit";
        }

        // TIER 3: Dictionary lookup (high confidence)
        String dictResult = checkDictionary(text);
        if (!dictResult.isEmpty()) {
            return dictResult;
        }

        // TIER 4: Pattern matching (medium confidence)
        if (hasSanskritPatterns(text)) {
            return "Sanskrit";
        }

        // TIER 5: Statistical analysis (lower confidence)
        if (isLikelySanskrit(text)) {
            return "Sanskrit";
        }

        // TIER 6: Default to English for Latin script
        if ((firstChar >= 'A' && firstChar <= 'Z') ||
                (firstChar >= 'a' && firstChar <= 'z')) {
            return "English";
        }

        return "";
    }

    /**
     * Check for Sanskrit-specific diacritics used in IAST/ISO 15919 transliteration
     */
    public static boolean hasSanskritDiacritics(String text) {
        // Sanskrit uses these special characters that English never does
        return text.contains("ā") || text.contains("ī") || text.contains("ū") ||
                text.contains("ṛ") || text.contains("ṝ") || text.contains("ḷ") || text.contains("ḹ") ||
                text.contains("ṅ") || text.contains("ñ") || text.contains("ṭ") || text.contains("ḍ") ||
                text.contains("ṇ") || text.contains("ś") || text.contains("ṣ") || text.contains("ḥ") ||
                text.contains("ṃ") || text.contains("ḻ");
    }

    /**
     * Check for common Sanskrit syllable patterns and word endings
     */
    public static boolean hasSanskritPatterns(String text) {
        String lower = text.toLowerCase();

        // Common Sanskrit word endings
        if (lower.endsWith("ah") || lower.endsWith("am") || lower.endsWith("as") ||
                lower.endsWith("āh") || lower.endsWith("ām") || lower.endsWith("ās") ||
                lower.endsWith("aḥ") || lower.endsWith("āḥ")) {
            return true;
        }

        // Common Sanskrit prefixes - but make sure it's not just an English word
        if ((lower.startsWith("pra") || lower.startsWith("upa") ||
                lower.startsWith("sam") || lower.startsWith("vi") ||
                lower.startsWith("anu") || lower.startsWith("ava") ||
                lower.startsWith("ni") || lower.startsWith("pari")) &&
                text.length() > 5 && !isCommonEnglishWord(lower)) {
            return true;
        }

        // Sanskrit consonant clusters that are rare in English
        if (lower.contains("dhya") || lower.contains("ksha") || lower.contains("jña") ||
                lower.contains("stha") || lower.contains("bhy") || lower.contains("kshe") ||
                lower.contains("kshu")) {
            return true;
        }

        // Double consonants common in Sanskrit but rare in English at word start
        return lower.matches("^(ks|pt|bd|dh|bh|ph|kh|gh|th|sh|ch|jh).*");
    }

    /**
     * Heuristic: Is this likely Sanskrit based on overall characteristics?
     */
    public static boolean isLikelySanskrit(String text) {
        if (text.length() < 4) {
            return false; // Too short to determine
        }

        String lower = text.toLowerCase();

        // Count Sanskrit indicators
        int sanskritScore = 0;

        // Long words with many vowels (common in Sanskrit)
        if (text.length() > 8) {
            long vowelCount = lower.chars().filter(c -> "aeiouāīūṛṝḷḹ".indexOf(c) >= 0).count();
            double vowelRatio = (double) vowelCount / text.length();
            if (vowelRatio > 0.4) {
                sanskritScore++;
            }
        }

        // Presence of 'h' after consonants (bh, dh, ph, kh, etc.)
        if (lower.matches(".*[bpdtkgc]h.*")) {
            sanskritScore++;
        }

        // Ends with 'a' or 'am' (very common in Sanskrit)
        if (lower.endsWith("a") && !lower.endsWith("ia") && !lower.endsWith("ea")) {
            sanskritScore++;
        }

        // Contains multiple 'a' vowels
        if (lower.chars().filter(c -> c == 'a').count() >= 3) {
            sanskritScore++;
        }

        // No common English suffixes
        if (!lower.matches(".*(ing|ed|tion|ness|ment|ly|ity|able|ible|ful|less)$")) {
            sanskritScore++;
        }

        // Decision: 3 or more indicators suggests Sanskrit
        return sanskritScore >= 3;
    }

    /**
     * Check against known Sanskrit/English terms
     */
    public static String checkDictionary(String text) {
        String lower = text.toLowerCase();

        // Common Sanskrit terms that might be in your glossary
        Set<String> knownSanskrit = Set.of(
                "dharma", "karma", "buddha", "bodhisattva", "sutra", "sūtra",
                "nirvana", "nirvāṇa", "samsara", "saṃsāra", "bodhi",
                "maitri", "maitrī", "karuna", "karuṇā", "prajna", "prajñā",
                "upaya", "upāya", "samadhi", "samādhi", "dhyana", "dhyāna",
                "vinaya", "śīla", "sila", "sangha", "saṅgha",
                "stupa", "stūpa", "mandala", "maṇḍala", "mantra",
                "tantra", "vajra", "vidya", "vidyā", "arhat", "arhant",
                "tathagata", "tathāgata", "mahāyāna", "mahayana",
                "hīnayāna", "hinayana", "theravada", "theravāda"
        );

        if (knownSanskrit.contains(lower)) {
            return "Sanskrit";
        }

        // Common English words that might look Sanskrit-like
        Set<String> knownEnglish = Set.of(
                "drama", "cinema", "banana", "data", "visa", "sofa",
                "tuna", "mega", "arena", "area", "idea", "mantra"
        );

        if (knownEnglish.contains(lower)) {
            return "English";
        }

        return "";
    }

    /**
     * Check if word is a common English word (to avoid false positives)
     */
    private static boolean isCommonEnglishWord(String word) {
        // Short list of common English words that might match Sanskrit patterns
        Set<String> commonWords = Set.of(
                "practice", "practical", "praise", "pray", "prayer",
                "sample", "same", "save", "vine", "view", "visit",
                "animal", "animate", "available", "avenue",
                "nice", "night", "niece", "parse", "part", "party"
        );
        return commonWords.contains(word);
    }
}