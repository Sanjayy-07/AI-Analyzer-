package com.archanalyzer.refactor;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.pipeline.AnalyzerPipeline;
import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.pipeline.ProjectDiscovery;
import com.archanalyzer.refactor.CycleRefactoringService.*;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.simulator.SimulationResult;
import com.archanalyzer.simulator.WhatIfSimulator;
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
 * Verifies application service-layer integration of Novelty 1 (Automated Cycle-Breaking Refactoring).
 * Uses dedicated synthetic fixtures outside sample-demo-project.
 */
public class CycleRefactoringServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private CycleRefactoringService service;
    private AnalyzerPipeline pipeline;

    @BeforeEach
    public void setUp() {
        service = new CycleRefactoringService();
        pipeline = new AnalyzerPipeline(true);
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
        MetricsEngine metricsEngine = new MetricsEngine();
        MetricsEngine.ProjectMetrics metrics = metricsEngine.analyze(model, graph);

        PipelineResult res = new PipelineResult();
        res.projectRoot = proj;
        res.model = model;
        res.graph = graph;
        res.metricsBefore = metrics;
        return res;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. API / Service successfully identifies a cyclic dependency
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("1. Service successfully identifies cyclic dependencies in ProjectModel")
    public void testIdentifyCyclicDependencies() throws Exception {
        Path proj = tempDir.resolve("test-proj-1");
        createJavaFile(proj, "src/main/java/com/pkg/Alpha.java",
                "package com.pkg;\npublic class Alpha { private Beta b; public void doA() {} }\n");
        createJavaFile(proj, "src/main/java/com/pkg/Beta.java",
                "package com.pkg;\npublic class Beta { private Alpha a; public void doB() {} }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        CycleAnalysisDto analysis = service.analyzeCycles(proj, pr.model, pr.graph);

        assertEquals(1, analysis.totalCyclicSccs);
        assertEquals(1, analysis.cyclicSccs.size());
        assertTrue(analysis.cyclicSccs.get(0).members.contains("com.pkg.Alpha"));
        assertTrue(analysis.cyclicSccs.get(0).members.contains("com.pkg.Beta"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. API / Service returns candidate repair plans ranked by cost score
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("2. Service returns candidate repair plans ranked by cost score")
    public void testCandidateRepairPlansRanked() throws Exception {
        Path proj = tempDir.resolve("test-proj-2");
        createJavaFile(proj, "src/main/java/com/pkg/ServiceA.java",
                "package com.pkg;\npublic class ServiceA { private ServiceB b; public void a() {} }\n");
        createJavaFile(proj, "src/main/java/com/pkg/ServiceB.java",
                "package com.pkg;\npublic class ServiceB { private ServiceA a; public void b() {} }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        CycleAnalysisDto analysis = service.analyzeCycles(proj, pr.model, pr.graph);

        SccDto scc = analysis.cyclicSccs.get(0);
        assertNotNull(scc.selectedEdge);
        assertFalse(scc.candidates.isEmpty());
        assertTrue(scc.selectedEdge.costScore > 0);
        assertTrue(scc.selectedEdge.feasible);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Preview returns proposed modifications without changing source files
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("3. Preview returns proposed modifications without changing source files (Non-destructive)")
    public void testPreviewWithoutModifyingFiles() throws Exception {
        Path proj = tempDir.resolve("test-proj-3");
        Path fileA = createJavaFile(proj, "src/main/java/com/pkg/Order.java",
                "package com.pkg;\npublic class Order { private Payment p; public void place() {} }\n");
        Path fileB = createJavaFile(proj, "src/main/java/com/pkg/Payment.java",
                "package com.pkg;\npublic class Payment { private Order o; public void pay() {} }\n");

        String contentABefore = Files.readString(fileA);
        String contentBBefore = Files.readString(fileB);

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        CyclePreviewDto preview = service.previewCycleBreaking(proj, pr.model, pr.graph, null, null);

        assertTrue(preview.feasible);
        assertNotNull(preview.proposedInterfaceName);
        assertNotNull(preview.generatedInterfaceSource);
        assertTrue(preview.generatedInterfaceSource.contains("public interface I"));
        assertFalse(preview.proposedCallerEdits.isEmpty());

        // Verify zero disk modifications
        assertEquals(contentABefore, Files.readString(fileA));
        assertEquals(contentBBefore, Files.readString(fileB));
        assertFalse(Files.exists(proj.resolve("src/main/java/com/pkg/IPayment.java")));
        assertFalse(Files.exists(proj.resolve("src/main/java/com/pkg/IOrder.java")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Apply invokes the existing CycleRefactoringExecutor & verifies result
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("4. Apply executes refactoring, reduces cycles, and packages zip (Scenarios 4, 5, 6, 10, 11)")
    public void testApplyRefactoringSuccess() throws Exception {
        Path proj = tempDir.resolve("test-proj-4");
        createJavaFile(proj, "src/main/java/com/pkg/CompA.java",
                "package com.pkg;\npublic class CompA { private CompB b; public void runA() {} }\n");
        createJavaFile(proj, "src/main/java/com/pkg/CompB.java",
                "package com.pkg;\npublic class CompB { private CompA a; public void runB() {} }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        CycleExecutionDto result = service.applyCycleBreaking(proj, pr, opts);

        assertTrue(result.success);
        assertFalse(result.rolledBack);
        assertEquals("VERIFIED", result.status);
        assertEquals(1, result.initialCycleCount);
        assertEquals(0, result.finalCycleCount);
        assertNotNull(result.generatedInterfacePath);
        assertTrue(Files.exists(Path.of(result.generatedInterfacePath)));
        assertEquals(0, pr.metricsAfter.cycleCount);
        assertNotNull(result.zipDownloadPath);

        // Verify ZIP contents (Scenario 11)
        Path zipPath = Path.of(result.zipDownloadPath);
        assertTrue(Files.exists(zipPath));
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipPath.toFile())) {
            boolean hasInterface = false;
            boolean hasModifiedCaller = false;
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.contains("ICompB.java") || name.contains("ICompA.java")) {
                    hasInterface = true;
                }
                if (name.contains("CompA.java") || name.contains("CompB.java")) {
                    hasModifiedCaller = true;
                }
            }
            assertTrue(hasInterface, "ZIP must contain newly generated interface file");
            assertTrue(hasModifiedCaller, "ZIP must contain modified sources");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Compilation failure returns accurate error and triggers rollback
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("5. Compilation failure triggers atomic rollback and surfaces error")
    public void testCompilationFailureTriggersRollback() throws Exception {
        Path proj = tempDir.resolve("test-proj-5");
        Path fileA = createJavaFile(proj, "src/main/java/com/pkg/ClassA.java",
                "package com.pkg;\npublic class ClassA { private ClassB b; public void doA() {} }\n");
        Path fileB = createJavaFile(proj, "src/main/java/com/pkg/ClassB.java",
                "package com.pkg;\npublic class ClassB { private ClassA a; public void doB() {} }\n");

        String origA = Files.readString(fileA);
        String origB = Files.readString(fileB);

        // Inject broken caller rewriter to simulate compiler failure
        CycleRefactoringExecutor brokenExecutor = new CycleRefactoringExecutor(
                new com.archanalyzer.validate.BuildValidator(),
                new com.archanalyzer.refactor.cycle.CycleBreakingStrategy(),
                new com.archanalyzer.refactor.cycle.AstInterfaceExtractor(),
                new com.archanalyzer.refactor.cycle.AstCallerRewriter() {
                    @Override
                    public RewriteResult rewriteCaller(Path caller, String tName, String tFqn,
                                                        String iName, String iFqn, String iPkg, boolean decouple) {
                        return new RewriteResult(caller, List.of(new com.archanalyzer.refactor.cycle.SourceEdit(0, 0, "BROKEN SYNTAX ERROR !!!\n", "bad")), 1, 0, 0, 0, false);
                    }
                },
                new com.archanalyzer.refactor.cycle.SourceFileEditor()
        );

        CycleRefactoringService brokenService = new CycleRefactoringService(
                new com.archanalyzer.refactor.cycle.CycleBreakingStrategy(),
                brokenExecutor,
                new com.archanalyzer.refactor.cycle.AstInterfaceExtractor(),
                new com.archanalyzer.refactor.cycle.AstCallerRewriter(),
                new MetricsEngine(),
                new com.archanalyzer.pipeline.ProjectPackager()
        );

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        CycleExecutionDto res = brokenService.applyCycleBreaking(proj, pr, new RefactoringOptions());

        assertFalse(res.success);
        assertTrue(res.rolledBack);
        assertEquals("COMPILE_FAILED", res.status);
        assertFalse(res.errors.isEmpty());
        // Verify rollback bitwise equality
        assertEquals(origA, Files.readString(fileA));
        assertEquals(origB, Files.readString(fileB));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Invalid / Missing project paths are rejected safely
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("6. Invalid or missing project paths are rejected safely")
    public void testInvalidProjectPathsRejected() throws Exception {
        Path nonExistent = tempDir.resolve("does-not-exist");
        CycleExecutionDto res = service.applyCycleBreaking(nonExistent, null, new RefactoringOptions());

        assertFalse(res.success);
        assertEquals("PROJECT_NOT_FOUND", res.status);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. RefactoringEngine delegation works
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("7. RefactoringEngine exposes CycleRefactoringService bridge")
    public void testRefactoringEngineBridge() {
        RefactoringEngine engine = new RefactoringEngine();
        assertNotNull(engine.getCycleRefactoringService());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 8. AnalyzerPipeline helper methods work
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("8. AnalyzerPipeline exposes cycle refactoring methods")
    public void testAnalyzerPipelineCycleMethods() throws Exception {
        Path proj = tempDir.resolve("test-proj-8");
        createJavaFile(proj, "src/main/java/com/pkg/NodeX.java",
                "package com.pkg;\npublic class NodeX { private NodeY y; public void x() {} }\n");
        createJavaFile(proj, "src/main/java/com/pkg/NodeY.java",
                "package com.pkg;\npublic class NodeY { private NodeX x; public void y() {} }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        CycleAnalysisDto analysis = pipeline.analyzeCycles(pr);

        assertEquals(1, analysis.totalCyclicSccs);
        CyclePreviewDto prev = pipeline.previewCycleBreaking(pr, null, null);
        assertTrue(prev.feasible);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 9. Existing What-If Simulator functionality remains intact
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("9. Existing What-If Simulator functionality remains intact")
    public void testWhatIfSimulatorUnchanged() throws Exception {
        Path proj = tempDir.resolve("test-proj-9");
        createJavaFile(proj, "src/main/java/com/pkg/AlphaService.java",
                "package com.pkg;\npublic class AlphaService { private BetaService b; public void run() {} }\n");
        createJavaFile(proj, "src/main/java/com/pkg/BetaService.java",
                "package com.pkg;\npublic class BetaService { public void run() {} }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        SimulationResult sim = pipeline.simulate(pr, "Remove BetaService", "com.pkg.BetaService", null, null);

        assertNotNull(sim);
        assertNotNull(sim.getSimulationId());
        assertNotNull(sim.getBaselineMetrics());
        assertNotNull(sim.getProjectedMetrics());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 10. Multi-project isolation: refactoring project A does not affect project B
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("12. Multi-project isolation: refactoring project A does not affect project B")
    public void testMultiProjectIsolation() throws Exception {
        Path projA = tempDir.resolve("test-proj-A");
        createJavaFile(projA, "src/main/java/com/pkg/A1.java",
                "package com.pkg;\npublic class A1 { private A2 a2; public void doA1() {} }\n");
        createJavaFile(projA, "src/main/java/com/pkg/A2.java",
                "package com.pkg;\npublic class A2 { private A1 a1; public void doA2() {} }\n");

        Path projB = tempDir.resolve("test-proj-B");
        createJavaFile(projB, "src/main/java/com/pkg/B1.java",
                "package com.pkg;\npublic class B1 { private B2 b2; public void doB1() {} }\n");
        createJavaFile(projB, "src/main/java/com/pkg/B2.java",
                "package com.pkg;\npublic class B2 { private B1 b1; public void doB2() {} }\n");

        PipelineResult prA = buildSyntheticPipelineResult(projA);
        PipelineResult prB = buildSyntheticPipelineResult(projB);

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        CycleExecutionDto resA = service.applyCycleBreaking(projA, prA, opts);
        assertTrue(resA.success);

        // Project B is completely untouched and still retains its cycle
        CycleAnalysisDto analysisB = service.analyzeCycles(projB, prB.model, prB.graph);
        assertEquals(1, analysisB.totalCyclicSccs);
        assertFalse(Files.exists(projB.resolve("src/main/java/com/pkg/IB2.java")));
        assertFalse(Files.exists(projB.resolve("src/main/java/com/pkg/IB1.java")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 11. Invalid/non-existent edge selection returns clean error, no side effects
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("13. Invalid/non-existent edge selection returns clean error without side effects")
    public void testInvalidEdgeSelection() throws Exception {
        Path proj = tempDir.resolve("test-proj-invalid");
        createJavaFile(proj, "src/main/java/com/pkg/X.java",
                "package com.pkg;\npublic class X { private Y y; }\n");
        createJavaFile(proj, "src/main/java/com/pkg/Y.java",
                "package com.pkg;\npublic class Y { private X x; }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        CyclePreviewDto preview = service.previewCycleBreaking(proj, pr.model, pr.graph, "NonExistentSource", "NonExistentTarget");
        assertFalse(preview.feasible);
        assertNotNull(preview.rejectionReason);
        assertTrue(preview.rejectionReason.contains("not found") || preview.rejectionReason.contains("Specified edge"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 12. Project with zero cycles returns appropriate response
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("14. Project with zero cycles returns appropriate response")
    public void testZeroCycles() throws Exception {
        Path proj = tempDir.resolve("test-proj-zero");
        createJavaFile(proj, "src/main/java/com/pkg/Solo.java",
                "package com.pkg;\npublic class Solo { public void run() {} }\n");

        PipelineResult pr = buildSyntheticPipelineResult(proj);
        CycleAnalysisDto analysis = service.analyzeCycles(proj, pr.model, pr.graph);
        assertEquals(0, analysis.totalCyclicSccs);
        assertTrue(analysis.cyclicSccs.isEmpty());

        CyclePreviewDto preview = service.previewCycleBreaking(proj, pr.model, pr.graph, null, null);
        assertFalse(preview.feasible);
        assertTrue(preview.rejectionReason.contains("No cyclic"));
    }
}
