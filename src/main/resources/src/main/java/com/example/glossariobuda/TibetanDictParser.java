package com.example.glossariobuda;

import java.io.*;
import java.util.*;

/**
 * Parser for Steinert's tibetan-dictionary pipe-delimited format
 *
 * Format: wylie_term|english_definition
 *
 * Handles dictionaries from Christian Steinert's tibetan-dictionary-master compilation:
 * - Hopkins, Rangjung Yeshe, Ives-Waldo, Dan Martin, etc.
 * - Lines starting with # are comments (skipped)
 * - Simple two-column format with pipe delimiter
 */
public class TibetanDictParser implements FormatParser {

    // Map of filename prefixes to contributor names
    private static final Map<String, String> CONTRIBUTOR_MAP = new HashMap<>();

    static {
        CONTRIBUTOR_MAP.put("01-Hopkins2015", "Hopkins, Jeffrey - Tibetan-Sanskrit-English Dictionary (2015)");
        CONTRIBUTOR_MAP.put("02-RangjungYeshe", "Rangjung Yeshe Dictionary");
        CONTRIBUTOR_MAP.put("03-Berzin", "Berzin, Alexander");
        CONTRIBUTOR_MAP.put("04-Berzin-Def", "Berzin, Alexander - Definitions");
        CONTRIBUTOR_MAP.put("05-Hackett-Def2015", "Hackett - Definitions (2015)");
        CONTRIBUTOR_MAP.put("05-Hopkins-Def2015", "Hopkins, Jeffrey - Definitions (2015)");
        CONTRIBUTOR_MAP.put("06-Hopkins-Comment", "Hopkins, Jeffrey - Commentary");
        CONTRIBUTOR_MAP.put("07-JimValby", "Valby, Jim");
        CONTRIBUTOR_MAP.put("08-IvesWaldo", "Ives-Waldo Tibetan-English Dictionary");
        CONTRIBUTOR_MAP.put("09-DanMartin", "Martin, Dan - Tibetan Vocabulary");
        CONTRIBUTOR_MAP.put("10-RichardBarron", "Barron, Richard");
        CONTRIBUTOR_MAP.put("15-Hopkins-Skt1992", "Hopkins, Jeffrey - Tibetan-Sanskrit (1992)");
        CONTRIBUTOR_MAP.put("15-Hopkins-Skt2015", "Hopkins, Jeffrey - Tibetan-Sanskrit (2015)");
        CONTRIBUTOR_MAP.put("21-Mahavyutpatti-Skt", "Mahāvyutpatti - Sanskrit-Tibetan");
        CONTRIBUTOR_MAP.put("22-Yoghacharabhumi-glossary", "Yogācārabhūmi Glossary");
        CONTRIBUTOR_MAP.put("23-GatewayToKnowledge", "Gateway to Knowledge");
        CONTRIBUTOR_MAP.put("25-tshig-mdzod-chen-mo-Tib", "Tshig mdzod chen mo");
        CONTRIBUTOR_MAP.put("26-Verbinator", "Verbinator 2000 - Hill, Nathan");
        CONTRIBUTOR_MAP.put("33-TsepakRigdzin", "Tsepak Rigdzin");
        // Hopkins special dictionaries
        CONTRIBUTOR_MAP.put("11-Hopkins-Divisions2015", "Hopkins, Jeffrey - Divisions (2015)");
        CONTRIBUTOR_MAP.put("12-Hopkins-Divisions,Tib2015", "Hopkins, Jeffrey - Divisions with Tibetan (2015)");
        CONTRIBUTOR_MAP.put("13-Hopkins-Examples", "Hopkins, Jeffrey - Examples");
        CONTRIBUTOR_MAP.put("14-Hopkins-Examples,Tib", "Hopkins, Jeffrey - Examples with Tibetan");
        CONTRIBUTOR_MAP.put("16-Hopkins-Synonyms1992", "Hopkins, Jeffrey - Synonyms (1992)");
        CONTRIBUTOR_MAP.put("17-Hopkins-TibetanSynonyms1992", "Hopkins, Jeffrey - Tibetan Synonyms (1992)");
        CONTRIBUTOR_MAP.put("17-Hopkins-TibetanSynonyms2015", "Hopkins, Jeffrey - Tibetan Synonyms (2015)");
        CONTRIBUTOR_MAP.put("18-Hopkins-TibetanDefinitions2015", "Hopkins, Jeffrey - Tibetan Definitions (2015)");
        CONTRIBUTOR_MAP.put("19-Hopkins-TibetanTenses2015", "Hopkins, Jeffrey - Tibetan Tenses (2015)");
        CONTRIBUTOR_MAP.put("20-Hopkins-others'English2015", "Hopkins, Jeffrey - Others' English (2015)");
        // More Tibetan dictionaries
        CONTRIBUTOR_MAP.put("37-dag_tshig_gsar_bsgrigs-Tib", "dag tshig gsar bsgrigs");
        CONTRIBUTOR_MAP.put("54-bod_rgya_nang_don_rig_pai_tshig_mdzod", "bod rgya nang don rig pa'i tshig mdzod");
        CONTRIBUTOR_MAP.put("55-brda_dkrol_gser_gyi_me_long", "brda dkrol gser gyi me long");
        CONTRIBUTOR_MAP.put("56-chos_rnam_kun_btus", "chos rnam kun btus");
        CONTRIBUTOR_MAP.put("57-li_shii_gur_khang", "li shii gur khang");
        CONTRIBUTOR_MAP.put("58-sgom_sde_tshig_mdzod_chen_mo", "sgom sde tshig mdzod chen mo");
        CONTRIBUTOR_MAP.put("59-sgra_bye_brag_tu_rtogs_byed_chen_mo", "sgra bye brag tu rtogs byed chen mo");
        CONTRIBUTOR_MAP.put("60-sngas_rgyas_chos_gzhung_tshig_mdzod", "sngas rgyas chos gzhung tshig mdzod");
        CONTRIBUTOR_MAP.put("61-gangs_can_mkhas_grub_rim_byon_ming_mdzod", "gangs can mkhas grub rim byon ming mdzod");
        CONTRIBUTOR_MAP.put("62-bod_yig_tshig_gter_rgya_mtsho", "bod yig tshig gter rgya mtsho");
        CONTRIBUTOR_MAP.put("63-Mahavyutpatti-Scan-1989", "Mahāvyutpatti Scan (1989)");
        CONTRIBUTOR_MAP.put("64-sgra-sbyor-bam-po-gnyis-pa", "sgra sbyor bam po gnyis pa");
        CONTRIBUTOR_MAP.put("34-dung-dkar-tshig-mdzod-chen-mo-Tib", "Dung dkar tshig mdzod chen mo");
        CONTRIBUTOR_MAP.put("35-ThomasDoctor", "Doctor, Thomas");
        CONTRIBUTOR_MAP.put("36-ComputerTerms", "Tibetan Computer Terms");
        CONTRIBUTOR_MAP.put("48-TibTermProject", "Tibetan Terminology Project");
        CONTRIBUTOR_MAP.put("49-LokeshChandraSkt", "Lokesh Chandra - Tibetan-Sanskrit");
        CONTRIBUTOR_MAP.put("50-NegiSkt", "Negi - Tibetan-Sanskrit Dictionary");
        CONTRIBUTOR_MAP.put("51-LaineAbbreviations", "Laine, Bruno - Tibetan Abbreviations");
        CONTRIBUTOR_MAP.put("52-ITLR", "ITLR - Library of Tibetan Works and Archives");
        CONTRIBUTOR_MAP.put("53-Bialek", "Bialek, Joanna");
        CONTRIBUTOR_MAP.put("65-ChandraDas_Scan", "Chandra Das - Tibetan-English Dictionary");
        CONTRIBUTOR_MAP.put("66-Jaeschke_Scan", "Jäschke, H.A. - Tibetan-English Dictionary");
        CONTRIBUTOR_MAP.put("67-hotl1", "Oertle, F. - Heart of Tibetan Language Vol. 1");
        CONTRIBUTOR_MAP.put("67-hotl2", "Oertle, F. - Heart of Tibetan Language Vol. 2");
        CONTRIBUTOR_MAP.put("67-hotl3", "Oertle, F. - Heart of Tibetan Language Vol. 3");
    }

