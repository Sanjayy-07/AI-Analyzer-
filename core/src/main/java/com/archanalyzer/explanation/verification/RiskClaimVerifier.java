package com.archanalyzer.explanation.verification;

import com.archanalyzer.explanation.evidence.ArchitectureEvidence;
import com.archanalyzer.explanation.evidence.EvidenceItem;

import java.util.*;

/**
 * Deterministically verifies architectural defect risk claims against the
 * machine learning ensemble predictions produced by Novelty 3 (ArchitectureRiskService).
 */
public class RiskClaimVerifier {

    private static final double SCORE_TOLERANCE = 0.15;

    public ClaimVerificationResult verify(Claim claim, ArchitectureEvidence evidence) {
        String subject = claim.subjectId();
        if (subject == null || !evidence.containsComponent(subject)) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.UNSUPPORTED,
                    "Component '" + subject + "' does not exist in analyzed project components.",
                    null,
                    null,
                    List.of(),
                    List.of()
            );
        }

        String canonSubject = evidence.resolveCanonicalComponentName(subject);
        Double observedScore = evidence.getRiskScore(canonSubject);
        String observedCategory = evidence.getRiskCategory(canonSubject);

        if (observedCategory == null && observedScore == null) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.UNSUPPORTED,
                    "No Novelty 3 risk prediction data is available for component '" + canonSubject + "'.",
                    null,
                    null,
                    List.of(),
                    List.of()
            );
        }

        List<String> supportingEvs = new ArrayList<>();
        List<String> contradictingEvs = new ArrayList<>();
        for (String evId : claim.citedEvidenceIds()) {
            Optional<EvidenceItem> itemOpt = evidence.findById(evId);
            if (itemOpt.isPresent()) {
                EvidenceItem item = itemOpt.get();
                if (canonSubject.equalsIgnoreCase(item.subjectId()) && item.type() == com.archanalyzer.explanation.evidence.EvidenceType.RISK) {
                    supportingEvs.add(evId);
                } else {
                    contradictingEvs.add(evId);
                }
            }
        }

        Double claimedScore = claim.claimedNumericValue();
        String claimedCat = claim.claimedTextValue();
        if (claimedCat == null || claimedCat.isBlank()) {
            claimedCat = inferRiskCategory(claim.text());
        }

        // Check category alignment
        if (claimedCat != null && observedCategory != null) {
            String cUpper = claimedCat.toUpperCase();
            String oUpper = observedCategory.toUpperCase();
            if (cUpper.equals(oUpper)) {
                return new ClaimVerificationResult(
                        claim,
                        ClaimVerificationStatus.VERIFIED,
                        String.format("Verified risk category: %s is classified as %s risk (score: %s).",
                                canonSubject, oUpper, observedScore != null ? observedScore : "N/A"),
                        observedScore,
                        oUpper,
                        supportingEvs,
                        contradictingEvs
                );
            } else if (isAdjacent(cUpper, oUpper)) {
                return new ClaimVerificationResult(
                        claim,
                        ClaimVerificationStatus.PARTIALLY_VERIFIED,
                        String.format("Partially verified risk category: claimed %s risk, but observed category is %s (score: %s).",
                                cUpper, oUpper, observedScore != null ? observedScore : "N/A"),
                        observedScore,
                        oUpper,
                        supportingEvs,
                        contradictingEvs
                );
            } else {
                return new ClaimVerificationResult(
                        claim,
                        ClaimVerificationStatus.CONTRADICTED,
                        String.format("Contradicted risk category: claimed %s risk for %s, but ground truth is %s risk (score: %s).",
                                cUpper, canonSubject, oUpper, observedScore != null ? observedScore : "N/A"),
                        observedScore,
                        oUpper,
                        supportingEvs,
                        contradictingEvs
                );
            }
        }

        // Check numerical score alignment
        if (claimedScore != null && observedScore != null) {
            double diff = Math.abs(claimedScore - observedScore);
            if (diff <= SCORE_TOLERANCE) {
                return new ClaimVerificationResult(
                        claim,
                        ClaimVerificationStatus.VERIFIED,
                        String.format("Verified risk probability score: claimed %.2f matches observed %.2f.",
                                claimedScore, observedScore),
                        observedScore,
                        observedCategory,
                        supportingEvs,
                        contradictingEvs
                );
            } else {
                return new ClaimVerificationResult(
                        claim,
                        ClaimVerificationStatus.CONTRADICTED,
                        String.format("Contradicted risk score: claimed %.2f for %s, but observed ML score is %.2f.",
                                claimedScore, canonSubject, observedScore),
                        observedScore,
                        observedCategory,
                        supportingEvs,
                        contradictingEvs
                );
            }
        }

        return new ClaimVerificationResult(
                claim,
                ClaimVerificationStatus.VERIFIED,
                "Verified risk assertion presence for " + canonSubject + ".",
                observedScore,
                observedCategory,
                supportingEvs,
                contradictingEvs
        );
    }

    private boolean isAdjacent(String a, String b) {
        if ("MEDIUM".equals(a) && ("HIGH".equals(b) || "LOW".equals(b))) return true;
        if ("MEDIUM".equals(b) && ("HIGH".equals(a) || "LOW".equals(a))) return true;
        return false;
    }

    private String inferRiskCategory(String text) {
        if (text == null) return null;
        String t = text.toUpperCase();
        if (t.contains("HIGH RISK") || t.contains("ELEVATED RISK") || t.contains("CRITICAL RISK")) return "HIGH";
        if (t.contains("LOW RISK") || t.contains("MINIMAL RISK")) return "LOW";
        if (t.contains("MEDIUM RISK") || t.contains("MODERATE RISK")) return "MEDIUM";
        return null;
    }
}
