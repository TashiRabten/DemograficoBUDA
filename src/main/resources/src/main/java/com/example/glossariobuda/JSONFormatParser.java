package com.example.glossariobuda;

import com.example.glossariobuda.exceptions.ImportException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for JSON glossary files with intelligent language detection
 */
public class JSONFormatParser implements FormatParser {

    @Override
    public List<GlossaryTerm> parse(File file, LoadProgressCallback callback) throws Exception {
        List<GlossaryTerm> terms = new ArrayList<>();

        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append("\n");
            }
        }

        String json = jsonContent.toString().trim();

        if (!json.startsWith("[")) {
            throw new ImportException("JSON deve começar com um array [ ]");
        }

        int objectCount = 0;
        int braceDepth = 0;
        StringBuilder currentObject = new StringBuilder();

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '{') {
                braceDepth++;
                if (braceDepth == 1) {
                    currentObject = new StringBuilder();
                }
            }

            if (braceDepth > 0) {
                currentObject.append(c);
            }

            if (c == '}') {
                braceDepth--;
                if (braceDepth == 0 && currentObject.length() > 0) {
                    try {
                        GlossaryTerm term = parseJsonObject(currentObject.toString());
                        if (term != null) {
                            terms.add(term);
                            objectCount++;
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao processar objeto JSON: " + e.getMessage());
                    }

                    currentObject = new StringBuilder();

                    if (callback != null && objectCount % 100 == 0) {
                        callback.onProgress(objectCount, -1, "Processados " + objectCount + " termos");
                    }
                }
            }
        }

        return terms;
    }

    @Override
    public boolean canParse(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".json");
    }

    @Override
    public String getFileExtension() {
        return "json";
    }

    @Override
    public String getFormatDescription() {
        return "JSON (JavaScript Object Notation)";
    }

    /**
     * Extract owner metadata from JSON root object
     */
    @Override
    public ImportMetadata extractMetadata(File file) {
        try {
            StringBuilder jsonContent = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line).append("\n");
                }
            }

            String json = jsonContent.toString().trim();

            // Check if it's an object (might have metadata at root level)
            if (json.startsWith("{") && !json.startsWith("[")) {
                // Try to extract owner from root object
                String owner = extractJsonValue(json, "owner", "maintainer", "source");
                if (!owner.isEmpty()) {
                    return new ImportMetadata(owner, file.getName());
                }
            }

            return new ImportMetadata(null, file.getName());
        } catch (Exception e) {
            System.err.println("Error extracting metadata from JSON: " + e.getMessage());
            return new ImportMetadata(null, file.getName());
        }
    }

    /**
     * Parse a single JSON object with three-phase language detection
     */
    private GlossaryTerm parseJsonObject(String objectStr) {
        GlossaryTerm term = new GlossaryTerm();

        System.out.println("=== PARSING JSON OBJECT ===");
        System.out.println("Raw object: " + objectStr.substring(0, Math.min(200, objectStr.length())) + "...");

        // PHASE 1: Check for explicit source_term and target_term
        String explicitSource = extractJsonValue(objectStr, "source_term");
        String explicitTarget = extractJsonValue(objectStr, "target_term");

        System.out.println("Explicit source: '" + explicitSource + "'");
        System.out.println("Explicit target: '" + explicitTarget + "'");

        if (!explicitSource.isEmpty() && !explicitTarget.isEmpty()) {
            System.out.println(">>> USING PHASE 1: Explicit fields");
            term.setSourceTerm(explicitSource);
            term.setTargetTerm(explicitTarget);
            term.setTranslation(explicitSource);
            term.setTibetan(explicitTarget);

            String sourceLang = extractJsonValue(objectStr, "source_language");
            term.setSourceLanguage(!sourceLang.isEmpty() ? sourceLang : null);

            String targetLang = extractJsonValue(objectStr, "target_language");
            term.setTargetLanguage(!targetLang.isEmpty() ? targetLang : null);

            String romanization = extractJsonValue(objectStr, "romanization", "wylie", "pinyin", "romanized");
            if (!romanization.isEmpty()) {
                term.setNotes("Romanization: " + romanization);
                term.setWylie(romanization);
            }

        } else {
            System.out.println(">>> USING PHASE 2: Buddhist glossary logic");

            // Extract all available terms
            String tibetan = extractJsonValue(objectStr, "tibetan");
            String sanskrit = extractJsonValue(objectStr, "sanskrit", "skt");
            String english = extractJsonValue(objectStr, "translation", "english");
            String chinese = extractJsonValue(objectStr, "chinese");
            String pali = extractJsonValue(objectStr, "pali");
            String thai = extractJsonValue(objectStr, "thai");
            String mongolian = extractJsonValue(objectStr, "mongolian");

            // PRIORITY: Tibetan (if present) should be SOURCE
            if (!tibetan.isEmpty()) {
                term.setSourceTerm(tibetan);
                term.setSourceLanguage("Tibetan");
                term.setTibetan(tibetan);

                // Target priority: Sanskrit > English
                if (!sanskrit.isEmpty()) {
                    term.setTargetTerm(sanskrit);
                    term.setTranslation(sanskrit);

                    // Detect Sanskrit language (romanized vs Devanagari)
                    if (LanguageDetector.hasSanskritDiacritics(sanskrit)) {
                        term.setTargetLanguage("Sanskrit");
                    } else {
                        String detected = LanguageDetector.detectLanguage(sanskrit);
                        term.setTargetLanguage(detected.isEmpty() ? "Sanskrit" : detected);
                    }

                    // Store English in notes if present
                    if (!english.isEmpty()) {
                        term.setNotes("English: " + english);
                    }
                } else if (!english.isEmpty()) {
                    term.setTargetTerm(english);
                    term.setTranslation(english);
                    term.setTargetLanguage("English");
                }
            }
            // No Tibetan - check other Asian scripts
            else if (!chinese.isEmpty()) {
                term.setSourceTerm(chinese);
                term.setSourceLanguage("Chinese");
                term.setTargetTerm(english.isEmpty() ? "" : english);
                term.setTargetLanguage(english.isEmpty() ? null : "English");
                term.setTibetan(chinese);
                term.setTranslation(english);
            } else if (!pali.isEmpty()) {
                term.setSourceTerm(pali);
                term.setSourceLanguage("Pali");
                term.setTargetTerm(english.isEmpty() ? "" : english);
                term.setTargetLanguage(english.isEmpty() ? null : "English");
                term.setTibetan(pali);
                term.setTranslation(english);
            } else if (!thai.isEmpty()) {
                term.setSourceTerm(thai);
                term.setSourceLanguage("Thai");
                term.setTargetTerm(english.isEmpty() ? "" : english);
                term.setTargetLanguage(english.isEmpty() ? null : "English");
                term.setTibetan(thai);
                term.setTranslation(english);
            } else if (!mongolian.isEmpty()) {
                term.setSourceTerm(mongolian);
                term.setSourceLanguage("Mongolian");
                term.setTargetTerm(english.isEmpty() ? "" : english);
                term.setTargetLanguage(english.isEmpty() ? null : "English");
                term.setTibetan(mongolian);
                term.setTranslation(english);
            }
            // No Asian language - Sanskrit as source (Sanskrit-only glossaries)
            else if (!sanskrit.isEmpty()) {
                term.setSourceTerm(sanskrit);
                term.setTibetan(sanskrit);

                // Detect Sanskrit language
                if (LanguageDetector.hasSanskritDiacritics(sanskrit)) {
                    term.setSourceLanguage("Sanskrit");
                } else {
                    String detected = LanguageDetector.detectLanguage(sanskrit);
                    term.setSourceLanguage(detected.isEmpty() ? "Sanskrit" : detected);
                }

                term.setTargetTerm(english.isEmpty() ? "" : english);
                term.setTranslation(english);
                term.setTargetLanguage(english.isEmpty() ? null : "English");
            }
            // Last resort: use whatever we have
            else {
                String possibleSource = extractJsonValue(objectStr, "term", "source", "word");
                String possibleTarget = extractJsonValue(objectStr, "target");

                term.setSourceTerm(possibleSource);
                term.setTibetan(possibleSource);
                term.setTargetTerm(possibleTarget.isEmpty() ? english : possibleTarget);
                term.setTranslation(english);

                // Detect languages
                if (!possibleSource.isEmpty()) {
                    if (LanguageDetector.hasSanskritDiacritics(possibleSource)) {
                        term.setSourceLanguage("Sanskrit");
                    } else {
                        term.setSourceLanguage(LanguageDetector.detectLanguage(possibleSource));
                    }
                }

                if (!possibleTarget.isEmpty()) {
                    if (LanguageDetector.hasSanskritDiacritics(possibleTarget)) {
                        term.setTargetLanguage("Sanskrit");
                    } else {
                        term.setTargetLanguage(LanguageDetector.detectLanguage(possibleTarget));
                    }
                } else if (!english.isEmpty()) {
                    term.setTargetLanguage("English");
                }
            }

            String romanization = extractJsonValue(objectStr, "wylie", "pinyin", "romanization", "romanized");
            if (!romanization.isEmpty()) {
                String existingNotes = term.getNotes();
                String romNote = "Romanization: " + romanization;
                term.setNotes(existingNotes == null || existingNotes.isEmpty() ? romNote : existingNotes + "\n" + romNote);
                term.setWylie(romanization);
            }
        }

        // Extract definition/context
        String definition = extractJsonValue(objectStr, "definition", "context", "meaning", "description");
        term.setContext(definition);

        // Extract type/category
        String type = extractJsonValue(objectStr, "type", "category");
        if (!type.isEmpty()) {
            String existingNotes = term.getNotes();
            String typeNote = "Type: " + type;
            if (existingNotes == null || existingNotes.isEmpty()) {
                term.setNotes(typeNote);
            } else if (!existingNotes.contains("Type:")) {
                term.setNotes(existingNotes + "\n" + typeNote);
            }
        }

        // Extract contributor/author
        String contributor = extractJsonValue(objectStr, "contributor", "author", "translator");
        term.setContributor(contributor);

        // Extract Sanskrit/Pali
        String sanskrit = extractJsonValue(objectStr, "sanskrit", "pali", "skt");
        if (!sanskrit.isEmpty() &&
                !sanskrit.equals(term.getSourceTerm()) &&
                !sanskrit.equals(term.getTargetTerm())) {
            String existingNotes = term.getNotes();
            String sanskritNote = "Sanskrit: " + sanskrit;
            if (existingNotes == null || existingNotes.isEmpty()) {
                term.setNotes(sanskritNote);
            } else if (!existingNotes.contains("Sanskrit:")) {
                term.setNotes(existingNotes + "\n" + sanskritNote);
            }
        }

        // Extract additional notes
        String notes = extractJsonValue(objectStr, "notes", "note", "comments");
        if (!notes.isEmpty()) {
            String existingNotes = term.getNotes();
            if (existingNotes == null || existingNotes.isEmpty()) {
                term.setNotes(notes);
            } else {
                term.setNotes(existingNotes + "\n" + notes);
            }
        }

        // Parse references
        String refsStr = extractJsonValue(objectStr, "references", "refs");
        if (!refsStr.isEmpty()) {
            String[] refs = refsStr.split("[;|]");
            List<String> refList = new ArrayList<>();
            for (String ref : refs) {
                String trimmed = ref.trim();
                if (!trimmed.isEmpty()) {
                    refList.add(trimmed);
                }
            }
            term.setReferences(refList);
        }

        return term;
    }

    /**
     * Detect language from field names by matching which field contains the extracted value
     * Now with enhanced content verification
     */
    private String detectLanguageFromFieldNames(String objectStr, String extractedValue) {
        if (extractedValue == null || extractedValue.isEmpty()) {
            return "";
        }

        // Check which specific language field contains our extracted value
        String[][] languageFields = {
                {"sanskrit", "Sanskrit"},
                {"skt", "Sanskrit"},
                {"pali", "Pali"},
                {"tibetan", "Tibetan"},
                {"chinese", "Chinese"},
                {"thai", "Thai"},
                {"mongolian", "Mongolian"},
                {"english", "English"},
                {"translation", null},  // Ambiguous - needs verification
                {"term", null}          // Ambiguous - needs verification
        };

        String detectedFromField = "";
        boolean needsVerification = false;

        for (String[] langField : languageFields) {
            String fieldName = langField[0];
            String language = langField[1];

            String fieldValue = extractJsonValue(objectStr, fieldName);
            if (!fieldValue.isEmpty() && fieldValue.equals(extractedValue)) {
                if (language == null) {
                    // Ambiguous field - needs content verification
                    needsVerification = true;
                    System.out.println("Ambiguous field '" + fieldName + "' - will verify content");
                    break;
                } else {
                    detectedFromField = language;
                    System.out.println("Matched field '" + fieldName + "' -> " + language);

                    // Even explicit fields should be verified if content contradicts
                    // (e.g., someone might have mislabeled columns)
                    if (LanguageDetector.hasSanskritDiacritics(extractedValue) && !language.equals("Sanskrit")) {
                        System.out.println("Diacritics override: Sanskrit (was " + language + ")");
                        return "Sanskrit";
                    }
                    return language;
                }
            }
        }

        // If ambiguous or not found, use content-based detection
        if (needsVerification || detectedFromField.isEmpty()) {
            String contentLanguage = LanguageDetector.detectLanguage(extractedValue);
            System.out.println("Content-based detection: " + contentLanguage);
            return contentLanguage;
        }

        return detectedFromField;
    }

    /**
     * Detect language from Unicode script in the text
     */
    private String detectLanguageFromScript(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        char firstChar = text.charAt(0);

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

        // Latin/ASCII
        if ((firstChar >= 'A' && firstChar <= 'Z') ||
                (firstChar >= 'a' && firstChar <= 'z')) {
            return "English";
        }

        return "";
    }

    /**
     * Extract a JSON field value (tries multiple possible field names)
     */
    private String extractJsonValue(String json, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String fieldPattern = "\"" + fieldName + "\"";
            int fieldIndex = json.indexOf(fieldPattern);

            if (fieldIndex == -1) {
                continue;
            }

            int colonIndex = fieldIndex + fieldPattern.length();

            while (colonIndex < json.length() && Character.isWhitespace(json.charAt(colonIndex))) {
                colonIndex++;
            }

            if (colonIndex >= json.length() || json.charAt(colonIndex) != ':') {
                continue;
            }

            colonIndex++;

            while (colonIndex < json.length() && Character.isWhitespace(json.charAt(colonIndex))) {
                colonIndex++;
            }

            if (colonIndex >= json.length() || json.charAt(colonIndex) != '"') {
                continue;
            }

            int startIndex = colonIndex + 1;
            int endIndex = startIndex;

            boolean escaped = false;
            while (endIndex < json.length()) {
                char c = json.charAt(endIndex);
                if (c == '\\') {
                    escaped = !escaped;
                } else if (c == '"' && !escaped) {
                    break;
                } else {
                    escaped = false;
                }
                endIndex++;
            }

            if (endIndex > startIndex) {
                String value = json.substring(startIndex, endIndex);
                value = unescapeJson(value);
                return value.trim();
            }
        }

        return "";
    }

    /**
     * Unescape JSON escape sequences
     */
    private String unescapeJson(String value) {
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < value.length()) {
            char c = value.charAt(i);

            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case '\\':
                        result.append('\\');
                        i += 2;
                        break;
                    case '"':
                        result.append('"');
                        i += 2;
                        break;
                    case 'n':
                        result.append('\n');
                        i += 2;
                        break;
                    case 'r':
                        result.append('\r');
                        i += 2;
                        break;
                    case 't':
                        result.append('\t');
                        i += 2;
                        break;
                    case '/':
                        result.append('/');
                        i += 2;
                        break;
                    case 'b':
                        result.append('\b');
                        i += 2;
                        break;
                    case 'f':
                        result.append('\f');
                        i += 2;
                        break;
                    default:
                        result.append(c);
                        i++;
                        break;
                }
            } else {
                result.append(c);
                i++;
            }
        }

        return result.toString();
    }
}
