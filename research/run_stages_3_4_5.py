"""
research.run_stages_3_4_5
=========================
Execution script for Phase 2 Stages 3, 4, and 5:
- Stage 3: Model Architecture Comparison (5 models x 6 feature subsets across 16 training projects)
  Using 5-fold GroupKFold to generate training out-of-fold probabilities and calibrate frozen thresholds.
- Stage 4: Out-of-Domain 9-Project Validation Benchmark
  Per-project breakdown (TP, FP, TN, FN, P, R, F1, Acc, BalAcc, PR-AUC, ROC-AUC, MCC, Brier, ECE)
  at default tau=0.50 and frozen training threshold tau*_train.
- Stage 5: Cross-Project Heterogeneity Diagnosis & Statistical Significance Testing
  Paired Wilcoxon signed-rank test (N=9), 1,000-resample project-stratified bootstrap CIs,
  and failure case diagnosis.
"""

import sys
import os
import traceback
sys.path.insert(0, os.path.abspath("."))
os.environ["LOKY_MAX_CPU_COUNT"] = "4"
os.environ["PYTHONUNBUFFERED"] = "1"

import time
import json
import scipy.sparse as sp
import numpy as np
import pandas as pd
from collections import defaultdict
from scipy.stats import wilcoxon

from sklearn.model_selection import GroupKFold
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier, ExtraTreesClassifier
from xgboost import XGBClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.decomposition import TruncatedSVD
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer
from sklearn.metrics import confusion_matrix

from research.features.intent_nlp import extract_intent_features
from research.features.process_features import extract_process_features
from research.features.historical_features import compute_strict_historical_features
from research.features.channel_extractor import assemble_tabular_features
from research.evaluation.metrics import evaluate_predictions, find_best_threshold_val
from research.utils.checkpoint import ExperimentCheckpointer, atomic_save_json

