# PHASE 3A GATE 4.1: INDEPENDENT STATISTICAL AUDIT REPORT

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Component**: Just-In-Time (JIT) Software Defect Prediction Research Benchmark (`TD dataset`)  
**Audit Stage**: Phase 3A Gate 4.1 Independent Statistical Verification & Report Correction  
**Auditor**: Independent Statistical Auditor, ML Evaluation Specialist & Defect Prediction Researcher  
**Date**: November 2026  
**Audit Verdict**: **CONDITIONAL PASS**  

---

## 1. Executive Summary & Audit Verdict

This audit independently verifies the experimental artifacts, metric calculations, evaluation windows, 90-day defect maturation queues, and statistical significance claims produced in **Phase 3A Gate 4**.

### Final Audit Verdict: **CONDITIONAL PASS**

**Justification**:
1. **Core Data & Model Integrity Verified (PASS)**: The underlying raw prediction data (`research/data/gate4_raw_predictions.csv`, 23,321 commits) is intact, reproducible, and mathematically authentic. The frozen model (`SUB-1__XGBoost`, 43 features) was not altered, retrained, or tuned during adaptation.
2. **Quarantine Verified (PASS)**: Zero bytes of the 6 held-out test projects (`archiva`, `collections`, `commons-cli`, `dbcp`, `santuario`, `validator`) were accessed or evaluated.
3. **Evaluation Window Discrepancy Resolved (CORRECTED)**: The reported discrepancy between $N=23,321$ (Full Validation Set) and $N=22,421$ (Post-Burn-in Window) is verified as the exact exclusion of $9 \times 100 = 900$ burn-in commits. While the underlying Java statistical tests were paired on the identical 22,421 commits, Table 1 in the original Gate 4 report conflated the full-sample baseline with post-burn-in models without providing the aligned baseline row. This has been resolved by separating full-sample and post-burn-in tables.
4. **Zero-Defect Mathematical Imputation Resolved (CORRECTED)**: In `commons-exec` (0 defective commits), ROC-AUC is mathematically undefined. Reporting $0.5000$ was an arbitrary imputation convention that artificially deflated Macro ROC-AUC from $0.8020$ (8 valid projects) to $0.7685$ (9 projects). Both 8-project defined and 9-project imputed averages are now documented.
5. **Statistical Significance Claims Calibrated (CORRECTED)**: Quantile threshold adaptation improved macro precision from $27.57\% \to 31.69\%$ ($+4.12\%$ mean gain across 6 of 9 projects). However, the paired Wilcoxon test yields $W = 10.0, p = 0.3125$. This is **not statistically significant** at $\alpha = 0.05$ and is properly re-characterized as an exploratory positive operational trend.

---

## 2. Scope & Protected Assets Verification

| Asset | Status | Verification Mechanism |
| :--- | :---: | :--- |
| **Frozen SUB-1 XGBoost Model** | **UNTOUCHED** | File hash and hyperparameter configurations unchanged; zero retraining. |
| **43 Model Feature Columns** | **UNTOUCHED** | Feature channel dimensions verified ($X \in \mathbb{R}^{23321 \times 43}$). |
| **6 Quarantined Test Projects** | **100% UNTOUCHED** | Verified disjoint set intersection: $\text{Val} \cap \text{Test} = \emptyset$. |
| **Production Core Java Engine** | **UNTOUCHED** | Zero files in `core/` modified; all 73 core Maven tests passing. |
| **Java-First Implementation** | **SATISFIED** | All recomputations and statistical tests executed in Java (`Phase3aIndependentAuditor.java`). |

---

## 3. Read-Only Artifact Inventory

The following underlying prediction and configuration artifacts were verified on disk prior to recomputation:

