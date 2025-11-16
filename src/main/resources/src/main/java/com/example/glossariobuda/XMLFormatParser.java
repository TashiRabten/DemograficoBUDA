package com.example.glossariobuda;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ENHANCED XML Parser for Buddhist Glossaries
 * Fully refactored to handle all 7 edge cases identified
 */
public class XMLFormatParser implements FormatParser {

    @Override
    public List<GlossaryTerm> parse(File file, LoadProgressCallback callback) throws Exception {
        List<GlossaryTerm> terms = new ArrayList<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // XXE Prevention: Allow DOCTYPE but disable external entities
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setXIncludeAware(false);
        dbFactory.setExpandEntityReferences(false);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);
        doc.getDocumentElement().normalize();

        // ===== FIX #1: EXTRACT ROOT-LEVEL METADATA =====
        Element rootElement = doc.getDocumentElement();
        Date glossaryDate = extractGlossaryDate(rootElement);

        // Fallback to today's date if not found
        if (glossaryDate == null) {
            glossaryDate = new Date();
        }

        // Get all term elements
        NodeList termList = doc.getElementsByTagName("term");
        int totalTerms = termList.getLength();

        if (callback != null) {
            callback.onProgress(0, totalTerms, "Encontrados " + totalTerms + " termos no XML");
        }