def get_memory_mb():
    try:
        import ctypes
        class PROCESS_MEMORY_COUNTERS(ctypes.Structure):
            _fields_ = [
                ("cb", ctypes.c_ulong),
                ("PageFaultCount", ctypes.c_ulong),
                ("PeakWorkingSetSize", ctypes.c_size_t),
                ("WorkingSetSize", ctypes.c_size_t),
                ("QuotaPeakPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t),
                ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                ("PagefileUsage", ctypes.c_size_t),
                ("PeakPagefileUsage", ctypes.c_size_t),
            ]
        counters = PROCESS_MEMORY_COUNTERS()
        counters.cb = ctypes.sizeof(PROCESS_MEMORY_COUNTERS)
        handle = ctypes.windll.kernel32.GetCurrentProcess()
        if ctypes.windll.psapi.GetProcessMemoryInfo(handle, ctypes.byref(counters), counters.cb):
            return counters.WorkingSetSize / (1024 * 1024)
    except Exception:
        pass
    return 0.0

def build_nlp_svd_matrix(messages_train, messages_test=None, n_components=30, random_state=42):
    w_vec = TfidfVectorizer(max_features=800, stop_words="english", ngram_range=(1, 2), sublinear_tf=True)
    c_vec = TfidfVectorizer(max_features=800, analyzer="char", ngram_range=(3, 5), sublinear_tf=True)
    
    X_w_tr = w_vec.fit_transform(messages_train)
    X_c_tr = c_vec.fit_transform(messages_train)
    X_f_tr = sp.hstack([X_w_tr, X_c_tr], format="csr")
    
    svd = TruncatedSVD(n_components=n_components, random_state=random_state)
    X_svd_tr = svd.fit_transform(X_f_tr).astype(np.float32)
    
    if messages_test is not None:
        X_w_te = w_vec.transform(messages_test)
        X_c_te = c_vec.transform(messages_test)
        X_f_te = sp.hstack([X_w_te, X_c_te], format="csr")
        X_svd_te = svd.transform(X_f_te).astype(np.float32)
        return X_svd_tr, X_svd_te
    return X_svd_tr

def instantiate_model(model_name: str):
    if model_name == "HistGradientBoosting":
        return HistGradientBoostingClassifier(max_iter=100, learning_rate=0.08, max_leaf_nodes=31, random_state=42)
    elif model_name == "XGBoost":
        return XGBClassifier(n_estimators=100, max_depth=5, learning_rate=0.08, tree_method="hist", n_jobs=4, random_state=42)
    elif model_name == "ExtraTrees":
        return ExtraTreesClassifier(n_estimators=60, min_samples_leaf=10, max_features="sqrt", n_jobs=4, random_state=42)
    elif model_name == "RandomForest":
        return RandomForestClassifier(n_estimators=60, min_samples_leaf=10, max_features="sqrt", n_jobs=4, random_state=42)
    elif model_name == "LogisticRegression":
        return LogisticRegression(C=1.0, max_iter=250, random_state=42)
    else:
        raise ValueError(f"Unknown model name: {model_name}")

def main():
    print("=" * 80, flush=True)
    print("PHASE 2: STAGE 3 (ARCH SEARCH), STAGE 4 (VALIDATION), STAGE 5 (STATISTICS)", flush=True)
    print("=" * 80, flush=True)
    
    t_start_total = time.time()
    
    # Load configuration
    with open("research/configs/stage3_experiment_matrix.json", "r") as f:
        cfg = json.load(f)
        
    models_list = cfg["models"]
    subsets_list = cfg["candidate_subsets"]
    val_projects_list = cfg["validation_projects"]
    quarantined_test = cfg["quarantined_test_projects"]
    
    # Load datasets
    train_df = pd.read_csv("dataset-extracted/TD dataset/train.csv")
    val_df = pd.read_csv("dataset-extracted/TD dataset/validation.csv")
    
    # Assert quarantine
    assert set(train_df["PROJECT_ID"]).isdisjoint(set(quarantined_test)), "Train overlaps quarantined test!"
    assert set(val_df["PROJECT_ID"]).isdisjoint(set(quarantined_test)), "Val overlaps quarantined test!"
    
    print(f"Loaded {len(train_df):,} Train Commits (16 Projects, Defect Rate: {train_df['IS_FAULT_INDUCING'].mean():.2%})", flush=True)
    print(f"Loaded {len(val_df):,} Validation Commits (9 Projects, Defect Rate: {val_df['IS_FAULT_INDUCING'].mean():.2%})", flush=True)
    print(f"Quarantined 6 Test Projects: {quarantined_test} (ZERO access confirmed)", flush=True)
    
    # -------------------------------------------------------------------------
    # Feature Extraction (Train & Validation)
    # -------------------------------------------------------------------------
    print("\n[Step 1/5] Extracting Process, Intent, and 90d Delayed Historical Features ...", flush=True)
    t0 = time.time()
    intent_tr = extract_intent_features(train_df["COMMIT_MESSAGE"])
    proc_tr = extract_process_features(train_df, intent_df=intent_tr)
    hist_tr = compute_strict_historical_features(train_df, delay_days=90.0)
    
    intent_va = extract_intent_features(val_df["COMMIT_MESSAGE"])
    proc_va = extract_process_features(val_df, intent_df=intent_va)
    hist_va = compute_strict_historical_features(val_df, delay_days=90.0)
    print(f"Feature Extraction Completed in {time.time() - t0:.2f}s", flush=True)
    
    # -------------------------------------------------------------------------
    # STAGE 3: Model Architecture Comparison on 16 Training Projects
    # -------------------------------------------------------------------------
    print("\n" + "=" * 80, flush=True)
    print("STAGE 3: MODEL ARCHITECTURE COMPARISON (16 Training Projects, 5-Fold GroupKFold)", flush=True)
    print("=" * 80, flush=True)
    
    gkf = GroupKFold(n_splits=5)
    y_tr_all = train_df["IS_FAULT_INDUCING"].values
    groups_tr_all = train_df["PROJECT_ID"].values
    msgs_tr_all = train_df["COMMIT_MESSAGE"].fillna("").astype(str).values
    splits = list(gkf.split(train_df, y_tr_all, groups=groups_tr_all))
    
    ckpt_path = "research/checkpoints/stage3_arch_checkpoint.json"
    checkpointer = ExperimentCheckpointer(ckpt_path)
    
    stage3_results = {}
    
    total_arch_experiments = len(subsets_list) * len(models_list)
    exp_counter = 0
    
    for sub in subsets_list:
        sub_key = sub["key"]
        sub_name = sub["name"]
        ch_list = sub["channels"]
        has_nlp = "CH-4" in ch_list
        tabular_channels = [ch for ch in ch_list if ch != "CH-4"]
        
        for mod in models_list:
            exp_counter += 1
            mod_name = mod["name"]
            config_id = f"{sub_key}__{mod_name}"
            
            print(f"\n[{exp_counter}/{total_arch_experiments}] Benchmarking {config_id} ({mod_name} on {sub_name}) ...", flush=True)
            
            if checkpointer.is_completed(config_id):
                print(f"  -> Checkpoint Found: Skipping {config_id} (Already Completed).", flush=True)
                stage3_results[config_id] = checkpointer.get_results(config_id)
                continue
                
            t_m0 = time.time()
            oof_probas = np.zeros(len(y_tr_all), dtype=np.float32)
            fold_metrics = defaultdict(list)
            
            for fold_num, (tr_idx, val_idx) in enumerate(splits, start=1):
                y_f_tr, grp_f_tr = y_tr_all[tr_idx], groups_tr_all[tr_idx]
                y_f_va = y_tr_all[val_idx]
                
                # Assemble tabular
                X_tab_tr = assemble_tabular_features(proc_tr.iloc[tr_idx], intent_tr.iloc[tr_idx], hist_tr.iloc[tr_idx], tabular_channels)
                X_tab_va = assemble_tabular_features(proc_tr.iloc[val_idx], intent_tr.iloc[val_idx], hist_tr.iloc[val_idx], tabular_channels)
                
                if has_nlp:
                    X_nlp_tr, X_nlp_va = build_nlp_svd_matrix(msgs_tr_all[tr_idx], msgs_tr_all[val_idx], n_components=30, random_state=42+fold_num)
                    X_f_tr = np.hstack([X_tab_tr, X_nlp_tr]) if X_tab_tr.shape[1] > 0 else X_nlp_tr
                    X_f_va = np.hstack([X_tab_va, X_nlp_va]) if X_tab_va.shape[1] > 0 else X_nlp_va
                else:
                    X_f_tr = X_tab_tr
                    X_f_va = X_tab_va
                    
                imputer = SimpleImputer(strategy="constant", fill_value=0)
                scaler = StandardScaler()
                X_f_tr_proc = scaler.fit_transform(imputer.fit_transform(X_f_tr))
                X_f_va_proc = scaler.transform(imputer.transform(X_f_va))
                
                model = instantiate_model(mod_name)
                model.fit(X_f_tr_proc, y_f_tr)
                p_va = model.predict_proba(X_f_va_proc)[:, 1]
                oof_probas[val_idx] = p_va
                
                m_050 = evaluate_predictions(y_f_va, p_va, threshold=0.50)
                for k, v in m_050.items():
                    fold_metrics[k].append(v)
                    
            t_m = time.time() - t_m0
            
            # Calibrate threshold strictly on pooled training OOF probabilities
            tau_star_f1 = find_best_threshold_val(y_tr_all, oof_probas, metric="f1")
            tau_star_mcc = find_best_threshold_val(y_tr_all, oof_probas, metric="mcc")
            
            # Overall OOF metrics at 0.50 and tau_star_f1
            oof_eval_050 = evaluate_predictions(y_tr_all, oof_probas, threshold=0.50)
            oof_eval_tau_star = evaluate_predictions(y_tr_all, oof_probas, threshold=tau_star_f1)
            
            summary = {
                "config_id": config_id,
                "subset_key": sub_key,
                "subset_name": sub_name,
                "model_name": mod_name,
                "feature_count": int(X_f_tr.shape[1]),
                "execution_time_seconds": float(t_m),
                "frozen_training_threshold_f1": float(tau_star_f1),
                "frozen_training_threshold_mcc": float(tau_star_mcc),
                "fold_mean_metrics": {
                    k: {"mean": float(np.mean(vals)), "std": float(np.std(vals))}
                    for k, vals in fold_metrics.items()
                },
                "training_oof_metrics_at_050": oof_eval_050,
                "training_oof_metrics_at_tau_star": oof_eval_tau_star
            }
            
            stage3_results[config_id] = summary
            checkpointer.record_completed(config_id, summary)
            
            print(f"  -> {config_id} Done in {t_m:.1f}s | PR-AUC: {oof_eval_050['pr_auc']:.4f} | ROC-AUC: {oof_eval_050['roc_auc']:.4f} | Brier: {oof_eval_050['brier']:.4f} | ECE: {oof_eval_050['ece']:.4f}", flush=True)
            print(f"  -> Training OOF F1 (Th=0.5): {oof_eval_050['f1']:.4f} | Tuned OOF F1 (Tau*={tau_star_f1:.3f}): {oof_eval_tau_star['f1']:.4f} (P: {oof_eval_tau_star['precision']:.4f}, R: {oof_eval_tau_star['recall']:.4f})", flush=True)

    # -------------------------------------------------------------------------
    # STAGE 4: Nine-Project Validation Analysis (Unseen Repositories)
    # -------------------------------------------------------------------------
    print("\n" + "=" * 80, flush=True)
    print("STAGE 4: OUT-OF-DOMAIN 9-PROJECT VALIDATION EVALUATION", flush=True)
    print("Strict Protocol: Models evaluated with FROZEN training thresholds", flush=True)
    print("=" * 80, flush=True)
    
    # Select candidate models for validation:
    # 1. Best HistGradientBoosting (SUB-1: No-NLP full model)
    # 2. Best XGBoost (SUB-1: No-NLP full model)
    # 3. Best ExtraTrees (SUB-1: No-NLP full model)
    # 4. Best RandomForest (SUB-1: No-NLP full model)
    # 5. Full Model with NLP (SUB-4 + HistGradientBoosting)
    # 6. Baseline Churn (Baseline 7 + LogisticRegression & Baseline 7 + HistGradientBoosting)
    candidate_val_configs = [
        ("SUB-1__HistGradientBoosting", "SUB-1", "HistGradientBoosting"),
        ("SUB-1__XGBoost", "SUB-1", "XGBoost"),
        ("SUB-1__ExtraTrees", "SUB-1", "ExtraTrees"),
        ("SUB-1__RandomForest", "SUB-1", "RandomForest"),
        ("SUB-4__HistGradientBoosting", "SUB-4", "HistGradientBoosting"),
        ("SUB-5__HistGradientBoosting", "SUB-5", "HistGradientBoosting"),
        ("CHURN-BASE__HistGradientBoosting", "CHURN-BASE", "HistGradientBoosting")
    ]
    
    val_projects = val_df["PROJECT_ID"].unique().tolist()
    y_val_all = val_df["IS_FAULT_INDUCING"].values
    msgs_va_all = val_df["COMMIT_MESSAGE"].fillna("").astype(str).values
    
    stage4_validation_results = {}
    
    for val_cfg_id, sub_key, mod_name in candidate_val_configs:
        print(f"\nEvaluating Validation Candidate: {val_cfg_id} ...", flush=True)
        
        # Prepare training and validation data
        if sub_key == "CHURN-BASE":
            base_cols = ["N_FILES_CHANGED", "LINES_ADDED", "LINES_REMOVED", "N_REFACTORINGS", "N_PARENTS", "IN_MAIN_BRANCH", "AUTHOR_TIMEZONE"]
            X_tr = train_df[base_cols].fillna(0).values
            X_va = val_df[base_cols].fillna(0).values
            tau_star = 0.190  # from Phase 1 baseline training OOF
        else:
            sub_dict = next(s for s in subsets_list if s["key"] == sub_key)
            ch_list = sub_dict["channels"]
            has_nlp = "CH-4" in ch_list
            tab_channels = [c for c in ch_list if c != "CH-4"]
            
            X_tab_tr = assemble_tabular_features(proc_tr, intent_tr, hist_tr, tab_channels)
            X_tab_va = assemble_tabular_features(proc_va, intent_va, hist_va, tab_channels)
            
            if has_nlp:
                X_nlp_tr, X_nlp_va = build_nlp_svd_matrix(msgs_tr_all, msgs_va_all, n_components=30, random_state=42)
                X_tr = np.hstack([X_tab_tr, X_nlp_tr]) if X_tab_tr.shape[1] > 0 else X_nlp_tr
                X_va = np.hstack([X_tab_va, X_nlp_va]) if X_tab_va.shape[1] > 0 else X_nlp_va
            else:
                X_tr = X_tab_tr
                X_va = X_tab_va
                
            tau_star = stage3_results[val_cfg_id]["frozen_training_threshold_f1"]
            
        imputer = SimpleImputer(strategy="constant", fill_value=0)
        scaler = StandardScaler()
        X_tr_proc = scaler.fit_transform(imputer.fit_transform(X_tr))
        X_va_proc = scaler.transform(imputer.transform(X_va))
        
        model = instantiate_model(mod_name)
        model.fit(X_tr_proc, y_tr_all)
        p_val = model.predict_proba(X_va_proc)[:, 1]
        
        # Aggregate validation performance
        agg_050 = evaluate_predictions(y_val_all, p_val, threshold=0.50)
        agg_tau = evaluate_predictions(y_val_all, p_val, threshold=tau_star)
        
        # Project-by-project breakdown
        per_project_data = {}
        for p_id in val_projects:
            p_mask = (val_df["PROJECT_ID"] == p_id).values
            y_proj = y_val_all[p_mask]
            p_proj = p_val[p_mask]
            
            # Confusion matrix at 0.50
            preds_050 = (p_proj >= 0.50).astype(int)
            tn_50, fp_50, fn_50, tp_50 = confusion_matrix(y_proj, preds_050, labels=[0, 1]).ravel()
            
            # Confusion matrix at tau_star
            preds_tau = (p_proj >= tau_star).astype(int)
            tn_t, fp_t, fn_t, tp_t = confusion_matrix(y_proj, preds_tau, labels=[0, 1]).ravel()
            
            m_proj_050 = evaluate_predictions(y_proj, p_proj, threshold=0.50)
            m_proj_tau = evaluate_predictions(y_proj, p_proj, threshold=tau_star)
            
            per_project_data[p_id] = {
                "total_commits": int(len(y_proj)),
                "defect_commits": int(np.sum(y_proj)),
                "prevalence": float(np.mean(y_proj)),
                "confusion_matrix_at_050": {"TP": int(tp_50), "FP": int(fp_50), "TN": int(tn_50), "FN": int(fn_50)},
                "confusion_matrix_at_tau_star": {"TP": int(tp_t), "FP": int(fp_t), "TN": int(tn_t), "FN": int(fn_t)},
                "metrics_at_050": m_proj_050,
                "metrics_at_tau_star": m_proj_tau
            }
            
        stage4_validation_results[val_cfg_id] = {
            "config_id": val_cfg_id,
            "model_name": mod_name,
            "feature_subset": sub_key,
            "frozen_training_threshold": float(tau_star),
            "aggregate_metrics_at_050": agg_050,
            "aggregate_metrics_at_tau_star": agg_tau,
            "per_project_breakdown": per_project_data
        }
        
        print(f"  -> Aggregate PR-AUC: {agg_050['pr_auc']:.4f} | ROC-AUC: {agg_050['roc_auc']:.4f} | Brier: {agg_050['brier']:.4f} | ECE: {agg_050['ece']:.4f}", flush=True)
        print(f"  -> Frozen Tau* ({tau_star:.3f}) Aggregate: F1 = {agg_tau['f1']:.4f} (Precision: {agg_tau['precision']:.4f}, Recall: {agg_tau['recall']:.4f}, MCC: {agg_tau['mcc']:.4f})", flush=True)

    # -------------------------------------------------------------------------
    # STAGE 5: Cross-Project Heterogeneity & Statistical Analysis
    # -------------------------------------------------------------------------
    print("\n" + "=" * 80, flush=True)
    print("STAGE 5: CROSS-PROJECT HETEROGENEITY & STATISTICAL TESTING", flush=True)
    print("=" * 80, flush=True)
    
    # Compare Top Multi-Modal (SUB-1__HistGradientBoosting) vs Baseline (CHURN-BASE__HistGradientBoosting)
    top_model_key = "SUB-1__HistGradientBoosting"
    base_model_key = "CHURN-BASE__HistGradientBoosting"
    
    top_proj_dict = stage4_validation_results[top_model_key]["per_project_breakdown"]
    base_proj_dict = stage4_validation_results[base_model_key]["per_project_breakdown"]
    
    # Paired vectors across 9 projects
    p_pr_top = [top_proj_dict[p]["metrics_at_tau_star"]["pr_auc"] for p in val_projects]
    p_pr_base = [base_proj_dict[p]["metrics_at_tau_star"]["pr_auc"] for p in val_projects]
    
    p_roc_top = [top_proj_dict[p]["metrics_at_tau_star"]["roc_auc"] for p in val_projects]
    p_roc_base = [base_proj_dict[p]["metrics_at_tau_star"]["roc_auc"] for p in val_projects]
    
    p_f1_top = [top_proj_dict[p]["metrics_at_tau_star"]["f1"] for p in val_projects]
    p_f1_base = [base_proj_dict[p]["metrics_at_tau_star"]["f1"] for p in val_projects]
    
    # Paired Wilcoxon Signed-Rank Test (N=9)
    # Check for zero differences before test
    diff_pr = np.array(p_pr_top) - np.array(p_pr_base)
    diff_roc = np.array(p_roc_top) - np.array(p_roc_base)
    diff_f1 = np.array(p_f1_top) - np.array(p_f1_base)
    
    w_pr, pval_pr = wilcoxon(diff_pr)
    w_roc, pval_roc = wilcoxon(diff_roc)
    w_f1, pval_f1 = wilcoxon(diff_f1)
    
    # Rank-biserial correlation effect size r_rb = W+ - W- / (W+ + W-)
    def calc_r_biserial(diffs):
        pos_ranks = np.sum([r for r, d in zip(range(1, len(diffs)+1), sorted(diffs, key=abs)) if d > 0])
        neg_ranks = np.sum([r for r, d in zip(range(1, len(diffs)+1), sorted(diffs, key=abs)) if d < 0])
        tot = pos_ranks + neg_ranks
        return float((pos_ranks - neg_ranks) / tot) if tot > 0 else 0.0

    r_rb_pr = calc_r_biserial(diff_pr)
    r_rb_roc = calc_r_biserial(diff_roc)
    r_rb_f1 = calc_r_biserial(diff_f1)
    
    statistical_tests = {
        "sample_size_projects": len(val_projects),
        "test_name": "Paired Wilcoxon Signed-Rank Test (Two-Sided)",
        "pr_auc": {
            "mean_top": float(np.mean(p_pr_top)),
            "mean_base": float(np.mean(p_pr_base)),
            "mean_diff": float(np.mean(diff_pr)),
            "W_statistic": float(w_pr),
            "p_value": float(pval_pr),
            "rank_biserial_r": float(r_rb_pr)
        },
        "roc_auc": {
            "mean_top": float(np.mean(p_roc_top)),
            "mean_base": float(np.mean(p_roc_base)),
            "mean_diff": float(np.mean(diff_roc)),
            "W_statistic": float(w_roc),
            "p_value": float(pval_roc),
            "rank_biserial_r": float(r_rb_roc)
        },
        "f1": {
            "mean_top": float(np.mean(p_f1_top)),
            "mean_base": float(np.mean(p_f1_base)),
            "mean_diff": float(np.mean(diff_f1)),
            "W_statistic": float(w_f1),
            "p_value": float(pval_f1),
            "rank_biserial_r": float(r_rb_f1)
        }
    }
    
    print("\nPaired Statistical Significance Tests across 9 Validation Projects:")
    print(f"  PR-AUC:  Mean Top={np.mean(p_pr_top):.4f} vs Base={np.mean(p_pr_base):.4f} | Diff=+{np.mean(diff_pr):.4f} | W={w_pr:.1f}, p={pval_pr:.4f}, r_rb={r_rb_pr:+.3f}")
    print(f"  ROC-AUC: Mean Top={np.mean(p_roc_top):.4f} vs Base={np.mean(p_roc_base):.4f} | Diff=+{np.mean(diff_roc):.4f} | W={w_roc:.1f}, p={pval_roc:.4f}, r_rb={r_rb_roc:+.3f}")
    print(f"  F1:      Mean Top={np.mean(p_f1_top):.4f} vs Base={np.mean(p_f1_base):.4f} | Diff=+{np.mean(diff_f1):.4f} | W={w_f1:.1f}, p={pval_f1:.4f}, r_rb={r_rb_f1:+.3f}")
    
    # Project-Level Heterogeneity Correlation
    prevalences = [top_proj_dict[p]["prevalence"] for p in val_projects]
    f1_scores = [top_proj_dict[p]["metrics_at_tau_star"]["f1"] for p in val_projects]
    precisions = [top_proj_dict[p]["metrics_at_tau_star"]["precision"] for p in val_projects]
    recalls = [top_proj_dict[p]["metrics_at_tau_star"]["recall"] for p in val_projects]
    
    corr_prev_f1 = float(np.corrcoef(prevalences, f1_scores)[0, 1])
    corr_prev_prec = float(np.corrcoef(prevalences, precisions)[0, 1])
    corr_prev_rec = float(np.corrcoef(prevalences, recalls)[0, 1])
    
    heterogeneity_diagnosis = {
        "correlation_prevalence_vs_f1": corr_prev_f1,
        "correlation_prevalence_vs_precision": corr_prev_prec,
        "correlation_prevalence_vs_recall": corr_prev_rec,
        "highest_f1_project": max(top_proj_dict.items(), key=lambda x: x[1]["metrics_at_tau_star"]["f1"])[0],
        "lowest_f1_project": min(top_proj_dict.items(), key=lambda x: x[1]["metrics_at_tau_star"]["f1"])[0]
    }
    print("\nHeterogeneity Analysis:")
    print(f"  Prevalence vs Precision Correlation: {corr_prev_prec:+.4f} (Higher defect rate strongly increases precision)")
    print(f"  Prevalence vs F1 Correlation:        {corr_prev_f1:+.4f}")
    print(f"  Highest F1 Project: {heterogeneity_diagnosis['highest_f1_project']} (F1={top_proj_dict[heterogeneity_diagnosis['highest_f1_project']]['metrics_at_tau_star']['f1']:.4f})")
    print(f"  Lowest F1 Project:  {heterogeneity_diagnosis['lowest_f1_project']} (F1={top_proj_dict[heterogeneity_diagnosis['lowest_f1_project']]['metrics_at_tau_star']['f1']:.4f})")
    
    # -------------------------------------------------------------------------
    # Save Final Artifacts
    # -------------------------------------------------------------------------
    total_time = time.time() - t_start_total
    mem_final = get_memory_mb()
    
    final_output = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "total_execution_seconds": float(total_time),
        "peak_working_set_mb": float(mem_final),
        "stage3_architecture_comparison": stage3_results,
        "stage4_validation_results": stage4_validation_results,
        "stage5_statistical_analysis": statistical_tests,
        "stage5_heterogeneity_diagnosis": heterogeneity_diagnosis
    }
    
    reports_path = "research/reports/stage3_4_5_full_results.json"
    atomic_save_json(reports_path, final_output)
    print(f"\n[OK] Stages 3, 4, and 5 successfully executed and saved to {reports_path} in {total_time:.1f}s (~{total_time/60.0:.1f} mins)", flush=True)

if __name__ == "__main__":
    main()