| Artifact Path | Size (Bytes) | SHA-256 / File Status | Role in Audit |
| :--- | :---: | :--- | :--- |
| `research/data/gate4_raw_predictions.csv` | 3,051,869 | Intact (23,321 rows + header) | Ground-truth commit records, timestamps, and model probabilities. |
| `research/data/gate4_raw_predictions.json` | 7,087,373 | Intact JSON structure | Structured project-level commit records. |
| `research/configs/phase3a_gate4_full_benchmark.json`| 3,065 | Intact JSON | Experiment configuration and hyperparameter definitions. |
| `research/reports/phase3a_gate4_full_results.json` | 233,632 | Intact JSON | Original Gate 4 runner output. |
| `research/reports/phase3a_gate4_statistical_analysis.json`| 3,504 | Intact JSON | Original statistical tests output. |
| `research/reports/PHASE3A_GATE4_FULL_BENCHMARK_REPORT.md`| 22,395 | Intact Markdown | Original Gate 4 research report (preserved unchanged). |

---

## 4. Evaluation-Window & Sample-Size Audit

### 4.1 Resolution of the 23,321 vs 22,421 Sample Discrepancy
The audit investigated why the baseline had $N=23,321$ commits while burn-in adaptation had $N=22,421$ commits:
- **Root Cause**: Burn-in experimentation at $K=100$ commits reserves the first 100 chronological commits of each project for historical defect rate estimation. Across 9 validation projects, exactly $9 \times 100 = 900$ commits form the burn-in window.
- **Evaluation Window**: Operational prediction is evaluated strictly on the post-burn-in window $[100, N)$, which contains $23,321 - 900 = \mathbf{22,421}$ commits.
- **Paired Audit Verification**: The Java benchmark runner *did* evaluate the baseline (`Base_K100_TauStar`) on the aligned 22,421 commits. However, Table 1 in the narrative report presented the full-sample baseline ($N=23,321$) directly adjacent to the post-burn-in adapted models ($N=22,421$), which conflated different evaluation windows.

### Table 4.1: Aligned Evaluation Window Audit per Project ($K=100$)

| Project ID | Total Available $N$ | Burn-in Commits ($K$) | Aligned Evaluation $N$ | Matured Labels (90d) | Immature Excluded | Matured Defect Rate | Fallback Used |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`org.apache:batik`** | 7,015 | 100 | **6,915** | 0 / 100 | 100 (100%) | 0.00% | **Yes (0 Matured)** |
| **`org.apache:codec`** | 2,144 | 100 | **2,044** | 56 / 100 | 44 (44%) | 17.86% | No |
| **`org.apache:commons-exec`** | 700 | 100 | **600** | 75 / 100 | 25 (25%) | 0.00% | No (0 Defects) |
| **`org.apache:commons-fileupload`**| 1,180 | 100 | **1,080** | 98 / 100 | 2 (2%) | 14.29% | No |
| **`org.apache:commons-jelly`** | 1,945 | 100 | **1,845** | 13 / 100 | 87 (87%) | 7.69% | No |
| **`org.apache:daemon`** | 1,251 | 100 | **1,151** | 62 / 100 | 38 (38%) | 27.42% | No |
| **`org.apache:digester`** | 2,192 | 100 | **2,092** | 61 / 100 | 39 (39%) | 37.70% | No (Defect Burst) |
| **`org.apache:httpclient`** | 3,290 | 100 | **3,190** | 53 / 100 | 47 (47%) | 43.40% | No |
| **`org.apache:httpcore`** | 3,604 | 100 | **3,504** | 0 / 100 | 100 (100%) | 0.00% | **Yes (0 Matured)** |
| **Total Validation** | **23,321** | **900** | **22,421** | **418 / 900** | **482 (53.6%)** | — | — |

---

## 5. 90-Day Maturation Queue & Causal Leakage Audit

### 5.1 Verification of Label Maturity Enforcement
The causal constraint requires:
$$\text{Matured}(j) \iff T_j + 90\text{ days} \le T_{\text{eval\_start}}$$
- **High-Velocity Fallback**: In `batik` and `httpcore`, 100 commits occur in 18 and 58 calendar days respectively. Because $T_j + 90\text{d} > T_{\text{eval\_start}}$ for all $j \in [0, 100)$, exactly **0 labels matured**. The engine safely executed fallback to the source training prior ($\pi_s = 0.1114$), preventing premature label leakage.
- **Zero Future Contamination**: An automated scan of commit timestamps confirmed that zero labels from $t \ge T_{\text{eval\_start}}$ entered the burn-in prevalence estimator.

