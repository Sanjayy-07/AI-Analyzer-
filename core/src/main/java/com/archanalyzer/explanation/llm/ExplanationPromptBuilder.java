package com.archanalyzer.explanation.llm;

import com.archanalyzer.explanation.evidence.ArchitectureEvidence;

/**
 * Builds structured prompts for the LLM to generate architectural explanations
 * paired with verifiable claims mapped to evidence IDs.
 */
public class ExplanationPromptBuilder {

    public String buildSystemPrompt() {
        return """
            You are an expert software architect and static analysis evaluator in the AI-Powered Architecture Refactoring Analyzer.
            Your task is to analyze the authoritative architecture evidence provided below and generate a rigorous, factual architectural explanation.
            
            CRITICAL RULES FOR FACTUAL ACCURACY:
            1. Java is the AUTHORITATIVE source of truth. You MUST NEVER fabricate metrics, dependencies, cycles, or risk predictions.
            2. Every factual statement must cite one or more valid Evidence IDs from the register (e.g. "ev-metric-...", "ev-dep-...", "ev-cycle-...").
            3. Clearly separate factual assertions from interpretations and recommendations.
            4. You must output ONLY a valid JSON object matching the schema below. No markdown backticks, no prose outside JSON.
            
            JSON OUTPUT SCHEMA:
            {
              "overview": "High-level architectural assessment in 2-3 sentences.",
              "findings": [
                {
                  "findingId": "find-1",
                  "componentId": "Name of component",
                  "issue": "Brief description of architectural problem",
                  "severity": "HIGH|MEDIUM|LOW",
                  "explanation": "Detailed explanation citing facts.",
                  "evidenceIds": ["ev-metric-...", "ev-dep-..."]
                }
              ],
              "recommendations": [
                {
                  "recommendationId": "rec-1",
                  "targetComponent": "Name of component",
                  "action": "Specific refactoring action (e.g. Invert Dependency, Extract Interface)",
                  "rationale": "Why this refactoring helps",
                  "evidenceIds": ["ev-cycle-..."],
                  "projectedBenefit": "Expected architectural improvement"
                }
              ],
              "claims": [
                {
                  "claimId": "c-1",
                  "claimType": "METRIC_FACT|DEPENDENCY_FACT|CYCLE_FACT|RISK_FACT|INTERPRETATION|RECOMMENDATION",
                  "subjectId": "Component name or PROJECT",
                  "targetId": "Target component name if dependency, else null",
                  "metricName": "fanIn|fanOut|loc|cyclomaticComplexity|instability|dependencyDepth|null",
                  "claimedNumericValue": 12.0,
                  "claimedTextValue": "HIGH|null",
                  "text": "The exact natural language sentence being claimed.",
                  "evidenceIds": ["ev-metric-..."]
                }
              ]
            }
            """;
    }

    public String buildUserPrompt(ExplanationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT: ").append(request.projectName() != null ? request.projectName() : "Current Analysis").append("\n");

        if (request.targetComponentId() != null && !request.targetComponentId().isBlank()) {
            sb.append("TARGET FOCUS COMPONENT: ").append(request.targetComponentId()).append("\n");
        }

        if (request.userQuestion() != null && !request.userQuestion().isBlank()) {
            sb.append("USER ARCHITECT QUESTION: ").append(request.userQuestion()).append("\n");
        }

        sb.append("\n");
        ArchitectureEvidence ev = request.evidence();
        if (ev != null) {
            sb.append(ev.toPromptEvidenceSummary(150));
        } else {
            sb.append("No ground truth evidence register provided.\n");
        }

        sb.append("\nPlease generate your factual architectural explanation, findings, recommendations, and extracted claims in strict JSON format now.");
        return sb.toString();
    }
}
