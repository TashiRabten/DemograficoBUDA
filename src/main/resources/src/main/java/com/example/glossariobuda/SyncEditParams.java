package com.example.glossariobuda;

/**
 * Parameter object for sync edit operations.
 * Reduces parameter count from 10 to 2 (localId + params).
 */
public class SyncEditParams {
    public final String sourceTerm;
    public final String sourceLanguage;
    public final String targetTerm;
    public final String targetLanguage;
    public final String context;
    public final String contributor;
    public final String notes;
    public final String originalHash;
    public final String status;

    public SyncEditParams(String sourceTerm, String sourceLanguage,
                         String targetTerm, String targetLanguage,
                         String context, String contributor,
                         String notes, String originalHash, String status) {
        this.sourceTerm = sourceTerm;
        this.sourceLanguage = sourceLanguage;
        this.targetTerm = targetTerm;
        this.targetLanguage = targetLanguage;
        this.context = context;
        this.contributor = contributor;
        this.notes = notes;
        this.originalHash = originalHash;
        this.status = status;
    }
}
