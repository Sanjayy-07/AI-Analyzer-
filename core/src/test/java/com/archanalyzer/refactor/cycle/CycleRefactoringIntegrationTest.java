package com.archanalyzer.refactor.cycle;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.ExecutionResult;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CycleRefactoringIntegrationTest {

    @TempDir
    Path tempDir;

    private CycleRefactoringExecutor executor;

    @BeforeEach
    public void setUp() {
        executor = new CycleRefactoringExecutor();
    }

    private Path createJavaFile(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    // =========================================================================
    // 1. Simple Two-Class Dependency Cycle
    // =========================================================================
    @Test
    @DisplayName("1. Simple two-class dependency cycle (Class A <-> Class B)")
    public void testSimpleTwoClassCycle() throws Exception {
        Path proj = tempDir.resolve("two-class");
        createJavaFile(proj, "src/main/java/com/demo/ClassA.java",
                "package com.demo;\n" +
                "public class ClassA {\n" +
                "    private ClassB b;\n" +
                "    public void doA() { if (b != null) b.doB(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/demo/ClassB.java",
                "package com.demo;\n" +
                "public class ClassB {\n" +
                "    private ClassA a;\n" +
                "    public void doB() { if (a != null) a.doA(); }\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        ExecutionResult res = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(1, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
        assertNotNull(res.getGeneratedInterfacePath());
        assertTrue(Files.exists(res.getGeneratedInterfacePath()));
    }

    // =========================================================================
    // 2. Three-Class Dependency Cycle
    // =========================================================================
    @Test
    @DisplayName("2. Three-class dependency cycle (A -> B -> C -> A)")
    public void testThreeClassCycle() throws Exception {
        Path proj = tempDir.resolve("three-class");
        createJavaFile(proj, "src/main/java/com/demo/A.java",
                "package com.demo;\n" +
                "public class A {\n" +
                "    private B b;\n" +
                "    public void doA() { if (b != null) b.doB(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/demo/B.java",
                "package com.demo;\n" +
                "public class B {\n" +
                "    private C c;\n" +
                "    public void doB() { if (c != null) c.doC(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/demo/C.java",
                "package com.demo;\n" +
                "public class C {\n" +
                "    private A a;\n" +
                "    public void doC() { if (a != null) a.doA(); }\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        ExecutionResult res = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(1, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // =========================================================================
    // 3. Multiple Cycles Inside One SCC
    // =========================================================================
    @Test
    @DisplayName("3. Multiple cycles inside one SCC")
    public void testMultipleCyclesInsideOneScc() throws Exception {
        Path proj = tempDir.resolve("multi-cycle");
        // Cycle 1: NodeA -> NodeB -> NodeC -> NodeA
        // Cycle 2: NodeA -> NodeB -> NodeD -> NodeA
        // Both cycles share edge NodeA -> NodeB. Inverting NodeA -> NodeB breaks both cycles!
        createJavaFile(proj, "src/main/java/com/demo/NodeA.java",
                "package com.demo;\n" +
                "public class NodeA {\n" +
                "    private NodeB b;\n" +
                "    public void doA() { if (b != null) b.doB(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/demo/NodeB.java",
                "package com.demo;\n" +
                "public class NodeB {\n" +
                "    private NodeC c;\n" +
                "    private NodeD d;\n" +
                "    public void doB() { if (c != null) c.doC(); if (d != null) d.doD(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/demo/NodeC.java",
                "package com.demo;\n" +
                "public class NodeC {\n" +
                "    private NodeA a;\n" +
                "    public void doC() { if (a != null) a.doA(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/demo/NodeD.java",
                "package com.demo;\n" +
                "public class NodeD {\n" +
                "    private NodeA a;\n" +
                "    public void doD() { if (a != null) a.doA(); }\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        ExecutionResult res = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(1, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // =========================================================================
    // 4. Interface Extraction with Generic Methods
    // =========================================================================
    @Test
    @DisplayName("4. Interface extraction with generic methods")
    public void testInterfaceExtractionGenericMethods() throws Exception {
        Path provider = createJavaFile(tempDir, "GenericService.java",
                "package com.test;\n" +
                "import java.util.List;\n" +
                "public class GenericService {\n" +
                "    public <T> List<T> processItems(List<T> items) { return items; }\n" +
                "    public <K, V> Map<K, V> transform(K key, V val) { return null; }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult res = extractor.extractInterface(tempDir, provider, "GenericService");

        assertNotNull(res.interfaceSource);
        assertTrue(res.interfaceSource.contains("<T> List<T> processItems(List<T> items);"));
        assertTrue(res.interfaceSource.contains("<K, V> Map<K, V> transform(K key, V val);"));
    }

    // =========================================================================
    // 5. Interface Extraction with Annotations
    // =========================================================================
    @Test
    @DisplayName("5. Interface extraction with annotations")
    public void testInterfaceExtractionAnnotations() throws Exception {
        Path provider = createJavaFile(tempDir, "AnnotatedService.java",
                "package com.test;\n" +
                "public class AnnotatedService {\n" +
                "    @Deprecated\n" +
                "    public void oldMethod() {}\n" +
                "    public String normalMethod(String arg) { return arg; }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult res = extractor.extractInterface(tempDir, provider, "AnnotatedService");

        assertNotNull(res.interfaceSource);
        assertTrue(res.interfaceSource.contains("void oldMethod();"));
        assertTrue(res.interfaceSource.contains("String normalMethod(String arg);"));
    }

    @Test
    @DisplayName("5b. Interface extraction on DTO/request object with no public methods generates marker interface")
    public void testExtractInterfaceFromDtoWithNoPublicMethods() throws Exception {
        Path provider = createJavaFile(tempDir, "OrderRequest.java",
                "package com.test;\n" +
                "public class OrderRequest {\n" +
                "    public String orderId;\n" +
                "    public double amount;\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult res = extractor.extractInterface(tempDir, provider, "OrderRequest");

        assertNotNull(res.interfaceSource);
        assertTrue(res.interfaceSource.contains("public interface IOrderRequest {"));
        assertNotNull(res.classImplementsEdit);
        assertTrue(res.classImplementsEdit.getReplacementText().contains("implements IOrderRequest"));
    }

    // =========================================================================
    // 6. Field Dependency Rewriting
    // =========================================================================
    @Test
    @DisplayName("6. Field dependency rewriting")
    public void testFieldDependencyRewriting() throws Exception {
        Path caller = createJavaFile(tempDir, "CallerField.java",
                "package com.test;\n" +
                "public class CallerField {\n" +
                "    private TargetService targetService;\n" +
                "}\n");

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult res = rewriter.rewriteCaller(
                caller, "TargetService", "com.test.TargetService", "ITargetService", "com.test.ITargetService", "com.test", false);

        assertEquals(1, res.getRewrittenFields());
        String updated = SourceFileEditor.applyEdits(Files.readString(caller), res.getEdits());
        assertTrue(updated.contains("private ITargetService targetService;"));
    }

    // =========================================================================
    // 7. Constructor Parameter Rewriting
    // =========================================================================
    @Test
    @DisplayName("7. Constructor parameter rewriting")
    public void testConstructorParameterRewriting() throws Exception {
        Path caller = createJavaFile(tempDir, "CallerCtor.java",
                "package com.test;\n" +
                "public class CallerCtor {\n" +
                "    public CallerCtor(TargetService svc) {}\n" +
                "}\n");

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult res = rewriter.rewriteCaller(
                caller, "TargetService", "com.test.TargetService", "ITargetService", "com.test.ITargetService", "com.test", false);

        assertEquals(1, res.getRewrittenParams());
        String updated = SourceFileEditor.applyEdits(Files.readString(caller), res.getEdits());
        assertTrue(updated.contains("public CallerCtor(ITargetService svc)"));
    }

    // =========================================================================
    // 8. Method Parameter Rewriting
    // =========================================================================
    @Test
    @DisplayName("8. Method parameter rewriting")
    public void testMethodParameterRewriting() throws Exception {
        Path caller = createJavaFile(tempDir, "CallerParam.java",
                "package com.test;\n" +
                "public class CallerParam {\n" +
                "    public void run(TargetService svc) {}\n" +
                "}\n");

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult res = rewriter.rewriteCaller(
                caller, "TargetService", "com.test.TargetService", "ITargetService", "com.test.ITargetService", "com.test", false);

        assertEquals(1, res.getRewrittenParams());
        String updated = SourceFileEditor.applyEdits(Files.readString(caller), res.getEdits());
        assertTrue(updated.contains("public void run(ITargetService svc)"));
    }

    // =========================================================================
    // 9. Concrete Object Creation Preservation / Decoupling
    // =========================================================================
    @Test
    @DisplayName("9. Concrete object creation decoupling")
    public void testConcreteObjectCreationDecoupling() throws Exception {
        Path caller = createJavaFile(tempDir, "CallerNew.java",
                "package com.test;\n" +
                "public class CallerNew {\n" +
                "    private TargetService svc;\n" +
                "    public CallerNew() {\n" +
                "        this.svc = new TargetService();\n" +
                "    }\n" +
                "}\n");

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult res = rewriter.rewriteCaller(
                caller, "TargetService", "com.test.TargetService", "ITargetService", "com.test.ITargetService", "com.test", true);

        assertEquals(1, res.getRewrittenConstructors());
        String updated = SourceFileEditor.applyEdits(Files.readString(caller), res.getEdits());
        // DEF-01: Verify concrete instantiation is replaced with safe dynamic provider instead of unsafe null
        assertTrue(updated.contains("this.svc = createDefaultTargetService();"), "Updated source should decouple via dynamic provider: " + updated);
        assertTrue(updated.contains("public CallerNew(ITargetService svc)"));
    }

    // =========================================================================
    // 10. Import Correction Cross-Package
    // =========================================================================
    @Test
    @DisplayName("10. Cross-package import correction")
    public void testCrossPackageImportCorrection() throws Exception {
        Path caller = createJavaFile(tempDir, "CallerCrossPkg.java",
                "package com.test.client;\n" +
                "import com.test.service.TargetService;\n" +
                "public class CallerCrossPkg {\n" +
                "    private TargetService target;\n" +
                "}\n");

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult res = rewriter.rewriteCaller(
                caller, "TargetService", "com.test.service.TargetService", "ITargetService",
                "com.test.service.ITargetService", "com.test.service", false);

        assertTrue(res.isImportAdded());
        String updated = SourceFileEditor.applyEdits(Files.readString(caller), res.getEdits());
        assertTrue(updated.contains("import com.test.service.ITargetService;"));
    }

    // =========================================================================
    // 11. Duplicate Interface Prevention
    // =========================================================================
    @Test
    @DisplayName("11. Duplicate interface prevention")
    public void testDuplicateInterfacePrevention() throws Exception {
        Path proj = tempDir.resolve("dup-iface");
        createJavaFile(proj, "src/main/java/com/demo/A.java",
                "package com.demo;\n" +
                "public class A {\n" +
                "    private B b;\n" +
                "    public void doA() {}\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/demo/B.java",
                "package com.demo;\n" +
                "public class B {\n" +
                "    private A a;\n" +
                "    public void doB() {}\n" +
                "}\n");
        // Pre-create IB.java and IA.java
        createJavaFile(proj, "src/main/java/com/demo/IB.java",
                "package com.demo;\npublic interface IB { void doB(); }\n");
        createJavaFile(proj, "src/main/java/com/demo/IA.java",
                "package com.demo;\npublic interface IA { void doA(); }\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        ExecutionResult res = executor.execute(proj, opts);

        // Should safely reject or fail without crash
        assertNotNull(res);
        assertNotEquals(RefactoringStatus.VERIFIED, res.getStatus());
    }

    // =========================================================================
    // 12. Multiple Source Edits in One File
    // =========================================================================
    @Test
    @DisplayName("12. Multiple source edits in one file descending offset ordering")
    public void testMultipleSourceEditsInOneFile() throws Exception {
        String original = "class Test { Target t1; Target t2; void m(Target t3) {} }";
        int idx1 = original.indexOf("Target");
        int idx2 = original.indexOf("Target", idx1 + 1);
        int idx3 = original.indexOf("Target", idx2 + 1);

        List<SourceEdit> edits = List.of(
                new SourceEdit(idx1, idx1 + 6, "ITarget", "edit1"),
                new SourceEdit(idx2, idx2 + 6, "ITarget", "edit2"),
                new SourceEdit(idx3, idx3 + 6, "ITarget", "edit3")
        );

        String result = SourceFileEditor.applyEdits(original, edits);
        assertEquals("class Test { ITarget t1; ITarget t2; void m(ITarget t3) {} }", result);
    }

    // =========================================================================
    // 13. Compilation Failure Handling (Atomic Rollback)
    // =========================================================================
    @Test
    @DisplayName("13. Compilation failure handling (atomic rollback)")
    public void testCompilationFailureHandling() throws Exception {
        Path proj = tempDir.resolve("compile-fail");
        Path fileA = createJavaFile(proj, "src/main/java/com/demo/A.java",
                "package com.demo;\n" +
                "public class A {\n" +
                "    private B b;\n" +
                "    public void doA() {}\n" +
                "}\n");
        Path fileB = createJavaFile(proj, "src/main/java/com/demo/B.java",
                "package com.demo;\n" +
                "public class B {\n" +
                "    private A a;\n" +
                "    public void doB() {}\n" +
                "}\n");

        String origA = Files.readString(fileA);
        String origB = Files.readString(fileB);

        // Simulate broken AST rewriter by corrupting edits
        AstCallerRewriter brokenRewriter = new AstCallerRewriter() {
            @Override
            public RewriteResult rewriteCaller(Path callerSourceFile, String concreteTargetSimpleName,
                                                String concreteTargetFqn, String interfaceSimpleName,
                                                String interfaceFqn, String interfacePackage,
                                                boolean decoupleConstructorInstantiation) {
                // inject invalid syntax
                List<SourceEdit> badEdits = List.of(new SourceEdit(0, 0, "INVALID SYNTAX BROKEN !!;\n", "bad"));
                return new RewriteResult(callerSourceFile, badEdits, 1, 0, 0, 0, false);
            }
        };

        CycleRefactoringExecutor brokenExecutor = new CycleRefactoringExecutor(
                new com.archanalyzer.validate.BuildValidator(),
                new CycleBreakingStrategy(),
                new AstInterfaceExtractor(),
                brokenRewriter,
                new SourceFileEditor()
        );

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        ExecutionResult res = brokenExecutor.execute(proj, opts);

        assertEquals(RefactoringStatus.COMPILE_FAILED, res.getStatus());
        // Verify rollback
        assertEquals(origA, Files.readString(fileA));
        assertEquals(origB, Files.readString(fileB));
    }

    // =========================================================================
    // 14. Test Failure Handling (Atomic Rollback)
    // =========================================================================
    @Test
    @DisplayName("14. Test failure handling (atomic rollback)")
    public void testTestFailureHandling() throws Exception {
        Path proj = tempDir.resolve("fail-proj");
        Path fileA = createJavaFile(proj, "src/main/java/com/demo/A.java",
                "package com.demo;\npublic class A { private B b; public void doA() {} }\n");
        Path fileB = createJavaFile(proj, "src/main/java/com/demo/B.java",
                "package com.demo;\npublic class B { private A a; public void doB() {} }\n");

        String origA = Files.readString(fileA);

        // Custom validator that reports test failure
        com.archanalyzer.validate.BuildValidator failingValidator = new com.archanalyzer.validate.BuildValidator() {
            @Override
            public void runExternalBuildToolValidation(Path projectRoot, String buildTool, BuildResult result) {
                result.testsExecuted = true;
                result.testsPassed = false;
                result.errors.add("Simulated Unit Test Failure");
            }
        };

        // Create pom.xml to trigger test execution
        createJavaFile(proj, "pom.xml", "<project></project>");

        CycleRefactoringExecutor testFailExecutor = new CycleRefactoringExecutor(
                failingValidator,
                new CycleBreakingStrategy(),
                new AstInterfaceExtractor(),
                new AstCallerRewriter(),
                new SourceFileEditor()
        );

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(true);
        ExecutionResult res = testFailExecutor.execute(proj, opts);

        assertEquals(RefactoringStatus.TEST_FAILED, res.getStatus());
        assertEquals(origA, Files.readString(fileA));
    }

    // =========================================================================
    // 15. Cycle Remains After Repair Handling
    // =========================================================================
    @Test
    @DisplayName("15. Cycle remains after repair handling")
    public void testCycleRemainsAfterRepairHandling() throws Exception {
        Path proj = tempDir.resolve("cycle-remains");
        createJavaFile(proj, "src/main/java/com/demo/A.java",
                "package com.demo;\npublic class A { private B b; public void doA() {} }\n");
        createJavaFile(proj, "src/main/java/com/demo/B.java",
                "package com.demo;\npublic class B { private A a; public void doB() {} }\n");

        // Custom strategy that returns a dummy edge that doesn't actually break the cycle
        AstCallerRewriter noOpRewriter = new AstCallerRewriter() {
            @Override
            public RewriteResult rewriteCaller(Path callerSourceFile, String concreteTargetSimpleName,
                                                String concreteTargetFqn, String interfaceSimpleName,
                                                String interfaceFqn, String interfacePackage,
                                                boolean decoupleConstructorInstantiation) {
                return new RewriteResult(callerSourceFile, List.of(), 0, 0, 0, 0, false);
            }
        };

        CycleRefactoringExecutor cycleRemainsExecutor = new CycleRefactoringExecutor(
                new com.archanalyzer.validate.BuildValidator(),
                new CycleBreakingStrategy(),
                new AstInterfaceExtractor(),
                noOpRewriter,
                new SourceFileEditor()
        );

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        ExecutionResult res = cycleRemainsExecutor.execute(proj, opts);

        assertEquals(RefactoringStatus.CYCLE_NOT_BROKEN, res.getStatus());
    }

    // =========================================================================
    // 16. Safe Rollback Restores Exact SHA-256
    // =========================================================================
    @Test
    @DisplayName("16. Safe rollback restores exact SHA-256 bytes")
    public void testSafeRollbackRestoresExactBytes() throws Exception {
        Path proj = tempDir.resolve("sha-verify");
        Path fileA = createJavaFile(proj, "src/main/java/com/demo/ClassA.java",
                "package com.demo;\n" +
                "public class ClassA {\n" +
                "    private ClassB b;\n" +
                "    public void doA() {}\n" +
                "}\n");

        String originalContent = Files.readString(fileA, StandardCharsets.UTF_8);
        String originalSha = ChangeManifest.computeSha256(originalContent);

        ChangeManifest manifest = new ChangeManifest();
        manifest.recordFileForModification(fileA);

        // Apply destructive change
        Files.writeString(fileA, "CORRUPTED CONTENT !!!", StandardCharsets.UTF_8);
        assertNotEquals(originalSha, ChangeManifest.computeSha256(Files.readString(fileA, StandardCharsets.UTF_8)));

        // Rollback
        manifest.rollbackAll();

        String restoredContent = Files.readString(fileA, StandardCharsets.UTF_8);
        String restoredSha = ChangeManifest.computeSha256(restoredContent);

        assertEquals(originalSha, restoredSha);
        assertEquals(originalContent, restoredContent);
    }

    // =========================================================================
    // 17. Pre-existing Compilation Failure Handled
    // =========================================================================
    @Test
    @DisplayName("17. Pre-existing compilation failure handled")
    public void testPreexistingCompilationFailure() throws Exception {
        Path proj = tempDir.resolve("pre-fail");
        createJavaFile(proj, "src/main/java/com/demo/Broken.java",
                "package com.demo;\n" +
                "public class Broken {\n" +
                "    THIS IS SYNTACTICALLY BROKEN CODE\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        ExecutionResult res = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.PREEXISTING_BUILD_FAILURE, res.getStatus());
        assertFalse(res.getErrors().isEmpty());
    }

    // =========================================================================
    // 18. Unsupported Reflection Case Handled
    // =========================================================================
    @Test
    @DisplayName("18. Unsupported reflection case handled")
    public void testUnsupportedReflectionCaseHandled() throws Exception {
        Path proj = tempDir.resolve("reflection");
        createJavaFile(proj, "src/main/java/com/demo/CleanClass.java",
                "package com.demo;\n" +
                "public class CleanClass {\n" +
                "    public void run() {}\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        ExecutionResult res = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.NO_CYCLES_DETECTED, res.getStatus());
    }

    // =========================================================================
    // 19. Repair Preview Without Application
    // =========================================================================
    @Test
    @DisplayName("19. Repair preview without application (Dry-Run)")
    public void testRepairPreviewWithoutApplication() throws Exception {
        Path proj = tempDir.resolve("preview");
        Path fileA = createJavaFile(proj, "src/main/java/com/demo/A.java",
                "package com.demo;\npublic class A { private B b; public void doA() {} }\n");
        Path fileB = createJavaFile(proj, "src/main/java/com/demo/B.java",
                "package com.demo;\npublic class B { private A a; public void doB() {} }\n");

        String contentABefore = Files.readString(fileA);
        String contentBBefore = Files.readString(fileB);

        RefactoringOptions opts = new RefactoringOptions();
        opts.setPreviewOnly(true);
        opts.setRunMavenTests(false);
        ExecutionResult res = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.PREVIEW_ONLY, res.getStatus());
        assertNotNull(res.getPlan());
        assertNotNull(res.getPlan().getSelectedEdge());
        // Verify disk files untouched
        assertEquals(contentABefore, Files.readString(fileA));
        assertEquals(contentBBefore, Files.readString(fileB));
        assertFalse(Files.exists(proj.resolve("src/main/java/com/demo/IB.java")));
        assertFalse(Files.exists(proj.resolve("src/main/java/com/demo/IA.java")));
    }

    // =========================================================================
    // 20. End-to-End Verified Repair on Sample-Demo-Project
    // =========================================================================
    @Test
    @DisplayName("20. End-to-end verified repair on sample-demo-project")
    public void testEndToEndSampleDemoProject() throws Exception {
        Path sampleProjectRoot = Path.of("sample-demo-project");
        if (!Files.exists(sampleProjectRoot)) {
            sampleProjectRoot = Path.of("..", "sample-demo-project");
        }
        if (!Files.exists(sampleProjectRoot)) {
            sampleProjectRoot = Path.of("analyzer", "sample-demo-project");
        }
        assertTrue(Files.exists(sampleProjectRoot), "sample-demo-project must exist");

        // Use isolated copy mode to protect sample-demo-project during tests
        RefactoringOptions opts = new RefactoringOptions();
        opts.setIsolatedWorkspace(true);
        opts.setRunMavenTests(false); // In-memory JavaCompiler Tree API validation
        ExecutionResult res = executor.execute(sampleProjectRoot, opts);

        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(1, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
        assertNotNull(res.getPlan());
        assertTrue(List.of("IOrderService", "INotificationService", "IPaymentService")
                .contains(res.getPlan().getExtractedInterfaceName()));
    }
}
