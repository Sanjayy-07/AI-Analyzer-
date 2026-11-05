# NOVELTY 2 — REFACTORING PLANNER FUNCTIONALITY AUDIT REPORT
**AI-Powered Dependency & Architecture Refactoring Analyzer**  
**Audit Type:** Read-Only Diagnostic & Architecture Verification  
**Date:** November 5, 2026  
**Status:** AUDIT COMPLETE — ROOT CAUSE IDENTIFIED (READ-ONLY, ZERO PRODUCTION CHANGES)

---

## 1. Executive Summary

An adversarial read-only diagnostic was conducted on the **Novelty 2: Simulation-Guided Refactoring Planner** following reported UI malfunctions where:
- Candidate operation cards appeared blank (empty badges, missing titles, literal `"Target: "` with missing targets).
- The Active Refactoring Plan remained at 0 operations.
- The `+ Add`, `Simulate Combined Plan`, and `Approve & Apply` workflows failed to operate.

### Summary of Diagnostic Findings

1. **Underlying Core Engine is Functionally Sound:**
   The core algorithmic engines—`SimulationPlannerService`, `SimulationEngine`, `RefactoringConflictDetector`, `PostApplicationVerifier`, and graph topology recalculation—are fully implemented, structurally correct, and pass 22/22 unit and integration tests. Candidate discovery correctly discovers cycle-breaking and coupling-reduction candidates from the AST graph.

2. **Severe Serialization Failure at the REST Boundary:**
   The failure is caused by an object serialization defect in `webapp/src/main/java/com/archanalyzer/web/dto/JsonUtil.java`.
   * In `JsonUtil.serializePojo()`, non-record objects are inspected exclusively via `cls.getFields()`, which **only returns `public` fields**.
   * The Novelty 2 domain model classes (`RefactoringOperation`, `RefactoringPlan`, `PlannerSimulationResult`, `MetricDelta`) strictly follow standard Java encapsulation with `private` fields and public getter methods (`getId()`, `getType()`, `getTargetComponent()`, `getDescription()`, `getMetricDeltas()`, etc.).
   * Because none of these classes define public fields, `cls.getFields()` returns an empty array `Field[0]`.
   * Consequently, `JsonUtil.toJson()` serializes every single `RefactoringOperation` as an empty JSON object: `{}`.

3. **Cascading Failure Across Frontend UI:**
   * The Candidates endpoint (`GET /api/refactor/simulation/candidates`) returns `{"status":"ok","totalCandidates":5,"candidates":[{},{},{},{},{}]}`.
   * In `planner.jsp`, JavaScript accesses `c.type`, `c.description`, and `c.targetComponent`, which evaluate to `undefined`.
   * Cards render with empty badge pills, blank title headers, and literal `"Target: "` strings.
   * Clicking `+ Add` triggers `addCandidateToPlan('undefined', 'undefined', '', '', 'undefined')`.
   * The backend receives `type=undefined`, throws `IllegalArgumentException: No enum constant OperationType.UNDEFINED`, and responds with **HTTP 400 Bad Request**.
   * The operation is never added to the plan, leaving the active plan locked at 0 operations.
   * Even if operations were staged, `/api/refactor/simulation/plan` and `/api/refactor/simulation/simulate` similarly serialize operations and simulation results as `{}`.

---

## 2. Confirmed Working Features vs. Confirmed Broken Features

