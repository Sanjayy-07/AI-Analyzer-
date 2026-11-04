# PHASE 3A GATE 4.3.1: POOLED CONFUSION MATRIX & MICRO-METRIC RECONCILIATION
## Independent Numerical Consistency, Micro-Aggregation & Scientific Integrity Audit

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Subsystem**: Just-In-Time (JIT) Software Defect Prediction Research Engine  
**Auditor**: Independent Statistical Auditor, Validation Specialist & Java Reproducibility Engineer  
**Audit Target**: Phase 3A Gate 4.3 / Gate 4.2 Results & Table Aggregations  
**Authoritative Baseline**: [`research/reports/phase3a_gate4_2_canonical_metrics.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/phase3a_gate4_2_canonical_metrics.json)  
**Reconciliation Output**: [`research/reports/phase3a_gate431_reconciliation.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/phase3a_gate431_reconciliation.json)  
**Execution Manifest**: [`research/reports/phase3a_gate431_execution_manifest.json`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/reports/phase3a_gate431_execution_manifest.json)  
**Final Audit Verdict**: **PASS**

---

## 1. Executive Summary & Verdict

This audit was commissioned to investigate and rigorously resolve a critical arithmetic contradiction identified in Phase 3A Gate 4.3 outputs:
- In chat narrative text following Gate 4.3, a pooled confusion matrix was cited for `Base_K100` as:  
  $$\text{TP} = 1,202, \quad \text{FP} = 1,374, \quad \text{TN} = 18,442, \quad \text{FN} = 1,403 \quad (\Sigma = 22,421)$$
- Applying the standard Micro-F1 formula to these counts yields:
  $$\text{Micro-F1} = \frac{2 \times 1,202}{2 \times 1,202 + 1,374 + 1,403} = \frac{2,404}{5,181} \approx \mathbf{46.40\%}$$
- Yet the canonical benchmark report authoritative micro-F1 for `Base_K100` is **45.05%**.

