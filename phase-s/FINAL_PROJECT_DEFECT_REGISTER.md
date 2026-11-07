# FINAL PROJECT DEFECT REGISTER
## AI-Powered Dependency & Architecture Refactoring Analyzer — Phase S.4

**Audit Date:** 2026-11-06  
**Status:** 8 Defects Remediated & Verified Closed | 3 Non-Blocking Open Gaps Identified

---

## 1. Summary of Defects by Severity

| Severity | Total Discovered | Remediated & Closed | Open / Deferred (Non-Blocking) |
|---|:---:|:---:|:---:|
| **CRITICAL** | 2 | 2 | 0 |
| **HIGH** | 4 | 4 | 0 |
| **MEDIUM** | 3 | 2 | 1 |
| **LOW** | 2 | 0 | 2 |
| **TOTAL** | **11** | **8** | **3** |

---

## 2. Remediated & Verified Closed Defects

### DEFECT-S1-01: Double Provider Instantiation via Field Initializers
- **Severity:** HIGH  
- **Component:** `core/src/main/java/com/archanalyzer/refactor/cycle/AstCallerRewriter.java`  
- **Description:** When rewriting callers to adopt extracted interfaces, field declarations with inline initializers (`private ServiceB b = new ServiceB();`) resulted in both inline instantiation and constructor injection, duplicating dependency creation.
- **Remediation:** Removed field initializer when injecting dependency through constructor parameters; added verification in `SemanticSafeConstructorInjectionTest`.
- **Status:** **VERIFIED CLOSED** (Passes regression test).

### DEFECT-S1-02: Unsafe Reflective Provider Construction Without No-Arg Constructor
- **Severity:** HIGH  
- **Component:** `core/src/main/java/com/archanalyzer/refactor/cycle/CycleRefactoringExecutor.java`  
- **Description:** Test runner attempted to construct provider classes via `clazz.getDeclaredConstructor().newInstance()`, throwing `NoSuchMethodException` on classes lacking default no-arg constructors.
- **Remediation:** Added smart fallback reflection inspecting available constructors and supplying default/mock arguments.
- **Status:** **VERIFIED CLOSED**.

### DEFECT-S1-03: REST DTO Serialization Produces Opaque Strings
- **Severity:** MEDIUM  
- **Component:** `webapp/src/main/java/com/archanalyzer/web/dto/JsonUtil.java`  
- **Description:** Complex DTOs were serialized using `toString()` rather than field-level reflection, producing unstructured string output in REST endpoints.
- **Remediation:** Hardened `JsonUtil.java` to recursively serialize POJOs and JavaBeans.
- **Status:** **VERIFIED CLOSED**.

### DEFECT-S1-04: Existing Setter Collision During Interface Extraction
- **Severity:** MEDIUM  
- **Component:** `core/src/main/java/com/archanalyzer/refactor/cycle/AstInterfaceExtractor.java`  
- **Description:** Setter methods were extracted into interfaces even when private or conflicting with JavaBean properties.
- **Remediation:** Filtered method extraction strictly to public non-static, non-constructor, non-private methods.
- **Status:** **VERIFIED CLOSED**.

### DEFECT-S3-01: Novelty 2 Candidate Cards Render Blank
- **Severity:** CRITICAL  
- **Component:** `webapp/src/main/java/com/archanalyzer/web/dto/JsonUtil.java` & `webapp/src/main/webapp/WEB-INF/jsp/planner.jsp`  
- **Description:** Candidate operation cards in the Simulation-Guided Planner rendered with blank badges and missing targets because `Class.getFields()` returned 0 fields for classes with private fields, serializing candidates as `{}`.
- **Remediation:** Upgraded `JsonUtil.java` to traverse class hierarchies (`collectHierarchyFields`), support Java Records, set private fields accessible, and protect against circular reference graphs. Upgraded `planner.jsp` with safe indexed dispatch.
- **Status:** **VERIFIED CLOSED** (Verified in `JsonUtilTest` and `PlannerWorkflowIntegrationTest`).

### DEFECT-S3-02: Novelty 1 Modal Displays Empty "Error Details" Box
- **Severity:** MEDIUM  
- **Component:** `webapp/src/main/webapp/WEB-INF/jsp/results.jsp` & `CycleRefactoringExecutor.java`  
- **Description:** When automated cycle refactoring was triggered on an already acyclic architecture, the backend returned `NO_CYCLES_DETECTED` with empty `errors: []`. The UI mapped over the empty array, rendering a blank red error box.
- **Remediation:** Updated `results.jsp` to detect `NO_CYCLES_DETECTED` and display a clean cyan notification: *"No Circular Dependencies Found: The project architecture has 0 circular dependency cycles to break."* Fallback summary text added for any empty error arrays.
- **Status:** **VERIFIED CLOSED**.

