package com.archanalyzer.planner;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;

import java.util.*;

/**
 * Statically analyzes a RefactoringPlan against the dependency graph to detect
 * duplicate operations, contradictory mutations, missing targets, non-existent edges,
 * and invalid operation orderings.
 */
public class RefactoringConflictDetector {

    public enum ConflictSeverity {
        ERROR,
        WARNING
    }

    public enum ConflictCategory {
        DUPLICATE_OPERATION,
        CONTRADICTORY_TRANSFORMATION,
        MISSING_TARGET_COMPONENT,
        MISSING_DEPENDENCY_EDGE,
        INVALID_ORDERING,
        EDGE_NOT_IN_CYCLE,
        DANGLING_REFERENCE
    }

    public static class PlanConflict {
        private final ConflictSeverity severity;
        private final ConflictCategory category;
        private final List<String> operationIds = new ArrayList<>();
        private final String message;
        private final String remedy;

        public PlanConflict(ConflictSeverity severity,
                            ConflictCategory category,
                            List<String> operationIds,
                            String message,
                            String remedy) {
            this.severity = severity;
            this.category = category;
            if (operationIds != null) this.operationIds.addAll(operationIds);
            this.message = message;
            this.remedy = remedy;
        }

        public ConflictSeverity getSeverity() { return severity; }
        public ConflictCategory getCategory() { return category; }
        public List<String> getOperationIds() { return Collections.unmodifiableList(operationIds); }
        public String getMessage() { return message; }
        public String getRemedy() { return remedy; }

        @Override
        public String toString() {
            return String.format("[%s][%s] Ops %s: %s (Remedy: %s)",
                    severity, category, operationIds, message, remedy);
        }
    }

