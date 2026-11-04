# PHASE 3A GATE 4.4: SCIENTIFIC CLAIMS, STATISTICAL CONSISTENCY & MANUSCRIPT READINESS AUDIT
## Independent Peer-Review Audit & Scientific Integrity Evaluation

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Subsystem**: Just-In-Time (JIT) Software Defect Prediction Research Engine  
**Audit Target**: Phase 3A Frozen Research Package & Publication-Facing Artifacts  
**Role**: Senior Empirical Software Engineering Researcher, Statistical Auditor & IEEE Transactions Reviewer  
**Audit Date**: 2026-11-04  
**Audit Verdict**: **CONDITIONAL PASS** *(Internal numerical and statistical consistency is 100% verified; conditional upon author review of manuscript tone, threats to validity disclosures, and external literature benchmarking).*

---

## 1. Audit Scope & Absolute Safety Statement

This audit evaluates the scientific defensibility, statistical interpretations, numerical rigor, and publication-facing claims of the frozen Phase 3A research artifacts.

### Safety & Boundary Affirmations
1. **Zero Modifications to Frozen Artifacts**: Existing research reports, canonical metrics, benchmark results, configurations, and raw prediction records were strictly read-only and were **not modified, overwritten, renamed, or deleted**.
2. **Zero Production Code Changes**: Zero lines of code were modified in `core/`, `models/`, or application resources.
3. **Zero Retraining or Reruns**: The frozen `SUB-1__XGBoost` model was not retrained; no new predictions were generated; no benchmarks were re-run.
4. **Strict Test Dataset Quarantine**: The held-out test dataset `dataset-extracted/TD dataset/test.csv` (7,866,054 bytes, SHA-256: `c669378be165e9a92fe9dc6b31d607aba2dc8a4b709e28346c13f76b6b1b7feb`) was **100% quarantined and never accessed**. Any hypothetical question requiring test-set access is marked: `NOT PERMITTED — RESERVED FOR PHASE 4`.
5. **No Phase 3B Execution**: Phase 3B has not started.

---

## 2. Inputs Inspected

| Artifact Path | File Size | SHA-256 Checksum | Inspection Role |
| :--- | :---: | :---: | :--- |
| `research/reports/PHASE3A_FINAL_RESEARCH_SUMMARY.md` | 8,941 B | `e431088c32cd0eb05410a68422a458cef0f953e79530c94bef5d9dc9f044ea5f` | Synthesis Audit |
| `research/reports/PHASE3A_PUBLICATION_READINESS_ASSESSMENT.md`| 7,753 B | `4672357bd503ba830567725d07d249d32c9512effd330d8951861c1c238c361d` | Peer-Review Audit |
| `research/reports/PHASE3A_FINAL_FREEZE_MANIFEST.md` | 10,439 B | `5872e58b58e41cf33e79c7e95f428b223e86b70227b2af3d62c50401277660e2` | Freeze Manifest Audit |
| `research/reports/PHASE3A_PUBLICATION_PACKAGE_INDEX.md` | 9,470 B | `c989997b54c8c9799ff10c95e850a33cd70e209c4e534750bac7a95b1837ea09` | Index Traceability |
| `research/reports/PHASE3A_GATE4_FULL_BENCHMARK_REPORT_GATE43_VERIFIED.md`| 14,665 B | `5ed971045a0aa96ec2455dca6ce1e268911414c80b2aa1c30da1801ef7d1c051` | Canonical Report |
| `research/reports/PHASE3A_GATE4_3_1_MICRO_METRIC_RECONCILIATION.md`| 14,346 B | `f78d5bbd72e7516d2161f3acabc03b7cda9ac0e720d82339a6ec8f4659ce5295` | Reconciliation Audit |
| `research/reports/phase3a_gate4_2_canonical_metrics.json` | 52,222 B | `e1053c6e154bfb33db36de360f90457c1d20261565fb0527300a610dbec16573` | Authoritative Baseline |
| `research/reports/phase3a_gate4_statistical_analysis.json`| 3,504 B | `993cd97492e974a2d9077f1f3fb3e422ef7696c837b90e6356fa65b86a2a0c1b` | Statistical Source |
| `research/reports/phase3a_gate431_reconciliation.json` | 5,167 B | `19ab1dacdd2621def4ee7baf90a5bc4b075e2d450ea1c86b3ffad854b04fc785` | Reconciliation Source |
| `research/data/gate4_raw_predictions.csv` | 3,051,869 B | `a1cc86cc3094adf45eea693d964d6f75a743ef20fe2b258700b9fcc5f2f75dec` | Raw Prediction Audit |

