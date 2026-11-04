package com.archanalyzer.explanation.llm;

import java.util.List;

/**
 * Raw parsed LLM explanation response containing overview, findings, recommendations,
 * and extracted claims ready for deterministic Java ground-truth verification.
 */
public record ExplanationResponse(
        String rawResponse,
        String overview,
        List<FindingDto> findings,
        List<RecommendationDto> recommendations,
        List<ExtractedClaimDto> extractedClaims
) {
    public record FindingDto(
            String findingId,
            String componentId,
            String issue,
            String severity,
            String explanation,
            List<String> evidenceIds
    ) {}

    public record RecommendationDto(
            String recommendationId,
            String targetComponent,
            String action,
            String rationale,
            List<String> evidenceIds,
            String projectedBenefit
    ) {}

    public record ExtractedClaimDto(
            String claimId,
            String claimType,
            String subjectId,
            String targetId,
            String metricName,
            Double claimedNumericValue,
            String claimedTextValue,
            String text,
            List<String> evidenceIds
    ) {}
}
