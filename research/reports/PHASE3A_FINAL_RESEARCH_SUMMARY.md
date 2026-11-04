# PHASE 3A: FINAL RESEARCH SUMMARY
## Executive Synthesis for IEEE Manuscript Preparation

**Project**: AI-Powered Dependency & Architecture Refactoring Analyzer  
**Subsystem**: Just-In-Time (JIT) Software Defect Prediction Research Engine  
**Document Purpose**: Concise, factual empirical summary supporting IEEE TSE/EMSE manuscript drafting  
**Status**: Authoritative & Frozen  
**Date**: 2026-11-04  

---

## 1. Research Objective

In cross-project Just-In-Time (JIT) software defect prediction, predictive models are trained on historical source projects and deployed to unseen target repositories. A pervasive challenge in this cross-project transfer is **prevalence shift** (class imbalance mismatch between source and target projects). This research investigates whether target-project prevalence adaptation—combining unsupervised soft-label estimation, Bayesian Beta-Binomial updating, and quantile-matched decision threshold tuning—can improve defect prediction performance across unseen repositories under realistic **chronological feedback latency constraints** (90-day defect maturation delay).

---

## 2. Frozen Model & Feature Engineering

* **Model Architecture**: Frozen `SUB-1__XGBoost` classifier (histogram tree construction, shallow depth, conservative regularization).
* **Feature Dimension (43 Features)**:
  - **Code Churn (`CH-1`, 5 dims)**: Added lines, deleted lines, total lines, churn ratio, entropy of changes across files.
  - **Process & Modularity (`CH-2`, 8 dims)**: Number of modified files, modified subsystems, directories, directory depth, change type, fix commit flag, test commit flag, documentation flag.
  - **Developer Intent (`CH-3`, 6 dims)**: Commit message length, token entropy, and domain keyword flags (bug fix, refactor, performance, documentation).
  - **Combined Author History (`CH-5C`, 24 dims)**: Zero-label author experience (prior commits, tenure, velocity) combined with delayed fault feedback (faults committed in preceding commits matured under a strict 90-day lag).
  - *Exclusion*: High-dimensional NLP bag-of-words (`CH-4`) was ablated and excluded to maximize CPU inference throughput and prevent overfitting.
* **Operating Decision Threshold**: $\tau^* = 0.170$ (frozen from training nested cross-validation).

---

## 3. Dataset Structure & Quarantine Boundaries

The research utilizes the standardized Apache Java Temporal Dataset (TD):
* **Training Dataset (Source)**: 107,582 commits across 16 Apache projects ($\pi_s = 11.144\%$).
* **Validation Dataset (Target Adaptation)**: 23,321 commits across 9 unseen Apache projects ($\pi_v = 12.096\%$).
  - Projects: `batik` (7,015), `codec` (2,144), `commons-exec` (700), `commons-fileupload` (1,180), `commons-jelly` (1,945), `daemon` (1,251), `digester` (2,192), `httpclient` (3,290), `httpcore` (3,604).
* **Burn-In Window**: $K = 100$ commits per project ($9 \times 100 = 900$ commits total).
* **Post-Burn-In Evaluation Window**: Exactly **22,421 commits** ($23,321 - 900$). Contains **2,680 defect-inducing commits** and **19,741 clean commits**.
* **Held-Out Test Dataset (Final Evaluation)**: 23,091 commits across 6 projects (`archiva`, `collections`, `commons-cli`, `dbcp`, `santuario`, `validator`) — **100% QUARANTINED AND UNTOUCHED**.

---

## 4. Experimental Design & Adaptation Strategies

1. **Chronological Stream Evaluation**: Commits are processed in strict timestamp order.
2. **Realistic 90-Day Defect Maturation Queue**: Supervised labels from prior commits are only made available to adaptation estimators after 90 calendar days have elapsed from commit time, preventing future-label leakage.
3. **Adaptation Methods Evaluated**:
   - `Base_K100`: Fixed model and threshold $\tau^* = 0.170$ evaluated on post-burn-in commits.
   - `A1_SoftMean`: Unsupervised adaptation using expected target prevalence from uncalibrated probabilities.
   - `Quantile_Thresh_K100_Delay90d`: Adjusting the decision threshold to match the estimated Bayesian prevalence: $\tau_{\text{adapted}} = \text{quantile}(p, 1 - \hat{\pi}_{\text{Bayes}})$.
   - `Bayes_K100_Delay90d`: Bayesian Beta-Binomial updating with prior strength $M \in \{10, 20, 50\}$.

---

## 5. Summary Empirical Findings

### Core Quantitative Benchmark Results ($N = 22,421$ Commits)

