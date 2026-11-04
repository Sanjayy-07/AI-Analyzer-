# NOVELTY 1 — PHASE 0: STRICT REPOSITORY INSPECTION & IMPLEMENTATION PLAN
## Automated Cycle-Breaking Refactoring with Verified Dependency Inversion

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Subsystem**: Automated Architectural Refactoring & Verification Engine  
**Author**: Principal Software Architecture Researcher & Systems Implementation Lead  
**Date**: 2026-11-04  
**Status**: APPROVED IMPLEMENTATION BLUEPRINT  

---

## 1. Executive Summary & Objective

The objective of Novelty 1 is to advance the AI-Powered Dependency & Architecture Refactoring Analyzer from static smell detection and simulated change planning to an **autonomous, end-to-end, compiler-aware architectural repair engine**:
1. Automatically detect circular architectural dependency components (SCCs) using Tarjan's algorithm.
2. Select the optimal dependency edge for inversion using a minimum feedback arc heuristic and a multi-factor modification cost model.
3. Extract clean, compiler-verified interfaces using the Java Compiler Tree API (`com.sun.source.tree.*`, `com.sun.source.util.*`, `SourcePositions`).
4. Rewrite caller sites (field declarations, constructor parameters, method signatures, local variables, imports) via AST-directed source modifications while preserving object construction semantics.
5. Execute isolated workspace transformations with atomic rollback guarantees.
6. Verify the repair through two-phase validation: in-memory compiler check + external Maven test execution + graph recomputation confirming the architectural cycle is eliminated.

---

## 2. Comprehensive Repository Inspection (Phase 0 Audit)

### 2.1 Dependency Graph Construction & Cycle Detection
* **Graph Representation**: [`core/src/main/java/com/archanalyzer/graph/DependencyGraph.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/core/src/main/java/com/archanalyzer/graph/DependencyGraph.java)
  - Directed graph $G=(V, E)$ constructed from `ProjectModel` components.
  - Adjacency maps: `adjacency` (outgoing), `reverseAdjacency` (incoming).
  - Isolated cloning via `graph.copy()`.
* **Cycle Algorithms**: [`core/src/main/java/com/archanalyzer/graph/GraphAlgorithms.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/core/src/main/java/com/archanalyzer/graph/GraphAlgorithms.java)
  - Tarjan's Strongly Connected Components (`stronglyConnectedComponents(DependencyGraph g)`): Returns `List<Set<String>>`. Any SCC with `size > 1` is an architectural cycle.
  - `isInCycle(List<Set<String>> sccs, String v)`: Boolean membership query.
  - `topologicalSort(DependencyGraph g)`: Kahn's algorithm returning `Optional.empty()` when cycles are present.
* **Smell Detection**: [`core/src/main/java/com/archanalyzer/smell/SmellDetector.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/core/src/main/java/com/archanalyzer/smell/SmellDetector.java)
  - Detects `CIRCULAR_DEPENDENCY` smells for all members of an SCC with severity `HIGH`.
* **Identified Limitation**:
  - The graph currently detects vertex SCC sets, but lacks an explicit cycle-edge decomposition and feedback-arc heuristic to prioritize which edge in the cycle should be broken.

### 2.2 Existing Refactoring Engine
* **Engine Implementation**: [`core/src/main/java/com/archanalyzer/refactor/RefactoringEngine.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/core/src/main/java/com/archanalyzer/refactor/RefactoringEngine.java)
  - `extractInterface(projectRoot, targetClassFile, targetClassSimpleName)`:
    - Parses target class using `JavacTask.parse()`.
    - Scans `MethodTree` for public non-constructor, non-static methods.
    - Emits interface `I<TargetClass>` in the same package.
    - Rewrites class declaration using regular expression pattern `CLASS_HEADER_PATTERN`.
  - `introduceInterfaceBetween(projectRoot, clientFile, providerFile, clientSimple, providerSimple)`:
    - Calls `extractInterface` on provider.
    - Rewrites client file via regex pattern `\\b<ProviderSimple>\\b` checking if preceded by `"new "`.
