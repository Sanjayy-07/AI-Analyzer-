# Revised Implementation Plan: High-Performance Cross-Project Just-In-Time Software Defect Prediction

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Component**: Advanced JIT Defect Prediction Benchmark (`TD dataset`)  
**Status**: Proposal Pending User Explicit Approval (Decision Gate)  
**Authors**: Antigravity Principal ML & Static Analysis Research Engineering Team  
**Date**: November 2026  

---

## 1. Executive Summary & Research Strategy

This revised implementation plan establishes a comprehensive, publication-grade experimental roadmap to advance the performance of cross-project Just-In-Time (JIT) defect prediction on the Apache Software Foundation dataset (153,994 commits across 31 projects).

### 1.1 Verified Empirical Baseline (Held-Out Test Set: 6 Unseen Apache Projects, 23,091 Commits)
| Model | Threshold ($\tau$) | Accuracy | Precision | Recall | F1-Score | PR-AUC | ROC-AUC | MCC |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Majority Baseline (ZeroR)** | 0.500 | 89.10% | 0.00% | 0.00% | 0.00% | 10.90% | 0.5000 | 0.0000 |
| **Logistic Regression** | 0.500 | 22.34% | 11.51% | **91.61%** | 0.2045 | 15.90% | 0.5921 | +0.0381 |
| **Random Forest (Default)** | 0.500 | 63.69% | 19.47% | 74.40% | 0.3087 | 28.82% | 0.7420 | +0.2313 |
| **HistGradientBoosting (Best)** | **0.620** | **79.52%** | **25.71%** | **47.93%** | **0.3347** | **28.29%** | **0.7473** | **+0.2401** |

### 1.2 The Gap to Target ($\text{F1} \ge 0.80$, $\text{Precision} \ge 0.80$, $\text{Recall} \ge 0.80$)
The empirical audit identified two structural bottlenecks in the current 7-feature churn representation:
1. **The Churn Confounder (High False Positives)**: Clean, large architectural refactorings and features have median $\text{LINES\_ADDED} = 107$, causing tree models to trigger false alarms.
2. **The "Silent Ninja" Bug (High False Negatives)**: Subtle, fault-inducing logic bugs have median $\text{LINES\_ADDED} = 18$ and $\text{N\_FILES\_CHANGED} = 2$, slipping entirely past churn thresholds.

To break past this barrier, we implement the **Multi-Modal Churn + Author History + NLP Commit Representation**, explicitly addressing requirements A through F.

---

## 2. Requirement A: Commit Message NLP Experiment Architecture

The raw dataset contains 153,994 un-truncated `COMMIT_MESSAGE` entries. Developer commit messages provide direct semantic signal regarding developer intent, bug-fixing activity, ticket references, and refactoring scope that churn metrics cannot capture.

```
                    ┌────────────────────────────────────────────────────────┐
                    │               RAW COMMIT_MESSAGE STRING                │
                    └──────────────────────────┬─────────────────────────────┘
                                               │
               ┌───────────────────────────────┼───────────────────────────────┐
               ▼                               ▼                               ▼
    ┌──────────────────────┐       ┌──────────────────────┐       ┌──────────────────────┐
    │  Intent Regex Mining │       │  Lexical Statistics  │       │ Sub-word & Character │
    ├──────────────────────┤       ├──────────────────────┤       ├──────────────────────┤
    │ • Bug-fix indicators │       │ • Message length     │       │ • Word TF-IDF        │
    │ • Refactoring intent │       │ • Word & line count  │       │ • Char TF-IDF (3-5)  │
    │ • Ticket keys (JIRA) │       │ • Type-token ratio   │       │ • FeatureUnion       │
    │ • Maintenance flags  │       │ • Punctuation ratios │       │ • Truncated SVD      │
    └──────────┬───────────┘       └──────────┬───────────┘       └──────────┬───────────┘
               │                              │                              │
               └──────────────────────────────┼──────────────────────────────┘
                                              ▼
                               ┌─────────────────────────────┐
                               │  Dense NLP Feature Vector   │
                               │   (~65 engineered dims)     │
                               └─────────────────────────────┘
```

