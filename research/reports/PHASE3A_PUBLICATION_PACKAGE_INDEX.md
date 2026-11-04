# PHASE 3A: PUBLICATION PACKAGE REPOSITORY INDEX
## Master Index of Authoritative Research Artifacts & Traceability Links

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Subsystem**: Just-In-Time (JIT) Software Defect Prediction Research Engine  
**Package Scope**: Phase 3A Target-Project Prevalence Adaptation  
**Auditor**: Senior Research Reproducibility Auditor & Experimental Integrity Officer  
**Timestamp**: 2026-11-04T19:55:00+05:30  
**Status**: Authoritative & Frozen  

---

## 1. Master Artifact Directory

All artifacts are linked via relative paths from the repository root. All source files remain in place without copying, moving, or renaming.

### 1.1 Authoritative Research Reports
* **Authoritative Benchmark Report**:  
  [`research/reports/PHASE3A_GATE4_FULL_BENCHMARK_REPORT_GATE43_VERIFIED.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_GATE4_FULL_BENCHMARK_REPORT_GATE43_VERIFIED.md)  
  *Contains complete methodology, experimental setup, Table 2.1 project defect profiles, Table 3.1 macro/micro benchmark results, Table 4.1 per-project breakdown, and Table 5.1 hypothesis testing matrix.*
* **Micro-Metric Reconciliation Report (Gate 4.3.1)**:  
  [`research/reports/PHASE3A_GATE4_3_1_MICRO_METRIC_RECONCILIATION.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_GATE4_3_1_MICRO_METRIC_RECONCILIATION.md)  
  *Authoritative resolution of the Base_K100 confusion matrix contradiction, proving exact canonical counts ($TP=1764, FP=3388, TN=16353, FN=916 \implies 45.05\%$ Micro-F1).*
* **Table Integrity & Metric Semantics Report (Gate 4.3)**:  
  [`research/reports/PHASE3A_GATE4_3_FINAL_TABLE_INTEGRITY_AUDIT.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_GATE4_3_FINAL_TABLE_INTEGRITY_AUDIT.md)  
  *Audit of metric semantics, zero-defect single-class conventions (`commons-exec`), and dual reporting.*
* **Consistency Audit Report (Gate 4.2)**:  
  [`research/reports/PHASE3A_GATE4_2_FINAL_CONSISTENCY_AUDIT.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_GATE4_2_FINAL_CONSISTENCY_AUDIT.md)  
  *Resolves numerical contradictions in baseline and quantile recall.*

### 1.2 Canonical Machine-Readable Metric Data
* **Authoritative Canonical Metrics JSON**:  
  [`research/reports/phase3a_gate4_2_canonical_metrics.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/phase3a_gate4_2_canonical_metrics.json) (52,222 bytes, SHA-256: `e1053c6e154bfb33db36de360f90457c1d20261565fb0527300a610dbec16573`)  
  *Authoritative source for macro-averages, micro-aggregates, and project-level validation metrics.*
* **Comprehensive Full Results JSON**:  
  [`research/reports/phase3a_gate4_full_results.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/phase3a_gate4_full_results.json) (233,632 bytes, SHA-256: `a4fc471694baef576d124302b824c55dc2690016a38b83cef9c48e59536722f3`)  
  *Raw evaluation results across all 9 projects and burn-in values ($K \in \{25, 50, 100, 250, 500\}$).*
* **Statistical Hypothesis Testing JSON**:  
  [`research/reports/phase3a_gate4_statistical_analysis.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/phase3a_gate4_statistical_analysis.json) (3,504 bytes, SHA-256: `993cd97492e974a2d9077f1f3fb3e422ef7696c837b90e6356fa65b86a2a0c1b`)  
  *Exact two-sided permutation p-values, Wilcoxon W statistics, and rank-biserial correlations.*
* **Micro-Metric Reconciliation JSON**:  
  [`research/reports/phase3a_gate431_reconciliation.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/phase3a_gate431_reconciliation.json) (5,167 bytes, SHA-256: `19ab1dacdd2621def4ee7baf90a5bc4b075e2d450ea1c86b3ffad854b04fc785`)  
  *Machine-readable confusion matrix counts and micro-metrics for all 5 adaptation methods.*