### Audit Finding & Forensic Conclusion
1. **The $45.05\%$ Micro-F1 is Mathematically Exact, Genuine, and Canonical**:
   Independent reconstruction directly from all 22,421 raw commit prediction records in [`research/data/gate4_raw_predictions.csv`](file:///c:/Users/Harini/Desktop/java%20project/analyzer/analyzer/research/data/gate4_raw_predictions.csv) and from the authoritative project-level records confirms that the genuine post-burn-in pooled confusion matrix is:
   $$\mathbf{TP = 1,764, \quad FP = 3,388, \quad TN = 16,353, \quad FN = 916 \quad (N = 22,421)}$$
   - $\text{Ground Truth Defects} = \text{TP} + \text{FN} = 1,764 + 916 = \mathbf{2,680}$ (prevalence: $11.953\%$).
   - $\text{Micro-Precision} = \frac{1,764}{1,764 + 3,388} = \frac{1,764}{5,152} = 0.34239130 \implies \mathbf{34.24\%}$.
   - $\text{Micro-Recall} = \frac{1,764}{1,764 + 916} = \frac{1,764}{2,680} = 0.65820896 \implies \mathbf{65.82\%}$.
   - $\text{Micro-F1} = \frac{2 \times 1,764}{2 \times 1,764 + 3,388 + 916} = \frac{3,528}{7,832} = 0.45045965 \implies \mathbf{45.05\%}$.
   - $\text{Micro-Accuracy} = \frac{1,764 + 16,353}{22,421} = \frac{18,117}{22,421} = 0.80803711 \implies \mathbf{80.80\%}$.
   - $\text{Micro-MCC} = \mathbf{+0.375224}$.

2. **The Cited Matrix ($1,202 / 1,374 / 18,442 / 1,403$) Was an Invalid Prose Transcription Artifact**:
   In that matrix, the total positive count is $\text{TP} + \text{FN} = 1,202 + 1,403 = \mathbf{2,605}$, which is **75 defect labels short of the actual 2,680 defects** present in the post-burn-in validation dataset. It never existed in any raw predictions, JSON results, or canonical files; it was accidentally typed into a chat response block during Gate 4.3 turn synthesis. It is formally discarded.

3. **Markdown Table 3.1 Transcription Discrepancy Resolved**:
   In Table 3.1 of `PHASE3A_GATE4_FULL_BENCHMARK_REPORT_GATE43_VERIFIED.md`, `Base_K100` Micro-F1 was correctly listed as 45.05%, but other methods had copy-paste errors from an unaligned draft (Quantile Thresh was listed as 46.85% instead of 37.76%, and Bayes M=50/20/10 were listed as 44.57%, 44.40%, 44.20% instead of 41.57%, 40.00%, 39.08%). Table 3.1 has been updated to reflect the canonical counts and values.

4. **Verdict**: **PASS**. All confusion matrices and micro-metrics across all 9 validation projects and 5 adaptation methods are reconciled, mathematically verified, and 100% consistent across code, raw data, JSON, and report tables.

---

## 2. Experimental Boundaries & Provenance Verification

### Non-Negotiable Boundary Guarantees
- **Test Quarantine**: The held-out test dataset `dataset-extracted/TD dataset/test.csv` (7,866,054 bytes, SHA-256: `c669378be165e9a92fe9dc6b31d607aba2dc8a4b709e28346c13f76b6b1b7feb`) was **100% quarantined and never read, parsed, or evaluated**.
- **Model Immutability**: The frozen `SUB-1__XGBoost` model, all 43 feature definitions, and model parameters remained completely unchanged. No model retraining was performed.
- **Raw Prediction Immutability**: `research/data/gate4_raw_predictions.csv` and `gate4_raw_predictions.json` were strictly read-only and bit-level preserved.
- **Population Definition**:
  - Full Validation Dataset: $N = 23,321$ commits across 9 unseen Apache Java projects (2,821 defects).
  - Burn-in Window: $K = 100$ commits per project ($9 \times 100 = 900$ commits; 141 defects).
  - Aligned Post-Burn-In Evaluation Window: $N = 22,421$ commits ($23,321 - 900$; exactly 2,680 defects).

### Table 2.1: Artifact Inventory & SHA-256 Checksums

| Artifact Path | Size (Bytes) | SHA-256 Checksum | Audit Status |
| :--- | :---: | :---: | :---: |
| `research/data/gate4_raw_predictions.csv` | 3,051,869 | `a1cc86cc3094adf45eea693d964d6f75a743ef20fe2b258700b9fcc5f2f75dec` | Verified Read-Only Source |
| `research/data/gate4_raw_predictions.json` | 7,087,373 | `a26f8edb73aed0b43cbc7176dd15bd24762b48f66e215f1a88a2278135983d19` | Verified Read-Only Source |
| `research/reports/phase3a_gate4_2_canonical_metrics.json` | 52,222 | `e1053c6e154bfb33db36de360f90457c1d20261565fb0527300a610dbec16573` | Canonical Metric Baseline |
| `research/reports/phase3a_gate431_reconciliation.json` | 5,167 | Generated | Authorized Audit Deliverable |
| `research/reports/phase3a_gate431_execution_manifest.json`| 926 | Generated | Authorized Audit Deliverable |
| `dataset-extracted/TD dataset/test.csv` | 7,866,054 | `c669378be165e9a92fe9dc6b31d607aba2dc8a4b709e28346c13f76b6b1b7feb` | **100% Quarantined (Untouched)**|

---

## 3. Reconstructed Pooled Confusion Matrices & Micro-Metrics

All metrics are reconstructed directly on the aligned post-burn-in evaluation window ($N = 22,421$, Ground Truth Defects $= 2,680$, Non-Defects $= 19,741$).

### Table 3.1: Pooled Confusion Matrices & Micro-Metrics across All 5 Methods

| Adaptation Method | TP | FP | TN | FN | Total ($N$) | Ground Truth Defects | Micro Precision | Micro Recall | Micro F1-Score | Micro Accuracy | Micro MCC |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`Base_K100_TauStar`** | **1,764** | **3,388** | **16,353** | **916** | **22,421** | **2,680** | **34.24%** | **65.82%** | **45.05%** | **80.80%** | **+0.3752** |
| **`Quantile_Thresh_K100_Delay90d`** | 1,164 | 2,322 | 17,419 | 1,516 | 22,421 | 2,680 | 33.39% | 43.43% | 37.76% | 82.88% | +0.2835 |
| **`Bayes_K100_M50_Delay90d`** | 1,894 | 4,539 | 15,202 | 786 | 22,421 | 2,680 | 29.44% | 70.67% | 41.57% | 76.25% | +0.3420 |
| **`Bayes_K100_M20_Delay90d`** | 1,917 | 4,987 | 14,754 | 763 | 22,421 | 2,680 | 27.77% | 71.53% | 40.00% | 74.35% | +0.3251 |
| **`Bayes_K100_M10_Delay90d`** | 1,911 | 5,190 | 14,551 | 769 | 22,421 | 2,680 | 26.91% | 71.31% | 39.08% | 73.42% | +0.3139 |

---

## 4. Project-Level Confusion Matrix Breakdown & Summation Reconciliation

For every method, the sum of project-level confusion matrix cells strictly equals the pooled confusion matrix cells:
$$\sum_{i=1}^9 \text{TP}_i = \text{TP}_{\text{pooled}}, \quad \sum_{i=1}^9 \text{FP}_i = \text{FP}_{\text{pooled}}, \quad \sum_{i=1}^9 \text{TN}_i = \text{TN}_{\text{pooled}}, \quad \sum_{i=1}^9 \text{FN}_i = \text{FN}_{\text{pooled}}$$

### Table 4.1: Project-Level Breakdown for `Base_K100_TauStar` ($\tau^* = 0.170$)

| Project ID | Evaluated Commits ($N$) | Ground Truth Defects | TP | FP | TN | FN | Precision | Recall | F1-Score | Summation Check |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`org.apache:batik`** | 6,915 | 488 | 188 | 285 | 6,142 | 300 | 39.75% | 38.52% | 0.3913 | Verified |
| **`org.apache:codec`** | 2,044 | 260 | 139 | 333 | 1,451 | 121 | 29.45% | 53.46% | 0.3798 | Verified |
| **`org.apache:commons-exec`** | 600 | 0 | 0 | 0 | 600 | 0 | 0.00% | 0.00%* | 0.0000* | Verified |
| **`org.apache:commons-fileupload`**| 1,080 | 106 | 55 | 111 | 863 | 51 | 33.13% | 51.89% | 0.4044 | Verified |
| **`org.apache:commons-jelly`** | 1,845 | 200 | 94 | 328 | 1,317 | 106 | 22.27% | 47.00% | 0.3023 | Verified |
| **`org.apache:daemon`** | 1,151 | 172 | 131 | 256 | 723 | 41 | 33.85% | 76.16% | 0.4687 | Verified |
| **`org.apache:digester`** | 2,092 | 73 | 46 | 204 | 1,815 | 27 | 18.40% | 63.01% | 0.2848 | Verified |
| **`org.apache:httpclient`** | 3,190 | 895 | 785 | 1,074 | 1,221 | 110 | 42.23% | 87.71% | 0.5701 | Verified |
| **`org.apache:httpcore`** | 3,504 | 486 | 326 | 797 | 2,221 | 160 | 29.03% | 67.08% | 0.4052 | Verified |
| **Total (Sum of 9 Projects)** | **22,421** | **2,680** | **1,764** | **3,388** | **16,353** | **916** | **34.24%** | **65.82%** | **45.05%** | **EXACT MATCH** |

*Note*: In `commons-exec`, actual positives = 0; hence Recall and F1 are mathematically undefined (0/0) and reported as 0.00% by convention.

### Table 4.2: Project-Level Breakdown for `Quantile_Thresh_K100_Delay90d`

| Project ID | Evaluated Commits ($N$) | Ground Truth Defects | TP | FP | TN | FN | Precision | Recall | F1-Score | Summation Check |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`org.apache:batik`** | 6,915 | 488 | 239 | 532 | 5,895 | 249 | 31.00% | 48.98% | 0.3797 | Verified |
| **`org.apache:codec`** | 2,044 | 260 | 117 | 240 | 1,544 | 143 | 32.77% | 45.00% | 0.3793 | Verified |
| **`org.apache:commons-exec`** | 600 | 0 | 0 | 15 | 585 | 0 | 0.00% | 0.00%* | 0.0000* | Verified |
| **`org.apache:commons-fileupload`**| 1,080 | 106 | 51 | 89 | 885 | 55 | 36.43% | 48.11% | 0.4146 | Verified |
| **`org.apache:commons-jelly`** | 1,845 | 200 | 41 | 84 | 1,561 | 159 | 32.80% | 20.50% | 0.2523 | Verified |
| **`org.apache:daemon`** | 1,151 | 172 | 103 | 154 | 825 | 69 | 40.08% | 59.88% | 0.4802 | Verified |
| **`org.apache:digester`** | 2,092 | 73 | 62 | 745 | 1,274 | 11 | 7.68% | 84.93% | 0.1409 | Verified |
| **`org.apache:httpclient`** | 3,190 | 895 | 383 | 240 | 2,055 | 512 | 61.48% | 42.79% | 0.5046 | Verified |
| **`org.apache:httpcore`** | 3,504 | 486 | 168 | 223 | 2,795 | 318 | 42.97% | 34.57% | 0.3831 | Verified |
| **Total (Sum of 9 Projects)** | **22,421** | **2,680** | **1,164** | **2,322** | **17,419** | **1,516** | **33.39%** | **43.43%** | **37.76%** | **EXACT MATCH** |

---

## 5. Discrepancy Resolution & Regression Analysis

### Forensic Breakdown of the False Matrix ($1,202 / 1,374 / 18,442 / 1,403$)
1. **Mathematical Defect**:
   $$\text{Actual Positives} = \text{TP} + \text{FN} = 1,202 + 1,403 = \mathbf{2,605}$$
   In the genuine post-burn-in validation dataset ($N = 22,421$), exactly **2,680 commits are defect-inducing**.
   The false matrix underestimates true defects by **75 commits** ($\Delta = -75$).
2. **Micro-F1 of the False Matrix**:
   $$\text{Micro-F1} = \frac{2 \times 1,202}{2 \times 1,202 + 1,374 + 1,403} = \frac{2,404}{5,181} = 0.46400309 \implies \mathbf{46.40\%}$$
3. **Contrast with Canonical Reality**:
   The true canonical counts are $TP=1,764, FP=3,388, TN=16,353, FN=916$.
   $$\text{Micro-F1} = \frac{2 \times 1,764}{2 \times 1,764 + 3,388 + 916} = \frac{3,528}{7,832} = 0.45045965 \implies \mathbf{45.05\%}$$
   $$\text{Defect Commits} = 1,764 + 916 = \mathbf{2,680} \quad (\text{Exact Match})$$
4. **Resolution**: The false matrix originated from an erroneous chat draft in step 2801 and was never part of any underlying model prediction or canonical result. It has been completely excised and replaced with verified canonical evidence.

---

## 6. Verification Test Suite Execution Results

All 53 unit and integration tests across the research module passed without failure:
- `Phase3aGate431ReconciliationAuditorTest`: **18 tests executed, 18 passed** (0 failures, 0 errors).
  - Explicit regression test demonstrating that $1202/1374/18442/1403 \implies 46.40\%$ is inconsistent with $45.05\%$.
  - Canonical verification that $1764/3388/16353/916$ yields exact $45.05\%$ Micro-F1 and $2680$ defects.
  - Verification of harmonic-mean equality ($2\text{TP}/(2\text{TP}+\text{FP}+\text{FN}) \equiv 2PR/(P+R)$).
  - Verification of project-level count summation to pooled counts for all methods.
  - Verification of quarantine preservation for the six held-out test projects.
- `Phase3aGate43TableIntegrityAuditorTest`: **12 tests executed, 12 passed**.
- `Phase3aGate42AuditorTest`: **8 tests executed, 8 passed**.
- `PrevalenceAdaptationComprehensiveTest`: **15 tests executed, 15 passed**.

**Test Command**: `mvn test -f research/java/pom.xml`  
**Execution Outcome**: `BUILD SUCCESS`, 53/53 tests passed, exit code 0.

---

## 7. Final Gate 4.3.1 Verdict & Publication Readiness

### Verdict: **PASS**

### Justification
1. The arithmetic contradiction between the reported Micro-F1 (45.05%) and the chat-cited matrix (1202/1374/18442/1403 => 46.40%) is **completely and irrefutably resolved**.
2. The genuine Base_K100 confusion matrix is $\mathbf{TP = 1,764, FP = 3,388, TN = 16,353, FN = 916}$, which yields exact **Micro-F1 = 45.05%**, **Micro-Precision = 34.24%**, **Micro-Recall = 65.82%**, **Micro-Accuracy = 80.80%**, and defect count = **2,680**.
3. All project-level confusion matrix sums strictly equal pooled matrix counts.
4. All 5 adaptation methods have been independently reconstructed and reconciled.
5. All 53 automated tests pass with exit code 0.
6. The six held-out test projects remain **100% quarantined and unread**.
7. Model weights, parameters, feature sets, and raw predictions remain **100% frozen and immutable**.
8. Gate 4.3 publication readiness is now **fully justified, mathematically reconciled, and verified**.
