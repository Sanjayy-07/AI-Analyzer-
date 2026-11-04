# Revised Phase 2 Implementation Plan: High-Performance JIT Defect Prediction

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Component**: Advanced JIT Defect Prediction Benchmark (`TD dataset`)  
**Phase**: Phase 2 Plan (Stages 1 & 2 Approved: Unbiased Nested Thresholding & Multi-Channel Ablation)  
**Authors**: Antigravity Principal ML & Static Analysis Research Engineering Team  
**Date**: November 2026  
**Status**: APPROVED for Stage 1 & Stage 2 Implementation (Subject to Mandatory Refinements)  

---

## 1. Executive Summary & Audit of Methodological Requirements

Phase 1 demonstrated that multi-modal fusion substantially outperforms churn-only baselines. However, to ensure absolute publication defensibility, the user has approved Phase 2 strictly for **Stage 1 (Unbiased Nested Thresholding)** and **Stage 2 (Multi-Channel Ablation Matrix & Pilot)** with 7 mandatory scientific refinements:

1. **Subdivision of Author History (CH-5)**:
   - **CH-5A (Zero-Label Experience)**: Prior commits, tenure, velocity, repository commit sequence. Uses **zero defect labels**; completely causal at commit time.
   - **CH-5B (Delayed Defect Feedback)**: Prior faults and empirical Bayes fault rate with simulated 90-day defect reporting delay ($\Delta t_{\text{delay}} = 90\text{ days}$).
   - **CH-5C (Combined Author History)**: Union of CH-5A and CH-5B.
2. **Clarification of 90-Day Defect Delay**:
   - The dataset contains `COMMITTER_DATE` and `AUTHOR_DATE`, but **no observed bug-discovery timestamps**. Therefore, the 90-day delay is an **assumed operational feedback delay**, simulated as $T_{\text{discovery}} = T_{\text{commit}} + 90\text{ days}$. It is explicitly documented as a simulation, not observed historical discovery.
3. **Computational Safeguards & Checkpointing**:
   - Total model fits estimated beforehand.
   - Resumable checkpointing with atomic disk writes (`tempfile` + `os.replace`).
   - Leakage-safe caching of fold-specific transformed matrices.
   - Compulsory **small pilot experiment** benchmarking runtime and memory before launching any full evaluation.
4. **Statistical Inference Rigor**:
   - Nine validation projects treated as a small sample ($N=9$).
   - Report effect sizes, bootstrap confidence intervals, and p-values with explicit small-sample caveats.
   - Commit-level bootstrap uncertainty explicitly distinguished from project-level cluster uncertainty.
   - DeLong's test not interpreted as correcting for repository-level clustering.
5. **Research Safeguards Preserved**:
   - Thresholds never tuned on evaluation labels.
   - Preprocessing 100% fold-isolated.
   - 6 test projects quarantined.
   - Production code and model artifacts 100% untouched.
   - All code in `research/`.
6. **Strict Scope Limitation**:
   - Stage 1 and Stage 2 only. No Stage 3+ or final test evaluation. No claims of 80–90% guaranteed targets.
7. **Deliverables Protocol**:
   - Experiment configs, unit tests, leakage audits, pilot results, and checkpoint verification reports.

---

## 2. Requirement 1 & 7: Two-Tier Unbiased Thresholding Architecture

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        UNBIASED THRESHOLD-SELECTION PROTOCOL                           │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│  LEVEL 1: Nested GroupKFold CV (Within 16 Training Projects)                           │
│  ───────────────────────────────────────────────────────────                           │
│  • Outer Loop (5 Folds):                                                               │
│      - Partition 16 projects into 4 train-project groups and 1 test-project group.      │
│      - Inner Loop (4 Folds across the 4 train-project groups):                         │
│          * Train fold-pipeline & estimator on 3 groups.                                │
│          * Predict probabilities on 1 inner validation group.                          │
│          * Pool inner OOF predictions across all 4 train groups.                       │
│          * Sweep τ ∈ [0.05, 0.95] to find τ*_inner = argmax F1(inner_OOF).             │
│      - Fit final outer estimator on all 4 train groups.                                │
│      - Transform outer test group with outer fold-pipeline.                            │
│      - Evaluate test group probabilities using FROZEN τ*_inner.                        │
│      - Output fold metrics at τ = 0.50 and τ = τ*_inner (Zero Test-Label Peeking!).    │
│                                                                                        │
│  LEVEL 2: Global Training Out-of-Fold Threshold for Validation Set                     │
│  ─────────────────────────────────────────────────────────────────                     │
│  • Pool out-of-fold predictions across all 16 training projects.                       │
│  • Find global operating points:                                                       │
│      - τ*_F1: Threshold maximizing training OOF F1.                                    │
│      - τ*_MCC: Threshold maximizing training OOF MCC.                                  │
│      - τ*_Prec50: Lowest threshold guaranteeing Precision >= 0.50 on training OOF.     │
│  • Freeze thresholds and apply blindly to validation projects.                         │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Requirement 1 & 2: Author History Protocols & 90-Day Delay Clarification

