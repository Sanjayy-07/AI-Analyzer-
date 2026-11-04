package com.archanalyzer.explanation.verification;

import com.archanalyzer.explanation.evidence.ArchitectureEvidence;
import com.archanalyzer.explanation.evidence.EvidenceItem;

import java.util.*;

/**
 * Deterministically verifies numerical and quantitative metric claims against
 * the authoritative ground truth extracted from the AST and MetricsEngine.
 */
public class MetricClaimVerifier {

    private static final double EXACT_TOLERANCE = 0.05;
    private static final double RELATIVE_TOLERANCE = 0.05; // 5%
    private static final double PARTIAL_RELATIVE_TOLERANCE = 0.20; // 20%

    public ClaimVerificationResult verify(Claim claim, ArchitectureEvidence evidence) {
        String subject = claim.subjectId();
        String metricName = claim.metricName();
        Double claimed = claim.claimedNumericValue();

        if (metricName == null || metricName.isBlank()) {
            metricName = inferMetricName(claim.text());
        }

        if (subject == null || subject.isBlank() || "PROJECT".equalsIgnoreCase(subject)) {
            return verifyProjectMetric(claim, metricName, claimed, evidence);
        }

        if (!evidence.containsComponent(subject)) {
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

        Double observed = evidence.getMetricValue(subject, metricName);
        if (observed == null) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.UNSUPPORTED,
                    "Metric '" + metricName + "' is not recorded or unavailable for component '" + subject + "'.",
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
                if (item.numericValue() != null && Math.abs(item.numericValue() - observed) <= EXACT_TOLERANCE) {
                    supportingEvs.add(evId);
                } else {
                    contradictingEvs.add(evId);
                }
            }
        }

        if (claimed == null) {
            // Claim mentioned the metric without an explicit number
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.VERIFIED,
                    "Verified metric presence: " + metricName + " for " + subject + " is " + observed + ".",
                    observed,
                    String.valueOf(observed),
                    supportingEvs,
                    contradictingEvs
            );
        }

        double diff = Math.abs(claimed - observed);
        double denom = Math.max(1.0, Math.abs(observed));
        double relDiff = diff / denom;

        if (diff <= EXACT_TOLERANCE || relDiff <= RELATIVE_TOLERANCE) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.VERIFIED,
                    String.format("Verified metric fact: %s for %s is %.2f (claimed %.2f matches observed %.2f).",
                            metricName, subject, observed, claimed, observed),
                    observed,
                    String.valueOf(observed),
                    supportingEvs,
                    contradictingEvs
            );
        } else if (relDiff <= PARTIAL_RELATIVE_TOLERANCE || diff <= 1.0) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.PARTIALLY_VERIFIED,
                    String.format("Partially verified metric: %s for %s is observed as %.2f (claimed %.2f, discrepancy: %.1f%%).",
                            metricName, subject, observed, claimed, relDiff * 100),
                    observed,
                    String.valueOf(observed),
                    supportingEvs,
                    contradictingEvs
            );
        } else {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.CONTRADICTED,
                    String.format("Contradicted metric fact: claimed %s = %.2f for %s, but ground truth is %.2f.",
                            metricName, claimed, subject, observed),
                    observed,
                    String.valueOf(observed),
                    supportingEvs,
                    contradictingEvs
            );
        }
    }

    private ClaimVerificationResult verifyProjectMetric(Claim claim, String metricName, Double claimed, ArchitectureEvidence evidence) {
        Double observed = null;
        if (metricName != null) {
            for (EvidenceItem item : evidence.findBySubject("PROJECT")) {
                if (metricName.equalsIgnoreCase(item.metricName())) {
                    observed = item.numericValue();
                    break;
                }
            }
        }

        if (observed == null) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.UNSUPPORTED,
                    "Project-level metric '" + metricName + "' not found in ground truth register.",
                    null,
                    null,
                    List.of(),
                    List.of()
            );
        }

        if (claimed == null) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.VERIFIED,
                    "Verified project metric presence: " + metricName + " = " + observed,
                    observed,
                    String.valueOf(observed),
                    List.of(),
                    List.of()
            );
        }

        double diff = Math.abs(claimed - observed);
        if (diff <= EXACT_TOLERANCE || (diff / Math.max(1.0, observed)) <= RELATIVE_TOLERANCE) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.VERIFIED,
                    String.format("Verified project metric: claimed %.2f matches observed %.2f.", claimed, observed),
                    observed,
                    String.valueOf(observed),
                    List.of(),
                    List.of()
            );
        } else {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.CONTRADICTED,
                    String.format("Contradicted project metric: claimed %.2f for %s, but ground truth is %.2f.",
                            claimed, metricName, observed),
                    observed,
                    String.valueOf(observed),
                    List.of(),
                    List.of()
            );
        }
    }

    private String inferMetricName(String text) {
        if (text == null) return "unknown";
        String t = text.toLowerCase();
        if (t.contains("fan-out") || t.contains("fanout") || t.contains("outgoing")) return "fanOut";
        if (t.contains("fan-in") || t.contains("fanin") || t.contains("incoming")) return "fanIn";
        if (t.contains("loc") || t.contains("lines of code") || t.contains("size")) return "loc";
        if (t.contains("cyclomatic") || t.contains("complexity")) return "cyclomaticComplexity";
        if (t.contains("instability")) return "instability";
        if (t.contains("depth")) return "dependencyDepth";
        if (t.contains("health")) return "architectureHealthScore";
        if (t.contains("cycle count") || t.contains("cycles")) return "cycleCount";
        return "unknown";
    }
}
