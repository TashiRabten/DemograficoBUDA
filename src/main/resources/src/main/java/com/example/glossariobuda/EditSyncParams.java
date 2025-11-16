package com.example.glossariobuda;

/**
 * Parameter object for term edit synchronization operations.
 * Groups all parameters needed for syncing term edits to cloud.
 */
public class EditSyncParams {
    public final int localId;
    public final String sourceTerm;
    public final String sourceLanguage;
    public final String targetTerm;
    public final String targetLanguage;
    public final String context;
    public final String contributor;
    public final String notes;
    public final String oldHash;
    public final String verifiedStatus;
    public final String oldSourceTerm;
    public final String oldTargetTerm;

    public EditSyncParams(int localId, String sourceTerm, String sourceLanguage,
                          String targetTerm, String targetLanguage, String context,
                          String contributor, String notes, String oldHash,
                          String verifiedStatus, String oldSourceTerm, String oldTargetTerm) {
        this.localId = localId;
        this.sourceTerm = sourceTerm;
        this.sourceLanguage = sourceLanguage;
        this.targetTerm = targetTerm;
        this.targetLanguage = targetLanguage;
        this.context = context;
        this.contributor = contributor;
        this.notes = notes;
        this.oldHash = oldHash;
        this.verifiedStatus = verifiedStatus;
        this.oldSourceTerm = oldSourceTerm;
        this.oldTargetTerm = oldTargetTerm;
    }
}
