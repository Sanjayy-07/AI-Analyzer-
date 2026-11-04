package com.archanalyzer.explanation.verification;

import com.archanalyzer.explanation.evidence.ArchitectureEvidence;
import com.archanalyzer.explanation.llm.ExplanationResponse;

import java.util.*;

/**
 * Deterministic Java Claim Verification Engine.
 * Takes the raw LLM explanation and independently evaluates every factual assertion
 * against the authoritative ArchitectureEvidence ground truth.
 */
public class ClaimVerificationEngine {

    private final MetricClaimVerifier metricVerifier = new MetricClaimVerifier();
    private final DependencyClaimVerifier dependencyVerifier = new DependencyClaimVerifier();
    private final CycleClaimVerifier cycleVerifier = new CycleClaimVerifier();
    private final RiskClaimVerifier riskVerifier = new RiskClaimVerifier();

    public VerifiedExplanation verifyExplanation(
            ExplanationResponse response,
            ArchitectureEvidence evidence,
            String modelUsed,
            long executionDurationMs
    ) {
        if (response == null) {
            return new VerifiedExplanation(
                    "No explanation response provided.",
                    List.of(),
                    List.of(),
                    List.of(),
                    0.0,
                    0.0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    modelUsed != null ? modelUsed : "none",
                    executionDurationMs
            );
        }

        List<ClaimVerificationResult> verificationResults = new ArrayList<>();

        int verifiedCount = 0;
        int partiallyVerifiedCount = 0;
        int unsupportedCount = 0;
        int contradictedCount = 0;
        int notVerifiableCount = 0;
        int verifiableCount = 0;

        for (ExplanationResponse.ExtractedClaimDto claimDto : response.extractedClaims()) {
            Claim claim = toClaim(claimDto);
            ClaimVerificationResult result = verifyClaim(claim, evidence);
            verificationResults.add(result);

            switch (result.status()) {
                case VERIFIED -> {
                    verifiedCount++;
                    verifiableCount++;
                }
                case PARTIALLY_VERIFIED -> {
                    partiallyVerifiedCount++;
                    verifiableCount++;
                }
                case UNSUPPORTED -> {
                    unsupportedCount++;
                    verifiableCount++;
                }
                case CONTRADICTED -> {
                    contradictedCount++;
                    verifiableCount++;
                }
                case NOT_VERIFIABLE -> notVerifiableCount++;
            }
        }

        int totalClaims = verificationResults.size();
        double verificationRate = verifiableCount > 0
                ? Math.round(((verifiedCount + 0.5 * partiallyVerifiedCount) / (double) verifiableCount) * 1000.0) / 10.0
                : 100.0;
        double contradictionRate = totalClaims > 0
                ? Math.round((contradictedCount / (double) totalClaims) * 1000.0) / 10.0
                : 0.0;

        return new VerifiedExplanation(
                response.overview(),
                response.findings(),
                response.recommendations(),
                verificationResults,
                verificationRate,
                contradictionRate,
                totalClaims,
                verifiedCount,
                partiallyVerifiedCount,
                unsupportedCount,
                contradictedCount,
                notVerifiableCount,
                modelUsed != null ? modelUsed : "gemini-3.5-flash",
                executionDurationMs
        );
    }

    public ClaimVerificationResult verifyClaim(Claim claim, ArchitectureEvidence evidence) {
        if (evidence == null) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.UNSUPPORTED,
                    "No architecture ground truth evidence available for verification.",
                    null,
                    null,
                    List.of(),
                    List.of()
            );
        }

        return switch (claim.type()) {
            case METRIC_FACT -> metricVerifier.verify(claim, evidence);
            case DEPENDENCY_FACT -> dependencyVerifier.verify(claim, evidence);
            case CYCLE_FACT -> cycleVerifier.verify(claim, evidence);
            case RISK_FACT -> riskVerifier.verify(claim, evidence);
            case INTERPRETATION -> verifyInterpretation(claim, evidence);
            case RECOMMENDATION -> verifyRecommendation(claim, evidence);
        };
    }

    private ClaimVerificationResult verifyInterpretation(Claim claim, ArchitectureEvidence evidence) {
        String subject = claim.subjectId();
        if (subject != null && !subject.isBlank() && !"PROJECT".equalsIgnoreCase(subject)) {
            if (!evidence.containsComponent(subject)) {
                return new ClaimVerificationResult(
                        claim,
                        ClaimVerificationStatus.UNSUPPORTED,
                        "Interpretation references non-existent component: " + subject,
                        null,
                        null,
                        List.of(),
                        List.of()
                );
            }
        }

        boolean hasSupporting = false;
        List<String> validCited = new ArrayList<>();
        for (String evId : claim.citedEvidenceIds()) {
            if (evidence.findById(evId).isPresent()) {
                hasSupporting = true;
                validCited.add(evId);
            }
        }

        if (hasSupporting) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.VERIFIED,
                    "Interpretation is grounded in valid architecture evidence register items.",
                    null,
                    "GROUNDED_INTERPRETATION",
                    validCited,
                    List.of()
            );
        } else {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.PARTIALLY_VERIFIED,
                    "Architectural interpretation is plausible for existing components but cites no specific evidence items.",
                    null,
                    "QUALITATIVE_INTERPRETATION",
                    List.of(),
                    List.of()
            );
        }
    }

    private ClaimVerificationResult verifyRecommendation(Claim claim, ArchitectureEvidence evidence) {
        String target = claim.subjectId() != null ? claim.subjectId() : claim.targetId();
        if (target != null && !target.isBlank() && !"PROJECT".equalsIgnoreCase(target)) {
            if (!evidence.containsComponent(target)) {
                return new ClaimVerificationResult(
                        claim,
                        ClaimVerificationStatus.UNSUPPORTED,
                        "Refactoring recommendation targets unknown component: " + target,
                        null,
                        null,
                        List.of(),
                        List.of()
                );
            }
        }

        List<String> validCited = new ArrayList<>();
        for (String evId : claim.citedEvidenceIds()) {
            if (evidence.findById(evId).isPresent()) {
                validCited.add(evId);
            }
        }

        return new ClaimVerificationResult(
                claim,
                ClaimVerificationStatus.VERIFIED,
                "Refactoring recommendation targets valid component '" + target + "' and cites architectural evidence.",
                null,
                "ACTIONABLE_RECOMMENDATION",
                validCited,
                List.of()
        );
    }

    private Claim toClaim(ExplanationResponse.ExtractedClaimDto dto) {
        ClaimType type = ClaimType.INTERPRETATION;
        if (dto.claimType() != null) {
            try {
                type = ClaimType.valueOf(dto.claimType().toUpperCase().trim());
            } catch (IllegalArgumentException ignored) {}
        }
        return new Claim(
                dto.claimId(),
                type,
                dto.text(),
                dto.subjectId(),
                dto.targetId(),
                dto.metricName(),
                dto.claimedNumericValue(),
                dto.claimedTextValue(),
                dto.evidenceIds()
        );
    }
}
