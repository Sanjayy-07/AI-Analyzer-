# PHASE R-A: BASELINE & REFACTORING IMPLEMENTATION INVENTORY

**Project:** AI-Powered Dependency & Architecture Refactoring Analyzer  
**Audit Scope:** Refactoring Engine, AST Transformations, Validation, Rollback, and Archive Handling  
**Date:** November 4, 2026  
**Auditor:** Antigravity Behavioral Correctness & Refactoring Safety Audit Team  

---

## 1. ARCHITECTURAL OVERVIEW

The application provides two complementary refactoring pathways for dependency inversion and architectural cycle elimination:

1. **Pathway A — What-If Approval Execution (`RefactoringEngine` & `WhatIfSimulatorServlet`):**
   - User analyzes a project and submits a What-If simulation (`POST /simulator`).
   - If approved (`POST /simulator/approve`), `RefactoringEngine.introduceInterfaceBetween()` is invoked.
   - Mechanism: Regex-based class header rewrite (`implements I...`), interface extraction, and caller type retargeting.
   - Scope: Leaves concrete `new Provider()` constructor invocations intact.

2. **Pathway B — Automated Cycle-Breaking Engine (`CycleRefactoringExecutor` & `CycleRefactoringService`):**
   - Detects strongly connected components (SCCs) using Tarjan's algorithm on `DependencyGraph`.
   - Identifies candidate cycle-breaking edges (`CycleBreakingStrategy`).
   - Extracts interface via Java Compiler Tree API (`AstInterfaceExtractor`).
   - Rewrites caller AST via `AstCallerRewriter`.
   - Replaces concrete instantiation with `null` when `decoupleConstructorInstantiation=true`.
   - Injects constructor and setter for the interface into the caller class.
   - Guards existing invocations on the decoupled field with `if (field != null)`.
   - Verifies via in-memory `javac` (`BuildValidator.compile`) and external build tools (`BuildValidator.runExternalBuildToolValidation`).
   - Maintains transactional backup and rollback via `ChangeManifest`.

---

## 2. RELEVANT CLASSES & METHODS

| Class | Package | Primary Responsibility | Key Methods |
| :--- | :--- | :--- | :--- |
| `CycleRefactoringExecutor` | `com.archanalyzer.refactor.cycle` | Master coordinator for cycle breaking | `execute(Path, RefactoringOptions)` |
| `AstInterfaceExtractor` | `com.archanalyzer.refactor.cycle` | AST-based interface extraction | `extractInterface(Path, Path, String)` |
| `AstCallerRewriter` | `com.archanalyzer.refactor.cycle` | AST caller rewriting & instantiation decoupling | `rewriteCaller(...)` |
| `ChangeManifest` | `com.archanalyzer.refactor.cycle` | File backup tracking & atomic rollback | `recordFileForModification()`, `rollbackAll()` |
| `SourceFileEditor` | `com.archanalyzer.refactor.cycle` | Offset-based source text patch applicator | `applyEditsToFile(Path, List<SourceEdit>)` |
| `RefactoringEngine` | `com.archanalyzer.refactor` | Single-step interface & caller retargeting | `introduceInterfaceBetween()`, `extractInterface()` |
| `BuildValidator` | `com.archanalyzer.validate` | In-memory `javac` and external Maven validation | `compile()`, `runExternalBuildToolValidation()` |
| `ProjectPackager` | `com.archanalyzer.pipeline` | Packs refactored project directory into ZIP | `packageToZip(Path, Path)` |
| `ZipExtractor` | `com.archanalyzer.archive` | Safely extracts uploaded archives | `extract(Path, Path)` |

---

## 3. REFACTORING EXECUTION FLOW