| Component / Feature | Operational Status | Verification Evidence |
| :--- | :---: | :--- |
| **Candidate Discovery Algorithm** | **WORKING** | `SimulationPlannerService.discoverCandidateOperations()` successfully analyzes the graph, discovers cyclic edges via Tarjan SCC / CycleBreakingStrategy, and discovers high-fan-in/fan-out coupling nodes. |
| **Non-Cycle Coupling Discovery** | **WORKING** | Evaluates `fanIn >= 2 && fanOut >= 1` and extracts `EXTRACT_INTERFACE` candidate operations independently of whether cycles exist. |
| **Graph Isolation & Simulation Engine** | **WORKING** | In-memory graph cloning, non-additive Martin metric recalculation, and multi-operation delta derivation pass all unit tests in `SimulationPlannerServiceIntegrationTest`. |
| **Structural Conflict Detector** | **WORKING** | Detects mutually exclusive and dependent structural modifications before plan execution. |
| **Candidate API REST Serialization** | **BROKEN** | `JsonUtil.toJson` outputs `[{}, {}, ...]` because `RefactoringOperation` fields are private. |
| **Frontend Candidate Cards Rendering** | **BROKEN** | Cards render empty badges, blank titles, and missing targets due to empty JSON payloads. |
| **Add Candidate Button (`+ Add`)** | **BROKEN** | Passes `'undefined'` arguments to `/api/refactor/simulation/plan/operation`, triggering HTTP 400 Bad Request on the backend. |
| **Active Plan Retrieval API** | **BROKEN** | `operations` array in `/api/refactor/simulation/plan` returns `[{}]`, rendering `undefined` in plan items. |
| **Combined Simulation API** | **BROKEN** | Blocked when plan is empty; when populated, serializes `PlannerSimulationResult` as `{}`. |
| **Approve & Apply UI Workflow** | **BLOCKED** | Gated on valid non-empty simulation results; completely unreachable from browser UI. |

---

## 3. Investigation Task Results

### Task 1: Candidate Generation
* **Where generated:** Inside `SimulationPlannerService.discoverCandidateOperations(Path projectRoot, ProjectModel model, DependencyGraph graph)`.
* **Which service generates them:** `SimulationPlannerService` using `CycleBreakingStrategy` (Novelty 1) and `MetricsEngine`.
* **Which endpoint returns them:** `GET /api/refactor/simulation/candidates`, handled by `SimulationPlannerServlet.handleCandidates()`.
* **Are candidates generated when circular dependencies = 0?** **YES.** Lines 107–115 scan all vertices in `graph.vertices()`. Any class with `fanIn >= 2 && fanOut >= 1` produces an `EXTRACT_INTERFACE` candidate operation.
* **Are coupling-based candidates generated independently of cycles?** **YES.** The coupling-based candidate generation operates on graph degree metrics independently of cycle presence.
* **Does the backend response contain valid data?** The Java `List<RefactoringOperation>` contains valid instances (e.g. `type=BREAK_CYCLE`, `targetComponent=com.demo.shop.service.OrderService`, `description="Break cycle by extracting..."`).
* **Does the HTTP response contain valid data?** **NO.** The JSON response body emitted over the network is:
  ```json
  {"status":"ok","totalCandidates":5,"candidates":[{},{},{},{},{}]}
  ```
  The candidate list has size 5, but every candidate object is completely empty `{}`.

---

### Task 2: Frontend Rendering Diagnosis
* **Where:** `webapp/src/main/webapp/WEB-INF/jsp/planner.jsp` (lines 559–584).
* **Code Trace:**
  ```javascript
  list.innerHTML = json.candidates.map(c => `
      <div class="candidate-item">
          <div class="op-info">
              <span class="op-badge ${getBadgeClass(c.type)}">${esc(c.type)}</span>
              <div class="op-title">${esc(c.description)}</div>
              <div class="op-desc">Target: ${esc(c.targetComponent || '')} ${c.secondaryComponent ? '⇄ ' + esc(c.secondaryComponent) : ''}</div>
          </div>
          <button type="button" class="btn-add" onclick="addCandidateToPlan('${esc(c.type)}', '${esc(c.targetComponent)}', '${esc(c.secondaryComponent || '')}', '${esc(c.newComponent || '')}', '${esc(c.description)}')">
              + Add
          </button>
      </div>
  `).join('');
  ```
* **Visual Symptoms Explained:**
  * `c.type` is `undefined`: `getBadgeClass(undefined)` falls back to `'badge-redirect'` (green border/background). `esc(undefined)` renders `""`. Result: an empty green badge pill.
  * `c.description` is `undefined`: `esc(undefined)` renders `""`. Result: empty title line.
  * `c.targetComponent` is `undefined`: `esc(undefined || '')` renders `""`. Result: the literal string `"Target: "` with no target name following it.
  * Button `onclick` generates: `addCandidateToPlan('undefined', 'undefined', '', '', 'undefined')`.
* **Does CSS hide text?** **NO.** The DOM elements are rendered with empty inner text because the parsed JSON object contains no properties.
* **Does Refresh request new candidates?** **YES.** Clicking `🔄 Refresh` invokes `loadCandidates()`, which issues a `GET` request to `/api/refactor/simulation/candidates`, but receives the same empty JSON objects.

