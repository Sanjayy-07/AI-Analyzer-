package com.archanalyzer.web.dto;

import com.archanalyzer.refactor.CycleRefactoringService.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates DEFECT-S1-03 remediation:
 * Ensures JsonUtil serializes structured DTOs (CycleAnalysisDto, CycleExecutionDto, SccDto, CandidateEdgeDto)
 * into structured JSON objects rather than opaque ClassName@hash toString() strings.
 */
public class JsonUtilTest {

    @Test
    @DisplayName("CycleAnalysisDto serializes into structured JSON without opaque toString strings")
    public void testCycleAnalysisDtoSerialization() {
        CycleAnalysisDto dto = new CycleAnalysisDto();
        dto.totalCyclicSccs = 1;

        SccDto scc = new SccDto();
        scc.members = List.of("com.demo.A", "com.demo.B");

        CandidateEdgeDto candidate = new CandidateEdgeDto();
        candidate.fromComponent = "com.demo.A";
        candidate.toComponent = "com.demo.B";
        candidate.costScore = 1.5;
        candidate.feasible = true;
        candidate.rationale = "Lowest coupling edge";

        scc.candidates = List.of(candidate);
        scc.selectedEdge = candidate;
        dto.cyclicSccs = List.of(scc);

        String json = JsonUtil.toJson(dto);

        assertNotNull(json);
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertFalse(json.contains("CycleAnalysisDto@"), "Must not contain opaque toString memory address");
        assertFalse(json.contains("SccDto@"), "Nested DTO must not contain opaque toString memory address");
        assertFalse(json.contains("CandidateEdgeDto@"), "Nested candidate DTO must not contain opaque memory address");

        // Key fields verification
        assertTrue(json.contains("\"totalCyclicSccs\":1"));
        assertTrue(json.contains("\"cyclicSccs\":["));
        assertTrue(json.contains("\"members\":[\"com.demo.A\",\"com.demo.B\"]"));
        assertTrue(json.contains("\"fromComponent\":\"com.demo.A\""));
        assertTrue(json.contains("\"toComponent\":\"com.demo.B\""));
        assertTrue(json.contains("\"costScore\":1.5"));
        assertTrue(json.contains("\"feasible\":true"));
        assertTrue(json.contains("\"rationale\":\"Lowest coupling edge\""));
    }

