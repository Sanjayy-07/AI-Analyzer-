package com.archanalyzer.planner;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.planner.MetricDelta.Preference;
import com.archanalyzer.planner.RefactoringConflictDetector.ConflictSeverity;
import com.archanalyzer.planner.RefactoringConflictDetector.PlanConflict;

import java.util.*;

/**
 * Executes multi-operation hypothetical architecture simulations.
 *
 * CRITICAL SCIENTIFIC PRINCIPLES:
 * 1. ZERO SOURCE CODE MUTATION: Transformations occur purely on an isolated graph snapshot.
 * 2. FULL TOPOLOGY RECALCULATION: Metrics are computed from the complete transformed graph,
 *    never by linearly adding individual operation deltas.
 * 3. STRICT DETERMINISM: Identical project models and plans produce bitwise identical results.
 */
public class SimulationEngine {

    private final MetricsEngine metricsEngine;
    private final RefactoringConflictDetector conflictDetector;

    public SimulationEngine() {
        this(new MetricsEngine(), new RefactoringConflictDetector());
    }

    public SimulationEngine(MetricsEngine metricsEngine, RefactoringConflictDetector conflictDetector) {
        this.metricsEngine = Objects.requireNonNull(metricsEngine);
        this.conflictDetector = Objects.requireNonNull(conflictDetector);
    }

    public PlannerSimulationResult simulate(RefactoringPlan plan, ProjectModel model, DependencyGraph graph) {
        Objects.requireNonNull(plan, "RefactoringPlan cannot be null");
        Objects.requireNonNull(graph, "DependencyGraph cannot be null");

        String planHash = plan.getPlanHash();
        PlannerSimulationResult result = new PlannerSimulationResult(plan.getPlanId(), planHash);

        // 1. Conflict detection and precondition validation
        List<PlanConflict> conflicts = conflictDetector.detectConflicts(plan, graph, model);
        boolean hasBlockingErrors = conflicts.stream().anyMatch(c -> c.getSeverity() == ConflictSeverity.ERROR);

        if (hasBlockingErrors) {
            return PlannerSimulationResult.conflictBlocked(plan.getPlanId(), planHash, conflicts);
        }
        result.setConflicts(conflicts);

        // 2. Create isolated architecture snapshot
        ArchitectureGraphSnapshot snapshot = ArchitectureGraphSnapshot.from(model, graph);

        // 3. Sequentially apply hypothetical operations onto snapshot
        for (RefactoringOperation op : plan.getOperations()) {
            applyOperation(op, snapshot);
        }

        // 4. Verify working graph structural integrity
        List<String> integrityViolations = snapshot.validateWorkingGraphIntegrity();
        if (!integrityViolations.isEmpty()) {
            return PlannerSimulationResult.integrityViolation(plan.getPlanId(), planHash, integrityViolations);
        }

        // 5. Verify original baseline graph was completely protected
        if (!snapshot.verifyBaselineIntegrity()) {
            throw new IllegalStateException("CRITICAL BUG: Simulation mutated the original baseline dependency graph!");
        }

        // 6. Recalculate baseline and simulated metrics completely from graphs
        ProjectMetrics baselineMetrics = metricsEngine.analyze(model, graph);
        ProjectMetrics simulatedMetrics = metricsEngine.analyze(snapshot.getWorkingModel(), snapshot.getWorkingGraph());

        result.setBaselineMetrics(baselineMetrics);
        result.setSimulatedMetrics(simulatedMetrics);

        // 7. Cycle analysis
        result.setBaselineCycleCount(baselineMetrics.cycleCount);
        result.setSimulatedCycleCount(simulatedMetrics.cycleCount);
        result.setBaselineCycles(baselineMetrics.cycles);
        result.setSimulatedCycles(simulatedMetrics.cycles);

        // 8. Compute quantitative deltas
        populateMetricDeltas(result, baselineMetrics, simulatedMetrics);

        // 9. Synthesize architectural trade-offs
        synthesizeTradeoffs(result, baselineMetrics, simulatedMetrics);

        // 10. Record graph changes
        result.setAddedNodes(snapshot.getAddedNodes());
        result.setRemovedNodes(snapshot.getRemovedNodes());
        result.setAddedEdges(snapshot.getAddedEdges());
        result.setRemovedEdges(snapshot.getRemovedEdges());
        result.setAppliedOperations(plan.getOperations());

        // 11. Mark plan as simulated and success
        plan.markSimulated(planHash);
        result.setStatus(PlannerSimulationResult.Status.SUCCESS);
        result.setSuccess(true);

        return result;
    }

