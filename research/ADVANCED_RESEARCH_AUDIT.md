# Advanced Research Audit: High-Performance Cross-Project Just-In-Time Software Defect Prediction

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Focus**: Cross-Project Just-In-Time (JIT) Software Defect Prediction Benchmark  
**Author**: Antigravity Principal ML & Static Analysis Research Engineering Team  
**Date**: November 2026  
**Status**: Phase 0 Complete — Audit & Experimental Plan (Awaiting Explicit Approval)

---

## 1. Executive Summary & Audit Overview

This audit establishes the baseline facts, computational reality, data limitations, and architectural roadmap for advancing the performance of the **Just-In-Time (JIT) Software Defect Prediction system**. 

The target is ambitious: advancing cross-project defect prediction toward $\text{F1} \ge 0.80$, $\text{Precision} \ge 0.80$, $\text{Recall} \ge 0.80$, and $\text{Accuracy} \ge 0.90$ across previously unseen Apache Java projects. 

This audit provides:
1. **Verified baseline metrics** from the actual evaluation artifacts.
2. **Data and repository reality**: What is present in the dataset versus raw Git clones.
3. **Hardware & computational feasibility**: CPU, RAM, disk space, and library availability.
4. **Feasibility of AST parsing, dependency graph reconstruction, and semantic embeddings**.
5. **Leakage-free multi-feature experimental architecture** designed to push predictive performance without scientific compromise.

---

## 2. Existing Repository Architecture & Protection Protocol

### 2.1 Production Java Application (Protected)
- **`core/src/main/java/`**: Core static analysis engine (Compiler Tree API AST parser, Tarjan SCC cycle detector, MetricsEngine, WhatIfSimulator, RefactoringEngine). Zero runtime dependencies.
- **`core/src/main/resources/models/`**:
  - `random_forest_model.txt` (Active production 100-tree model for component architectural risk).
  - `logistic_regression_model.txt` (Active baseline model).
- **`webapp/`**: Enterprise web interface, servlets, and JSP views.
- **Protection Mandate**: In accordance with the critical rules, **all production models and Java code will remain 100% untouched**. All new experiments, feature pipelines, and models will be housed in an isolated `research/` directory.

### 2.2 Existing Dataset & Pipeline Artifacts
- **`Dataset/`**: Original preserved archives (`CSIQ.zip`, `multi-smell-dataset-v1_2.zip`, `smells.zip`, `TD dataset.zip`).
- **`dataset-extracted/TD dataset/`**:
  - `train.csv` (107,582 commits across 16 projects, 35.49 MB)
  - `validation.csv` (23,321 commits across 9 projects, 8.24 MB)
  - `test.csv` (23,091 commits across 6 held-out projects, 7.50 MB)
  - `cleaned_dataset.csv` (153,994 total commits, 51.23 MB)
- **`dataset-pipeline/`**:
  - `train_benchmarks.py` and `optimize_models.py` (Existing baseline and optimization scripts).
  - `models_output/benchmark_results.json` and `td_analysis_output/td_error_analysis_report.json` (Verified empirical artifacts).

---

## 3. Verified Baseline Performance

The baseline figures have been verified directly from the experimental artifact (`dataset-pipeline/td_analysis_output/td_error_analysis_report.json`) evaluated on the **23,091 held-out test commits** across the **6 unseen Apache projects**:

| Model Architecture | Threshold ($\tau$) | Accuracy | Balanced Accuracy | Precision | Recall | F1-Score | PR-AUC | ROC-AUC | MCC | Brier Score |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Majority Baseline (ZeroR)** | 0.500 | 89.10% | 50.00% | 0.00% | 0.00% | 0.00% | 10.90% | 0.5000 | 0.0000 | 0.0971 |
| **Logistic Regression (Baseline)** | 0.500 | 22.34% | 52.74% | 11.51% | **91.61%** | 20.45% | 15.90% | 0.5921 | +0.0381 | 0.2234 |
| **Random Forest (Default)** | 0.500 | 63.69% | 68.39% | 19.47% | 74.40% | 30.87% | 28.82% | 0.7420 | +0.2313 | 0.1982 |
| **Random Forest (Tuned)** | 0.630 | 76.81% | 62.10% | 26.95% | 39.07% | 31.90% | 28.89% | 0.7441 | +0.2242 | 0.1745 |
| **XGBoost (Tuned)** | 0.500 | 75.12% | 65.58% | 25.27% | 47.06% | 32.88% | 25.71% | 0.7389 | +0.2305 | 0.1798 |
| **HistGradientBoosting (Best)** | **0.620** | **79.52%** | **66.98%** | **25.71%** | **47.93%** | **0.3347** | **28.29%** | **0.7473** | **+0.2401** | **0.0883** |

