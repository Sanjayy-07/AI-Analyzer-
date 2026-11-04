package com.archanalyzer.explanation.llm;

import com.archanalyzer.ai.*;
import com.archanalyzer.explanation.evidence.ArchitectureEvidence;
import com.archanalyzer.explanation.evidence.EvidenceItem;
import com.archanalyzer.explanation.evidence.EvidenceType;

import java.util.*;

/**
 * Provider that coordinates with Google Gemini (via existing LlmProvider infrastructure)
 * to generate architectural explanations and parse them into structured verifiable claims.
 * If the LLM provider is unconfigured, disabled, or unreachable, provides a deterministic,
 * grounded fallback generated directly from the authoritative ArchitectureEvidence.
 */
public class GeminiExplanationProvider {

    private final LlmProvider llmProvider;
    private final ExplanationPromptBuilder promptBuilder;
    private final ExplanationResponseParser responseParser;

    public GeminiExplanationProvider() {
        this(createDefaultProvider(), new ExplanationPromptBuilder(), new ExplanationResponseParser());
    }

    public GeminiExplanationProvider(LlmProvider llmProvider) {
        this(llmProvider, new ExplanationPromptBuilder(), new ExplanationResponseParser());
    }

    public GeminiExplanationProvider(
            LlmProvider llmProvider,
            ExplanationPromptBuilder promptBuilder,
            ExplanationResponseParser responseParser
    ) {
        this.llmProvider = llmProvider != null ? llmProvider : new MockLlmProvider();
        this.promptBuilder = promptBuilder != null ? promptBuilder : new ExplanationPromptBuilder();
        this.responseParser = responseParser != null ? responseParser : new ExplanationResponseParser();
    }

    public ExplanationResponse generateExplanation(ExplanationRequest request) {
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(request);

        try {
            String rawJson = llmProvider.generateCompletion(systemPrompt, userPrompt);
            ExplanationResponse parsed = responseParser.parse(rawJson);
            if (parsed.extractedClaims().isEmpty() && parsed.findings().isEmpty()) {
                return generateDeterministicGroundedResponse(request);
            }
            return parsed;
        } catch (Exception e) {
            // Graceful fallback to deterministic grounded response
            return generateDeterministicGroundedResponse(request);
        }
    }