---

### Task 3: Add Operation Test Results
* **Client Call:**
  Clicking `+ Add` executes:
  ```javascript
  addCandidateToPlan('undefined', 'undefined', '', '', 'undefined')
  ```
* **HTTP Request Emitted:**
  * **Method:** `POST`
  * **Endpoint:** `/api/refactor/simulation/plan/operation`
  * **Headers:** `Content-Type: application/x-www-form-urlencoded`
  * **Payload:** `type=undefined&target=undefined&secondary=&newComponent=&description=undefined`
* **Backend Processing in `SimulationPlannerServlet.handleAddOperation`:**
  ```java
  String typeStr = req.getParameter("type"); // "undefined"
  ...
  try {
      type = OperationType.valueOf(typeStr.toUpperCase()); // Throws IllegalArgumentException!
  } catch (IllegalArgumentException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Unknown operation type: " + typeStr)));
      return;
  }
  ```
* **HTTP Response Received:**
  * **Status Code:** `400 Bad Request`
  * **Body:** `{"error":"Unknown operation type: undefined"}`
* **Consequence:** The operation is rejected. `loadActivePlan()` is called, which queries `GET /api/refactor/simulation/plan`. The plan remains at `operationsCount: 0`. The UI displays:
  *"No operations in plan. Add operations from the candidates browser on the left."*

---

### Task 4: Simulation Test Results
* When `Active Refactoring Plan` has 0 operations:
  * The frontend button `⚡ Simulate Combined Plan` has:
    ```javascript
    document.getElementById('simulatePlanBtn').disabled = (json.operationsCount === 0);
    ```
  * The button is disabled.
  * If invoked directly via HTTP `POST /api/refactor/simulation/simulate`, the servlet checks:
    ```java
    if (plan == null || plan.isEmpty()) {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Plan is empty. Please add at least one operation to simulate.")));
        return;
    }
    ```
  * Response: **HTTP 400 Bad Request** (`{"error":"Plan is empty. Please add at least one operation to simulate."}`).
* When populated with a valid operation (e.g. via direct API test):
  * Simulation calculates delta metrics correctly.
  * BUT when serializing `PlannerSimulationResult`:
    ```java
    data.put("simulation", result);
    resp.getWriter().write(JsonUtil.toJson(data));
    ```
  * `JsonUtil.toJson` serializes `PlannerSimulationResult` as `{}` because all its fields (`metricDeltas`, `tradeoffs`, `addedNodes`, `conflicts`) are private.
  * Network response: `{"status":"ok","simulation":{}}`.
  * In `renderSimulationResults(sim)`:
    `sim.metricDeltas` is `undefined`, resulting in empty table rendering.

---

### Task 5: Approval & Application Workflow
* Approval requires `simulationHash` matching `currentPlanHash`.
* In `planner.jsp`, `approveBtn` is inside `#simulationSection`, which is hidden (`display: none`) until a simulation succeeds.
* Because the simulation section is never rendered, `Approve Plan` and `Apply Approved Plan to Codebase` cannot be reached or clicked by the user.

---

## 6. API Request & Response Trace Matrix

| Endpoint | Method | Request Payload | Response Status | Actual Response Body | Root Cause |
| :--- | :---: | :--- | :---: | :--- | :--- |
| `/api/refactor/simulation/candidates` | `GET` | *(none)* | `200 OK` | `{"candidates":[{}],"status":"ok"}` | `JsonUtil.serializePojo` ignores private fields and getters. |
| `/api/refactor/simulation/plan/operation` (via UI) | `POST` | `type=undefined&target=undefined&secondary=&newComponent=&description=undefined` | `400 Bad Request` | `{"error":"Unknown operation type: undefined"}` | Frontend receives `{}` candidate and submits `'undefined'`. |
| `/api/refactor/simulation/plan/operation` (Valid) | `POST` | `type=BREAK_CYCLE&target=com.demo.A&secondary=com.demo.B&newComponent=com.demo.IB&description=Test` | `200 OK` | `{"isStale":true,"lifecycle":"DRAFT","name":"Refactoring Plan","operations":[{}],"operationsCount":1,"planHash":"...","planId":"plan-xxx","status":"ok"}` | Operation added to backend plan, but serialized as `{}` in `operations`. |
| `/api/refactor/simulation/plan` | `GET` | *(none)* | `200 OK` | `{"isStale":true,"lifecycle":"DRAFT","name":"Refactoring Plan","operations":[{}],"operationsCount":1,"planHash":"...","planId":"plan-xxx","status":"ok"}` | `plan.getOperations()` serialized as `[{}]`. |
| `/api/refactor/simulation/simulate` | `POST` | *(none)* | `200 OK` | `{"simulation":{},"status":"ok"}` | `PlannerSimulationResult` serialized as `{}`. |

