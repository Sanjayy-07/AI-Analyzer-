package com.archanalyzer.explanation.verification;

import java.util.List;

/**
 * An individual assertion extracted from an LLM-generated explanation.
 */
public record Claim(
        String claimId,
        ClaimType type,
        String text,
        String subjectId,
        String targetId,
        String metricName,
        Double claimedNumericValue,
        String claimedTextValue,
        List<String> citedEvidenceIds
) {
    public Claim {
        if (citedEvidenceIds == null) citedEvidenceIds = List.of();
    }
}
