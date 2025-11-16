package com.example.glossariobuda;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.*;
import com.ibm.icu.text.Transliterator;

/**
 * Parser for Monier-Williams Sanskrit Dictionary (MW72 XML format)
 * Converts SLP1 → IAST → Devanāgarī using Slp1Converter + ICU4J.
 */
public class MW72Parser implements FormatParser {

    private static final Transliterator IAST_TO_DEVANAGARI;

    static {
        Transliterator tmp = null;
        try {
            // Load rules from our custom rules class
            String rules = IastToDevanagariRules.getRules();
            System.out.println("[MW72Parser] 📄 Loaded Sanskrit transliteration rules (" + rules.length() + " chars)");

            // Create custom transliterator
            tmp = Transliterator.createFromRules(
                    "IAST-Devanagari-Sanskrit",
                    rules,
                    Transliterator.FORWARD
            );

            // Register it so it can be reused
            Transliterator.registerInstance(tmp);

            System.out.println("[MW72Parser] ✅ Custom Sanskrit transliterator created successfully");

        } catch (Exception e) {
            System.err.println("[MW72Parser] ❌ Failed to create transliterator: " + e.getMessage());
            e.printStackTrace();
            System.err.println("[MW72Parser] Attempting fallback to ICU Latin-Devanagari...");

            try {
                tmp = Transliterator.getInstance("Latin-Devanagari");
                System.out.println("[MW72Parser] ⚠️ Using ICU fallback (may not be accurate for Sanskrit)");
            } catch (Exception e2) {
                System.err.println("[MW72Parser] ❌ Fallback also failed: " + e2.getMessage());
            }
        }

        IAST_TO_DEVANAGARI = tmp;

        if (IAST_TO_DEVANAGARI != null) {
            System.out.println("✅ Transliterator active: " + IAST_TO_DEVANAGARI.getID());
        } else {
            System.err.println("⚠️ No transliterator available!");
        }
    }

    public static Transliterator getCustomTransliterator() {
        return IAST_TO_DEVANAGARI;
    }


    @Override
    public List<GlossaryTerm> parse(File file, LoadProgressCallback callback) throws Exception {
        List<GlossaryTerm> terms = new ArrayList<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setXIncludeAware(false);
        dbFactory.setExpandEntityReferences(false);

        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);
        doc.getDocumentElement().normalize();

        NodeList entryList = doc.getElementsByTagName("H1");
        int totalEntries = entryList.getLength();

        if (callback != null)
            callback.onProgress(0, totalEntries, "Found " + totalEntries + " entries in MW72");

        Date importDate = new Date();

        for (int i = 0; i < entryList.getLength(); i++) {
            Node entryNode = entryList.item(i);
            if (entryNode.getNodeType() == Node.ELEMENT_NODE) {
                Element entryElement = (Element) entryNode;
                try {
                    GlossaryTerm term = parseEntry(entryElement, importDate);
                    if (term != null) terms.add(term);
                } catch (Exception e) {
                    System.err.println("Error processing entry " + (i + 1) + ": " + e.getMessage());
                }
            }
            if (callback != null && i % 100 == 0)
                callback.onProgress(i, totalEntries, "Processing entry " + (i + 1));
        }

        if (callback != null)
            callback.onProgress(totalEntries, totalEntries, "Import complete!");