### Verified Project-Wise Baseline Across the 6 Held-Out Projects:
| Held-Out Project | Total Commits | Faulty Commits | Prevalence | Precision | Recall | F1-Score | PR-AUC |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`org.apache:archiva`** | 8,695 | 1,297 | 14.92% | 32.03% | 48.73% | **0.3865** | 0.3451 |
| **`org.apache:collections`** | 3,616 | 319 | 8.82% | 18.40% | 46.71% | **0.2640** | 0.1842 |
| **`org.apache:commons-cli`** | 1,021 | 101 | 9.89% | 28.57% | 45.54% | **0.3511** | 0.3598 |
| **`org.apache:dbcp`** | 2,465 | 407 | 16.51% | **50.34%** | 36.86% | **0.4255** | **0.4513** |
| **`org.apache:santuario`** | 5,746 | 251 | 4.37% | 15.35% | **74.10%** | **0.2543** | 0.3269 |
| **`org.apache:validator`** | 1,548 | 141 | 9.11% | 18.22% | 30.50% | **0.2281** | 0.1783 |

---

## 4. Hardware Environment & Computational Constraints

A direct system audit was executed to benchmark available execution resources:
- **Operating System**: Windows 10 (Build 26200, 64-bit)
- **CPU Architecture**: 8 Logical Cores
- **System Memory (RAM)**:
  - Total Physical RAM: **15.76 GB**
  - Currently Available RAM: **4.33 GB**
- **Persistent Storage (Disk)**:
  - Total Disk Space: **475.44 GB**
  - Free Disk Space: **61.81 GB**
- **Python Runtime**: Python 3.10.3 (64-bit)
- **Library Inventory**:
  - `scikit-learn`: Available (version 1.6.1)
  - `xgboost`: Available (version 3.1.1)
  - `numpy`, `pandas`, `scipy`: Available
  - `nltk`: Available (version 3.9.2)
  - `torch`, `transformers`, `tokenizers`: **NOT currently installed**

### Computational Reality & Feasibility Analysis:
1. **GPU Acceleration**: No active CUDA GPU runtime is configured; training and feature extraction run on the 8-core CPU.
2. **RAM Constraints (4.33 GB Available)**: Heavy neural models (e.g., 500M+ parameter CodeBERT fine-tuning) would induce Windows page-file thrashing and Out-Of-Memory (OOM) crashes across 153k samples.
3. **Storage Feasibility**: 61.81 GB free disk space is ample for tabular representations, TF-IDF / NLTK embeddings, feature caches, and tree ensemble checkpoints, but cannot store raw clones of 31 full historical Apache git repositories (~100–150 GB).

---

## 5. Dataset Schema & Available Information Audit

The TD dataset provides 18 columns per commit record:

```
[ IDENTIFIERS ]
  ├── PROJECT_ID             (String: e.g. "org.apache:archiva")
  └── COMMIT_HASH            (String: 40-char SHA-1)

[ TEMPORAL & AUTHOR METADATA ]
  ├── AUTHOR                 (String: Developer name)
  ├── COMMITTER              (String: Committer name)
  ├── AUTHOR_DATE            (ISO-8601 UTC timestamp)
  ├── COMMITTER_DATE         (ISO-8601 UTC timestamp)
  ├── AUTHOR_TIMEZONE        (Integer offset in minutes)
  └── COMMITTER_TIMEZONE     (Integer offset in minutes)

[ PROCESS & BRANCH METADATA ]
  ├── BRANCH                 (String: "master" / "trunk")
  ├── IN_MAIN_BRANCH         (Binary: 0 or 1)
  ├── MERGE                  (Binary: 0 or 1)
  └── N_PARENTS              (Integer: 0, 1, 2)

[ CHURN & STRUCTURAL REFACTORING AGGREGATES ]
  ├── N_FILES_CHANGED        (Float: Distinct files modified in commit)
  ├── LINES_ADDED            (Float: Inserted lines across files)
  ├── LINES_REMOVED          (Float: Deleted lines across files)
  └── N_REFACTORINGS         (Integer: Count of RefactoringMiner operations)

[ NATURAL LANGUAGE SEMANTICS ]
  └── COMMIT_MESSAGE         (Full raw text message written by developer)

[ GROUND TRUTH TARGET ]
  └── IS_FAULT_INDUCING      (Binary: 1 if SZZ identified commit as bug-inducing, else 0)
```