    @Override
    public List<GlossaryTerm> parse(File file, LoadProgressCallback callback) throws Exception {
        List<GlossaryTerm> terms = new ArrayList<>();
        String contributor = getContributor(file.getName());
        String filename = file.getName();

        // Detect dictionary format
        boolean isSanskritSource = isSanskritSourceDictionary(filename);
        boolean isTibetanOnly = isTibetanOnlyDictionary(filename);
        boolean isSanskritTarget = isSanskritTargetDictionary(filename);
        boolean isIASTSanskrit = isIASTSanskritDictionary(filename);
        boolean hasSanskritEtymology = hasSanskritEtymologyFormat(filename);
        boolean isHOTL = isHOTLDictionary(filename);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            int validTerms = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip comments and empty lines
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse pipe-delimited line
                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) {
                    continue; // Skip malformed lines
                }

                String firstColumn = parts[0].trim();
                String secondColumn = parts[1].trim();

                if (firstColumn.isEmpty() || secondColumn.isEmpty()) {
                    continue;
                }

                // Extract audio reference from content (for any file with [sound:...] tags)
                String audioPath = "";
                String[] audioResult = extractAudioReference(secondColumn, filename);
                secondColumn = audioResult[0]; // Text without [sound:...] tag
                audioPath = audioResult[1];     // Audio file path

                // Convert literal \n to actual newlines (for test files)
                secondColumn = secondColumn.replace("\\n", "\n");

                // Create term with appropriate source/target based on dictionary type
                GlossaryTerm term = new GlossaryTerm();

                if (isSanskritSource) {
                    // Mahavyutpatti: File has Tibetan|Sanskrit, but display as Sanskrit→Tibetan
                    // This is historically a Sanskrit→Tibetan reference dictionary
                    term.setWylie(firstColumn);

                    // Convert Wylie to Tibetan Unicode for target
                    String tibetanUnicode = WylieConverter.toUnicode(firstColumn);
                    term.setTargetTerm(tibetanUnicode);
                    term.setTargetLanguage("Tibetan");

                    // Second column is Sanskrit - make it the source
                    term.setSanskrit(secondColumn);
                    term.setSourceTerm(secondColumn);
                    term.setSourceLanguage("Sanskrit");
                    term.setContext("Tibetan Wylie: " + firstColumn);
                } else {
                    // Standard: Tibetan → X
                    term.setWylie(firstColumn);

                    // Convert Wylie to Tibetan Unicode for source
                    String tibetanUnicode = WylieConverter.toUnicode(firstColumn);
                    term.setSourceTerm(tibetanUnicode);
                    term.setSourceLanguage("Tibetan");

                    // Process target term based on dictionary type
                    String processedTarget;
                    String fullContext;

                    if (isTibetanOnly) {
                        // Tibetan → Tibetan: Smart mixed content conversion
                        // Handles pure Wylie, mixed Wylie/English, structure markers
                        String cleanedTarget = cleanTargetText(secondColumn);

                        // Check if it's pure Wylie or mixed content
                        // Pure Wylie: only lowercase, apostrophes, slashes, and Tibetan punctuation
                        if (cleanedTarget.matches("^[a-z' /\u0F00-\u0FFF]+$")) {
                            // Pure Wylie - convert directly
                            processedTarget = WylieConverter.toUnicode(cleanedTarget);
                        } else {
                            // Mixed content - use smart converter
                            // Aggressively convert Wylie while preserving English citations
                            processedTarget = convertMixedContentAggressive(cleanedTarget);
                        }

                        // Apply curly brace conversion for any remaining Wylie in braces
                        processedTarget = convertCurlyBraceWylie(processedTarget);

                        // Enhanced truncation for long Tibetan definitions
                        String[] defParts = splitTibetanDefinition(processedTarget, secondColumn);
                        processedTarget = defParts[0]; // Truncated if needed
                        fullContext = defParts[1];     // Full content
                        term.setTargetLanguage("Tibetan");
                    } else if (isIASTSanskrit) {
                        // Tibetan → Sanskrit (already IAST, e.g., Yogacharabhumi)
                        // No SLP1 conversion needed

                        // Apply newline splitting for long definitions
                        String[] defParts = splitDefinition(secondColumn);
                        processedTarget = cleanIASTSanskrit(defParts[0]);
                        fullContext = defParts[1]; // Extended context

                        term.setSanskrit(processedTarget);
                        term.setTargetLanguage("Sanskrit");
                    } else if (isSanskritTarget) {
                        // Tibetan → Sanskrit (SLP1 format): Convert SLP1 to IAST
                        // Apply curly brace conversion first (for ITLR, Negi with Tibetan in braces)
                        String targetWithBraces = convertCurlyBraceWylie(secondColumn);

                        // Apply newline splitting for long Sanskrit definitions
                        String[] defParts = splitDefinition(targetWithBraces);
                        processedTarget = processSanskritTarget(defParts[0]); // Process first part
                        fullContext = defParts[1]; // Extended context

                        term.setSanskrit(processedTarget);
                        term.setTargetLanguage("Sanskrit");
                        // Extract citations to notes
                        String citations = extractCitations(secondColumn);
                        if (!citations.isEmpty()) {
                            term.setNotes(citations);
                        }
                    } else if (hasSanskritEtymology) {
                        // Special format: (Skt. X) english_term
                        // Preserve Sanskrit in IAST and English as-is
                        processedTarget = processSanskritEtymology(secondColumn);

                        // Apply curly brace conversion for any Tibetan in braces
                        processedTarget = convertCurlyBraceWylie(processedTarget);

                        String[] defParts = splitDefinition(processedTarget);
                        processedTarget = defParts[0]; // Main definition
                        fullContext = defParts[1];      // Extended context
                        term.setTargetLanguage("Sanskrit+English"); // Mixed Sanskrit etymology and English
                    } else {
                        // Tibetan → English/Other: Smart truncation for long definitions
                        // Apply curly brace conversion first (for HOTL and others)
                        String targetWithBraces = convertCurlyBraceWylie(secondColumn);
                        String[] defParts = splitDefinition(targetWithBraces);
                        processedTarget = defParts[0]; // Main definition
                        fullContext = defParts[1];      // Extended context
                        term.setTargetLanguage(detectLanguage(targetWithBraces));
                    }

                    term.setTargetTerm(processedTarget);
                    term.setContext(fullContext);
                }