    public List<PlanConflict> detectConflicts(RefactoringPlan plan, DependencyGraph graph, ProjectModel model) {
        List<PlanConflict> conflicts = new ArrayList<>();
        if (plan == null || plan.isEmpty() || graph == null) {
            return conflicts;
        }

        List<RefactoringOperation> ops = plan.getOperations();

        // 1. Detect duplicate operations
        Map<String, String> seenSignatures = new HashMap<>();
        for (RefactoringOperation op : ops) {
            String sig = op.signature();
            if (seenSignatures.containsKey(sig)) {
                conflicts.add(new PlanConflict(
                        ConflictSeverity.ERROR,
                        ConflictCategory.DUPLICATE_OPERATION,
                        List.of(seenSignatures.get(sig), op.getId()),
                        String.format("Operation '%s' is a duplicate of operation '%s'.", op.getId(), seenSignatures.get(sig)),
                        "Remove the duplicate operation from the plan."
                ));
            } else {
                seenSignatures.put(sig, op.getId());
            }
        }

        // 2. Track simulated components and edges as they evolve across the plan
        Set<String> knownComponents = new HashSet<>(graph.vertices());
        Map<String, Set<String>> simulatedEdges = new HashMap<>();
        for (String v : graph.vertices()) {
            simulatedEdges.put(v, new HashSet<>(graph.outgoing(v)));
        }

        // Precompute SCCs for cycle checks
        List<Set<String>> sccs = GraphAlgorithms.stronglyConnectedComponents(graph);
        Set<String> cyclicComponents = new HashSet<>();
        for (Set<String> scc : sccs) {
            if (scc.size() > 1) cyclicComponents.addAll(scc);
        }

        Set<String> removedEdges = new HashSet<>();
        Map<String, String> edgeCreatedByOp = new HashMap<>();
        Map<String, String> edgeRemovedByOp = new HashMap<>();
        Map<String, String> componentCreatedByOp = new HashMap<>();

        for (int i = 0; i < ops.size(); i++) {
            RefactoringOperation op = ops.get(i);
            String target = op.getTargetComponent();
            String secondary = op.getSecondaryComponent();
            String createdComp = op.getNewComponent();

            // A. Check target existence
            if (target != null && !knownComponents.contains(target)) {
                conflicts.add(new PlanConflict(
                        ConflictSeverity.ERROR,
                        ConflictCategory.MISSING_TARGET_COMPONENT,
                        List.of(op.getId()),
                        String.format("Target component '%s' for operation '%s' does not exist in the architecture.", target, op.getId()),
                        "Ensure the target class name is spelled correctly and exists in the uploaded project."
                ));
            }

            // B. Operation-specific validations
            switch (op.getType()) {
                case EXTRACT_INTERFACE -> {
                    if (createdComp != null) {
                        if (knownComponents.contains(createdComp)) {
                            conflicts.add(new PlanConflict(
                                    ConflictSeverity.WARNING,
                                    ConflictCategory.CONTRADICTORY_TRANSFORMATION,
                                    List.of(op.getId()),
                                    String.format("Interface name '%s' already exists in the architecture.", createdComp),
                                    "Choose a distinct interface name (e.g., I" + createdComp + ") to avoid collisions."
                            ));
                        }
                        knownComponents.add(createdComp);
                        componentCreatedByOp.put(createdComp, op.getId());
                    }
                }

                case BREAK_CYCLE -> {
                    String edgeKey = target + "->" + secondary;
                    boolean edgeExists = simulatedEdges.getOrDefault(target, Set.of()).contains(secondary);

                    if (edgeRemovedByOp.containsKey(edgeKey)) {
                        conflicts.add(new PlanConflict(
                                ConflictSeverity.ERROR,
                                ConflictCategory.CONTRADICTORY_TRANSFORMATION,
                                List.of(edgeRemovedByOp.get(edgeKey), op.getId()),
                                String.format("Operation '%s' attempts to break edge '%s', which was already removed by operation '%s'.",
                                        op.getId(), edgeKey, edgeRemovedByOp.get(edgeKey)),
                                "Remove the redundant break cycle operation or remove the preceding removal operation."
                        ));
                    } else if (!edgeExists) {
                        conflicts.add(new PlanConflict(
                                ConflictSeverity.ERROR,
                                ConflictCategory.MISSING_DEPENDENCY_EDGE,
                                List.of(op.getId()),
                                String.format("Cannot break cycle on edge '%s': edge does not exist in the dependency graph.", edgeKey),
                                "Verify that the dependency edge exists before attempting to invert it."
                        ));
                    } else if (!cyclicComponents.contains(target) || !cyclicComponents.contains(secondary)) {
                        conflicts.add(new PlanConflict(
                                ConflictSeverity.WARNING,
                                ConflictCategory.EDGE_NOT_IN_CYCLE,
                                List.of(op.getId()),
                                String.format("Edge '%s' does not participate in any multi-node Strongly Connected Component (cycle).", edgeKey),
                                "This operation will decouple the edge, but will not reduce the cycle count."
                        ));
                    }

                    if (createdComp != null) {
                        knownComponents.add(createdComp);
                        componentCreatedByOp.put(createdComp, op.getId());
                    }
                    edgeRemovedByOp.put(edgeKey, op.getId());
                    simulatedEdges.computeIfAbsent(target, k -> new HashSet<>()).remove(secondary);
                }

                case REMOVE_DEPENDENCY -> {
                    String edgeKey = target + "->" + secondary;
                    boolean edgeExists = simulatedEdges.getOrDefault(target, Set.of()).contains(secondary);

                    if (edgeRemovedByOp.containsKey(edgeKey)) {
                        conflicts.add(new PlanConflict(
                                ConflictSeverity.ERROR,
                                ConflictCategory.DUPLICATE_OPERATION,
                                List.of(edgeRemovedByOp.get(edgeKey), op.getId()),
                                String.format("Edge '%s' is already scheduled for removal by operation '%s'.",
                                        edgeKey, edgeRemovedByOp.get(edgeKey)),
                                "Remove the duplicate dependency removal."
                        ));
                    } else if (!edgeExists) {
                        conflicts.add(new PlanConflict(
                                ConflictSeverity.ERROR,
                                ConflictCategory.MISSING_DEPENDENCY_EDGE,
                                List.of(op.getId()),
                                String.format("Cannot remove edge '%s': edge does not exist in the dependency graph.", edgeKey),
                                "Select an existing edge from the dependency graph."
                        ));
                    }
                    edgeRemovedByOp.put(edgeKey, op.getId());
                    simulatedEdges.computeIfAbsent(target, k -> new HashSet<>()).remove(secondary);
                }

                case REDIRECT_DEPENDENCY -> {
                    String oldTarget = op.getParameters().get("oldTarget");
                    String newTarget = op.getParameters().get("newTarget");
                    if (oldTarget == null) oldTarget = secondary;
                    if (newTarget == null) newTarget = createdComp;

                    String oldEdgeKey = target + "->" + oldTarget;
                    boolean oldEdgeExists = simulatedEdges.getOrDefault(target, Set.of()).contains(oldTarget);

                    if (!oldEdgeExists) {
                        conflicts.add(new PlanConflict(
                                ConflictSeverity.ERROR,
                                ConflictCategory.MISSING_DEPENDENCY_EDGE,
                                List.of(op.getId()),
                                String.format("Cannot redirect edge '%s': edge does not exist in the simulated architecture.", oldEdgeKey),
                                "Ensure the source edge exists prior to redirection."
                        ));
                    }

                    if (newTarget != null && !knownComponents.contains(newTarget)) {
                        conflicts.add(new PlanConflict(
                                ConflictSeverity.ERROR,
                                ConflictCategory.INVALID_ORDERING,
                                List.of(op.getId()),
                                String.format("Redirect target '%s' does not exist yet. It must be created by a preceding operation.", newTarget),
                                "Reorder the plan so that the abstraction is created before dependencies are redirected to it."
                        ));
                    }

                    simulatedEdges.computeIfAbsent(target, k -> new HashSet<>()).remove(oldTarget);
                    simulatedEdges.computeIfAbsent(target, k -> new HashSet<>()).add(newTarget);
                }

                case INTRODUCE_ABSTRACTION -> {
                    if (createdComp != null) {
                        knownComponents.add(createdComp);
                        componentCreatedByOp.put(createdComp, op.getId());
                    }
                }
            }
        }

        return conflicts;
    }
}
