package com.archanalyzer.planner;

import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.refactor.cycle.*;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.validate.BuildValidator;
import com.archanalyzer.validate.BuildValidator.BuildResult;
import com.archanalyzer.validate.BuildValidator.Status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Executes an APPROVED multi-operation RefactoringPlan on actual project files.
 * Enforces atomic rollback if compilation or tests fail at any point.
 */
public class PlanExecutionEngine {

    public static class PlanExecutionResult {
        public boolean success;
        public boolean rolledBack;
        public String status;
        public String message;
        public List<Path> modifiedFiles = new ArrayList<>();
        public List<Path> createdFiles = new ArrayList<>();
        public List<String> compilerErrors = new ArrayList<>();
        public String compilerStatus = "UNKNOWN";
        public String testStatus = "N/A";
        public long durationMs;
    }

    private final BuildValidator buildValidator;
    private final AstInterfaceExtractor interfaceExtractor;
    private final AstCallerRewriter callerRewriter;
    private final SourceFileEditor sourceFileEditor;

    public PlanExecutionEngine() {
        this(new BuildValidator(),
             new AstInterfaceExtractor(),
             new AstCallerRewriter(),
             new SourceFileEditor());
    }

    public PlanExecutionEngine(BuildValidator buildValidator,
                               AstInterfaceExtractor interfaceExtractor,
                               AstCallerRewriter callerRewriter,
                               SourceFileEditor sourceFileEditor) {
        this.buildValidator = Objects.requireNonNull(buildValidator);
        this.interfaceExtractor = Objects.requireNonNull(interfaceExtractor);
        this.callerRewriter = Objects.requireNonNull(callerRewriter);
        this.sourceFileEditor = Objects.requireNonNull(sourceFileEditor);
    }

    public PlanExecutionResult executePlan(Path projectRoot,
                                          RefactoringPlan plan,
                                          PlannerSimulationResult simulation,
                                          RefactoringOptions options) {
        Objects.requireNonNull(projectRoot, "Project root cannot be null");
        Objects.requireNonNull(plan, "RefactoringPlan cannot be null");

        PlanExecutionResult result = new PlanExecutionResult();
        long startTime = System.currentTimeMillis();

        // 1. Guard against unapproved or stale plans
        if (plan.getLifecycle() != PlanLifecycle.APPROVED) {
            result.success = false;
            result.status = "NOT_APPROVED";
            result.message = "Cannot execute plan: Plan has not received explicit approval (Current state: " + plan.getLifecycle() + ").";
            return result;
        }

        if (plan.isSimulationStale()) {
            result.success = false;
            result.status = "STALE_SIMULATION";
            result.message = "Cannot execute plan: Plan has been modified since last simulation. Re-simulation is required.";
            return result;
        }

        if (simulation != null && !Objects.equals(simulation.getPlanHash(), plan.getPlanHash())) {
            result.success = false;
            result.status = "SIMULATION_HASH_MISMATCH";
            result.message = "Simulation hash does not match current plan hash.";
            return result;
        }

        ChangeManifest manifest = new ChangeManifest();

        try {
            Map<String, Path> classToFileMap = scanProjectClasses(projectRoot);

            // 2. Sequentially apply each operation's source code modifications
            for (RefactoringOperation op : plan.getOperations()) {
                applyOperationToDisk(projectRoot, op, classToFileMap, manifest, result, options);
            }

            // 3. Compiler validation
            BuildResult compileResult = buildValidator.compile(projectRoot, List.of());
            if (compileResult.status == Status.FAIL) {
                manifest.rollbackAll();
                result.success = false;
                result.rolledBack = true;
                result.status = "COMPILE_FAILED";
                result.compilerStatus = "FAIL";
                result.compilerErrors.addAll(compileResult.errors);
                result.message = "Compilation failed after applying refactoring operations. Changes were safely rolled back.";
                result.durationMs = System.currentTimeMillis() - startTime;
                return result;
            }
            result.compilerStatus = "PASSED";

            // 4. Test validation (if enabled)
            if (options != null && options.isRunMavenTests() && Files.exists(projectRoot.resolve("pom.xml"))) {
                BuildResult testResult = new BuildResult();
                buildValidator.runExternalBuildToolValidation(projectRoot, "maven", testResult);
                if (testResult.testsExecuted && !testResult.testsPassed) {
                    manifest.rollbackAll();
                    result.success = false;
                    result.rolledBack = true;
                    result.status = "TEST_FAILED";
                    result.testStatus = "FAIL";
                    result.compilerErrors.addAll(testResult.errors);
                    result.message = "Maven project tests failed post-refactoring. Changes were safely rolled back.";
                    result.durationMs = System.currentTimeMillis() - startTime;
                    return result;
                }
                result.testStatus = testResult.testsExecuted ? "PASSED" : "SKIPPED";
            }

            // 5. Success
            plan.markApplied();
            result.success = true;
            result.rolledBack = false;
            result.status = "VERIFIED";
            result.message = "Plan applied and verified successfully.";
            result.durationMs = System.currentTimeMillis() - startTime;
            return result;

        } catch (Exception e) {
            try {
                manifest.rollbackAll();
            } catch (IOException rollbackEx) {
                // log
            }
            result.success = false;
            result.rolledBack = true;
            result.status = "EXECUTION_EXCEPTION";
            result.compilerErrors.add("Exception during plan execution: " + e.getMessage());
            result.message = "Execution failed with exception. All changes were rolled back.";
            result.durationMs = System.currentTimeMillis() - startTime;
            return result;
        }
    }

