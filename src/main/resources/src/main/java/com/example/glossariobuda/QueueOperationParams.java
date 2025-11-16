package com.example.glossariobuda;

/**
 * Parameter object for offline queue operations.
 * Reduces method parameter count from 12 to 1.
 */
public class QueueOperationParams {
    public final String operation;
    public final int termId;
    public final String sourceTerm;
    public final String sourceLanguage;
    public final String targetTerm;
    public final String targetLanguage;
    public final String context;
    public final String contributor;
    public final String notes;
    public final String status;
    public final String owner;
    public final String originalHash;

    public QueueOperationParams(String operation, int termId, String sourceTerm,
                                String sourceLanguage, String targetTerm,
                                String targetLanguage, String context,
                                String contributor, String notes, String status,
                                String owner, String originalHash) {
        this.operation = operation;
        this.termId = termId;
        this.sourceTerm = sourceTerm;
        this.sourceLanguage = sourceLanguage;
        this.targetTerm = targetTerm;
        this.targetLanguage = targetLanguage;
        this.context = context;
        this.contributor = contributor;
        this.notes = notes;
        this.status = status;
        this.owner = owner;
        this.originalHash = originalHash;
    }

    /**
     * Builder for convenient construction
     */
    public static class Builder {
        private String operation;
        private int termId;
        private String sourceTerm;
        private String sourceLanguage;
        private String targetTerm;
        private String targetLanguage;
        private String context;
        private String contributor;
        private String notes;
        private String status;
        private String owner;
        private String originalHash;

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder termId(int termId) {
            this.termId = termId;
            return this;
        }

        public Builder sourceTerm(String sourceTerm) {
            this.sourceTerm = sourceTerm;
            return this;
        }

        public Builder sourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
            return this;
        }

        public Builder targetTerm(String targetTerm) {
            this.targetTerm = targetTerm;
            return this;
        }

        public Builder targetLanguage(String targetLanguage) {
            this.targetLanguage = targetLanguage;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder contributor(String contributor) {
            this.contributor = contributor;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder originalHash(String originalHash) {
            this.originalHash = originalHash;
            return this;
        }

        public QueueOperationParams build() {
            return new QueueOperationParams(operation, termId, sourceTerm, sourceLanguage,
                    targetTerm, targetLanguage, context, contributor, notes, status, owner, originalHash);
        }
    }
}