                term.setContributor(contributor);

                // Add audio link to notes for any file with [sound:...] tags
                if (!audioPath.isEmpty()) {
                    String audioLink = createAudioLink(audioPath);
                    String existingNotes = term.getNotes();

                    if (existingNotes != null && !existingNotes.isEmpty()) {
                        term.setNotes(existingNotes + "\n" + audioLink);
                    } else {
                        term.setNotes(audioLink);
                    }
                }

                // Add PDF reference link for scan dictionaries with page numbers
                String pdfLink = createPdfReferenceLink(filename, term.getTargetTerm());
                if (pdfLink != null && !pdfLink.isEmpty()) {
                    String existingNotes = term.getNotes();

                    if (existingNotes != null && !existingNotes.isEmpty()) {
                        term.setNotes(existingNotes + "\n" + pdfLink);
                    } else {
                        term.setNotes(pdfLink);
                    }
                }

                terms.add(term);
                validTerms++;

                // Report progress every 1000 lines
                if (callback != null && validTerms % 1000 == 0) {
                    callback.onProgress(validTerms, -1, "Processados " + validTerms + " termos");
                }
            }

            if (callback != null) {
                callback.onProgress(validTerms, validTerms,
                    "Carregamento completo: " + validTerms + " termos de " + contributor);
            }
        }

        return terms;
    }

    /**
     * Get contributor name from filename
     */
    private String getContributor(String filename) {
        // Try exact match first
        for (Map.Entry<String, String> entry : CONTRIBUTOR_MAP.entrySet()) {
            if (filename.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Fallback: clean filename
        String cleaned = filename.replaceAll("^\\d+-", "")  // Remove number prefix
                                 .replace("-", " ")
                                 .replace("_", " ");
        return cleaned;
    }

    @Override
    public boolean canParse(File file) {
        String name = file.getName().toLowerCase();

        // Reject 84000 dictionaries
        if (name.contains("84000")) {
            return false;
        }

        // Reject XML files (handled by SteinertXMLParser)
        if (name.endsWith(".xml")) {
            return false;
        }

        // Accept:
        // 1. Files starting with number-dash (Steinert pattern): 67-hotl1, 01-Hopkins2015
        // 2. Files with no extension: TEST-ALL-FEATURES-PLAINTEXT
        // 3. Files with .txt extension: TEST-ALL-FEATURES-PLAINTEXT.txt
        return name.matches("\\d+-.*") || !name.contains(".") || name.endsWith(".txt");
    }

    @Override
    public String getFileExtension() {
        return "txt";
    }

    @Override
    public String getFormatDescription() {
        return "Tibetan Dictionary (Steinert Collection - Pipe-delimited)";
    }

    @Override
    public ImportMetadata extractMetadata(File file) {
        return new ImportMetadata("Steinert", file.getName());
    }

    /**
     * Check if dictionary is Tibetan-only based on filename
     * These dictionaries have Tibetan definitions (in Wylie) rather than English
     */
    private boolean isTibetanOnlyDictionary(String filename) {
        String lower = filename.toLowerCase();

        // Exceptions: These look Tibetan but are actually English
        if (lower.contains("tibterm") || lower.contains("48-")) {
            return false;  // TibTermProject is Tib→English
        }

        // CRITICAL: Hopkins dictionaries WITHOUT ",Tib" suffix are ENGLISH, not Tibetan!
        // Only Hopkins dictionaries WITH ",Tib" in filename are Tibetan-only
        if (lower.contains("hopkins")) {
            // Only these Hopkins dictionaries have Tibetan definitions:
            return lower.contains(",tib") ||           // 12-Hopkins-Divisions,Tib2015, 14-Hopkins-Examples,Tib
                   lower.contains("tibetansynonyms") || // 17-Hopkins-TibetanSynonyms
                   lower.contains("tibetandefinitions") || // 18-Hopkins-TibetanDefinitions
                   lower.contains("tibetantenses");     // 19-Hopkins-TibetanTenses
            // Note: Hopkins-Divisions2015, Hopkins-Examples (without ,Tib) are ENGLISH
        }

        // Dan Martin has mixed Wylie/English, treat as mixed content
        if (lower.contains("danmartin") || lower.contains("09-danmartin")) {
            return true; // Uses smart mixed content parser
        }

        // EXCEPTION: sgra_bye_brag and similar Sanskrit etymology dictionaries
        // These have special format: (Skt. X) english_translation
        // Should NOT be treated as Tibetan-only despite having "sgra_" pattern
        if (lower.contains("sgra_bye_brag") || lower.contains("59-sgra")) {
            return false; // Has Sanskrit + English, not Tibetan-only
        }

        // Known pure Tibetan-only dictionaries (monolingual Tibetan)
        return lower.contains("-tib") ||
               lower.contains("bod_") ||         // bod_rgya, bod_yig
               lower.contains("tshig") ||
               lower.contains("mdzod") ||
               lower.contains("dag_tshig") ||
               lower.contains("sgom_sde") ||
               lower.contains("sgra_") ||
               lower.contains("gangs_can") ||
               lower.contains("li_shii") ||
               lower.contains("brda_") ||
               lower.contains("chos_") ||
               lower.contains("sera");          // Sera textbook definitions
    }

    /**
     * Check if dictionary uses Sanskrit etymology format: (Skt. X) english_term
     * These dictionaries need special handling to preserve Sanskrit in IAST
     * and English translations
     */
    private boolean hasSanskritEtymologyFormat(String filename) {
        String lower = filename.toLowerCase();
        return lower.contains("sgra_bye_brag") ||
               lower.contains("59-sgra") ||
               lower.contains("sgra-sbyor");  // Sanskrit etymology dictionaries
    }

    /**
     * Process text with Sanskrit etymology format: (Skt. X) english_term
     * Preserves Sanskrit in (Skt. ...) or (Skt.) patterns and keeps English outside
     * Used for dictionaries like sgra_bye_brag that show Sanskrit etymologies
     */
    private String processSanskritEtymology(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Pattern: (Skt. sanskrit_word) english_translation
        // or: (Skt. X) Y where X should stay in IAST and Y should stay in English

        // Check if text contains (Skt. ...) pattern
        if (text.contains("(Skt.")) {
            // This text should be preserved as-is - it's already in the correct format
            // Sanskrit is in IAST inside the parentheses, English is outside
            return text;
        }

        return text;
    }

    /**
     * Check if dictionary has Sanskrit as SOURCE (reverse direction)
     * These are Sanskrit→Tibetan dictionaries
     */
    private boolean isSanskritSourceDictionary(String filename) {
        String lower = filename.toLowerCase();

        // Mahavyutpatti is the only known Sanskrit→Tibetan dictionary
        return lower.contains("21-mahavyutpatti-skt") ||
               lower.contains("mahavyutpatti-skt");
    }

    /**
     * Check if dictionary has Sanskrit as TARGET
     * These are Tibetan→Sanskrit dictionaries (in SLP1 format)
     */
    private boolean isSanskritTargetDictionary(String filename) {
        String lower = filename.toLowerCase();

        // Exclude IAST Sanskrit dictionaries
        if (isIASTSanskritDictionary(filename)) {
            return false;
        }

        // Known Tibetan→Sanskrit dictionaries (SLP1 format)
        return (lower.contains("skt") || lower.contains("sanskrit")) &&
               !isSanskritSourceDictionary(filename) &&  // Exclude reverse dictionaries
               !lower.contains("tibterm");  // TibTermProject is English
    }

    /**
     * Check if dictionary has Sanskrit already in IAST format (not SLP1)
     * These dictionaries don't need SLP1→IAST conversion
     */
    private boolean isIASTSanskritDictionary(String filename) {
        String lower = filename.toLowerCase();

        // Yogacharabhumi has Sanskrit already in IAST format
        return lower.contains("yoghacharabhumi") ||
               lower.contains("yogacara");
    }

    /**
     * Detect if text is primarily Tibetan Unicode or English
     * Returns "Tibetan" if contains significant Tibetan Unicode characters
     * Returns "English" otherwise
     *
     * Note: This won't detect Wylie-transliterated Tibetan.
     * Use isTibetanOnlyDictionary() for filename-based detection.
     */
    private String detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return "English";
        }

        // Count Tibetan Unicode characters (U+0F00 to U+0FFF)
        int tibetanChars = 0;
        int totalChars = 0;

        for (char c : text.toCharArray()) {
            if (c >= '\u0F00' && c <= '\u0FFF') {
                tibetanChars++;
            }
            if (Character.isLetter(c)) {
                totalChars++;
            }
        }

        // If more than 30% of letters are Tibetan Unicode, consider it Tibetan
        if (totalChars > 0 && (tibetanChars * 100.0 / totalChars) > 30) {
            return "Tibetan";
        }

        return "English";
    }

    /**
     * Clean Wylie target text by removing special markers
     * Handles: /\n separators, multiple synonyms
     */
    private String cleanTargetText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Handle multiple synonyms separated by /\n - take first one
        if (text.contains("/\\n")) {
            String[] parts = text.split("/\\\\n");
            text = parts[0].trim();
        }

        // Clean up any remaining special characters
        text = text.replaceAll("/\\s*$", "").trim();

        return text;
    }

    /**
     * AGGRESSIVE conversion of mixed Wylie/English content
     * Assumes most lowercase words are Wylie unless they're clearly English
     * Use this for dictionaries with mostly Tibetan + occasional English citations
     *
     * IMPORTANT: Preserves content inside curly braces {} to be converted separately
     */
    private String convertMixedContentAggressive(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // First, extract and protect curly brace content
        // We'll convert those separately to preserve spaces inside braces
        List<String> protectedSegments = new ArrayList<>();
        StringBuilder textWithPlaceholders = new StringBuilder();
        int pos = 0;

        while (pos < text.length()) {
            int openBrace = text.indexOf('{', pos);

            if (openBrace == -1) {
                // No more braces, append rest
                textWithPlaceholders.append(text.substring(pos));
                break;
            }

            // Append text before brace
            textWithPlaceholders.append(text.substring(pos, openBrace));

            // Find matching closing brace
            int closeBrace = text.indexOf('}', openBrace);
            if (closeBrace == -1) {
                // No closing brace, append rest as-is
                textWithPlaceholders.append(text.substring(openBrace));
                break;
            }

            // Extract braced content including braces
            String bracedContent = text.substring(openBrace, closeBrace + 1);
            protectedSegments.add(bracedContent);

            // Add placeholder
            textWithPlaceholders.append("{{PROTECTED_" + (protectedSegments.size() - 1) + "}}");

            pos = closeBrace + 1;
        }

        // Now process the text with placeholders (no curly braces to worry about)
        String textToProcess = textWithPlaceholders.toString();
        StringBuilder result = new StringBuilder();
        String[] tokens = textToProcess.split("\\s+");

        boolean prevWasTibetan = false; // Track if previous token was Tibetan

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String processedToken = "";
            boolean currentIsTibetan = false;

            // Check if this is a placeholder
            if (token.matches("\\{\\{PROTECTED_\\d+\\}\\}")) {
                processedToken = token;
                currentIsTibetan = false;
            }
            // Skip if starts with uppercase (likely English proper noun/citation)
            else if (!token.isEmpty() && Character.isUpperCase(token.charAt(0))) {
                processedToken = token;
                currentIsTibetan = false;
            }
            // Skip if contains numbers
            else if (token.matches(".*\\d+.*")) {
                processedToken = token;
                currentIsTibetan = false;
            }
            else {
                // Extract punctuation
                String prefix = "";
                String suffix = "";
                String core = token;

                // Extract leading punctuation, BUT preserve leading apostrophes if they're part of Wylie
                // In Wylie: 'di'i → འདིའི (apostrophe at start represents the letter འ)
                while (!core.isEmpty() && isPunctuation(core.charAt(0))) {
                    char firstChar = core.charAt(0);

                    // If it's an apostrophe at the start, check if it's part of Wylie
                    if (firstChar == '\'' && core.length() > 1) {
                        char nextChar = core.charAt(1);
                        // If followed by a lowercase letter, it's likely Wylie - keep it
                        if (Character.isLowerCase(nextChar)) {
                            break; // Don't strip this apostrophe
                        }
                    }

                    prefix += firstChar;
                    core = core.substring(1);
                }

                // Extract trailing punctuation, BUT preserve trailing apostrophes if they're part of Wylie
                // In Wylie: gna' → གནའ (apostrophe after consonant represents a-chung vowel)
                while (!core.isEmpty() && isPunctuation(core.charAt(core.length() - 1))) {
                    char lastChar = core.charAt(core.length() - 1);

                    // If it's an apostrophe at the end, check if it's part of Wylie
                    if (lastChar == '\'' && core.length() > 1) {
                        char prevChar = core.charAt(core.length() - 2);
                        // If preceded by a lowercase letter (consonant), it's likely Wylie - keep it
                        if (Character.isLowerCase(prevChar)) {
                            break; // Don't strip this apostrophe
                        }
                    }

                    suffix = lastChar + suffix;
                    core = core.substring(0, core.length() - 1);
                }

                if (core.isEmpty()) {
                    processedToken = token;
                    currentIsTibetan = false;
                }
                // Check if it's a common English word (blacklist)
                else if (isCommonEnglishWord(core.toLowerCase())) {
                    processedToken = token;
                    currentIsTibetan = false;
                }
                // Otherwise, assume it's Wylie and convert
                else {
                    try {
                        String converted = WylieConverter.toUnicode(core);
                        processedToken = prefix + converted + suffix;
                        // Check if converted text contains Tibetan characters (U+0F00-U+0FFF)
                        currentIsTibetan = converted.matches(".*[\u0F00-\u0FFF].*");
                    } catch (Exception e) {
                        // If conversion fails, keep original
                        processedToken = token;
                        currentIsTibetan = false;
                    }
                }
            }

            // Add separator before appending (except for first token)
            if (i > 0) {
                // Use tsheg between Tibetan words, space otherwise
                if (prevWasTibetan && currentIsTibetan) {
                    result.append("་"); // Tibetan tsheg
                } else {
                    result.append(" "); // Regular space
                }
            }

            result.append(processedToken);
            prevWasTibetan = currentIsTibetan;
        }

        // Restore protected segments
        String finalResult = result.toString();
        for (int i = 0; i < protectedSegments.size(); i++) {
            finalResult = finalResult.replace("{{PROTECTED_" + i + "}}", protectedSegments.get(i));
        }

        return finalResult;
    }

    /**
     * Check if a word is a common English word that should NOT be converted
     */
    private boolean isCommonEnglishWord(String word) {
        String[] commonWords = {
            // Articles, prepositions, conjunctions
            "a", "an", "the", "of", "in", "on", "at", "to", "for", "by", "with", "from",
            "and", "or", "but", "if", "as", "than", "then", "when", "where", "while",
            // Common verbs/aux
            "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
            "do", "does", "did", "can", "could", "will", "would", "should", "may", "might",
            // Pronouns
            "i", "you", "he", "she", "it", "we", "they", "this", "that", "these", "those",
            "who", "what", "which", "whose", "whom",
            // Common adjectives/adverbs
            "not", "no", "yes", "all", "some", "any", "each", "every", "both", "either",
            "neither", "one", "two", "three", "four", "five", "also", "only", "just", "even",
            "very", "too", "so", "more", "most", "less", "least", "much", "many", "few",
            // Common nouns that could be confused
            "term", "way", "time", "year", "day", "part", "place", "case", "point", "hand",
            "work", "word", "fact", "thing", "name", "number", "area", "level", "order",
            // Citations/references
            "see", "cf", "vol", "coll", "ed", "trans", "ibid", "op", "cit", "etc", "lit",
            "fig", "fn", "note", "ref", "source", "footnote",
            // Grammar terms
            "verb", "noun", "adj", "adv", "prep", "conj", "arch", "archaic"
        };

        for (String common : commonWords) {
            if (word.equals(common)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Smart conversion of mixed Wylie/English content (CONSERVATIVE)
     * Converts Wylie words to Tibetan Unicode while preserving:
     * - English words, citations, numbers
     * - Structure markers: (1), (2), /
     * - Punctuation and formatting
     */
    private String convertMixedContent(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Split on spaces but preserve structure
        StringBuilder result = new StringBuilder();
        String[] tokens = text.split("\\s+");

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];

            // Check if this token should be converted
            if (isLikelyWylie(token)) {
                // Extract any leading/trailing punctuation
                String prefix = "";
                String suffix = "";
                String core = token;

                // Extract leading punctuation: (, {, [, "
                while (!core.isEmpty() && isPunctuation(core.charAt(0))) {
                    prefix += core.charAt(0);
                    core = core.substring(1);
                }

                // Extract trailing punctuation: ), }, ], ., ,, ;, :, /, "
                while (!core.isEmpty() && isPunctuation(core.charAt(core.length() - 1))) {
                    suffix = core.charAt(core.length() - 1) + suffix;
                    core = core.substring(0, core.length() - 1);
                }

                // Convert the core if it's still Wylie-like
                if (!core.isEmpty() && isLikelyWylie(core)) {
                    String converted = WylieConverter.toUnicode(core);
                    result.append(prefix).append(converted).append(suffix);
                } else {
                    result.append(token);
                }
            } else {
                // Not Wylie - keep as is
                result.append(token);
            }

            // Add space between tokens (except last)
            if (i < tokens.length - 1) {
                result.append(" ");
            }
        }

        return result.toString();
    }

    /**
     * Check if a token is likely Wylie transliteration
     * Uses heuristics to distinguish from English words
     */
    private boolean isLikelyWylie(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        String clean = token.toLowerCase();

        // Remove common punctuation for analysis
        clean = clean.replaceAll("[\\(\\)\\[\\]{}.,;:/\\\"']", "");

        if (clean.isEmpty()) {
            return false;
        }

        // Definitely NOT Wylie if:
        // 1. All caps Roman numerals: I, II, III, IV, V, etc.
        if (clean.matches("^[ivxlcdm]+$") && token.equals(token.toUpperCase())) {
            return false;
        }

        // 2. Pure numbers or years
        if (clean.matches("^\\d+$")) {
            return false;
        }

        // 3. Common English words that look like Wylie (EXPANDED LIST)
        String[] commonEnglishWords = {
            // Citations
            "coll", "vol", "see", "cf", "ed", "trans", "ibid", "op", "cit", "etc",
            "lit", "fig", "fn", "footnote", "note", "ref", "source",
            // Grammar terms
            "verb", "noun", "adj", "adv", "prep", "conj", "arch", "archaic",
            // Common English words that might appear
            "a", "an", "the", "of", "in", "on", "at", "to", "for", "by", "with",
            "is", "are", "was", "were", "be", "been", "being",
            "and", "or", "but", "if", "as", "that", "this", "these", "those",
            "one", "two", "three", "four", "five", "also", "only", "own", "effect",
            "from", "into", "through", "during", "before", "after", "above", "below",
            "not", "no", "yes", "all", "some", "any", "each", "every", "both",
            // Words that could be confused with Wylie
            "lay", "participant", "ritual", "term", "heard", "used", "animal", "class"
        };

        for (String word : commonEnglishWords) {
            if (clean.equals(word)) {
                return false;
            }
        }

        // 4. English abbreviations/markers with periods or capitals
        if (token.matches("^[A-Z]{2,}$")) {  // All caps: ND, PH, etc.
            return false;
        }

        // Likely Wylie if has typical Tibetan patterns:
        // 1. Contains apostrophe (common in Wylie): 'a, nga', dang'
        if (token.contains("'")) {
            return true;
        }

        // 2. Has double consonants typical of Wylie: rgy, sgy, bgy, dby, etc.
        if (clean.matches(".*(rgy|sgy|bgy|dby|mgy|bry|gry|kry|pry|try|dry|bsy|gsy|brd|brl|bya|mya).*")) {
            return true;
        }

        // 3. Contains common Tibetan syllables
        String[] tibetanSyllables = {"nga", "gya", "nya", "dza", "tsa", "zha", "bya", "mya", "kha", "gha", "cha", "ja", "tha", "dha", "pha", "bha"};
        for (String syllable : tibetanSyllables) {
            if (clean.contains(syllable)) {
                return true;
            }
        }

        // 4. Ends with common Tibetan suffixes: pa, ba, ma, la, ra, sa, etc.
        // BUT exclude common English words ending in these letters
        if (clean.matches(".*(pa|ba|ma|la|ra|sa|ta|da|na|ga|ka)s?$")) {
            // Double-check it's not a common English word
            String[] englishExceptions = {"spa", "data", "visa", "agenda", "canada", "pasta"};
            for (String eng : englishExceptions) {
                if (clean.equals(eng)) {
                    return false;
                }
            }
            return true;
        }

        // 5. Short (1-2 chars) and all lowercase - likely Wylie particle
        // BUT not common English words
        if (clean.length() <= 2 && clean.equals(clean.toLowerCase())) {
            String[] shortEnglish = {"a", "an", "as", "at", "be", "by", "do", "go",
                                     "he", "if", "in", "is", "it", "me", "my", "no",
                                     "of", "on", "or", "so", "to", "up", "us", "we"};
            for (String eng : shortEnglish) {
                if (clean.equals(eng)) {
                    return false;
                }
            }
            return true;
        }

        // Otherwise, probably English
        return false;
    }

    /**
     * Check if character is punctuation
     */
    private boolean isPunctuation(char c) {
        return "()[]{}.,;:/\\\"'".indexOf(c) >= 0;
    }

    /**
     * Convert Wylie content inside curly braces to Tibetan Unicode
     * Handles patterns like: {rnam shes/}, {a ཕ zhes...}, {thugs rje che}
     * Used for ITLR, Negi, HOTL, and other dictionaries that use curly braces
     * for Tibetan content
     */
    private String convertCurlyBraceWylie(String text) {
        if (text == null || text.isEmpty() || !text.contains("{")) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int pos = 0;

        while (pos < text.length()) {
            int openBrace = text.indexOf('{', pos);

            if (openBrace == -1) {
                // No more curly braces - append rest of text
                result.append(text.substring(pos));
                break;
            }

            // Append text before the opening brace
            result.append(text.substring(pos, openBrace));

            // Find matching closing brace
            int closeBrace = text.indexOf('}', openBrace);

            if (closeBrace == -1) {
                // No closing brace - append rest of text as-is
                result.append(text.substring(openBrace));
                break;
            }

            // Extract content inside braces
            String content = text.substring(openBrace + 1, closeBrace);

            // Convert the Wylie content to Tibetan Unicode
            String converted = convertBracedContent(content);

            // Append with braces preserved
            result.append("{").append(converted).append("}");

            // Move past the closing brace
            pos = closeBrace + 1;
        }

        return result.toString();
    }

    /**
     * Convert Wylie content inside a single pair of curly braces
     * Handles mixed Wylie/Tibetan Unicode content
     */
    private String convertBracedContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        StringBuilder result = new StringBuilder();
        StringBuilder wylieBuffer = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            // Check if this is Tibetan Unicode (already converted)
            if (c >= '\u0F00' && c <= '\u0FFF') {
                // Flush any pending Wylie
                if (wylieBuffer.length() > 0) {
                    String converted = WylieConverter.toUnicode(wylieBuffer.toString());
                    result.append(converted);
                    wylieBuffer.setLength(0);
                }
                // Append the Tibetan Unicode character as-is
                result.append(c);
            } else {
                // Accumulate Wylie characters
                wylieBuffer.append(c);
            }
        }

        // Flush any remaining Wylie
        if (wylieBuffer.length() > 0) {
            String converted = WylieConverter.toUnicode(wylieBuffer.toString());
            result.append(converted);
        }

        return result.toString();
    }

    /**
     * Build context for Tibetan-only dictionaries
     * Includes all synonyms and original Wylie
     */
    private String buildTibetanContext(String targetText, String sourceWylie) {
        StringBuilder context = new StringBuilder();

        // If target has multiple synonyms, include all of them
        if (targetText.contains("/\\n")) {
            String[] synonyms = targetText.split("/\\\\n");
            context.append("Synonyms (Wylie): ");
            for (int i = 0; i < synonyms.length; i++) {
                context.append(synonyms[i].trim());
                if (i < synonyms.length - 1) {
                    context.append("; ");
                }
            }
            context.append("\n");
        }

        // Add source Wylie for reference
        context.append("Source Wylie: ").append(sourceWylie);

        return context.toString();
    }

    /**
     * Process Sanskrit target with SLP1→IAST conversion
     * Preserves citation markers like [C], [MSA], [LCh]
     */
    private String processSanskritTarget(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Pattern to match citation markers and Sanskrit text
        // Example: [C]vā; [C]athavā-api
        StringBuilder result = new StringBuilder();
        String[] segments = text.split(";");

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();

            // Extract citation marker if present [C], [MSA], etc.
            String citation = "";
            String sanskritPart = segment;

            if (segment.startsWith("[")) {
                int endBracket = segment.indexOf("]");
                if (endBracket > 0) {
                    citation = segment.substring(0, endBracket + 1);
                    sanskritPart = segment.substring(endBracket + 1);
                }
            }

            // Convert SLP1 to IAST
            String convertedSanskrit = Slp1Converter.convert(sanskritPart);

            // Rebuild with citation
            if (!citation.isEmpty()) {
                result.append(citation);
            }
            result.append(convertedSanskrit);

            if (i < segments.length - 1) {
                result.append("; ");
            }
        }

        return result.toString();
    }

    /**
     * Smart split for long definitions
     * Returns: [0] = main definition (first sentence or ~100 chars)
     *          [1] = full context (complete definition + Wylie reference)
     */
    private String[] splitDefinition(String definition) {
        String[] result = new String[2];

        if (definition == null || definition.isEmpty()) {
            result[0] = "";
            result[1] = "";
            return result;
        }

        // Strategy 0: Split on FIRST newline if present
        // Everything before first newline goes to target_term
        // Everything after goes to context
        int firstNewline = definition.indexOf("\\n");
        if (firstNewline > 0) {
            String firstPart = definition.substring(0, firstNewline).trim();
            String restPart = definition.substring(firstNewline + 2).trim(); // Skip \n

            // First part becomes the target_term (may still be long, so check)
            if (firstPart.length() > 150) {
                // First part is still too long, apply further truncation
                result[0] = truncateToSentence(firstPart);
            } else {
                result[0] = firstPart;
            }

            // Everything after newline goes to context
            result[1] = restPart.isEmpty() ? firstPart : restPart;
            return result;
        }

        // Strategy 1: If definition is short (<150 chars), use as-is
        if (definition.length() <= 150) {
            result[0] = definition;
            result[1] = definition;
            return result;
        }

        // Strategy 2: Find first sentence (period, exclamation, or question mark)
        int firstSentenceEnd = -1;
        String[] sentenceEnders = {". ", "! ", "? ", "། "};

        for (String ender : sentenceEnders) {
            int pos = definition.indexOf(ender);
            if (pos > 20 && pos < 200) { // Must be reasonable length
                if (firstSentenceEnd == -1 || pos < firstSentenceEnd) {
                    firstSentenceEnd = pos + ender.length();
                }
            }
        }

        if (firstSentenceEnd > 0) {
            result[0] = definition.substring(0, firstSentenceEnd).trim();
            result[1] = "Full definition: " + definition;
            return result;
        }

        // Strategy 3: Truncate at ~100 chars on word boundary
        int truncateAt = Math.min(100, definition.length());
        int lastSpace = definition.lastIndexOf(' ', truncateAt);

        if (lastSpace > 50) {
            result[0] = definition.substring(0, lastSpace).trim() + "...";
            result[1] = "Full definition: " + definition;
        } else {
            // Last resort: just truncate
            result[0] = definition.substring(0, Math.min(100, definition.length())).trim() + "...";
            result[1] = "Full definition: " + definition;
        }

        return result;
    }

    /**
     * Helper method to truncate text to first sentence
     */
    private String truncateToSentence(String text) {
        if (text.length() <= 150) {
            return text;
        }

        // Find first sentence
        String[] sentenceEnders = {". ", "! ", "? ", "། "};
        int firstSentenceEnd = -1;

        for (String ender : sentenceEnders) {
            int pos = text.indexOf(ender);
            if (pos > 20 && pos < 200) {
                if (firstSentenceEnd == -1 || pos < firstSentenceEnd) {
                    firstSentenceEnd = pos + ender.length();
                }
            }
        }

        if (firstSentenceEnd > 0) {
            return text.substring(0, firstSentenceEnd).trim();
        }

        // Truncate at 100 chars
        int lastSpace = text.lastIndexOf(' ', 100);
        if (lastSpace > 50) {
            return text.substring(0, lastSpace).trim() + "...";
        }

        return text.substring(0, Math.min(100, text.length())).trim() + "...";
    }

    /**
     * Enhanced truncation for long Tibetan definitions
     * Uses Tibetan punctuation marks for natural break points
     *
     * @param unicodeText Already converted Tibetan Unicode text
     * @param wylieText Original Wylie text
     * @return [0] = truncated display text, [1] = full context
     */
    private String[] splitTibetanDefinition(String unicodeText, String wylieText) {
        String[] result = new String[2];

        if (unicodeText == null || unicodeText.isEmpty()) {
            result[0] = "";
            result[1] = "";
            return result;
        }

        // Strategy 0: Split on FIRST newline if present (looking for \n in ORIGINAL wylie)
        // Check if the original wylie text has newlines
        int firstNewlineWylie = wylieText.indexOf("\\n");
        if (firstNewlineWylie > 0) {
            String firstPartWylie = wylieText.substring(0, firstNewlineWylie).trim();
            String restPartWylie = wylieText.substring(firstNewlineWylie + 2).trim(); // Skip \n

            // Convert first part only for display
            String firstPartUnicode = WylieConverter.toUnicode(firstPartWylie);

            // First part becomes the target_term (may still be long, so check)
            if (firstPartUnicode.length() > 150) {
                result[0] = truncateTibetanToNaturalBreak(firstPartUnicode);
            } else {
                result[0] = firstPartUnicode;
            }

            // Everything after newline goes to context (convert to Unicode)
            if (!restPartWylie.isEmpty()) {
                String restPartUnicode = WylieConverter.toUnicode(restPartWylie);
                result[1] = restPartUnicode + "\nOriginal Wylie: " + wylieText;
            } else {
                result[1] = firstPartUnicode + "\nOriginal Wylie: " + wylieText;
            }
            return result;
        }

        // Strategy 1: If definition is short (<150 chars), use as-is
        if (unicodeText.length() <= 150) {
            result[0] = unicodeText;
            result[1] = unicodeText + "\nOriginal Wylie: " + wylieText;
            return result;
        }

        // Strategy 2: Find natural Tibetan break points
        // Tibetan sentence ender: ། (U+0F0D shad)
        // Common particles: ཏེ་ (te), སོགས་ (sogs), སྟེ་ (ste)
        int breakPoint = -1;

        // Look for shad (།) within first 150 chars
        for (int i = 50; i < Math.min(150, unicodeText.length()); i++) {
            char c = unicodeText.charAt(i);
            if (c == '\u0F0D') { // Tibetan shad (།)
                breakPoint = i + 1;
                break;
            }
        }

        // If found good break point, use it
        if (breakPoint > 0 && breakPoint < 200) {
            result[0] = unicodeText.substring(0, breakPoint).trim();
            result[1] = "Full definition: " + unicodeText + "\nOriginal Wylie: " + wylieText;
            return result;
        }

        // Strategy 3: Look for tsheg space + common particles
        String[] particles = {"ཏེ་", "སོགས་", "སྟེ་", "ལ་", "དང་"};
        for (String particle : particles) {
            int pos = unicodeText.indexOf(particle, 50);
            if (pos > 0 && pos < 150) {
                breakPoint = pos + particle.length();
                result[0] = unicodeText.substring(0, breakPoint).trim();
                result[1] = "Full definition: " + unicodeText + "\nOriginal Wylie: " + wylieText;
                return result;
            }
        }

        // Strategy 4: Truncate at ~100 chars on tsheg boundary
        result[0] = truncateTibetanToNaturalBreak(unicodeText);
        result[1] = "Full definition: " + unicodeText + "\nOriginal Wylie: " + wylieText;

        return result;
    }

    /**
     * Helper to truncate Tibetan text at natural break point
     */
    private String truncateTibetanToNaturalBreak(String unicodeText) {
        int truncateAt = Math.min(100, unicodeText.length());
        int breakPoint = -1;

        // Look for Tibetan tsheg (་) - word separator
        for (int i = truncateAt; i > 50 && i < unicodeText.length(); i--) {
            if (unicodeText.charAt(i) == '\u0F0B') { // Tibetan tsheg (་)
                breakPoint = i + 1;
                break;
            }
        }

        if (breakPoint > 50) {
            return unicodeText.substring(0, breakPoint).trim() + "...";
        } else {
            // Last resort: just truncate
            return unicodeText.substring(0, Math.min(100, unicodeText.length())).trim() + "...";
        }
    }

    /**
     * Clean IAST Sanskrit text (already in IAST, not SLP1)
     * Handles special notation like n^a → nā, multiple forms
     */
    private String cleanIASTSanskrit(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Handle special notations in Yogacharabhumi glossary
        String cleaned = text;

        // Fix circumflex notation: n^a → nā, A → Ā, etc.
        cleaned = cleaned.replaceAll("([aeiou])\\^([aeiou])", "$1\u0304$2"); // Add macron
        cleaned = cleaned.replaceAll("n\\^a", "nā");
        cleaned = cleaned.replaceAll("^A", "Ā");

        // Clean up multiple forms separated by periods or semicolons
        // Example: "kevala. n^anyatra" → "kevala; nānyatra"
        cleaned = cleaned.replaceAll("\\s*\\.\\s+", "; ");

        // Remove leading/trailing slashes
        cleaned = cleaned.replaceAll("^/|/$", "");

        return cleaned.trim();
    }

    /**
     * Extract citation markers from Sanskrit text
     * Returns formatted string describing citations
     */
    private String extractCitations(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        List<String> citations = new ArrayList<>();

        // Extract [C], [MSA], [LCh], [L] style markers
        if (text.contains("[C]")) {
            citations.add("[C] = Candra (Chandravyākaraṇa)");
        }
        if (text.contains("[MSA]")) {
            citations.add("[MSA] = Mahāyānasūtrālaṃkāra");
        }
        if (text.contains("[LCh]")) {
            citations.add("[LCh] = Lokesh Chandra");
        }
        if (text.contains("[L]")) {
            citations.add("[L] = Lalitavistara");
        }

        if (citations.isEmpty()) {
            return "";
        }

        return "Citation sources: " + String.join("; ", citations);
    }

    /**
     * Check if dictionary is a HOTL (Heart of Tibetan Language) dictionary
     * These have audio files that need to be linked
     */
    private boolean isHOTLDictionary(String filename) {
        String lower = filename.toLowerCase();
        return lower.contains("hotl") ||
               lower.contains("67-hotl");
    }

    /**
     * Extract audio file reference from HOTL dictionary entry
     * Pattern: [sound:filename.mp3]
     * Returns: [text without sound tag, audio filename]
     */
    private String[] extractAudioReference(String text, String dictionaryName) {
        String[] result = new String[2];
        result[0] = text; // Default: original text
        result[1] = "";   // Default: no audio

        if (text == null || !text.contains("[sound:")) {
            return result;
        }

        // Find [sound:filename.mp3] pattern
        int soundStart = text.indexOf("[sound:");
        int soundEnd = text.indexOf("]", soundStart);

        if (soundStart >= 0 && soundEnd > soundStart) {
            // Extract filename
            String soundTag = text.substring(soundStart + 7, soundEnd); // Skip "[sound:"
            String filename = soundTag.trim();

            // Determine which HOTL volume based on dictionary name
            String volume = "hotl1"; // Default
            if (dictionaryName.toLowerCase().contains("hotl2")) {
                volume = "hotl2";
            } else if (dictionaryName.toLowerCase().contains("hotl3")) {
                volume = "hotl3";
            }

            // Remove [sound:...] tag from text
            String cleanedText = text.substring(0, soundStart) + text.substring(soundEnd + 1);
            cleanedText = cleanedText.trim();

            // Handle \n after [sound:...] tag
            if (cleanedText.startsWith("\\n")) {
                cleanedText = cleanedText.substring(2).trim();
            }

            result[0] = cleanedText;
            result[1] = volume + "/" + filename;
        }

        return result;
    }

    /**
     * Create audio link for notes field
     * Returns HTML link to Netlify-hosted audio file
     */
    private String createAudioLink(String audioPath) {
        if (audioPath == null || audioPath.isEmpty()) {
            return "";
        }

        // Netlify base URL for audio files (from rel-site-2025 repo: netlify/audio/)
        String baseUrl = "https://glossariobudacompact.netlify.app/netlify/audio/";
        String fullUrl = baseUrl + audioPath;

        // Create clickable audio link
        return "Audio: <a href=\"" + fullUrl + "\" target=\"_blank\">🔊 Play Audio</a>";
    }

    /**
     * Create PDF reference link for scan dictionaries with page numbers
     * These dictionaries have numbers as target_term which are page references
     *
     * @param filename Dictionary filename
     * @param targetTerm The target term (page number)
     * @return HTML link to PDF at specific page, or empty string if not applicable
     */
    private String createPdfReferenceLink(String filename, String targetTerm) {
        if (filename == null || targetTerm == null || targetTerm.isEmpty()) {
            return "";
        }

        // Detect which PDF reference dictionary this is
        String pdfName = null;
        String dictionaryName = null;

        if (filename.contains("63-Mahavyutpatti-Scan") || filename.contains("Mahavyutpatti-Scan-1989")) {
            pdfName = "Mahavyutpatti.pdf";
            dictionaryName = "Mahāvyutpatti";
        } else if (filename.contains("64-sgra-sbyor") || filename.contains("sgra-sbyor-bam-po-gnyis-pa")) {
            pdfName = "sgra-sbyor.pdf";
            dictionaryName = "sGra sbyor bam po gnyis pa";
        } else if (filename.contains("65-ChandraDas") || filename.contains("ChandraDas_Scan")) {
            pdfName = "ChandraDas.pdf";
            dictionaryName = "Chandra Das Dictionary";
        } else if (filename.contains("66-Jaeschke") || filename.contains("Jaeschke_Scan")) {
            pdfName = "Jaeschke.pdf";
            dictionaryName = "Jäschke Dictionary";
        }

        // If not a PDF reference dictionary, return empty
        if (pdfName == null) {
            return "";
        }

        // Extract page number from target term (may have "?" or other chars)
        String pageNumber = targetTerm.replaceAll("[^0-9]", "").trim();

        if (pageNumber.isEmpty()) {
            return "";
        }

        // Netlify base URL for PDF files
        String baseUrl = "https://glossariobudacompact.netlify.app/netlify/pdfs/";
        String fullUrl = baseUrl + pdfName + "#page=" + pageNumber;

        // Create clickable PDF link with page reference
        return "📄 Reference: <a href=\"" + fullUrl + "\" target=\"_blank\">" +
               dictionaryName + " page " + pageNumber + "</a>";
    }
}
