# PHASE 3A GATE 4.3: FINAL TABLE INTEGRITY & METRIC SEMANTICS AUDIT REPORT

**Auditor**: Independent Senior Research Auditor & Java Reproducibility Engineer  
**Audit Phase**: Phase 3A Gate 4.3  
**Canonical Baseline**: `research/reports/phase3a_gate4_2_canonical_metrics.json`  
**Verdict**: **PASS**  

---

## 1. Executive Summary & Audit Mandate

Gate 4.3 is a narrowly scoped final integrity audit focused strictly on:
1. **Table Column Integrity**: Verifying that every column header across all Gate 4 markdown reports accurately matches the values beneath it.
2. **Undefined Metric Semantics**: Explicitly classifying the behavior of zero-positive repositories (`commons-exec`) and forbidding unannotated `0.0000` or `0.5000` entries from being interpreted as empirical measurements.
3. **Macro and Micro Aggregation Denominators**: Enforcing clear distinction between 8-project defined macro averages, 9-project convention-imputed macro averages, and pooled micro metrics.
4. **Statistical Claims Auditing**: Auditing inference statements to ensure non-significant results ($p = 0.3125$) are never presented as statistically confirmed superiority.

### Audit Verdict: PASS
All final markdown tables, column alignments, undefined metric conventions, and statistical statements have been verified, standardized, and harmonized with the canonical Gate 4.2 JSON artifact.

---

## 2. Metric Semantics Classification (`commons-exec`)

For `org.apache:commons-exec` ($N=600$ post-burn-in commits, Actual Positives = 0):

| Metric Name | Evaluated Method | Raw Value Displayed | Semantic Classification | Mathematical Justification |
| :--- | :--- | :---: | :---: | :--- |
| **Recall** | `Base_K100_TauStar` | `0.00% (or UNDEFINED)` | `UNDEFINED_MATHEMATICAL` | Actual positives = 0. Denominator TP + FN = 0. Mathematically undefined (0/0). Reported 0.00% is a REPORTING_CONVENTION. |
| **Precision** | `Base_K100_TauStar` | `0.00%` | `DEFINED_EMPIRICAL` | Predicted positives = 7, True Positives = 0. Precision = 0 / 7 = 0.00% (Mathematically defined empirical zero due to false alarms). |
| **F1-Score** | `Base_K100_TauStar` | `0.0000` | `UNDEFINED_MATHEMATICAL` | Precision + Recall denominator is zero. Mathematically undefined. Reported 0.0000 is a REPORTING_CONVENTION. |
| **MCC** | `Base_K100_TauStar` | `0.0000` | `UNDEFINED_MATHEMATICAL` | Denominator sqrt((TP+FP)(TP+FN)(TN+FP)(TN+FN)) = 0. Mathematically undefined. Reported 0.0000 is a REPORTING_CONVENTION. |
| **ROC-AUC** | `Base_K100_TauStar` | `0.5000 (Imputed) / N/A` | `IMPUTED_FOR_AGGREGATION` | Mann-Whitney denominator N_pos * N_neg = 0. Mathematically undefined. 0.5000 is strictly an IMPUTED_FOR_AGGREGATION convention, NOT an empirical measurement. |
| **PR-AUC** | `Base_K100_TauStar` | `0.0000 (Imputed) / N/A` | `IMPUTED_FOR_AGGREGATION` | No positive recall thresholds exist. Mathematically undefined. 0.0000 is strictly an IMPUTED_FOR_AGGREGATION convention. |
| **Brier Score** | `Base_K100_TauStar` | `0.0025` | `DEFINED_EMPIRICAL` | Mean squared difference sum((p - 0)^2) / 600 = 0.0025. Strictly defined empirical loss. |
| **ECE** | `Base_K100_TauStar` | `0.0117` | `DEFINED_EMPIRICAL` | 10-bin expected calibration error. Strictly defined empirical calibration loss. |
| **Precision** | `Quantile_Thresh_K100` | `0.00%` | `DEFINED_EMPIRICAL` | Predicted positives = 359, TP = 0. Precision = 0 / 359 = 0.00% (Empirical zero). |
| **Recall** | `Quantile_Thresh_K100` | `0.00% (or UNDEFINED)` | `UNDEFINED_MATHEMATICAL` | Actual positives = 0. Denominator = 0. Mathematically undefined. |
| **ROC-AUC** | `Quantile_Thresh_K100` | `0.5000 (Imputed)` | `IMPUTED_FOR_AGGREGATION` | Mathematically undefined. 0.5000 is strictly an imputation convention. |