---

## 7. Root Cause Analysis

### Exact File & Code Responsible
* **File:** `webapp/src/main/java/com/archanalyzer/web/dto/JsonUtil.java`
* **Method:** `serializePojo(Object value, StringBuilder sb)`
* **Lines:** 87–117
* **Code Block:**
  ```java
  private static void serializePojo(Object value, StringBuilder sb) {
      sb.append('{');
      boolean first = true;
      Class<?> cls = value.getClass();

      if (cls.isRecord()) {
          for (RecordComponent rc : cls.getRecordComponents()) {
              ...
          }
      } else {
          for (Field field : cls.getFields()) {
              if (Modifier.isStatic(field.getModifiers())) continue;
              if (!first) sb.append(',');
              first = false;
              sb.append('"').append(escape(field.getName())).append("\":");
              try {
                  write(field.get(value), sb);
              } catch (Exception e) {
                  sb.append("null");
              }
          }
      }
      sb.append('}');
  }
  ```

### Mechanism of Defect
1. `cls.getFields()` returns only **public member fields** of the class or its superclasses.
2. In Novelty 1 DTOs (`CycleAnalysisDto`, `CycleExecutionDto`, `CandidateEdgeDto`), all fields were declared `public int totalCyclicSccs;`, so `cls.getFields()` succeeded.
3. In Novelty 2 domain model classes:
   * `RefactoringOperation.java`:
     All fields are `private final` with public getter methods (`getId()`, `getType()`, `getTargetComponent()`, `getDescription()`, `getSecondaryComponent()`, etc.).
   * `PlannerSimulationResult.java`:
     All fields are `private` with public getter methods (`isSuccess()`, `getMetricDeltas()`, `getTradeoffs()`, `getAddedNodes()`, etc.).
   * `MetricDelta.java`:
     All fields are `private` with public getter methods.
4. When `cls.getFields()` is invoked on any Novelty 2 object, it returns an empty array `[]`.
5. The loop executes zero iterations, emitting `{}` for every Novelty 2 object.
6. The frontend receives empty JSON objects and fails at every stage of the user interaction.

---

## 8. Minimal Recommended Remediation (For Future Implementation Phase)

To permanently resolve this without introducing third-party dependencies:

1. **Enhance `JsonUtil.serializePojo` in `JsonUtil.java`:**
   * For non-record classes in `com.archanalyzer`:
     * Inspect public `get*()` and `is*()` getter methods (or `cls.getDeclaredFields()` with `field.setAccessible(true)`).
     * Filter out `getClass()`, `static` fields, and synthetic compiler fields.
     * This immediately outputs full, structured JSON for `RefactoringOperation`, `PlannerSimulationResult`, `MetricDelta`, and any other encapsulated domain models.

2. **Add Frontend Guard Check in `planner.jsp`:**
   * In `addCandidateToPlan()`, check `if (!type || type === 'undefined') return;` to prevent submitting invalid parameters if an empty payload is ever received.

---

## 9. End-to-End Usability Assessment

* **Is the planner usable end-to-end currently?** **NO.**
* The user cannot view candidate names, cannot add candidates to the plan, cannot simulate the plan, and cannot approve or apply refactoring changes through the web UI.

---

## 10. Final Audit Verdict

# FINAL VERDICT: FAIL (UI & REST Serialization Layer)

* **Core Refactoring & Simulation Engine:** PASS (100% algorithmically sound, 22/22 unit tests passing).
* **Novelty 2 Web UI & REST API Layer:** FAIL (Blocked by `JsonUtil` private field serialization defect).
* **Data Integrity & Project Safety:** PASS (All protection rules observed, sample-demo-project 100% untouched).
