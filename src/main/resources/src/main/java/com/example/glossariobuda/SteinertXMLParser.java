package com.example.glossariobuda;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Steinert's Heart of Tibetan Language XML files
 *
 * Source: Heart of Tibetan Language by Franziska Oertle
 * Publisher: Dharma Publishing (dharmapublishing.com)
 * ISBNs: Vol 1: 978-0-89800-233-1, Vol 2: 978-0-89800-308-6
 *
 * XML Structure:
 * <entries>
 *   <entry>
 *     <field name="བོད་སྐད།">ཐུགས་རྗེ་ཆེ།</field>
 *     <field name="Wylie">thugs rje che</field>
 *     <field name="English">Thank you!</field>
 *     <field name="Morphemes">...</field>
 *     ...
 *   </entry>
 * </entries>
 */
public class SteinertXMLParser implements FormatParser {

    private static final Pattern SOUND_PATTERN = Pattern.compile("\\[sound:[^\\]]+\\]");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern CURLY_BRACE_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    @Override
    public List<GlossaryTerm> parse(File file, LoadProgressCallback callback) throws Exception {
        List<GlossaryTerm> terms = new ArrayList<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // XXE Prevention
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setXIncludeAware(false);
        dbFactory.setExpandEntityReferences(false);

        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);
        doc.getDocumentElement().normalize();

        // Detect which volume from filename
        String volume = detectVolume(file.getName());

        // Get all entry elements
        NodeList entryList = doc.getElementsByTagName("entry");
        int totalEntries = entryList.getLength();

        if (callback != null) {
            callback.onProgress(0, totalEntries,
                "Heart of Tibetan Language " + volume + ": " + totalEntries + " termos encontrados");
        }

        // Process each entry
        for (int i = 0; i < entryList.getLength(); i++) {
            Node entryNode = entryList.item(i);

            if (entryNode.getNodeType() == Node.ELEMENT_NODE) {
                Element entryElement = (Element) entryNode;
                try {
                    GlossaryTerm term = parseEntry(entryElement, volume, file.getName());
                    if (term != null) {
                        terms.add(term);
                    }
                } catch (Exception e) {
                    System.err.println("[SteinertXMLParser] Erro ao processar entrada " + (i + 1) + ": " + e.getMessage());
                }
            }

            if (callback != null && i % 100 == 0) {
                callback.onProgress(i, totalEntries,
                    "Processando entrada " + (i + 1) + " de " + totalEntries);
            }
        }

        if (callback != null) {
            callback.onProgress(totalEntries, totalEntries,
                "Heart of Tibetan Language " + volume + " processado!");
        }

