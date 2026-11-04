package com.archanalyzer.explanation.verification;

import com.archanalyzer.explanation.evidence.ArchitectureEvidence;
import com.archanalyzer.explanation.evidence.EvidenceItem;

import java.util.*;

/**
 * Deterministically verifies directed dependency edge claims against the
 * ground-truth DependencyGraph built from compiler AST symbols.
 * Enforces topological directionality and detects inverted dependency hallucinations.
 */
public class DependencyClaimVerifier {

    public ClaimVerificationResult verify(Claim claim, ArchitectureEvidence evidence) {
        String from = claim.subjectId();
        String to = claim.targetId();

        if (from == null || to == null) {
            // Attempt to infer from text: "X depends on Y" or "X -> Y"
            String[] inferred = inferDependencyPair(claim.text());
            if (inferred != null) {
                from = inferred[0];
                to = inferred[1];
            }
        }

        if (from == null || to == null) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.UNSUPPORTED,
                    "Dependency claim does not specify both source and target components.",
                    null,
                    null,
                    List.of(),
                    List.of()
            );
        }

        boolean fromExists = evidence.containsComponent(from);
        boolean toExists = evidence.containsComponent(to);

        if (!fromExists || !toExists) {
            String missing = !fromExists && !toExists ? from + " and " + to : (!fromExists ? from : to);
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.UNSUPPORTED,
                    "Referenced component(s) '" + missing + "' do not exist in the analyzed project.",
                    null,
                    null,
                    List.of(),
                    List.of()
            );
        }

        String canonFrom = evidence.resolveCanonicalComponentName(from);
        String canonTo = evidence.resolveCanonicalComponentName(to);

        List<String> supportingEvs = new ArrayList<>();
        List<String> contradictingEvs = new ArrayList<>();
        for (String evId : claim.citedEvidenceIds()) {
            Optional<EvidenceItem> itemOpt = evidence.findById(evId);
            if (itemOpt.isPresent()) {
                EvidenceItem item = itemOpt.get();
                if (canonFrom.equalsIgnoreCase(item.subjectId()) && canonTo.equalsIgnoreCase(item.targetId())) {
                    supportingEvs.add(evId);
                } else {
                    contradictingEvs.add(evId);
                }
            }
        }

        boolean directEdge = evidence.hasDependency(canonFrom, canonTo);
        boolean reverseEdge = evidence.hasDependency(canonTo, canonFrom);

        if (directEdge) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.VERIFIED,
                    String.format("Verified dependency fact: %s -> %s exists in the static dependency graph.", canonFrom, canonTo),
                    1.0,
                    canonFrom + " -> " + canonTo,
                    supportingEvs,
                    contradictingEvs
            );
        } else if (reverseEdge) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.CONTRADICTED,
                    String.format("Contradicted dependency fact (Inverted coupling): claimed %s -> %s, but ground truth is %s -> %s.",
                            canonFrom, canonTo, canonTo, canonFrom),
                    0.0,
                    canonTo + " -> " + canonFrom,
                    supportingEvs,
                    contradictingEvs
            );
        } else {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.CONTRADICTED,
                    String.format("Contradicted dependency fact: no architectural dependency edge exists between %s and %s in either direction.",
                            canonFrom, canonTo),
                    0.0,
                    "NO_EDGE",
                    supportingEvs,
                    contradictingEvs
            );
        }
    }

    private String[] inferDependencyPair(String text) {
        if (text == null) return null;
        if (text.contains("->")) {
            String[] parts = text.split("->");
            if (parts.length == 2) {
                return new String[]{cleanWord(parts[0]), cleanWord(parts[1])};
            }
        }
        if (text.toLowerCase().contains("depends on")) {
            String[] parts = text.split("(?i)depends on");
            if (parts.length == 2) {
                return new String[]{cleanWord(parts[0]), cleanWord(parts[1])};
            }
        }
        if (text.toLowerCase().contains("calls")) {
            String[] parts = text.split("(?i)calls");
            if (parts.length == 2) {
                return new String[]{cleanWord(parts[0]), cleanWord(parts[1])};
            }
        }
        return null;
    }

    private String cleanWord(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-zA-Z0-9_.]", " ").trim().split("\\s+")[0];
    }
}