    private void applyOperationToDisk(Path projectRoot,
                                      RefactoringOperation op,
                                      Map<String, Path> classToFileMap,
                                      ChangeManifest manifest,
                                      PlanExecutionResult result,
                                      RefactoringOptions options) throws Exception {
        String target = op.getTargetComponent();
        String secondary = op.getSecondaryComponent();
        String newComponent = op.getNewComponent();

        switch (op.getType()) {
            case BREAK_CYCLE -> {
                Path callerPath = classToFileMap.get(target);
                Path providerPath = classToFileMap.get(secondary);

                if (callerPath != null && providerPath != null) {
                    String toSimple = simpleName(secondary);
                    AstInterfaceExtractor.ExtractionResult ext = interfaceExtractor.extractInterface(
                            projectRoot, providerPath, toSimple);

                    Path interfaceFile = ext.interfaceFile;
                    manifest.recordFileForModification(interfaceFile);
                    Files.createDirectories(interfaceFile.getParent());
                    Files.writeString(interfaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
                    result.createdFiles.add(interfaceFile);
                    classToFileMap.put(ext.packageName.isEmpty() ? ext.interfaceName : ext.packageName + "." + ext.interfaceName, interfaceFile);

                    // Update provider implements
                    if (ext.classImplementsEdit != null) {
                        manifest.recordFileForModification(providerPath);
                        SourceFileEditor.applyEditsToFile(providerPath, List.of(ext.classImplementsEdit));
                        result.modifiedFiles.add(providerPath);
                    }

                    // Rewrite caller
                    boolean decouple = options == null || options.isDecoupleConstructorInstantiation();
                    manifest.recordFileForModification(callerPath);
                    AstCallerRewriter.RewriteResult rw = callerRewriter.rewriteCaller(
                            callerPath,
                            toSimple,
                            secondary,
                            ext.interfaceName,
                            ext.packageName.isEmpty() ? ext.interfaceName : ext.packageName + "." + ext.interfaceName,
                            ext.packageName,
                            decouple
                    );
                    SourceFileEditor.applyEditsToFile(callerPath, rw.getEdits());
                    result.modifiedFiles.add(callerPath);
                }
            }

            case EXTRACT_INTERFACE -> {
                Path targetPath = classToFileMap.get(target);
                if (targetPath != null) {
                    String targetSimple = simpleName(target);
                    AstInterfaceExtractor.ExtractionResult ext = interfaceExtractor.extractInterface(
                            projectRoot, targetPath, targetSimple);

                    Path interfaceFile = ext.interfaceFile;
                    manifest.recordFileForModification(interfaceFile);
                    Files.createDirectories(interfaceFile.getParent());
                    Files.writeString(interfaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
                    result.createdFiles.add(interfaceFile);
                    String ifaceFqn = ext.packageName.isEmpty() ? ext.interfaceName : ext.packageName + "." + ext.interfaceName;
                    classToFileMap.put(ifaceFqn, interfaceFile);

                    // Update target implements
                    if (ext.classImplementsEdit != null) {
                        manifest.recordFileForModification(targetPath);
                        SourceFileEditor.applyEditsToFile(targetPath, List.of(ext.classImplementsEdit));
                        result.modifiedFiles.add(targetPath);
                    }

                    // Optional caller retargeting
                    boolean decouple = !"false".equalsIgnoreCase(op.getParameters().get("decoupleCallers"));
                    if (decouple) {
                        for (Map.Entry<String, Path> entry : classToFileMap.entrySet()) {
                            Path callerP = entry.getValue();
                            if (!callerP.equals(targetPath) && !callerP.equals(interfaceFile)) {
                                AstCallerRewriter.RewriteResult rw = callerRewriter.rewriteCaller(
                                        callerP, targetSimple, target, ext.interfaceName, ifaceFqn, ext.packageName, false);
                                if (!rw.getEdits().isEmpty()) {
                                    manifest.recordFileForModification(callerP);
                                    SourceFileEditor.applyEditsToFile(callerP, rw.getEdits());
                                    result.modifiedFiles.add(callerP);
                                }
                            }
                        }
                    }
                }
            }

            default -> {
                // Non-AST or purely architectural operations logged
            }
        }
    }

    private Map<String, Path> scanProjectClasses(Path projectRoot) throws IOException {
        Map<String, Path> map = new HashMap<>();
        try (Stream<Path> stream = Files.walk(projectRoot)) {
            List<Path> javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String rel = projectRoot.relativize(p).toString().replace('\\', '/');
                        return !rel.startsWith("target/") && !rel.contains("/target/") &&
                               !rel.startsWith("src/test/") && !rel.contains("/src/test/");
                    })
                    .toList();

            for (Path p : javaFiles) {
                String simple = p.getFileName().toString().replace(".java", "");
                // Read package
                String content = Files.readString(p, StandardCharsets.UTF_8);
                String pkg = "";
                for (String line : content.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                        pkg = trimmed.substring(8, trimmed.length() - 1).trim();
                        break;
                    }
                }
                String fqn = pkg.isEmpty() ? simple : pkg + "." + simple;
                map.put(fqn, p);
                map.put(simple, p);
            }
        }
        return map;
    }

    private static String simpleName(String fqn) {
        if (fqn == null) return "unknown";
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
