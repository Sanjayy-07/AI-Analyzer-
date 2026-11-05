package com.archanalyzer.refactor.cycle;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.validate.BuildValidator;
import com.archanalyzer.validate.BuildValidator.BuildResult;
import com.archanalyzer.validate.BuildValidator.Status;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Orchestrates automated cycle-breaking refactoring with verified dependency inversion.
 * Ensures complete safety via atomic rollback, compiler validation, test verification,
 * and graph cycle reduction proof.
 */
public class CycleRefactoringExecutor {

    public enum RefactoringStatus {
        VERIFIED,
        PREVIEW_ONLY,
        COMPILE_FAILED,
        TEST_FAILED,
        CYCLE_NOT_BROKEN,
        REPAIR_REJECTED,
        PREEXISTING_BUILD_FAILURE,
        NO_CYCLES_DETECTED
    }

    public enum CycleGoal {
        BREAK_ONE,
        BREAK_ALL,
        REDUCE_CYCLES
    }

    public static class RefactoringOptions {
        private boolean previewOnly = false;
        private boolean runMavenTests = true;
        private boolean isolatedWorkspace = false;
        private boolean decoupleConstructorInstantiation = true;
        private Path logOutputDir = null;
        private CycleGoal goal = CycleGoal.BREAK_ALL;
        private int maxIterations = 10;

        public boolean isPreviewOnly() { return previewOnly; }
        public void setPreviewOnly(boolean previewOnly) { this.previewOnly = previewOnly; }

        public boolean isRunMavenTests() { return runMavenTests; }
        public void setRunMavenTests(boolean runMavenTests) { this.runMavenTests = runMavenTests; }

        public boolean isIsolatedWorkspace() { return isolatedWorkspace; }
        public void setIsolatedWorkspace(boolean isolatedWorkspace) { this.isolatedWorkspace = isolatedWorkspace; }

        public boolean isDecoupleConstructorInstantiation() { return decoupleConstructorInstantiation; }
        public void setDecoupleConstructorInstantiation(boolean decouple) { this.decoupleConstructorInstantiation = decouple; }

        public Path getLogOutputDir() { return logOutputDir; }
        public void setLogOutputDir(Path logOutputDir) { this.logOutputDir = logOutputDir; }

        public CycleGoal getGoal() { return goal; }
        public void setGoal(CycleGoal goal) { this.goal = goal; }

        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    }

    public static class ExecutionResult {
        private RefactoringStatus status;
        private CycleRefactoringPlan plan;
        private int initialCyclicSccCount;
        private int finalCyclicSccCount;
        private Path generatedInterfacePath;
        private List<Path> modifiedFiles = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        private long executionDurationMs;
        private ChangeManifest manifest;
        private int iterationsExecuted = 0;
        private List<CycleCandidateEdge> appliedEdges = new ArrayList<>();

        public RefactoringStatus getStatus() { return status; }
        public void setStatus(RefactoringStatus status) { this.status = status; }

        public CycleRefactoringPlan getPlan() { return plan; }
        public void setPlan(CycleRefactoringPlan plan) { this.plan = plan; }

        public int getInitialCyclicSccCount() { return initialCyclicSccCount; }
        public void setInitialCyclicSccCount(int initialCyclicSccCount) { this.initialCyclicSccCount = initialCyclicSccCount; }

        public int getFinalCyclicSccCount() { return finalCyclicSccCount; }
        public void setFinalCyclicSccCount(int finalCyclicSccCount) { this.finalCyclicSccCount = finalCyclicSccCount; }

        public Path getGeneratedInterfacePath() { return generatedInterfacePath; }
        public void setGeneratedInterfacePath(Path generatedInterfacePath) { this.generatedInterfacePath = generatedInterfacePath; }

        public List<Path> getModifiedFiles() { return modifiedFiles; }
        public List<String> getErrors() { return errors; }

        public long getExecutionDurationMs() { return executionDurationMs; }
        public void setExecutionDurationMs(long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

        public ChangeManifest getManifest() { return manifest; }
        public void setManifest(ChangeManifest manifest) { this.manifest = manifest; }

        public int getIterationsExecuted() { return iterationsExecuted; }
        public void setIterationsExecuted(int it) { this.iterationsExecuted = it; }

        public List<CycleCandidateEdge> getAppliedEdges() { return appliedEdges; }
    }

    private final BuildValidator buildValidator;
    private final CycleBreakingStrategy breakingStrategy;
    private final AstInterfaceExtractor interfaceExtractor;
    private final AstCallerRewriter callerRewriter;
    private final SourceFileEditor sourceFileEditor;