### Mandatory Metric Handling Rules:
1. **Recall**: When actual positives equal zero, Recall is mathematically undefined ($0/0$). Any displayed `0.00%` represents a `REPORTING_CONVENTION`, not an empirical zero detection rate.
2. **Precision**: Because false alarms occur ($FP = 7$ at $\tau^* = 0.170$), Precision is a `DEFINED_EMPIRICAL` measurement of $0 / 7 = 0.00\%$.
3. **ROC-AUC**: The Wilcoxon-Mann-Whitney denominator is $N_{\text{pos}} \times N_{\text{neg}} = 0$. ROC-AUC is mathematically undefined. Displaying `0.5000` is strictly an `IMPUTED_FOR_AGGREGATION` convention and must be explicitly annotated as such.
4. **PR-AUC**: With zero positive recall thresholds, PR-AUC is mathematically undefined. `0.0000` is an `IMPUTED_FOR_AGGREGATION` convention.

---

## 3. Macro and Micro Aggregation Verification

### Table 3.1: Aligned Post-Burn-in Aggregates ($N = 22,421$, $K = 100$)

| Metric | Base_K100 Value | Quantile Thresh Value | Project Denominator | Handling of `commons-exec` |
| :--- | :---: | :---: | :---: | :--- |
| **Macro Precision** | 27.57% | 31.69% | 9 Projects | Defined empirical 0.00% included |
| **Macro Recall (9 Proj)** | 53.87% | 42.75% | 9 Projects | Convention 0.00% included |
| **Macro Recall (8 Proj)** | 60.60% | 48.10% | 8 Projects | Excluded (Only projects with defined recall) |
| **Macro F1-Score** | 0.3563 | 0.3261 | 9 Projects | Convention 0.0000 included |
| **Macro ROC-AUC (8 Proj)**| 0.8023 | 0.8023 | 8 Projects | Excluded (Mathematically undefined) |
| **Macro ROC-AUC (9 Proj)**| 0.7687 | 0.7687 | 9 Projects | Imputed 0.5000 included |
| **Macro PR-AUC (8 Proj)** | 0.3882 | 0.3882 | 8 Projects | Excluded (Mathematically undefined) |
| **Macro PR-AUC (9 Proj)** | 0.3450 | 0.3450 | 9 Projects | Imputed 0.0000 included |
| **Micro F1-Score (Pooled)**| 45.05% | 37.76% | 22,421 Commits | Calculated strictly from pooled counts (Base: $TP=1764, FP=3388, TN=16353, FN=916$; Quantile: $TP=1164, FP=2322, TN=17419, FN=1516$) |

---

## 4. Table Column Integrity Verification

All project-level results tables were audited. Findings:
- **TABLE_COLUMN_ORDERING** (MINOR_RESOLVED): In original Table 7.1, columns were: [F1-Score, PR-AUC, ROC-AUC, MCC]. In audited Table 3.1, columns were: [F1-Score, MCC, PR-AUC, ROC-AUC]. In both tables, values strictly matched headers, but order differed. -> *Resolution*: STANDARDIZED
- **TABLE_PROJECT_COVERAGE** (MODERATE_RESOLVED): Table 4.1 in Gate 4.2 verified report displayed only 4 representative projects (batik, commons-exec, httpclient, httpcore). The other 5 validation projects were omitted from the table. -> *Resolution*: EXPANDED_ALL_9_PROJECTS
- **UNDEFINED_ROC_AUC_LABELING** (MODERATE_RESOLVED): Displaying '0.5000' or '*0.5000*' under ROC-AUC for commons-exec can mislead readers into interpreting 0.5 as an empirical measurement. -> *Resolution*: EXPLICIT_NA_LABEL_APPLIED

---

## 5. Statistical Claims Audit

| Evaluated Claim | Canonical Statistic | Exact p-value | Audit Verdict | Approved Publication Phrasing |
| :--- | :---: | :---: | :---: | :--- |
| **Quantile Threshold Precision Improvement** | +0.0412 (+4.12 pp) | $p = 0.3125$ | `CONFIRMED_NON_SIGNIFICANT` | Must be described as an exploratory positive operational trade-off across 6 of 9 projects, NOT statistically significant at alpha=0.05. |
| **Bayes M=10 F1 Degradation** | -0.0447 (-4.47 pp) | $p = 0.0313$ | `CONFIRMED_STATISTICALLY_SIGNIFICANT` | Statistically significant decrease in F1 (p=0.0313*) demonstrating small-sample burn-in overreaction. |
| **Scalar Log-Odds Ranking Invariance** | < 1e-12 | Theorem Invariant | `THEORETICALLY_AND_EMPIRICALLY_VERIFIED` | PR-AUC and ROC-AUC are strictly invariant under scalar log-odds adjustments. |

---

## 6. Final Gate 4.3 Conclusion

1. **Table Column Integrity**: **PASS**. All headers, orderings, and cells verified.
2. **Metric Semantics**: **PASS**. Zero-positive project behavior strictly classified.
3. **Aggregation Transparency**: **PASS**. Dual macro reporting (8 valid vs 9 imputed) enforced.
4. **Statistical Phrasing**: **PASS**. Non-significant results honestly presented.
5. **Publication Readiness**: **ACHIEVED**.