* **Identified Critical Limitations**:
  1. **Regex-Based Call-Site Retargeting**: `retargetTypeUsages` uses regex word boundaries, which cannot distinguish variable types from string literals, comments, or method return types, and cannot handle complex expressions.
  2. **No SourcePositions AST Editing**: Does not use `TreePathScanner` with `SourcePositions` to calculate exact character replacement offsets.
  3. **No Automatic Multi-Caller Discovery**: Only rewrites a single designated client file rather than all callers of the inverted dependency across the project.
  4. **Constructor Recursion Unhandled**: In constructor-instantiated cycles (e.g. `OrderService -> PaymentService -> NotificationService -> OrderService` in `sample-demo-project`), simply changing field types does not resolve recursive `new OrderService()` initialization loops.
  5. **No Atomic Rollback / Pre-Edit Validation**: Overwrites source files in place without hash verification or incremental rollback.

### 2.3 What-If Simulator & Implementation Engine
* **Simulator**: [`core/src/main/java/com/archanalyzer/simulator/WhatIfSimulator.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/core/src/main/java/com/archanalyzer/simulator/WhatIfSimulator.java)
  - Non-destructive simulation on isolated in-memory graph clones.
* **Implementation Engine**: [`core/src/main/java/com/archanalyzer/simulator/ChangeImplementationEngine.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/core/src/main/java/com/archanalyzer/simulator/ChangeImplementationEngine.java)
  - Creates isolated copy in `refactoredRoot`.
  - Materializes changes, re-discovers project model, runs `BuildValidator.compile`.
  - Rolls back if compilation fails.

### 2.4 Compilation & Test Validation
* **Validator**: [`core/src/main/java/com/archanalyzer/validate/BuildValidator.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/core/src/main/java/com/archanalyzer/validate/BuildValidator.java)
  - Provides in-memory `JavaCompiler` diagnostic collection.
  - Distinguishes new refactoring compiler errors from missing third-party dependencies (Spring, Lombok).
  - External Maven validation currently executes `mvn test-compile -B -DskipTests=true -o`.
* **Structural Metrics Comparison**: [`core/src/main/java/com/archanalyzer/validate/StructuralValidator.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/core/src/main/java/com/archanalyzer/validate/StructuralValidator.java)
  - Compares baseline metrics against post-refactoring metrics: `cyclesBefore`, `cyclesAfter`, `cycleReductionPercent`, `architectureHealthDelta`.

---

## 3. Architecture & Class Design for Novelty 1

To achieve automated cycle-breaking with verified dependency inversion while preserving all existing classes and APIs, we introduce a dedicated, cohesive refactoring subsystem in package `com.archanalyzer.refactor.cycle`:

```
core/src/main/java/com/archanalyzer/refactor/
├── RefactoringEngine.java                          (Preserved & enhanced)
├── RefactoringChange.java                          (Preserved)
└── cycle/
    ├── CycleBreakingStrategy.java                 (Phase 1: SCC edge evaluation & cost heuristic)
    ├── CycleCandidateEdge.java                    (Phase 1: Candidate edge model & metrics)
    ├── AstInterfaceExtractor.java                 (Phase 2: Compiler Tree API interface extraction)
    ├── AstCallerRewriter.java                     (Phase 3: TreePathScanner AST caller retargeting)
    ├── SourceEdit.java                            (Phase 4: Source-position offset replacement)
    ├── SourceFileEditor.java                      (Phase 4: Descending-position atomic file editor)
    ├── ChangeManifest.java                        (Phase 4: File hashes, patch preview, rollback)
    ├── CycleRefactoringPlan.java                  (Phase 1/4: Detailed plan with explanation)
    ├── CycleRefactoringExecutor.java              (Phase 5: Isolated execution & verification orchestrator)
    ├── CycleVerificationResult.java              (Phase 5: Verification status, metrics, build output)
    └── CycleRefactoringLogger.java                (Phase 6: Structured research log generator)
```

---

## 4. Phase-by-Phase Implementation Specifications

### Phase 1: SCC-Based Cycle Breaking Strategy (`CycleBreakingStrategy.java`)
1. **SCC Edge Extraction**:
   - For an SCC $C = \{v_1, \dots, v_k\}$ ($k \ge 2$), extract all internal edges:
     $$E_C = \{(u, v) \in E \mid u, v \in C\}$$
