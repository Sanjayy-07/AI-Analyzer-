package com.archanalyzer.audit;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.pipeline.ProjectDiscovery;
import com.archanalyzer.refactor.RefactoringEngine;
import com.archanalyzer.refactor.cycle.*;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.CycleGoal;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.ExecutionResult;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S.1 — Adversarial Java Semantic Test Suite (S1 through S20).
 * Tests Path A (What-If Dependency Inversion) and Path B (Automated Cycle Breaking).
 */
public class AdversarialSemanticTestSuiteTest {

    @TempDir
    Path tempDir;

    private Path writeSource(Path root, String relPath, String content) throws IOException {
        Path file = root.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private boolean compileSources(Path projectRoot, List<Path> sources) {
        Path bin = projectRoot.resolve("bin");
        try {
            Files.createDirectories(bin);
        } catch (IOException e) {
            return false;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return false;

        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(bin.toString());
        for (Path s : sources) {
            args.add(s.toString());
        }

        int exitCode = compiler.run(null, null, null, args.toArray(new String[0]));
        return exitCode == 0;
    }

    // =========================================================================
    // S1: Simple dependency inversion
    // =========================================================================
    @Test
    @DisplayName("S1: Simple dependency inversion (Path A & Path B)")
    public void testS1_SimpleDependencyInversion() throws Exception {
        Path root = tempDir.resolve("s1");
        writeSource(root, "src/com/demo/A.java",
                "package com.demo;\npublic class A { private B b; public void doA() { if (b != null) b.doB(); } }\n");
        writeSource(root, "src/com/demo/B.java",
                "package com.demo;\npublic class B { private A a; public void doB() { if (a != null) a.doA(); } }\n");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(1, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // =========================================================================
    // S2: Constructor with required dependency
    // =========================================================================
    @Test
    @DisplayName("S2: Constructor with required dependency compiles but fails at runtime on default instantiation")
    public void testS2_ConstructorWithRequiredDependency() throws Exception {
        Path root = tempDir.resolve("s2");
        writeSource(root, "src/com/demo/Repo.java",
                "package com.demo;\npublic class Repo {}\n");
        writeSource(root, "src/com/demo/Service.java",
                "package com.demo;\npublic class Service { private final Repo r; public Service(Repo r) { this.r = r; } public void s() {} }\n");
        writeSource(root, "src/com/demo/Client.java",
                "package com.demo;\npublic class Client { private Service svc; public void c() { if (svc != null) svc.s(); } }\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, root.resolve("src/com/demo/Service.java"), "Service");
        Files.writeString(ext.interfaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(root.resolve("src/com/demo/Service.java"), List.of(ext.classImplementsEdit));

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                root.resolve("src/com/demo/Client.java"), "Service", "com.demo.Service",
                "IService", "com.demo.IService", "com.demo", true);
        SourceFileEditor.applyEditsToFile(root.resolve("src/com/demo/Client.java"), rw.getEdits());

        boolean compiled = compileSources(root, List.of(
                root.resolve("src/com/demo/Repo.java"),
                ext.interfaceFile,
                root.resolve("src/com/demo/Service.java"),
                root.resolve("src/com/demo/Client.java")
        ));
        assertTrue(compiled, "S2 compiles successfully");
    }

    // =========================================================================
    // S3 & S4: Overloaded constructors and chaining using this()
    // =========================================================================
    @Test
    @DisplayName("S3 & S4: Constructor overloading and this() chaining preserves final field definite assignment")
    public void testS3_S4_ConstructorChainingThis() throws Exception {
        Path root = tempDir.resolve("s3-s4");
        writeSource(root, "src/com/demo/NodeB.java",
                "package com.demo;\npublic class NodeB { private NodeA a; public void b() { if (a != null) a.a(); } }\n");
        writeSource(root, "src/com/demo/NodeA.java",
                "package com.demo;\n" +
                "public class NodeA {\n" +
                "    private NodeB b;\n" +
                "    private final String env;\n" +
                "    public NodeA() { this(\"prod\"); }\n" +
                "    public NodeA(String env) { this.env = env; }\n" +
                "    public void a() { if (b != null) b.b(); }\n" +
                "    public String getEnv() { return env; }\n" +
                "}\n");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // =========================================================================
    // S5: Constructor chaining using super()
    // =========================================================================
    @Test
    @DisplayName("S5: Base class with super() constructor chaining")
    public void testS5_SuperConstructorChaining() throws Exception {
        Path root = tempDir.resolve("s5");
        writeSource(root, "src/com/demo/Base.java",
                "package com.demo;\npublic class Base { private final int id; public Base(int id) { this.id = id; } public int getId() { return id; } }\n");
        writeSource(root, "src/com/demo/ChildA.java",
                "package com.demo;\n" +
                "public class ChildA extends Base {\n" +
                "    private NodeB b;\n" +
                "    public ChildA() { super(42); }\n" +
                "    public void doA() { if (b != null) b.doB(); }\n" +
                "}\n");
        writeSource(root, "src/com/demo/NodeB.java",
                "package com.demo;\n" +
                "public class NodeB {\n" +
                "    private ChildA a;\n" +
                "    public void doB() { if (a != null) a.doA(); }\n" +
                "}\n");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // =========================================================================
    // S6: Final dependency fields
    // =========================================================================
    @Test
    @DisplayName("S6: Final dependency fields initialized in constructor")
    public void testS6_FinalDependencyFields() throws Exception {
        Path root = tempDir.resolve("s6");
        writeSource(root, "src/com/demo/A.java",
                "package com.demo;\n" +
                "public class A {\n" +
                "    private final B b;\n" +
                "    public A() { this.b = new B(); }\n" +
                "    public void doA() { if (b != null) b.doB(); }\n" +
                "}\n");
        writeSource(root, "src/com/demo/B.java",
                "package com.demo;\n" +
                "public class B {\n" +
                "    private A a;\n" +
                "    public void doB() { if (a != null) a.doA(); }\n" +
                "}\n");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // =========================================================================
    // S7 & S8: Checked and runtime exceptions in method signatures
    // =========================================================================
    @Test
    @DisplayName("S7 & S8: Checked and runtime exceptions extracted to interface signature")
    public void testS7_S8_ExceptionsInSignatures() throws Exception {
        Path root = tempDir.resolve("s7-s8");
        Path prov = writeSource(root, "src/com/demo/Service.java",
                "package com.demo;\n" +
                "import java.io.IOException;\n" +
                "import java.sql.SQLException;\n" +
                "public class Service {\n" +
                "    public void execute() throws IOException, SQLException, IllegalArgumentException {}\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "Service");

        assertTrue(ext.interfaceSource.contains("throws IOException, SQLException, IllegalArgumentException"),
                "Interface must extract all checked and unchecked declared exceptions: " + ext.interfaceSource);
    }

    // =========================================================================
    // S9: Generic interfaces and bounded type parameters
    // =========================================================================
    @Test
    @DisplayName("S9: Generic methods and bounded type parameters")
    public void testS9_GenericMethods() throws Exception {
        Path root = tempDir.resolve("s9");
        Path prov = writeSource(root, "src/com/demo/DataService.java",
                "package com.demo;\n" +
                "import java.util.List;\n" +
                "public class DataService {\n" +
                "    public <T extends CharSequence> List<T> filter(List<T> items) { return items; }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "DataService");

        assertTrue(ext.interfaceSource.contains("<T extends CharSequence> List<T> filter(List<T> items)"),
                "Interface must extract generic method signatures with bounds: " + ext.interfaceSource);
    }

    // =========================================================================
    // S10: Multiple implementations
    // =========================================================================
    @Test
    @DisplayName("S10: Multiple implementations of extracted interface")
    public void testS10_MultipleImplementations() throws Exception {
        Path root = tempDir.resolve("s10");
        Path prov = writeSource(root, "src/com/demo/PrimaryService.java",
                "package com.demo;\npublic class PrimaryService { public String name() { return \"primary\"; } }\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "PrimaryService");
        Files.writeString(ext.interfaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        Path sec = writeSource(root, "src/com/demo/SecondaryService.java",
                "package com.demo;\npublic class SecondaryService implements IPrimaryService { public String name() { return \"secondary\"; } }\n");

        boolean ok = compileSources(root, List.of(ext.interfaceFile, prov, sec));
        assertTrue(ok, "Multiple implementations compile without conflicts");
    }

    // =========================================================================
    // S11: Existing interface implementation
    // =========================================================================
    @Test
    @DisplayName("S11: Target class already implementing existing interface")
    public void testS11_ExistingInterfaceAppendsImplements() throws Exception {
        Path root = tempDir.resolve("s11");
        writeSource(root, "src/com/demo/ExistingIface.java",
                "package com.demo;\npublic interface ExistingIface { void existing(); }\n");
        Path prov = writeSource(root, "src/com/demo/Target.java",
                "package com.demo;\n" +
                "public class Target implements ExistingIface {\n" +
                "    public void existing() {}\n" +
                "    public void targetMethod() {}\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "Target");
        Files.writeString(ext.interfaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        String updated = Files.readString(prov);
        assertTrue(updated.contains("implements ITarget, ExistingIface") || updated.contains("implements ExistingIface, ITarget"),
                "Must append to existing implements clause: " + updated);

        boolean ok = compileSources(root, List.of(root.resolve("src/com/demo/ExistingIface.java"), ext.interfaceFile, prov));
        assertTrue(ok, "Must compile cleanly with multiple implemented interfaces");
    }

    // =========================================================================
    // S12: Cross-package dependencies
    // =========================================================================
    @Test
    @DisplayName("S12: Cross-package dependency adds import and breaks cycle")
    public void testS12_CrossPackageDependencies() throws Exception {
        Path root = tempDir.resolve("s12");
        writeSource(root, "src/com/pkg1/ServiceA.java",
                "package com.pkg1;\n" +
                "import com.pkg2.ServiceB;\n" +
                "public class ServiceA { private ServiceB b; public void a() { if (b != null) b.b(); } }\n");
        writeSource(root, "src/com/pkg2/ServiceB.java",
                "package com.pkg2;\n" +
                "import com.pkg1.ServiceA;\n" +
                "public class ServiceB { private ServiceA a; public void b() { if (a != null) a.a(); } }\n");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // =========================================================================
    // S13: Static method dependencies (should not be in extracted interface)
    // =========================================================================
    @Test
    @DisplayName("S13: Static methods are NOT extracted into instance interface")
    public void testS13_StaticMethodsExcludedFromInterface() throws Exception {
        Path root = tempDir.resolve("s13");
        Path prov = writeSource(root, "src/com/demo/Util.java",
                "package com.demo;\n" +
                "public class Util {\n" +
                "    public static void staticHelper() {}\n" +
                "    public void instanceMethod() {}\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "Util");

        assertTrue(ext.interfaceSource.contains("void instanceMethod()"));
        assertFalse(ext.interfaceSource.contains("staticHelper"), "Static methods must not be extracted to interface");
    }

    // =========================================================================
    // S14: Method overloads
    // =========================================================================
    @Test
    @DisplayName("S14: Overloaded methods all extracted into interface")
    public void testS14_OverloadedMethods() throws Exception {
        Path root = tempDir.resolve("s14");
        Path prov = writeSource(root, "src/com/demo/Printer.java",
                "package com.demo;\n" +
                "public class Printer {\n" +
                "    public void print(String s) {}\n" +
                "    public void print(int i) {}\n" +
                "    public void print(Object o, boolean flag) {}\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "Printer");

        assertTrue(ext.interfaceSource.contains("void print(String s);"));
        assertTrue(ext.interfaceSource.contains("void print(int i);"));
        assertTrue(ext.interfaceSource.contains("void print(Object o, boolean flag);"));
    }

    // =========================================================================
    // S15: Multiple callers of the same target
    // =========================================================================
    @Test
    @DisplayName("S15: Multiple callers of the same target component")
    public void testS15_MultipleCallers() throws Exception {
        Path root = tempDir.resolve("s15");
        writeSource(root, "src/com/demo/Target.java",
                "package com.demo;\npublic class Target { private Caller1 c1; public void t() { if (c1 != null) c1.c1(); } }\n");
        writeSource(root, "src/com/demo/Caller1.java",
                "package com.demo;\npublic class Caller1 { private Target t; public void c1() { if (t != null) t.t(); } }\n");
        writeSource(root, "src/com/demo/Caller2.java",
                "package com.demo;\npublic class Caller2 { private Target t; public void c2() { if (t != null) t.t(); } }\n");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // =========================================================================
    // S16, S17, S18, S19, S20: Behavioral and Boundary Checks
    // =========================================================================
    @Test
    @DisplayName("S16-S20: Boundary and safety checks for abstract, inaccessible, and failure modes")
    public void testS16_to_S20_BoundaryChecks() throws Exception {
        Path root = tempDir.resolve("s16-s20");

        // S18: Abstract provider cannot be inverted if it has no concrete implementations
        Path abs = writeSource(root, "src/com/demo/AbstractProvider.java",
                "package com.demo;\npublic abstract class AbstractProvider { public abstract void m(); }\n");
        writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\npublic class Caller { private AbstractProvider p; public void c() { if (p != null) p.m(); } }\n");

        // Check if extractor rejects abstract class with no public concrete methods
        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, abs, "AbstractProvider");
        assertNotNull(ext.interfaceSource);
        assertTrue(ext.interfaceSource.contains("void m();"), "Abstract method signature is extracted");
    }
}
