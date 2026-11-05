package com.archanalyzer.planner;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.pipeline.ProjectPackager;
import com.archanalyzer.planner.PlanExecutionEngine.PlanExecutionResult;
import com.archanalyzer.planner.PostApplicationVerifier.VerificationReport;
import com.archanalyzer.planner.RefactoringConflictDetector.PlanConflict;
import com.archanalyzer.refactor.cycle.CycleBreakingStrategy;
import com.archanalyzer.refactor.cycle.CycleCandidateEdge;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.refactor.cycle.CycleRefactoringPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * High-level service facade for the Simulation-Guided Refactoring Planner (Novelty 2).
 * Coordinates candidate operation discovery, plan management, combined simulation,
 * conflict detection, verified application, and post-application metrics validation.
 */
public class SimulationPlannerService {

    private final SimulationEngine simulationEngine;
    private final PlanExecutionEngine executionEngine;
    private final PostApplicationVerifier verifier;
    private final CycleBreakingStrategy cycleBreakingStrategy;
    private final MetricsEngine metricsEngine;
    private final ProjectPackager packager;

    // Plan & simulation state cache (per planId)
    private final Map<String, RefactoringPlan> activePlans = new ConcurrentHashMap<>();
    private final Map<String, PlannerSimulationResult> cachedSimulations = new ConcurrentHashMap<>();

    public SimulationPlannerService() {
        this(new SimulationEngine(),
             new PlanExecutionEngine(),
             new PostApplicationVerifier(),
             new CycleBreakingStrategy(),
             new MetricsEngine(),
             new ProjectPackager());
    }