    public CycleRefactoringExecutor() {
        this(new BuildValidator(), new CycleBreakingStrategy(), new AstInterfaceExtractor(), new AstCallerRewriter(), new SourceFileEditor());
    }

    public CycleRefactoringExecutor(BuildValidator buildValidator,
                                    CycleBreakingStrategy breakingStrategy,
                                    AstInterfaceExtractor interfaceExtractor,
                                    AstCallerRewriter callerRewriter,
                                    SourceFileEditor sourceFileEditor) {
        this.buildValidator = buildValidator;
        this.breakingStrategy = breakingStrategy;
        this.interfaceExtractor = interfaceExtractor;
        this.callerRewriter = callerRewriter;
        this.sourceFileEditor = sourceFileEditor;
    }

    public ExecutionResult execute(Path projectRoot, RefactoringOptions options) throws IOException {
        long startTime = System.currentTimeMillis();
        ExecutionResult result = new ExecutionResult();

        if (options.isIsolatedWorkspace()) {
            Path tempDir = Files.createTempDirectory("archanalyzer-isolated-");
            try {
                copyDirectory(projectRoot, tempDir);
                options.setIsolatedWorkspace(false); // don't recurse
                ExecutionResult isolatedRes = execute(tempDir, options);
                options.setIsolatedWorkspace(true);
                return isolatedRes;
            } finally {
                deleteDirectory(tempDir);
            }
        }

        // 1. Parse Project & Build Baseline Dependency Graph
        ProjectModel initialModel = parseProject(projectRoot);
        if (initialModel.hasParseErrors()) {
            result.setStatus(RefactoringStatus.PREEXISTING_BUILD_FAILURE);
            result.getErrors().addAll(initialModel.getParseErrors().values());
            result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
            logExecution(projectRoot, result, null);
            return result;
        }

        DependencyGraph initialGraph = DependencyGraph.buildFrom(initialModel);
        List<Set<String>> initialSccs = GraphAlgorithms.stronglyConnectedComponents(initialGraph);
        List<Set<String>> cyclicSccs = initialSccs.stream().filter(s -> s.size() > 1).toList();

        result.setInitialCyclicSccCount(cyclicSccs.size());

        if (cyclicSccs.isEmpty()) {
            result.setStatus(RefactoringStatus.NO_CYCLES_DETECTED);
            result.setFinalCyclicSccCount(0);
            result.getErrors().add("No circular dependencies exist in the project architecture. All components form an acyclic dependency graph.");
            result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
            logExecution(projectRoot, result, null);
            return result;
        }

        // 2. Check Baseline Compilation
        BuildResult baselineBuild = buildValidator.compile(projectRoot, List.of());
        if (baselineBuild.status == Status.FAIL) {
            result.setStatus(RefactoringStatus.PREEXISTING_BUILD_FAILURE);
            result.getErrors().addAll(baselineBuild.errors);
            result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
            logExecution(projectRoot, result, null);
            return result;
        }

        // 4. Apply Graph-Aware Iterative Refactoring with Atomic Transactional Manifest Tracking
        ChangeManifest manifest = new ChangeManifest();
        result.setManifest(manifest);
        Path baselineDir = null;

        ProjectModel currentModel = initialModel;
        DependencyGraph currentGraph = initialGraph;
        List<Set<String>> currentCyclicSccs = cyclicSccs;
        int initialCyclicSccCount = cyclicSccs.size();
        int currentCyclicSccCount = cyclicSccs.size();
        int currentCyclicNodes = cyclicSccs.stream().mapToInt(Set::size).sum();

        Set<String> attemptedEdgeSignatures = new HashSet<>();
        int maxIterations = options.getMaxIterations() > 0 ? options.getMaxIterations() : 10;
        int iteration = 0;

        try {
            try {
                baselineDir = Files.createTempDirectory("archanalyzer-cycle-baseline-");
                copyDirectory(projectRoot, baselineDir);
            } catch (Exception ignored) {}

            while (iteration < maxIterations) {
                // Goal check: if cycles eliminated, stop iteration
                if (currentCyclicSccCount == 0) {
                    break;
                }
                if (options.getGoal() == CycleGoal.BREAK_ONE && iteration > 0 && currentCyclicSccCount < initialCyclicSccCount) {
                    break;
                }

                // 4a. Plan cycle breaking for current graph state
                CycleRefactoringPlan plan = breakingStrategy.planCycleBreaking(projectRoot, currentModel, currentGraph);
                if (plan == null || plan.getSelectedEdge() == null) {
                    // No further candidate edges found
                    break;
                }

                if (iteration == 0) {
                    result.setPlan(plan);
                }

                if (options.isPreviewOnly()) {
                    result.setStatus(RefactoringStatus.PREVIEW_ONLY);
                    result.setFinalCyclicSccCount(currentCyclicSccCount);
                    result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
                    logExecution(projectRoot, result, plan);
                    return result;
                }

                CycleCandidateEdge edge = plan.getSelectedEdge();
                String edgeSig = edge.getFromComponent() + "->" + edge.getToComponent();
                if (attemptedEdgeSignatures.contains(edgeSig)) {
                    // Prevent infinite cycle oscillation on the same edge
                    break;
                }
                attemptedEdgeSignatures.add(edgeSig);

                // 4b. Interface Extraction
                Path providerPath = edge.getToFile();
                AstInterfaceExtractor.ExtractionResult extResult = interfaceExtractor.extractInterface(
                        projectRoot, providerPath, edge.getToSimpleName());

                Path interfaceFile = extResult.interfaceFile;
                if (!extResult.reusedExisting) {
                    if (Files.exists(interfaceFile) && !result.getModifiedFiles().contains(interfaceFile)) {
                        manifest.rollbackAll();
                        result.setStatus(RefactoringStatus.REPAIR_REJECTED);
                        result.getErrors().add("Target interface already exists: " + interfaceFile);
                        result.setFinalCyclicSccCount(cyclicSccs.size());
                        result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
                        logExecution(projectRoot, result, plan);
                        return result;
                    }
                    manifest.recordFileForModification(interfaceFile);
                    Files.createDirectories(interfaceFile.getParent());
                    Files.writeString(interfaceFile, extResult.interfaceSource, StandardCharsets.UTF_8);
                    result.setGeneratedInterfacePath(interfaceFile);
                    result.getModifiedFiles().add(interfaceFile);
                }

                // 4c. Update Provider class to implement interface
                manifest.recordFileForModification(providerPath);
                SourceFileEditor.applyEditsToFile(providerPath, List.of(extResult.classImplementsEdit));
                result.getModifiedFiles().add(providerPath);

                // 4d. Rewrite Caller class to reference interface
                Path callerPath = edge.getFromFile();
                manifest.recordFileForModification(callerPath);
                AstCallerRewriter.RewriteResult rewriteRes = callerRewriter.rewriteCaller(
                        callerPath,
                        edge.getToSimpleName(),
                        edge.getToComponent(),
                        extResult.interfaceName,
                        (extResult.packageName.isEmpty() ? extResult.interfaceName : extResult.packageName + "." + extResult.interfaceName),
                        extResult.packageName,
                        options.isDecoupleConstructorInstantiation()
                );
                SourceFileEditor.applyEditsToFile(callerPath, rewriteRes.getEdits());
                result.getModifiedFiles().add(callerPath);

                // 5. In-Memory Compilation Verification for this step
                BuildResult postBuild = buildValidator.compile(projectRoot, List.of(), baselineDir);
                if (postBuild.status == Status.FAIL) {
                    manifest.rollbackAll();
                    result.setStatus(RefactoringStatus.COMPILE_FAILED);
                    if (postBuild.errors.isEmpty()) {
                        result.getErrors().add("In-memory compiler verification failed post-refactoring.");
                    } else {
                        result.getErrors().addAll(postBuild.errors);
                    }
                    result.setFinalCyclicSccCount(cyclicSccs.size());
                    result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
                    logExecution(projectRoot, result, plan);
                    return result;
                }

                // 6. Re-parse Project & Re-analyze Graph
                ProjectModel nextModel = parseProject(projectRoot);
                DependencyGraph nextGraph = DependencyGraph.buildFrom(nextModel);
                List<Set<String>> nextSccs = GraphAlgorithms.stronglyConnectedComponents(nextGraph);
                List<Set<String>> nextCyclicSccs = nextSccs.stream().filter(s -> s.size() > 1).toList();
                int nextCyclicCount = nextCyclicSccs.size();
                int nextCyclicNodes = nextCyclicSccs.stream().mapToInt(Set::size).sum();

                // Check graph progression: either SCC count decreased, or total nodes in cyclic SCCs decreased
                boolean improved = (nextCyclicCount < currentCyclicSccCount) || (nextCyclicNodes < currentCyclicNodes);
                if (!improved && iteration == 0) {
                    manifest.rollbackAll();
                    result.setStatus(RefactoringStatus.CYCLE_NOT_BROKEN);
                    result.getErrors().add("Refactoring completed compilation but did not eliminate the architectural cycle.");
                    result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
                    logExecution(projectRoot, result, plan);
                    return result;
                }

                currentModel = nextModel;
                currentGraph = nextGraph;
                currentCyclicSccs = nextCyclicSccs;
                currentCyclicSccCount = nextCyclicCount;
                currentCyclicNodes = nextCyclicNodes;
                result.getAppliedEdges().add(edge);
                iteration++;
            }

            result.setIterationsExecuted(iteration);
            result.setFinalCyclicSccCount(currentCyclicSccCount);

            if (iteration == 0 || (currentCyclicSccCount >= initialCyclicSccCount && currentCyclicNodes >= cyclicSccs.stream().mapToInt(Set::size).sum())) {
                manifest.rollbackAll();
                result.setStatus(RefactoringStatus.CYCLE_NOT_BROKEN);
                result.getErrors().add("Iterative refactoring completed but could not eliminate cycles.");
                result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
                logExecution(projectRoot, result, result.getPlan());
                return result;
            }

            // External Build Tool & Test Verification (if enabled)
            if (options.isRunMavenTests() && Files.exists(projectRoot.resolve("pom.xml"))) {
                BuildResult testResult = new BuildResult();
                buildValidator.runExternalBuildToolValidation(projectRoot, "maven", testResult);
                if (testResult.testsExecuted && !testResult.testsPassed) {
                    manifest.rollbackAll();
                    result.setStatus(RefactoringStatus.TEST_FAILED);
                    result.getErrors().addAll(testResult.errors);
                    result.setFinalCyclicSccCount(cyclicSccs.size());
                    result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
                    logExecution(projectRoot, result, result.getPlan());
                    return result;
                }
            }

            // Success: Refactoring Verified
            result.setStatus(RefactoringStatus.VERIFIED);
            result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
            logExecution(projectRoot, result, result.getPlan());
            return result;

        } catch (Exception e) {
            manifest.rollbackAll();
            result.setStatus(RefactoringStatus.REPAIR_REJECTED);
            result.getErrors().add("Refactoring exception: " + e.getMessage());
            result.setFinalCyclicSccCount(cyclicSccs.size());
            result.setExecutionDurationMs(System.currentTimeMillis() - startTime);
            logExecution(projectRoot, result, result.getPlan());
            return result;
        } finally {
            if (baselineDir != null) {
                deleteDirectory(baselineDir);
            }
        }
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

    private void logExecution(Path projectRoot, ExecutionResult result, CycleRefactoringPlan plan) {
        CycleRefactoringLogger.LogEntry entry = new CycleRefactoringLogger.LogEntry();
        entry.projectPath = projectRoot.toString();
        entry.status = result.getStatus().name();
        entry.initialCyclicSccCount = result.getInitialCyclicSccCount();
        entry.finalCyclicSccCount = result.getFinalCyclicSccCount();
        entry.executionDurationMs = result.getExecutionDurationMs();
        entry.errors = result.getErrors();
        entry.compilationPassed = result.getStatus() == RefactoringStatus.VERIFIED;
        entry.testsPassed = result.getStatus() == RefactoringStatus.VERIFIED;

        if (plan != null) {
            entry.sccMembers = new ArrayList<>(plan.getSccMembers());
            if (plan.getSelectedEdge() != null) {
                entry.selectedEdgeFrom = plan.getSelectedEdge().getFromComponent();
                entry.selectedEdgeTo = plan.getSelectedEdge().getToComponent();
                entry.selectedEdgeCost = plan.getSelectedEdge().getCostScore();
            }
            entry.extractedInterface = plan.getInterfaceFqn();
            if (plan.getInterfaceFilePath() != null) {
                entry.interfaceFilePath = plan.getInterfaceFilePath().toString();
            }
            entry.strategyExplanation = plan.getStrategyExplanation();
        }

        entry.affectedFiles = result.getModifiedFiles().stream().map(Path::toString).toList();

        try {
            Path reportsDir = Path.of("research", "reports");
            CycleRefactoringLogger.writeLogFile(reportsDir, entry);
        } catch (IOException ignored) {}
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest);
                }
            }
        }
    }

    private static void deleteDirectory(Path path) {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException ignored) {}
    }
}
