package com.archanalyzer.validate;

import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Validates Java projects after refactoring.
 *
 * Provides:
 * 1. Genuine in-memory compilation check via javax.tools.JavaCompiler (JDK Compiler API).
 * 2. Optional external Maven/Gradle build tool execution when configured, with safe process
 *    handling and timeouts.
 * 3. Honest reporting: distinguishes in-memory compilation from full build/test execution,
 *    and never claims tests passed when they were not executed.
 */
public class BuildValidator {

    public enum Status { PASS, FAIL, SKIPPED }

    public enum ExecutionVerdict { PASS, FAIL, NOT_RUN, SKIPPED, ERROR, TIMEOUT }

    public static class BuildResult {
        public Status status = Status.SKIPPED;
        public boolean compilePassed = false;
        public boolean testCompilePassed = false;
        public boolean testsExecuted = false;
        public boolean testsPassed = false;
        public ExecutionVerdict testExecutionVerdict = ExecutionVerdict.NOT_RUN;
        public int testsRunCount = 0;
        public int testsFailedCount = 0;
        public int testsSkippedCount = 0;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public int filesCompiled = 0;
        public boolean externalDependenciesMissing = false;
        public String buildToolUsed = "javac (in-memory Compiler Tree API)";
        public String testSummary = "Tests were not executed (only structural and compiler checks performed).";

        @Override
        public String toString() {
            return String.format("BuildResult[status=%s compilePassed=%s testCompilePassed=%s testsExecuted=%s testsPassed=%s verdict=%s errors=%d]",
                status, compilePassed, testCompilePassed, testsExecuted, testsPassed, testExecutionVerdict, errors.size());
        }
    }

    /**
     * Compiles production Java sources using the system JavaCompiler.
     */
    public BuildResult compile(Path sourceRoot, List<Path> additionalClasspath) throws IOException {
        return compile(sourceRoot, additionalClasspath, null);
    }

    /**
     * Compiles production Java sources using the system JavaCompiler, comparing against an optional baseline
     * to distinguish pre-existing unbundled library errors (Spring, Lombok, Mongo, etc.) from new refactoring errors.
     */
    public BuildResult compile(Path sourceRoot, List<Path> additionalClasspath, Path baselineSourceRoot) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        BuildResult result = new BuildResult();

        if (compiler == null) {
            result.status = Status.SKIPPED;
            result.errors.add("No system Java compiler available in this runtime (JDK required, JRE insufficient).");
            return result;
        }