---

## 3. Numerical Consistency Findings

Every publication-facing numerical claim was cross-checked against canonical artifacts:

1. **Training Prevalence**: Exactly **11.144%** (11,990 defects / 107,582 commits = 0.1114406). -> **VERIFIED**
2. **Validation Prevalence (Full)**: Exactly **12.096%** (2,821 defects / 23,321 commits = 0.1209639). -> **VERIFIED**
3. **Post-Burn-In Prevalence**: Exactly **11.953%** (2,680 defects / 22,421 commits = 0.1195308). -> **VERIFIED**
4. **Burn-In Schedule**: $K = 100$ commits per validation project across 9 projects. -> **VERIFIED**
5. **Burn-In Commits Omitted**: Exactly **900 commits** ($9 \times 100$; exactly 141 defects omitted: $2,821 - 2,680 = 141$). -> **VERIFIED**
6. **Aligned Post-Burn-In Sample Size**: Exactly **$N = 22,421$ commits** ($23,321 - 900 = 22,421$). -> **VERIFIED**
7. **Ground Truth Label Distribution**: Exactly **2,680 defects** and **19,741 clean commits** ($2,680 + 19,741 = 22,421$). -> **VERIFIED**
8. **Pooled Confusion Counts ($N=22,421$)**:
   - `Base_K100`: $\text{TP}=1,764, \text{FP}=3,388, \text{TN}=16,353, \text{FN}=916$. -> **VERIFIED**
   - `Quantile_Thresh`: $\text{TP}=1,164, \text{FP}=2,322, \text{TN}=17,419, \text{FN}=1,516$. -> **VERIFIED**
   - `Bayes_M50`: $\text{TP}=1,894, \text{FP}=4,539, \text{TN}=15,202, \text{FN}=786$. -> **VERIFIED**
   - `Bayes_M20`: $\text{TP}=1,917, \text{FP}=4,987, \text{TN}=14,754, \text{FN}=763$. -> **VERIFIED**
   - `Bayes_M10`: $\text{TP}=1,911, \text{FP}=5,190, \text{TN}=14,551, \text{FN}=769$. -> **VERIFIED**
9. **Micro-Metrics**:
   - `Base_K100`: Precision = 34.24%, Recall = 65.82%, F1 = 45.05%, Accuracy = 80.80%, MCC = +0.3752. -> **VERIFIED**
   - `Quantile_Thresh`: Precision = 33.39%, Recall = 43.43%, F1 = 37.76%, Accuracy = 82.88%, MCC = +0.2835. -> **VERIFIED**
   - `Bayes_M50`: Precision = 29.44%, Recall = 70.67%, F1 = 41.57%, Accuracy = 76.25%, MCC = +0.3420. -> **VERIFIED**
   - `Bayes_M20`: Precision = 27.77%, Recall = 71.53%, F1 = 40.00%, Accuracy = 74.35%, MCC = +0.3251. -> **VERIFIED**
   - `Bayes_M10`: Precision = 26.91%, Recall = 71.31%, F1 = 39.08%, Accuracy = 73.42%, MCC = +0.3139. -> **VERIFIED**
10. **Macro vs Micro Distinctions**: Maintained cleanly across all report tables. Macro F1 for `Base_K100` is 0.3563; Micro F1 is 45.05%. -> **VERIFIED**
11. **Dual Reporting**: 8-project defined ROC-AUC is **0.8023**; 9-project imputed ROC-AUC is **0.7687**. PR-AUC is **0.3882** (8 valid) vs **0.3450** (9 imputed). -> **VERIFIED**
12. **Undefined Metrics Handling (`commons-exec`)**: Zero defect commits in post-burn-in window ($N=600$); empirical Precision = 0.00%, Recall and MCC undefined (0/0) and reported as 0.00% / 0.0000 by library convention. -> **VERIFIED**
13. **Ranking Invariance**: ROC-AUC and PR-AUC deltas are strictly $0.000000000000$ across all prior shifts. -> **VERIFIED**
14. **Statistical Test Statistics**: Wilcoxon signed-rank tests match canonical JSON exactly. -> **VERIFIED**
15. **Cross-Artifact Agreement**: Report text, tables, canonical JSON, and manifest agree with 100% precision. -> **VERIFIED**