### 2.1 Intent Lexical Indicators (Domain-Specific Regex Patterns)
We extract explicit binary and frequency indicators representing developer intent:
- **`is_bug_fix_intent`**: Matches patterns like `fix(e[ds])?`, `bug(s)?`, `patch`, `issue(s)?`, `defect`, `error`, `fail(ure|ed)?`, `crash`, `npe`, `nullpointer`, `deadlock`, `race condition`.
- **`is_refactoring_intent`**: Matches `refactor`, `clean(up)?`, `reorgani[sz]e`, `rename`, `move`, `extract`, `inline`, `simplify`, `restructur`.
- **`has_ticket_reference`**: Matches Apache JIRA ticket patterns (`[A-Z]{2,10}-[0-9]+`), GitHub issue patterns (`#[0-9]+`, `gh-[0-9]+`), and bug ID references.
- **`is_maintenance_intent`**: Matches `doc(s)?`, `javadoc`, `comment(s)?`, `license`, `header`, `format`, `indent`, `pom\.xml`, `build`, `maven`, `gradle`, `test(s)?`, `junit`.
- **`is_dependency_architecture_intent`**: Matches `dep(endenc(y|ies))?`, `upgrade`, `bump`, `api`, `interface`, `abstract`, `module`, `package`, `import`, `version`.

### 2.2 Lexical Complexity & Token Diversity Metrics
- **`msg_char_length`**: Total character length of the commit message.
- **`msg_word_count`**: Number of whitespace-delimited tokens.
- **`msg_line_count`**: Number of newline characters.
- **`msg_type_token_ratio`**: Unique word count / total word count (lexical richness).
- **`msg_uppercase_ratio`**: Ratio of capital letters (often indicates shouting or urgent tickets).
- **`msg_digit_ratio`**: Ratio of numeric characters (ticket IDs, version numbers).

### 2.3 TF-IDF Vectorization & Dimensionality Reduction
To prevent high-dimensional sparsity and excessive memory consumption:
1. **Word-Level TF-IDF**:
   - N-gram range: $(1, 2)$ (unigrams + bigrams).
   - Pruning: `min_df=5`, `max_df=0.85`, sublinear term frequency scaling (`sublinear_tf=True`).
   - Stop words: English standard + Git/Java domain stop words (`commit`, `svn`, `git`, `trunk`, `branch`).
2. **Character-Level TF-IDF**:
   - N-gram range: $(3, 5)$ across character boundaries.
   - Captures morphologic affixes (e.g., `fix-`, `un-`, `re-`) and ticket prefixes without explicit tokenization.
3. **Word + Character Feature Fusion**:
   - Integrated via `FeatureUnion` or horizontal sparse matrix concatenation.
4. **Truncated SVD (Latent Semantic Analysis)**:
   - Reduced to $K = 50$ latent semantic components.
   - **Strict Leakage Prevention**: SVD and TF-IDF vectorizers are **fitted strictly on the training partition** (`train.csv`). The validation and test partitions are transformed using the pre-fitted estimators.

### 2.4 Three-Way Ablation Benchmark Protocol
To verify the exact contribution of natural language semantics, we perform a controlled 3-way ablation under identical 5-fold GroupKFold cross-validation on the 16 training projects:
1. **Model A (Churn-Only)**: Process metrics only (`LINES_ADDED`, `LINES_REMOVED`, `N_FILES_CHANGED`, `N_REFACTORINGS`).
2. **Model B (NLP-Only)**: SVD latent components + lexical intent features only.
3. **Model C (Fused Hybrid)**: Churn + NLP + Interaction terms.

---

## 3. Requirement B: Advanced Feature Engineering

Every feature is derived strictly from data already present in the local dataset files (`train.csv`, `validation.csv`, `test.csv`).

### 3.1 Churn Ratios & Normalized Dispersion
- **`net_churn`**: $\text{LINES\_ADDED} - \text{LINES\_REMOVED}$
- **`total_churn`**: $\text{LINES\_ADDED} + \text{LINES\_REMOVED}$
- **`churn_ratio`**: $\frac{\text{LINES\_ADDED}}{\text{LINES\_REMOVED} + 1}$
- **`deletion_ratio`**: $\frac{\text{LINES\_REMOVED}}{\text{LINES\_ADDED} + 1}$
- **`churn_per_file`**: $\frac{\text{total\_churn}}{\text{N\_FILES\_CHANGED} + 10^{-5}}$
- **`lines_added_per_file`**: $\frac{\text{LINES\_ADDED}}{\text{N\_FILES\_CHANGED} + 10^{-5}}$
- **`lines_removed_per_file`**: $\frac{\text{LINES\_REMOVED}}{\text{N\_FILES\_CHANGED} + 10^{-5}}$

### 3.2 Refactoring Density & Structural Intensity
- **`refactorings_per_file`**: $\frac{\text{N\_REFACTORINGS}}{\text{N\_FILES\_CHANGED} + 10^{-5}}$
- **`refactorings_per_kloc`**: $\frac{\text{N\_REFACTORINGS}}{(\text{total\_churn} / 1000) + 10^{-5}}$
- **`is_pure_refactoring`**: Binary indicator where $\text{N\_REFACTORINGS} > 0$ and $\text{is\_refactoring\_intent} = 1$.

