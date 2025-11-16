package com.example.glossariobuda;

/**
 * Parameter object for adding new terms with synchronization.
 * Groups all parameters needed for adding a term to local database and syncing to cloud.
 */
public class AddTermParams {
    public final String sourceTerm;
    public final String sourceLanguage;
    public final String targetTerm;
    public final String targetLanguage;
    public final String context;
    public final String contributor;
    public final String notes;
    public final String owner;

    public AddTermParams(String sourceTerm, String sourceLanguage,
                         String targetTerm, String targetLanguage,
                         String context, String contributor,
                         String notes, String owner) {
        this.sourceTerm = sourceTerm;
        this.sourceLanguage = sourceLanguage;
        this.targetTerm = targetTerm;
        this.targetLanguage = targetLanguage;
        this.context = context;
        this.contributor = contributor;
        this.notes = notes;
        this.owner = owner;
    }
}