    private void applyOperation(RefactoringOperation op, ArchitectureGraphSnapshot snapshot) {
        String target = op.getTargetComponent();
        String secondary = op.getSecondaryComponent();
        String created = op.getNewComponent();

        switch (op.getType()) {
            case EXTRACT_INTERFACE -> {
                String iface = created != null ? created : (target + "Interface");
                snapshot.addInterfaceNode(iface);
                snapshot.addEdge(target, iface); // target implements iface

                boolean decouple = !"false".equalsIgnoreCase(op.getParameters().get("decoupleCallers"));
                if (decouple) {
                    List<String> callers = new ArrayList<>(snapshot.getWorkingGraph().incoming(target));
                    for (String caller : callers) {
                        if (!caller.equals(iface) && !caller.equals(target)) {
                            snapshot.redirectEdge(caller, target, iface);
                        }
                    }
                }
            }

            case BREAK_CYCLE -> {
                String iface = created != null ? created : ("I" + secondary);
                snapshot.addInterfaceNode(iface);
                snapshot.removeEdge(target, secondary);
                snapshot.addEdge(target, iface);    // caller uses interface
                snapshot.addEdge(secondary, iface); // provider implements interface
            }

            case REMOVE_DEPENDENCY -> {
                snapshot.removeEdge(target, secondary);
            }

            case REDIRECT_DEPENDENCY -> {
                String oldTarget = op.getParameters().getOrDefault("oldTarget", secondary);
                String newTarget = op.getParameters().getOrDefault("newTarget", created);
                snapshot.redirectEdge(target, oldTarget, newTarget);
            }

            case INTRODUCE_ABSTRACTION -> {
                String abstraction = created != null ? created : op.getParameters().get("abstractionName");
                snapshot.addInterfaceNode(abstraction);
                snapshot.addEdge(target, abstraction);

                String callersParam = op.getParameters().get("callers");
                if (callersParam != null && !callersParam.isBlank()) {
                    for (String caller : callersParam.split(",")) {
                        String c = caller.trim();
                        if (!c.isEmpty()) {
                            snapshot.redirectEdge(c, target, abstraction);
                        }
                    }
                }
            }
        }
    }

    private void populateMetricDeltas(PlannerSimulationResult result,
                                      ProjectMetrics base,
                                      ProjectMetrics sim) {
        result.addMetricDelta(new MetricDelta("Architecture Health",
                base.architectureHealthScore, sim.architectureHealthScore, Preference.HIGHER_IS_BETTER));

        result.addMetricDelta(new MetricDelta("Dependency Pressure Score",
                base.dependencyScore, sim.dependencyScore, Preference.LOWER_IS_BETTER));

        result.addMetricDelta(new MetricDelta("Circular Dependencies",
                base.cycleCount, sim.cycleCount, Preference.LOWER_IS_BETTER));

        result.addMetricDelta(new MetricDelta("Total Dependencies",
                base.totalDependencies, sim.totalDependencies, Preference.LOWER_IS_BETTER));

        result.addMetricDelta(new MetricDelta("Total Components",
                base.totalComponents, sim.totalComponents, Preference.NEUTRAL));

        result.addMetricDelta(new MetricDelta("Average Coupling",
                base.averageCoupling, sim.averageCoupling, Preference.LOWER_IS_BETTER));

        result.addMetricDelta(new MetricDelta("Average Fan-Out",
                base.averageFanOut, sim.averageFanOut, Preference.LOWER_IS_BETTER));

        result.addMetricDelta(new MetricDelta("Max Dependency Depth",
                base.maxDependencyDepth, sim.maxDependencyDepth, Preference.LOWER_IS_BETTER));
    }

    private void synthesizeTradeoffs(PlannerSimulationResult result,
                                     ProjectMetrics base,
                                     ProjectMetrics sim) {
        if (sim.cycleCount < base.cycleCount) {
            result.addTradeoff(String.format("Successfully eliminated %d dependency cycle(s) (%d -> %d).",
                    (base.cycleCount - sim.cycleCount), base.cycleCount, sim.cycleCount));
        }
        if (sim.architectureHealthScore > base.architectureHealthScore) {
            result.addTradeoff(String.format("Architecture health improves by +%.1f points (%.1f -> %.1f).",
                    (sim.architectureHealthScore - base.architectureHealthScore),
                    base.architectureHealthScore, sim.architectureHealthScore));
        } else if (sim.architectureHealthScore < base.architectureHealthScore) {
            result.addTradeoff(String.format("Notice: Architecture health decreases by -%.1f points.",
                    (base.architectureHealthScore - sim.architectureHealthScore)));
        }

        if (sim.totalComponents > base.totalComponents) {
            result.addTradeoff(String.format("System introduces %d new abstraction interface(s), increasing total types from %d to %d.",
                    (sim.totalComponents - base.totalComponents), base.totalComponents, sim.totalComponents));
        }

        if (sim.maxDependencyDepth > base.maxDependencyDepth) {
            result.addTradeoff(String.format("Abstraction layer increases maximum dependency depth from %d to %d.",
                    base.maxDependencyDepth, sim.maxDependencyDepth));
        }
    }
}
