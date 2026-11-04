"""
research.run_phase1_experiments
===============================
Execution script for Phase 1 (Exp 1–4):
1. Exp 1: Baseline Verification, Chronological Ordering, and Dataset Integrity Audit.
2. Exp 2: Commit Message Intent and Lexical Complexity Feature Extraction.
3. Exp 3: Controlled 6-Configuration NLP Comparison strictly within 5-Fold GroupKFold.
4. Exp 4: Historical & Process Feature Generation, Multi-Modal Model, and Validation Evaluation.

Enforces:
- 100% fold-level preprocessing isolation (TF-IDF, SVD, scalers, imputers fitted per fold).
- Zero test-set peeking (6 test projects quarantined).
- Temporal causality and conservative same-timestamp tie handling.
"""

import sys
import os
sys.path.insert(0, os.path.abspath("."))
os.environ["LOKY_MAX_CPU_COUNT"] = "4"
os.environ["PYTHONUNBUFFERED"] = "1"
import time
import json
# Process memory handled via ctypes
import numpy as np
import pandas as pd
from collections import defaultdict

from sklearn.model_selection import GroupKFold
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.metrics import confusion_matrix

from research.features.intent_nlp import extract_intent_features, INTENT_FEATURE_NAMES
from research.features.process_features import extract_process_features, PROCESS_FEATURE_NAMES
from research.features.historical_features import compute_strict_historical_features, HISTORICAL_FEATURE_NAMES
from research.features.fold_pipeline import FoldFeaturePipeline
from research.evaluation.metrics import evaluate_predictions, find_best_threshold_val

# Resource measurement helper
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