* **Execution Manifest JSON**:  
  [`research/reports/phase3a_gate431_execution_manifest.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/phase3a_gate431_execution_manifest.json) (926 bytes, SHA-256: `3778d04e0310ce2ae8bdcac6de6fe1ebff232fb2d66858c54c16bb2eebf3c550`)  
  *Execution provenance, JDK 18 environment details, and file verification records.*

### 1.3 Raw Validation Predictions (Bit-Level Reproducible)
* **Raw Prediction CSV**:  
  [`research/data/gate4_raw_predictions.csv`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/data/gate4_raw_predictions.csv) (3,051,869 bytes, SHA-256: `a1cc86cc3094adf45eea693d964d6f75a743ef20fe2b258700b9fcc5f2f75dec`)  
  *23,321 rows: project_id, original_index, commit_id, timestamp_nanos, committer_date, is_fault_inducing, baseline_probability.*
* **Raw Prediction JSON**:  
  [`research/data/gate4_raw_predictions.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/data/gate4_raw_predictions.json) (7,087,373 bytes, SHA-256: `a26f8edb73aed0b43cbc7176dd15bd24762b48f66e215f1a88a2278135983d19`)  
  *Structured JSON mirror of all 23,321 raw prediction records.*

### 1.4 Benchmark Configuration
* **Benchmark Config JSON**:  
  [`research/configs/phase3a_gate4_full_benchmark.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/configs/phase3a_gate4_full_benchmark.json) (3,065 bytes, SHA-256: `03e18776d58253aefb9850808e9180c881c4f5830e3c7538f082f33e5295e2a7`)  
  *Benchmark parameters, burn-in schedule, 90-day defect maturation window, and source prevalence $\pi_s = 0.11144$.*

### 1.5 Research-Only Java Audit & Verification Code
* **Reconciliation Auditor**: [`research/java/src/main/java/com/archanalyzer/research/adaptation/Phase3aGate431ReconciliationAuditor.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/java/src/main/java/com/archanalyzer/research/adaptation/Phase3aGate431ReconciliationAuditor.java)
* **Reconciliation Test Suite**: [`research/java/src/test/java/com/archanalyzer/research/adaptation/Phase3aGate431ReconciliationAuditorTest.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/java/src/test/java/com/archanalyzer/research/adaptation/Phase3aGate431ReconciliationAuditorTest.java)
* **Table Integrity Auditor**: [`research/java/src/main/java/com/archanalyzer/research/adaptation/Phase3aGate43TableIntegrityAuditor.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/java/src/main/java/com/archanalyzer/research/adaptation/Phase3aGate43TableIntegrityAuditor.java)
* **Table Integrity Test Suite**: [`research/java/src/test/java/com/archanalyzer/research/adaptation/Phase3aGate43TableIntegrityAuditorTest.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/java/src/test/java/com/archanalyzer/research/adaptation/Phase3aGate43TableIntegrityAuditorTest.java)
* **Independent Auditor**: [`research/java/src/main/java/com/archanalyzer/research/adaptation/Phase3aGate42Auditor.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/java/src/main/java/com/archanalyzer/research/adaptation/Phase3aGate42Auditor.java)
* **Audit Test Suite**: [`research/java/src/test/java/com/archanalyzer/research/adaptation/Phase3aGate42AuditorTest.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/java/src/test/java/com/archanalyzer/research/adaptation/Phase3aGate42AuditorTest.java)
* **Comprehensive Test Suite**: [`research/java/src/test/java/com/archanalyzer/research/adaptation/PrevalenceAdaptationComprehensiveTest.java`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/java/src/test/java/com/archanalyzer/research/adaptation/PrevalenceAdaptationComprehensiveTest.java)
* **Maven Configuration**: [`research/java/pom.xml`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/java/pom.xml)

### 1.6 Package Documentation Artifacts
* **Final Freeze Manifest**: [`research/reports/PHASE3A_FINAL_FREEZE_MANIFEST.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_FINAL_FREEZE_MANIFEST.md)
* **Publication Readiness Assessment**: [`research/reports/PHASE3A_PUBLICATION_READINESS_ASSESSMENT.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_PUBLICATION_READINESS_ASSESSMENT.md)
* **Final Research Summary**: [`research/reports/PHASE3A_FINAL_RESEARCH_SUMMARY.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_FINAL_RESEARCH_SUMMARY.md)

---

## 2. Explicitly Excluded & Quarantined Artifacts

In adherence to strict scientific and experimental integrity standards, the following artifacts are strictly excluded from this publication package:
1. **Held-Out Test Dataset**: `dataset-extracted/TD dataset/test.csv` (7,866,054 bytes, SHA-256: `c669378be165e9a92fe9dc6b31d607aba2dc8a4b709e28346c13f76b6b1b7feb`) — **REMAINS 100% QUARANTINED AND UNTOUCHED**.
2. **Test-Project Labels & Predictions**: Zero test labels, test predictions, or test-set performance metrics exist.
3. **Private Credentials & Secrets**: No API keys, credentials, or proprietary environment variables are included.
4. **Intermediate Scratch Logs**: Transient execution scratch files have been omitted to preserve repository cleanliness.