### 3.3 Semantic & Process Interaction Terms
- **`bugfix_churn_interaction`**: $\text{is\_bug\_fix\_intent} \times \ln(1 + \text{total\_churn})$
- **`refactor_churn_interaction`**: $\text{is\_refactoring\_intent} \times \ln(1 + \text{total\_churn})$
- **`ticket_file_interaction`**: $\text{has\_ticket\_reference} \times \text{N\_FILES\_CHANGED}$

### 3.4 Temporal & Author Dynamics
- **`commit_hour`**: Hour of day (0–23) extracted from `AUTHOR_DATE`.
- **`commit_dayofweek`**: Day of week (0 = Monday, 6 = Sunday).
- **`is_weekend`**: Binary indicator ($1$ if Saturday or Sunday, else $0$).
- **`is_off_hours`**: Binary indicator ($1$ if commit occurred between 20:00 and 06:00).
- **`author_timezone_offset`**: Minutes offset from UTC (`AUTHOR_TIMEZONE`).

### 3.5 Strict Chronological Author Defect History (Zero Leakage)
To compute developer experience and past defect rate without future data contamination:
1. All commits are sorted chronologically by `COMMITTER_DATE` within each project.
2. For each commit at time $t$, author metrics are calculated strictly over the window $[\tau_0, t - \epsilon]$ where $\epsilon > 0$:
   - **`author_cumulative_commits`**: Total commits authored by this developer prior to time $t$.
   - **`author_prior_faults`**: Total fault-inducing commits authored by this developer prior to time $t$.
   - **`author_historical_fault_rate`**: Empirical Bayes smoothed fault rate:
     $$\text{Rate} = \frac{\text{author\_prior\_faults} + 1}{\text{author\_cumulative\_commits} + 10}$$
   - **`author_tenure_days`**: Days elapsed between the author's first recorded commit in the project and the current commit.
   - **`author_velocity_30d`**: Number of commits authored in the preceding 30 days.

---

## 4. Requirement C: Santuario False-Positive & Cross-Project Calibration Investigation

### 4.1 Empirical Diagnosis of the Santuario Anomaly
On the 6 held-out test projects, `org.apache:santuario` exhibits:
- Total commits: 5,746
- Faulty commits: 251 (Prevalence: **4.37%**)
- Default RF Recall: **92.83%**; Tuned HistGradientBoosting Recall: **74.10%**
- Tuned Model Precision: **15.35%** (1,026 False Positives out of 1,212 predicted defects)

**Root Causes**:
1. **Prevalence Mismatch**: The training set has an average defect rate of ~11.1%. By Bayes' Theorem:
   $$P(Y=1 \mid X) = \frac{P(X \mid Y=1) P(Y=1)}{P(X \mid Y=1) P(Y=1) + P(X \mid Y=0) P(Y=0)}$$
   When the prior $P(Y=1)$ drops from $11.1\%$ to $4.37\%$, a fixed decision threshold tuned for higher prior prevalence inevitably produces an explosion of false positives.
2. **Domain-Specific Churn Artifacts**: Santuario is Apache's XML Security implementation. It contains large commits modifying XML encryption schemas, canonicalization test vectors, and cryptographic certificates. These produce massive `LINES_ADDED` spikes without introducing code faults, misleading churn-dominated models.

### 4.2 Research Interventions to Be Evaluated
1. **Empirical Prior Log-Odds Calibration**:
   Adjust uncalibrated model probabilities using the target project's estimated prior:
   $$\text{logit}(p_{\text{calibrated}}) = \text{logit}(p) - \ln\left(\frac{\pi_{\text{train}}}{1 - \pi_{\text{train}}}\right) + \ln\left(\frac{\pi_{\text{target}}}{1 - \pi_{\text{target}}}\right)$$
   We will evaluate whether estimating $\pi_{\text{target}}$ from an initial small historical window of the target project restores precision.
2. **Pareto Frontier & Cost-Sensitive Operating Point Analysis**:
   Map the precision-recall trade-off across thresholds $\tau \in [0.10, 0.90]$ per project to determine the exact operating point required to reach $\ge 50\%$ and $\ge 80\%$ precision on low-prevalence repositories.
3. **NLP Disambiguation of Cryptographic & Test Churn**:
   Test whether commit message features (e.g., `test vector`, `c14n`, `signature`, `certificate`) provide the discriminative signal needed to suppress false alarms on large non-faulty XML test updates.

---

## 5. Requirement D: Experiment Prioritization Order (1 to 10)

The experimental execution is strictly sequenced from foundational verification to complex ensembling:

```
[EXP 1: Pipeline Audit & Baseline Verification]
                      │
                      ▼
[EXP 2: Commit Message NLP Tokenization & Intent Extraction]
                      │
                      ▼
[EXP 3: TF-IDF & Truncated SVD Dimensionality Reduction]
                      │
                      ▼
[EXP 4: Strict Chronological Historical & Process Feature Pipeline]
                      │
                      ▼
[EXP 5: 3-Way Ablation Benchmark (Churn vs NLP vs Hybrid)]
                      │
                      ▼
[EXP 6: Multi-Model Architecture Optimization & Hyperparameter Search]
                      │
                      ▼
[EXP 7: Probability Calibration & Out-of-Fold Stacking Ensemble]
                      │
                      ▼
[EXP 8: Santuario & Cross-Project Prevalence Calibration Study]
                      │
                      ▼
[EXP 9: Validation Split Multi-Objective Threshold Optimization]
                      │
                      ▼
[EXP 10: Single-Pass Final Evaluation on 6 Held-Out Test Projects]
```

1. **Exp 1: Baseline Verification & Chronological Ordering**: Verify deterministic seeds (`seed=42`), sort datasets chronologically per project, verify split boundaries, reproduce baseline metrics.
2. **Exp 2: Commit Message Lexical & Intent Feature Extraction**: Implement regex intent detectors, message length, and token diversity statistics.
3. **Exp 3: TF-IDF & Dimensionality Reduction**: Fit Word + Char TF-IDF and Truncated SVD strictly on `train.csv`. Transform validation and test sets.
4. **Exp 4: Historical & Process Feature Engineering**: Construct cumulative author defect rates, tenure, churn ratios, and interaction terms with zero future leakage.
5. **Exp 5: 3-Way Ablation Benchmark (GroupKFold)**: Evaluate Churn-only vs. NLP-only vs. Fused representations across the 16 training projects using 5-fold GroupKFold.
6. **Exp 6: Model Architecture Search**: Benchmark regularized Logistic Regression, Random Forest, Extra Trees, HistGradientBoosting, and XGBoost across the full feature set.
7. **Exp 7: Probability Calibration & Stacking**: Fit Platt scaling (Sigmoid) and Isotonic Regression on out-of-fold training predictions; evaluate meta-learner stacking.
8. **Exp 8: Santuario & Cross-Project Prevalence Adaptation**: Evaluate prior-adjustment calibration and domain-specific false-alarm reduction.
9. **Exp 9: Multi-Objective Threshold Optimization on Validation Set**: Optimize classification thresholds on the 9 validation projects for: (a) Maximum F1, (b) Maximum MCC, and (c) Precision-constrained ($\ge 50\%$).
10. **Exp 10: Final Held-Out Evaluation on 6 Test Projects**: Run a single evaluation pass on `test.csv` (23,091 commits), compute 1,000 project-stratified bootstrap confidence intervals, and compile per-project performance breakdowns.

---

## 6. Requirement E: Statistical Integrity & Experimental Safeguards

To ensure absolute academic rigor, reproducibility, and publication defensibility:

1. **5-Fold GroupKFold Cross-Validation**:
   - The 16 training projects are grouped into 5 disjoint folds where no project appears in both training and validation within any fold.
   - Mean and standard deviation are reported across all folds ($\mu \pm \sigma$).
2. **Strict Test Partition Isolation**:
   - The 6 held-out test projects (`archiva`, `collections`, `commons-cli`, `dbcp`, `santuario`, `validator`) will be evaluated **exactly once**.
   - No feature scalers, imputers, TF-IDF vectorizers, SVD projections, or decision thresholds will have access to test partition data.
3. **1,000-Resample Project-Stratified Bootstrap Confidence Intervals**:
   - For all test metrics (Accuracy, Balanced Accuracy, Precision, Recall, F1, PR-AUC, ROC-AUC, MCC), report 95% Bootstrap Confidence Intervals: $[\text{CI}_{2.5\%}, \text{CI}_{97.5\%}]$.
4. **Explicit Reporting of 6-Project Sample Scope**:
   - The evaluation will explicitly acknowledge that cross-project results are based on 6 held-out Apache repositories.
   - Per-project breakdowns will be reported alongside aggregate scores to prevent masking repository-level performance variance.
5. **Zero Production Code Overwrite**:
   - Production models (`random_forest_model.txt`, `logistic_regression_model.txt`) and Java core analysis classes will remain completely unmodified.
   - All experimental scripts and models reside strictly in `research/`.

---

## 7. Requirement F: Research Decision Gate

```
================================================================================
                           RESEARCH DECISION GATE
================================================================================
 [!] All Phase 0 audits are verified.
 [!] The revised plan incorporates Requirements A, B, C, D, and E in full.
 [!] Zero source code or production models will be modified.
 [!] Implementation will proceed strictly in research/ upon explicit approval.
================================================================================
```

### Action Required:
Please review this revised implementation plan. Upon your approval, we will proceed immediately to implement **Exp 1 through Exp 4 (Feature Extraction and Pipeline Setup)** inside `research/`.
