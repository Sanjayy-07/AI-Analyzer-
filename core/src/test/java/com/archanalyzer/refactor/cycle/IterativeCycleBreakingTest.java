package com.archanalyzer.refactor.cycle;

import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.CycleGoal;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.ExecutionResult;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression and behavioral tests for DEF-03:
 * Multi-cycle and Figure-8 iterative cycle-breaking with graph re-evaluation.
 */
public class IterativeCycleBreakingTest {

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

    @Test
    @DisplayName("DEF-03: Two independent cycles broken iteratively to 0 cycles (BREAK_ALL)")
    public void testTwoIndependentCyclesBrokenCompletely() throws Exception {
        Path proj = tempDir.resolve("two-cycles-project");

        // Cycle 1: ServiceA <-> ServiceB in com.cycleone
        createJavaFile(proj, "src/main/java/com/cycleone/ServiceA.java",
                "package com.cycleone;\n" +
                "public class ServiceA {\n" +
                "    private ServiceB b;\n" +
                "    public void doA() { if (b != null) b.doB(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/cycleone/ServiceB.java",
                "package com.cycleone;\n" +
                "public class ServiceB {\n" +
                "    private ServiceA a;\n" +
                "    public void doB() { if (a != null) a.doA(); }\n" +
                "}\n");

        // Cycle 2: OrderMgr <-> PaymentMgr in com.cycletwo
        createJavaFile(proj, "src/main/java/com/cycletwo/OrderMgr.java",
                "package com.cycletwo;\n" +
                "public class OrderMgr {\n" +
                "    private PaymentMgr payment;\n" +
                "    public void processOrder() { if (payment != null) payment.pay(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/cycletwo/PaymentMgr.java",
                "package com.cycletwo;\n" +
                "public class PaymentMgr {\n" +
                "    private OrderMgr order;\n" +
                "    public void pay() { if (order != null) order.processOrder(); }\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        opts.setGoal(CycleGoal.BREAK_ALL);
        opts.setMaxIterations(5);

        ExecutionResult result = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.VERIFIED, result.getStatus(), "Refactoring must verify successfully: " + result.getErrors());
        assertEquals(2, result.getInitialCyclicSccCount(), "Must detect 2 initial cyclic SCCs");
        assertEquals(0, result.getFinalCyclicSccCount(), "All cycles must be eliminated under BREAK_ALL");
        assertEquals(2, result.getIterationsExecuted(), "Should take exactly 2 iterations to break 2 independent cycles");
        assertEquals(2, result.getAppliedEdges().size(), "Should have inverted 2 edges");
    }

    @Test
    @DisplayName("DEF-03: Figure-8 shared-node cycle broken iteratively (BREAK_ALL)")
    public void testFigureEightCycleWithSharedNode() throws Exception {
        Path proj = tempDir.resolve("figure-eight-project");

        // Center <-> LeftNode
        // Center <-> RightNode
        // Shared node: Center
        createJavaFile(proj, "src/main/java/com/fig8/Center.java",
                "package com.fig8;\n" +
                "public class Center {\n" +
                "    private LeftNode left;\n" +
                "    private RightNode right;\n" +
                "    public void doCenter() {\n" +
                "        if (left != null) left.doLeft();\n" +
                "        if (right != null) right.doRight();\n" +
                "    }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/fig8/LeftNode.java",
                "package com.fig8;\n" +
                "public class LeftNode {\n" +
                "    private Center center;\n" +
                "    public void doLeft() { if (center != null) center.doCenter(); }\n" +
                "}\n");
        createJavaFile(proj, "src/main/java/com/fig8/RightNode.java",
                "package com.fig8;\n" +
                "public class RightNode {\n" +
                "    private Center center;\n" +
                "    public void doRight() { if (center != null) center.doCenter(); }\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        opts.setGoal(CycleGoal.BREAK_ALL);
        opts.setMaxIterations(5);

        ExecutionResult result = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.VERIFIED, result.getStatus(), "Figure-8 refactoring must verify: " + result.getErrors());
        assertEquals(1, result.getInitialCyclicSccCount());
        assertEquals(0, result.getFinalCyclicSccCount(), "Figure-8 cycles must both be broken to 0 SCCs");
        assertEquals(2, result.getIterationsExecuted(), "Must take 2 iterations to untangle both loops");
        assertEquals(2, result.getAppliedEdges().size());
    }

    @Test
    @DisplayName("DEF-03: CycleGoal.BREAK_ONE stops after exactly one iteration")
    public void testGoalBreakOneStopsAfterSingleCycle() throws Exception {
        Path proj = tempDir.resolve("break-one-project");

        createJavaFile(proj, "src/main/java/com/c1/A.java",
                "package com.c1;\n" +
                "public class A { private B b; public void a() { if (b != null) b.b(); } }\n");
        createJavaFile(proj, "src/main/java/com/c1/B.java",
                "package com.c1;\n" +
                "public class B { private A a; public void b() { if (a != null) a.a(); } }\n");

        createJavaFile(proj, "src/main/java/com/c2/X.java",
                "package com.c2;\n" +
                "public class X { private Y y; public void x() { if (y != null) y.y(); } }\n");
        createJavaFile(proj, "src/main/java/com/c2/Y.java",
                "package com.c2;\n" +
                "public class Y { private X x; public void y() { if (x != null) x.x(); } }\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);
        opts.setGoal(CycleGoal.BREAK_ONE);

        ExecutionResult result = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.VERIFIED, result.getStatus());
        assertEquals(2, result.getInitialCyclicSccCount());
        assertEquals(1, result.getFinalCyclicSccCount(), "Should have exactly 1 cycle remaining under BREAK_ONE");
        assertEquals(1, result.getIterationsExecuted(), "BREAK_ONE should stop after 1 iteration");
        assertEquals(1, result.getAppliedEdges().size());
    }
}