        // Process each term
        for (int i = 0; i < termList.getLength(); i++) {
            Node termNode = termList.item(i);

            if (termNode.getNodeType() == Node.ELEMENT_NODE) {
                Element termElement = (Element) termNode;
                try {
                    GlossaryTerm term = parseTermElement(termElement, glossaryDate);
                    if (term != null) {
                        terms.add(term);
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao processar termo " + (i + 1) + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            if (callback != null && i % 100 == 0) {
                callback.onProgress(i, totalTerms, "Processando termo " + (i + 1) + " de " + totalTerms);
            }
        }

        if (callback != null) {
            callback.onProgress(totalTerms, totalTerms, "Processamento completo!");
        }

        return terms;
    }

    /**
     * FIX #1: Extract glossary creation date from root element attributes
     * Tries: created, date, timestamp, creation-date, created-date
     */
    private Date extractGlossaryDate(Element rootElement) {
        String[] dateAttributes = {"created", "date", "timestamp", "creation-date", "created-date"};

        for (String attr : dateAttributes) {
            String dateStr = rootElement.getAttribute(attr);
            if (dateStr != null && !dateStr.isEmpty()) {
                Date parsed = parseDate(dateStr);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    /**
     * Parse date from various formats (ISO 8601, simple formats)
     */
    private Date parseDate(String dateStr) {
        SimpleDateFormat[] formats = {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
                new SimpleDateFormat("yyyy-MM-dd"),
                new SimpleDateFormat("dd/MM/yyyy"),
                new SimpleDateFormat("MM/dd/yyyy")
        };

        for (SimpleDateFormat format : formats) {
            try {
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                return format.parse(dateStr);
            } catch (Exception e) {
                // Try next format
            }
        }
        return null;
    }

    @Override
    public boolean canParse(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".xml");
    }

    @Override
    public String getFileExtension() {
        return "xml";
    }

    @Override
    public String getFormatDescription() {
        return "XML (Buddhist Glossary Format)";
    }

    /**
     * Extract owner metadata from XML root element
     */
    @Override
    public ImportMetadata extractMetadata(File file) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            // XXE Prevention: Allow DOCTYPE but disable external entities
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbFactory.setXIncludeAware(false);
            dbFactory.setExpandEntityReferences(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);
            doc.getDocumentElement().normalize();

            Element rootElement = doc.getDocumentElement();

            // Try to extract owner from root element attributes
            String owner = rootElement.getAttribute("owner");
            if (owner == null || owner.isEmpty()) {
                owner = rootElement.getAttribute("maintainer");
            }
            if (owner == null || owner.isEmpty()) {
                owner = rootElement.getAttribute("source");
            }

            return new ImportMetadata(
                owner != null && !owner.isEmpty() ? owner : null,
                file.getName()
            );
        } catch (Exception e) {
            System.err.println("Error extracting metadata from XML: " + e.getMessage());
            return new ImportMetadata(null, file.getName());
        }
    }

    /**
     * Parse a term element with comprehensive field extraction
     */
    private GlossaryTerm parseTermElement(Element termElement, Date glossaryDate) {
        GlossaryTerm term = new GlossaryTerm();
        term.setDate(glossaryDate);
        term.setDateAdded(java.time.Instant.now().toString());

        TermMapping mapping = determineTermMapping(termElement);

        // WYLIE CONVERSION: If source is Tibetan and we have Wylie, convert to Unicode
        convertWylieIfNeeded(mapping);

        applyTermMapping(term, mapping);

        String notes = buildNotesSection(termElement, mapping);
        if (!notes.isEmpty()) {
            term.setNotes(notes);
        }

        String context = buildContextSection(termElement, term);
        term.setContext(context);

        String contributor = extractContributors(termElement);
        term.setContributor(contributor);

        return term;
    }

    /**
     * Convert Wylie to Tibetan Unicode if:
     * 1. Source language is Tibetan
     * 2. Source term is empty or looks like Wylie
     * 3. We have Wylie romanization
     */
    private void convertWylieIfNeeded(TermMapping mapping) {
        // Only convert if source is Tibetan language
        if (!"Tibetan".equals(mapping.sourceLanguage)) {
            return;
        }

        // If source term is empty or is Wylie, and we have romanization
        boolean sourceEmpty = mapping.sourceTerm == null || mapping.sourceTerm.isEmpty();
        boolean sourceIsWylie = WylieConverter.isWylie(mapping.sourceTerm);
        boolean hasWylie = mapping.romanization != null && !mapping.romanization.isEmpty();

        if ((sourceEmpty || sourceIsWylie) && hasWylie) {
            // Convert Wylie to Tibetan Unicode
            String tibetanUnicode = WylieConverter.toUnicode(mapping.romanization);
            mapping.sourceTerm = tibetanUnicode;

            System.out.println("[XMLFormatParser] Converted Wylie '" + mapping.romanization +
                             "' to Tibetan '" + tibetanUnicode + "'");
        }
    }

    private TermMapping determineTermMapping(Element termElement) {
        String explicitSource = getElementText(termElement, "source_term");
        String explicitTarget = getElementText(termElement, "target_term");

        if (!explicitSource.isEmpty() && !explicitTarget.isEmpty()) {
            return createExplicitMapping(termElement, explicitSource, explicitTarget);
        }

        String sourceLang = getElementTextMultiple(termElement, "source_language", "from_language");
        String targetLang = getElementTextMultiple(termElement, "target_language", "to_language");

        if (!sourceLang.isEmpty() && !targetLang.isEmpty()) {
            return mapFieldsUsingLanguages(termElement, sourceLang, targetLang);
        }

        return mapFieldsUsingHeuristics(termElement);
    }

    private TermMapping createExplicitMapping(Element termElement, String explicitSource, String explicitTarget) {
        TermMapping mapping = new TermMapping();
        mapping.sourceTerm = explicitSource;
        mapping.targetTerm = explicitTarget;
        mapping.sourceLanguage = getElementTextMultiple(termElement, "source_language", "from_language");
        mapping.targetLanguage = getElementTextMultiple(termElement, "target_language", "to_language");
        return mapping;
    }

    private void applyTermMapping(GlossaryTerm term, TermMapping mapping) {
        term.setSourceTerm(mapping.sourceTerm);
        term.setTargetTerm(mapping.targetTerm);
        term.setSourceLanguage(mapping.sourceLanguage);
        term.setTargetLanguage(mapping.targetLanguage);

        // Store Wylie romanization for Tibetan terms
        if ("Tibetan".equals(mapping.sourceLanguage) &&
            "Wylie".equals(mapping.romanizationLabel) &&
            mapping.romanization != null && !mapping.romanization.isEmpty()) {
            term.setWylie(mapping.romanization);
        }
    }

    private String buildNotesSection(Element termElement, TermMapping mapping) {
        StringBuilder notesBuilder = new StringBuilder();

        appendTypeToNotes(notesBuilder, termElement);
        appendRomanizationToNotes(notesBuilder, mapping);
        appendLanguageFieldsToNotes(notesBuilder, termElement, mapping);
        appendOriginalNotesToNotes(notesBuilder, termElement);
        appendReferencesToNotes(notesBuilder, termElement);

        return notesBuilder.toString().trim();
    }

    private void appendTypeToNotes(StringBuilder notesBuilder, Element termElement) {
        String type = getElementTextMultiple(termElement, "type", "category");
        if (!type.isEmpty()) {
            notesBuilder.append("Type: ").append(type).append("\n");
        }
    }

    private void appendRomanizationToNotes(StringBuilder notesBuilder, TermMapping mapping) {
        if (!mapping.romanization.isEmpty()) {
            notesBuilder.append(mapping.romanizationLabel).append(": ")
                    .append(mapping.romanization).append("\n");
        }
    }

    private void appendLanguageFieldsToNotes(StringBuilder notesBuilder, Element termElement, TermMapping mapping) {
        Map<String, String> allLanguageFields = extractAllLanguageFields(termElement, mapping);
        for (Map.Entry<String, String> entry : allLanguageFields.entrySet()) {
            notesBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
    }

    private void appendOriginalNotesToNotes(StringBuilder notesBuilder, Element termElement) {
        String originalNotes = getElementTextMultiple(termElement, "notes", "note", "comments");
        if (!originalNotes.isEmpty()) {
            notesBuilder.append(originalNotes).append("\n");
        }
    }

    private void appendReferencesToNotes(StringBuilder notesBuilder, Element termElement) {
        List<String> references = extractReferences(termElement);
        for (String ref : references) {
            notesBuilder.append("Ref: ").append(ref).append("\n");
        }
    }

    private String buildContextSection(Element termElement, GlossaryTerm term) {
        String definition = extractDefinition(termElement);
        String englishTranslation = getElementText(termElement, "translation");

        if (!englishTranslation.isEmpty()) {
            definition = incorporateEnglishTranslation(definition, englishTranslation, term);
        }

        return definition;
    }

    private String extractDefinition(Element termElement) {
        String definition = getElementTextMultiple(termElement, "definition", "meaning", "context", "description");

        if (definition.isEmpty()) {
            NodeList refList = termElement.getElementsByTagName("ref");
            if (refList.getLength() > 0) {
                Element refElement = (Element) refList.item(0);
                definition = getElementTextMultiple(refElement, "definition", "meaning", "context");
            }
        }

        return definition;
    }

    private String incorporateEnglishTranslation(String definition, String englishTranslation, GlossaryTerm term) {
        boolean translationIsTarget = englishTranslation.equals(term.getTargetTerm());
        boolean translationIsSource = englishTranslation.equals(term.getSourceTerm());

        if (translationIsTarget || translationIsSource) {
            return definition;
        }

        if (!definition.isEmpty() && !definition.toLowerCase().contains(englishTranslation.toLowerCase())) {
            return "English: " + englishTranslation + "\n\n" + definition;
        } else if (definition.isEmpty()) {
            return "English: " + englishTranslation;
        }

        return definition;
    }

    /**
     * Extract ALL language fields for notes section
     */
    private Map<String, String> extractAllLanguageFields(Element termElement, TermMapping mapping) {
        Map<String, String> fields = new LinkedHashMap<>();

        String[][] languageFields = {
                {"Sanskrit", "sanskrit", "skt", "sa"},
                {"Pali", "pali", "pi"},
                {"Chinese", "chinese", "zh"},
                {"Thai", "thai", "th"},
                {"Mongolian", "mongolian", "mn"},
                {"Vietnamese", "vietnamese", "vi"},
                {"Japanese", "japanese", "ja"},
                {"Korean", "korean", "ko"},
                {"Burmese", "burmese", "my"}
        };

        for (String[] langDef : languageFields) {
            String label = langDef[0];
            String[] tags = Arrays.copyOfRange(langDef, 1, langDef.length);

            String value = getElementTextMultiple(termElement, tags);
            if (!value.isEmpty()) {
                // Only add if NOT already used as source or target
                boolean isUsed = value.equals(mapping.sourceTerm) || value.equals(mapping.targetTerm);
                if (!isUsed) {
                    fields.put(label, value);
                }
            }
        }

        return fields;
    }

    /**
     * FIX #2 (Enhanced): Extract contributors with MULTIPLE support
     * Now properly handles multiple translator tags AND multiple translations
     */
    private String extractContributors(Element termElement) {
        // First try direct contributor/author tags
        String contributor = getElementTextMultiple(termElement, "contributor", "author");
        if (!contributor.isEmpty()) {
            return contributor;
        }

        String translator = getElementText(termElement, "translator");
        if (!translator.isEmpty()) {
            return translator;
        }

        // For 84000 format: check multiple translators inside ALL ref elements (not just first)
        NodeList refList = termElement.getElementsByTagName("ref");
        List<String> allTranslators = new ArrayList<>();

        for (int refIdx = 0; refIdx < refList.getLength(); refIdx++) {
            Element refElement = (Element) refList.item(refIdx);
            NodeList translatorList = refElement.getElementsByTagName("translator");

            for (int i = 0; i < translatorList.getLength(); i++) {
                String trans = translatorList.item(i).getTextContent().trim();
                if (!trans.isEmpty() && !allTranslators.contains(trans)) {
                    allTranslators.add(trans);
                }
            }
        }

        if (!allTranslators.isEmpty()) {
            return String.join(", ", allTranslators);
        }

        return "";
    }

    /**
     * FIX #2 & #3: Enhanced reference extraction with multilingual titles and links
     * Now properly extracts ALL ref elements (not just first), ALL translators, ALL titles
     */
    private List<String> extractReferences(Element termElement) {
        List<String> references = new ArrayList<>();
        NodeList refList = findReferenceElements(termElement);

        for (int i = 0; i < refList.getLength(); i++) {
            Element refElement = (Element) refList.item(i);
            String reference = buildReferenceString(refElement);

            if (!reference.isEmpty()) {
                references.add(reference);
            } else {
                addFallbackReferenceIfValid(references, refElement);
            }
        }

        return references;
    }

    private NodeList findReferenceElements(Element termElement) {
        NodeList refList = termElement.getElementsByTagName("ref");
        if (refList.getLength() == 0) {
            refList = termElement.getElementsByTagName("reference");
        }
        return refList;
    }

    private String buildReferenceString(Element refElement) {
        StringBuilder refBuilder = new StringBuilder();

        appendTohNumber(refBuilder, refElement);
        appendReferenceId(refBuilder, refElement);
        appendTitles(refBuilder, refElement);
        appendTranslators(refBuilder, refElement);
        appendLink(refBuilder, refElement);

        return refBuilder.toString().trim();
    }

    private void appendTohNumber(StringBuilder refBuilder, Element refElement) {
        String tohKey = getElementText(refElement, "toh");
        if (!tohKey.isEmpty()) {
            refBuilder.append("Toh ").append(tohKey);
        }
    }

    private void appendReferenceId(StringBuilder refBuilder, Element refElement) {
        String refId = getElementTextMultiple(refElement, "id", "number", "ref_id");
        if (!refId.isEmpty() && refBuilder.length() == 0) {
            refBuilder.append(refId);
        }
    }

    private void appendTitles(StringBuilder refBuilder, Element refElement) {
        List<String> titles = extractMultilingualTitles(refElement);
        if (titles.isEmpty()) {
            return;
        }

        if (refBuilder.length() > 0) {
            refBuilder.append(": ");
        }
        refBuilder.append(titles.get(0));

        appendAlternativeTitles(refBuilder, titles);
    }

    private void appendAlternativeTitles(StringBuilder refBuilder, List<String> titles) {
        if (titles.size() <= 1) {
            return;
        }

        refBuilder.append(" (");
        for (int j = 1; j < titles.size(); j++) {
            if (j > 1) {
                refBuilder.append("; ");
            }
            refBuilder.append(titles.get(j));
        }
        refBuilder.append(")");
    }

    private void appendTranslators(StringBuilder refBuilder, Element refElement) {
        List<String> refTranslators = extractTranslators(refElement);
        if (refTranslators.isEmpty()) {
            return;
        }

        if (refBuilder.length() > 0) {
            refBuilder.append(" | ");
        }
        refBuilder.append("Translator(s): ").append(String.join(", ", refTranslators));
    }

    private List<String> extractTranslators(Element refElement) {
        List<String> refTranslators = new ArrayList<>();
        NodeList translatorList = refElement.getElementsByTagName("translator");

        for (int j = 0; j < translatorList.getLength(); j++) {
            String trans = translatorList.item(j).getTextContent().trim();
            if (!trans.isEmpty()) {
                refTranslators.add(trans);
            }
        }

        return refTranslators;
    }

    private void appendLink(StringBuilder refBuilder, Element refElement) {
        String link = extractLink(refElement);
        if (link.isEmpty()) {
            return;
        }

        if (refBuilder.length() > 0) {
            refBuilder.append(" - ");
        }
        refBuilder.append("URL: ").append(link);
    }

    private void addFallbackReferenceIfValid(List<String> references, Element refElement) {
        String fallbackText = refElement.getTextContent().trim();
        if (!fallbackText.isEmpty() && fallbackText.length() < 500) {
            references.add(fallbackText);
        }
    }

    /**
     * FIX #2: Extract multilingual titles with proper language labels
     */
    private List<String> extractMultilingualTitles(Element refElement) {
        List<String> titles = new ArrayList<>();
        String englishTitle = null;

        NodeList titleList = refElement.getElementsByTagName("title");
        for (int i = 0; i < titleList.getLength(); i++) {
            Element titleElement = (Element) titleList.item(i);
            String lang = titleElement.getAttribute("xml:lang");
            String titleText = titleElement.getTextContent().trim();

            if (titleText.isEmpty()) continue;

            if ("en".equals(lang) || lang.isEmpty()) {
                englishTitle = titleText;
            } else {
                String langLabel = getLanguageLabel(lang);
                titles.add(langLabel + ": " + titleText);
            }
        }

        // English first (highest priority)
        if (englishTitle != null) {
            titles.add(0, englishTitle);
        }

        // Fallback
        if (titles.isEmpty()) {
            String fallbackTitle = getElementTextMultiple(refElement, "title", "name", "source");
            if (!fallbackTitle.isEmpty()) {
                titles.add(fallbackTitle);
            }
        }

        return titles;
    }

    /**
     * FIX #3: Extract link/URL from ref element with href attribute support
     */
    private String extractLink(Element refElement) {
        // Try link element with href attribute
        NodeList linkList = refElement.getElementsByTagName("link");
        if (linkList.getLength() > 0) {
            Element linkElement = (Element) linkList.item(0);

            // Try href attribute (FIX: this was missing!)
            String href = linkElement.getAttribute("href");
            if (!href.isEmpty()) {
                return href;
            }

            // Try text content
            String linkText = linkElement.getTextContent().trim();
            if (!linkText.isEmpty()) {
                return linkText;
            }
        }

        // Try url element
        String url = getElementText(refElement, "url");
        if (!url.isEmpty()) {
            return url;
        }

        return "";
    }

    /**
     * Get language label from ISO code
     */
    private String getLanguageLabel(String langCode) {
        switch (langCode.toLowerCase()) {
            case "bo": return "Tibetan";
            case "bo-ltn": return "Tibetan";
            case "sa-ltn": return "Sanskrit";
            case "zh": return "Chinese";
            case "pi": return "Pali";
            case "th": return "Thai";
            case "mn": return "Mongolian";
            case "ja": return "Japanese";
            case "ko": return "Korean";
            case "vi": return "Vietnamese";
            default: return langCode.toUpperCase();
        }
    }

    /**
     * FIX #4: Map fields using languages with proper romanization handling
     */
    private TermMapping mapFieldsUsingLanguages(Element termElement, String sourceLang, String targetLang) {
        TermMapping mapping = new TermMapping();

        sourceLang = normalizeLangCode(sourceLang);
        targetLang = normalizeLangCode(targetLang);

        String[] sourceTags = getTagsForLanguage(sourceLang);
        String[] targetTags = getTagsForLanguage(targetLang);

        mapping.sourceTerm = getElementTextMultiple(termElement, sourceTags);
        if (mapping.sourceTerm.isEmpty()) {
            mapping.sourceTerm = getElementTextMultiple(termElement, "term", "source");
        }

        mapping.targetTerm = getElementTextMultiple(termElement, targetTags);
        if (mapping.targetTerm.isEmpty()) {
            mapping.targetTerm = getElementTextMultiple(termElement, "target", "translation");
        }

        // Get romanization for source if non-Latin script
        if (isNonLatinScript(sourceLang)) {
            mapping.romanization = getRomanizationForLanguage(termElement, sourceLang);
            mapping.romanizationLabel = getRomanizationLabel(sourceLang);
        }

        mapping.sourceLanguage = sourceLang;
        mapping.targetLanguage = targetLang;

        return mapping;
    }

    /**
     * FIX #4: Map fields using heuristics with proper labels
     */
    private TermMapping mapFieldsUsingHeuristics(Element termElement) {
        TermMapping mapping = new TermMapping();

        String tibetan = getElementText(termElement, "tibetan");
        String wylie = getElementText(termElement, "wylie");
        String chinese = getElementText(termElement, "chinese");
        String pali = getElementText(termElement, "pali");
        String thai = getElementText(termElement, "thai");
        String mongolian = getElementText(termElement, "mongolian");
        String sanskrit = getElementText(termElement, "sanskrit");
        String english = getElementTextMultiple(termElement, "translation", "english");
        String target = getElementText(termElement, "target");
        String source = getElementText(termElement, "source");

        // 1. TIBETAN as source (highest priority)
        if (!tibetan.isEmpty()) {
            mapping.sourceTerm = tibetan;
            mapping.sourceLanguage = "Tibetan";
            mapping.romanization = getElementTextMultiple(termElement, "wylie", "romanization");
            mapping.romanizationLabel = "Wylie"; // FIX #4

            if (!sanskrit.isEmpty()) {
                mapping.targetTerm = sanskrit;
                mapping.targetLanguage = "Sanskrit";
            } else if (!english.isEmpty()) {
                mapping.targetTerm = english;
                mapping.targetLanguage = "English";
            } else if (!target.isEmpty()) {
                mapping.targetTerm = target;
                mapping.targetLanguage = detectLanguageFromScript(target);
            }
            return mapping;
        }

        // 1.5. WYLIE ONLY (no Tibetan field) - Convert Wylie to Tibetan
        if (!wylie.isEmpty() && tibetan.isEmpty()) {
            mapping.sourceTerm = ""; // Will be filled by convertWylieIfNeeded
            mapping.sourceLanguage = "Tibetan";
            mapping.romanization = wylie;
            mapping.romanizationLabel = "Wylie";

            if (!sanskrit.isEmpty()) {
                mapping.targetTerm = sanskrit;
                mapping.targetLanguage = "Sanskrit";
            } else if (!english.isEmpty()) {
                mapping.targetTerm = english;
                mapping.targetLanguage = "English";
            } else if (!target.isEmpty()) {
                mapping.targetTerm = target;
                mapping.targetLanguage = detectLanguageFromScript(target);
            }
            return mapping;
        }

        // 2. CHINESE as source
        if (!chinese.isEmpty()) {
            mapping.sourceTerm = chinese;
            mapping.sourceLanguage = "Chinese";
            mapping.romanization = getElementText(termElement, "pinyin");
            mapping.romanizationLabel = "Pinyin"; // FIX #4

            if (!english.isEmpty()) {
                mapping.targetTerm = english;
                mapping.targetLanguage = "English";
            } else if (!target.isEmpty()) {
                mapping.targetTerm = target;
                mapping.targetLanguage = detectLanguageFromScript(target);
            }
            return mapping;
        }

        // 3. PALI as source
        if (!pali.isEmpty()) {
            mapping.sourceTerm = pali;
            mapping.sourceLanguage = "Pali";
            mapping.romanization = getElementTextMultiple(termElement, "romanization", "transliteration");
            mapping.romanizationLabel = "Romanization"; // FIX #4

            if (!english.isEmpty()) {
                mapping.targetTerm = english;
                mapping.targetLanguage = "English";
            } else if (!target.isEmpty()) {
                mapping.targetTerm = target;
                mapping.targetLanguage = detectLanguageFromScript(target);
            }
            return mapping;
        }

        // 4. THAI as source
        if (!thai.isEmpty()) {
            mapping.sourceTerm = thai;
            mapping.sourceLanguage = "Thai";
            mapping.romanization = getElementTextMultiple(termElement, "romanization", "transliteration");
            mapping.romanizationLabel = "Romanization"; // FIX #4

            if (!english.isEmpty()) {
                mapping.targetTerm = english;
                mapping.targetLanguage = "English";
            } else if (!target.isEmpty()) {
                mapping.targetTerm = target;
                mapping.targetLanguage = detectLanguageFromScript(target);
            }
            return mapping;
        }

        // 5. MONGOLIAN as source
        if (!mongolian.isEmpty()) {
            mapping.sourceTerm = mongolian;
            mapping.sourceLanguage = "Mongolian";
            mapping.romanization = getElementTextMultiple(termElement, "romanization", "transliteration");
            mapping.romanizationLabel = "Romanization"; // FIX #4

            if (!english.isEmpty()) {
                mapping.targetTerm = english;
                mapping.targetLanguage = "English";
            } else if (!target.isEmpty()) {
                mapping.targetTerm = target;
                mapping.targetLanguage = detectLanguageFromScript(target);
            }
            return mapping;
        }

        // 6. SANSKRIT as source
        if (!sanskrit.isEmpty() && tibetan.isEmpty()) {
            mapping.sourceTerm = sanskrit;
            mapping.sourceLanguage = detectSanskritType(sanskrit);
            mapping.romanization = "";
            mapping.romanizationLabel = "";

            if (!english.isEmpty()) {
                mapping.targetTerm = english;
                mapping.targetLanguage = "English";
            } else if (!target.isEmpty()) {
                mapping.targetTerm = target;
                mapping.targetLanguage = detectLanguageFromScript(target);
            }
            return mapping;
        }

        // 7. Generic source/target
        if (!source.isEmpty() && !target.isEmpty()) {
            mapping.sourceTerm = source;
            mapping.targetTerm = target;
            mapping.sourceLanguage = detectLanguageFromScript(source);
            mapping.targetLanguage = detectLanguageFromScript(target);

            if (isNonLatinScript(mapping.sourceLanguage)) {
                mapping.romanization = getElementTextMultiple(termElement, "wylie", "pinyin", "romanization");
                mapping.romanizationLabel = getRomanizationLabel(mapping.sourceLanguage);
            }
            return mapping;
        }

        // 8. FALLBACK: English only
        if (!english.isEmpty()) {
            if (LanguageDetector.hasSanskritDiacritics(english)) {
                mapping.sourceTerm = english;
                mapping.sourceLanguage = "Sanskrit";
            } else {
                mapping.sourceTerm = english;
                mapping.sourceLanguage = "English";
            }
            mapping.targetTerm = "";
            mapping.targetLanguage = null;
            return mapping;
        }

        // 9. Last resort
        String possibleSource = getElementTextMultiple(termElement, "term", "word");
        if (!possibleSource.isEmpty()) {
            mapping.sourceTerm = possibleSource;
            mapping.sourceLanguage = detectLanguageFromScript(possibleSource);
        } else {
            mapping.sourceTerm = "[No term found]";
            mapping.sourceLanguage = "Tibetan";
        }

        return mapping;
    }

    private String detectSanskritType(String text) {
        if (LanguageDetector.hasSanskritDiacritics(text)) {
            return "Sanskrit";
        }
        String detected = LanguageDetector.detectLanguage(text);
        return detected.isEmpty() ? "Sanskrit" : detected;
    }

    private String detectLanguageFromScript(String text) {
        return LanguageDetector.detectLanguage(text);
    }

    private String getRomanizationForLanguage(Element termElement, String langCode) {
        switch (langCode) {
            case "Tibetan":
            case "bo":
                String wylie = getElementText(termElement, "wylie");
                if (!wylie.isEmpty()) return wylie;
                return getElementTextMultiple(termElement, "romanization", "romanized", "transliteration");

            case "Chinese":
            case "zh":
                String pinyin = getElementText(termElement, "pinyin");
                if (!pinyin.isEmpty()) return pinyin;
                return getElementTextMultiple(termElement, "romanization", "romanized", "transliteration");

            case "Thai":
            case "th":
            case "Pali":
            case "pi":
            case "Mongolian":
            case "mn":
                return getElementTextMultiple(termElement, "romanization", "romanized", "transliteration");

            default:
                return getElementTextMultiple(termElement, "wylie", "pinyin", "romanization", "romanized", "transliteration");
        }
    }

    private String getRomanizationLabel(String langCode) {
        switch (langCode) {
            case "Tibetan":
            case "bo":
                return "Wylie";
            case "Chinese":
            case "zh":
                return "Pinyin";
            case "Thai":
            case "th":
            case "Pali":
            case "pi":
            case "Mongolian":
            case "mn":
                return "Romanization";
            default:
                return "Romanization";
        }
    }

    private String[] getTagsForLanguage(String langCode) {
        switch (langCode) {
            case "Tibetan":
            case "bo":
                return new String[]{"tibetan", "bo"};
            case "Chinese":
            case "zh":
                return new String[]{"chinese", "zh"};
            case "Sanskrit":
            case "sa":
                return new String[]{"sanskrit", "skt", "sa"};
            case "Pali":
            case "pi":
                return new String[]{"pali", "pi"};
            case "English":
            case "en":
                return new String[]{"english", "translation", "en"};
            case "Thai":
            case "th":
                return new String[]{"thai", "th"};
            case "Mongolian":
            case "mn":
                return new String[]{"mongolian", "mn"};
            default:
                return new String[]{langCode.toLowerCase(), "term", "translation"};
        }
    }

    private String normalizeLangCode(String lang) {
        if (lang == null || lang.isEmpty()) return "";
        lang = lang.toLowerCase().trim();

        switch (lang) {
            case "tibetan": return "Tibetan";
            case "bo": return "Tibetan";
            case "chinese": return "Chinese";
            case "zh": return "Chinese";
            case "sanskrit": return "Sanskrit";
            case "sa": return "Sanskrit";
            case "pali": return "Pali";
            case "pi": return "Pali";
            case "english": return "English";
            case "en": return "English";
            case "thai": return "Thai";
            case "th": return "Thai";
            case "mongolian": return "Mongolian";
            case "mn": return "Mongolian";
            default: return lang;
        }
    }

    private boolean isNonLatinScript(String langCode) {
        switch (langCode) {
            case "bo":
            case "Tibetan":
            case "zh":
            case "Chinese":
            case "th":
            case "Thai":
            case "mn":
            case "Mongolian":
                return true;
            default:
                return false;
        }
    }

    private String getElementTextMultiple(Element parent, String... tagNames) {
        for (String tagName : tagNames) {
            String text = getElementText(parent, tagName);
            if (text != null && !text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private String getElementText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node != null) {
                String text = node.getTextContent();
                return (text != null) ? text.trim() : "";
            }
        }
        return "";
    }

    /**
     * Extract ALL translation tags (84000 format can have multiple)
     * FIX #5: Handle multiple <translation> tags from XML
     */
    private List<String> extractAllTranslations(Element termElement) {
        List<String> translations = new ArrayList<>();
        NodeList translationList = termElement.getElementsByTagName("translation");

        for (int i = 0; i < translationList.getLength(); i++) {
            String text = translationList.item(i).getTextContent().trim();
            if (!text.isEmpty()) {
                translations.add(text);
            }
        }

        return translations;
    }

    /**
     * Helper class to hold mapped term fields
     * FIX #4: Added romanizationLabel field
     */
    private static class TermMapping {
        String sourceTerm = "";
        String targetTerm = "";
        String sourceLanguage = null;
        String targetLanguage = null;
        String romanization = "";
        String romanizationLabel = "Romanization";
    }
}