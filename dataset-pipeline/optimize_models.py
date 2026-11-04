"""
optimize_models.py
==================
Advanced ML Optimization & Research-Grade Evaluation Pipeline
Strict adherence to:
1. Zero test data leakage (all transformations, scalers, weights, and threshold sweeps fit on train/val only).
2. Independent evaluation of all 4 datasets.
3. Multi-algorithm comparison: Logistic Regression, Random Forest, Extra Trees, HistGradientBoosting, XGBoost.
4. Threshold tuning on validation partition only.
5. Project-wise test evaluation for TD dataset (6 held-out repositories).
6. Comprehensive metrics: Precision, Recall, F1, Balanced Accuracy, MCC, PR-AUC, ROC-AUC, Brier score, Confusion Matrices.
"""

import os
os.environ["LOKY_MAX_CPU_COUNT"] = "4"
os.environ["PYTHONUNBUFFERED"] = "1"

import json
import time
import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import (
    RandomForestClassifier, ExtraTreesClassifier,
    HistGradientBoostingClassifier
)
from xgboost import XGBClassifier
from sklearn.metrics import (
    accuracy_score, balanced_accuracy_score, precision_recall_fscore_support,
    confusion_matrix, average_precision_score, roc_auc_score, matthews_corrcoef,
    brier_score_loss, hamming_loss
)

EXP_DIR = os.path.join("dataset-pipeline", "optimization_experiments")
os.makedirs(EXP_DIR, exist_ok=True)

final_report = {}

def find_best_threshold(y_val, proba_val, metric="f1"):
    """Finds decision threshold on validation set only."""
    best_th = 0.5
    best_score = -1.0
    for th in np.linspace(0.05, 0.95, 91):
        preds = (proba_val >= th).astype(int)
        if metric == "f1":
            _, _, score, _ = precision_recall_fscore_support(y_val, preds, average="binary", zero_division=0)
        elif metric == "mcc":
            score = matthews_corrcoef(y_val, preds)
        elif metric == "bal_acc":
            score = balanced_accuracy_score(y_val, preds)
        if score > best_score:
            best_score = score
            best_th = th
    return float(best_th), float(best_score)

def evaluate_binary_comprehensive(y_true, y_prob, threshold=0.5):
    y_pred = (y_prob >= threshold).astype(int)
    acc = accuracy_score(y_true, y_pred)
    bal_acc = balanced_accuracy_score(y_true, y_pred)
    prec, rec, f1, _ = precision_recall_fscore_support(y_true, y_pred, average="binary", zero_division=0)
    mcc = matthews_corrcoef(y_true, y_pred)
    pr_auc = average_precision_score(y_true, y_prob)
    roc_auc = roc_auc_score(y_true, y_prob) if len(np.unique(y_true)) > 1 else 0.5
    brier = brier_score_loss(y_true, y_prob)
    cm = confusion_matrix(y_true, y_pred).tolist()
    tn, fp, fn, tp = cm[0][0], cm[0][1], cm[1][0], cm[1][1]
    spec = tn / (tn + fp) if (tn + fp) > 0 else 0.0
    fpr = fp / (tn + fp) if (tn + fp) > 0 else 0.0
    fnr = fn / (fn + tp) if (fn + tp) > 0 else 0.0
    return {
        "threshold": float(threshold),
        "accuracy": float(acc),
        "balanced_accuracy": float(bal_acc),
        "precision": float(prec),
        "recall": float(rec),
        "f1": float(f1),
        "mcc": float(mcc),
        "pr_auc": float(pr_auc),
        "roc_auc": float(roc_auc),
        "brier_score": float(brier),
        "specificity": float(spec),
        "fpr": float(fpr),
        "fnr": float(fnr),
        "confusion_matrix": cm,
        "counts": {"tn": tn, "fp": fp, "fn": fn, "tp": tp}
    }


# ==============================================================================
# 1. CSIQ OPTIMIZATION (Class-Level God Class Detection)
# ==============================================================================
print("\n" + "="*80)
print("1. OPTIMIZING CSIQ (God Class Detection)")
print("="*80)

csiq_dir = os.path.join("dataset-extracted", "CSIQ")
csiq_train = pd.read_csv(os.path.join(csiq_dir, "train.csv"))
csiq_val = pd.read_csv(os.path.join(csiq_dir, "validation.csv"))
csiq_test = pd.read_csv(os.path.join(csiq_dir, "test.csv"))

