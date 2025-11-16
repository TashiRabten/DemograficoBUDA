package com.example.glossariobuda;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class XMLGlossaryLoader {

    private DatabaseManager dbManager;
    private final String owner;

    public XMLGlossaryLoader(DatabaseManager dbManager, String owner) {
        this.dbManager = dbManager;
        this.owner = owner;
    }


    /**
     * Interface for progress callbacks during XML loading
     */
    public interface LoadProgressCallback {
        void onProgress(int current, int total, String message);
        void onComplete(int totalLoaded, String message);
        void onError(String error);
    }

    /**
     * Load glossary terms from XML file
     * @param xmlFilePath Path to the XML file
     * @param callback Progress callback (can be null)
     * @return Number of terms successfully loaded
     */
    public int loadFromXML(String xmlFilePath, LoadProgressCallback callback) {
        int loadedCount = 0;
        int skippedCount = 0;

        try {
            // Parse XML file
            File xmlFile = new File(xmlFilePath);
            if (!xmlFile.exists()) {
                if (callback != null) {
                    callback.onError("Arquivo XML não encontrado: " + xmlFilePath);
                }
                return 0;
            }

            if (callback != null) {
                callback.onProgress(0, 0, "Carregando arquivo XML...");
            }

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            // XXE Prevention: Disable DOCTYPE declarations and external entities
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbFactory.setXIncludeAware(false);
            dbFactory.setExpandEntityReferences(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

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
                        GlossaryTerm term = parseTermElement(termElement);

                        if (term != null && isValidTerm(term)) {
                            // Add term to database
                            addTermToDatabase(term);
                            loadedCount++;
                        } else {
                            skippedCount++;
                        }

                        // Report progress every 100 terms
                        if (callback != null && (i + 1) % 100 == 0) {
                            callback.onProgress(i + 1, totalTerms,
                                    String.format("Processados %d de %d termos (carregados: %d, ignorados: %d)",
                                            i + 1, totalTerms, loadedCount, skippedCount));
                        }

                    } catch (Exception e) {
                        skippedCount++;
                        System.err.println("Erro ao processar termo " + (i + 1) + ": " + e.getMessage());
                    }
                }
            }

            if (callback != null) {
                callback.onComplete(loadedCount,
                        String.format("Importação completa! %d termos carregados, %d ignorados",
                                loadedCount, skippedCount));
            }

        } catch (Exception e) {
            if (callback != null) {
                callback.onError("Erro ao carregar dados: " + e.getMessage());
            }
            e.printStackTrace();
        }

        return loadedCount;
    }

    /**
     * Parse a term element from the XML
     */
    private GlossaryTerm parseTermElement(Element termElement) {
        GlossaryTerm term = new GlossaryTerm();

        // Extract Tibetan term
        String tibetan = getElementText(termElement, "tibetan");
        term.setTibetan(tibetan);

        // Extract Wylie transliteration
        String wylie = getElementText(termElement, "wylie");
        term.setWylie(wylie);

        // Extract English translation
        String translation = getElementText(termElement, "translation");
        term.setTranslation(translation);

        // Extract Sanskrit
        String sanskrit = getElementText(termElement, "sanskrit");
        term.setSanskrit(sanskrit);

        // Extract type
        String type = getElementText(termElement, "type");
        term.setType(type);

        // Extract definition (from main level or first ref)
        String definition = getElementText(termElement, "definition");
        if (definition == null || definition.isEmpty()) {
            // Try to get definition from first ref element
            NodeList refList = termElement.getElementsByTagName("ref");
            if (refList.getLength() > 0) {
                Element refElement = (Element) refList.item(0);
                definition = getElementText(refElement, "definition");
            }
        }
        term.setDefinition(definition);

        // Extract references
        List<String> references = extractReferences(termElement);
        term.setReferences(references);

        return term;
    }

    /**
     * Extract text content from a child element
     */
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
     * Extract references from ref elements
     */
    private List<String> extractReferences(Element termElement) {
        List<String> references = new ArrayList<>();
        NodeList refList = termElement.getElementsByTagName("ref");

        for (int i = 0; i < refList.getLength(); i++) {
            Element refElement = (Element) refList.item(i);

            // Get Toh number
            String tohKey = "";
            NodeList tohList = refElement.getElementsByTagName("toh");
            if (tohList.getLength() > 0) {
                Element tohElement = (Element) tohList.item(0);
                tohKey = tohElement.getAttribute("key");
                if (tohKey.isEmpty()) {
                    tohKey = tohElement.getTextContent().trim();
                }
            }

            // Get title
            String title = "";
            NodeList titleList = refElement.getElementsByTagName("title");
            for (int j = 0; j < titleList.getLength(); j++) {
                Element titleElement = (Element) titleList.item(j);
                String lang = titleElement.getAttribute("xml:lang");
                if ("en".equals(lang)) {
                    title = titleElement.getTextContent().trim();
                    break;
                }
            }

            if (!tohKey.isEmpty() || !title.isEmpty()) {
                String reference = "";
                if (!tohKey.isEmpty()) {
                    reference = "Toh " + tohKey;
                }
                if (!title.isEmpty()) {
                    reference += (reference.isEmpty() ? "" : ": ") + title;
                }
                references.add(reference);
            }
        }

        return references;
    }

    /**
     * Check if term has minimum required data
     */
    private boolean isValidTerm(GlossaryTerm term) {
        // Must have at least Tibetan text
        return term.getTibetan() != null && !term.getTibetan().isEmpty();
    }

    /**
     * Add term to database
     * FIXED: Correctly handles Sanskrit-Tibetan parsing
     * Context field: definition + references
     * Notes field: English translation, Wylie, Type
     */
    private void addTermToDatabase(GlossaryTerm term) {
        // Build context from definition and references
        StringBuilder context = new StringBuilder();

        if (term.getDefinition() != null && !term.getDefinition().isEmpty()) {
            context.append(term.getDefinition());
        }

        if (term.getReferences() != null && !term.getReferences().isEmpty()) {
            if (context.length() > 0) {
                context.append("\n\n");
            }
            context.append("Referências:\n");
            for (String ref : term.getReferences()) {
                context.append("• ").append(ref).append("\n");
            }
        }

        // Build notes with English translation, Wylie, and Type
        // IMPORTANT: Include English translation in notes since we're not using it as source
        StringBuilder notes = new StringBuilder();

        // Add English translation to notes (since it's not the source language)
        if (term.getTranslation() != null && !term.getTranslation().isEmpty()) {
            notes.append("English: ").append(term.getTranslation()).append("\n");
        }

        // Add Wylie transliteration
        if (term.getWylie() != null && !term.getWylie().isEmpty()) {
            notes.append("Wylie: ").append(term.getWylie()).append("\n");
        }

        // Add type information
        if (term.getType() != null && !term.getType().isEmpty()) {
            notes.append("Type: ").append(term.getType()).append("\n");
        }

        // FIXED: Determine source and target languages correctly
        String sourceTerm;
        String sourceLanguage;
        String targetTerm;
        String targetLanguage;

        // Always use Tibetan as the target
        targetTerm = term.getTibetan();
        targetLanguage = "Tibetan";

        // Determine source: Sanskrit if available, otherwise Tibetan itself
        if (term.getSanskrit() != null && !term.getSanskrit().isEmpty()) {
            // Sanskrit → Tibetan
            sourceTerm = term.getSanskrit();
            sourceLanguage = "Sanskrit";
            // Sanskrit is already the source, no need to add to notes
        } else {
            // Tibetan → Tibetan (monolingual entry)
            // This handles cases where we only have Tibetan terms
            sourceTerm = term.getTibetan();
            sourceLanguage = "Tibetan";

            // If we have Sanskrit in this case, add it to notes
            if (term.getSanskrit() != null && !term.getSanskrit().isEmpty()) {
                notes.append("Sanskrit: ").append(term.getSanskrit()).append("\n");
            }
        }

        // Add to database
        AddTermParams params = new AddTermParams(
                sourceTerm,                    // source term (Sanskrit or Tibetan)
                sourceLanguage,                // source language
                targetTerm,                    // target term (always Tibetan)
                targetLanguage,                // target language (always Tibetan)
                context.toString(),           // context (definition + references)
                "84000 Glossary",            // contributor
                notes.toString(),              // notes (English translation, Wylie, type)
                owner);
        dbManager.addTerm(params);
    }

    /**
     * Inner class to hold parsed term data
     */
    private static class GlossaryTerm {
        private String tibetan;
        private String wylie;
        private String translation;
        private String sanskrit;
        private String type;
        private String definition;
        private List<String> references;

        // Getters and setters
        public String getTibetan() { return tibetan; }
        public void setTibetan(String tibetan) { this.tibetan = tibetan; }

        public String getWylie() { return wylie; }
        public void setWylie(String wylie) { this.wylie = wylie; }

        public String getTranslation() { return translation; }
        public void setTranslation(String translation) { this.translation = translation; }

        public String getSanskrit() { return sanskrit; }
        public void setSanskrit(String sanskrit) { this.sanskrit = sanskrit; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDefinition() { return definition; }
        public void setDefinition(String definition) { this.definition = definition; }

        public List<String> getReferences() { return references; }
        public void setReferences(List<String> references) { this.references = references; }
    }
}