### 3.1 Data Reality & Simulated Delay Documentation
- The local TD dataset (`train.csv`, `validation.csv`, `test.csv`) does **not** record the timestamp when SZZ linked a fix to a commit.
- In reality, developers do not know a commit is faulty until an issue is filed and fixed.
- **Scientific Simulation**: We simulate a conservative 90-day defect discovery window:
  $$T_{\text{feedback}} = T_{\text{commit}} + 90\text{ days}$$
  For any commit evaluated at timestamp $T$:
  - A historical commit at $T_j < T$ is always counted in `prior_commits` (the commit exists in Git history).
  - Its defect label $y_j$ is only visible if $T_j + 90\text{ days} < T$. If $T_j + 90\text{ days} \ge T$, the defect outcome is considered **pending / unresolved** and does not contribute to `prior_faults`.

### 3.2 The CH-5 Tripartite Subdivision
1. **CH-5A (Zero-Label Experience)**:
   - `author_prior_commits`, `author_tenure_days`, `author_velocity_30d`, `project_prior_commits`.
   - **Zero defect labels used**. Completely causal at commit time.
2. **CH-5B (Delayed Defect Feedback)**:
   - `author_prior_faults_90d`, `author_fault_rate_90d`, `project_prior_faults_90d`, `project_prior_fault_rate_90d`.
   - Incorporates past defect rate under simulated 90-day lag.
3. **CH-5C (Combined Author History)**:
   - Union of CH-5A and CH-5B.

---