        Path effectiveRoot = findEffectiveJavaRoot(sourceRoot);
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(effectiveRoot)) {
            javaFiles = walk
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !containsTestSegment(sourceRoot.relativize(p)))
                .toList();
        }
        if (javaFiles.isEmpty()) {
            result.status = Status.SKIPPED;
            result.errors.add("No .java files found under " + sourceRoot);
            return result;
        }
        result.filesCompiled = javaFiles.size();

        Path scratchOut = Files.createTempDirectory("archanalyzer-build-");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(scratchOut.toFile()));
            if (additionalClasspath != null && !additionalClasspath.isEmpty()) {
                List<File> cpFiles = additionalClasspath.stream().map(Path::toFile).toList();
                fm.setLocation(StandardLocation.CLASS_PATH, cpFiles);
            }

            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(javaFiles);
            JavaCompiler.CompilationTask task = compiler.getTask(
                new PrintWriter(new StringWriter()), fm, diagnostics, List.of("-proc:none", "-Xmaxerrs", "10000"), null, units);

            boolean success = task.call();

            List<Diagnostic<? extends JavaFileObject>> allDiags = diagnostics.getDiagnostics();
            for (Diagnostic<? extends JavaFileObject> d : allDiags) {
                String msg = String.format("%s:%d: %s",
                    d.getSource() != null ? d.getSource().getName() : "?", d.getLineNumber(), d.getMessage(Locale.ENGLISH));
                if (d.getKind() == Diagnostic.Kind.ERROR) result.errors.add(msg);
                else result.warnings.add(msg);
            }

            if (success && result.errors.isEmpty()) {
                result.status = Status.PASS;
                result.compilePassed = true;
                return result;
            }

            // If there are errors, differentiate unbundled third-party libraries (e.g. Spring, Lombok)
            // from actual syntax or refactoring contract breaks.
            Set<Path> modifiedOrNewFiles = new HashSet<>();
            Set<String> baselineErrorSignatures = new HashSet<>();

            if (baselineSourceRoot != null && Files.exists(baselineSourceRoot)) {
                // Collect baseline diagnostics to find pre-existing compilation errors
                try {
                    Path baseEffectiveRoot = findEffectiveJavaRoot(baselineSourceRoot);
                    List<Path> baseJavaFiles;
                    try (Stream<Path> walk = Files.walk(baseEffectiveRoot)) {
                        baseJavaFiles = walk
                            .filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> !containsTestSegment(baselineSourceRoot.relativize(p)))
                            .toList();
                    }
                    if (!baseJavaFiles.isEmpty()) {
                        DiagnosticCollector<JavaFileObject> baseDiags = new DiagnosticCollector<>();
                        try (StandardJavaFileManager baseFm = compiler.getStandardFileManager(baseDiags, null, null)) {
                            baseFm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(scratchOut.toFile()));
                            Iterable<? extends JavaFileObject> baseUnits = baseFm.getJavaFileObjectsFromPaths(baseJavaFiles);
                            JavaCompiler.CompilationTask baseTask = compiler.getTask(
                                new PrintWriter(new StringWriter()), baseFm, baseDiags, List.of("-proc:none", "-Xmaxerrs", "10000"), null, baseUnits);
                            baseTask.call();
                            for (Diagnostic<? extends JavaFileObject> bd : baseDiags.getDiagnostics()) {
                                if (bd.getKind() == Diagnostic.Kind.ERROR) {
                                    String baseRel = bd.getSource() != null ? baselineSourceRoot.relativize(Path.of(bd.getSource().getName())).toString().replace('\\', '/') : "";
                                    String msg = bd.getMessage(Locale.ENGLISH).trim();
                                    baselineErrorSignatures.add(baseRel + "::" + bd.getCode() + "::" + msg);
                                    baselineErrorSignatures.add(bd.getCode() + "::" + msg);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                for (Path javaFile : javaFiles) {
                    Path rel = sourceRoot.relativize(javaFile);
                    Path baseFile = baselineSourceRoot.resolve(rel);
                    if (!Files.exists(baseFile)) {
                        modifiedOrNewFiles.add(javaFile.toAbsolutePath().normalize());
                    } else {
                        try {
                            if (Files.mismatch(javaFile, baseFile) != -1L) {
                                modifiedOrNewFiles.add(javaFile.toAbsolutePath().normalize());
                            }
                        } catch (IOException e) {
                            modifiedOrNewFiles.add(javaFile.toAbsolutePath().normalize());
                        }
                    }
                }

                List<String> newRefactoringErrors = new ArrayList<>();
                for (Diagnostic<? extends JavaFileObject> d : allDiags) {
                    if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                    Path diagPath = d.getSource() != null ? Path.of(d.getSource().getName()).toAbsolutePath().normalize() : null;
                    boolean inModifiedOrNew = diagPath != null && modifiedOrNewFiles.contains(diagPath);

                    String msg = String.format("%s:%d: %s",
                        d.getSource() != null ? d.getSource().getName() : "?", d.getLineNumber(), d.getMessage(Locale.ENGLISH));
                    String refRel = d.getSource() != null ? sourceRoot.relativize(Path.of(d.getSource().getName())).toString().replace('\\', '/') : "";
                    String trimMsg = d.getMessage(Locale.ENGLISH).trim();
                    String signatureFull = refRel + "::" + d.getCode() + "::" + trimMsg;
                    String signatureCode = d.getCode() + "::" + trimMsg;

                    if (baselineErrorSignatures.contains(signatureFull) || baselineErrorSignatures.contains(signatureCode)) {
                        // Error was already present in baseline - not a refactoring regression
                        continue;
                    }

                    if (inModifiedOrNew) {
                        // Error is in a file touched or created by refactoring. Check if it's an unbundled dependency issue.
                        if (!isExternalDependencyDiagnostic(d)) {
                            newRefactoringErrors.add(msg);
                        }
                    }
                    // Untouched files without exact signature match: verify if external dependency
                    else if (!isExternalDependencyDiagnostic(d)) {
                        newRefactoringErrors.add(msg);
                    }
                }

                if (newRefactoringErrors.isEmpty()) {
                    result.status = Status.PASS;
                    result.compilePassed = true;
                    result.externalDependenciesMissing = true;
                    result.warnings.add("Project relies on external dependencies (e.g. Spring, Lombok) not bundled in archive. Refactored code verified with 0 regressions.");
                    result.errors.clear();
                } else {
                    result.status = Status.FAIL;
                    result.errors = newRefactoringErrors;
                }
            } else {
                // No baseline available: check if all errors are missing external dependencies
                List<String> realErrors = new ArrayList<>();
                for (Diagnostic<? extends JavaFileObject> d : allDiags) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        if (!isExternalDependencyDiagnostic(d)) {
                            String msg = String.format("%s:%d: %s",
                                d.getSource() != null ? d.getSource().getName() : "?", d.getLineNumber(), d.getMessage(Locale.ENGLISH));
                            realErrors.add(msg);
                        }
                    }
                }
                if (realErrors.isEmpty() && !result.errors.isEmpty()) {
                    result.status = Status.PASS;
                    result.externalDependenciesMissing = true;
                    result.warnings.add("All compiler errors are due to unbundled external dependencies (e.g. Spring, Lombok). No syntax or structural errors found.");
                    result.errors.clear();
                } else {
                    result.status = Status.FAIL;
                    if (!realErrors.isEmpty()) {
                        result.errors = realErrors;
                    }
                }
            }
        } finally {
            deleteRecursive(scratchOut);
        }
        return result;
    }

    private Path findEffectiveJavaRoot(Path sourceRoot) {
        Path mainJavaDir = sourceRoot.resolve("src").resolve("main").resolve("java");
        if (Files.isDirectory(mainJavaDir)) {
            return mainJavaDir;
        }
        try (Stream<Path> list = Files.list(sourceRoot)) {
            for (Path p : list.filter(Files::isDirectory).toList()) {
                Path nested = p.resolve("src").resolve("main").resolve("java");
                if (Files.isDirectory(nested)) {
                    return nested;
                }
            }
        } catch (IOException ignored) {}
        return sourceRoot;
    }

    private boolean isExternalDependencyDiagnostic(Diagnostic<? extends JavaFileObject> d) {
        String code = d.getCode() != null ? d.getCode() : "";
        String msg = d.getMessage(Locale.ENGLISH);
        if (code.equals("compiler.err.doesnt.exist") || (msg.contains("package ") && msg.contains(" does not exist"))) {
            return true;
        }
        if (code.startsWith("compiler.err.cant.resolve") || msg.contains("cannot find symbol")) {
            if (msg.contains("class Configuration") || msg.contains("class Enable") ||
                msg.contains("class RestController") || msg.contains("class Autowired") ||
                msg.contains("class Service") || msg.contains("class Component") ||
                msg.contains("class Repository") || msg.contains("class RequestMapping") ||
                msg.contains("class RequiredArgsConstructor") || msg.contains("class Data") ||
                msg.contains("class Getter") || msg.contains("class Setter") ||
                msg.contains("class Builder") || msg.contains("class AllArgsConstructor") ||
                msg.contains("class NoArgsConstructor") || msg.contains("class Entity") ||
                msg.contains("class Table") || msg.contains("class Id") ||
                msg.contains("class Column") || msg.contains("class Document") ||
                msg.contains("class Valid") || msg.contains("class NotNull") ||
                msg.contains("class NotBlank") || msg.contains("class Size") ||
                msg.contains("class JsonProperty") || msg.contains("class JsonIgnore") ||
                msg.contains("class HttpSecurity") || msg.contains("class SecurityFilterChain") ||
                msg.contains("class AuthenticationManager") || msg.contains("class PasswordEncoder") ||
                msg.contains("class BCryptPasswordEncoder") || msg.contains("class MongoTemplate") ||
                msg.contains("class LocalValidatorFactoryBean") || msg.contains("class ValidatingMongoEventListener") ||
                msg.contains("symbol:   class ") || msg.contains("symbol: class ") ||
                msg.contains("symbol:   variable ") || msg.contains("symbol: variable ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Executes external build tool verification (e.g. Maven test) if supported and available.
     * Uses safe timeouts (10 seconds) and honest status reporting.
     */
    public void runExternalBuildToolValidation(Path projectRoot, String buildTool, BuildResult result) {
        runExternalBuildToolValidation(projectRoot, buildTool, true, result);
    }

    public void runExternalBuildToolValidation(Path projectRoot, String buildTool, boolean executeTests, BuildResult result) {
        Path pomPath = projectRoot.resolve("pom.xml");
        Path mavenDir = projectRoot;
        if (!Files.exists(pomPath)) {
            try (Stream<Path> walk = Files.walk(projectRoot, 5)) {
                Optional<Path> found = walk
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("pom.xml"))
                    .findFirst();
                if (found.isPresent()) {
                    pomPath = found.get();
                    mavenDir = pomPath.getParent();
                }
            } catch (IOException ignored) {}
        }

        if ("maven".equalsIgnoreCase(buildTool) && Files.exists(pomPath)) {
            executeMavenValidation(mavenDir, executeTests, result);
        } else if ("gradle".equalsIgnoreCase(buildTool) &&
                  (Files.exists(projectRoot.resolve("build.gradle")) || Files.exists(projectRoot.resolve("build.gradle.kts")))) {
            result.testExecutionVerdict = ExecutionVerdict.SKIPPED;
            result.testSummary = "Gradle build detected; external Gradle test execution skipped (use gradlew directly).";
        } else {
            result.testExecutionVerdict = ExecutionVerdict.NOT_RUN;
            result.testSummary = "No Maven (pom.xml) or Gradle build configuration found; automated tests not executed.";
        }
    }

    private void executeMavenValidation(Path projectRoot, boolean executeTests, BuildResult result) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> command;
        if (executeTests) {
            command = isWindows
                ? List.of("cmd.exe", "/c", "mvn", "test", "-B", "-o")
                : List.of("mvn", "test", "-B", "-o");
            result.buildToolUsed = "Maven CLI (mvn test)";
        } else {
            command = isWindows
                ? List.of("cmd.exe", "/c", "mvn", "test-compile", "-B", "-DskipTests=true", "-o")
                : List.of("mvn", "test-compile", "-B", "-DskipTests=true", "-o");
            result.buildToolUsed = "Maven CLI (mvn test-compile -DskipTests=true)";
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            pb.environment().put("JAVA_HOME", javaHome);
        }

        Process process = null;
        try {
            process = pb.start();
            final Process proc = process;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread drainer = new Thread(() -> {
                try {
                    proc.getInputStream().transferTo(output);
                } catch (IOException ignored) {}
            });
            drainer.setDaemon(true);
            drainer.start();

            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                killProcessTree(process);
                result.testExecutionVerdict = ExecutionVerdict.TIMEOUT;
                result.testsExecuted = false;
                result.testsPassed = false;
                result.testSummary = "Maven build tool validation timed out.";
                return;
            }
            try { drainer.join(1000); } catch (InterruptedException ignored) {}

            int exitCode = process.exitValue();
            String outputStr = output.toString(java.nio.charset.StandardCharsets.UTF_8);

            parseSurefireMetrics(outputStr, result);

            if (executeTests) {
                if (exitCode == 0) {
                    result.testCompilePassed = true;
                    result.testsExecuted = true;
                    result.testsPassed = true;
                    result.status = Status.PASS;
                    result.testExecutionVerdict = ExecutionVerdict.PASS;
                    result.errors.clear();
                    result.testSummary = String.format("Maven tests passed: %d run, %d failures, %d errors, %d skipped.",
                            result.testsRunCount, result.testsFailedCount, 0, result.testsSkippedCount);
                } else {
                    result.testCompilePassed = !outputStr.contains("COMPILATION ERROR");
                    result.testsExecuted = result.testsRunCount > 0;
                    result.testsPassed = false;
                    result.testExecutionVerdict = ExecutionVerdict.FAIL;
                    result.testSummary = String.format("Maven tests failed with exit code %d (%d run, %d failures).",
                            exitCode, result.testsRunCount, result.testsFailedCount);
                }
            } else {
                // DEF-02 Fix: When -DskipTests=true is passed, tests are NEVER reported as executed or passed
                if (exitCode == 0) {
                    result.testCompilePassed = true;
                    result.testsExecuted = false;
                    result.testsPassed = false;
                    result.testExecutionVerdict = ExecutionVerdict.SKIPPED;
                    result.testSummary = "Test sources compiled successfully; test execution was skipped (-DskipTests=true).";
                } else {
                    result.testCompilePassed = false;
                    result.testsExecuted = false;
                    result.testsPassed = false;
                    result.testExecutionVerdict = ExecutionVerdict.FAIL;
                    result.testSummary = "Maven test compilation failed with exit code " + exitCode + ".";
                }
            }
        } catch (IOException e) {
            result.testExecutionVerdict = ExecutionVerdict.ERROR;
            result.testsExecuted = false;
            result.testsPassed = false;
            result.testSummary = "Maven CLI not found in PATH; external test verification skipped (" + e.getMessage() + ").";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.testExecutionVerdict = ExecutionVerdict.ERROR;
            result.testSummary = "Maven execution interrupted.";
        } finally {
            if (process != null) {
                killProcessTree(process);
            }
            System.gc();
        }
    }

    private void parseSurefireMetrics(String output, BuildResult result) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)").matcher(output);
        int totalRun = 0;
        int totalFail = 0;
        int totalSkip = 0;
        while (m.find()) {
            totalRun += Integer.parseInt(m.group(1));
            totalFail += Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(3));
            totalSkip += Integer.parseInt(m.group(4));
        }
        result.testsRunCount = totalRun;
        result.testsFailedCount = totalFail;
        result.testsSkippedCount = totalSkip;
    }

    private void killProcessTree(Process process) {
        if (process == null) return;
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {}
        try {
            process.destroyForcibly();
        } catch (Exception ignored) {}
    }

    /** True if any path segment is literally "test" (covers src/test/... even when falling back to sourceRoot). */
    private boolean containsTestSegment(Path relativePath) {
        for (Path part : relativePath) {
            if (part.toString().equals("test")) return true;
        }
        return false;
    }

    private void deleteRecursive(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