---

## 4. Statistical Interpretation Audit

The manuscript artifacts were audited to ensure rigorous statistical hygiene:

### 4.1 Strict Metric Separation in Quantile Adaptation
The audit confirms that the artifacts **strictly distinguish** between precision and F1 hypothesis tests:
* **Quantile vs Base Macro Precision**: Wilcoxon $W = 10.0, p = 0.3125$ ($r_{\text{rb}} = +0.444$, 6 wins / 2 losses / 1 tie, mean gain $+4.12$ pp).
* **Quantile vs Base Macro F1**: Wilcoxon $W = 5.0, p = 0.0781$ ($r_{\text{rb}} = -0.722$, 2 wins / 6 losses / 1 tie, mean loss $-0.0302$ F1).
* **Auditor Assessment**: These two outcomes measure fundamentally different trade-offs: Quantile adaptation aggressively filters false positives (raising precision in 6/9 projects), but sacrifices recall ($-11.12$ pp), pulling down the composite F1 score. The reports properly characterize the precision improvement as an **exploratory operational trend**, explicitly noting that it **fails to achieve statistical significance at $\alpha = 0.05$** ($p = 0.3125$).

### 4.2 Bayesian Shrinkage Tests
* **Bayes $M=50$ Macro F1**: $W = 2.0, p = 0.0938$ (1 win / 5 losses / 3 ties) -> Non-significant.
* **Bayes $M=20$ Macro F1**: $W = 2.0, p = 0.0938$ (1 win / 5 losses / 3 ties) -> Non-significant.
* **Bayes $M=10$ Macro F1**: $W = 0.0, p = 0.0313^*$ (0 wins / 6 losses / 3 ties, $r_{\text{rb}} = -1.0$) -> **Statistically significant degradation**.
* **Auditor Assessment**: The claim that weak shrinkage ($M=10$) causes a statistically significant degradation is mathematically sound under the exact two-sided permutation Wilcoxon signed-rank test at $\alpha = 0.05$.

### 4.3 Multiple Comparisons & Statistical Limitations
* **Multiple Testing Disclosure**: When testing multiple adaptation methods against the baseline, family-wise error rates increase. Under a strict Bonferroni correction for 4 comparisons against baseline, $\alpha_{\text{adjusted}} = 0.05 / 4 = 0.0125$. The Bayes $M=10$ degradation ($p = 0.0313$) would not meet the Bonferroni-corrected threshold, though it remains suggestive.
* **Auditor Mandate for Authors**: The manuscript must explicitly disclose that p-values are unadjusted for multiple comparisons, framing the $M=10$ degradation as an unadjusted exploratory finding.

---

## 5. Scientific Claims Audit Table