---

## 6. Zero-Defect Project Mathematical Treatment (`commons-exec`)

The project `commons-exec` contains $N=700$ commits and **0 ground-truth defects**.

### 6.1 Classification Metrics
- Baseline at $\tau^* = 0.170$: $TP = 0, FP = 9, TN = 691, FN = 0$.
- Accuracy: $98.71\%$. False Discovery Count: 9 commits.
- $F1$, Precision, Recall: Because $TP = 0$ and total defects $= 0$, Precision and Recall are mathematically $0/0$. In accordance with standard machine learning conventions, these are reported as **0.0000 (convention)**, indicating no defects detected.

### 6.2 Ranking Metrics (ROC-AUC & PR-AUC)
- **ROC-AUC**: Mathematically **undefined** when a dataset contains only negative instances ($N_{\text{pos}} = 0$). Reporting $0.5000$ is an imputation convention, not an empirical measurement. Averaging $0.5000$ into the 9-project macro-average deflated Macro ROC-AUC from **$0.8020$** (across the 8 defined projects) to **$0.7685$**.
- **PR-AUC**: Also mathematically degenerate ($0.0000$ by convention). Averaging $0.0000$ deflated Macro PR-AUC from **$0.3985$** (8 valid projects) to **$0.3542$**.

---

## 7. Macro-Average versus Micro-Pooled Independent Recomputation

The audit independently recomputed both Macro-Averaged (unweighted per project) and Micro-Pooled (aggregated counts) metrics directly from raw predictions:

### Table 7.1: Full Validation Set ($N=23,321$ Commits)

| Method | Aggregation | Evaluated $N$ | Precision | Recall | F1-Score | MCC | PR-AUC (8 Valid) | PR-AUC (All 9) | ROC-AUC (8 Valid) | ROC-AUC (All 9) | Brier Score | ECE |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **A0: Baseline ($\tau=0.50$)** | **Macro** | 23,321 | 50.02% | 15.85% | 0.2285 | +0.2240 | 0.3985 | 0.3542 | 0.8020 | 0.7685 | 0.0814 | 0.0217 |
| | **Micro** | 23,321 | 58.33% | 20.74% | 0.3060 | +0.3005 | — | — | — | — | 0.0850 | — |
| **A1: Baseline ($\tau^*=0.170$)**| **Macro** | 23,321 | 27.92% | 54.77% | **0.3630** | +0.2822 | **0.3985** | **0.3542** | **0.8020** | **0.7685** | **0.0814** | **0.0217** |
| | **Micro** | 23,321 | 33.99% | 66.18% | 0.4492 | +0.3727 | — | — | — | — | 0.0850 | — |
| **A1-Soft: Unlabeled Soft** | **Macro** | 23,321 | 28.16% | 55.95% | 0.3335 | +0.2552 | 0.3985 | 0.3542 | 0.8020 | 0.7685 | 0.0867 | 0.0480 |
| | **Micro** | 23,321 | 29.26% | 68.95% | 0.4109 | +0.3324 | — | — | — | — | 0.0928 | — |

---

### Table 7.2: Aligned Post-Burn-in Evaluation Window ($N=22,421$ Commits, $K=100$)

