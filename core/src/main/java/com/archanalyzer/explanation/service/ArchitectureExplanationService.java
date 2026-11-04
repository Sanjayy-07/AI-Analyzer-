package com.archanalyzer.explanation.service;

import com.archanalyzer.ai.LlmProvider;
import com.archanalyzer.explanation.evidence.ArchitectureEvidence;
import com.archanalyzer.explanation.evidence.EvidenceCollector;
import com.archanalyzer.explanation.llm.ExplanationRequest;
import com.archanalyzer.explanation.llm.ExplanationResponse;
import com.archanalyzer.explanation.llm.GeminiExplanationProvider;
import com.archanalyzer.explanation.verification.ClaimVerificationEngine;
import com.archanalyzer.explanation.verification.VerifiedExplanation;
import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService;

import java.util.List;

/**
 * Service facade for Novelty 4: LLM-Based Architecture Explanations with Automatic Claim Verification.
 * Coordinates evidence collection, Gemini prompt generation, JSON response parsing,
 * and deterministic Java claim verification.
 */
public class ArchitectureExplanationService {

    private final EvidenceCollector evidenceCollector;
    private final GeminiExplanationProvider explanationProvider;
    private final ClaimVerificationEngine verificationEngine;

    public ArchitectureExplanationService() {
        this(new EvidenceCollector(), new GeminiExplanationProvider(), new ClaimVerificationEngine());
    }

    public ArchitectureExplanationService(LlmProvider llmProvider) {
        this(new EvidenceCollector(), new GeminiExplanationProvider(llmProvider), new ClaimVerificationEngine());
    }

    public ArchitectureExplanationService(
            EvidenceCollector evidenceCollector,
            GeminiExplanationProvider explanationProvider,
            ClaimVerificationEngine verificationEngine
    ) {
        this.evidenceCollector = evidenceCollector != null ? evidenceCollector : new EvidenceCollector();
        this.explanationProvider = explanationProvider != null ? explanationProvider : new GeminiExplanationProvider();
        this.verificationEngine = verificationEngine != null ? verificationEngine : new ClaimVerificationEngine();
    }

    public VerifiedExplanation explainProject(
            ProjectModel model,
            DependencyGraph graph,
            MetricsEngine.ProjectMetrics metrics,
            List<ArchitectureRiskService.ComponentRiskResult> risks
    ) {
        long start = System.currentTimeMillis();
        ArchitectureEvidence evidence = evidenceCollector.collect(model, graph, metrics, risks);
        ExplanationRequest request = new ExplanationRequest(
                model != null && model.getProjectName() != null ? model.getProjectName() : "Project",
                evidence
        );
        ExplanationResponse response = explanationProvider.generateExplanation(request);
        long duration = System.currentTimeMillis() - start;
        String modelName = explanationProvider.getLlmProvider().getModelName();
        return verificationEngine.verifyExplanation(response, evidence, modelName, duration);
    }

    public VerifiedExplanation explainComponent(
            ProjectModel model,
            DependencyGraph graph,
            MetricsEngine.ProjectMetrics metrics,
            List<ArchitectureRiskService.ComponentRiskResult> risks,
            String targetComponentId
    ) {
        long start = System.currentTimeMillis();
        ArchitectureEvidence evidence = evidenceCollector.collect(model, graph, metrics, risks);
        ExplanationRequest request = new ExplanationRequest(
                model != null && model.getProjectName() != null ? model.getProjectName() : "Project",
                targetComponentId,
                evidence
        );
        ExplanationResponse response = explanationProvider.generateExplanation(request);
        long duration = System.currentTimeMillis() - start;
        String modelName = explanationProvider.getLlmProvider().getModelName();
        return verificationEngine.verifyExplanation(response, evidence, modelName, duration);
    }

    public VerifiedExplanation answerArchitectQuestion(
            ProjectModel model,
            DependencyGraph graph,
            MetricsEngine.ProjectMetrics metrics,
            List<ArchitectureRiskService.ComponentRiskResult> risks,
            String userQuestion
    ) {
        long start = System.currentTimeMillis();
        ArchitectureEvidence evidence = evidenceCollector.collect(model, graph, metrics, risks);
        ExplanationRequest request = new ExplanationRequest(
                model != null && model.getProjectName() != null ? model.getProjectName() : "Project",
                userQuestion,
                evidence,
                true
        );
        ExplanationResponse response = explanationProvider.generateExplanation(request);
        long duration = System.currentTimeMillis() - start;
        String modelName = explanationProvider.getLlmProvider().getModelName();
        return verificationEngine.verifyExplanation(response, evidence, modelName, duration);
    }

    public ArchitectureEvidence collectEvidence(
            ProjectModel model,
            DependencyGraph graph,
            MetricsEngine.ProjectMetrics metrics,
            List<ArchitectureRiskService.ComponentRiskResult> risks
    ) {
        return evidenceCollector.collect(model, graph, metrics, risks);
    }

    public EvidenceCollector getEvidenceCollector() {
        return evidenceCollector;
    }

    public GeminiExplanationProvider getExplanationProvider() {
        return explanationProvider;
    }

    public ClaimVerificationEngine getVerificationEngine() {
        return verificationEngine;
    }
}