2. **Cost-Based Edge Scoring Function**:
   For each candidate edge $e = (u, v) \in E_C$:
   $$\text{Cost}(u \to v) = w_{\text{callers}} \cdot N_{\text{callers}} + w_{\text{fields}} \cdot N_{\text{fields}} + w_{\text{methods}} \cdot N_{\text{methods}} + w_{\text{constructors}} \cdot N_{\text{ctors}} + w_{\text{complexity}} \cdot \text{Complexity}(v)$$
   Where:
   - $N_{\text{callers}}$: Number of files referencing $v$ inside the SCC.
   - $N_{\text{fields}}$: Number of fields of type $v$ in $u$.
   - $N_{\text{constructors}}$: Number of constructors in $u$ instantiating $v$.
   - Interface feasibility: Feasible if $v$ has $\ge 1$ public non-static method and is not a primitive/enum/record.
3. **Cycle Reduction Verification**:
   - Temporarily simulate removal of $e$ on $G_C$. Re-run Tarjan's algorithm. If the SCC splits or cycle count decreases by $\ge 1$, the edge is a valid cycle-breaker.
4. **Transparent Strategy Explanation**:
   - Generate structured justification: original cycle path, selected edge, cost score, cycle reduction, and potential risks.

### Phase 2: Compiler-Aware Interface Extraction (`AstInterfaceExtractor.java`)
1. **Compiler Tree API Scanning**:
   - Parse provider source file using `JavaCompiler`, `JavacTask.parse()`, and `SourcePositions`.
   - Inspect `ClassTree` matching provider simple name.
2. **Contract Extraction Rules**:
   - Extract all public, non-static, non-constructor methods (`MethodTree`).
   - Preserve generic type parameters (`TypeParameterTree`), return types, method arguments, and thrown exceptions.
   - Preserve method-level parameter annotations (e.g. `@NotNull`, `@Valid`) where appropriate.
   - Retain necessary imports (`ImportTree`) referenced in signatures.
3. **Interface File Generation**:
   - Interface name: `I<ProviderName>`.
   - Add `implements I<ProviderName>` to provider class declaration using exact source positions.

### Phase 3: AST-Directed Caller Rewriting (`AstCallerRewriter.java`)
1. **TreePathScanner Traversal**:
   - Scan client AST using `TreePathScanner<Void, Void>`.
2. **Targeted Reference Rewriting**:
   - **Field Declarations (`VariableTree`)**: When parent is `ClassTree`, replace type identifier $v$ with $I_v$.
   - **Method Parameters (`VariableTree`)**: When parent is `MethodTree`, replace parameter type $v$ with $I_v$.
   - **Local Variables (`VariableTree`)**: When parent is block statement, replace declaration type $v$ with $I_v$.
   - **Constructor Injection Support**: In constructors initializing `this.field = new Concrete()`, detect recursive dependency cycles and generate overloaded constructor accepting `I<Provider>` to enable injection.
   - **Object Creation Preservation**: Leave `new ConcreteService(...)` expressions intact, or refactor to decouple initialization.
   - **Import Insertion**: If client and provider/interface reside in different packages, insert `import <package>.I<ProviderName>;`.

### Phase 4: Position-Based Safe Editing & Rollback (`SourceFileEditor.java`, `ChangeManifest.java`)
1. **Descending Source-Position Application**:
   - Collect edits as `SourceEdit(int startOffset, int endOffset, String replacement)`.
   - Sort in descending order by `startOffset`:
     $$e_1.\text{start} \ge e_2.\text{start} \ge \dots \ge e_m.\text{start}$$
   - Apply edits in reverse order so character insertions/deletions never invalidate upstream offsets.
   - Overlap check: Reject edit if $e_i.\text{start} < e_{i+1}.\text{end}$.
2. **Atomic Change Manifest & Rollback**:
   - Record SHA-256 hash and original content of all touched files before editing.
   - If any step fails, restore all files from cached original content.