csiq_features = [
    "CBO", "RFC", "SRFC", "DIT", "NOC", "WMC", "LOC", "CMLOC",
    "NOF", "NOSF", "NOM", "NOSM", "NORM", "LCOM", "LCAM", "LTCC", "ATFD", "SI"
]
csiq_target = "GodClass"

imputer_csiq = SimpleImputer(strategy="median")
X_tr_csiq = imputer_csiq.fit_transform(csiq_train[csiq_features])
y_tr_csiq = csiq_train[csiq_target].values
X_v_csiq = imputer_csiq.transform(csiq_val[csiq_features])
y_v_csiq = csiq_val[csiq_target].values
X_te_csiq = imputer_csiq.transform(csiq_test[csiq_features])
y_te_csiq = csiq_test[csiq_target].values

scaler_csiq = StandardScaler()
X_tr_csiq_s = scaler_csiq.fit_transform(X_tr_csiq)
X_v_csiq_s = scaler_csiq.transform(X_v_csiq)
X_te_csiq_s = scaler_csiq.transform(X_te_csiq)

csiq_models = {
    "LogisticRegression_Baseline": (LogisticRegression(max_iter=1000, class_weight="balanced", random_state=42), True),
    "RandomForest_Default": (RandomForestClassifier(n_estimators=100, max_depth=12, class_weight="balanced", random_state=42, n_jobs=2), False),
    "RandomForest_Optimized": (RandomForestClassifier(n_estimators=150, max_depth=16, min_samples_leaf=4, class_weight="balanced", random_state=42, n_jobs=2), False),
    "ExtraTrees_Classifier": (ExtraTreesClassifier(n_estimators=100, max_depth=16, min_samples_leaf=4, class_weight="balanced", random_state=42, n_jobs=2), False),
    "HistGradientBoosting": (HistGradientBoostingClassifier(max_iter=150, max_depth=8, class_weight="balanced", random_state=42), False),
    "XGBoost_Classifier": (XGBClassifier(n_estimators=150, max_depth=6, scale_pos_weight=4.8, eval_metric="logloss", random_state=42, n_jobs=2), False)
}

csiq_results = {}
for m_name, (clf, use_scaled) in csiq_models.items():
    t0 = time.time()
    X_train_curr = X_tr_csiq_s if use_scaled else X_tr_csiq
    X_val_curr = X_v_csiq_s if use_scaled else X_v_csiq
    X_test_curr = X_te_csiq_s if use_scaled else X_te_csiq
    
    clf.fit(X_train_curr, y_tr_csiq)
    train_time = time.time() - t0
    
    val_prob = clf.predict_proba(X_val_curr)[:, 1]
    test_prob = clf.predict_proba(X_test_curr)[:, 1]
    
    # Threshold optimization strictly on validation set
    best_th, val_f1 = find_best_threshold(y_v_csiq, val_prob, metric="f1")
    
    eval_default = evaluate_binary_comprehensive(y_te_csiq, test_prob, threshold=0.5)
    eval_tuned = evaluate_binary_comprehensive(y_te_csiq, test_prob, threshold=best_th)
    
    csiq_results[m_name] = {
        "train_time_sec": float(round(train_time, 2)),
        "val_best_threshold": best_th,
        "val_f1_at_best_threshold": val_f1,
        "test_default_th_0.5": eval_default,
        "test_tuned_threshold": eval_tuned
    }
    print(f"[{m_name}] Val Best Th={best_th:.3f} | Test F1 (th=0.5)={eval_default['f1']:.4f} -> Tuned F1={eval_tuned['f1']:.4f}, BalAcc={eval_tuned['balanced_accuracy']:.4f}, PR-AUC={eval_tuned['pr_auc']:.4f}")

final_report["CSIQ"] = csiq_results


# ==============================================================================
# 2. MULTI-SMELL OPTIMIZATION (Multi-Label Code Smell Detection)
# ==============================================================================
print("\n" + "="*80)
print("2. OPTIMIZING MULTI-SMELL (Multi-Label Detection)")
print("="*80)

ms_dir = os.path.join("dataset-extracted", "multi-smell-dataset-v1_2")
ms_train = pd.read_csv(os.path.join(ms_dir, "train.csv"))
ms_val = pd.read_csv(os.path.join(ms_dir, "validation.csv"))
ms_test = pd.read_csv(os.path.join(ms_dir, "test.csv"))

