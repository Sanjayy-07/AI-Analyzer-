package com.archanalyzer.refactor;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.pipeline.AnalyzerPipeline.PipelineResult;
import com.archanalyzer.pipeline.ProjectPackager;
import com.archanalyzer.refactor.cycle.*;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.ExecutionResult;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringStatus;
import com.archanalyzer.validate.BuildValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Service-layer bridge integrating the Novelty 1 Automated Cycle-Breaking Refactoring Engine
 * into the main application. Provides preview, candidate ranking, execution, packaging,
 * and session state synchronization.
 */
public class CycleRefactoringService {

    private final CycleBreakingStrategy breakingStrategy;
    private final CycleRefactoringExecutor refactoringExecutor;
    private final AstInterfaceExtractor interfaceExtractor;
    private final AstCallerRewriter callerRewriter;
    private final MetricsEngine metricsEngine;
    private final ProjectPackager packager;

    public CycleRefactoringService() {
        this(new CycleBreakingStrategy(),
             new CycleRefactoringExecutor(),
             new AstInterfaceExtractor(),
             new AstCallerRewriter(),
             new MetricsEngine(),
             new ProjectPackager());
    }

    public CycleRefactoringService(CycleBreakingStrategy breakingStrategy,
                                   CycleRefactoringExecutor refactoringExecutor,
                                   AstInterfaceExtractor interfaceExtractor,
                                   AstCallerRewriter callerRewriter,
                                   MetricsEngine metricsEngine,
                                   ProjectPackager packager) {
        this.breakingStrategy = breakingStrategy;
        this.refactoringExecutor = refactoringExecutor;
        this.interfaceExtractor = interfaceExtractor;
        this.callerRewriter = callerRewriter;
        this.metricsEngine = metricsEngine;
        this.packager = packager;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Data Transfer Objects for Service Layer
    // ──────────────────────────────────────────────────────────────────────────

    public static class CandidateEdgeDto {
        public String fromComponent;
        public String toComponent;
        public double costScore;
        public int callerCount;
        public int fieldCount;
        public int constructorUsages;
        public int cycleReductionCount;
        public String rationale;
        public boolean feasible;
        public String rejectionReason;

        public static CandidateEdgeDto from(CycleCandidateEdge edge) {
            CandidateEdgeDto dto = new CandidateEdgeDto();
            dto.fromComponent = edge.getFromComponent();
            dto.toComponent = edge.getToComponent();
            dto.costScore = edge.getCostScore();
            dto.callerCount = edge.getCallerCount();
            dto.fieldCount = edge.getFieldCount();
            dto.constructorUsages = edge.getConstructorUsageCount();
            dto.cycleReductionCount = edge.getCycleReductionCount();
            dto.rationale = edge.getRationale();
            dto.feasible = edge.isFeasible();
            dto.rejectionReason = edge.getRejectionReason();
            return dto;
        }
    }

    public static class SccDto {
        public List<String> members = new ArrayList<>();
        public List<CandidateEdgeDto> candidates = new ArrayList<>();
        public CandidateEdgeDto selectedEdge;
    }

    public static class CycleAnalysisDto {
        public int totalCyclicSccs;
        public List<SccDto> cyclicSccs = new ArrayList<>();
    }

    public static class CyclePreviewDto {
        public boolean feasible;
        public String rejectionReason = "";
        public CandidateEdgeDto selectedEdge;
        public String proposedInterfaceName;
        public String interfacePackage;
        public String generatedInterfaceSource;
        public String providerFilePath;
        public String callerFilePath;
        public List<String> proposedProviderEdits = new ArrayList<>();
        public List<String> proposedCallerEdits = new ArrayList<>();
        public List<String> potentialRisks = new ArrayList<>();
        public int expectedCycleReduction;
        public String strategyExplanation = "";
    }

    public static class CycleExecutionDto {
        public boolean success;
        public boolean rolledBack;
        public String status;
        public int initialCycleCount;
        public int finalCycleCount;
        public String generatedInterfacePath;
        public List<String> modifiedFiles = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
        public long durationMs;
        public String summary;
        public String compilerStatus = "UNKNOWN";
        public String testSummary = "N/A";
        public String zipDownloadPath;
        public double architectureHealthBefore;
        public double architectureHealthAfter;
        public double dependencyScoreBefore;
        public double dependencyScoreAfter;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Operation A: Analyze Cycles & Rank Candidates
    // ──────────────────────────────────────────────────────────────────────────

    public CycleAnalysisDto analyzeCycles(Path projectRoot, ProjectModel model, DependencyGraph graph) {
        CycleAnalysisDto dto = new CycleAnalysisDto();
        if (graph == null || projectRoot == null) return dto;

        List<Set<String>> sccs = GraphAlgorithms.stronglyConnectedComponents(graph);
        List<Set<String>> cyclicSccs = sccs.stream().filter(s -> s.size() > 1).toList();
        dto.totalCyclicSccs = cyclicSccs.size();

        for (Set<String> scc : cyclicSccs) {
            SccDto sccDto = new SccDto();
            sccDto.members.addAll(scc);

            CycleRefactoringPlan plan = breakingStrategy.planForScc(projectRoot, graph, model, scc);
            if (plan != null) {
                for (CycleCandidateEdge candidate : plan.getAllCandidates()) {
                    sccDto.candidates.add(CandidateEdgeDto.from(candidate));
                }
                if (plan.getSelectedEdge() != null) {
                    sccDto.selectedEdge = CandidateEdgeDto.from(plan.getSelectedEdge());
                }
            }
            dto.cyclicSccs.add(sccDto);
        }

        return dto;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Operation A.2: Non-Destructive Preview of Proposed Cycle Repair
    // ──────────────────────────────────────────────────────────────────────────

    public CyclePreviewDto previewCycleBreaking(Path projectRoot, ProjectModel model, DependencyGraph graph,
                                                String fromComponent, String toComponent) {
        CyclePreviewDto preview = new CyclePreviewDto();
        if (projectRoot == null || graph == null || model == null) {
            preview.feasible = false;
            preview.rejectionReason = "Missing project context or dependency graph.";
            return preview;
        }

        List<CycleRefactoringPlan> plans = breakingStrategy.analyzeCycles(projectRoot, graph, model);
        if (plans.isEmpty()) {
            preview.feasible = false;
            preview.rejectionReason = "No cyclic strongly connected components found in the current project.";
            return preview;
        }

        // Find matching plan by edge or default to the highest ranked plan
        CycleRefactoringPlan chosenPlan = null;
        if (fromComponent != null && toComponent != null) {
            for (CycleRefactoringPlan plan : plans) {
                for (CycleCandidateEdge c : plan.getAllCandidates()) {
                    if (c.getFromComponent().equals(fromComponent) && c.getToComponent().equals(toComponent)) {
                        chosenPlan = plan;
                        break;
                    }
                }
                if (chosenPlan != null) break;
            }
            if (chosenPlan == null) {
                preview.feasible = false;
                preview.rejectionReason = "Specified edge " + fromComponent + " -> " + toComponent + " was not found in cyclic components.";
                return preview;
            }
        } else {
            chosenPlan = plans.get(0);
        }

        CycleCandidateEdge selected = chosenPlan.getSelectedEdge();
        if (selected == null || !selected.isFeasible()) {
            preview.feasible = false;
            preview.rejectionReason = selected != null ? selected.getRejectionReason() : "No feasible candidate edge.";
            return preview;
        }

        preview.feasible = true;
        preview.selectedEdge = CandidateEdgeDto.from(selected);
        preview.proposedInterfaceName = chosenPlan.getExtractedInterfaceName();
        preview.interfacePackage = chosenPlan.getInterfacePackage();
        preview.potentialRisks.addAll(chosenPlan.getPotentialRisks());
        preview.expectedCycleReduction = selected.getCycleReductionCount();
        preview.strategyExplanation = chosenPlan.getStrategyExplanation();

        try {
            // Generate interface in-memory without disk modification
            AstInterfaceExtractor.ExtractionResult ext = interfaceExtractor.extractInterface(
                    projectRoot, selected.getToFile(), selected.getToSimpleName());
            preview.generatedInterfaceSource = ext.interfaceSource;
            preview.providerFilePath = selected.getToFile() != null ? selected.getToFile().toString() : "";
            preview.callerFilePath = selected.getFromFile() != null ? selected.getFromFile().toString() : "";

            if (ext.classImplementsEdit != null) {
                preview.proposedProviderEdits.add(ext.classImplementsEdit.getDescription() +
                        " -> " + ext.classImplementsEdit.getReplacementText());
            }

            // Simulate caller rewrites in-memory
            AstCallerRewriter.RewriteResult rw = callerRewriter.rewriteCaller(
                    selected.getFromFile(),
                    selected.getToSimpleName(),
                    selected.getToComponent(),
                    ext.interfaceName,
                    (ext.packageName.isEmpty() ? ext.interfaceName : ext.packageName + "." + ext.interfaceName),
                    ext.packageName,
                    true
            );
            for (SourceEdit se : rw.getEdits()) {
                preview.proposedCallerEdits.add(se.getDescription() + " -> " + se.getReplacementText().trim());
            }

        } catch (Exception e) {
            preview.feasible = false;
            preview.rejectionReason = "Failed to simulate AST extraction/rewriting: " + e.getMessage();
        }

        return preview;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Operation B: Apply Cycle Repair with Multi-Stage Verification
    // ──────────────────────────────────────────────────────────────────────────

    public CycleExecutionDto applyCycleBreaking(Path projectRoot, PipelineResult baseline, RefactoringOptions options) throws IOException {
        long startTime = System.currentTimeMillis();
        CycleExecutionDto dto = new CycleExecutionDto();

        if (projectRoot == null || !Files.exists(projectRoot)) {
            dto.success = false;
            dto.status = "PROJECT_NOT_FOUND";
            dto.errors.add("Project directory does not exist: " + projectRoot);
            return dto;
        }

        if (options == null) {
            options = new RefactoringOptions();
            options.setRunMavenTests(false); // In-memory compiler default for fast UI responsiveness
        }

        if (baseline != null && baseline.metricsBefore != null) {
            dto.architectureHealthBefore = baseline.metricsBefore.architectureHealthScore;
            dto.dependencyScoreBefore = baseline.metricsBefore.dependencyScore;
        }

        // Execute refactoring
        ExecutionResult execResult = refactoringExecutor.execute(projectRoot, options);

        dto.durationMs = System.currentTimeMillis() - startTime;
        dto.status = execResult.getStatus().name();
        dto.initialCycleCount = execResult.getInitialCyclicSccCount();
        dto.finalCycleCount = execResult.getFinalCyclicSccCount();
        dto.errors.addAll(execResult.getErrors());

        for (Path p : execResult.getModifiedFiles()) {
            dto.modifiedFiles.add(p.getFileName().toString());
        }
        if (execResult.getGeneratedInterfacePath() != null) {
            dto.generatedInterfacePath = execResult.getGeneratedInterfacePath().toString();
        }

        if (execResult.getStatus() == RefactoringStatus.VERIFIED) {
            dto.success = true;
            dto.rolledBack = false;
            dto.compilerStatus = "PASS";
            dto.testSummary = options.isRunMavenTests() ? "All Maven project tests passed." : "In-memory compiler tree syntax & type checks passed.";
            dto.summary = String.format("Successfully broke architectural cycle via dependency inversion of '%s' -> '%s'. Cycles reduced from %d to %d.",
                    execResult.getPlan().getSelectedEdge().getFromSimpleName(),
                    execResult.getPlan().getSelectedEdge().getToSimpleName(),
                    execResult.getInitialCyclicSccCount(),
                    execResult.getFinalCyclicSccCount());

            // Re-analyze project metrics and update baseline session state if provided
            try {
                ProjectModel postModel = parseProject(projectRoot);
                DependencyGraph postGraph = DependencyGraph.buildFrom(postModel);
                ProjectMetrics postMetrics = metricsEngine.analyze(postModel, postGraph);

                dto.architectureHealthAfter = postMetrics.architectureHealthScore;
                dto.dependencyScoreAfter = postMetrics.dependencyScore;

                if (baseline != null) {
                    baseline.model = postModel;
                    baseline.graph = postGraph;
                    baseline.metricsAfter = postMetrics;
                    baseline.refactoredProjectRoot = projectRoot;

                    // Package into downloadable zip
                    Path workDir = projectRoot.getParent();
                    if (workDir != null) {
                        Path zipFile = workDir.resolve(projectRoot.getFileName().toString() + "-refactored.zip");
                        packager.packageToZip(projectRoot, zipFile);
                        baseline.finalZipPath = zipFile;
                        dto.zipDownloadPath = zipFile.toAbsolutePath().toString();
                    }
                }
            } catch (Exception e) {
                dto.errors.add("Warning: Failed to package zip or recompute post metrics: " + e.getMessage());
            }

        } else if (execResult.getStatus() == RefactoringStatus.NO_CYCLES_DETECTED) {
            dto.success = false;
            dto.rolledBack = false;
            dto.status = "NO_CYCLES_DETECTED";
            dto.compilerStatus = "PASSED";
            dto.testSummary = "N/A";
            dto.summary = "No circular dependencies detected. Project architecture is already cycle-free.";
            dto.errors.add("No circular dependencies exist in the project architecture. All components form an acyclic dependency graph.");
            if (baseline != null && baseline.metricsBefore != null) {
                dto.architectureHealthAfter = baseline.metricsBefore.architectureHealthScore;
                dto.dependencyScoreAfter = baseline.metricsBefore.dependencyScore;
            }
        } else {
            dto.success = false;
            dto.rolledBack = true;
            dto.compilerStatus = execResult.getStatus() == RefactoringStatus.COMPILE_FAILED ? "FAIL" : "PASSED";
            dto.testSummary = execResult.getStatus() == RefactoringStatus.TEST_FAILED ? "Tests failed post-refactoring." : "N/A";
            dto.summary = "Cycle breaking could not be verified (" + execResult.getStatus() + "). Changes were safely rolled back.";
            if (baseline != null && baseline.metricsBefore != null) {
                dto.architectureHealthAfter = baseline.metricsBefore.architectureHealthScore;
                dto.dependencyScoreAfter = baseline.metricsBefore.dependencyScore;
            }
            if (dto.errors.isEmpty()) {
                if (execResult.getStatus() == RefactoringStatus.COMPILE_FAILED) {
                    dto.errors.add("In-memory compiler verification failed. Proposed interface extraction or caller rewrite produced compiler errors.");
                } else if (execResult.getStatus() == RefactoringStatus.TEST_FAILED) {
                    dto.errors.add("Maven project tests failed post-refactoring.");
                } else if (execResult.getStatus() == RefactoringStatus.CYCLE_NOT_BROKEN) {
                    dto.errors.add("Refactoring did not eliminate the targeted architectural cycle.");
                } else if (execResult.getStatus() == RefactoringStatus.PREEXISTING_BUILD_FAILURE) {
                    dto.errors.add("Pre-existing compilation failure detected in original source files.");
                } else {
                    dto.errors.add(dto.summary);
                }
            }
        }

        return dto;
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