def main():
    print("=" * 80)
    print("PHASE 1 EXECUTION: EXPERIMENTS 1 TO 4 (DEFECT PREDICTION BENCHMARK)")
    print("=" * 80)
    
    start_total_time = time.time()
    initial_mem = get_memory_mb()
    
    # Paths
    train_path = "dataset-extracted/TD dataset/train.csv"
    val_path = "dataset-extracted/TD dataset/validation.csv"
    test_path = "dataset-extracted/TD dataset/test.csv"
    out_dir = "research/reports"
    os.makedirs(out_dir, exist_ok=True)
    
    # -------------------------------------------------------------------------
    # EXP 1: Baseline Verification & Dataset Integrity Audit
    # -------------------------------------------------------------------------
    print("\n--- [EXP 1] Loading Datasets and Auditing Integrity ---")
    train_df = pd.read_csv(train_path)
    val_df = pd.read_csv(val_path)
    
    # Verify test set exists and quarantine it
    assert os.path.exists(test_path), f"Test path not found: {test_path}"
    test_projects = [
        "org.apache:archiva", "org.apache:collections", "org.apache:commons-cli",
        "org.apache:dbcp", "org.apache:santuario", "org.apache:validator"
    ]
    print(f"Quarantined 6 Test Projects: {test_projects}")
    print("Strict Protocol Active: ZERO interaction with test.csv during Phase 1.")
    
    train_projects = train_df["PROJECT_ID"].unique().tolist()
    val_projects = val_df["PROJECT_ID"].unique().tolist()
    
    # Check disjointness
    assert set(train_projects).isdisjoint(set(val_projects)), "Train and Val projects overlap!"
    assert set(train_projects).isdisjoint(set(test_projects)), "Train and Test projects overlap!"
    assert set(val_projects).isdisjoint(set(test_projects)), "Val and Test projects overlap!"
    
    train_defects = train_df["IS_FAULT_INDUCING"].sum()
    val_defects = val_df["IS_FAULT_INDUCING"].sum()
    
    integrity_report = {
        "train_rows": len(train_df),
        "train_projects_count": len(train_projects),
        "train_projects": train_projects,
        "train_defects": int(train_defects),
        "train_defect_prevalence": float(train_defects / len(train_df)),
        "validation_rows": len(val_df),
        "validation_projects_count": len(val_projects),
        "validation_projects": val_projects,
        "validation_defects": int(val_defects),
        "validation_defect_prevalence": float(val_defects / len(val_df)),
        "quarantined_test_projects_count": len(test_projects),
        "quarantined_test_projects": test_projects,
        "cross_split_overlap": 0
    }
    print(f"Train Set: {len(train_df):,} rows across {len(train_projects)} projects | Defect Rate: {train_defects/len(train_df):.2%}")
    print(f"Validation Set: {len(val_df):,} rows across {len(val_projects)} projects | Defect Rate: {val_defects/len(val_df):.2%}")
    
    # -------------------------------------------------------------------------
    # EXP 2: Intent & Lexical Feature Extraction
    # -------------------------------------------------------------------------
    print("\n--- [EXP 2] Extracting Intent & Lexical NLP Features ---")
    t0 = time.time()
    train_intent = extract_intent_features(train_df["COMMIT_MESSAGE"])
    val_intent = extract_intent_features(val_df["COMMIT_MESSAGE"])
    t_intent = time.time() - t0
    print(f"Extracted 11 Intent Features for Train & Val in {t_intent:.2f}s")
    
    intent_stats = train_intent.describe().T[["mean", "std", "min", "max"]].to_dict(orient="index")
    
    # -------------------------------------------------------------------------
    # EXP 4 (Part A): Process & Historical Feature Extraction
    # -------------------------------------------------------------------------
    print("\n--- [EXP 4 - Part A] Extracting Process & Strict Chronological Historical Features ---")
    t0 = time.time()
    train_proc = extract_process_features(train_df, intent_df=train_intent)
    val_proc = extract_process_features(val_df, intent_df=val_intent)
    t_proc = time.time() - t0
    print(f"Extracted 23 Process Features in {t_proc:.2f}s")
    
    t0 = time.time()
    train_hist = compute_strict_historical_features(train_df)
    val_hist = compute_strict_historical_features(val_df)
    t_hist = time.time() - t0
    print(f"Extracted 7 Strict Historical Features in {t_hist:.2f}s")
    
    proc_stats = train_proc.describe().T[["mean", "std", "min", "max"]].to_dict(orient="index")
    hist_stats = train_hist.describe().T[["mean", "std", "min", "max"]].to_dict(orient="index")
    
    # Check for NaNs
    assert not train_intent.isnull().any().any(), "NaN found in train_intent!"
    assert not train_proc.isnull().any().any(), "NaN found in train_proc!"
    assert not train_hist.isnull().any().any(), "NaN found in train_hist!"
    
    # -------------------------------------------------------------------------
    # EXP 3: Controlled 6-Way NLP Comparison inside 5-Fold GroupKFold
    # -------------------------------------------------------------------------
    print("\n--- [EXP 3] Controlled NLP Comparison strictly inside 5-Fold GroupKFold ---")
    gkf = GroupKFold(n_splits=5)
    groups = train_df["PROJECT_ID"].values
    y_train_all = train_df["IS_FAULT_INDUCING"].values
    messages_train = train_df["COMMIT_MESSAGE"].fillna("").astype(str).values
    
    # Base features for Churn Baseline (from previous optimization study)
    base_cols = [
        "N_FILES_CHANGED", "LINES_ADDED", "LINES_REMOVED",
        "N_REFACTORINGS", "N_PARENTS", "IN_MAIN_BRANCH", "AUTHOR_TIMEZONE"
    ]
    raw_churn_train = train_df[base_cols].fillna(0).values
    raw_churn_val = val_df[base_cols].fillna(0).values
    
    configurations = [
        ("0. Churn Baseline (Base 7)", "churn_baseline", raw_churn_train),
        ("1. Word TF-IDF without SVD", "word_tfidf_no_svd", None),
        ("2. Character TF-IDF without SVD", "char_tfidf_no_svd", None),
        ("3. Word + Char TF-IDF without SVD", "word_char_tfidf_no_svd", None),
        ("4. Word + Char TF-IDF + SVD (50)", "word_char_tfidf_svd", None),
        ("5. NLP (SVD) + Intent Features", "nlp_plus_intent", None),
        ("6. Churn + NLP + Historical (Full Fused)", "churn_plus_nlp_plus_historical", None)
    ]
    
    cv_comparison_results = {}
    
    for label, cfg_name, extra_data in configurations:
        print(f"\nEvaluating: {label} ...")
        fold_metrics = defaultdict(list)
        t_cfg_start = time.time()
        
        for fold, (tr_idx, val_idx) in enumerate(gkf.split(train_df, groups=groups)):
            # Instantiate fold-isolated pipeline
            pipe = FoldFeaturePipeline(config_name=cfg_name, random_state=42)
            
            y_tr, y_va = y_train_all[tr_idx], y_train_all[val_idx]
            msg_tr, msg_va = messages_train[tr_idx], messages_train[val_idx]
            
            # Prepare fold data
            if cfg_name == "churn_baseline":
                X_tr = pipe.fit_transform(messages=msg_tr, process_features=extra_data[tr_idx])
                X_va = pipe.transform(messages=msg_va, process_features=extra_data[val_idx])
            elif cfg_name in ["word_tfidf_no_svd", "char_tfidf_no_svd", "word_char_tfidf_no_svd", "word_char_tfidf_svd"]:
                X_tr = pipe.fit_transform(messages=msg_tr)
                X_va = pipe.transform(messages=msg_va)
            elif cfg_name == "nlp_plus_intent":
                intent_tr = train_intent.iloc[tr_idx].values
                intent_va = train_intent.iloc[val_idx].values
                X_tr = pipe.fit_transform(messages=msg_tr, intent_features=intent_tr)
                X_va = pipe.transform(messages=msg_va, intent_features=intent_va)
            elif cfg_name == "churn_plus_nlp_plus_historical":
                proc_tr, proc_va = train_proc.iloc[tr_idx].values, train_proc.iloc[val_idx].values
                intent_tr, intent_va = train_intent.iloc[tr_idx].values, train_intent.iloc[val_idx].values
                hist_tr, hist_va = train_hist.iloc[tr_idx].values, train_hist.iloc[val_idx].values
                X_tr = pipe.fit_transform(
                    messages=msg_tr,
                    intent_features=intent_tr,
                    process_features=proc_tr,
                    historical_features=hist_tr
                )
                X_va = pipe.transform(
                    messages=msg_va,
                    intent_features=intent_va,
                    process_features=proc_va,
                    historical_features=hist_va
                )
                
            # Fit unified Logistic Regression estimator
            clf = LogisticRegression(C=1.0, max_iter=250, random_state=42)
            clf.fit(X_tr, y_tr)
            proba_va = clf.predict_proba(X_va)[:, 1]
            
            # Evaluate at standard threshold 0.50
            m_050 = evaluate_predictions(y_va, proba_va, threshold=0.50)
            
            # Evaluate at fold-tuned threshold
            best_th = find_best_threshold_val(y_va, proba_va, metric="f1")
            m_tuned = evaluate_predictions(y_va, proba_va, threshold=best_th)
            
            fold_metrics["f1_050"].append(m_050["f1"])
            fold_metrics["precision_050"].append(m_050["precision"])
            fold_metrics["recall_050"].append(m_050["recall"])
            fold_metrics["accuracy_050"].append(m_050["accuracy"])
            fold_metrics["pr_auc"].append(m_050["pr_auc"])
            fold_metrics["roc_auc"].append(m_050["roc_auc"])
            fold_metrics["mcc_050"].append(m_050["mcc"])
            fold_metrics["f1_tuned"].append(m_tuned["f1"])
            fold_metrics["precision_tuned"].append(m_tuned["precision"])
            fold_metrics["recall_tuned"].append(m_tuned["recall"])
            fold_metrics["tuned_threshold"].append(best_th)
            
        t_cfg = time.time() - t_cfg_start
        
        # Aggregate across folds
        summary = {
            metric: {
                "mean": float(np.mean(vals)),
                "std": float(np.std(vals))
            } for metric, vals in fold_metrics.items()
        }
        summary["execution_time_seconds"] = float(t_cfg)
        cv_comparison_results[label] = summary
        
        print(f"  -> PR-AUC: {summary['pr_auc']['mean']:.4f} ± {summary['pr_auc']['std']:.4f}")
        print(f"  -> ROC-AUC: {summary['roc_auc']['mean']:.4f} ± {summary['roc_auc']['std']:.4f}")
        print(f"  -> F1 (Th=0.5): {summary['f1_050']['mean']:.4f} ± {summary['f1_050']['std']:.4f} (P: {summary['precision_050']['mean']:.4f}, R: {summary['recall_050']['mean']:.4f})")
        print(f"  -> F1 (Tuned): {summary['f1_tuned']['mean']:.4f} ± {summary['f1_tuned']['std']:.4f} (Mean Th: {summary['tuned_threshold']['mean']:.3f})")
        
    # -------------------------------------------------------------------------
    # EXP 4 (Part B): Gradient Boosting on Full Multi-Modal Representation
    # -------------------------------------------------------------------------
    print("\n--- [EXP 4 - Part B] Non-Linear Tree Ensemble on Multi-Modal Representation ---")
    gb_metrics = defaultdict(list)
    t0 = time.time()
    
    for fold, (tr_idx, val_idx) in enumerate(gkf.split(train_df, groups=groups)):
        pipe = FoldFeaturePipeline(config_name="churn_plus_nlp_plus_historical", random_state=42)
        
        y_tr, y_va = y_train_all[tr_idx], y_train_all[val_idx]
        msg_tr, msg_va = messages_train[tr_idx], messages_train[val_idx]
        proc_tr, proc_va = train_proc.iloc[tr_idx].values, train_proc.iloc[val_idx].values
        intent_tr, intent_va = train_intent.iloc[tr_idx].values, train_intent.iloc[val_idx].values
        hist_tr, hist_va = train_hist.iloc[tr_idx].values, train_hist.iloc[val_idx].values
        
        X_tr = pipe.fit_transform(
            messages=msg_tr,
            intent_features=intent_tr,
            process_features=proc_tr,
            historical_features=hist_tr
        )
        X_va = pipe.transform(
            messages=msg_va,
            intent_features=intent_va,
            process_features=proc_va,
            historical_features=hist_va
        )
        
        hgb = HistGradientBoostingClassifier(
            max_iter=100,
            learning_rate=0.08,
            max_leaf_nodes=31,
            random_state=42
        )
        hgb.fit(X_tr, y_tr)
        proba_va = hgb.predict_proba(X_va)[:, 1]
        
        m_050 = evaluate_predictions(y_va, proba_va, threshold=0.50)
        best_th = find_best_threshold_val(y_va, proba_va, metric="f1")
        m_tuned = evaluate_predictions(y_va, proba_va, threshold=best_th)
        
        gb_metrics["f1_050"].append(m_050["f1"])
        gb_metrics["precision_050"].append(m_050["precision"])
        gb_metrics["recall_050"].append(m_050["recall"])
        gb_metrics["accuracy_050"].append(m_050["accuracy"])
        gb_metrics["pr_auc"].append(m_050["pr_auc"])
        gb_metrics["roc_auc"].append(m_050["roc_auc"])
        gb_metrics["mcc_050"].append(m_050["mcc"])
        gb_metrics["f1_tuned"].append(m_tuned["f1"])
        gb_metrics["precision_tuned"].append(m_tuned["precision"])
        gb_metrics["recall_tuned"].append(m_tuned["recall"])
        gb_metrics["tuned_threshold"].append(best_th)
        
    t_gb = time.time() - t0
    gb_summary = {
        metric: {
            "mean": float(np.mean(vals)),
            "std": float(np.std(vals))
        } for metric, vals in gb_metrics.items()
    }
    gb_summary["execution_time_seconds"] = float(t_gb)
    cv_comparison_results["HistGradientBoosting (Full Multi-Modal)"] = gb_summary
    
    print(f"HistGradientBoosting Multi-Modal Results:")
    print(f"  -> PR-AUC: {gb_summary['pr_auc']['mean']:.4f} ± {gb_summary['pr_auc']['std']:.4f}")
    print(f"  -> ROC-AUC: {gb_summary['roc_auc']['mean']:.4f} ± {gb_summary['roc_auc']['std']:.4f}")
    print(f"  -> F1 (Th=0.5): {gb_summary['f1_050']['mean']:.4f} ± {gb_summary['f1_050']['std']:.4f} (P: {gb_summary['precision_050']['mean']:.4f}, R: {gb_summary['recall_050']['mean']:.4f})")
    print(f"  -> F1 (Tuned): {gb_summary['f1_tuned']['mean']:.4f} ± {gb_summary['f1_tuned']['std']:.4f} (Mean Th: {gb_summary['tuned_threshold']['mean']:.3f})")
    
    # -------------------------------------------------------------------------
    # EXP 4 (Part C): Unseen 9-Project Validation Set Evaluation
    # -------------------------------------------------------------------------
    print("\n--- [EXP 4 - Part C] Training on 16 Train Projects, Evaluating on 9 Validation Projects ---")
    val_pipe = FoldFeaturePipeline(config_name="churn_plus_nlp_plus_historical", random_state=42)
    X_train_full = val_pipe.fit_transform(
        messages=messages_train,
        intent_features=train_intent.values,
        process_features=train_proc.values,
        historical_features=train_hist.values
    )
    
    messages_val = val_df["COMMIT_MESSAGE"].fillna("").astype(str).values
    X_val_full = val_pipe.transform(
        messages=messages_val,
        intent_features=val_intent.values,
        process_features=val_proc.values,
        historical_features=val_hist.values
    )
    y_val_all = val_df["IS_FAULT_INDUCING"].values
    
    # Train full model on all 16 projects
    hgb_full = HistGradientBoostingClassifier(
        max_iter=100, learning_rate=0.08, max_leaf_nodes=31, random_state=42
    )
    hgb_full.fit(X_train_full, y_train_all)
    val_probas = hgb_full.predict_proba(X_val_full)[:, 1]
    
    val_m_050 = evaluate_predictions(y_val_all, val_probas, threshold=0.50)
    best_val_th = find_best_threshold_val(y_val_all, val_probas, metric="f1")
    val_m_tuned = evaluate_predictions(y_val_all, val_probas, threshold=best_val_th)
    
    # Baseline on validation set for comparison
    base_pipe = FoldFeaturePipeline(config_name="churn_baseline", random_state=42)
    X_tr_base = base_pipe.fit_transform(messages=messages_train, process_features=raw_churn_train)
    X_val_base = base_pipe.transform(messages=messages_val, process_features=raw_churn_val)
    hgb_base = HistGradientBoostingClassifier(max_iter=100, random_state=42)
    hgb_base.fit(X_tr_base, y_train_all)
    val_base_probas = hgb_base.predict_proba(X_val_base)[:, 1]
    val_base_m_050 = evaluate_predictions(y_val_all, val_base_probas, threshold=0.50)
    best_base_th = find_best_threshold_val(y_val_all, val_base_probas, metric="f1")
    val_base_m_tuned = evaluate_predictions(y_val_all, val_base_probas, threshold=best_base_th)
    
    validation_eval_summary = {
        "baseline_churn_model": {
            "metrics_at_050": val_base_m_050,
            "metrics_tuned": val_base_m_tuned,
            "best_threshold": best_base_th
        },
        "multimodal_model": {
            "metrics_at_050": val_m_050,
            "metrics_tuned": val_m_tuned,
            "best_threshold": best_val_th
        }
    }
    
    print("\nValidation Set (9 Unseen Projects) Performance Comparison:")
    print(f"  [Baseline Churn 7-Feat] PR-AUC: {val_base_m_050['pr_auc']:.4f} | ROC-AUC: {val_base_m_050['roc_auc']:.4f} | F1 (Th=0.50): {val_base_m_050['f1']:.4f} | F1 (Th={best_base_th:.3f}): {val_base_m_tuned['f1']:.4f}")
    print(f"  [Multi-Modal Fused]     PR-AUC: {val_m_050['pr_auc']:.4f} | ROC-AUC: {val_m_050['roc_auc']:.4f} | F1 (Th=0.50): {val_m_050['f1']:.4f} | F1 (Th={best_val_th:.3f}): {val_m_tuned['f1']:.4f}")
    
    # -------------------------------------------------------------------------
    # Resource Usage & Final Report Serialization
    # -------------------------------------------------------------------------
    total_time = time.time() - start_total_time
    final_mem = get_memory_mb()
    
    resource_usage = {
        "total_wall_clock_seconds": float(total_time),
        "peak_working_set_mb": float(final_mem),
        "feature_extraction_seconds": {
            "intent_nlp": float(t_intent),
            "process": float(t_proc),
            "historical": float(t_hist)
        }
    }
    print(f"\nResource Usage: Total Execution Time = {total_time:.2f}s | Process RAM = {final_mem:.1f} MB")
    
    final_output = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "integrity_report": integrity_report,
        "feature_statistics": {
            "intent_features": intent_stats,
            "process_features": proc_stats,
            "historical_features": hist_stats
        },
        "cv_comparison_results": cv_comparison_results,
        "validation_eval_summary": validation_eval_summary,
        "resource_usage": resource_usage
    }
    
    json_path = os.path.join(out_dir, "phase1_experiment_results.json")
    with open(json_path, "w") as f:
        json.dump(final_output, f, indent=2)
    print(f"\n[OK] Phase 1 experimental results saved to {json_path}")

if __name__ == "__main__":
    main()
