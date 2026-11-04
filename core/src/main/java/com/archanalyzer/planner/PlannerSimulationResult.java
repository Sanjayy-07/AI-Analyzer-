package com.archanalyzer.planner;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.planner.RefactoringConflictDetector.PlanConflict;

import java.util.*;

/**
 * Output of a multi-operation architecture change simulation.
 *
 * CRITICAL SCIENTIFIC PROPERTY:
 * All simulated metrics are recomputed deterministically from the complete modified dependency graph.
 * Combined metric deltas are NEVER computed by summing individual operation deltas.
 */
public class PlannerSimulationResult {

    public enum Status {
        SUCCESS,
        CONFLICT_BLOCKED,
        INTEGRITY_VIOLATION
    }

    private final String planId;
    private final String planHash;
    private final long timestamp;
    private Status status;
    private boolean success;

    private ProjectMetrics baselineMetrics;
    private ProjectMetrics simulatedMetrics;
    private final Map<String, MetricDelta> metricDeltas = new LinkedHashMap<>();

    private int baselineCycleCount;
    private int simulatedCycleCount;
    private List<Set<String>> baselineCycles = new ArrayList<>();
    private List<Set<String>> simulatedCycles = new ArrayList<>();

    private final Set<String> addedNodes = new LinkedHashSet<>();
    private final Set<String> removedNodes = new LinkedHashSet<>();
    private final Set<String> addedEdges = new LinkedHashSet<>();
    private final Set<String> removedEdges = new LinkedHashSet<>();

    private final List<PlanConflict> conflicts = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> tradeoffs = new ArrayList<>();
    private final List<RefactoringOperation> appliedOperations = new ArrayList<>();

    private final boolean isAdditiveSum = false;
    private String calculationMethod = "Complete Graph Topology Recalculation (Non-Additive)";

    public PlannerSimulationResult(String planId, String planHash) {
        this.planId = planId;
        this.planHash = planHash;
        this.timestamp = System.currentTimeMillis();
    }

    public static PlannerSimulationResult conflictBlocked(String planId, String planHash, List<PlanConflict> conflicts) {
        PlannerSimulationResult res = new PlannerSimulationResult(planId, planHash);
        res.status = Status.CONFLICT_BLOCKED;
        res.success = false;
        if (conflicts != null) res.conflicts.addAll(conflicts);
        return res;
    }

    public static PlannerSimulationResult integrityViolation(String planId, String planHash, List<String> violations) {
        PlannerSimulationResult res = new PlannerSimulationResult(planId, planHash);
        res.status = Status.INTEGRITY_VIOLATION;
        res.success = false;
        if (violations != null) res.warnings.addAll(violations);
        return res;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────────────────────────────────

    public String getPlanId() { return planId; }
    public String getPlanHash() { return planHash; }
    public long getTimestamp() { return timestamp; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public ProjectMetrics getBaselineMetrics() { return baselineMetrics; }
    public void setBaselineMetrics(ProjectMetrics baselineMetrics) { this.baselineMetrics = baselineMetrics; }
    public ProjectMetrics getSimulatedMetrics() { return simulatedMetrics; }
    public void setSimulatedMetrics(ProjectMetrics simulatedMetrics) { this.simulatedMetrics = simulatedMetrics; }

    public Map<String, MetricDelta> getMetricDeltas() { return Collections.unmodifiableMap(metricDeltas); }
    public void addMetricDelta(MetricDelta delta) { this.metricDeltas.put(delta.getMetricName(), delta); }

    public int getBaselineCycleCount() { return baselineCycleCount; }
    public void setBaselineCycleCount(int baselineCycleCount) { this.baselineCycleCount = baselineCycleCount; }
    public int getSimulatedCycleCount() { return simulatedCycleCount; }
    public void setSimulatedCycleCount(int simulatedCycleCount) { this.simulatedCycleCount = simulatedCycleCount; }

    public List<Set<String>> getBaselineCycles() { return Collections.unmodifiableList(baselineCycles); }
    public void setBaselineCycles(List<Set<String>> baselineCycles) {
        this.baselineCycles = baselineCycles != null ? new ArrayList<>(baselineCycles) : new ArrayList<>();
    }
    public List<Set<String>> getSimulatedCycles() { return Collections.unmodifiableList(simulatedCycles); }
    public void setSimulatedCycles(List<Set<String>> simulatedCycles) {
        this.simulatedCycles = simulatedCycles != null ? new ArrayList<>(simulatedCycles) : new ArrayList<>();
    }

    public Set<String> getAddedNodes() { return Collections.unmodifiableSet(addedNodes); }
    public void setAddedNodes(Set<String> addedNodes) {
        this.addedNodes.clear();
        if (addedNodes != null) this.addedNodes.addAll(addedNodes);
    }
    public Set<String> getRemovedNodes() { return Collections.unmodifiableSet(removedNodes); }
    public void setRemovedNodes(Set<String> removedNodes) {
        this.removedNodes.clear();
        if (removedNodes != null) this.removedNodes.addAll(removedNodes);
    }
    public Set<String> getAddedEdges() { return Collections.unmodifiableSet(addedEdges); }
    public void setAddedEdges(Set<String> addedEdges) {
        this.addedEdges.clear();
        if (addedEdges != null) this.addedEdges.addAll(addedEdges);
    }
    public Set<String> getRemovedEdges() { return Collections.unmodifiableSet(removedEdges); }
    public void setRemovedEdges(Set<String> removedEdges) {
        this.removedEdges.clear();
        if (removedEdges != null) this.removedEdges.addAll(removedEdges);
    }

    public List<PlanConflict> getConflicts() { return Collections.unmodifiableList(conflicts); }
    public void setConflicts(List<PlanConflict> conflicts) {
        this.conflicts.clear();
        if (conflicts != null) this.conflicts.addAll(conflicts);
    }

    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
    public void addWarning(String warning) { this.warnings.add(warning); }

    public List<String> getTradeoffs() { return Collections.unmodifiableList(tradeoffs); }
    public void addTradeoff(String tradeoff) { this.tradeoffs.add(tradeoff); }

    public List<RefactoringOperation> getAppliedOperations() { return Collections.unmodifiableList(appliedOperations); }
    public void setAppliedOperations(List<RefactoringOperation> ops) {
        this.appliedOperations.clear();
        if (ops != null) this.appliedOperations.addAll(ops);
    }

    public boolean isAdditiveSum() { return isAdditiveSum; }
    public String getCalculationMethod() { return calculationMethod; }
    public void setCalculationMethod(String method) { this.calculationMethod = method; }
}
