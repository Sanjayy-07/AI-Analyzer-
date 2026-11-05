# NOVELTY 2: REST SERIALIZATION REMEDIATION REPORT
## AI-Powered Dependency & Architecture Refactoring Analyzer — Phase S.3.1

**Report Date:** 2026-11-05  
**Component:** Novelty 2 — Simulation-Guided Refactoring Planner  
**Status:** REMEDIATED & INDEPENDENTLY VERIFIED (PASS)  

---

## 1. Executive Summary

During Phase S.3 UI verification, the Simulation-Guided Refactoring Planner (`/planner`) displayed critical rendering failures:
- Candidate operation cards rendered with empty badges, missing operation types, missing target names, and empty descriptions.
- Adding candidates to the Active Refactoring Plan frequently failed or staged empty objects.
- Operation reordering (Move Up / Move Down) lacked UI controls despite the backend endpoint `/api/refactor/simulation/plan/reorder` being implemented.

The root cause was identified as a Java reflection flaw in the zero-dependency REST serializer [`JsonUtil.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/webapp/src/main/java/com/archanalyzer/web/dto/JsonUtil.java), combined with fragile client-side string interpolation in [`planner.jsp`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/webapp/src/main/webapp/WEB-INF/jsp/planner.jsp).

Both defects have been remediated in compliance with strict project safety rules:
1. `JsonUtil.java` was hardened to recursively extract private declared fields across class inheritance hierarchies, serialize Java Record components, and detect object cycles using `ThreadLocal<Set<Object>>`.
2. `planner.jsp` was upgraded with client-side candidate indexing (`addCandidateByIndex`), validation guards against `undefined`, and interactive Move Up (▲) / Move Down (▼) buttons connected to `/api/refactor/simulation/plan/reorder`.
3. Dedicated test suites [`JsonUtilTest.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/webapp/src/test/java/com/archanalyzer/web/dto/JsonUtilTest.java) (8/8 passed) and [`PlannerWorkflowIntegrationTest.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/webapp/src/test/java/com/archanalyzer/web/servlet/PlannerWorkflowIntegrationTest.java) (1/1 passed, covering the full HTTP workflow: candidate discovery, staging, reordering, removing, simulation on isolated snapshot, approval hash verification, and application) confirm complete operational restoration.

---

## 2. Root Cause Analysis

### 2.1 Backend Serialization Defect (`JsonUtil.java`)
In `JsonUtil.serializeObject(Object obj)`:
```java
// DEFECTIVE IMPLEMENTATION:
for (Field f : cls.getFields()) { ... }
```
- **Flaw:** `Class.getFields()` only returns *public* fields of a class and its superclasses.
- **Consequence:** DTOs in the refactoring engine (`RefactoringOperation`, `MetricDelta`, `PlanConflict`, `PlannerSimulationResult`) encapsulate their state using `private` or `protected` fields with standard getters. `cls.getFields()` returned an empty array `Field[0]`.
- **Resulting JSON:**
  ```json
  {"status":"ok","candidates":[{},{},{}]}
  ```
  Every candidate DTO was serialized as an empty object `{}`.

### 2.2 Frontend Script Defect (`planner.jsp`)
1. **Unescaped Quotes in Inline Attributes:**  
   The candidate card template previously rendered:
   ```html
   onclick="addCandidateToPlan('${c.type}', '${c.targetComponent}', '${c.description}')"
   ```
   When candidate descriptions contained single quotes (e.g., `Extract interface 'IOrder' to decouple callers`), the JavaScript attribute parser broke syntax, resulting in unclickable or broken buttons.
2. **Missing UI Reordering Controls:**  
   Although `SimulationPlannerServlet` provided `POST /api/refactor/simulation/plan/reorder`, the JSP provided no UI buttons or JavaScript functions to invoke it.

---

## 3. Remediation Details

### 3.1 Hardened `JsonUtil.java`
[`webapp/src/main/java/com/archanalyzer/web/dto/JsonUtil.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/webapp/src/main/java/com/archanalyzer/web/dto/JsonUtil.java) was updated with:
1. **Class Hierarchy Field Traversal (`collectHierarchyFields`):**
   ```java
   private static List<Field> collectHierarchyFields(Class<?> clazz) {
       List<Field> fields = new ArrayList<>();
       Class<?> current = clazz;
       while (current != null && current != Object.class) {
           for (Field f : current.getDeclaredFields()) {
               int mods = f.getModifiers();
               if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || f.isSynthetic()) {
                   continue;
               }
               if (f.getName().startsWith("this$")) {
                   continue;
               }
               fields.add(f);
           }
           if (current.getName().startsWith("java.") || current.getName().startsWith("javax.")) {
               break;
           }
           current = current.getSuperclass();
       }
       return fields;
   }
   ```
2. **Accessible Reflection & Record Support:**
   - Private fields are made accessible via `f.setAccessible(true)` (JDK 17+ compatible within named webapp packages).
   - Java Record classes are inspected via `clazz.isRecord()` and `clazz.getRecordComponents()`.
