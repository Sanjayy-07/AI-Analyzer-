package com.archanalyzer.audit;

import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.CycleGoal;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.ExecutionResult;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S.1 — Independent Audit for DEF-03 (Multi-Cycle Algorithm Challenge):
 * Tests complex multi-cycle topologies, Figure-8 shared nodes, overlapping edges,
 * BREAK_ONE vs BREAK_ALL goals, and atomic transactional rollback.
 */
public class Def03MultiCycleChallengeTest {

    @TempDir
    Path tempDir;

    private Path writeSource(Path root, String relPath, String content) throws IOException {
        Path file = root.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    // 1. One 4-node cycle: A -> B -> C -> D -> A
    @Test
    @DisplayName("1. Four-node cycle (A -> B -> C -> D -> A)")
    public void testFourNodeCycle() throws Exception {
        Path root = tempDir.resolve("four-node");
        writeSource(root, "src/com/demo/A.java", "package com.demo; public class A { private B b; public void doA() { if (b != null) b.doB(); } }");
        writeSource(root, "src/com/demo/B.java", "package com.demo; public class B { private C c; public void doB() { if (c != null) c.doC(); } }");
        writeSource(root, "src/com/demo/C.java", "package com.demo; public class C { private D d; public void doC() { if (d != null) d.doD(); } }");
        writeSource(root, "src/com/demo/D.java", "package com.demo; public class D { private A a; public void doD() { if (a != null) a.doA(); } }");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(1, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // 2. Three independent cycles: (A<->B), (C<->D), (E<->F)
    @Test
    @DisplayName("2. Three independent cycles eliminated iteratively under BREAK_ALL")
    public void testThreeIndependentCycles() throws Exception {
        Path root = tempDir.resolve("three-cycles");
        // Cycle 1
        writeSource(root, "src/com/c1/A.java", "package com.c1; public class A { private B b; public void a() { if (b != null) b.b(); } }");
        writeSource(root, "src/com/c1/B.java", "package com.c1; public class B { private A a; public void b() { if (a != null) a.a(); } }");
        // Cycle 2
        writeSource(root, "src/com/c2/C.java", "package com.c2; public class C { private D d; public void c() { if (d != null) d.d(); } }");
        writeSource(root, "src/com/c2/D.java", "package com.c2; public class D { private C c; public void d() { if (c != null) c.c(); } }");
        // Cycle 3
        writeSource(root, "src/com/c3/E.java", "package com.c3; public class E { private F f; public void e() { if (f != null) f.f(); } }");
        writeSource(root, "src/com/c3/F.java", "package com.c3; public class F { private E e; public void f() { if (e != null) e.e(); } }");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        opts.setGoal(CycleGoal.BREAK_ALL);
        opts.setMaxIterations(10);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(3, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
        assertEquals(3, res.getIterationsExecuted(), "Must take 3 iterations for 3 independent cycles");
    }

    // 3. Figure-8 with shared node
    @Test
    @DisplayName("3. Figure-8 shared-node cycle broken to 0 cycles")
    public void testFigureEightSharedNode() throws Exception {
        Path root = tempDir.resolve("fig8");
        writeSource(root, "src/com/fig/Hub.java",
                "package com.fig;\n" +
                "public class Hub {\n" +
                "    private WingLeft left;\n" +
                "    private WingRight right;\n" +
                "    public void h() { if (left != null) left.l(); if (right != null) right.r(); }\n" +
                "}\n");
        writeSource(root, "src/com/fig/WingLeft.java",
                "package com.fig; public class WingLeft { private Hub h; public void l() { if (h != null) h.h(); } }\n");
        writeSource(root, "src/com/fig/WingRight.java",
                "package com.fig; public class WingRight { private Hub h; public void r() { if (h != null) h.h(); } }\n");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        opts.setGoal(CycleGoal.BREAK_ALL);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(1, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
        assertEquals(2, res.getIterationsExecuted());
    }

    // 4. Overlapping cycles sharing an edge: (A -> B -> C -> A) and (A -> B -> D -> A)
    @Test
    @DisplayName("4. Overlapping cycles sharing edge (A -> B) eliminated in single iteration")
    public void testOverlappingCyclesSharingEdge() throws Exception {
        Path root = tempDir.resolve("shared-edge");
        writeSource(root, "src/com/demo/NodeA.java",
                "package com.demo;\n" +
                "public class NodeA {\n" +
                "    private NodeB b;\n" +
                "    public void a() { if (b != null) b.b(); }\n" +
                "}\n");
        writeSource(root, "src/com/demo/NodeB.java",
                "package com.demo;\n" +
                "public class NodeB {\n" +
                "    private NodeC c;\n" +
                "    private NodeD d;\n" +
                "    public void b() { if (c != null) c.c(); if (d != null) d.d(); }\n" +
                "}\n");
        writeSource(root, "src/com/demo/NodeC.java",
                "package com.demo; public class NodeC { private NodeA a; public void c() { if (a != null) a.a(); } }\n");
        writeSource(root, "src/com/demo/NodeD.java",
                "package com.demo; public class NodeD { private NodeA a; public void d() { if (a != null) a.a(); } }\n");

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();
        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.VERIFIED, res.getStatus());
        assertEquals(1, res.getInitialCyclicSccCount());
        assertEquals(0, res.getFinalCyclicSccCount());
    }

    // 5. Atomic Rollback Verification on Injected Compilation Failure
    @Test
    @DisplayName("5. Transactional rollback restores exact file contents on compilation failure")
    public void testAtomicRollbackRestoresWorkspace() throws Exception {
        Path root = tempDir.resolve("rollback-test");
        Path fileA = writeSource(root, "src/com/demo/A.java",
                "package com.demo; public class A { private B b; public void a() { if (b != null) b.b(); } }");
        Path fileB = writeSource(root, "src/com/demo/B.java",
                "package com.demo; public class B { private A a; public void b() { if (a != null) a.a(); } }");

        String originalContentA = Files.readString(fileA);
        String originalContentB = Files.readString(fileB);

        // Subclass validator that forces compile failure on first refactoring attempt
        com.archanalyzer.validate.BuildValidator failingValidator = new com.archanalyzer.validate.BuildValidator() {
            private int calls = 0;
            @Override
            public BuildResult compile(Path sourceRoot, java.util.List<Path> cp, Path baseline) {
                calls++;
                BuildResult res = new BuildResult();
                if (calls > 1) { // Baseline passes (call 1), refactored compile fails (call 2)
                    res.status = Status.FAIL;
                    res.errors.add("Injected simulated compilation error");
                    return res;
                }
                res.status = Status.PASS;
                return res;
            }
        };

        CycleRefactoringExecutor executor = new CycleRefactoringExecutor(
                failingValidator,
                new com.archanalyzer.refactor.cycle.CycleBreakingStrategy(),
                new com.archanalyzer.refactor.cycle.AstInterfaceExtractor(),
                new com.archanalyzer.refactor.cycle.AstCallerRewriter(),
                new com.archanalyzer.refactor.cycle.SourceFileEditor()
        );

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult res = executor.execute(root, opts);
        assertEquals(RefactoringStatus.COMPILE_FAILED, res.getStatus(), "Should detect compilation failure and abort");

        // Transactional invariant: All files restored to exact pre-refactoring content
        assertEquals(originalContentA, Files.readString(fileA), "File A must be completely restored");
        assertEquals(originalContentB, Files.readString(fileB), "File B must be completely restored");

        // Generated interface must have been cleaned up / deleted by rollback
        Path ifaceB = root.resolve("src/com/demo/IB.java");
        Path ifaceA = root.resolve("src/com/demo/IA.java");
        assertFalse(Files.exists(ifaceB), "Generated interface B must not remain after rollback");
        assertFalse(Files.exists(ifaceA), "Generated interface A must not remain after rollback");
    }
}
