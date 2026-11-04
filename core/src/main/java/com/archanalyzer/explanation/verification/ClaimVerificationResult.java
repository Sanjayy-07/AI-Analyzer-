package com.archanalyzer.explanation.verification;

import java.util.List;

/**
 * Result of evaluating an individual claim against authoritative ArchitectureEvidence.
 */
public record ClaimVerificationResult(
        Claim claim,
        ClaimVerificationStatus status,
        String explanation,
        Double observedNumericValue,
        String observedTextValue,
        List<String> supportingEvidenceIds,
        List<String> contradictingEvidenceIds
) {
    public ClaimVerificationResult {
        if (supportingEvidenceIds == null) supportingEvidenceIds = List.of();
        if (contradictingEvidenceIds == null) contradictingEvidenceIds = List.of();
    }

    public boolean isVerified() {
        return status == ClaimVerificationStatus.VERIFIED;
    }

    public boolean isContradicted() {
        return status == ClaimVerificationStatus.CONTRADICTED;
    }
}
