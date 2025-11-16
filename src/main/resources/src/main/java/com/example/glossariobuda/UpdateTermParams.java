package com.example.glossariobuda;

/**
 * Parameter object for DatabaseManager.updateTerm method.
 * Reduces parameter count from 10 to 2 (termId + params).
 */
public class UpdateTermParams {
    public final String sourceTerm;
    public final String sourceLanguage;
    public final String targetTerm;
    public final String targetLanguage;
    public final String context;
    public final String contributor;
    public final String notes;
    public final String status;
    public final String owner;

    public UpdateTermParams(String sourceTerm, String sourceLanguage,
                           String targetTerm, String targetLanguage,
                           String context, String contributor,
                           String notes, String status, String owner) {
        this.sourceTerm = sourceTerm;
        this.sourceLanguage = sourceLanguage;
        this.targetTerm = targetTerm;
        this.targetLanguage = targetLanguage;
        this.context = context;
        this.contributor = contributor;
        this.notes = notes;
        this.status = status;
        this.owner = owner;
    }

    public static class Builder {
        private String sourceTerm;
        private String sourceLanguage;
        private String targetTerm;
        private String targetLanguage;
        private String context;
        private String contributor;
        private String notes;
        private String status;
        private String owner;

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

        public UpdateTermParams build() {
            return new UpdateTermParams(sourceTerm, sourceLanguage, targetTerm,
                    targetLanguage, context, contributor, notes, status, owner);
        }
    }
}