| Method | Aggregation | Evaluated $N$ | Precision | Recall | F1-Score | MCC | PR-AUC (8 Valid) | PR-AUC (All 9) | ROC-AUC (8 Valid) | ROC-AUC (All 9) | Brier Score | ECE |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Base\_K100 (Aligned Base)** | **Macro** | 22,421 | 27.57% | 53.87% | **0.3563** | +0.2784 | **0.3882** | **0.3450** | **0.8023** | **0.7687** | **0.0792** | **0.0209** |
| | **Micro** | 22,421 | 34.24% | 65.82% | 0.4505 | +0.3752 | — | — | — | — | 0.0838 | — |
| **Bayes $M=10$ ($K=100, 90\text{d}$)** | **Macro** | 22,421 | 24.18% | 60.48% | 0.3116 | +0.2379 | 0.3882 | 0.3450 | 0.8023 | 0.7687 | 0.0909 | 0.0701 |
| | **Micro** | 22,421 | 26.91% | 71.31% | 0.3908 | +0.3139 | — | — | — | — | 0.0936 | — |
| **Bayes $M=20$ ($K=100, 90\text{d}$)** | **Macro** | 22,421 | 24.27% | 60.71% | 0.3207 | +0.2455 | 0.3882 | 0.3450 | 0.8023 | 0.7687 | 0.0885 | 0.0617 |
| | **Micro** | 22,421 | 27.77% | 71.53% | 0.4000 | +0.3251 | — | — | — | — | 0.0915 | — |
| **Bayes $M=50$ ($K=100, 90\text{d}$)** | **Macro** | 22,421 | 24.52% | 59.73% | 0.3280 | +0.2531 | 0.3882 | 0.3450 | 0.8023 | 0.7687 | 0.0848 | 0.0483 |
| | **Micro** | 22,421 | 29.44% | 70.67% | 0.4157 | +0.3420 | — | — | — | — | 0.0884 | — |
| **Quantile Thresh ($K=100$)** | **Macro** | 22,421 | **31.69%** | 42.75% | 0.3261 | +0.2631 | 0.3882 | 0.3450 | 0.8023 | 0.7687 | **0.0792** | **0.0209** |
| | **Micro** | 22,421 | 33.39% | 43.43% | 0.3776 | +0.2835 | — | — | — | — | 0.0838 | — |

---

## 8. Statistical Test Verification (Paired Wilcoxon Signed-Rank Test)

All paired tests were independently calculated using the exact small-sample permutation distribution across the $N=9$ projects:

### Table 8.1: Audited Statistical Significance Results

| Comparison | Target Metric | Baseline Macro | Adapted Macro | Mean Difference | Wilcoxon $W$ | Exact Two-Sided $p$-value | Rank-Biserial $r_{\text{rb}}$ | Wins / Losses / Ties | Auditor Assessment |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Soft Mean vs Baseline** | F1-Score | 0.3630 | 0.3335 | -0.0296 | 9.0 | $p = 0.2500$ | -0.500 | 3 / 5 / 1 | Not Significant |
| | Precision | 0.2792 | 0.2816 | +0.0024 | 14.0 | $p = 0.6406$ | -0.222 | 3 / 5 / 1 | Not Significant |
| **Bayes $M=10$ vs Base ($K=100$)** | F1-Score | 0.3563 | 0.3116 | -0.0447 | 0.0 | $\mathbf{p = 0.0312^*}$ | -1.000 | 0 / 6 / 3 | **Significant Decrease** |
| | Precision | 0.2757 | 0.2418 | -0.0339 | 4.0 | $p = 0.2188$ | -0.619 | 1 / 5 / 3 | Not Significant |
| **Bayes $M=20$ vs Base ($K=100$)** | F1-Score | 0.3563 | 0.3207 | -0.0356 | 2.0 | $p = 0.0938$ | -0.810 | 1 / 5 / 3 | Marginally Significant |
| **Bayes $M=50$ vs Base ($K=100$)** | F1-Score | 0.3563 | 0.3280 | -0.0283 | 2.0 | $p = 0.0938$ | -0.810 | 1 / 5 / 3 | Marginally Significant |
| **Quantile Thresh vs Base ($K=100$)**| Precision | 0.2757 | 0.3169 | **+0.0412** | 10.0 | $p = 0.3125$ | **+0.444** | **6 / 2 / 1** | **Not Significant (Trend)** |
| | Recall | 0.5387 | 0.4275 | -0.1112 | 4.0 | $p = 0.0625$ | -0.778 | 1 / 7 / 1 | Expected Trade-off |
| | F1-Score | 0.3563 | 0.3261 | -0.0302 | 5.0 | $p = 0.0781$ | -0.722 | 2 / 6 / 1 | Marginally Significant |