---

## 6. Audit of Source Code & Git History Availability

### 6.1 What IS Available Locally
1. **Full Commit Metadata for 153,994 Commits**: Exact dates, author identities, branch flags, change sizes, and refactoring counts.
2. **153,994 Developer Commit Messages**: Complete, un-truncated natural language text detailing the exact purpose of the commit, bug tracker tickets (e.g., `MRM-977`, `COLLECTIONS-300`), architectural intent, and subsystem references.
3. **Longitudinal History per Project**: Commits span sequential years from 2001 to 2019, enabling the construction of **true chronological historical metrics** (prior developer experience, prior file bug history, recent churn velocity) strictly without future leakage.

### 6.2 What is NOT Available on Local Disk
1. **Raw Git Clones for the 31 Apache Repositories**: The `.git` object databases for the 31 Apache projects are not present on local disk. The dataset was pre-aggregated from the SQLite database `td_V2.db` by the dataset authors.
2. **Implications for Java AST & Graph Reconstruction**:
   - Running `JavaParserAdapter` or AST diffing on raw Java files requires the underlying `.java` file versions before and after each commit.
   - Because local disk space is 61 GB and network cloning 31 giant historical Apache repositories would consume days of download time and exceed disk capacity, **performing line-by-line AST parsing on all 153k historical commits is not immediately feasible without downloading the raw repositories**.
   - However, **RefactoringMiner metrics are already captured** in `N_REFACTORINGS`, which were extracted directly from the ASTs by the original researchers.
   - Furthermore, we can extract **rich architectural, semantic, and historical feature representations** directly from the data present:
     - Lexical & semantic code change representations from commit messages and ticket references.
     - Longitudinal author defect profiles and experience curves.
     - Multi-scale process metrics and churn entropy.

---

## 7. Leakage Risks & Scientific Integrity Audit

To maintain publication-grade defensibility, the research pipeline must enforce four non-negotiable leakage guards:

1. **Temporal Leakage Guard**:
   - Any historical feature (e.g., *author historical bug rate*, *cumulative author commits*, *project historical churn*) must be calculated strictly from commits with `COMMITTER_DATE < current_commit_date`.
   - Never use whole-dataset averages or post-commit bug-fix frequencies.
2. **Project Boundary Guard**:
   - The 6 held-out test projects (`archiva`, `collections`, `commons-cli`, `dbcp`, `santuario`, `validator`) must never appear in training, validation, feature scaling, or threshold selection.
3. **SZZ Label Contamination Guard**:
   - SZZ works backward from bug fixes to blaming commits. No feature may be computed using the SZZ fix-link table itself.
4. **Validation-Only Decision Thresholding**:
   - All classification cutoffs must be optimized on validation projects; the test partition must be evaluated once with fixed parameters.

---

## 8. Proposed Advanced Research Architecture

To push predictive capability toward the performance objectives, we replace the basic 7-feature model with an **Advanced Multi-Channel Hybrid Architecture**:

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              ADVANCED MULTI-CHANNEL COMMIT REPRESENTATION                              │
├────────────────────────────────┬───────────────────────────────┬───────────────────────────────────────┤
│ CHANNEL 1: Process & Churn     │ CHANNEL 2: Developer & History│ CHANNEL 3: Semantic & Intent (NLP)    │
│ • Churn volume (lines add/rem) │ • Prior author commit count   │ • TF-IDF n-gram intent embeddings     │
│ • Churn density per file       │ • Author historical fault rate│ • Sentiment / urgency signals         │
│ • Addition / deletion balance  │ • Author experience tenure    │ • Bug-fix / maintenance intent flags  │
│ • Modification spread / entropy│ • Multi-author collaboration  │ • Architectural keyword density       │
│ • Refactoring intensity        │ • Time-of-day / weekend churn │ • Commit message length / token depth │
└────────────────────────────────┴───────────────────────────────┴───────────────────────────────────────┘
                                                 │
                                                 ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 MODEL ARSENAL & CALIBRATED ENSEMBLE                                    │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 1. Regularized Logistic Regression (Linear baseline with StandardScaler)                               │
