# PHASE R-I: FULL REGRESSION AUDIT REPORT

**Project:** AI-Powered Dependency & Architecture Refactoring Analyzer  
**Audit Target:** Core Analyzer Regression Testing across Novelties 1, 2, 3, and 4  
**Execution Timestamp:** 2026-11-05 00:03:38  
**Command:** `mvn test -f core/pom.xml`  
**Execution Time:** 01:21 min  
**Status:** **BUILD SUCCESS — 0 FAILURES, 0 ERRORS**  

---

## 1. SUITE EXECUTION SUMMARY

| Test Suite Class | Tests Run | Passed | Failures | Errors | Skipped | Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `AiExplanationServiceTest` | 14 | 14 | 0 | 0 | 0 | **PASS** |
| `LiveGeminiIntegrationTest` | 1 | 0 | 0 | 0 | 1 | **SKIPPED (OFFLINE)** |
| `ZipExtractorTest` | 2 | 2 | 0 | 0 | 0 | **PASS** |
| `ArchitectureExplanationIntegrationTest` (Novelty 4) | 8 | 8 | 0 | 0 | 0 | **PASS** |
| `LanguageDetectorTest` | 5 | 5 | 0 | 0 | 0 | **PASS** |
| `MultiLanguageParserTest` | 4 | 4 | 0 | 0 | 0 | **PASS** |
| `AnalyzerPipelineTest` | 8 | 8 | 0 | 0 | 0 | **PASS** |
| `ProjectDiscoveryTest` | 11 | 11 | 0 | 0 | 0 | **PASS** |
| `WorkflowVerificationTest` | 4 | 4 | 0 | 0 | 0 | **PASS** |
| `SimulationPlannerServiceIntegrationTest` (Novelty 2) | 22 | 22 | 0 | 0 | 0 | **PASS** |
| `CycleRefactoringIntegrationTest` (Novelty 1) | 20 | 20 | 0 | 0 | 0 | **PASS** |
| `CycleRefactoringServiceIntegrationTest` (Novelty 1) | 12 | 12 | 0 | 0 | 0 | **PASS** |
| `RefactoringEngineTest` | 5 | 5 | 0 | 0 | 0 | **PASS** |
| `ArchitectureRiskPredictionIntegrationTest` (Novelty 3) | 14 | 14 | 0 | 0 | 0 | **PASS** |
| `WhatIfSimulatorTest` | 11 | 11 | 0 | 0 | 0 | **PASS** |
| `ValidationTest` | 8 | 8 | 0 | 0 | 0 | **PASS** |
| **TOTAL** | **151** | **150** | **0** | **0** | **1** | **BUILD SUCCESS** |

---

## 2. RESEARCH ARTIFACT FREEZE VERIFICATION

- JIT-SDP Phase 3A research artifacts verified untouched.
- `sample-demo-project` verified 100% untouched.
- Zero regressions detected across all 4 novelties.