*Critical Correction on Precision*: The original narrative described the $+4.12\%$ precision increase as a "substantial empirical gain." The audit confirms that with $W = 10.0$ and $p = 0.3125$, the improvement is **statistically non-significant** at standard significance thresholds ($\alpha = 0.05$). It represents a positive operational tendency in 6 of 9 projects, but does not constitute statistical proof of superiority.

---

## 9. Ranking Invariance Theorem Verification

The audit mathematically and numerically verified the **Ranking Invariance Theorem**:
$$\text{logit}(p^*) = \text{logit}(p) - \text{logit}(\pi_s) + \text{logit}(\hat{\pi}_t)$$
Since the scalar shift $\Delta = \text{logit}(\hat{\pi}_t) - \text{logit}(\pi_s)$ is constant across all commits within a project, and both $\text{logit}$ and $\sigma$ are strictly monotonic, the ordering of probability predictions is invariant:
$$p_i < p_j \iff p_i^* < p_j^*$$
- **Verification Result**: Across all 9 validation projects, the PR-AUC and ROC-AUC of A1-SoftMean and all Bayesian log-odds adapted models matched the baseline down to **$10^{-12}$ precision**.
- **Scientific Implication**: Prior log-odds adaptation cannot expand the ROC or PR curve. It strictly alters the decision threshold operating point on the existing curve.

---

## 10. Audit Findings Inventory

| Finding ID | Severity | Title | Summary of Issue | Resolution in Audited Report |
| :---: | :---: | :--- | :--- | :--- |
| **F-01** | **MAJOR** | Evaluation Window Sample Conflation | Table 1 presented Full Baseline ($N=23,321$) directly alongside Burn-In models ($N=22,421$) without showing the aligned baseline. | Separated Full-Sample ($N=23,321$) and Aligned Post-Burn-in ($N=22,421$) tables. |
| **F-02** | **MODERATE** | ROC-AUC Imputation on Zero-Defect Project | Reporting 0.5000 for `commons-exec` deflated Macro ROC-AUC from 0.8020 (8 valid) to 0.7685. | Documented both 8-Project Defined (0.8020) and 9-Project Imputed (0.7685) averages. |
| **F-03** | **MODERATE** | Table 1 Precision-Recall Alignment | Transposed aligned post-burn-in baseline precision (27.57%) and recall (60.29%) into full-sample baseline row. | Restored true full-sample baseline precision (27.92%) and recall (54.77%). |
| **F-04** | **MODERATE** | Precision Statistical Significance Claim | Claimed $+4.12\%$ precision improvement without stating it is statistically non-significant ($p=0.3125$). | Explicitly reported $W=10.0, p=0.3125$ and re-characterized as an operational trend. |
| **F-05** | **INFORMATIONAL**| Monotonic Ranking Invariance Confirmed | Verified exact bit-level invariance of PR-AUC and ROC-AUC under scalar prior odds adjustments. | Formally integrated the Monotonic Invariance Theorem into report narrative. |

---

## 11. Final Audit Verdict & Recommendations

### Verdict: **CONDITIONAL PASS**
The underlying experimental architecture, Java adaptation engine, and raw prediction artifacts are technically sound, reproducible, and strictly leakage-free. The conditions of the pass require adopting the corrected, fully aligned research report ([`PHASE3A_GATE4_FULL_BENCHMARK_REPORT_AUDITED.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_GATE4_FULL_BENCHMARK_REPORT_AUDITED.md)).

**Recommendations**:
1. Use [`PHASE3A_GATE4_FULL_BENCHMARK_REPORT_AUDITED.md`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/PHASE3A_GATE4_FULL_BENCHMARK_REPORT_AUDITED.md) as the authoritative scientific manuscript for Phase 3A.
2. In all future publications, explicitly state both the 8-project defined and 9-project imputed ranking metrics to avoid zero-defect ambiguity.
3. Keep the 6 held-out test projects quarantined until final test authorization.
