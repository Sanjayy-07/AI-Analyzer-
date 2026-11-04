package com.archanalyzer.audit;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.ExecutionResult;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class CycleGeneralizationAuditor {

    public static class FixtureResult {
        public int id;
        public String name;
        public int initialCycles;
        public int finalCycles;
        public boolean cycleEliminated;
        public String status;
        public String selectedEdge;
        public String notes;

        public FixtureResult(int id, String name, int initialCycles, int finalCycles,
                             boolean cycleEliminated, String status, String selectedEdge, String notes) {
            this.id = id;
            this.name = name;
            this.initialCycles = initialCycles;
            this.finalCycles = finalCycles;
            this.cycleEliminated = cycleEliminated;
            this.status = status;
            this.selectedEdge = selectedEdge;
            this.notes = notes;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("PHASE R-G: CYCLE REFACTORING GENERALIZATION AUDIT (10 FIXTURES)");
        System.out.println("=================================================================");

        List<FixtureResult> results = new ArrayList<>();
        CycleRefactoringExecutor executor = new CycleRefactoringExecutor();

        for (int i = 1; i <= 10; i++) {
            results.add(auditFixture(i, executor));
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("GENERALIZATION RESULTS SUMMARY");
        System.out.println("-----------------------------------------------------------------");
        int passCount = 0;
        for (FixtureResult r : results) {
            System.out.printf("[%s] Fixture %02d: %s\n     Initial Cycles: %d -> Final Cycles: %d | Status: %s | Edge: %s\n     Notes: %s\n",
                    r.cycleEliminated ? "PASS" : "WARN", r.id, r.name,
                    r.initialCycles, r.finalCycles, r.status, r.selectedEdge, r.notes);
            if (r.cycleEliminated) passCount++;
        }
        System.out.printf("\nTotal Fixtures: %d, Passed (Cycle Reduced): %d, Limitations/Unbroken: %d\n",
                results.size(), passCount, results.size() - passCount);
        System.out.println("=================================================================");
    }

    private static FixtureResult auditFixture(int id, CycleRefactoringExecutor executor) throws Exception {
        Path tempRoot = Files.createTempDirectory("fixture-" + id + "-");
        try {
            String fixtureName = setupFixture(id, tempRoot);

            RefactoringOptions options = new RefactoringOptions();
            options.setRunMavenTests(false); // test in-memory compilation and graph cycle elimination
            options.setDecoupleConstructorInstantiation(true);

            ExecutionResult res = executor.execute(tempRoot, options);

            boolean pass = res.getFinalCyclicSccCount() < res.getInitialCyclicSccCount();
            String edgeStr = res.getPlan() != null && res.getPlan().getSelectedEdge() != null
                    ? res.getPlan().getSelectedEdge().getFromComponent() + " -> " + res.getPlan().getSelectedEdge().getToComponent()
                    : "None";

            String notes = pass
                    ? "Cycle eliminated cleanly via interface inversion."
                    : (res.getErrors().isEmpty() ? "Cycle not fully reduced in single step." : res.getErrors().get(0));

            return new FixtureResult(id, fixtureName, res.getInitialCyclicSccCount(),
                    res.getFinalCyclicSccCount(), pass, res.getStatus().name(), edgeStr, notes);

        } finally {
            deleteDir(tempRoot);
        }
    }

    private static String setupFixture(int id, Path root) throws IOException {
        Path src = root.resolve("src/main/java");
        Files.createDirectories(src);

        switch (id) {
            case 1: // Three-node cycle: A -> B -> C -> A
                Path p1 = src.resolve("com/demo/fix1");
                Files.createDirectories(p1);
                Files.writeString(p1.resolve("NodeA.java"), "package com.demo.fix1;\npublic class NodeA { private NodeB b; public void a() { if (b!=null) b.b(); } }\n");
                Files.writeString(p1.resolve("NodeB.java"), "package com.demo.fix1;\npublic class NodeB { private NodeC c; public void b() { if (c!=null) c.c(); } }\n");
                Files.writeString(p1.resolve("NodeC.java"), "package com.demo.fix1;\npublic class NodeC { private NodeA a; public void c() { if (a!=null) a.a(); } }\n");
                return "Three-Node Cycle (A -> B -> C -> A)";

            case 2: // Four-node cycle: A -> B -> C -> D -> A
                Path p2 = src.resolve("com/demo/fix2");
                Files.createDirectories(p2);
                Files.writeString(p2.resolve("NodeA.java"), "package com.demo.fix2;\npublic class NodeA { private NodeB b; public void a() {} }\n");
                Files.writeString(p2.resolve("NodeB.java"), "package com.demo.fix2;\npublic class NodeB { private NodeC c; public void b() {} }\n");
                Files.writeString(p2.resolve("NodeC.java"), "package com.demo.fix2;\npublic class NodeC { private NodeD d; public void c() {} }\n");
                Files.writeString(p2.resolve("NodeD.java"), "package com.demo.fix2;\npublic class NodeD { private NodeA a; public void d() {} }\n");
                return "Four-Node Cycle (A -> B -> C -> D -> A)";

            case 3: // Two independent cycles: A -> B -> C -> A and D -> E -> F -> D
                Path p3 = src.resolve("com/demo/fix3");
                Files.createDirectories(p3);
                Files.writeString(p3.resolve("NodeA.java"), "package com.demo.fix3;\npublic class NodeA { private NodeB b; public void a() {} }\n");
                Files.writeString(p3.resolve("NodeB.java"), "package com.demo.fix3;\npublic class NodeB { private NodeC c; public void b() {} }\n");
                Files.writeString(p3.resolve("NodeC.java"), "package com.demo.fix3;\npublic class NodeC { private NodeA a; public void c() {} }\n");
                Files.writeString(p3.resolve("NodeD.java"), "package com.demo.fix3;\npublic class NodeD { private NodeE e; public void d() {} }\n");
                Files.writeString(p3.resolve("NodeE.java"), "package com.demo.fix3;\npublic class NodeE { private NodeF f; public void e() {} }\n");
                Files.writeString(p3.resolve("NodeF.java"), "package com.demo.fix3;\npublic class NodeF { private NodeD d; public void f() {} }\n");
                return "Two Independent Cycles (A-B-C and D-E-F)";

            case 4: // Shared-node cycles: A -> B -> C -> A and A -> D -> E -> A (Figure-8)
                Path p4 = src.resolve("com/demo/fix4");
                Files.createDirectories(p4);
                Files.writeString(p4.resolve("NodeA.java"), "package com.demo.fix4;\npublic class NodeA { private NodeB b; private NodeD d; public void a() {} }\n");
                Files.writeString(p4.resolve("NodeB.java"), "package com.demo.fix4;\npublic class NodeB { private NodeC c; public void b() {} }\n");
                Files.writeString(p4.resolve("NodeC.java"), "package com.demo.fix4;\npublic class NodeC { private NodeA a; public void c() {} }\n");
                Files.writeString(p4.resolve("NodeD.java"), "package com.demo.fix4;\npublic class NodeD { private NodeE e; public void d() {} }\n");
                Files.writeString(p4.resolve("NodeE.java"), "package com.demo.fix4;\npublic class NodeE { private NodeA a; public void e() {} }\n");
                return "Shared-Node Figure-8 Cycles (A-B-C-A and A-D-E-A)";

            case 5: // Multiple candidate edges: A -> B -> C -> A with cross edge C -> B
                Path p5 = src.resolve("com/demo/fix5");
                Files.createDirectories(p5);
                Files.writeString(p5.resolve("NodeA.java"), "package com.demo.fix5;\npublic class NodeA { private NodeB b; public void a() {} }\n");
                Files.writeString(p5.resolve("NodeB.java"), "package com.demo.fix5;\npublic class NodeB { private NodeC c; public void b() {} }\n");
                Files.writeString(p5.resolve("NodeC.java"), "package com.demo.fix5;\npublic class NodeC { private NodeA a; private NodeB b; public void c() {} }\n");
                return "Multiple Candidate Feedback Edges";

            case 6: // Multiple callers: Target C used by A and B in cycle A -> B -> C -> A
                Path p6 = src.resolve("com/demo/fix6");
                Files.createDirectories(p6);
                Files.writeString(p6.resolve("NodeA.java"), "package com.demo.fix6;\npublic class NodeA { private NodeB b; private NodeC c; public void a() {} }\n");
                Files.writeString(p6.resolve("NodeB.java"), "package com.demo.fix6;\npublic class NodeB { private NodeC c; public void b() {} }\n");
                Files.writeString(p6.resolve("NodeC.java"), "package com.demo.fix6;\npublic class NodeC { private NodeA a; public void c() {} }\n");
                return "Multiple Callers of Target Dependency";

            case 7: // Multiple methods on target
                Path p7 = src.resolve("com/demo/fix7");
                Files.createDirectories(p7);
                Files.writeString(p7.resolve("NodeA.java"), "package com.demo.fix7;\npublic class NodeA { private NodeB b; public void a() { if (b!=null) { b.m1(); b.m2(); } } }\n");
                Files.writeString(p7.resolve("NodeB.java"), "package com.demo.fix7;\npublic class NodeB {\n" +
                        "    private NodeC c;\n" +
                        "    public void m1() {}\n" +
                        "    public void m2() {}\n" +
                        "    public int m3(String s) { return s.length(); }\n" +
                        "    public void b() { if (c!=null) c.c(); }\n" +
                        "}\n");
                Files.writeString(p7.resolve("NodeC.java"), "package com.demo.fix7;\npublic class NodeC { private NodeA a; public void c() { if (a!=null) a.a(); } }\n");
                return "Multiple Public Methods on Target Class";

            case 8: // Nested packages: Distributed across packages
                Path pkgA = src.resolve("com/demo/pkg1");
                Path pkgB = src.resolve("com/demo/pkg2");
                Path pkgC = src.resolve("com/demo/pkg3");
                Files.createDirectories(pkgA);
                Files.createDirectories(pkgB);
                Files.createDirectories(pkgC);
                Files.writeString(pkgA.resolve("NodeA.java"), "package com.demo.pkg1;\nimport com.demo.pkg2.NodeB;\npublic class NodeA { private NodeB b; public void a() {} }\n");
                Files.writeString(pkgB.resolve("NodeB.java"), "package com.demo.pkg2;\nimport com.demo.pkg3.NodeC;\npublic class NodeB { private NodeC c; public void b() {} }\n");
                Files.writeString(pkgC.resolve("NodeC.java"), "package com.demo.pkg3;\nimport com.demo.pkg1.NodeA;\npublic class NodeC { private NodeA a; public void c() {} }\n");
                return "Cross-Package Dependency Cycle";

            case 9: // Existing interfaces: Target already implements an interface
                Path p9 = src.resolve("com/demo/fix9");
                Files.createDirectories(p9);
                Files.writeString(p9.resolve("ExistingInterface.java"), "package com.demo.fix9;\npublic interface ExistingInterface { void existing(); }\n");
                Files.writeString(p9.resolve("NodeA.java"), "package com.demo.fix9;\npublic class NodeA { private NodeB b; public void a() {} }\n");
                Files.writeString(p9.resolve("NodeB.java"), "package com.demo.fix9;\npublic class NodeB implements ExistingInterface { private NodeC c; public void existing() {} public void b() {} }\n");
                Files.writeString(p9.resolve("NodeC.java"), "package com.demo.fix9;\npublic class NodeC { private NodeA a; public void c() {} }\n");
                return "Class with Pre-Existing Implements Clause";

            case 10: // Generic types: Method signatures with generics
                Path p10 = src.resolve("com/demo/fix10");
                Files.createDirectories(p10);
                Files.writeString(p10.resolve("NodeA.java"), "package com.demo.fix10;\nimport java.util.List;\npublic class NodeA { private NodeB b; public void a() {} }\n");
                Files.writeString(p10.resolve("NodeB.java"), "package com.demo.fix10;\nimport java.util.List;\npublic class NodeB { private NodeC c; public <T> List<T> wrap(T item) { return List.of(item); } public void b() {} }\n");
                Files.writeString(p10.resolve("NodeC.java"), "package com.demo.fix10;\npublic class NodeC { private NodeA a; public void c() {} }\n");
                return "Cycle Involving Generic Methods";

            default:
                return "Unknown Fixture";
        }
    }

    private static void deleteDir(Path p) {
        try {
            Files.walk(p).sorted(Comparator.reverseOrder()).forEach(f -> {
                try { Files.deleteIfExists(f); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
