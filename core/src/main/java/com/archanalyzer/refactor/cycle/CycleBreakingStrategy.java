package com.archanalyzer.refactor.cycle;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.model.ProjectModel.ComponentNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Phase 1: Identifies architectural dependency cycles (SCCs), evaluates all candidate edges
 * within the SCC using a cost-based heuristic, selects the optimal dependency edge for
 * dependency inversion, and explains the chosen strategy.
 */
public class CycleBreakingStrategy {

    // Cost model weights
    private static final double WEIGHT_CALLERS = 2.0;
    private static final double WEIGHT_FIELDS = 1.5;
    private static final double WEIGHT_CONSTRUCTORS = 3.0;
    private static final double WEIGHT_METHODS = 1.0;

    /**
     * Analyzes all cyclic SCCs in the graph and returns a list of proposed refactoring plans.
     */
    public List<CycleRefactoringPlan> analyzeCycles(Path projectRoot, DependencyGraph graph, ProjectModel model) {
        List<CycleRefactoringPlan> plans = new ArrayList<>();
        List<Set<String>> sccs = GraphAlgorithms.stronglyConnectedComponents(graph);

        for (Set<String> scc : sccs) {
            if (scc.size() > 1) {
                CycleRefactoringPlan plan = planForScc(projectRoot, graph, model, scc);
                if (plan != null && plan.getSelectedEdge() != null) {
                    plans.add(plan);
                }
            }
        }
        return plans;
    }

    public List<CycleRefactoringPlan> planCycleBreaking(Path projectRoot, DependencyGraph graph, ProjectModel model) {
        return analyzeCycles(projectRoot, graph, model);
    }

    public CycleRefactoringPlan planCycleBreaking(Path projectRoot, ProjectModel model, DependencyGraph graph) {
        List<CycleRefactoringPlan> plans = analyzeCycles(projectRoot, graph, model);
        return plans.isEmpty() ? null : plans.get(0);
    }

