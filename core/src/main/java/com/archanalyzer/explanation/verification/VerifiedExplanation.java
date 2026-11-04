package com.archanalyzer.explanation.verification;

import com.archanalyzer.explanation.llm.ExplanationResponse;

import java.util.List;

/**
 * Complete verified architecture explanation containing findings, recommendations,
 * and deterministic Java ground-truth verification results for every claim.
 */
public record VerifiedExplanation(
        String overview,
        List<ExplanationResponse.FindingDto> findings,
        List<ExplanationResponse.RecommendationDto> recommendations,
        List<ClaimVerificationResult> verifiedClaims,
        double verificationRate,
        double contradictionRate,
        int totalClaims,
        int verifiedCount,
        int partiallyVerifiedCount,
        int unsupportedCount,
        int contradictedCount,
        int notVerifiableCount,
        String modelUsed,
        long executionDurationMs
) {
    public boolean isPublicationReady() {
        return contradictionRate == 0.0 && verificationRate >= 80.0;
    }
}