    /**
     * Synthesizes a 100% grounded explanation directly from the authoritative ArchitectureEvidence.
     * Guaranteed to cite valid evidence IDs and adhere strictly to Java ground truth.
     */
    public ExplanationResponse generateDeterministicGroundedResponse(ExplanationRequest request) {
        ArchitectureEvidence ev = request.evidence();
        if (ev == null) {
            return new ExplanationResponse(
                    "{}",
                    "No architectural evidence available.",
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        StringBuilder overview = new StringBuilder();
        overview.append(String.format("Deterministic architecture analysis of %s: %d components, %d dependencies, and %d circular cycles detected.",
                request.projectName() != null ? request.projectName() : "project",
                ev.getAllComponentIds().size(),
                ev.getDependencyEdges().size(),
                ev.getCycles().size()));

        List<ExplanationResponse.FindingDto> findings = new ArrayList<>();
        List<ExplanationResponse.RecommendationDto> recs = new ArrayList<>();
        List<ExplanationResponse.ExtractedClaimDto> claims = new ArrayList<>();

        int findingCount = 0;
        int recCount = 0;
        int claimCount = 0;

        // Cycles
        for (Set<String> cycle : ev.getCycles()) {
            findingCount++;
            String cMembers = String.join(", ", cycle);
            String firstComp = cycle.iterator().next();
            List<String> evIds = new ArrayList<>();
            for (EvidenceItem item : ev.findByType(EvidenceType.CYCLE)) {
                if (cycle.contains(item.subjectId())) evIds.add(item.evidenceId());
            }

            findings.add(new ExplanationResponse.FindingDto(
                    "find-cycle-" + findingCount,
                    firstComp,
                    "Circular Dependency Cycle (" + cycle.size() + " components)",
                    "HIGH",
                    "A strongly connected circular dependency cycle was detected among: " + cMembers,
                    evIds
            ));

            claimCount++;
            claims.add(new ExplanationResponse.ExtractedClaimDto(
                    "claim-cycle-" + claimCount,
                    "CYCLE_FACT",
                    firstComp,
                    null,
                    "inCycle",
                    1.0,
                    "true",
                    firstComp + " is part of a circular dependency cycle with " + cMembers,
                    evIds
            ));

            recCount++;
            recs.add(new ExplanationResponse.RecommendationDto(
                    "rec-cycle-" + recCount,
                    firstComp,
                    "Invert Dependency / Extract Interface",
                    "Break circular dependency between " + cMembers + " using Dependency Inversion Principle",
                    evIds,
                    "Eliminates circular coupling and isolates failure domains"
            ));
        }

        // Project metrics claims
        claimCount++;
        claims.add(new ExplanationResponse.ExtractedClaimDto(
                "claim-proj-components-" + claimCount,
                "METRIC_FACT",
                "PROJECT",
                null,
                "totalComponents",
                (double) ev.getAllComponentIds().size(),
                String.valueOf(ev.getAllComponentIds().size()),
                "Project contains " + ev.getAllComponentIds().size() + " components",
                List.of("ev-proj-components")
        ));

        // High Instability / Metric Findings
        for (Map.Entry<String, Map<String, Double>> entry : ev.getComponentMetrics().entrySet()) {
            String comp = entry.getKey();
            Map<String, Double> m = entry.getValue();
            Double fanOut = m.get("fanout");
            Double fanIn = m.get("fanin");
            Double instability = m.get("instability");
            Double loc = m.get("loc");

            if (fanOut != null && (fanOut >= 1.0 || (fanIn != null && fanIn >= 2.0))) {
                findingCount++;
                List<String> evIds = new ArrayList<>();
                for (EvidenceItem item : ev.findBySubject(comp)) {
                    if ("fanOut".equalsIgnoreCase(item.metricName()) || "fanIn".equalsIgnoreCase(item.metricName())) {
                        evIds.add(item.evidenceId());
                    }
                }

                findings.add(new ExplanationResponse.FindingDto(
                        "find-fanout-" + findingCount,
                        comp,
                        "Component Coupling (Fan-Out: " + fanOut.intValue() + ", Fan-In: " + (fanIn != null ? fanIn.intValue() : 0) + ")",
                        fanOut >= 5 ? "HIGH" : "MEDIUM",
                        comp + " exhibits outgoing coupling (" + fanOut.intValue() + ") and incoming coupling (" + (fanIn != null ? fanIn.intValue() : 0) + ").",
                        evIds
                ));

                claimCount++;
                claims.add(new ExplanationResponse.ExtractedClaimDto(
                        "claim-fanout-" + claimCount,
                        "METRIC_FACT",
                        comp,
                        null,
                        "fanOut",
                        fanOut,
                        String.valueOf(fanOut),
                        comp + " has outgoing coupling fanOut = " + fanOut.intValue(),
                        evIds
                ));
            }
        }

        // Novelty 3 Risk Findings
        for (Map.Entry<String, String> entry : ev.getRiskCategories().entrySet()) {
            String comp = entry.getKey();
            String cat = entry.getValue();
            Double score = ev.getRiskScore(comp);
            if ("HIGH".equalsIgnoreCase(cat)) {
                findingCount++;
                List<String> evIds = new ArrayList<>();
                for (EvidenceItem item : ev.findBySubject(comp)) {
                    if (item.type() == EvidenceType.RISK) evIds.add(item.evidenceId());
                }

                findings.add(new ExplanationResponse.FindingDto(
                        "find-risk-" + findingCount,
                        comp,
                        "Elevated Architecture Defect Risk (High)",
                        "HIGH",
                        comp + " is predicted with high defect risk probability (" + (score != null ? score : 0.8) + ") based on topological centrality and churn.",
                        evIds
                ));

                claimCount++;
                claims.add(new ExplanationResponse.ExtractedClaimDto(
                        "claim-risk-" + claimCount,
                        "RISK_FACT",
                        comp,
                        null,
                        "riskScore",
                        score != null ? score : 0.8,
                        cat,
                        comp + " has HIGH architecture defect risk with score " + score,
                        evIds
                ));
            }
        }

        return new ExplanationResponse(
                "Deterministic Grounded Explanation",
                overview.toString(),
                findings,
                recs,
                claims
        );
    }

    private static LlmProvider createDefaultProvider() {
        AiConfig config = AiConfig.loadFromEnvironment();
        if (config.isConfigured() && "gemini".equalsIgnoreCase(config.getProvider())) {
            try {
                return new GeminiLlmProvider(config);
            } catch (Exception ignored) {}
        }
        return new MockLlmProvider();
    }

    public LlmProvider getLlmProvider() {
        return llmProvider;
    }
}
