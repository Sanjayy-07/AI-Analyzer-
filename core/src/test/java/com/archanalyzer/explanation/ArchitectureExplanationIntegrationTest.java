package com.archanalyzer.explanation;

import com.archanalyzer.ai.MockLlmProvider;
import com.archanalyzer.explanation.evidence.*;
import com.archanalyzer.explanation.llm.*;
import com.archanalyzer.explanation.service.ArchitectureExplanationService;
import com.archanalyzer.explanation.verification.*;
import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService.ComponentRiskResult;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService.RiskCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rigorous integration and verification tests for Novelty 4:
 * LLM-Based Architecture Explanations with Automatic Claim Verification.
 */
public class ArchitectureExplanationIntegrationTest {

    private ProjectModel model;
    private DependencyGraph graph;
    private MetricsEngine.ProjectMetrics projectMetrics;
    private List<ComponentRiskResult> riskResults;
    private ArchitectureEvidence evidence;

    @BeforeEach
    public void setUp() {
        model = new ProjectModel("TestProject");

        // Build a synthetic 4-component architecture:
        // ServiceA -> ServiceB -> ServiceC -> ServiceA (SCC Cycle of 3 components)
        // ControllerD -> ServiceA (Acyclic incoming)
        ProjectModel.ComponentNode cA = new ProjectModel.ComponentNode("com.test.ServiceA", "ServiceA", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/ServiceA.java");
        ProjectModel.ComponentNode cB = new ProjectModel.ComponentNode("com.test.ServiceB", "ServiceB", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/ServiceB.java");
        ProjectModel.ComponentNode cC = new ProjectModel.ComponentNode("com.test.ServiceC", "ServiceC", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/ServiceC.java");
        ProjectModel.ComponentNode cD = new ProjectModel.ComponentNode("com.test.ControllerD", "ControllerD", "com.test", ProjectModel.ComponentNode.Kind.CLASS, "src/ControllerD.java");

        model.addComponent(cA);
        model.addComponent(cB);
        model.addComponent(cC);
        model.addComponent(cD);

        model.addDependency(new ProjectModel.DependencyEdge("com.test.ServiceA", "com.test.ServiceB", ProjectModel.DependencyEdge.Type.CALL));
        model.addDependency(new ProjectModel.DependencyEdge("com.test.ServiceB", "com.test.ServiceC", ProjectModel.DependencyEdge.Type.CALL));
        model.addDependency(new ProjectModel.DependencyEdge("com.test.ServiceC", "com.test.ServiceA", ProjectModel.DependencyEdge.Type.CALL));
        model.addDependency(new ProjectModel.DependencyEdge("com.test.ControllerD", "com.test.ServiceA", ProjectModel.DependencyEdge.Type.CALL));

        graph = DependencyGraph.buildFrom(model);

        MetricsEngine metricsEngine = new MetricsEngine();
        projectMetrics = metricsEngine.analyze(model, graph);

        riskResults = List.of(
                new ComponentRiskResult("com.test.ServiceA", 0.85, 85.0, RiskCategory.HIGH, true, List.of("High fan-in"), List.of("Frequent churn"), List.of(), Map.of(), Map.of()),
                new ComponentRiskResult("com.test.ServiceB", 0.60, 60.0, RiskCategory.MEDIUM, true, List.of("In cycle"), List.of("Moderate churn"), List.of(), Map.of(), Map.of()),
                new ComponentRiskResult("com.test.ControllerD", 0.15, 15.0, RiskCategory.LOW, false, List.of("Acyclic"), List.of("Low churn"), List.of(), Map.of(), Map.of())
        );

        EvidenceCollector collector = new EvidenceCollector();
        evidence = collector.collect(model, graph, projectMetrics, riskResults);
    }

    @Test
    @DisplayName("EvidenceCollector: Extracts authoritative ground truth across metrics, graph, cycles, and risk")
    public void testEvidenceCollectorCompleteness() {
        assertNotNull(evidence);
        assertTrue(evidence.size() >= 10, "Should extract at least 10 authoritative ground truth items");

        // Verify components
        assertTrue(evidence.containsComponent("com.test.ServiceA"));
        assertTrue(evidence.containsComponent("com.test.ControllerD"));
        assertFalse(evidence.containsComponent("com.test.FakeComponent"));

        // Verify directed dependencies
        assertTrue(evidence.hasDependency("com.test.ServiceA", "com.test.ServiceB"));
        assertTrue(evidence.hasDependency("com.test.ControllerD", "com.test.ServiceA"));
        assertFalse(evidence.hasDependency("com.test.ServiceB", "com.test.ServiceA"), "Inverted dependency must be false");

        // Verify SCC cycles
        assertTrue(evidence.isInCycle("com.test.ServiceA"));
        assertTrue(evidence.isInCycle("com.test.ServiceB"));
        assertTrue(evidence.isInCycle("com.test.ServiceC"));
        assertFalse(evidence.isInCycle("com.test.ControllerD"), "ControllerD is acyclic");

        // Verify metrics
        Double fanInA = evidence.getMetricValue("com.test.ServiceA", "fanIn");
        assertNotNull(fanInA);
        assertEquals(2.0, fanInA, 0.001, "ServiceA has incoming from ServiceC and ControllerD");

        // Verify risk
        Double riskScoreA = evidence.getRiskScore("com.test.ServiceA");
        assertNotNull(riskScoreA);
        assertEquals(0.85, riskScoreA, 0.001);
        assertEquals("HIGH", evidence.getRiskCategory("com.test.ServiceA"));
    }

    @Test
    @DisplayName("MetricClaimVerifier: Accurately verifies exact, partial, contradicted, and unsupported metric facts")
    public void testMetricClaimVerifier() {
        MetricClaimVerifier verifier = new MetricClaimVerifier();

        // 1. Exact match -> VERIFIED
        Claim validClaim = new Claim("c1", ClaimType.METRIC_FACT, "ServiceA has fanIn of 2", "com.test.ServiceA", null, "fanIn", 2.0, "2", List.of());
        ClaimVerificationResult res1 = verifier.verify(validClaim, evidence);
        assertEquals(ClaimVerificationStatus.VERIFIED, res1.status());

        // 2. Discrepant value -> CONTRADICTED
        Claim hallucinatedClaim = new Claim("c2", ClaimType.METRIC_FACT, "ServiceA has fanIn of 20", "com.test.ServiceA", null, "fanIn", 20.0, "20", List.of());
        ClaimVerificationResult res2 = verifier.verify(hallucinatedClaim, evidence);
        assertEquals(ClaimVerificationStatus.CONTRADICTED, res2.status());
        assertTrue(res2.explanation().contains("Contradicted"));

        // 3. Non-existent component -> UNSUPPORTED
        Claim unknownCompClaim = new Claim("c3", ClaimType.METRIC_FACT, "Missing has fanIn of 5", "com.test.MissingClass", null, "fanIn", 5.0, "5", List.of());
        ClaimVerificationResult res3 = verifier.verify(unknownCompClaim, evidence);
        assertEquals(ClaimVerificationStatus.UNSUPPORTED, res3.status());
    }

    @Test
    @DisplayName("DependencyClaimVerifier: Detects true edges, inverted hallucinations, and fabricated couplings")
    public void testDependencyClaimVerifier() {
        DependencyClaimVerifier verifier = new DependencyClaimVerifier();

        // 1. True directed edge -> VERIFIED
        Claim trueDep = new Claim("d1", ClaimType.DEPENDENCY_FACT, "ServiceA -> ServiceB", "com.test.ServiceA", "com.test.ServiceB", null, null, null, List.of());
        ClaimVerificationResult res1 = verifier.verify(trueDep, evidence);
        assertEquals(ClaimVerificationStatus.VERIFIED, res1.status());

        // 2. Inverted edge (ServiceB -> ServiceA claimed, actual is ServiceA -> ServiceB) -> CONTRADICTED
        Claim invertedDep = new Claim("d2", ClaimType.DEPENDENCY_FACT, "ServiceB -> ServiceA", "com.test.ServiceB", "com.test.ServiceA", null, null, null, List.of());
        ClaimVerificationResult res2 = verifier.verify(invertedDep, evidence);
        assertEquals(ClaimVerificationStatus.CONTRADICTED, res2.status());
        assertTrue(res2.explanation().contains("Inverted coupling") || res2.explanation().contains("ground truth is"));

        // 3. Completely fabricated edge -> CONTRADICTED
        Claim fabricatedDep = new Claim("d3", ClaimType.DEPENDENCY_FACT, "ControllerD -> ServiceC", "com.test.ControllerD", "com.test.ServiceC", null, null, null, List.of());
        ClaimVerificationResult res3 = verifier.verify(fabricatedDep, evidence);
        assertEquals(ClaimVerificationStatus.CONTRADICTED, res3.status());
    }

    @Test
    @DisplayName("CycleClaimVerifier: Verifies true cycles and catches false cycle allegations")
    public void testCycleClaimVerifier() {
        CycleClaimVerifier verifier = new CycleClaimVerifier();

        // 1. True cycle -> VERIFIED
        Claim trueCycle = new Claim("cy1", ClaimType.CYCLE_FACT, "ServiceA is in a cycle with ServiceB", "com.test.ServiceA", "com.test.ServiceB", "inCycle", 1.0, "true", List.of());
        ClaimVerificationResult res1 = verifier.verify(trueCycle, evidence);
        assertEquals(ClaimVerificationStatus.VERIFIED, res1.status());

        // 2. False cycle on acyclic component -> CONTRADICTED
        Claim falseCycle = new Claim("cy2", ClaimType.CYCLE_FACT, "ControllerD is in a circular cycle", "com.test.ControllerD", null, "inCycle", 1.0, "true", List.of());
        ClaimVerificationResult res2 = verifier.verify(falseCycle, evidence);
        assertEquals(ClaimVerificationStatus.CONTRADICTED, res2.status());
    }

    @Test
    @DisplayName("RiskClaimVerifier: Verifies high/low classifications and penalizes contradictory ML predictions")
    public void testRiskClaimVerifier() {
        RiskClaimVerifier verifier = new RiskClaimVerifier();

        // 1. High risk match -> VERIFIED
        Claim highRisk = new Claim("r1", ClaimType.RISK_FACT, "ServiceA has high risk probability", "com.test.ServiceA", null, "riskScore", 0.85, "HIGH", List.of());
        ClaimVerificationResult res1 = verifier.verify(highRisk, evidence);
        assertEquals(ClaimVerificationStatus.VERIFIED, res1.status());

        // 2. Opposing risk category (claimed LOW, actual HIGH) -> CONTRADICTED
        Claim opposingRisk = new Claim("r2", ClaimType.RISK_FACT, "ServiceA has minimal risk", "com.test.ServiceA", null, "riskScore", 0.05, "LOW", List.of());
        ClaimVerificationResult res2 = verifier.verify(opposingRisk, evidence);
        assertEquals(ClaimVerificationStatus.CONTRADICTED, res2.status());
    }

    @Test
    @DisplayName("ExplanationResponseParser: Strips markdown fences, extracts nested JSON, and parses claims")
    public void testResponseParserWithMarkdownFences() {
        String jsonPayload = """
            ```json
            {
              "overview": "The system contains a circular dependency cycle between ServiceA, ServiceB, and ServiceC.",
              "findings": [
                {
                  "findingId": "find-1",
                  "componentId": "com.test.ServiceA",
                  "issue": "Circular dependency",
                  "severity": "HIGH",
                  "explanation": "Participates in cycle with ServiceB and ServiceC",
                  "evidenceIds": ["ev-cycle-c1-servicea"]
                }
              ],
              "recommendations": [
                {
                  "recommendationId": "rec-1",
                  "targetComponent": "com.test.ServiceA",
                  "action": "Invert dependency",
                  "rationale": "Breaks the cycle",
                  "evidenceIds": ["ev-cycle-c1-servicea"],
                  "projectedBenefit": "Cycle elimination"
                }
              ],
              "claims": [
                {
                  "claimId": "c1",
                  "claimType": "CYCLE_FACT",
                  "subjectId": "com.test.ServiceA",
                  "metricName": "inCycle",
                  "claimedNumericValue": 1.0,
                  "claimedTextValue": "true",
                  "text": "ServiceA is in a cycle",
                  "evidenceIds": ["ev-cycle-c1-servicea"]
                }
              ]
            }
            ```
            """;

        ExplanationResponseParser parser = new ExplanationResponseParser();
        ExplanationResponse response = parser.parse(jsonPayload);

        assertNotNull(response);
        assertEquals("The system contains a circular dependency cycle between ServiceA, ServiceB, and ServiceC.", response.overview());
        assertEquals(1, response.findings().size());
        assertEquals(1, response.recommendations().size());
        assertEquals(1, response.extractedClaims().size());
        assertEquals("CYCLE_FACT", response.extractedClaims().get(0).claimType());
    }

    @Test
    @DisplayName("End-to-End: Verifies mock LLM with adversarial hallucinations and catches all contradictions")
    public void testEndToEndAdversarialHallucinationDetection() {
        // Create an adversarial LLM responder that injects 2 true facts and 2 blatant hallucinations:
        // True: ServiceA -> ServiceB (edge), ServiceA is in a cycle
        // Hallucination 1: ControllerD has fanOut = 99 (actual is 1)
        // Hallucination 2: ServiceB -> ServiceA (inverted dependency)
        MockLlmProvider mockLlm = new MockLlmProvider();
        mockLlm.setCustomResponder(prompt -> """
            {
              "overview": "Adversarial test explanation with intentional hallucinations.",
              "findings": [
                {"findingId": "f1", "componentId": "com.test.ServiceA", "issue": "Cycle", "severity": "HIGH", "explanation": "In cycle", "evidenceIds": []}
              ],
              "recommendations": [
                {"recommendationId": "r1", "targetComponent": "com.test.ServiceA", "action": "Invert", "rationale": "Fixes cycle", "evidenceIds": [], "projectedBenefit": "Acyclic"}
              ],
              "claims": [
                {
                  "claimId": "c-true-1",
                  "claimType": "DEPENDENCY_FACT",
                  "subjectId": "com.test.ServiceA",
                  "targetId": "com.test.ServiceB",
                  "text": "ServiceA depends on ServiceB"
                },
                {
                  "claimId": "c-true-2",
                  "claimType": "CYCLE_FACT",
                  "subjectId": "com.test.ServiceA",
                  "claimedNumericValue": 1.0,
                  "text": "ServiceA participates in an architectural cycle"
                },
                {
                  "claimId": "c-false-1",
                  "claimType": "METRIC_FACT",
                  "subjectId": "com.test.ControllerD",
                  "metricName": "fanOut",
                  "claimedNumericValue": 99.0,
                  "text": "ControllerD has an extreme fanOut of 99"
                },
                {
                  "claimId": "c-false-2",
                  "claimType": "DEPENDENCY_FACT",
                  "subjectId": "com.test.ServiceB",
                  "targetId": "com.test.ServiceA",
                  "text": "ServiceB calls ServiceA directly"
                }
              ]
            }
            """);

        ArchitectureExplanationService service = new ArchitectureExplanationService(mockLlm);
        VerifiedExplanation verified = service.explainProject(model, graph, projectMetrics, riskResults);

        assertNotNull(verified);
        assertEquals(4, verified.totalClaims());
        assertEquals(2, verified.verifiedCount(), "Exactly 2 true claims must be verified");
        assertEquals(2, verified.contradictedCount(), "Exactly 2 hallucinations must be flagged CONTRADICTED");
        assertEquals(50.0, verified.verificationRate(), 0.1);
        assertEquals(50.0, verified.contradictionRate(), 0.1);
        assertFalse(verified.isPublicationReady(), "Must reject publication readiness when contradiction rate > 0");

        // Verify specific verdicts
        ClaimVerificationResult cFalse1 = verified.verifiedClaims().stream()
                .filter(c -> c.claim().claimId().equals("c-false-1")).findFirst().orElseThrow();
        assertEquals(ClaimVerificationStatus.CONTRADICTED, cFalse1.status());

        ClaimVerificationResult cFalse2 = verified.verifiedClaims().stream()
                .filter(c -> c.claim().claimId().equals("c-false-2")).findFirst().orElseThrow();
        assertEquals(ClaimVerificationStatus.CONTRADICTED, cFalse2.status());
    }

    @Test
    @DisplayName("Deterministic Grounded Fallback: Yields 100% verified claims and 0% contradictions")
    public void testDeterministicGroundedFallback() {
        // Default MockLlmProvider without custom responder triggers deterministic grounded generation
        ArchitectureExplanationService service = new ArchitectureExplanationService(new MockLlmProvider());
        VerifiedExplanation verified = service.explainProject(model, graph, projectMetrics, riskResults);

        assertNotNull(verified);
        assertTrue(verified.totalClaims() >= 3, "Deterministic grounded explanation should generate multiple verifiable claims");
        assertEquals(0, verified.contradictedCount(), "Deterministic grounded explanation must have 0 contradictions");
        assertEquals(0.0, verified.contradictionRate(), 0.001);
        assertTrue(verified.verificationRate() >= 80.0, "Verification rate should be high for grounded facts");
    }
}