| Claim ID | Exact Claim in Research Package | Source Document | Underlying Evidence | Classification | Risk Level | Recommended Manuscript Wording |
| :---: | :--- | :--- | :--- | :---: | :---: | :--- |
| **C-01** | *"PR-AUC and ROC-AUC remain completely identical down to $10^{-12}$ across all prior-shift methods."* | `PHASE3A_FINAL_RESEARCH_SUMMARY.md` §6 | Canonical JSON: AUC values differ by $< 10^{-14}$; log-odds adjustment is strictly monotonic. | `SUPPORTED_BY_CANONICAL_ARTIFACT` | Low | Retain; qualify as *"ranking invariance property under monotonic scalar prior transformation"*. |
| **C-02** | *"Adapting the decision threshold directly via target quantile matching improved macro precision from 27.57% to 31.69% (+4.12 pp)."* | `PHASE3A_FINAL_RESEARCH_SUMMARY.md` §6 | Macro precision rises from 0.2757 to 0.3169 across 6 of 9 validation projects. | `SUPPORTED_BY_CANONICAL_ARTIFACT` | Low | State clearly as descriptive macro gain across 6 of 9 projects. |
| **C-03** | *"This precision gain represents an exploratory positive operational trend, rather than a statistically significant confirmation (p=0.3125)."* | `PHASE3A_GATE4_FULL_BENCHMARK_REPORT_GATE43_VERIFIED.md` §1 | Wilcoxon exact two-sided test: $W=10.0, p=0.3125 > 0.05$. | `SUPPORTED_BY_CANONICAL_ARTIFACT` | Low | Retain as written; exemplary conservative scientific reporting. |
| **C-04** | *"Supervised Bayesian updating with weak shrinkage (M=10) resulted in a statistically significant degradation in macro F1 (p=0.0313*)."* | `PHASE3A_FINAL_RESEARCH_SUMMARY.md` §6 | Wilcoxon test: $W=0.0, p=0.03125 < 0.05$. | `SUPPORTED_WITH_QUALIFICATION` | Moderate | Disclose that significance is unadjusted for multiple comparisons. |
| **C-05** | *"Zero defect labels had matured at the start of evaluation in batik and httpcore under a 90-day defect maturation window."* | `PHASE3A_FINAL_RESEARCH_SUMMARY.md` §6 | Commit timestamp inspection: 100 commits spanned 18 days (`batik`) and 58 days (`httpcore`), both $< 90$ days. | `SUPPORTED_BY_CANONICAL_ARTIFACT` | Low | Retain; illustrates practical feedback latency bottleneck in JIT-SDP. |
| **C-06** | *"The research package is publication-ready."* | `PHASE3A_PUBLICATION_READINESS_ASSESSMENT.md` §1 | 53 unit tests pass; metrics reconciled; tables consistent. | `SUPPORTED_WITH_QUALIFICATION` | Moderate | Replace with *"Ready for author review and manuscript drafting"*; avoid claiming peer-review readiness. |
| **C-07** | *"Ranking Invariance Theorem."* | Multiple reports | Mathematical derivation: $f(x) = \text{logit}(x) + c$ has derivative $f'(x) > 0$, preserving ranks. | `SUPPORTED_WITH_QUALIFICATION` | Low | Refer to as *"Ranking Invariance Property"* unless formal theorem-proof formatting is used in the manuscript. |
| **C-08** | *"Quantile threshold adaptation offers a configurable precision dial for high-defect repositories."* | `PHASE3A_GATE4_FULL_BENCHMARK_REPORT_GATE43_VERIFIED.md` §6 | In `httpclient`, precision jumped from 42.23% to 61.48% (recall adjusted from 87.71% to 42.79%). | `INFERENCE` | Moderate | Frame as an operational design recommendation, not an established empirical law. |
| **C-09** | *"Novel contribution to cross-project JIT defect prediction."* | Implicit in project narrative | Realistic delay modeling in prevalence adaptation. | `REQUIRES_EXTERNAL_LITERATURE_REVIEW` | High | Authors must conduct formal literature search (e.g., comparing against McIntosh et al., Tan et al.) before claiming novelty. |
| **C-10** | *"Model achieves generalizable defect prediction."* | Project context | Evaluated across 9 validation projects. | `REQUIRES_AUTHOR_DECISION` | High | Generalization to the 6 quarantined test projects is unverified and reserved for Phase 4. |

---

## 6. Terminology & Claim Strength Audit

The audit identified several terms that are common in internal engineering reports but overly strong for IEEE Transactions submissions. The following replacements are recommended for the manuscript:

| Existing Phrase in Internal Reports | Recommended IEEE Manuscript Wording | Scientific Rationale |
| :--- | :--- | :--- |
| **"Publication-ready"** | *"Ready for author review and manuscript preparation"* | External peer review cannot be preempted; readiness applies to internal evidence. |
| **"Ranking Invariance Theorem"** | *"Monotonic ranking invariance property"* | Standard property of order-preserving strictly monotonic log-odds transformations. |
| **"100% verified / bit-level reproducible"** | *"Numerical consistency reconciled against canonical artifacts; SHA-256 integrity verified"* | "100%" reads as marketing prose; exact reproducibility should be described technically. |
| **"Superior performance"** | *"Operational trade-off characterized by precision gains (+4.12 pp) and recall reduction (-11.12 pp)"* | No universal Pareto dominance exists; quantile adaptation trades recall for precision. |
| **"Production-ready"** | *"Benchmarked validation prototype"* | Real-world production requires live CI/CD telemetry and developer acceptance testing. |
| **"Novel contribution"** | *"Empirical investigation under realistic chronological latency constraints"* | Novelty claims must be defended against external bibliographic prior art. |

---

## 7. Threats to Validity Audit

### 7.1 Internal Validity (Severity: MODERATE)
* **SZZ Noise**: Ground-truth defect labels originate from SZZ-style commit mining in the Apache TD dataset. SZZ is known to suffer from false-positive defect attribution (e.g., cosmetic refactorings misidentified as bug fixes).
  - *Recommendation*: Explicitly cite established SZZ noise literature (e.g., Netto et al., Rodriguez-Perez et al.) in Section 6 of the manuscript and note this as an inherent dataset limitation.
* **Feedback Maturation Delay Assumption**: A fixed 90-day defect maturation window was assumed across all repositories. In practice, defect discovery follows a continuous survival distribution varying by repository release cycle.
  - *Recommendation*: Disclose that the 90-day window is a conservative operational simulation to prevent look-ahead bias, not an empirically observed repository-specific discovery timestamp.

### 7.2 Construct Validity (Severity: LOW)
* **Prevalence Shift Construct**: Defect prevalence was operationalized as the ratio of fault-inducing commits to total commits. This construct matches standard JIT-SDP literature (Kamei et al., McIntosh et al.).
* **Threshold Adjustment**: Quantile threshold tuning directly operationalizes target prevalence as an alarm budget, which accurately measures the developer review capacity construct.

### 7.3 External Validity (Severity: HIGH)
* **Ecosystem Concentration**: All 31 repositories in the TD dataset are open-source Apache Java projects. Development practices, review rigor, and commit granularities may not generalize to proprietary, closed-source, or non-Java environments (e.g., Python, C++, Go microservices).
  - *Recommendation*: Clearly bound the scope of conclusions to open-source Java ecosystems in the title, abstract, and threats to validity.
* **Test Dataset Quarantined**: The 6 held-out projects (`archiva`, `collections`, `commons-cli`, `dbcp`, `santuario`, `validator`) remain completely blind. Generalization claims must be restricted to the 9 validation projects.

### 7.4 Conclusion Validity (Severity: MODERATE)
* **Sample Size for Hypothesis Testing ($N=9$)**: Non-parametric tests with $N=9$ paired observations have limited statistical power. A minimum of 7 consistent directional pairs is required to achieve two-sided significance at $\alpha = 0.05$. The $+4.12$ pp precision gain had 6 wins and 2 losses ($p = 0.3125$), which cannot establish significance.
* **Multiple Testing**: Four adaptation variants were tested against the baseline without family-wise error rate corrections.
  - *Recommendation*: Report both raw p-values and Bonferroni-corrected thresholds in the final paper.

---

## 8. Abstract & Conclusion Recommendations for Manuscript

### Recommended Abstract Structure (IEEE TSE Style)
> **Abstract**—Cross-project Just-In-Time (JIT) software defect prediction models frequently suffer performance degradation when deployed to target repositories with substantially different defect prevalence. While target-project prevalence adaptation methods have been proposed, prior evaluations frequently ignore defect maturation latency, allowing models to update using defect labels that would not yet be known in practice. In this paper, we investigate target-project prevalence adaptation under a realistic chronological 90-day defect feedback maturation constraint across nine unseen Apache Java projects (23,321 commits). We prove analytically and verify empirically that scalar log-odds prior adjustments are strictly monotonic, leaving ranking discriminative capacity (PR-AUC and ROC-AUC) unchanged. In contrast, target-quantile threshold adaptation provides a practical operational trade-off, increasing macro precision from 27.57% to 31.69% (+4.12 pp gain in 6 of 9 projects) and mitigating false alarms in high-prevalence environments, at the expense of recall (-11.12 pp). Under exact paired Wilcoxon signed-rank testing, this precision gain represents an exploratory operational trend ($p=0.3125$) rather than a statistically significant confirmation. Furthermore, aggressive Bayesian updating with weak regularization ($M=10$) causes statistically significant performance degradation ($p=0.0313^*$) due to localized defect burst sensitivity. Our findings indicate that prior adaptation cannot expand ranking capacity, but quantile thresholding serves as an effective operational dial for false-alarm suppression under real-world feedback delays.