## 4. Requirement 3: Multi-Channel Feature Ablation Matrix

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        FEATURE CHANNEL ISOLATION MATRIX                                │
├─────────┬───────────────────────────────┬──────┬───────────────────────────────────────┤
│ Channel │ Feature Group                 │ Dims │ Key Features                          │
├─────────┼───────────────────────────────┼──────┼───────────────────────────────────────┤
│ CH-1    │ Pure Churn Volume             │  6   │ LINES_ADDED, LINES_REMOVED, net_churn,│
│         │                               │      │ total_churn, churn_ratio, del_ratio   │
├─────────┼───────────────────────────────┼──────┼───────────────────────────────────────┤
│ CH-2    │ Process & Code Structure      │ 11   │ N_FILES_CHANGED, N_REFACTORINGS,      │
│         │                               │      │ churn_per_file, refactorings_per_file,│
│         │                               │      │ commit_hour, is_weekend, is_off_hours │
├─────────┼───────────────────────────────┼──────┼───────────────────────────────────────┤
│ CH-3    │ Developer Intent Mining       │ 11   │ is_bug_fix_intent, is_refactoring,    │
│         │                               │      │ has_ticket_ref, is_maint, msg_len     │
├─────────┼───────────────────────────────┼──────┼───────────────────────────────────────┤
│ CH-4    │ Sub-word & Semantic NLP       │ 50   │ Char TF-IDF (3-5) + Word TF-IDF (1-2) │
│         │                               │      │ reduced via TruncatedSVD (50 dims)    │
├─────────┼───────────────────────────────┼──────┼───────────────────────────────────────┤
│ CH-5A   │ Zero-Label Author Experience  │  4   │ author_prior_commits, tenure, velocity│
├─────────┼───────────────────────────────┼──────┼───────────────────────────────────────┤
│ CH-5B   │ Delayed Defect Feedback (90d) │  4   │ author_prior_faults_90d, fault_rate   │
├─────────┼───────────────────────────────┼──────┼───────────────────────────────────────┤
│ CH-5C   │ Combined Author History       │  8   │ Union of CH-5A and CH-5B              │
└─────────┴───────────────────────────────┴──────┴───────────────────────────────────────┘
```

### Planned Ablation Runs:
1. **Isolated Channel Evaluation**: CH-1, CH-2, CH-3, CH-4, CH-5A, CH-5B, CH-5C independently.
2. **Stepwise Additive Evaluation**:
   - CH-1
   - CH-1 + CH-2
   - CH-1 + CH-2 + CH-3
   - CH-1 + CH-2 + CH-3 + CH-4
   - CH-1 + CH-2 + CH-3 + CH-4 + CH-5A (Multi-modal with zero-label author experience)
   - CH-1 + CH-2 + CH-3 + CH-4 + CH-5C (Full Multi-modal with combined author history)
3. **Leave-One-Channel-Out**: Full model minus each channel.

---

## 5. Computational Cost, Checkpointing & Pilot Protocol

### 5.1 Estimated Computational Cost for Nested CV
- In nested 5-Fold GroupKFold:
  - Outer Folds: 5
  - Inner Folds per Outer Fold: 4
  - Inner model fits: $5 \times 4 = 20\text{ inner fits per configuration}$.
  - Outer model fits: $5\text{ outer fits per configuration}$.
  - Total fits per configuration: $25\text{ fits}$.
- If running 10 ablation configurations: $10 \times 25 = 250\text{ model fits}$.
- **Computational Optimization**:
  - Feature transformation (TF-IDF + SVD) is fitted at the fold level and its transformed numerical matrices are cached to `research/cache/` using deterministic fold hashes.
  - Linear models (`LogisticRegression`) take ~1–2s per fit.
  - Gradient boosting will be benchmarked in the pilot to determine exact feasibility.

### 5.2 Atomic Checkpointing
- All experiment progress is written to `research/checkpoints/stage2_checkpoint.json` using atomic temporary file renaming (`.tmp` $\to$ `.json`).
- If execution is interrupted, the pipeline resumes from the last completed configuration/fold without repeating computation.

### 5.3 Pilot Protocol (Mandatory Gate)
- Run a 2-fold pilot on a 4-project subset measuring:
  - Preprocessing time
  - Inner CV threshold selection time
  - Peak RAM and disk cache size
  - Threshold isolation verification
- Report pilot metrics and obtain user confirmation before launching full 16-project nested runs.

---

## 6. Statistical Rigor Protocol

1. **Small Sample Context**: The 9 validation projects will be analyzed as $N=9$ cluster samples.
2. **Dual Uncertainty Reporting**:
   - Commit-level bootstrap uncertainty (sampling variability of commit classifications).
   - Project-level bootstrap uncertainty (resampling entire repositories with replacement).
3. **Hypothesis Testing**:
   - Paired Wilcoxon signed-rank test across the 9 validation projects. Report exact $W$-statistic, rank-biserial correlation effect size, and two-sided $p$-value.
   - DeLong's test reported strictly for commit-level ROC curve comparison, explicitly acknowledging that it assumes independent observations and does not adjust for project clustering.

---

## 7. Stage 1 & Stage 2 Execution Roadmap

```
[STEP 1: Update experiment_config.json & historical_features.py with CH-5A/B/C and 90d delay]
                                        │
                                        ▼
[STEP 2: Implement research/evaluation/nested_threshold.py (Level 1 & Level 2 protocols)]
                                        │
                                        ▼
[STEP 3: Implement research/utils/checkpoint.py (Atomic resume & caching)]
                                        │
                                        ▼
[STEP 4: Implement Unit Tests (Threshold isolation, Channel partitioning, 90d delay)]
                                        │
                                        ▼
[STEP 5: Execute Small Pilot Benchmark & Measure Resources / Threshold Correctness]
                                        │
                                        ▼
[STEP 6: Compile Phase 2 Stage 1 & 2 Deliverables Report for User Review]
```

---

## 8. Research Decision Gate

```
================================================================================
                           RESEARCH DECISION GATE
================================================================================
 [!] Plan updated with all 7 user refinements.
 [!] CH-5 tripartite subdivision and 90-day feedback delay formalised.
 [!] Nested CV and atomic checkpointing specified.
 [!] Pilot protocol established before full benchmark execution.
 [!] 6 Test projects remain quarantined. Production code protected.
================================================================================
```