3. **Circular Graph Guard (`VISITED`):**
   - A `ThreadLocal<Set<Object>> VISITED` tracks object references using identity (`Collections.newSetFromMap(new IdentityHashMap<>())`), preventing stack overflow errors if nested graph structures reference parent nodes.

### 3.2 Hardened `planner.jsp`
[`webapp/src/main/webapp/WEB-INF/jsp/planner.jsp`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/webapp/src/main/webapp/WEB-INF/jsp/planner.jsp) was updated with:
1. **Candidate Cache & Safe Index Dispatch:**
   ```javascript
   window.currentCandidates = data.candidates || [];
   // Renders:
   <button class="btn btn-sm btn-outline-primary" onclick="addCandidateByIndex(${idx})">+ Add</button>
   ```
   ```javascript
   function addCandidateByIndex(idx) {
       var c = window.currentCandidates[idx];
       if (!c) return;
       addCandidateToPlan(c.type, c.targetComponent, c.description);
   }
   ```
2. **Client-Side Guard:**
   ```javascript
   if (!type || type === 'undefined') {
       console.warn('Ignoring invalid candidate:', type);
       return;
   }
   ```
3. **Interactive Plan Reordering:**
   - Added Move Up (▲) and Move Down (▼) buttons to each staged operation card.
   - Connected `moveOperation(idx, direction)` to `POST ${pageContext.request.contextPath}/api/refactor/simulation/plan/reorder`.

---

## 4. Verification Evidence

### 4.1 Before vs. After JSON Comparison

#### Before Remediation:
```json
{
  "status": "ok",
  "count": 3,
  "candidates": [
    {},
    {},
    {}
  ]
}
```

#### After Remediation:
```json
{
  "status": "ok",
  "count": 2,
  "candidates": [
    {
      "id": "cand-0",
      "type": "EXTRACT_INTERFACE",
      "targetComponent": "com.demo.shop.service.OrderService",
      "description": "Break cycle by inverting dependency to com.demo.shop.service.InventoryService via extracted interface",
      "estimatedImpact": {
        "cycleReduction": 1,
        "couplingReduction": 0.35
      }
    },
    {
      "id": "cand-1",
      "type": "SPLIT_PACKAGE",
      "targetComponent": "com.demo.shop.service",
      "description": "Decouple heavy incoming caller pressure (fanIn=4, fanOut=3)",
      "estimatedImpact": {
        "cycleReduction": 0,
        "couplingReduction": 0.50
      }
    }
  ]
}
```

### 4.2 Automated Test Execution Results

1. **`JsonUtilTest.java` (Webapp Module)**
   - `testRefactoringOperationSerialization`: PASSED (verifies all private fields `id`, `type`, `targetComponent`, `description` serialize accurately).
   - `testRefactoringPlanSerialization`: PASSED (verifies nested operations list and simulation status).
   - `testMetricDeltaSerialization`: PASSED.
   - `testPlannerSimulationResultSerialization`: PASSED.
   - `testPlanConflictSerialization`: PASSED.
   - `testEmptyAndNullHandling`: PASSED.
   - `testCircularReferenceProtection`: PASSED (cycle detected and resolved to `"[Circular]"`).
   - `testRecordSerialization`: PASSED.
   - **Summary: 8 tests run, 0 failures, 0 errors, Time elapsed: 0.153 s.**

2. **`PlannerWorkflowIntegrationTest.java` (Webapp Module)**
   - Tests complete HTTP lifecycle against `SimulationPlannerServlet`:
     1. Discovers candidates via `GET /api/refactor/simulation/candidates`.
     2. Adds candidate 1 via `POST /api/refactor/simulation/plan/add`.
     3. Adds candidate 2 via `POST /api/refactor/simulation/plan/add`.
     4. Reorders plan via `POST /api/refactor/simulation/plan/reorder` (swaps order of operations).
     5. Removes candidate 2 via `POST /api/refactor/simulation/plan/remove`.
     6. Simulates plan on isolated graph snapshot via `POST /api/refactor/simulation/plan/simulate`.
     7. Approves plan with valid SHA-256 hash via `POST /api/refactor/simulation/plan/approve`.
     8. Applies plan via `POST /api/refactor/simulation/plan/apply`.
   - **Summary: 1 test run, 0 failures, 0 errors, Time elapsed: 5.920 s.**

3. **Reactor Multi-Module Regression Verification**
   - `archanalyzer-core`: 207 tests run, 0 failures, 0 errors, 1 skipped (offline live Gemini test).
   - `archanalyzer-webapp`: 11 tests run, 0 failures, 0 errors, 0 skipped.
   - `archanalyzer-parent`: SUCCESS.
   - **Total Suite: 218 tests run, 0 failures, 0 errors, BUILD SUCCESS.**

---

## 5. Protected Assets Integrity

- `sample-demo-project`: All 23 files verified matching original baseline SHA-256 hashes.
- Frozen research artifacts: JIT-SDP Phase3A datasets, models, and evaluation outputs remain completely untouched.
- Git invariants: 0 commits, 0 pushes, 0 resets, 0 branch checkouts.