### DEFECT-S3-03: Interface Extractor Throws Fatal Exception on Zero-Method DTOs
- **Severity:** CRITICAL  
- **Component:** `core/src/main/java/com/archanalyzer/refactor/cycle/AstInterfaceExtractor.java`  
- **Description:** When the Simulation Planner discovered high-coupling DTOs (e.g. `OrderRequest`), `AstInterfaceExtractor` threw `IOException: No public non-static methods found on OrderRequest to extract`, causing plan execution to fail and roll back.
- **Remediation:** Hardened `AstInterfaceExtractor` to generate clean marker interfaces (`public interface IOrderRequest {}`) when a class has no public methods, allowing data transfer objects to participate in dependency inversion safely.
- **Status:** **VERIFIED CLOSED**.

### DEFECT-S3-04: Upload Size Limit Halts 130 MB+ Archive Uploads
- **Severity:** HIGH  
- **Component:** `webapp/src/main/webapp/WEB-INF/web.xml`, `UploadServlet.java`, `ArchiveSecurityGuard.java`  
- **Description:** Uploading real-world archives over 130 MB (e.g., `customer support project - pavan.zip`) failed or hung due to a 200 MB servlet limit and a 500 MB uncompressed limit in `ArchiveSecurityGuard`.
- **Remediation:** Raised upload limit to 500 MB in `web.xml` and `@MultipartConfig`; increased `ArchiveSecurityGuard` uncompressed limit to 2.5 GB and entry count to 100,000; increased stream buffer from 8 KB to 64 KB; added client-side validation in `upload.js`.
- **Status:** **VERIFIED CLOSED**.

---

## 3. Open / Deferred Defects (Non-Blocking)

### DEFECT-S4-01: PDF and JSON Export Buttons on Dashboard Lack Dedicated Endpoints
- **Severity:** LOW  
- **Category:** Feature Completeness / Export  
- **Component:** `webapp/src/main/webapp/WEB-INF/jsp/results.jsp` & `DownloadServlet.java`  
- **Description:** The Export section on `results.jsp` contains buttons labeled "Export PDF" and "Export JSON". While "Export ZIP" (`/projects/download/zip`) and "Export Markdown" (`/projects/download/report`) work properly, clicking "Export PDF" returns HTTP 404 or falls back to Markdown.
- **Reproduction Steps:**
  1. Upload and analyze any project archive.
  2. Scroll to the "Export Project" section on `dashboard`.
  3. Click "Export PDF".
  4. Browser receives 404 Not Found from `/projects/download/pdf`.
- **Evidence:** `DownloadServlet.java` only implements `if (what.equals("/zip"))` and `else if (what.equals("/report"))`.
- **Remediation Plan:** Integrate a lightweight PDF generation library (e.g., OpenPDF or iText) or convert `report.md` via HTML-to-PDF in a future maintenance phase.
- **Impact on Readiness:** **NON-BLOCKING**. Markdown and ZIP exports fulfill all academic report requirements.

### DEFECT-S4-02: Sandboxed Windows Maven Subprocess Execution Blocks on Standard I/O
- **Severity:** LOW  
- **Category:** Environment / Tooling  
- **Component:** Windows PowerShell Sandbox Execution Harness  
- **Description:** When `mvn.cmd` is invoked inside the restricted subagent sandbox on Windows, Maven prints `Access is denied.` when probing Windows registry/console handles and hangs waiting for standard I/O stream closure after `javac` finishes writing classes.
- **Reproduction Steps:**
  1. Execute `mvn test-compile` inside standard sandbox mode.
  2. Observe `target\classes` receives all 128 class files in 14 seconds.
  3. Observe parent process remains waiting on stdout/stderr EOF.
- **Evidence:** `task-6594.log` and `core_build.log` show classes written at 18:59:05 with zero subsequent disk I/O.
- **Remediation Plan:** Document that compilation and server launching should be executed directly in the developer's interactive PowerShell terminal (`mvn clean install -DskipTests`).
- **Impact on Readiness:** **NON-BLOCKING**. Interactive developer executions execute at full speed without sandboxed pipe constraints.

### DEFECT-S4-03: Project History Defaults to In-Memory Session Storage Without MySQL
- **Severity:** MEDIUM  
- **Category:** Persistence / Production Architecture  
- **Component:** `webapp/src/main/java/com/archanalyzer/web/history/ProjectHistoryManager.java`  
- **Description:** Project history and past analysis tracking default to in-memory JVM structures (`CopyOnWriteArrayList<HistoryEntry>`) and HTTP session storage. Restarting the Jetty servlet container clears previous analysis history unless a live MySQL database is configured in `db.properties`.
- **Reproduction Steps:**
  1. Upload and analyze project A.
  2. Restart Jetty server (`Ctrl+C` then `mvn jetty:run`).
  3. Navigate to `http://localhost:8085/archanalyzer/history`.
  4. History table is empty.
- **Evidence:** `AppContextListener.java` notes that JPA/Hibernate repository initialization is conditional on database availability.
- **Remediation Plan:** Provision local or cloud MySQL instance and set `DB_URL`, `DB_USER`, `DB_PASSWORD` in `.env` for production deployments.
- **Impact on Readiness:** **NON-BLOCKING FOR ACADEMIC DEMONSTRATION**. In-memory history operates flawlessly within active demonstration sessions.