### Recommended Conservative Conclusion
> In cross-project JIT defect prediction, prevalence adaptation does not alter the underlying discriminative ranking of commits. Instead, its practical utility lies in shifting the operating decision threshold to match target review budgets. When defect feedback is constrained by realistic maturation delays, supervised Bayesian updating is hindered by early label starvation in high-velocity projects. Practitioners seeking false-alarm reduction should prefer quantile threshold adaptation as a configurable precision dial, while acknowledging the resulting trade-off in defect recall.

---

## 9. Research Contribution Assessment

| Contribution Type | Supported by Phase 3A Artifacts? | Required Actions Before Final Submission |
| :--- | :---: | :--- |
| **Methodological Contribution** | **YES (High)** | Formalize the mathematical proof of the ranking invariance property in Section 3 of the paper. |
| **Empirical Evaluation** | **YES (High)** | Present the 9-project chronological benchmark with dual reporting (8 defined vs 9 imputed). |
| **Operational Trade-Off Analysis** | **YES (High)** | Frame quantile thresholding as an alarm-budgeting tool that trades recall for precision. |
| **Research Infrastructure** | **YES (Moderate)** | Document the Java-based chronological stream runner and burn-in queue as open-source artifacts. |
| **State-of-the-Art Claim** | **NO** | **DO NOT CLAIM SOTA**. Phase 3A evaluates prevalence adaptation limits, not raw model supremacy. |
| **Novelty Claim** | **CONDITIONAL** | Must conduct bibliographic review against recent JIT-SDP literature (2020–2026) to position delayed maturation novelty. |

---

## 10. Required Author Decisions

Before finalized manuscript submission, the research authors must decide:
1. **Title Selection**: Select between an operational framing (*"Prevalence Adaptation in Cross-Project JIT Defect Prediction: The Pragmatic Limits of Prior Adjustments under Defect Maturation Constraints"*) versus a methodological framing (*"On the Ranking Invariance and Operational Trade-Offs of Target Prevalence Adaptation in JIT Defect Prediction"*).
2. **Dual-Reporting Presentation**: Decide whether Table 3.1 in the paper primary text presents 8-project valid averages with 9-project imputed averages in a footnote, or presents both side-by-side.
3. **Multiple Testing Correction**: Confirm whether to present unadjusted p-values with an explicit footnote or apply Benjamini-Hochberg FDR adjustments.
4. **Literature Search**: Execute an external bibliographic query regarding defect maturation latency modeling in JIT-SDP.

---

## 11. Residual Risks

* **Risk 1 (Test-Set Performance Divergence)**: When the 6 quarantined test projects are eventually evaluated in Phase 4, empirical distributions may differ from the 9 validation projects. Preserving quarantine protects against overfitting to the validation set.
* **Risk 2 (Reviewer Scepticism on Non-Significance)**: Reviewers may point out that the $+4.12$ pp precision gain is not statistically significant ($p = 0.3125$). Framing this honestly as an exploratory operational trade-off rather than a definitive superiority claim directly disarms this critique.

---

## 12. Final Gate Verdict

### **VERDICT: CONDITIONAL PASS**

### Verdict Justification
* **Integrity & Consistency (PASS)**: All numerical results, confusion counts, micro-metrics, and statistical p-values are 100% reconciled across code, JSON, and report tables.
* **Scientific Rigor (PASS)**: Claims are backed by raw prediction data and automated test suites (53/53 tests passing).
* **Condition for Manuscript Submission**: The "Conditional" designation is assigned strictly because external literature benchmarking is required to substantiate novelty claims, and authorial tone softening (replacing "Theorem" and "100% verified") is required before IEEE submission.