        return terms;
    }

    /**
     * Parse a single entry element
     */
    private GlossaryTerm parseEntry(Element entryElement, String volume, String filename) {
        GlossaryTerm term = new GlossaryTerm();

        // Extract all fields from the entry
        Map<String, String> fields = extractFields(entryElement);

        // Set Tibetan (source) - handle both formats
        String tibetan = getFieldValue(fields, "བོད་སྐད།", "word, tibetan");
        String wylie = getFieldValue(fields, "Wylie");

        // Check if `བོད་སྐད།` field contains Wylie (has latin letters) or is already Tibetan Unicode
        if (tibetan != null && !tibetan.isEmpty()) {
            String cleanedTibetan = cleanText(tibetan);

            // If contains latin letters, it's Wylie that needs conversion
            String tibetanUnicode;
            if (cleanedTibetan.matches(".*[a-zA-Z].*")) {
                tibetanUnicode = WylieConverter.toUnicode(cleanedTibetan);
            } else {
                tibetanUnicode = cleanedTibetan;
            }

            // Apply newline splitting
            String[] splitResult = splitOnNewline(tibetanUnicode);
            term.setSourceTerm(splitResult[0]);
            term.setSourceLanguage("Tibetan");

            if (!splitResult[1].equals(splitResult[0])) {
                term.setContext(splitResult[1]);
            }
        }
        // If no `བོད་སྐད།` field, use Wylie field
        else if (wylie != null && !wylie.isEmpty()) {
            String cleanedWylie = cleanText(wylie);
            String tibetanUnicode = WylieConverter.toUnicode(cleanedWylie);

            String[] splitResult = splitOnNewline(tibetanUnicode);
            term.setSourceTerm(splitResult[0]);
            term.setSourceLanguage("Tibetan");

            if (!splitResult[1].equals(splitResult[0])) {
                term.setContext(splitResult[1]);
            }
        }

        // Always preserve Wylie in the wylie field for reference
        if (wylie != null && !wylie.isEmpty()) {
            term.setWylie(cleanText(wylie));
        }

        // Set English (target) - handle both formats
        String english = getFieldValue(fields, "English", "word, english");
        if (english != null && !english.isEmpty()) {
            term.setTargetTerm(cleanText(english));
            term.setTargetLanguage("English");
        }

        // Set Sanskrit (from IAST field)
        String iast = getFieldValue(fields, "IAST");
        if (iast != null && !iast.isEmpty()) {
            term.setSanskrit(cleanText(iast));
        }

        // Build context from example sentences (append to existing context if any)
        String exampleContext = buildContext(fields);
        if (exampleContext != null && !exampleContext.isEmpty()) {
            String existingContext = term.getContext();
            if (existingContext != null && !existingContext.isEmpty()) {
                // Append examples to existing context from newline splitting
                term.setContext(existingContext + "\n\n" + exampleContext);
            } else {
                term.setContext(exampleContext);
            }
        }

        // Build notes from morphemes, synonyms, and other meanings
        StringBuilder notesBuilder = new StringBuilder();
        String baseNotes = buildNotes(fields);
        if (baseNotes != null && !baseNotes.isEmpty()) {
            notesBuilder.append(baseNotes);
        }

        // Add audio link for HOTL dictionaries
        if (isHOTLDictionary(filename)) {
            // Check if target term has audio reference
            String targetForAudio = term.getTargetTerm();
            if (targetForAudio != null && targetForAudio.contains("[sound:")) {
                String[] audioResult = extractAudioReference(targetForAudio, filename);
                term.setTargetTerm(audioResult[0]); // Update target without sound tag
                String audioPath = audioResult[1];

                if (!audioPath.isEmpty()) {
                    String audioLink = createAudioLink(audioPath);
                    if (notesBuilder.length() > 0) {
                        notesBuilder.append("\n");
                    }
                    notesBuilder.append(audioLink);
                }
            }
        }

        // Add PDF reference link for scan dictionaries with page numbers
        String pdfLink = createPdfReferenceLink(filename, term.getTargetTerm());
        if (pdfLink != null && !pdfLink.isEmpty()) {
            if (notesBuilder.length() > 0) {
                notesBuilder.append("\n");
            }
            notesBuilder.append(pdfLink);
        }

        // Set final notes
        if (notesBuilder.length() > 0) {
            term.setNotes(notesBuilder.toString());
        }

        // Set contributor with proper attribution
        term.setContributor(buildContributor(volume));

        // Only return if we have at least source and target
        if (term.getSourceTerm() != null && !term.getSourceTerm().isEmpty() &&
            term.getTargetTerm() != null && !term.getTargetTerm().isEmpty()) {
            return term;
        }

        return null;
    }

    /**
     * Get field value trying multiple field names (for different formats)
     */
    private String getFieldValue(Map<String, String> fields, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = fields.get(fieldName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Extract all fields from an entry element into a map
     */
    private Map<String, String> extractFields(Element entryElement) {
        Map<String, String> fields = new HashMap<>();

        NodeList fieldNodes = entryElement.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element fieldElement = (Element) fieldNodes.item(i);
            String name = fieldElement.getAttribute("name");
            String value = fieldElement.getTextContent();

            if (name != null && !name.isEmpty() && value != null) {
                fields.put(name, value);
            }
        }

        return fields;
    }

    /**
     * Build context from example sentences
     */
    private String buildContext(Map<String, String> fields) {
        StringBuilder context = new StringBuilder();

        // English example - handle both formats
        String exampleEn = getFieldValue(fields, "Example", "sentence, english");
        if (exampleEn != null && !exampleEn.isEmpty()) {
            context.append("Example: ").append(cleanText(exampleEn)).append("\n");
        }

        // Tibetan example - handle both formats
        String exampleTib = getFieldValue(fields, "དཔེར་བརྗོད།", "Sentence, tibetan");
        if (exampleTib != null && !exampleTib.isEmpty()) {
            String cleaned = cleanText(exampleTib);
            // Extract Tibetan text from curly braces if present
            Matcher matcher = CURLY_BRACE_PATTERN.matcher(cleaned);
            if (matcher.find()) {
                context.append("དཔེར་བརྗོད།: ").append(matcher.group(1).trim()).append("\n");
            } else {
                context.append("དཔེར་བརྗོད།: ").append(cleaned).append("\n");
            }
        }

        // Spanish example (optional)
        String exampleEs = fields.get("Ejemplo");
        if (exampleEs != null && !exampleEs.isEmpty()) {
            context.append("Ejemplo: ").append(cleanText(exampleEs)).append("\n");
        }

        return context.toString().trim();
    }

    /**
     * Build notes from morphemes, synonyms, and other meanings
     */
    private String buildNotes(Map<String, String> fields) {
        StringBuilder notes = new StringBuilder();

        // Other meanings/acceptions
        String acceptions = fields.get("Acceptions");
        if (acceptions != null && !acceptions.isEmpty()) {
            notes.append("Other meanings: ").append(cleanText(acceptions)).append("\n\n");
        }

        // Morpheme breakdown - handle both formats
        String morphemes = getFieldValue(fields, "Morphemes", "syllables");
        if (morphemes != null && !morphemes.isEmpty()) {
            String cleaned = cleanText(morphemes);
            notes.append("Syllable breakdown:\n").append(cleaned).append("\n\n");
        }

        // Synonyms
        String synonyms = fields.get("Synonymous");
        if (synonyms != null && !synonyms.isEmpty()) {
            String cleaned = cleanText(synonyms);
            // Extract from curly braces if present
            Matcher matcher = CURLY_BRACE_PATTERN.matcher(cleaned);
            if (matcher.find()) {
                notes.append("Synonyms: ").append(matcher.group(1).trim()).append("\n\n");
            }
        }

        // Additional notes
        String additionalNotes = fields.get("Notes");
        if (additionalNotes != null && !additionalNotes.isEmpty()) {
            notes.append(cleanText(additionalNotes)).append("\n");
        }

        return notes.toString().trim();
    }

    /**
     * Build proper contributor attribution
     * Short citation for legal compliance
     */
    private String buildContributor(String volume) {
        return String.format(
            "Oertle, F. - Heart of Tibetan Language %s (Dharma Publishing)",
            volume
        );
    }

    /**
     * Get ISBN for volume
     */
    private String getISBN(String volume) {
        switch (volume) {
            case "Vol. 1":
                return "978-0-89800-233-1";
            case "Vol. 2":
                return "978-0-89800-308-6";
            case "Vol. 3":
                return "forthcoming";
            default:
                return "unknown";
        }
    }

    /**
     * Detect volume from filename
     */
    private String detectVolume(String filename) {
        if (filename.contains("hotl1")) {
            return "Vol. 1";
        } else if (filename.contains("hotl2")) {
            return "Vol. 2";
        } else if (filename.contains("hotl3")) {
            return "Vol. 3";
        }
        return "Unknown";
    }

    /**
     * Clean text: remove audio markers, HTML tags, excessive whitespace
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        // Remove sound markers: [sound:1-1a.mp3]
        text = SOUND_PATTERN.matcher(text).replaceAll("");

        // Remove HTML tags but preserve content
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");

        // Decode common HTML entities
        text = text.replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"");

        // Clean up whitespace
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }

    @Override
    public boolean canParse(File file) {
        if (!file.getName().toLowerCase().endsWith(".xml")) {
            return false;
        }

        // Check if it's a Steinert HOTL file
        String name = file.getName().toLowerCase();
        if (name.contains("hotl")) {
            return true;
        }

        // Otherwise, peek inside to check for <entries> root with <field name=""> structure
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);

            Element root = doc.getDocumentElement();

            // Check if root is "entries" and has entry/field structure
            if (!"entries".equals(root.getNodeName())) {
                return false;
            }

            NodeList entries = root.getElementsByTagName("entry");
            if (entries.getLength() == 0) {
                return false;
            }

            Element firstEntry = (Element) entries.item(0);
            NodeList fields = firstEntry.getElementsByTagName("field");

            // Check if it has field elements with "name" attribute
            return fields.getLength() > 0 &&
                   ((Element) fields.item(0)).hasAttribute("name");

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getFileExtension() {
        return "xml";
    }

    @Override
    public String getFormatDescription() {
        return "Steinert HOTL XML (Heart of Tibetan Language)";
    }

    @Override
    public ImportMetadata extractMetadata(File file) {
        String volume = detectVolume(file.getName());
        String suggestedOwner = "Steinert";

        return new ImportMetadata(suggestedOwner, file.getName());
    }

    /**
     * ENHANCED Wylie conversion with:
     * 1. Apostrophe preservation (leading ' = འ, trailing ' = a-chung)
     * 2. Tsheg spacing between Tibetan words
     * 3. Mixed content support
     */
    private String enhancedWylieConversion(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Split on spaces to process token by token
        String[] tokens = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        boolean prevWasTibetan = false;

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String processedToken;
            boolean currentIsTibetan;

            // Skip if it looks like English or numbers
            if (!containsWylie(token)) {
                processedToken = token;
                currentIsTibetan = false;
            } else {
                // Extract punctuation BUT preserve Wylie apostrophes
                String prefix = "";
                String suffix = "";
                String core = token;

                // Extract leading punctuation, BUT preserve leading apostrophes if they're part of Wylie
                // In Wylie: 'di'i → འདིའི (apostrophe at start represents the letter འ)
                while (!core.isEmpty() && isPunctuation(core.charAt(0))) {
                    char firstChar = core.charAt(0);
                    if (firstChar == '\'' && core.length() > 1) {
                        char nextChar = core.charAt(1);
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
                    if (lastChar == '\'' && core.length() > 1) {
                        char prevChar = core.charAt(core.length() - 2);
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
                } else {
                    try {
                        String converted = WylieConverter.toUnicode(core);
                        processedToken = prefix + converted + suffix;
                        // Check if converted text contains Tibetan characters (U+0F00-U+0FFF)
                        currentIsTibetan = converted.matches(".*[\u0F00-\u0FFF].*");
                    } catch (Exception e) {
                        processedToken = token;
                        currentIsTibetan = false;
                    }
                }
            }

            // Add separator before appending (except for first token)
            if (i > 0) {
                // Use tsheg between Tibetan words, space otherwise
                if (prevWasTibetan && currentIsTibetan) {
                    result.append("་"); // Tibetan tsheg (U+0F0B)
                } else {
                    result.append(" "); // Regular space
                }
            }

            result.append(processedToken);
            prevWasTibetan = currentIsTibetan;
        }

        return result.toString();
    }

    /**
     * Split definition on first newline if present
     * Returns [displayTerm, fullContext]
     */
    private String[] splitOnNewline(String text) {
        String[] result = new String[2];

        if (text == null || text.isEmpty()) {
            result[0] = "";
            result[1] = "";
            return result;
        }

        // Look for literal \n (two characters)
        int firstNewline = text.indexOf("\\n");
        if (firstNewline > 0) {
            String firstPart = text.substring(0, firstNewline).trim();
            String restPart = text.substring(firstNewline + 2).trim(); // Skip \n

            result[0] = firstPart;
            result[1] = restPart.isEmpty() ? firstPart : text; // Full text in context
            return result;
        }

        // No newline found
        result[0] = text;
        result[1] = text;
        return result;
    }

    /**
     * Check if text contains Wylie transliteration
     */
    private boolean containsWylie(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Check for common Wylie patterns
        // Tibetan consonants: k, kh, g, ng, c, ch, j, ny, t, th, d, n, p, ph, b, m, ts, tsh, dz, w, zh, z, ', y, r, l, sh, s, h, a
        return text.matches(".*[kgcjtnpbmwzyrlsh]+[aeiou]*.*");
    }

    /**
     * Check if character is punctuation (but not apostrophe in Wylie context)
     */
    private boolean isPunctuation(char c) {
        return ".,;:!?()[]{}\"<>/-+=*&^%$#@~`|\\".indexOf(c) >= 0;
    }

    /**
     * Check if dictionary is a HOTL (Heart of Tibetan Language) dictionary
     * These have audio files that need to be linked
     */
    private boolean isHOTLDictionary(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.contains("hotl") ||
               lower.contains("67-hotl");
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
        } else if (filename.contains("64-sgra-sbyor")) {
            pdfName = "sgra-sbyor.pdf";
            dictionaryName = "sGra sbyor";
        } else if (filename.contains("65-ChandraDas")) {
            pdfName = "ChandraDas.pdf";
            dictionaryName = "Chandra Das";
        } else if (filename.contains("66-Jaeschke")) {
            pdfName = "Jaeschke.pdf";
            dictionaryName = "Jäschke";
        }

        if (pdfName == null) {
            return ""; // Not a PDF reference dictionary
        }

        // Try to parse target as page number
        try {
            int pageNumber = Integer.parseInt(targetTerm.trim());

            String baseUrl = "https://glossariobudacompact.netlify.app/netlify/pdfs/";
            String fullUrl = baseUrl + pdfName + "#page=" + pageNumber;

            // Create clickable PDF link with page reference
            return "📄 Reference: <a href=\"" + fullUrl + "\" target=\"_blank\">" +
                   dictionaryName + " p." + pageNumber + "</a>";
        } catch (NumberFormatException e) {
            // Target is not a page number, don't create link
            return "";
        }
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
            if (dictionaryName != null) {
                if (dictionaryName.toLowerCase().contains("hotl2")) {
                    volume = "hotl2";
                } else if (dictionaryName.toLowerCase().contains("hotl3")) {
                    volume = "hotl3";
                }
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
}
