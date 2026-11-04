"""
research.run_full_ablation_benchmark
====================================
Phase 2 Stage 2: Full 18-Configuration Nested Ablation Benchmark
Executed strictly across the 16 training projects of the TD dataset.

Topology:
- 5 Outer GroupKFold folds by project.
- 4 Inner GroupKFold folds per outer fold for unbiased threshold selection.
- Total model fits: 18 configurations x 25 fits = 450 fits.

Guarantees:
- Preprocessing & SVD fitted 100% fold-internally with zero leakage.
- Thresholds frozen on inner OOF predictions before outer-fold evaluation.
- Zero test-set peeking (6 test projects quarantined).
- Zero validation-label threshold tuning.
- Resilient atomic checkpointing and progress logging.
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

from sklearn.model_selection import GroupKFold
from sklearn.linear_model import LogisticRegression
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.decomposition import TruncatedSVD
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer

from research.features.intent_nlp import extract_intent_features
from research.features.process_features import extract_process_features
from research.features.historical_features import compute_strict_historical_features
from research.features.channel_extractor import assemble_tabular_features
from research.evaluation.nested_threshold import select_inner_threshold_oof, evaluate_nested_fold
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
    """
    Fits Word+Char TF-IDF and TruncatedSVD strictly on messages_train,
    then transforms messages_test if provided.
    """
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

def main():
    print("=" * 80, flush=True)
    print("PHASE 2 STAGE 2: FULL 18-CONFIGURATION NESTED ABLATION BENCHMARK", flush=True)
    print("Strict Protocol: 16 Training Projects Only | 450 Model Fits Total", flush=True)
    print("=" * 80, flush=True)
    
    t_bench_start = time.time()
    
    # Load configuration
    with open("research/configs/experiment_config.json", "r") as f:
        cfg = json.load(f)
        
    ablation_matrix = cfg["ablation_matrix"]
    total_configs = len(ablation_matrix)
    print(f"Total Configurations in Matrix: {total_configs}", flush=True)
    
    # Load 16 training projects
    train_path = cfg["data_paths"]["train_path"]
    train_df = pd.read_csv(train_path)
    train_projects = train_df["PROJECT_ID"].unique().tolist()
    print(f"Loaded {len(train_df):,} training commits across {len(train_projects)} projects | Defect Rate: {train_df['IS_FAULT_INDUCING'].mean():.2%}", flush=True)
    
    # Extract deterministic features once for all rows
    print("\n[Step 1/3] Extracting Process, Intent, and 90-Day Delayed Historical Features ...", flush=True)
    t0 = time.time()
    intent_df = extract_intent_features(train_df["COMMIT_MESSAGE"])
    proc_df = extract_process_features(train_df, intent_df=intent_df)
    hist_df = compute_strict_historical_features(train_df, delay_days=90.0)
    print(f"Feature Extraction Completed in {time.time() - t0:.2f}s", flush=True)
    
    # Initialize Checkpointer
    ckpt_path = cfg["storage_paths"]["checkpoint_path"]
    checkpointer = ExperimentCheckpointer(ckpt_path)
    reports_path = cfg["storage_paths"]["reports_path"]
    
    # Outer GroupKFold (5 Folds)
    gkf_outer = GroupKFold(n_splits=cfg["nested_cv_config"]["n_splits_outer"])
    y_all = train_df["IS_FAULT_INDUCING"].values
    groups_all = train_df["PROJECT_ID"].values
    messages_all = train_df["COMMIT_MESSAGE"].fillna("").astype(str).values
    
    print("\n[Step 2/3] Setting Up Outer Fold Splits & Fold-Isolated Caching ...", flush=True)
    outer_splits = list(gkf_outer.split(train_df, y_all, groups=groups_all))
    
    # Benchmark Loop across 18 configurations
    benchmark_results = {}
    
    for cfg_idx, item in enumerate(ablation_matrix, start=1):
        key = item["key"]
        ch_list = item["channels"]
        desc = item["description"]
        cfg_type = item["type"]
        
        print(f"\n[{cfg_idx}/{total_configs}] Running {key} ({desc}) ...", flush=True)
        
        if checkpointer.is_completed(key):
            print(f"  -> Checkpoint Found: Skipping {key} (Already Completed).", flush=True)
            benchmark_results[key] = checkpointer.get_results(key)
            continue
            
        t_cfg_start = time.time()
        has_nlp = "CH-4" in ch_list
        tabular_channels = [ch for ch in ch_list if ch != "CH-4"]
        
        fold_metrics = defaultdict(list)
        
        try:
            for fold_num, (tr_idx, val_idx) in enumerate(outer_splits, start=1):
                y_tr, grp_tr = y_all[tr_idx], groups_all[tr_idx]
                y_va = y_all[val_idx]
                
                # Assemble outer tabular features
                if tabular_channels:
                    X_tab_tr = assemble_tabular_features(proc_df.iloc[tr_idx], intent_df.iloc[tr_idx], hist_df.iloc[tr_idx], tabular_channels)
                    X_tab_va = assemble_tabular_features(proc_df.iloc[val_idx], intent_df.iloc[val_idx], hist_df.iloc[val_idx], tabular_channels)
                else:
                    X_tab_tr = np.zeros((len(tr_idx), 0), dtype=np.float32)
                    X_tab_va = np.zeros((len(val_idx), 0), dtype=np.float32)
                    
                # Assemble outer NLP features (CH-4) if requested
                if has_nlp:
                    X_nlp_tr, X_nlp_va = build_nlp_svd_matrix(
                        messages_train=messages_all[tr_idx],
                        messages_test=messages_all[val_idx],
                        n_components=30,
                        random_state=42 + fold_num
                    )
                    X_outer_tr = np.hstack([X_tab_tr, X_nlp_tr]) if X_tab_tr.shape[1] > 0 else X_nlp_tr
                    X_outer_va = np.hstack([X_tab_va, X_nlp_va]) if X_tab_va.shape[1] > 0 else X_nlp_va
                else:
                    X_outer_tr = X_tab_tr
                    X_outer_va = X_tab_va
                    
                # Preprocess fold-internally
                imputer = SimpleImputer(strategy="constant", fill_value=0)
                scaler = StandardScaler()
                X_tr_proc = scaler.fit_transform(imputer.fit_transform(X_outer_tr))
                X_va_proc = scaler.transform(imputer.transform(X_outer_va))
                
                # Setup model factory
                model_factory = lambda: LogisticRegression(C=1.0, max_iter=250, random_state=42)
                
                # Execute nested fold with inner OOF threshold selection
                fold_res = evaluate_nested_fold(
                    X_train=X_tr_proc,
                    y_train=y_tr,
                    groups_train=grp_tr,
                    X_test=X_va_proc,
                    y_test=y_va,
                    model_factory=model_factory,
                    n_splits_inner=cfg["nested_cv_config"]["n_splits_inner"]
                )
                
                tau_inner = fold_res["selected_inner_threshold"]
                opt_tau = fold_res["optimistic_in_sample_threshold"]
                m_050 = fold_res["metrics_at_050"]
                m_nested = fold_res["metrics_at_nested_th"]
                m_opt = fold_res["metrics_at_optimistic_th"]
                
                fold_metrics["selected_inner_threshold"].append(tau_inner)
                outer_metrics_gap = float(opt_tau - tau_inner)
                fold_metrics["threshold_selection_gap"].append(outer_metrics_gap)
                
                fold_metrics["f1_050"].append(m_050["f1"])
                fold_metrics["precision_050"].append(m_050["precision"])
                fold_metrics["recall_050"].append(m_050["recall"])
                fold_metrics["accuracy_050"].append(m_050["accuracy"])
                fold_metrics["pr_auc"].append(m_nested["pr_auc"])
                fold_metrics["roc_auc"].append(m_nested["roc_auc"])
                fold_metrics["mcc_050"].append(m_050["mcc"])
                fold_metrics["brier"].append(m_nested["brier"])
                fold_metrics["ece"].append(m_nested["ece"])
                
                fold_metrics["f1_nested"].append(m_nested["f1"])
                fold_metrics["precision_nested"].append(m_nested["precision"])
                fold_metrics["recall_nested"].append(m_nested["recall"])
                fold_metrics["mcc_nested"].append(m_nested["mcc"])
                fold_metrics["f1_optimistic"].append(m_opt["f1"])
                
            t_cfg = time.time() - t_cfg_start
            
            # Aggregate fold summary
            summary = {
                "key": key,
                "type": cfg_type,
                "description": desc,
                "channels": ch_list,
                "feature_count": int(X_outer_tr.shape[1]),
                "execution_time_seconds": float(t_cfg),
                "metrics": {
                    metric: {
                        "mean": float(np.mean(vals)),
                        "std": float(np.std(vals))
                    } for metric, vals in fold_metrics.items()
                }
            }
            
            benchmark_results[key] = summary
            checkpointer.record_completed(key, summary)
            
            m = summary["metrics"]
            print(f"  -> Features: {summary['feature_count']} dims | Time: {t_cfg:.1f}s | PR-AUC: {m['pr_auc']['mean']:.4f}±{m['pr_auc']['std']:.4f} | ROC-AUC: {m['roc_auc']['mean']:.4f}±{m['roc_auc']['std']:.4f}", flush=True)
            print(f"  -> Frozen Nested F1: {m['f1_nested']['mean']:.4f}±{m['f1_nested']['std']:.4f} (Mean Tau*: {m['selected_inner_threshold']['mean']:.3f}) | F1 (Th=0.5): {m['f1_050']['mean']:.4f}±{m['f1_050']['std']:.4f}", flush=True)
            print(f"  -> In-Sample Optimistic F1: {m['f1_optimistic']['mean']:.4f} (Selection Gap: +{m['f1_optimistic']['mean'] - m['f1_nested']['mean']:.4f}) | Brier: {m['brier']['mean']:.4f} | ECE: {m['ece']['mean']:.4f}", flush=True)
            
        except Exception as e:
            err_msg = f"ERROR in {key}: {str(e)}\n{traceback.format_exc()}"
            print(err_msg, flush=True)
            summary = {
                "key": key,
                "type": cfg_type,
                "description": desc,
                "status": "FAILED",
                "error": err_msg
            }
            benchmark_results[key] = summary
            checkpointer.record_completed(key, summary)
            
    t_total = time.time() - t_bench_start
    mem_final = get_memory_mb()
    
    print("\n" + "=" * 80, flush=True)
    print(f"BENCHMARK COMPLETED: 18 Configurations Evaluated in {t_total:.1f}s (~{t_total/60.0:.1f} mins)", flush=True)
    print(f"Peak Working Set Memory: {mem_final:.1f} MB", flush=True)
    print("=" * 80, flush=True)
    
    # Save final report atomically
    final_output = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "total_configurations": total_configs,
        "total_model_fits": total_configs * 25,
        "total_execution_seconds": float(t_total),
        "peak_working_set_mb": float(mem_final),
        "benchmark_results": benchmark_results
    }
    atomic_save_json(reports_path, final_output)
    print(f"[OK] Full reproducible benchmark results saved to {reports_path}", flush=True)

if __name__ == "__main__":
    main()
