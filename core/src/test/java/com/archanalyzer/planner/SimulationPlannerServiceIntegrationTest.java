package com.archanalyzer.planner;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.pipeline.ProjectDiscovery;
import com.archanalyzer.pipeline.ProjectPackager;
import com.archanalyzer.planner.PlanExecutionEngine.PlanExecutionResult;
import com.archanalyzer.planner.PostApplicationVerifier.VerificationReport;
import com.archanalyzer.planner.RefactoringConflictDetector.ConflictCategory;
import com.archanalyzer.planner.RefactoringConflictDetector.PlanConflict;
import com.archanalyzer.refactor.cycle.*;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.validate.BuildValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for Novelty 2 (Simulation-Guided Refactoring Planner).
 * Uses synthetic architectures in isolated @TempDir workspaces.
 * sample-demo-project is NEVER accessed or touched.
 */
public class SimulationPlannerServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private SimulationPlannerService service;
    private SimulationEngine engine;
    private RefactoringConflictDetector conflictDetector;
    private MetricsEngine metricsEngine;

    @BeforeEach
    public void setUp() {
        this.metricsEngine = new MetricsEngine();
        this.conflictDetector = new RefactoringConflictDetector();
        this.engine = new SimulationEngine(metricsEngine, conflictDetector);
        this.service = new SimulationPlannerService(
                engine,
                new PlanExecutionEngine(),
                new PostApplicationVerifier(),
                new CycleBreakingStrategy(),
                metricsEngine,
                new ProjectPackager()
        );
    }

    private Path createJavaFile(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private PipelineResult buildSyntheticPipelineResult(Path proj) throws Exception {
        ProjectDiscovery discovery = new ProjectDiscovery(true);
        ProjectModel model = discovery.discoverAndParse(proj, proj.getFileName().toString());
        DependencyGraph graph = DependencyGraph.buildFrom(model);
        ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        PipelineResult res = new PipelineResult();
        res.projectRoot = proj;
        res.model = model;
        res.graph = graph;
        res.metricsBefore = metrics;
        return res;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Unit Tests: Operations, Lifecycle, Hashing, Snapshots, Conflicts
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Unit: RefactoringOperation creation and signature determinism")
    public void testOperationValidationAndHashing() {
        RefactoringOperation op1 = RefactoringOperation.extractInterface("com.pkg.Order", "com.pkg.IOrder", true);
        RefactoringOperation op2 = RefactoringOperation.extractInterface("com.pkg.Order", "com.pkg.IOrder", true);
        assertEquals(op1.signature(), op2.signature());

        RefactoringOperation op3 = RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "com.pkg.IB");
        assertNotEquals(op1.signature(), op3.signature());
    }

    @Test
    @DisplayName("Unit: RefactoringPlan lifecycle transitions and staleness detection")
    public void testPlanLifecycleAndStaleness() {
        RefactoringPlan plan = new RefactoringPlan("Test Plan");
        assertEquals(PlanLifecycle.DRAFT, plan.getLifecycle());
        assertTrue(plan.isSimulationStale());

        RefactoringOperation op = RefactoringOperation.removeDependency("com.pkg.A", "com.pkg.B", "Decouple");
        plan.addOperation(op);
        String hash1 = plan.getPlanHash();
        assertNotNull(hash1);

        plan.markSimulated(hash1);
        assertEquals(PlanLifecycle.SIMULATED, plan.getLifecycle());
        assertFalse(plan.isSimulationStale());

        // Modifying plan invalidates simulation and resets to DRAFT
        plan.addOperation(RefactoringOperation.extractInterface("com.pkg.C", "com.pkg.IC", false));
        assertEquals(PlanLifecycle.DRAFT, plan.getLifecycle());
        assertTrue(plan.isSimulationStale());
        assertNotEquals(hash1, plan.getPlanHash());

        // Cannot approve stale plan
        assertThrows(IllegalStateException.class, () -> plan.approve(hash1));
    }

    @Test
    @DisplayName("Unit: ArchitectureGraphSnapshot guarantees zero mutation of original graph")
    public void testGraphSnapshotIsolation() {
        DependencyGraph orig = new DependencyGraph();
        orig.addEdge("A", "B");
        orig.addEdge("B", "C");
        int origV = orig.vertexCount();
        int origE = orig.edgeCount();

        ProjectModel model = new ProjectModel("Test");
        ArchitectureGraphSnapshot snap = ArchitectureGraphSnapshot.from(model, orig);

        snap.addInterfaceNode("IB");
        snap.removeEdge("A", "B");
        snap.addEdge("A", "IB");
        snap.removeNode("C");

        // Verify working graph changed
        assertEquals(origV, snap.getWorkingGraph().vertexCount());
        assertTrue(snap.getWorkingGraph().containsVertex("IB"));
        assertFalse(snap.getWorkingGraph().containsVertex("C"));

        // Verify original baseline is bitwise unchanged
        assertTrue(snap.verifyBaselineIntegrity());
        assertEquals(origV, orig.vertexCount());
        assertEquals(origE, orig.edgeCount());
        assertTrue(orig.containsVertex("C"));
        assertTrue(orig.outgoing("A").contains("B"));
    }

    @Test
    @DisplayName("Unit: ConflictDetector flags duplicate operations")
    public void testConflictDetectorDuplicates() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge("com.pkg.A", "com.pkg.B");
        RefactoringPlan plan = new RefactoringPlan("Dup Plan");
        RefactoringOperation op1 = new RefactoringOperation("op-1", OperationType.REMOVE_DEPENDENCY, "com.pkg.A", "com.pkg.B", null, "Reason 1");
        RefactoringOperation op2 = new RefactoringOperation("op-2", OperationType.REMOVE_DEPENDENCY, "com.pkg.A", "com.pkg.B", null, "Reason 2");
        plan.addOperation(op1);
        plan.addOperation(op2);

        List<PlanConflict> conflicts = conflictDetector.detectConflicts(plan, graph, null);
        assertFalse(conflicts.isEmpty(), "Conflicts list should not be empty for duplicate operations");
        assertTrue(conflicts.stream().anyMatch(c -> c.getCategory() == ConflictCategory.DUPLICATE_OPERATION));
    }

    @Test
    @DisplayName("Unit: ConflictDetector flags contradictory operations on removed edge")
    public void testConflictDetectorContradictory() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge("com.pkg.A", "com.pkg.B");
        graph.addEdge("com.pkg.B", "com.pkg.A");

        RefactoringPlan plan = new RefactoringPlan("Contradictory Plan");
        plan.addOperation(RefactoringOperation.removeDependency("com.pkg.A", "com.pkg.B", "Drop"));
        plan.addOperation(RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "IB"));

        List<PlanConflict> conflicts = conflictDetector.detectConflicts(plan, graph, null);
        assertTrue(conflicts.stream().anyMatch(c -> c.getCategory() == ConflictCategory.CONTRADICTORY_TRANSFORMATION));
    }

    @Test
    @DisplayName("Unit: ConflictDetector flags missing edges and ordering dependencies")
    public void testConflictDetectorMissingEdgeAndOrdering() {
        DependencyGraph graph = new DependencyGraph();
        graph.addVertex("com.pkg.Source");

        RefactoringPlan plan = new RefactoringPlan("Order Plan");
        plan.addOperation(RefactoringOperation.redirectDependency("com.pkg.Source", "com.pkg.MissingTarget", "com.pkg.FutureAbstraction"));

        List<PlanConflict> conflicts = conflictDetector.detectConflicts(plan, graph, null);
        assertTrue(conflicts.stream().anyMatch(c -> c.getCategory() == ConflictCategory.MISSING_DEPENDENCY_EDGE));
        assertTrue(conflicts.stream().anyMatch(c -> c.getCategory() == ConflictCategory.INVALID_ORDERING));
    }

    @Test
    @DisplayName("Unit: Simulation determinism yields identical results for identical input")
    public void testSimulationDeterminism() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge("com.pkg.Alpha", "com.pkg.Beta");
        graph.addEdge("com.pkg.Beta", "com.pkg.Gamma");

        RefactoringPlan plan = new RefactoringPlan("Deterministic Plan");
        plan.addOperation(RefactoringOperation.extractInterface("com.pkg.Beta", "com.pkg.IBeta", true));

        PlannerSimulationResult res1 = engine.simulate(plan, null, graph);
        PlannerSimulationResult res2 = engine.simulate(plan, null, graph);

        assertEquals(res1.getPlanHash(), res2.getPlanHash());
        assertEquals(res1.getSimulatedCycleCount(), res2.getSimulatedCycleCount());
        assertEquals(res1.getMetricDeltas().get("Total Dependencies").getSimulatedValue(),
                     res2.getMetricDeltas().get("Total Dependencies").getSimulatedValue());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Integration Tests: 14 Required Scenarios
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scenario 1: Single operation simulation successfully projects metrics")
    public void testScenario1_SingleOperationSimulation() throws Exception {
        Path proj = tempDir.resolve("test-scenario-1");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A { private B b; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/B.java", "package com.pkg;\npublic class B { private A a; }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s1";
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "com.pkg.IB"));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        assertTrue(sim.isSuccess());
        assertEquals(1, sim.getBaselineCycleCount());
        assertEquals(0, sim.getSimulatedCycleCount());
        assertTrue(sim.getMetricDeltas().containsKey("Circular Dependencies"));
        assertTrue(sim.getMetricDeltas().get("Circular Dependencies").isImprovement());
    }

    @Test
    @DisplayName("Scenario 2: Two compatible operations combined into single simulation")
    public void testScenario2_TwoCompatibleOperations() throws Exception {
        Path proj = tempDir.resolve("test-scenario-2");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A { private B b; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/B.java", "package com.pkg;\npublic class B { private A a; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/X.java", "package com.pkg;\npublic class X { private Y y; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/Y.java", "package com.pkg;\npublic class Y { private X x; }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s2";
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "com.pkg.IB"));
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.X", "com.pkg.Y", "com.pkg.IY"));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        assertTrue(sim.isSuccess());
        assertEquals(2, sim.getBaselineCycleCount());
        assertEquals(0, sim.getSimulatedCycleCount());
        assertEquals(2, sim.getAppliedOperations().size());
    }

    @Test
    @DisplayName("Scenario 3: Three compatible operations simulate together")
    public void testScenario3_ThreeCompatibleOperations() throws Exception {
        Path proj = tempDir.resolve("test-scenario-3");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A { private B b; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/B.java", "package com.pkg;\npublic class B { private A a; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/C.java", "package com.pkg;\npublic class C { private D d; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/D.java", "package com.pkg;\npublic class D { private C c; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/Standalone.java", "package com.pkg;\npublic class Standalone {}\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s3";
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "com.pkg.IB"));
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.C", "com.pkg.D", "com.pkg.ID"));
        service.addOperation(planId, RefactoringOperation.extractInterface("com.pkg.Standalone", "com.pkg.IStandalone", false));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        assertTrue(sim.isSuccess());
        assertEquals(2, sim.getBaselineCycleCount());
        assertEquals(0, sim.getSimulatedCycleCount());
        assertEquals(3, sim.getAppliedOperations().size());
    }

    @Test
    @DisplayName("Scenario 4: Two operations affecting overlapping graph regions")
    public void testScenario4_OverlappingGraphRegions() throws Exception {
        Path proj = tempDir.resolve("test-scenario-4");
        // Hub class H called by C1, C2 and calling S
        createJavaFile(proj, "src/main/java/com/pkg/Hub.java", "package com.pkg;\npublic class Hub { private Sub s; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/Sub.java", "package com.pkg;\npublic class Sub {}\n");
        createJavaFile(proj, "src/main/java/com/pkg/C1.java", "package com.pkg;\npublic class C1 { private Hub h; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/C2.java", "package com.pkg;\npublic class C2 { private Hub h; }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s4";
        service.addOperation(planId, RefactoringOperation.extractInterface("com.pkg.Hub", "com.pkg.IHub", true));
        service.addOperation(planId, RefactoringOperation.removeDependency("com.pkg.Hub", "com.pkg.Sub", "Decouple sub"));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        assertTrue(sim.isSuccess());
        assertTrue(sim.getAddedNodes().contains("com.pkg.IHub"));
        assertTrue(sim.getRemovedEdges().contains("com.pkg.Hub -> com.pkg.Sub"));
    }

    @Test
    @DisplayName("Scenario 5: Conflicting operations correctly block simulation")
    public void testScenario5_ConflictingOperationsBlocked() throws Exception {
        Path proj = tempDir.resolve("test-scenario-5");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A { private B b; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/B.java", "package com.pkg;\npublic class B { private A a; }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s5";
        service.addOperation(planId, RefactoringOperation.removeDependency("com.pkg.A", "com.pkg.B", "Remove edge"));
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "com.pkg.IB"));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        assertFalse(sim.isSuccess());
        assertEquals(PlannerSimulationResult.Status.CONFLICT_BLOCKED, sim.getStatus());
        assertFalse(sim.getConflicts().isEmpty());
    }

    @Test
    @DisplayName("Scenario 6: Duplicate operation selection detected and blocked")
    public void testScenario6_DuplicateOperationBlocked() throws Exception {
        Path proj = tempDir.resolve("test-scenario-6");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A { private B b; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/B.java", "package com.pkg;\npublic class B {}\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s6";
        RefactoringPlan plan = service.getOrCreatePlan(planId, "Plan");
        plan.getOperations(); // empty
        RefactoringOperation op1 = new RefactoringOperation("op-1", OperationType.REMOVE_DEPENDENCY, "com.pkg.A", "com.pkg.B", null, "Remove A->B");
        RefactoringOperation op2 = new RefactoringOperation("op-2", OperationType.REMOVE_DEPENDENCY, "com.pkg.A", "com.pkg.B", null, "Remove A->B");
        plan.addOperation(op1);
        plan.addOperation(op2);

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        assertFalse(sim.isSuccess());
        assertEquals(PlannerSimulationResult.Status.CONFLICT_BLOCKED, sim.getStatus());
    }

    @Test
    @DisplayName("Scenario 7: Operation ordering dependency detected")
    public void testScenario7_OperationOrderingDependency() throws Exception {
        Path proj = tempDir.resolve("test-scenario-7");
        createJavaFile(proj, "src/main/java/com/pkg/Client.java", "package com.pkg;\npublic class Client { private Target t; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/Target.java", "package com.pkg;\npublic class Target {}\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s7";
        RefactoringPlan plan = service.getOrCreatePlan(planId, "Order Plan");
        // Redirect to abstraction before abstraction is introduced
        plan.addOperation(RefactoringOperation.redirectDependency("com.pkg.Client", "com.pkg.Target", "com.pkg.NonExistentService"));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        assertFalse(sim.isSuccess());
        assertEquals(PlannerSimulationResult.Status.CONFLICT_BLOCKED, sim.getStatus());
    }

    @Test
    @DisplayName("Scenario 8: Remove operation and re-simulate updates results")
    public void testScenario8_RemoveOperationAndResimulate() throws Exception {
        Path proj = tempDir.resolve("test-scenario-8");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A { private B b; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/B.java", "package com.pkg;\npublic class B { private A a; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/C.java", "package com.pkg;\npublic class C { private D d; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/D.java", "package com.pkg;\npublic class D { private C c; }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s8";
        RefactoringOperation op1 = RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "com.pkg.IB");
        RefactoringOperation op2 = RefactoringOperation.breakCycle("com.pkg.C", "com.pkg.D", "com.pkg.ID");
        service.addOperation(planId, op1);
        service.addOperation(planId, op2);

        PlannerSimulationResult sim1 = service.simulatePlan(planId, pr.model, pr.graph);
        assertEquals(0, sim1.getSimulatedCycleCount());

        // Remove op2: only 1 cycle broken
        service.removeOperation(planId, op2.getId());
        PlannerSimulationResult sim2 = service.simulatePlan(planId, pr.model, pr.graph);
        assertEquals(1, sim2.getSimulatedCycleCount());
    }

    @Test
    @DisplayName("Scenario 9: Modifying plan after simulation invalidates simulation state")
    public void testScenario9_ModifyPlanAfterSimulationInvalidates() throws Exception {
        Path proj = tempDir.resolve("test-scenario-9");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A { private B b; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/B.java", "package com.pkg;\npublic class B { private A a; }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s9";
        RefactoringPlan plan = service.getOrCreatePlan(planId, "Plan 9");
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "com.pkg.IB"));

        service.simulatePlan(planId, pr.model, pr.graph);
        assertFalse(plan.isSimulationStale());
        assertEquals(PlanLifecycle.SIMULATED, plan.getLifecycle());

        // Add another operation -> stale
        service.addOperation(planId, RefactoringOperation.extractInterface("com.pkg.A", "com.pkg.IA", false));
        assertTrue(plan.isSimulationStale());
        assertEquals(PlanLifecycle.DRAFT, plan.getLifecycle());
    }

    @Test
    @DisplayName("Scenario 10: Attempt to apply stale simulation is strictly rejected")
    public void testScenario10_ApplyStaleSimulationRejected() throws Exception {
        Path proj = tempDir.resolve("test-scenario-10");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A { private B b; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/B.java", "package com.pkg;\npublic class B { private A a; }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s10";
        RefactoringPlan plan = service.getOrCreatePlan(planId, "Plan 10");
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.A", "com.pkg.B", "com.pkg.IB"));

        service.simulatePlan(planId, pr.model, pr.graph);
        // User modifies plan without re-simulating
        service.addOperation(planId, RefactoringOperation.removeDependency("com.pkg.B", "com.pkg.A", "Break reverse"));

        VerificationReport report = service.applyPlan(planId, proj, pr, new RefactoringOptions());
        assertFalse(report.verified);
        assertTrue(report.discrepancies.stream().anyMatch(d -> d.contains("not received explicit approval") || d.contains("STALE")));
    }

    @Test
    @DisplayName("Scenario 11: Simulation failure handles invalid target gracefully")
    public void testScenario11_SimulationFailureHandling() throws Exception {
        Path proj = tempDir.resolve("test-scenario-11");
        createJavaFile(proj, "src/main/java/com/pkg/A.java", "package com.pkg;\npublic class A {}\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s11";
        service.addOperation(planId, RefactoringOperation.extractInterface("com.pkg.NonExistent", "com.pkg.INonExistent", false));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        assertFalse(sim.isSuccess());
        assertEquals(PlannerSimulationResult.Status.CONFLICT_BLOCKED, sim.getStatus());
    }

    @Test
    @DisplayName("Scenario 12: Application compilation failure triggers atomic rollback")
    public void testScenario12_ApplicationFailureAndRollback() throws Exception {
        Path proj = tempDir.resolve("test-scenario-12");
        Path fileA = createJavaFile(proj, "src/main/java/com/pkg/ClassX.java",
                "package com.pkg;\npublic class ClassX { private ClassY y; public void doX() {} }\n");
        Path fileB = createJavaFile(proj, "src/main/java/com/pkg/ClassY.java",
                "package com.pkg;\npublic class ClassY { private ClassX x; public void doY() {} }\n");

        String contentABefore = Files.readString(fileA);
        String contentBBefore = Files.readString(fileB);

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s12";
        RefactoringPlan plan = service.getOrCreatePlan(planId, "Plan 12");
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.ClassX", "com.pkg.ClassY", "com.pkg.IClassY"));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        service.approvePlan(planId, sim.getPlanHash());

        // Injected broken executor to simulate compilation error
        PlanExecutionEngine brokenEngine = new PlanExecutionEngine(
                new BuildValidator(),
                new AstInterfaceExtractor(),
                new AstCallerRewriter() {
                    @Override
                    public RewriteResult rewriteCaller(Path caller, String tName, String tFqn,
                                                        String iName, String iFqn, String iPkg, boolean decouple) {
                        return new RewriteResult(caller, List.of(new SourceEdit(0, 0, "INVALID SYNTAX COMPILER ERROR;\n", "bad")), 1, 0, 0, 0, false);
                    }
                },
                new SourceFileEditor()
        );

        SimulationPlannerService brokenService = new SimulationPlannerService(
                engine,
                brokenEngine,
                new PostApplicationVerifier(),
                new CycleBreakingStrategy(),
                metricsEngine,
                new ProjectPackager()
        );

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        PlanExecutionResult execRes = brokenEngine.executePlan(proj, plan, sim, opts);

        assertFalse(execRes.success);
        assertTrue(execRes.rolledBack);
        assertEquals("COMPILE_FAILED", execRes.status);

        // Bitwise rollback verification
        assertEquals(contentABefore, Files.readString(fileA));
        assertEquals(contentBBefore, Files.readString(fileB));
        assertFalse(Files.exists(proj.resolve("src/main/java/com/pkg/IClassY.java")));
    }

    @Test
    @DisplayName("Scenario 13: Successful combined application modifies codebase and verifies build")
    public void testScenario13_SuccessfulCombinedApplication() throws Exception {
        Path proj = tempDir.resolve("test-scenario-13");
        createJavaFile(proj, "src/main/java/com/pkg/ServiceA.java",
                "package com.pkg;\npublic class ServiceA { private ServiceB b; public void runA() {} }\n");
        createJavaFile(proj, "src/main/java/com/pkg/ServiceB.java",
                "package com.pkg;\npublic class ServiceB { private ServiceA a; public void runB() {} }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s13";
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.ServiceA", "com.pkg.ServiceB", "com.pkg.IServiceB"));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        service.approvePlan(planId, sim.getPlanHash());

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        VerificationReport report = service.applyPlan(planId, proj, pr, opts);

        assertTrue(report.verified);
        assertTrue(report.buildPassed);
        assertEquals("PASSED", report.compilerStatus);
        assertTrue(Files.exists(proj.resolve("src/main/java/com/pkg/IServiceB.java")));
        assertNotNull(report.zipDownloadPath);
        assertTrue(Files.exists(Path.of(report.zipDownloadPath)));
    }

    @Test
    @DisplayName("Scenario 14: Post-application metric verification compares actual against simulated")
    public void testScenario14_PostApplicationMetricVerification() throws Exception {
        Path proj = tempDir.resolve("test-scenario-14");
        createJavaFile(proj, "src/main/java/com/pkg/Node1.java",
                "package com.pkg;\npublic class Node1 { private Node2 n2; public void m1() {} }\n");
        createJavaFile(proj, "src/main/java/com/pkg/Node2.java",
                "package com.pkg;\npublic class Node2 { private Node1 n1; public void m2() {} }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        String planId = "plan-s14";
        service.addOperation(planId, RefactoringOperation.breakCycle("com.pkg.Node1", "com.pkg.Node2", "com.pkg.INode2"));

        PlannerSimulationResult sim = service.simulatePlan(planId, pr.model, pr.graph);
        service.approvePlan(planId, sim.getPlanHash());

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        VerificationReport report = service.applyPlan(planId, proj, pr, opts);

        assertTrue(report.verified);
        assertFalse(report.comparisons.isEmpty());
        // Verify cycle comparison
        var cycleComp = report.comparisons.stream()
                .filter(c -> c.metricName.contains("Circular Dependencies"))
                .findFirst().orElseThrow();
        assertEquals(0.0, cycleComp.simulatedValue);
        assertEquals(0.0, cycleComp.actualValue);
        assertTrue(cycleComp.isConcordant);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CRITICAL ACCEPTANCE TEST: Combined-Operation Non-Additivity Proof
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Critical Acceptance Test: Combined metrics recalculate from complete graph and are strictly NON-ADDITIVE")
    public void testCriticalAcceptanceNonAdditiveMetricRecalculation() {
        // Construct 3-node cycle: A -> B -> C -> A
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge("com.pkg.A", "com.pkg.B");
        graph.addEdge("com.pkg.B", "com.pkg.C");
        graph.addEdge("com.pkg.C", "com.pkg.A");

        ProjectModel model = new ProjectModel("Synthetic3Cycle");
        for (String v : graph.vertices()) {
            model.addComponent(new ProjectModel.ComponentNode(v, v, "com.pkg", ProjectModel.ComponentNode.Kind.CLASS, ""));
        }

        ProjectMetrics baseline = metricsEngine.analyze(model, graph);
        assertEquals(1, baseline.cycleCount);

        // 1. Simulate Operation A independently: Break A -> B
        RefactoringPlan planA = new RefactoringPlan("Plan A");
        planA.addOperation(RefactoringOperation.removeDependency("com.pkg.A", "com.pkg.B", "Decouple A->B"));
        PlannerSimulationResult simA = engine.simulate(planA, model, graph);
        assertTrue(simA.isSuccess());

        double deltaCyclesA = simA.getMetricDeltas().get("Circular Dependencies").getAbsoluteDelta();
        double deltaHealthA = simA.getMetricDeltas().get("Architecture Health").getAbsoluteDelta();
        assertEquals(-1.0, deltaCyclesA); // 1 -> 0

        // 2. Simulate Operation B independently: Break B -> C
        RefactoringPlan planB = new RefactoringPlan("Plan B");
        planB.addOperation(RefactoringOperation.removeDependency("com.pkg.B", "com.pkg.C", "Decouple B->C"));
        PlannerSimulationResult simB = engine.simulate(planB, model, graph);
        assertTrue(simB.isSuccess());

        double deltaCyclesB = simB.getMetricDeltas().get("Circular Dependencies").getAbsoluteDelta();
        double deltaHealthB = simB.getMetricDeltas().get("Architecture Health").getAbsoluteDelta();
        assertEquals(-1.0, deltaCyclesB); // 1 -> 0

        // 3. Simulate Combined Plan (A + B together)
        RefactoringPlan planCombined = new RefactoringPlan("Combined Plan A+B");
        planCombined.addOperation(RefactoringOperation.removeDependency("com.pkg.A", "com.pkg.B", "Decouple A->B"));
        planCombined.addOperation(RefactoringOperation.removeDependency("com.pkg.B", "com.pkg.C", "Decouple B->C"));
        PlannerSimulationResult simCombined = engine.simulate(planCombined, model, graph);
        assertTrue(simCombined.isSuccess());

        double deltaCyclesCombined = simCombined.getMetricDeltas().get("Circular Dependencies").getAbsoluteDelta();
        double deltaHealthCombined = simCombined.getMetricDeltas().get("Architecture Health").getAbsoluteDelta();
        double deltaCouplingCombined = simCombined.getMetricDeltas().get("Average Coupling").getAbsoluteDelta();

        // ── SCIENTIFIC NON-ADDITIVITY EVIDENCE ──
        // If additive: deltaCycles would be (-1) + (-1) = -2 (which would claim -1 remaining cycles, impossible!)
        // In reality: 1 cycle exists; removing both edges still leaves 0 cycles (delta = -1).
        assertNotEquals(deltaCyclesA + deltaCyclesB, deltaCyclesCombined,
                "Cycle reduction MUST NOT be a naive sum of individual deltas!");
        assertEquals(-1.0, deltaCyclesCombined);
        assertEquals(0, simCombined.getSimulatedCycleCount());

        // Combined health delta is computed from full graph topology, NOT the sum of individual health gains
        assertNotEquals(deltaHealthA + deltaHealthB, deltaHealthCombined,
                "Architecture Health improvement MUST NOT be a linear sum of individual deltas!");

        // Confirm calculation method flag confirms non-additivity
        assertFalse(simCombined.isAdditiveSum());
        assertEquals("Complete Graph Topology Recalculation (Non-Additive)", simCombined.getCalculationMethod());
    }
}
