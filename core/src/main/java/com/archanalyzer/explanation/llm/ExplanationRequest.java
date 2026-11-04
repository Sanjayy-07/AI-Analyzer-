package com.archanalyzer.explanation.llm;

import com.archanalyzer.explanation.evidence.ArchitectureEvidence;

/**
 * Request payload for generating an architecture explanation.
 */
public record ExplanationRequest(
        String projectName,
        String targetComponentId,
        String userQuestion,
        ArchitectureEvidence evidence,
        int maxFindings
) {
    public ExplanationRequest(String projectName, ArchitectureEvidence evidence) {
        this(projectName, null, null, evidence, 5);
    }

    public ExplanationRequest(String projectName, String targetComponentId, ArchitectureEvidence evidence) {
        this(projectName, targetComponentId, null, evidence, 5);
    }

    public ExplanationRequest(String projectName, String userQuestion, ArchitectureEvidence evidence, boolean isQuestion) {
        this(projectName, null, userQuestion, evidence, 5);
    }
}