    /**
     * Formulates an optimal cycle-breaking plan for a specific SCC.
     */
    public CycleRefactoringPlan planForScc(Path projectRoot, DependencyGraph graph, ProjectModel model, Set<String> scc) {
        List<CycleCandidateEdge> candidates = new ArrayList<>();

        // 1. Extract all internal edges within the SCC: u -> v where u, v in scc
        for (String u : scc) {
            for (String v : graph.outgoing(u)) {
                if (scc.contains(v) && !u.equals(v)) {
                    Path uFile = findComponentSourceFile(projectRoot, model, u);
                    Path vFile = findComponentSourceFile(projectRoot, model, v);
                    CycleCandidateEdge edge = new CycleCandidateEdge(u, v, uFile, vFile);
                    evaluateCandidateEdge(graph, model, scc, edge);
                    candidates.add(edge);
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // 2. Rank candidates: prioritize feasible edges that break the cycle, with minimal cost score
        candidates.sort((e1, e2) -> {
            if (e1.isFeasible() != e2.isFeasible()) {
                return e1.isFeasible() ? -1 : 1;
            }
            if (e1.getCycleReductionCount() != e2.getCycleReductionCount()) {
                return Integer.compare(e2.getCycleReductionCount(), e1.getCycleReductionCount());
            }
            return Double.compare(e1.getCostScore(), e2.getCostScore());
        });

        CycleCandidateEdge selected = null;
        for (CycleCandidateEdge c : candidates) {
            if (c.isFeasible() && c.getCycleReductionCount() > 0) {
                selected = c;
                break;
            }
        }

        if (selected == null && !candidates.isEmpty()) {
            // Fallback to lowest cost feasible edge even if multi-edge cycle requires subsequent iterations
            for (CycleCandidateEdge c : candidates) {
                if (c.isFeasible()) {
                    selected = c;
                    break;
                }
            }
        }

        CycleRefactoringPlan plan = new CycleRefactoringPlan(scc, selected, candidates);
        if (selected != null) {
            populatePlanDetails(projectRoot, model, selected, plan);
        }

        return plan;
    }

    private void evaluateCandidateEdge(DependencyGraph graph, ProjectModel model, Set<String> scc, CycleCandidateEdge edge) {
        String u = edge.getFromComponent();
        String v = edge.getToComponent();

        ComponentNode uNode = model.getComponent(u).orElse(null);
        ComponentNode vNode = model.getComponent(v).orElse(null);

        if (edge.getFromFile() == null || !Files.exists(edge.getFromFile())) {
            edge.setFeasible(false);
            edge.setRejectionReason("Source file for client " + u + " not found.");
            return;
        }
        if (edge.getToFile() == null || !Files.exists(edge.getToFile())) {
            edge.setFeasible(false);
            edge.setRejectionReason("Source file for provider " + v + " not found.");
            return;
        }

        if (vNode != null && vNode.getKind() == ComponentNode.Kind.INTERFACE) {
            edge.setFeasible(false);
            edge.setRejectionReason("Provider " + v + " is already an interface.");
            return;
        }

        String vSimple = vNode != null ? vNode.getSimpleName() : v.substring(v.lastIndexOf('.') + 1);
        Path potentialIface = edge.getToFile().getParent().resolve("I" + vSimple + ".java");
        if (Files.exists(potentialIface)) {
            edge.setFeasible(false);
            edge.setRejectionReason("Target interface I" + vSimple + " already exists in project.");
            return;
        }

        // Count callers in project
        int callers = graph.fanIn(v);
        edge.setCallerCount(callers);

        // Count references in source file
        try {
            String uSource = Files.readString(edge.getFromFile());

            int fields = countOccurrences(uSource, vSimple + " ");
            int ctors = countOccurrences(uSource, "new " + vSimple + "(");
            int methodParams = countOccurrences(uSource, "(" + vSimple + " ") + countOccurrences(uSource, ", " + vSimple + " ");

            edge.setFieldCount(fields);
            edge.setConstructorUsageCount(ctors);
            edge.setMethodParameterCount(methodParams);

            double cost = (callers * WEIGHT_CALLERS)
                    + (fields * WEIGHT_FIELDS)
                    + (ctors * WEIGHT_CONSTRUCTORS)
                    + (methodParams * WEIGHT_METHODS)
                    + (vNode != null ? vNode.getMethodCount() * 0.2 : 1.0);

            edge.setCostScore(cost);
        } catch (IOException e) {
            edge.setCostScore(10.0);
        }

        // Simulate cycle reduction: copy graph, remove (u, v), check if cycle count decreases
        DependencyGraph copy = graph.copy();
        copy.removeEdge(u, v);
        List<Set<String>> postSccs = GraphAlgorithms.stronglyConnectedComponents(copy);
        int postCycles = (int) postSccs.stream().filter(s -> s.size() > 1).count();
        int initialCycles = (int) GraphAlgorithms.stronglyConnectedComponents(graph).stream().filter(s -> s.size() > 1).count();

        int cycleReduction = Math.max(0, initialCycles - postCycles);
        edge.setCycleReductionCount(cycleReduction);

        String rationale = String.format("Inverting %s -> %s reduces SCC cycle count by %d. Estimated refactoring cost score: %.1f (callers=%d, fields=%d, ctors=%d).",
                u, v, cycleReduction, edge.getCostScore(), callers, edge.getFieldCount(), edge.getConstructorUsageCount());
        edge.setRationale(rationale);
    }

    private void populatePlanDetails(Path projectRoot, ProjectModel model, CycleCandidateEdge selected, CycleRefactoringPlan plan) {
        String provider = selected.getToComponent();
        ComponentNode pNode = model.getComponent(provider).orElse(null);
        String pSimple = pNode != null ? pNode.getSimpleName() : provider.substring(provider.lastIndexOf('.') + 1);
        String pPkg = pNode != null ? pNode.getPackageName() : "";

        String ifaceName = "I" + pSimple;
        plan.setExtractedInterfaceName(ifaceName);
        plan.setInterfacePackage(pPkg);

        if (selected.getToFile() != null) {
            plan.setInterfaceFilePath(selected.getToFile().getParent().resolve(ifaceName + ".java"));
        }

        // Affected files: provider + client + new interface
        Set<Path> affected = new LinkedHashSet<>();
        if (selected.getToFile() != null) affected.add(selected.getToFile());
        if (selected.getFromFile() != null) affected.add(selected.getFromFile());
        plan.getAffectedFiles().addAll(affected);

        // Potential risks
        if (selected.getConstructorUsageCount() > 0) {
            plan.getPotentialRisks().add("Client directly calls 'new " + pSimple + "()'; requires constructor parameter injection or decoupling to avoid recursion.");
        }
        if (selected.getCallerCount() > 1) {
            plan.getPotentialRisks().add("Provider has " + selected.getCallerCount() + " incoming callers across project; non-cyclic callers may remain concrete or be migrated.");
        }

        StringBuilder explanation = new StringBuilder();
        explanation.append("Cycle Analysis & Inversion Strategy:\n");
        explanation.append("1. Cyclic SCC Detected with ").append(plan.getSccMembers().size()).append(" components: ").append(plan.getSccMembers()).append("\n");
        explanation.append("2. Selected Dependency Edge for Inversion: ").append(selected.getFromComponent()).append(" -> ").append(selected.getToComponent()).append("\n");
        explanation.append("3. Selection Justification: ").append(selected.getRationale()).append("\n");
        explanation.append("4. Planned Interface: ").append(ifaceName).append(" in package '").append(pPkg).append("'\n");
        explanation.append("5. Expected Impact: Eliminates direct cyclic dependency, reducing cyclic SCCs by ").append(selected.getCycleReductionCount()).append(".\n");

        plan.setStrategyExplanation(explanation.toString());
    }

    private Path findComponentSourceFile(Path projectRoot, ProjectModel model, String componentId) {
        ComponentNode node = model.getComponent(componentId).orElse(null);
        if (node != null && node.getDeclaredInFile() != null) {
            Path p = Path.of(node.getDeclaredInFile());
            return p.isAbsolute() ? p : projectRoot.resolve(p);
        }
        // Fallback search by simple name
        String simpleName = componentId.substring(componentId.lastIndexOf('.') + 1) + ".java";
        try (var walk = Files.walk(projectRoot)) {
            Optional<Path> found = walk.filter(p -> p.getFileName() != null && p.getFileName().toString().equals(simpleName)).findFirst();
            if (found.isPresent()) return found.get();
        } catch (IOException ignored) {}
        return null;
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