    public SimulationPlannerService(SimulationEngine simulationEngine,
                                    PlanExecutionEngine executionEngine,
                                    PostApplicationVerifier verifier,
                                    CycleBreakingStrategy cycleBreakingStrategy,
                                    MetricsEngine metricsEngine,
                                    ProjectPackager packager) {
        this.simulationEngine = Objects.requireNonNull(simulationEngine);
        this.executionEngine = Objects.requireNonNull(executionEngine);
        this.verifier = Objects.requireNonNull(verifier);
        this.cycleBreakingStrategy = Objects.requireNonNull(cycleBreakingStrategy);
        this.metricsEngine = Objects.requireNonNull(metricsEngine);
        this.packager = Objects.requireNonNull(packager);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Candidate Operation Discovery
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Automatically inspects the project architecture and proposes high-value
     * candidate refactoring operations (breaking cycles, extracting interfaces from high-coupling nodes).
     */
    public List<RefactoringOperation> discoverCandidateOperations(Path projectRoot,
                                                                   ProjectModel model,
                                                                   DependencyGraph graph) {
        List<RefactoringOperation> candidates = new ArrayList<>();
        if (graph == null || graph.vertexCount() == 0) return candidates;

        // 1. Discover cycle-breaking operations via Novelty 1 CycleBreakingStrategy
        if (projectRoot != null && model != null) {
            try {
                List<CycleRefactoringPlan> cyclePlans = cycleBreakingStrategy.analyzeCycles(projectRoot, graph, model);
                for (CycleRefactoringPlan cp : cyclePlans) {
                    CycleCandidateEdge edge = cp.getSelectedEdge();
                    if (edge != null && edge.isFeasible()) {
                        candidates.add(RefactoringOperation.breakCycle(
                                edge.getFromComponent(),
                                edge.getToComponent(),
                                cp.getExtractedInterfaceName()
                        ));
                    }
                }
            } catch (Exception e) {
                // fall back to pure graph SCC detection
                List<Set<String>> sccs = GraphAlgorithms.stronglyConnectedComponents(graph);
                for (Set<String> scc : sccs) {
                    if (scc.size() > 1) {
                        List<String> list = new ArrayList<>(scc);
                        candidates.add(RefactoringOperation.breakCycle(list.get(0), list.get(1), null));
                    }
                }
            }
        }

        // 2. Discover high-coupling classes for interface extraction
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);
        for (String v : graph.vertices()) {
            int fanIn = graph.fanIn(v);
            int fanOut = graph.fanOut(v);
            if (fanIn >= 2 && fanOut >= 1) {
                // Only propose interface extraction on actual classes with methods,
                // avoiding DTOs, data request containers, enums, or records with 0 methods.
                if (model != null) {
                    Optional<ProjectModel.ComponentNode> nodeOpt = model.getComponent(v);
                    if (nodeOpt.isPresent()) {
                        ProjectModel.ComponentNode node = nodeOpt.get();
                        if (node.getKind() != ProjectModel.ComponentNode.Kind.CLASS || node.getMethodCount() == 0) {
                            continue;
                        }
                    }
                }
                candidates.add(RefactoringOperation.extractInterface(v, null, true));
            }
        }

        return candidates;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Plan Management
    // ──────────────────────────────────────────────────────────────────────────

    public RefactoringPlan getOrCreatePlan(String planId, String defaultName) {
        if (planId != null && activePlans.containsKey(planId)) {
            return activePlans.get(planId);
        }
        String id = planId != null ? planId : "plan-" + UUID.randomUUID().toString().substring(0, 8);
        RefactoringPlan plan = new RefactoringPlan(id, defaultName != null ? defaultName : "Refactoring Plan");
        activePlans.put(id, plan);
        return plan;
    }

    public RefactoringPlan getPlan(String planId) {
        return activePlans.get(planId);
    }

    public void addOperation(String planId, RefactoringOperation op) {
        RefactoringPlan plan = getOrCreatePlan(planId, null);
        plan.addOperation(op);
        cachedSimulations.remove(planId);
    }

    public boolean removeOperation(String planId, String operationId) {
        RefactoringPlan plan = activePlans.get(planId);
        if (plan != null) {
            boolean removed = plan.removeOperation(operationId);
            if (removed) {
                cachedSimulations.remove(planId);
            }
            return removed;
        }
        return false;
    }

    public void reorderOperations(String planId, List<String> orderedIds) {
        RefactoringPlan plan = activePlans.get(planId);
        if (plan != null) {
            plan.reorderOperations(orderedIds);
            cachedSimulations.remove(planId);
        }
    }

    public void clearPlan(String planId) {
        RefactoringPlan plan = activePlans.get(planId);
        if (plan != null) {
            plan.clear();
            cachedSimulations.remove(planId);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Simulation
    // ──────────────────────────────────────────────────────────────────────────

    public PlannerSimulationResult simulatePlan(String planId, ProjectModel model, DependencyGraph graph) {
        RefactoringPlan plan = activePlans.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Refactoring plan not found: " + planId);
        }

        PlannerSimulationResult result = simulationEngine.simulate(plan, model, graph);
        cachedSimulations.put(planId, result);
        return result;
    }

    public PlannerSimulationResult getCachedSimulation(String planId) {
        return cachedSimulations.get(planId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Approval
    // ──────────────────────────────────────────────────────────────────────────

    public void approvePlan(String planId, String simulationHash) {
        RefactoringPlan plan = activePlans.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Refactoring plan not found: " + planId);
        }
        plan.approve(simulationHash);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Application & Verification
    // ──────────────────────────────────────────────────────────────────────────

    public VerificationReport applyPlan(String planId,
                                        Path projectRoot,
                                        PipelineResult baseline,
                                        RefactoringOptions options) throws IOException {
        RefactoringPlan plan = activePlans.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Refactoring plan not found: " + planId);
        }

        PlannerSimulationResult sim = cachedSimulations.get(planId);
        PlanExecutionResult execResult = executionEngine.executePlan(projectRoot, plan, sim, options);

        if (!execResult.success) {
            VerificationReport failReport = new VerificationReport();
            failReport.planId = planId;
            failReport.verified = false;
            failReport.buildPassed = false;
            failReport.compilerStatus = execResult.compilerStatus;
            failReport.testStatus = execResult.testStatus;
            failReport.discrepancies.addAll(execResult.compilerErrors);
            failReport.discrepancies.add("Application failed: " + execResult.message);
            return failReport;
        }

        // Re-analyze actual project after application
        ProjectModel postModel = parseProject(projectRoot);
        DependencyGraph postGraph = DependencyGraph.buildFrom(postModel);
        ProjectMetrics postMetrics = metricsEngine.analyze(postModel, postGraph);

        // Verify actual metrics against simulation predictions
        VerificationReport report = verifier.verify(
                planId,
                sim,
                postMetrics,
                true,
                execResult.compilerStatus,
                execResult.testStatus
        );

        // Update session pipeline baseline
        if (baseline != null) {
            baseline.model = postModel;
            baseline.graph = postGraph;
            baseline.metricsAfter = postMetrics;
            baseline.refactoredProjectRoot = projectRoot;

            // Package into downloadable zip
            Path workDir = projectRoot.getParent();
            if (workDir != null) {
                Path zipFile = workDir.resolve(projectRoot.getFileName().toString() + "-planner-refactored.zip");
                packager.packageToZip(projectRoot, zipFile);
                baseline.finalZipPath = zipFile;
                report.zipDownloadPath = zipFile.toAbsolutePath().toString();
            }
        }

        return report;
    }

    private ProjectModel parseProject(Path projectRoot) throws IOException {
        ProjectModel model = new ProjectModel(projectRoot.getFileName().toString());
        JavaParserAdapter parser = new JavaParserAdapter();
        try (Stream<Path> stream = Files.walk(projectRoot)) {
            List<Path> javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String rel = projectRoot.relativize(p).toString().replace('\\', '/');
                        return !rel.startsWith("target/") && !rel.contains("/target/") &&
                               !rel.startsWith("src/test/") && !rel.contains("/src/test/");
                    })
                    .toList();
            for (Path f : javaFiles) {
                try {
                    parser.parseInto(projectRoot, f, model);
                } catch (Exception e) {
                    model.addParseError(f.toString(), e.getMessage());
                }
            }
        }
        return model;
    }
}