    @Test
    @DisplayName("CycleExecutionDto serializes into structured JSON with accessible fields")
    public void testCycleExecutionDtoSerialization() {
        CycleExecutionDto exec = new CycleExecutionDto();
        exec.success = true;
        exec.status = "VERIFIED";
        exec.initialCycleCount = 2;
        exec.finalCycleCount = 0;
        exec.modifiedFiles = List.of("ServiceA.java", "ServiceB.java");
        exec.errors = List.of();
        exec.durationMs = 150;
        exec.summary = "Broke cycle successfully";
        exec.compilerStatus = "PASS";
        exec.testSummary = "All tests passed";
        exec.architectureHealthBefore = 75.0;
        exec.architectureHealthAfter = 92.5;

        Map<String, Object> respMap = Map.of(
                "status", "ok",
                "result", exec
        );

        String json = JsonUtil.toJson(respMap);

        assertNotNull(json);
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertFalse(json.contains("CycleExecutionDto@"), "Must not contain opaque toString memory address");

        assertTrue(json.contains("\"status\":\"ok\""));
        assertTrue(json.contains("\"result\":{"));
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"status\":\"VERIFIED\""));
        assertTrue(json.contains("\"initialCycleCount\":2"));
        assertTrue(json.contains("\"finalCycleCount\":0"));
        assertTrue(json.contains("\"modifiedFiles\":[\"ServiceA.java\",\"ServiceB.java\"]"));
        assertTrue(json.contains("\"summary\":\"Broke cycle successfully\""));
        assertTrue(json.contains("\"compilerStatus\":\"PASS\""));
        assertTrue(json.contains("\"architectureHealthBefore\":75.0"));
        assertTrue(json.contains("\"architectureHealthAfter\":92.5"));
    }

    @Test
    @DisplayName("Empty DTOs and null values serialize cleanly according to API contract")
    public void testEmptyDtoAndNullHandling() {
        CycleExecutionDto emptyExec = new CycleExecutionDto();
        String json = JsonUtil.toJson(emptyExec);

        assertNotNull(json);
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("\"status\":null"));
        assertTrue(json.contains("\"modifiedFiles\":[]"));
        assertTrue(json.contains("\"errors\":[]"));
    }

    @Test
    @DisplayName("Novelty 2: RefactoringOperation serializes private fields into structured JSON")
    public void testRefactoringOperationSerialization() {
        com.archanalyzer.planner.RefactoringOperation op = com.archanalyzer.planner.RefactoringOperation.breakCycle(
                "com.demo.OrderService", "com.demo.InventoryService", "com.demo.IInventoryService"
        );

        String json = JsonUtil.toJson(op);

        assertNotNull(json);
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertNotEquals("{}", json, "RefactoringOperation must not serialize to empty JSON object");
        assertTrue(json.contains("\"type\":\"BREAK_CYCLE\""));
        assertTrue(json.contains("\"targetComponent\":\"com.demo.OrderService\""));
        assertTrue(json.contains("\"secondaryComponent\":\"com.demo.InventoryService\""));
        assertTrue(json.contains("\"newComponent\":\"com.demo.IInventoryService\""));
        assertTrue(json.contains("\"description\":"));
        assertTrue(json.contains("\"parameters\":{"));
        assertTrue(json.contains("\"preconditions\":["));
        assertTrue(json.contains("\"expectedTransformations\":["));
    }

    @Test
    @DisplayName("Novelty 2: RefactoringPlan serializes operations and metadata")
    public void testRefactoringPlanSerialization() {
        com.archanalyzer.planner.RefactoringPlan plan = new com.archanalyzer.planner.RefactoringPlan("plan-test-1", "Test Plan");
        com.archanalyzer.planner.RefactoringOperation op = com.archanalyzer.planner.RefactoringOperation.extractInterface(
                "com.demo.OrderService", "com.demo.IOrderService", true
        );
        plan.addOperation(op);

        String json = JsonUtil.toJson(plan);

        assertNotNull(json);
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertTrue(json.contains("\"planId\":\"plan-test-1\""));
        assertTrue(json.contains("\"name\":\"Test Plan\""));
        assertTrue(json.contains("\"lifecycle\":\"DRAFT\""));
        assertTrue(json.contains("\"operations\":["));
        assertTrue(json.contains("\"type\":\"EXTRACT_INTERFACE\""));
        assertTrue(json.contains("\"targetComponent\":\"com.demo.OrderService\""));
    }

    @Test
    @DisplayName("Novelty 2: MetricDelta serializes values, delta, and improvement flags")
    public void testMetricDeltaSerialization() {
        com.archanalyzer.planner.MetricDelta delta = new com.archanalyzer.planner.MetricDelta(
                "Architecture Health", 75.0, 92.5, com.archanalyzer.planner.MetricDelta.Preference.HIGHER_IS_BETTER
        );

        String json = JsonUtil.toJson(delta);

        assertNotNull(json);
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertTrue(json.contains("\"metricName\":\"Architecture Health\""));
        assertTrue(json.contains("\"baselineValue\":75.0"));
        assertTrue(json.contains("\"simulatedValue\":92.5"));
        assertTrue(json.contains("\"absoluteDelta\":17.5"));
        assertTrue(json.contains("\"percentageChange\":23.33"));
        assertTrue(json.contains("\"preference\":\"HIGHER_IS_BETTER\""));
        assertTrue(json.contains("\"isImprovement\":true"));
    }

    @Test
    @DisplayName("Novelty 2: PlannerSimulationResult serializes metric deltas and conflicts")
    public void testPlannerSimulationResultSerialization() {
        com.archanalyzer.planner.PlannerSimulationResult sim = new com.archanalyzer.planner.PlannerSimulationResult("plan-1", "hash-123");
        sim.setStatus(com.archanalyzer.planner.PlannerSimulationResult.Status.SUCCESS);
        sim.setSuccess(true);
        sim.setBaselineMetrics(new com.archanalyzer.metrics.MetricsEngine.ProjectMetrics());
        sim.setSimulatedMetrics(new com.archanalyzer.metrics.MetricsEngine.ProjectMetrics());
        sim.addMetricDelta(new com.archanalyzer.planner.MetricDelta("Architecture Health", 70.0, 85.0, com.archanalyzer.planner.MetricDelta.Preference.HIGHER_IS_BETTER));
        sim.setAddedNodes(Set.of("com.demo.INode"));
        sim.addTradeoff("Coupling reduced");

        String json = JsonUtil.toJson(sim);

        assertNotNull(json);
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertNotEquals("{}", json);
        assertTrue(json.contains("\"planId\":\"plan-1\""));
        assertTrue(json.contains("\"planHash\":\"hash-123\""));
        assertTrue(json.contains("\"status\":\"SUCCESS\""));
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"metricDeltas\":{"));
        assertTrue(json.contains("\"Architecture Health\":{"));
        assertTrue(json.contains("\"baselineValue\":70.0"));
        assertTrue(json.contains("\"simulatedValue\":85.0"));
        assertTrue(json.contains("\"addedNodes\":[\"com.demo.INode\"]"));
        assertTrue(json.contains("\"tradeoffs\":[\"Coupling reduced\"]"));
    }

    @Test
    @DisplayName("Novelty 2: PlanConflict serializes category, severity, and message")
    public void testPlanConflictSerialization() {
        com.archanalyzer.planner.RefactoringConflictDetector.PlanConflict conflict =
                new com.archanalyzer.planner.RefactoringConflictDetector.PlanConflict(
                        com.archanalyzer.planner.RefactoringConflictDetector.ConflictSeverity.ERROR,
                        com.archanalyzer.planner.RefactoringConflictDetector.ConflictCategory.CONTRADICTORY_TRANSFORMATION,
                        List.of("op-1", "op-2"),
                        "Conflicting transformations on target",
                        "Remove one of the operations"
                );

        String json = JsonUtil.toJson(conflict);

        assertNotNull(json);
        assertTrue(json.contains("\"severity\":\"ERROR\""));
        assertTrue(json.contains("\"category\":\"CONTRADICTORY_TRANSFORMATION\""));
        assertTrue(json.contains("\"operationIds\":[\"op-1\",\"op-2\"]"));
        assertTrue(json.contains("\"message\":\"Conflicting transformations on target\""));
        assertTrue(json.contains("\"remedy\":\"Remove one of the operations\""));
    }
}
