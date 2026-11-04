package com.archanalyzer.explanation.verification;

import com.archanalyzer.explanation.evidence.ArchitectureEvidence;
import com.archanalyzer.explanation.evidence.EvidenceItem;

import java.util.*;

/**
 * Deterministically verifies circular dependency and architectural cycle claims against
 * Tarjan's Strongly Connected Components (SCC) decomposition of the dependency graph.
 */
public class CycleClaimVerifier {

    public ClaimVerificationResult verify(Claim claim, ArchitectureEvidence evidence) {
        String subject = claim.subjectId();
        if (subject == null || subject.isBlank()) {
            subject = inferComponent(claim.text(), evidence);
        }

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
        boolean actualInCycle = evidence.isInCycle(canonSubject);
        Set<String> cycleMembers = evidence.getCycleMembers(canonSubject);

        boolean claimedInCycle = isAssertingCycle(claim);

        List<String> supportingEvs = new ArrayList<>();
        List<String> contradictingEvs = new ArrayList<>();
        for (String evId : claim.citedEvidenceIds()) {
            Optional<EvidenceItem> itemOpt = evidence.findById(evId);
            if (itemOpt.isPresent()) {
                EvidenceItem item = itemOpt.get();
                if (canonSubject.equalsIgnoreCase(item.subjectId()) && item.type() == com.archanalyzer.explanation.evidence.EvidenceType.CYCLE) {
                    supportingEvs.add(evId);
                } else {
                    contradictingEvs.add(evId);
                }
            }
        }

        if (claimedInCycle && actualInCycle) {
            String partner = claim.targetId();
            if (partner != null && !partner.isBlank()) {
                String canonPartner = evidence.resolveCanonicalComponentName(partner);
                if (cycleMembers.contains(canonPartner) || cycleMembers.contains(partner)) {
                    return new ClaimVerificationResult(
                            claim,
                            ClaimVerificationStatus.VERIFIED,
                            String.format("Verified cycle fact: %s and %s participate in the same circular dependency cycle: %s.",
                                    canonSubject, canonPartner, cycleMembers),
                            1.0,
                            String.join(", ", cycleMembers),
                            supportingEvs,
                            contradictingEvs
                    );
                } else {
                    return new ClaimVerificationResult(
                            claim,
                            ClaimVerificationStatus.PARTIALLY_VERIFIED,
                            String.format("Partially verified cycle: %s is in a cycle (%s), but partner %s is not in this cycle.",
                                    canonSubject, cycleMembers, partner),
                            0.5,
                            String.join(", ", cycleMembers),
                            supportingEvs,
                            contradictingEvs
                    );
                }
            }

            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.VERIFIED,
                    String.format("Verified cycle fact: %s belongs to strongly connected cycle: %s.",
                            canonSubject, cycleMembers),
                    1.0,
                    String.join(", ", cycleMembers),
                    supportingEvs,
                    contradictingEvs
            );
        } else if (claimedInCycle && !actualInCycle) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.CONTRADICTED,
                    String.format("Contradicted cycle fact: claimed %s is in a circular cycle, but ground-truth Tarjan SCC confirms it is cycle-free.",
                            canonSubject),
                    0.0,
                    "NO_CYCLE",
                    supportingEvs,
                    contradictingEvs
            );
        } else if (!claimedInCycle && actualInCycle) {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.CONTRADICTED,
                    String.format("Contradicted claim: claimed %s has no cycles, but it participates in circular cycle: %s.",
                            canonSubject, cycleMembers),
                    1.0,
                    String.join(", ", cycleMembers),
                    supportingEvs,
                    contradictingEvs
            );
        } else {
            return new ClaimVerificationResult(
                    claim,
                    ClaimVerificationStatus.VERIFIED,
                    String.format("Verified acyclic state: %s is cycle-free as claimed.", canonSubject),
                    0.0,
                    "NO_CYCLE",
                    supportingEvs,
                    contradictingEvs
            );
        }
    }

    private boolean isAssertingCycle(Claim claim) {
        if (claim.claimedNumericValue() != null && claim.claimedNumericValue() > 0) return true;
        if (claim.claimedTextValue() != null && "true".equalsIgnoreCase(claim.claimedTextValue())) return true;
        String t = claim.text().toLowerCase();
        if (t.contains("no cycle") || t.contains("acyclic") || t.contains("does not participate in a cycle")) {
            return false;
        }
        return t.contains("cycle") || t.contains("circular") || t.contains("tangled") || t.contains("scc");
    }

    private String inferComponent(String text, ArchitectureEvidence evidence) {
        if (text == null) return null;
        for (String c : evidence.getAllComponentIds()) {
            if (text.contains(c)) return c;
            int idx = c.lastIndexOf('.');
            String simple = idx >= 0 ? c.substring(idx + 1) : c;
            if (text.contains(simple)) return c;
        }
        return null;
    }
}