        return terms;
    }

    private GlossaryTerm parseEntry(Element entryElement, Date importDate) {
        GlossaryTerm term = new GlossaryTerm();
        term.setDate(importDate);
        term.setDateAdded(java.time.Instant.now().toString());

        // Source: IAST romanization, Target: Devanagari script
        term.setSourceLanguage("Sanskrit");
        term.setTargetLanguage("Devanagari");

        Element headerElement = getFirstElementByTag(entryElement, "h");
        String slp1Term = getElementText(headerElement, "key1");

        // Convert SLP1 to both IAST and Devanagari
        String iastTerm = Slp1Converter.convert(slp1Term);
        String devanagariTerm = Slp1ToDevanagariConverter.convert(slp1Term);

        // Source = IAST romanization, Target = Devanagari
        term.setSourceTerm(iastTerm);
        term.setTargetTerm(devanagariTerm);

        // Build notes with original SLP1 and metadata
        StringBuilder notes = new StringBuilder();
        notes.append("SLP1: ").append(slp1Term);

        String homonym = getElementText(headerElement, "hom");
        if (!homonym.isEmpty()) notes.append("\nHomonym: ").append(homonym);

        // Extract English definition for context field
        Element bodyElement = getFirstElementByTag(entryElement, "body");
        if (bodyElement != null) {
            String englishMeaning = extractEnglishMeaning(bodyElement);
            String fullDefinition = extractDefinition(bodyElement);

            // Context contains the English definition
            term.setContext(englishMeaning);

            // Add full definition to notes if different from short meaning
            if (!fullDefinition.isEmpty() && !fullDefinition.equals(englishMeaning)) {
                notes.append("\n\nFull Definition: ").append(fullDefinition);
            }
        } else {
            term.setContext("[No definition available]");
        }

        // Add entry metadata to notes
        Element tailElement = getFirstElementByTag(entryElement, "tail");
        if (tailElement != null) {
            String entryId = getElementText(tailElement, "L");
            String page = getElementText(tailElement, "pc");
            if (!entryId.isEmpty()) notes.append("\nEntry ID: ").append(entryId);
            if (!page.isEmpty()) notes.append("\nPage: ").append(page);
        }

        term.setNotes(notes.toString().trim());
        term.setContributor("This application uses data from Cologne Digital Sanskrit Dictionaries, Cologne University, accessed on July 07, 2025.");
        return term;
    }

    /**
     * Convert IAST (Latin) → Devanāgarī using ICU's built-in transliterator
     */
    private String iastToDevanagari(String iast) {
        if (iast == null || iast.isEmpty()) return "";

        try {
            // Normalize to NFC form (precomposed characters)
            String norm = java.text.Normalizer.normalize(iast, java.text.Normalizer.Form.NFC);

            if (IAST_TO_DEVANAGARI != null) {
                return IAST_TO_DEVANAGARI.transliterate(norm);
            } else {
                System.err.println("[iastToDevanagari] ⚠️ No transliterator available");
                return iast;  // Return original if transliterator not available
            }
        } catch (Exception e) {
            System.err.println("[iastToDevanagari] Error transliterating '" + iast + "': " + e.getMessage());
            e.printStackTrace();
            return iast;
        }
    }


    // ---------------------------------------------------------------
    // --- Definition and English extraction (unchanged core logic) ---
    // ---------------------------------------------------------------

    private String extractEnglishMeaning(Element bodyElement) {
        if (bodyElement == null) return "[No translation]";
        StringBuilder definitionSb = new StringBuilder();
        boolean foundStartOfDefinition = false;
        boolean inParentheses = false;
        int parenDepth = 0;

        NodeList children = bodyElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);

            if (foundStartOfDefinition) {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) node;
                    String tagName = el.getTagName();

                    if ("i".equals(tagName)) {
                        String itext = el.getTextContent();
                        if (itext != null && itext.trim().startsWith("—")) break;
                    }

                    String content = getCleanTextContent(el);
                    if (content.contains("(fr.") || content.contains("(patronymic)")) continue;
                    if (!content.isEmpty() && !isGrammaticalAbbreviation(content)) {
                        if (definitionSb.length() > 0 && !definitionSb.toString().endsWith(" "))
                            definitionSb.append(" ");
                        definitionSb.append(content);
                    }

                } else if (node.getNodeType() == Node.TEXT_NODE) {
                    String text = node.getTextContent();
                    if (text == null) continue;

                    for (char c : text.toCharArray()) {
                        if (c == '(') parenDepth++;
                        if (c == ')') parenDepth--;
                    }

                    if (text.contains("(fr.") || text.contains("(patronymic)")) {
                        inParentheses = true;
                        int idx = text.indexOf('(');
                        if (idx > 0) text = text.substring(0, idx).trim();
                        else continue;
                    }

                    if (parenDepth > 0 && inParentheses) continue;
                    if (parenDepth == 0) inParentheses = false;

                    text = text.trim();
                    if (!text.isEmpty()) {
                        if (text.matches(".*\\(\\s*as\\s*\\).*") ||
                                text.matches(".*\\(\\s*am\\s*\\).*") ||
                                text.matches(".*\\(\\s*ā\\s*\\).*")) {
                            int genderIdx = text.indexOf('(');
                            if (genderIdx > 5) {
                                text = text.substring(0, genderIdx).trim();
                                definitionSb.append(" ").append(text);
                            }
                            break;
                        }
                        if (definitionSb.length() > 0 && !definitionSb.toString().endsWith(" "))
                            definitionSb.append(" ");
                        definitionSb.append(text);
                    }
                }

            } else {
                // Not yet started collecting
                if (node.getNodeType() == Node.TEXT_NODE) {
                    String text = node.getTextContent().trim();
                    text = text.replaceAll("^(\\d+\\s*\\.|,|;|\\.)\\s*", "").trim();
                    if (text.startsWith("[") && text.contains("cf.")) continue;
                    if (text.isEmpty() || text.equals("=") || isGrammaticalAbbreviation(text)) continue;
                    foundStartOfDefinition = true;
                    definitionSb.append(text);

                } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) node;
                    String tagName = el.getTagName();
                    if ("s".equals(tagName) || "i".equals(tagName) ||
                            "lang".equals(tagName) || "nsi".equals(tagName)) continue;
                    String content = getCleanTextContent(el).trim();
                    if (!content.isEmpty() && !isGrammaticalAbbreviation(content)) {
                        foundStartOfDefinition = true;
                        definitionSb.append(content);
                    }
                }
            }
        }

        String finalTerm = definitionSb.toString().replaceAll("\\s+", " ").trim();
        finalTerm = finalTerm.replaceAll("^(ind\\.?|adj\\.?|adv\\.?|m\\.?|n\\.?|f\\.?)\\s+", "");
        finalTerm = finalTerm.replaceAll("\\[cf\\..*?\\]", "").trim();

        int endIdx = -1;
        int semi = finalTerm.indexOf(';');
        int period = finalTerm.indexOf('.');
        int paren = finalTerm.indexOf('(');
        if (semi > 15) endIdx = semi;
        else if (period > 20) endIdx = period;
        else if (paren > 15) endIdx = paren;
        if (endIdx > 0) finalTerm = finalTerm.substring(0, endIdx).trim();
        if (finalTerm.length() > 150) finalTerm = finalTerm.substring(0, 150).trim();

        return finalTerm.isEmpty() ? "[No translation]" : finalTerm;
    }

    private boolean isGrammaticalAbbreviation(String part) {
        if (part == null || part.isEmpty()) return true;
        String lower = part.toLowerCase().replaceAll("\\.", "").trim();
        if (lower.matches("^(m|f|n|nom|acc|instr|dat|abl|gen|loc|voc|sing|du|pl|pres|perf|aor|fut|cond|opt|act|pass|mid|adj|adv|ind|prep|conj|pron|num|cl|ved|rt|caus|desid|intens|cf|viz|etc|esp|prob)$"))
            return true;
        return part.length() <= 1;
    }

    private String extractDefinition(Element bodyElement) {
        String sanskritInBody = getElementText(bodyElement, "s");
        String bodyText = getCleanTextContent(bodyElement);
        if (!sanskritInBody.isEmpty() && bodyText.startsWith(sanskritInBody))
            bodyText = bodyText.substring(sanskritInBody.length()).trim();
        return bodyText.replaceAll("\\s+", " ").trim();
    }

    private String getCleanTextContent(Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                String text = node.getTextContent().trim();
                if (!text.isEmpty()) {
                    if (sb.length() > 0 && !sb.toString().endsWith(" ")) sb.append(" ");
                    sb.append(text);
                }
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if ("div".equals(child.getTagName()) && "lb".equals(child.getAttribute("n"))) {
                    sb.append(" ");
                } else {
                    String inner = getCleanTextContent(child);
                    if (!inner.isEmpty()) {
                        if (sb.length() > 0 && !sb.toString().endsWith(" ")) sb.append(" ");
                        sb.append(inner);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String getElementText(Element parent, String tagName) {
        if (parent == null) return "";
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

    private Element getFirstElementByTag(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) return (Element) nodeList.item(0);
        return null;
    }

    @Override
    public boolean canParse(File file) {
        String name = file.getName().toLowerCase();
        return name.contains("mw72") || (name.endsWith(".xml") && name.contains("monier"));
    }

    @Override
    public String getFileExtension() {
        return "xml";
    }

    @Override
    public String getFormatDescription() {
        return "Monier-Williams Sanskrit Dictionary (MW72) — IAST Romanization → Devanāgarī Script";
    }

    @Override
    public ImportMetadata extractMetadata(File file) {
        return new ImportMetadata(
                "Monier-Williams (Cologne Digital Sanskrit Lexicon)",
                file.getName()
        );
    }
}