ms_features = [
    "Logical Lines", "Distinct Operators", "Distinct Operands", "Total Operators",
    "Total Operands", "Vocabulary", "Length", "Calculated Length", "Volume",
    "Difficulty", "Effort", "Time Required", "Bugs", "Cyclomatic Complexity"
]
ms_targets = ["God class", "Data class", "Feature envy", "Long method"]

X_tr_ms = ms_train[ms_features].values.astype(float)
y_tr_ms = ms_train[ms_targets].values.astype(int)
X_v_ms = ms_val[ms_features].values.astype(float)
y_v_ms = ms_val[ms_targets].values.astype(int)
X_te_ms = ms_test[ms_features].values.astype(float)
y_te_ms = ms_test[ms_targets].values.astype(int)

ms_results = {}
for clf_type in ["RandomForest", "HistGradientBoosting", "XGBoost"]:
    t0 = time.time()
    per_smell_eval = {}
    val_thresholds = {}
    
    test_preds_default = np.zeros_like(y_te_ms)
    test_preds_tuned = np.zeros_like(y_te_ms)
    
    for i, target in enumerate(ms_targets):
        y_tr_sub = y_tr_ms[:, i]
        y_v_sub = y_v_ms[:, i]
        y_te_sub = y_te_ms[:, i]
        
        neg_count = np.sum(y_tr_sub == 0)
        pos_count = np.sum(y_tr_sub == 1)
        scale_weight = float(neg_count / max(1, pos_count))
        
        if clf_type == "RandomForest":
            clf = RandomForestClassifier(n_estimators=120, max_depth=12, min_samples_leaf=4,
                                         class_weight="balanced", random_state=42, n_jobs=2)
        elif clf_type == "HistGradientBoosting":
            clf = HistGradientBoostingClassifier(max_iter=120, max_depth=8, class_weight="balanced", random_state=42)
        elif clf_type == "XGBoost":
            clf = XGBClassifier(n_estimators=120, max_depth=5, scale_pos_weight=scale_weight,
                                eval_metric="logloss", random_state=42, n_jobs=2)
        
        clf.fit(X_tr_ms, y_tr_sub)
        val_p = clf.predict_proba(X_v_ms)[:, 1]
        te_p = clf.predict_proba(X_te_ms)[:, 1]
        
        # Tune threshold on validation split specifically for F1 of that smell
        best_th, val_f1 = find_best_threshold(y_v_sub, val_p, metric="f1")
        val_thresholds[target] = best_th
        
        test_preds_default[:, i] = (te_p >= 0.5).astype(int)
        test_preds_tuned[:, i] = (te_p >= best_th).astype(int)
        
        eval_def = evaluate_binary_comprehensive(y_te_sub, te_p, threshold=0.5)
        eval_tun = evaluate_binary_comprehensive(y_te_sub, te_p, threshold=best_th)
        per_smell_eval[target] = {
            "val_threshold": best_th,
            "test_default": eval_def,
            "test_tuned": eval_tun
        }
    
    runtime = time.time() - t0
    macro_f1_def = float(np.mean([per_smell_eval[t]["test_default"]["f1"] for t in ms_targets]))
    macro_f1_tun = float(np.mean([per_smell_eval[t]["test_tuned"]["f1"] for t in ms_targets]))
    h_loss_def = float(hamming_loss(y_te_ms, test_preds_default))
    h_loss_tun = float(hamming_loss(y_te_ms, test_preds_tuned))
    
    ms_results[clf_type] = {
        "runtime_sec": round(runtime, 2),
        "val_thresholds": val_thresholds,
        "macro_f1_default": macro_f1_def,
        "macro_f1_tuned": macro_f1_tun,
        "hamming_loss_default": h_loss_def,
        "hamming_loss_tuned": h_loss_tun,
        "per_smell": per_smell_eval
    }
    print(f"[{clf_type}] Macro-F1 Default={macro_f1_def:.4f} -> Tuned={macro_f1_tun:.4f} | Hamming Loss: {h_loss_tun:.4f}")
    for t in ms_targets:
        print(f"   {t:15s} Th={val_thresholds[t]:.3f} | Default F1={per_smell_eval[t]['test_default']['f1']:.4f} -> Tuned F1={per_smell_eval[t]['test_tuned']['f1']:.4f} (P={per_smell_eval[t]['test_tuned']['precision']:.4f}, R={per_smell_eval[t]['test_tuned']['recall']:.4f})")

final_report["multi-smell"] = ms_results


