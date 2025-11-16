package com.example.glossariobuda;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Represents a glossary term with all its associated data
 * This is a universal format that can be populated from any source (XML, CSV, JSON, etc.)
 *
 * Supports both:
 * - Legacy format (tibetan, wylie, translation, sanskrit, type, definition, references)
 * - Modern format (sourceTerm, sourceLanguage, targetTerm, targetLanguage, context, contributor, notes)
 */
public class GlossaryTerm {
    // Legacy fields (for backward compatibility)
    private String tibetan;
    private String wylie;
    private String translation;
    private String sanskrit;
    private String type;
    private String definition;
    private List<String> references;

    // Modern fields (primary format)
    private String sourceTerm;
    private String sourceLanguage;
    private String targetTerm;
    private String targetLanguage;
    private String context;
    private String contributor;
    private String notes;

    // Metadata fields (NEW - required by controller)
    private Date date;  // Glossary creation date (from XML root)
    private String dateAdded;  // When term was added to database
    private String verifiedStatus;  // "verified" or "unverified"
    private String owner;  // Owner of this term (who manages it in the system)

    // Optional fields (for extended functionality)
    private String contentHash;  // SHA-256 hash of term content
    private Integer id;  // Database ID

    /**
     * Default constructor
     */
    public GlossaryTerm() {
        this.verifiedStatus = "unverified";
        this.dateAdded = Instant.now().toString();
    }

    /**
     * Constructor with source and target
     */
    public GlossaryTerm(String sourceTerm, String sourceLanguage, String targetTerm, String targetLanguage) {
        this();
        this.sourceTerm = sourceTerm;
        this.sourceLanguage = sourceLanguage;
        this.targetTerm = targetTerm;
        this.targetLanguage = targetLanguage;
    }

    // ===== LEGACY FIELDS (Backward Compatibility) =====
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

    // ===== MODERN FIELDS (Primary Format) =====
    public String getSourceTerm() { return sourceTerm; }
    public void setSourceTerm(String sourceTerm) { this.sourceTerm = sourceTerm; }

    public String getSourceLanguage() { return sourceLanguage; }
    public void setSourceLanguage(String sourceLanguage) { this.sourceLanguage = sourceLanguage; }

    public String getTargetTerm() { return targetTerm; }
    public void setTargetTerm(String targetTerm) { this.targetTerm = targetTerm; }

    public String getTargetLanguage() { return targetLanguage; }
    public void setTargetLanguage(String targetLanguage) { this.targetLanguage = targetLanguage; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getContributor() { return contributor; }
    public void setContributor(String contributor) { this.contributor = contributor; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    // ===== METADATA FIELDS (NEW - Required by Controller) =====

    /**
     * Glossary creation date (extracted from XML root attributes)
     * Set by XMLFormatParser from root element's 'created' attribute
     */
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    /**
     * When this specific term was added to the database
     * Typically set to now() when term is first created
     * Format: ISO 8601 (e.g., "2025-01-26T10:30:45.123Z")
     */
    public String getDateAdded() { return dateAdded; }
    public void setDateAdded(String dateAdded) { this.dateAdded = dateAdded; }

    /**
     * Verification status: "verified", "unverified", or "disputed"
     * Default: "unverified"
     */
    public String getVerifiedStatus() { return verifiedStatus; }
    public void setVerifiedStatus(String verifiedStatus) { this.verifiedStatus = verifiedStatus; }

    /**
     * Owner of this term (who manages it in the system)
     * Examples: "tashi", "shared", "84000_official"
     */
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    // ===== OPTIONAL FIELDS =====

    /**
     * SHA-256 hash of term content for deduplication
     * Generated from: sourceTerm | sourceLanguage | targetTerm | targetLanguage | context | contributor
     */
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    /**
     * Database ID (when term is stored in database)
     */
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    // ===== UTILITY METHODS =====

    /**
     * Get the primary term to display (source or fallback)
     */
    public String getDisplayTerm() {
        if (sourceTerm != null && !sourceTerm.isEmpty()) {
            return sourceTerm;
        }
        if (tibetan != null && !tibetan.isEmpty()) {
            return tibetan;
        }
        return translation != null ? translation : "[No term]";
    }

    /**
     * Get the translation to display (target or fallback)
     */
    public String getDisplayTranslation() {
        if (targetTerm != null && !targetTerm.isEmpty()) {
            return targetTerm;
        }
        if (translation != null && !translation.isEmpty()) {
            return translation;
        }
        return "[No translation]";
    }

    /**
     * Check if this term has all required fields
     */
    public boolean isComplete() {
        return (sourceTerm != null && !sourceTerm.isEmpty()) &&
                (targetTerm != null && !targetTerm.isEmpty()) &&
                (sourceLanguage != null && !sourceLanguage.isEmpty()) &&
                (targetLanguage != null && !targetLanguage.isEmpty());
    }

    /**
     * Get short summary for display
     * Format: "Tibetan → English"
     */
    public String getSummary() {
        String source = getDisplayTerm();
        String target = getDisplayTranslation();
        String sourceLang = sourceLanguage != null ? sourceLanguage : "Unknown";
        String targetLang = targetLanguage != null ? targetLanguage : "Unknown";

        return String.format("%s (%s) → %s (%s)",
                source, sourceLang, target, targetLang);
    }

    /**
     * Get detailed summary for debugging
     */
    public String getDetailedSummary() {
        StringBuilder sb = new StringBuilder();

        sb.append("Source: ").append(getDisplayTerm()).append(" (").append(sourceLanguage).append(")\n");
        sb.append("Target: ").append(getDisplayTranslation()).append(" (").append(targetLanguage).append(")\n");

        if (wylie != null && !wylie.isEmpty()) {
            sb.append("Wylie: ").append(wylie).append("\n");
        }

        if (context != null && !context.isEmpty()) {
            sb.append("Context: ").append(context.substring(0, Math.min(100, context.length())));
            if (context.length() > 100) sb.append("...");
            sb.append("\n");
        }

        if (contributor != null && !contributor.isEmpty()) {
            sb.append("Contributor: ").append(contributor).append("\n");
        }

        sb.append("Status: ").append(verifiedStatus).append("\n");

        if (contentHash != null && !contentHash.isEmpty()) {
            sb.append("Hash: ").append(contentHash.substring(0, 8)).append("...\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return getSummary();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GlossaryTerm term = (GlossaryTerm) o;

        // Compare by content if both have hashes
        if (this.contentHash != null && term.contentHash != null) {
            return this.contentHash.equals(term.contentHash);
        }

        // Fallback: compare by source and target
        return (this.sourceTerm != null ? this.sourceTerm.equals(term.sourceTerm) : term.sourceTerm == null) &&
                (this.targetTerm != null ? this.targetTerm.equals(term.targetTerm) : term.targetTerm == null) &&
                (this.sourceLanguage != null ? this.sourceLanguage.equals(term.sourceLanguage) : term.sourceLanguage == null) &&
                (this.targetLanguage != null ? this.targetLanguage.equals(term.targetLanguage) : term.targetLanguage == null);
    }

    @Override
    public int hashCode() {
        // Use content hash if available
        if (contentHash != null) {
            return contentHash.hashCode();
        }

        // Fallback: compute from fields
        int result = sourceTerm != null ? sourceTerm.hashCode() : 0;
        result = 31 * result + (targetTerm != null ? targetTerm.hashCode() : 0);
        result = 31 * result + (sourceLanguage != null ? sourceLanguage.hashCode() : 0);
        result = 31 * result + (targetLanguage != null ? targetLanguage.hashCode() : 0);
        return result;
    }
}