| Strategy | Macro Precision | Macro Recall (9 Proj) | Macro Recall (8 Proj) | Macro F1 | Micro F1 | Macro MCC | PR-AUC (8 Valid) | ROC-AUC (8 Valid) | Brier Score | ECE | Wilcoxon vs Base (F1) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`Base_K100`** | 27.57% | **53.87%** | **60.60%** | **0.3563** | **45.05%** | **+0.2784** | **0.3882** | **0.8023** | **0.0792** | **0.0209** | Reference |
| **`Quantile_Thresh`** | **31.69%** | 42.75% | 48.10% | 0.3261 | 37.76% | +0.2631 | **0.3882** | **0.8023** | **0.0792** | **0.0209** | $W=5.0, p=0.0781$ |
| **`Bayes_M50`** | 24.52% | 59.73% | 67.20% | 0.3280 | 41.57% | +0.2531 | **0.3882** | **0.8023** | 0.0848 | 0.0483 | $W=2.0, p=0.0938$ |
| **`Bayes_M20`** | 24.27% | 60.71% | 68.30% | 0.3207 | 40.00% | +0.2455 | **0.3882** | **0.8023** | 0.0885 | 0.0617 | $W=2.0, p=0.0938$ |
| **`Bayes_M10`** | 24.18% | 60.48% | 68.04% | 0.3116 | 39.08% | +0.2379 | **0.3882** | **0.8023** | 0.0909 | 0.0701 | $\mathbf{W=0.0, p=0.0313^*}$ |

### Reconciled Pooled Confusion Counts ($N = 22,421$, Defect Commits $= 2,680$)
* **`Base_K100`**: $\text{TP}=1,764, \text{FP}=3,388, \text{TN}=16,353, \text{FN}=916 \implies \text{Micro-F1} = 45.05\%$, $\text{Micro-Acc} = 80.80\%$.
* **`Quantile_Thresh`**: $\text{TP}=1,164, \text{FP}=2,322, \text{TN}=17,419, \text{FN}=1,516 \implies \text{Micro-F1} = 37.76\%$, $\text{Micro-Acc} = 82.88\%$.
* **`Bayes_M50`**: $\text{TP}=1,894, \text{FP}=4,539, \text{TN}=15,202, \text{FN}=786 \implies \text{Micro-F1} = 41.57\%$, $\text{Micro-Acc} = 76.25\%$.
* **`Bayes_M20`**: $\text{TP}=1,917, \text{FP}=4,987, \text{TN}=14,754, \text{FN}=763 \implies \text{Micro-F1} = 40.00\%$, $\text{Micro-Acc} = 74.35\%$.
* **`Bayes_M10`**: $\text{TP}=1,911, \text{FP}=5,190, \text{TN}=14,551, \text{FN}=769 \implies \text{Micro-F1} = 39.08\%$, $\text{Micro-Acc} = 73.42\%$.

---

## 6. Scientific Insights & Key Takeaways

1. **Ranking Invariance Theorem**: Prior-probability adjustment formulas $\text{logit}(p_{\text{adapted}}) = \text{logit}(p) + \ln(\pi_t / (1 - \pi_t)) - \ln(\pi_s / (1 - \pi_s))$ are strictly monotonic transformations. Consequently, **ROC-AUC and PR-AUC cannot be improved by prior-shift adaptation alone** (both are identical down to $10^{-12}$).
2. **Quantile Threshold Tuning as an Operational False-Alarm Mitigator**: Adapting the operating threshold via quantile matching produces a **$+4.12$ percentage point gain in macro precision** ($27.57\% \to 31.69\%$, improving 6 of 9 projects). In defect-heavy systems like `httpclient`, precision surges from $42.23\% \to 61.48\%$. However, this is an **operational trade-off** that costs $-11.12$ pp recall ($53.87\% \to 42.75\%$) and **does not achieve statistical significance** ($p = 0.3125$).
3. **The Maturation Latency Bottleneck**: In fast-moving projects (`batik`, 500 commits in 18 days; `httpcore`, 100 commits in 58 days), zero defect labels have matured at the end of burn-in, forcing estimators to fall back to the source prior.
4. **Bayesian Under-Regularization Penalty**: Weak prior shrinkage ($M=10$) causes a statistically significant degradation in F1 ($p = 0.0313^*$) due to localized defect burst over-sensitivity.

---

## 7. Main Limitations

* **Validation-Only Scope**: Findings are derived from 9 unseen validation projects; final confirmation on the 6 held-out test projects is reserved for Phase 4.
* **Non-Parametric Power**: With $N=9$ projects, non-parametric tests require 7+ consistent wins to achieve significance at $\alpha = 0.05$.
* **Single-Class Repositories**: Repositories with zero post-burn-in defects (`commons-exec`) yield mathematically undefined recall and MCC, requiring strict dual reporting.

---

## 8. Gate Completion & Research Integrity Affirmation

* **Gate Status**: Gates 1, 2, 3, 4, 4.1, 4.2, 4.3, and 4.3.1 are **100% complete and passed**.
* **Model Immutability**: No retraining of SUB-1 XGBoost; no new predictions generated.
* **Test Quarantine**: The held-out test dataset `dataset-extracted/TD dataset/test.csv` was **100% quarantined and never opened**.
* **Production Protection**: The production application in `core/` was never modified.
* **Phase 3B**: Has not started.

---

## 9. Future Work (Phase 3B / Phase 4 Boundary)

* **Phase 3B (Target Distribution Adaptation)**: Unsupervised domain adaptation of feature distributions (Covariate Shift adaptation via kernel mean matching or density ratio estimation).
* **Phase 4 (Final Evaluation)**: Unblinding the 6 held-out test projects (`archiva`, `collections`, `commons-cli`, `dbcp`, `santuario`, `validator`) for final publication evaluation.