# ==============================================================================
# 3. SMELLS.ZIP OPTIMIZATION (Architectural Smell Classification)
# ==============================================================================
print("\n" + "="*80)
print("3. OPTIMIZING SMELLS.ZIP (Architectural Smell Classification)")
print("="*80)

smells_dir = os.path.join("dataset-extracted", "smells")
sm_train = pd.read_csv(os.path.join(smells_dir, "train.csv"))
sm_val = pd.read_csv(os.path.join(smells_dir, "validation.csv"))
sm_test = pd.read_csv(os.path.join(smells_dir, "test.csv"))

sm_features = [
    "numOfEdges", "nPackages", "nClasses", "pageRankAvrg", "pageRankMax",
    "pageRankWeighted", "overlapRatio", "size", "age"
]
sm_target = "smellType"
classes_sm = ["cyclicDep", "unstableDep", "hubLikeDep"]
class_to_idx = {c: i for i, c in enumerate(classes_sm)}

X_tr_sm = sm_train[sm_features].values.astype(float)
y_tr_sm = np.array([class_to_idx[x] for x in sm_train[sm_target]])
X_v_sm = sm_val[sm_features].values.astype(float)
y_v_sm = np.array([class_to_idx[x] for x in sm_val[sm_target]])
X_te_sm = sm_test[sm_features].values.astype(float)
y_te_sm = np.array([class_to_idx[x] for x in sm_test[sm_target]])

sm_models = {
    "RandomForest_Tuned": RandomForestClassifier(n_estimators=120, max_depth=14, min_samples_leaf=4, class_weight="balanced", random_state=42, n_jobs=2),
    "HistGradientBoosting_Tuned": HistGradientBoostingClassifier(max_iter=150, max_depth=10, class_weight="balanced", random_state=42),
    "XGBoost_Multiclass": XGBClassifier(n_estimators=120, max_depth=6, objective="multi:softprob", num_class=3, eval_metric="mlogloss", random_state=42, n_jobs=2)
}

sm_results = {}
for m_name, clf in sm_models.items():
    t0 = time.time()
    clf.fit(X_tr_sm, y_tr_sm)
    runtime = time.time() - t0
    
    val_probs = clf.predict_proba(X_v_sm)
    te_probs = clf.predict_proba(X_te_sm)
    
    # Class-weight probability thresholding on validation set to reduce false alarms on unstableDep
    # Default argmax:
    preds_def = np.argmax(te_probs, axis=1)
    
    # Tuned multipliers on val set
    best_weights = [1.0, 1.0, 1.0]
    best_val_macro = -1.0
    for w_unstable in [0.5, 0.7, 0.8, 1.0, 1.2]:
        for w_hub in [0.8, 1.0, 1.2, 1.5]:
            weights = np.array([1.0, w_unstable, w_hub])
            v_adjusted = val_probs * weights
            v_preds = np.argmax(v_adjusted, axis=1)
            _, _, macro_f1, _ = precision_recall_fscore_support(y_v_sm, v_preds, average="macro", zero_division=0)
            if macro_f1 > best_val_macro:
                best_val_macro = macro_f1
                best_weights = [1.0, w_unstable, w_hub]
    
    te_adjusted = te_probs * np.array(best_weights)
    preds_tuned = np.argmax(te_adjusted, axis=1)
    
    def eval_mc(preds):
        acc = accuracy_score(y_te_sm, preds)
        bal_acc = balanced_accuracy_score(y_te_sm, preds)
        p_m, r_m, f1_m, _ = precision_recall_fscore_support(y_te_sm, preds, average="macro", zero_division=0)
        p_w, r_w, f1_w, _ = precision_recall_fscore_support(y_te_sm, preds, average="weighted", zero_division=0)
        cm = confusion_matrix(y_te_sm, preds).tolist()
        p_c, r_c, f1_c, _ = precision_recall_fscore_support(y_te_sm, preds, average=None, zero_division=0)
        per_c = {classes_sm[i]: {"precision": float(p_c[i]), "recall": float(r_c[i]), "f1": float(f1_c[i])} for i in range(3)}
        return {"accuracy": float(acc), "balanced_accuracy": float(bal_acc), "macro_f1": float(f1_m), "weighted_f1": float(f1_w), "confusion_matrix": cm, "per_class": per_c}
    
    res_def = eval_mc(preds_def)
    res_tun = eval_mc(preds_tuned)
    
    sm_results[m_name] = {
        "runtime_sec": round(runtime, 2),
        "best_val_weights": best_weights,
        "default_test": res_def,
        "tuned_test": res_tun
    }
    print(f"[{m_name}] Default Macro-F1={res_def['macro_f1']:.4f} -> Tuned Macro-F1={res_tun['macro_f1']:.4f} (Weights={best_weights})")
    for c in classes_sm:
        print(f"   {c:15s}: Precision={res_tun['per_class'][c]['precision']:.4f}, Recall={res_tun['per_class'][c]['recall']:.4f}, F1={res_tun['per_class'][c]['f1']:.4f}")