```
[Uploaded Project]
        │
        ▼
[ZipExtractor.extract()] ──► [ProjectModel] ──► [DependencyGraph]
                                                        │
                                                        ▼
                                            [Tarjan's SCC Algorithm]
                                                        │
                                    ┌───────────────────┴───────────────────┐
                                    ▼                                       ▼
                            [No Cycles]                             [Cycles Detected]
                                                                            │
                                                                            ▼
                                                                [CycleBreakingStrategy]
                                                                (Candidate edge chosen)
                                                                            │
                                                                            ▼
                                                                    [ChangeManifest]
                                                                    (Backup files)
                                                                            │
                                                                            ▼
                                                                [AstInterfaceExtractor]
                                                                (Creates IProvider.java,
                                                                 Provider implements IProvider)
                                                                            │
                                                                            ▼
                                                                [AstCallerRewriter]
                                                                (Rewrites caller fields,
                                                                 replaces new Concrete() -> null,
                                                                 wraps calls in null-checks,
                                                                 adds DI constructor & setter)
                                                                            │
                                                                            ▼
                                                                [BuildValidator.compile()]
                                                                (In-memory javac check)
                                                                            │
                                                    ┌───────────────────────┴───────────────────────┐
                                                    ▼ (FAIL)                                        ▼ (PASS)
                                            [manifest.rollbackAll()]                     [Post-Graph SCC Re-Check]
                                                                                                    │
                                                                                    ┌───────────────┴───────────────┐
                                                                                    ▼ (Cycle Not Broken)            ▼ (Cycle Removed)
                                                                            [manifest.rollbackAll()]            [ProjectPackager]
                                                                                                                (Export deliverable ZIP)
```

---

## 4. CRITICAL IMPLEMENTATION FINDINGS & KNOWN LIMITATIONS

### 4.1 Null Dependency Decoupling Risk
In `AstCallerRewriter`:
- When `decoupleConstructorInstantiation=true`, expressions matching `new ConcreteTarget(...)` are replaced with literal `null`.
- In `PaymentService` (`sample-demo-project`), `this.notificationService = new NotificationService();` became `this.notificationService = null;`.
- Although `AstCallerRewriter` wraps invocations `notificationService.sendReceipt(...)` with `if (notificationService != null)`, any logic that relies on `notificationService` actually performing side-effects (e.g. sending confirmation emails or recording audits) **is silently skipped at runtime** if the caller is instantiated using its default constructor without explicit injection.

### 4.2 External Build Tool Test Validation Limitation
In `BuildValidator.executeMavenValidation`:
- The command executed is:
  ```java
  List.of("cmd.exe", "/c", "mvn", "test-compile", "-B", "-DskipTests=true", "-o")
  ```
- **Discovery:** Because `-DskipTests=true` is supplied, Maven only compiles test code; it does NOT execute test cases.
- However, lines 342-343 state:
  ```java
  result.testsExecuted = true;
  result.testsPassed = true;
  ```
- **Risk:** The internal validator reports `testsExecuted=true` and `testsPassed=true` when tests were merely compiled, not executed!

### 4.3 Directory Preservation in ZIP Export
In `ProjectPackager.java`:
- Lines 13-14:
  ```java
  try (Stream<Path> walk = Files.walk(projectDir)) {
      walk.filter(Files::isRegularFile).forEach(...)
  ```
- **Discovery:** Only regular files are added to the export ZIP. Empty directory structures (such as empty resource folders or template dirs) are omitted from the exported archive.

### 4.4 Rollback Boundary
In `ChangeManifest.java`:
- Rollback operates strictly on files that were registered via `recordFileForModification(file)`.
- If an unhandled exception occurs *before* a file is registered or if a secondary step creates files outside `ChangeManifest`, those files would not be rolled back.

---

## 5. EXISTING TESTS RELEVANT TO REFACTORING SAFETY

- `com.archanalyzer.refactor.cycle.CycleRefactoringIntegrationTest`: 20 unit tests covering cycle breaking, AST rewriting, and rollback.
- `com.archanalyzer.refactor.CycleRefactoringServiceIntegrationTest`: 12 integration tests covering service-level execution.
- `com.archanalyzer.refactor.RefactoringEngineTest`: 5 unit tests covering interface extraction and implements header rewriting.
- `com.archanalyzer.validate.ValidationTest`: 8 unit tests covering `BuildValidator` compiler behavior and error isolation.
- `com.archanalyzer.archive.ZipExtractorTest`: 2 unit tests covering zip bomb defence and extraction.