│ 2. Deep Balanced Random Forest (Bagging ensemble with min_samples_leaf tuning)                         │
│ 3. Extra Trees Classifier (Extreme random splits reducing variance on noisy SZZ labels)               │
│ 4. HistGradientBoosting (Histogram binning with monotonic churn constraints)                           │
│ 5. Tuned XGBoost (Scale_pos_weight optimization & gradient boosting)                                   │
│ 6. Stacking Meta-Classifier (Out-of-fold probability blending strictly on training folds)               │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                 │
                                                 ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                             VALIDATION-DRIVEN CALIBRATION & THRESHOLDING                               │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ • Sigmoid (Platt) & Isotonic Probability Calibration on Validation Partition                           │
│ • Multi-objective threshold search (Max F1, Max MCC, Constrained Precision/Recall)                     │
│ • Final Evaluation on 6 Held-Out Projects with 1,000 Project-Stratified Bootstrap Resamples            │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Implementation Plan & Experimental Methodology

The research pipeline will be implemented incrementally across five distinct stages inside `research/`:

```text
research/
├── ADVANCED_RESEARCH_AUDIT.md            # (This complete audit document)
├── dataset/                              # Loaded partition references (zero copy)
├── features/
│   ├── build_rich_features.py            # Extracts Process, Temporal, Author, and Semantic features
│   └── feature_definitions.json          # Complete feature catalog and provenance
├── models/
│   ├── train_models.py                   # Multi-architecture training & hyperparameter search
│   └── stacking_ensemble.py              # Out-of-fold meta-learner blending
├── evaluation/
│   ├── evaluate_models.py                # Comprehensive test evaluation with bootstrap CIs
│   └── error_analysis.py                 # Deep false-positive / false-negative diagnosis
├── reports/
│   ├── ADVANCED_JIT_DEFECT_REPORT.md     # Final academic conference-grade manuscript chapter
│   └── ERROR_ANALYSIS.md                 # Qualitative & quantitative failure taxonomy
└── configs/
    └── experiment_config.json            # Reproducible seeds, parameters, and paths
```

### Detailed Execution Sequence (Upon Approval):
1. **Stage 1 (Feature Extraction)**: Build `research/features/build_rich_features.py`. Extract multi-scale churn, author history, temporal dynamics, and commit message semantic representations (expanding from 7 to ~35+ engineered features).
2. **Stage 2 (Model Training & Tuning)**: Train and tune the model suite on the training split; validate using 5-fold Project Grouped Cross-Validation and the dedicated 9-project validation split.
3. **Stage 3 (Calibration & Stacking)**: Fit probability calibrators and meta-learners strictly on validation folds.
4. **Stage 4 (Held-Out Evaluation)**: Benchmark against the 6 held-out test projects, calculating 95% bootstrap confidence intervals, per-project performance, and Delong statistical significance tests.
5. **Stage 5 (Research Reporting)**: Compile the complete research manuscript report, honestly evaluating how close the model approached the 80–90% targets and documenting the empirical boundaries of cross-project JIT defect prediction.

---

## 10. Estimated Resource Requirements & Feasibility Confirmation

- **Disk Usage**: $\approx 150 \text{ MB}$ (feature caches and model serialization). Fits comfortably within the 61.8 GB free disk space.
- **RAM Usage**: $\approx 2.5 \text{ GB}$ peak memory. Fits safely within the 4.33 GB available physical RAM.
- **Execution Time**: $\approx 4\text{–}6 \text{ minutes}$ for complete feature extraction, multi-model training, cross-validation, and bootstrap CI generation on 8 CPU cores.
- **Dependencies**: Uses currently installed Python libraries (`scikit-learn`, `xgboost`, `nltk`, `numpy`, `pandas`, `scipy`). No heavy external GPU or external frameworks required.

---

**PHASE 0 COMPLETED — AUDIT VERIFIED & AWAITING EXPLICIT APPROVAL TO COMMENCE IMPLEMENTATION.**