final_report["smells"] = sm_results


# ==============================================================================
# 4. TD DATASET OPTIMIZATION (Cross-Project Fault-Inducing Change Prediction)
# ==============================================================================
print("\n" + "="*80)
print("4. OPTIMIZING TD DATASET (Cross-Project SZZ Defect Prediction)")
print("="*80)

td_dir = os.path.join("dataset-extracted", "TD dataset")
td_train = pd.read_csv(os.path.join(td_dir, "train.csv"))
td_val = pd.read_csv(os.path.join(td_dir, "validation.csv"))
td_test = pd.read_csv(os.path.join(td_dir, "test.csv"))

td_features = [
    "N_FILES_CHANGED", "LINES_ADDED", "LINES_REMOVED",
    "N_REFACTORINGS", "N_PARENTS", "IN_MAIN_BRANCH", "AUTHOR_TIMEZONE"
]
td_target = "IS_FAULT_INDUCING"

imputer_td = SimpleImputer(strategy="constant", fill_value=0)
X_tr_td = imputer_td.fit_transform(td_train[td_features])
y_tr_td = td_train[td_target].values
X_v_td = imputer_td.transform(td_val[td_features])
y_v_td = td_val[td_target].values
X_te_td = imputer_td.transform(td_test[td_features])
y_te_td = td_test[td_target].values

td_models = {
    "RandomForest_Tuned": RandomForestClassifier(n_estimators=150, max_depth=10, min_samples_leaf=6, class_weight="balanced", random_state=42, n_jobs=2),
    "HistGradientBoosting_Tuned": HistGradientBoostingClassifier(max_iter=150, max_depth=8, class_weight="balanced", random_state=42),
    "XGBoost_Tuned": XGBClassifier(n_estimators=150, max_depth=6, scale_pos_weight=5.0, subsample=0.8, colsample_bytree=0.8, eval_metric="logloss", random_state=42, n_jobs=2)
}

td_results = {}
for m_name, clf in td_models.items():
    t0 = time.time()
    clf.fit(X_tr_td, y_tr_td)
    runtime = time.time() - t0
    
    val_prob = clf.predict_proba(X_v_td)[:, 1]
    te_prob = clf.predict_proba(X_te_td)[:, 1]
    
    # Tune threshold strictly on validation split (which spans 9 validation projects)
    best_th, val_f1 = find_best_threshold(y_v_td, val_prob, metric="f1")
    
    eval_def = evaluate_binary_comprehensive(y_te_td, te_prob, threshold=0.5)
    eval_tun = evaluate_binary_comprehensive(y_te_td, te_prob, threshold=best_th)
    
    # Project-wise evaluation across the 6 held-out test projects (Safeguard 2)
    proj_evals = {}
    for p_id in sorted(td_test["PROJECT_ID"].unique()):
        mask = (td_test["PROJECT_ID"] == p_id).values
        proj_evals[p_id] = evaluate_binary_comprehensive(y_te_td[mask], te_prob[mask], threshold=best_th)
    
    td_results[m_name] = {
        "runtime_sec": round(runtime, 2),
        "val_best_threshold": best_th,
        "val_f1_at_best_threshold": val_f1,
        "test_default_th_0.5": eval_def,
        "test_tuned_threshold": eval_tun,
        "project_wise_tuned": proj_evals
    }
    print(f"[{m_name}] Val Best Th={best_th:.3f} | Default F1={eval_def['f1']:.4f} -> Tuned F1={eval_tun['f1']:.4f}, Precision={eval_tun['precision']:.4f}, Recall={eval_tun['recall']:.4f}, PR-AUC={eval_tun['pr_auc']:.4f}")

final_report["TD_dataset"] = td_results

# Export complete results to experiment JSON
out_path = os.path.join(EXP_DIR, "optimization_results.json")
with open(out_path, "w") as f:
    json.dump(final_report, f, indent=2)

print("\n" + "="*80)
print(f"Optimization experiments complete! Results saved to: {out_path}")
print("="*80)