### Phase 5: Two-Phase Build, Test & Architectural Verification (`CycleRefactoringExecutor.java`)
1. **Baseline Capture**:
   - Check baseline compilation and execute project tests when feasible. Record pre-repair cycles.
2. **Isolated Workspace Transformation**:
   - Work in isolated directory (`target/refactoring-sandbox/...`).
   - Apply interface extraction and caller rewrites.
3. **Post-Repair Compilation**:
   - Compile using `BuildValidator.compile` (JDK Compiler API).
   - If project has `pom.xml`, run Maven compilation (`mvn compile`).
4. **Test Suite Execution**:
   - If Maven project has test sources, execute `mvn test -B`.
   - If tests fail, classify as `TEST_FAILED` and trigger rollback.
5. **Architectural Graph Re-verification**:
   - Re-parse modified project with `ProjectDiscovery`.
   - Rebuild `DependencyGraph`.
   - Run `GraphAlgorithms.stronglyConnectedComponents()`.
   - Confirm target cycle is strictly eliminated ($N_{\text{cycles\_after}} < N_{\text{cycles\_before}}$).
6. **Verification Verdict**:
   - `VERIFIED`: Compiles, tests pass, cycle count decreased.
   - `COMPILE_FAILED`: Compilation error introduced.
   - `TEST_FAILED`: Test regression introduced.
   - `CYCLE_NOT_BROKEN`: Compiles and tests pass, but cycle remains.
   - `REPAIR_REJECTED`: Preconditions violated or unsafe structure.

### Phase 6: Research Evaluation Logging (`CycleRefactoringLogger.java`)
- Serialize structured JSON records for every repair attempt:
  `research/reports/novelty1_repair_log_<timestamp>.json`.

---

## 5. Automated Test Plan (Phase 7 Requirements)

Comprehensive JUnit 5 test suite in `core/src/test/java/com/archanalyzer/refactor/cycle/CycleRefactoringIntegrationTest.java`:
1. `testTwoClassCycleBreaking`: A -> B -> A.
2. `testThreeClassCycleBreaking`: A -> B -> C -> A (as in `sample-demo-project`).
3. `testMultipleCyclesInOneScc`: Interlocking cycles inside single SCC.
4. `testInterfaceExtractionWithGenerics`: Methods with `<T>` and `List<T>`.
5. `testInterfaceExtractionWithAnnotations`: Parameter and method annotations.
6. `testFieldDependencyRewriting`: Field type retargeted to interface.
7. `testConstructorParameterRewriting`: Injected constructor argument retargeted.
8. `testMethodParameterRewriting`: Method parameter retargeted to interface.
9. `testConcreteObjectCreationPreserved`: `new Concrete()` preserved at call site.
10. `testImportCorrectionCrossPackage`: Client in `com.a` correctly imports interface from `com.b`.
11. `testDuplicateInterfacePrevention`: Reuses existing interface if contract is satisfied.
12. `testMultipleSourceEditsInOneFile`: Overlap-free descending position editing.
13. `testCompilationFailureHandling`: Rollback when invalid code is injected.
14. `testTestFailureHandling`: Rollback when unit tests fail.
15. `testCycleRemainsAfterRepairHandled`: Verification flags `CYCLE_NOT_BROKEN`.
16. `testSafeRollbackRestoresExactBytes`: Byte-for-byte original file restoration.
17. `testPreExistingCompilationFailureHandled`: Distinguishes pre-existing from introduced errors.
18. `testUnsupportedReflectionCaseRejected`: Safely rejects reflection-sensitive types.
19. `testRepairPreviewWithoutApplication`: Generates dry-run patch preview.
20. `testEndToEndVerifiedRepairOnSampleProject`: Full automated repair on `sample-demo-project`.

---

## 6. Strict Safety & Isolation Guarantees

* **Zero modifications to Phase 3A research artifacts**: All Phase 3A reports and JSON files remain frozen.
* **Test dataset quarantine**: `dataset-extracted/TD dataset/test.csv` is untouched.
* **Zero production model retraining**: ML models remain frozen.
* **Non-destructive What-If Simulator**: What-If simulation remains in-memory only.
