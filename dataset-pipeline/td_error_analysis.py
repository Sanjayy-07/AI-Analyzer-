"""
td_error_analysis.py
====================
Deep Research-Grade Error Analysis, Feature Quality Audit, Project-Grouped CV,
and Targeted Optimization for Fault-Inducing Commit Prediction (TD Dataset).
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
from sklearn.calibration import CalibratedClassifierCV, calibration_curve
from sklearn.model_selection import GroupKFold
from sklearn.inspection import permutation_importance
from xgboost import XGBClassifier
from sklearn.metrics import (
    accuracy_score, balanced_accuracy_score, precision_recall_fscore_support,
    confusion_matrix, average_precision_score, roc_auc_score, matthews_corrcoef,
    brier_score_loss
)

OUT_DIR = os.path.join("dataset-pipeline", "td_analysis_output")
os.makedirs(OUT_DIR, exist_ok=True)

def find_best_threshold(y_val_arr, proba_val_arr, metric="f1"):
    best_th = 0.5
    best_sc = -1.0
    for th in np.linspace(0.05, 0.95, 91):
        preds = (proba_val_arr >= th).astype(int)
        if metric == "f1":
            _, _, sc, _ = precision_recall_fscore_support(y_val_arr, preds, average="binary", zero_division=0)
        elif metric == "mcc":
            sc = matthews_corrcoef(y_val_arr, preds)
        elif metric == "bal_acc":
            sc = balanced_accuracy_score(y_val_arr, preds)
        if sc > best_sc:
            best_sc = sc
            best_th = th
    return float(best_th), float(best_sc)

print("Loading TD dataset splits...")
td_dir = os.path.join("dataset-extracted", "TD dataset")
train_df = pd.read_csv(os.path.join(td_dir, "train.csv"))
val_df = pd.read_csv(os.path.join(td_dir, "validation.csv"))
test_df = pd.read_csv(os.path.join(td_dir, "test.csv"))

print(f"Train: {train_df.shape} ({train_df['PROJECT_ID'].nunique()} projects)")
print(f"Val:   {val_df.shape} ({val_df['PROJECT_ID'].nunique()} projects)")
print(f"Test:  {test_df.shape} ({test_df['PROJECT_ID'].nunique()} projects)")

base_features = [
    "N_FILES_CHANGED", "LINES_ADDED", "LINES_REMOVED",
    "N_REFACTORINGS", "N_PARENTS", "IN_MAIN_BRANCH", "AUTHOR_TIMEZONE"
]
target = "IS_FAULT_INDUCING"

imputer = SimpleImputer(strategy="constant", fill_value=0)
X_train_raw = imputer.fit_transform(train_df[base_features])
y_train = train_df[target].values
X_val_raw = imputer.transform(val_df[base_features])
y_val = val_df[target].values
X_test_raw = imputer.transform(test_df[base_features])
y_test = test_df[target].values

report = {}

# ==============================================================================
# SECTION 1: TRAIN BEST HISTGRADIENTBOOSTING MODEL AND EXTRACT FULL TEST PREDICTIONS
# ==============================================================================
print("\n" + "="*80)
print("SECTION 1: BEST MODEL EVALUATION & ERROR BREAKDOWN")
print("="*80)

hgb = HistGradientBoostingClassifier(max_iter=150, max_depth=8, class_weight="balanced", random_state=42)
hgb.fit(X_train_raw, y_train)

val_probs = hgb.predict_proba(X_val_raw)[:, 1]
test_probs = hgb.predict_proba(X_test_raw)[:, 1]

# Validation threshold search
best_val_th = 0.5
best_val_f1 = -1.0
for th in np.linspace(0.05, 0.95, 91):
    preds = (val_probs >= th).astype(int)
    _, _, f1, _ = precision_recall_fscore_support(y_val, preds, average="binary", zero_division=0)
    if f1 > best_val_f1:
        best_val_f1 = f1
        best_val_th = th

best_val_th = float(best_val_th)
print(f"Best Validation Threshold (max F1): {best_val_th:.3f} (Val F1: {best_val_f1:.4f})")

test_preds = (test_probs >= best_val_th).astype(int)
cm = confusion_matrix(y_test, test_preds)
tn, fp, fn, tp = cm[0][0], cm[0][1], cm[1][0], cm[1][1]

print(f"Confusion Matrix (Test): TN={tn}, FP={fp}, FN={fn}, TP={tp}")
acc = accuracy_score(y_test, test_preds)
bal_acc = balanced_accuracy_score(y_test, test_preds)
prec, rec, f1, _ = precision_recall_fscore_support(y_test, test_preds, average="binary", zero_division=0)
pr_auc = average_precision_score(y_test, test_probs)
roc_auc = roc_auc_score(y_test, test_probs)
mcc = matthews_corrcoef(y_test, test_preds)
brier = brier_score_loss(y_test, test_probs)
spec = tn / (tn + fp)
fpr = fp / (tn + fp)
fnr = fn / (fn + tp)

test_df_eval = test_df.copy()
test_df_eval["PRED_PROB"] = test_probs
test_df_eval["PRED_LABEL"] = test_preds
test_df_eval["ERROR_TYPE"] = "TN"
test_df_eval.loc[(test_df_eval[target] == 1) & (test_df_eval["PRED_LABEL"] == 1), "ERROR_TYPE"] = "TP"
test_df_eval.loc[(test_df_eval[target] == 0) & (test_df_eval["PRED_LABEL"] == 1), "ERROR_TYPE"] = "FP"
test_df_eval.loc[(test_df_eval[target] == 1) & (test_df_eval["PRED_LABEL"] == 0), "ERROR_TYPE"] = "FN"

# Feature distributions for TP, FP, FN, TN
feature_distributions = {}
for feat in base_features:
    feat_stats = {}
    for err_t in ["TP", "FP", "FN", "TN"]:
        sub = test_df_eval[test_df_eval["ERROR_TYPE"] == err_t][feat].fillna(0)
        feat_stats[err_t] = {
            "mean": float(sub.mean()),
            "std": float(sub.std()),
            "median": float(sub.median()),
            "p25": float(sub.quantile(0.25)),
            "p75": float(sub.quantile(0.75)),
            "max": float(sub.max())
        }
    feature_distributions[feat] = feat_stats

print("\n--- Feature Distributions Across Classification Outcomes ---")
for feat in ["N_FILES_CHANGED", "LINES_ADDED", "LINES_REMOVED"]:
    print(f"Feature: {feat}")
    for err_t in ["TP", "FP", "FN", "TN"]:
        s = feature_distributions[feat][err_t]
        print(f"  {err_t}: Median={s['median']:.1f}, Mean={s['mean']:.1f}, IQR=[{s['p25']:.1f}, {s['p75']:.1f}]")

# Representative Error Cases
# Extreme False Positives (Clean commits flagged with high probability)
fps = test_df_eval[test_df_eval["ERROR_TYPE"] == "FP"].sort_values(by="PRED_PROB", ascending=False)
extreme_fps = []
for _, row in fps.head(5).iterrows():
    extreme_fps.append({
        "project": str(row["PROJECT_ID"]),
        "commit": str(row["COMMIT_HASH"])[:10],
        "pred_prob": float(round(row["PRED_PROB"], 4)),
        "files_changed": float(row["N_FILES_CHANGED"]) if pd.notna(row["N_FILES_CHANGED"]) else 0.0,
        "lines_added": float(row["LINES_ADDED"]) if pd.notna(row["LINES_ADDED"]) else 0.0,
        "lines_removed": float(row["LINES_REMOVED"]) if pd.notna(row["LINES_REMOVED"]) else 0.0,
        "refactorings": int(row["N_REFACTORINGS"]),
        "message": str(row["COMMIT_MESSAGE"]).strip()[:100].replace("\n", " ")
    })

# Extreme False Negatives (Fault-inducing commits that received very low probability)
fns = test_df_eval[test_df_eval["ERROR_TYPE"] == "FN"].sort_values(by="PRED_PROB", ascending=True)
extreme_fns = []
for _, row in fns.head(5).iterrows():
    extreme_fns.append({
        "project": str(row["PROJECT_ID"]),
        "commit": str(row["COMMIT_HASH"])[:10],
        "pred_prob": float(round(row["PRED_PROB"], 4)),
        "files_changed": float(row["N_FILES_CHANGED"]) if pd.notna(row["N_FILES_CHANGED"]) else 0.0,
        "lines_added": float(row["LINES_ADDED"]) if pd.notna(row["LINES_ADDED"]) else 0.0,
        "lines_removed": float(row["LINES_REMOVED"]) if pd.notna(row["LINES_REMOVED"]) else 0.0,
        "refactorings": int(row["N_REFACTORINGS"]),
        "message": str(row["COMMIT_MESSAGE"]).strip()[:100].replace("\n", " ")
    })

# Project-Wise Error Breakdown
project_breakdown = {}
print("\n--- Project-Wise Error Breakdown ---")
for proj in sorted(test_df["PROJECT_ID"].unique()):
    sub = test_df_eval[test_df_eval["PROJECT_ID"] == proj]
    sub_y = sub[target].values
    sub_preds = sub["PRED_LABEL"].values
    sub_probs = sub["PRED_PROB"].values
    cm_p = confusion_matrix(sub_y, sub_preds, labels=[0, 1])
    tn_p, fp_p, fn_p, tp_p = cm_p[0][0], cm_p[0][1], cm_p[1][0], cm_p[1][1]
    p_p, r_p, f1_p, _ = precision_recall_fscore_support(sub_y, sub_preds, average="binary", zero_division=0)
    pr_auc_p = average_precision_score(sub_y, sub_probs) if np.sum(sub_y) > 0 else 0.0
    roc_auc_p = roc_auc_score(sub_y, sub_probs) if len(np.unique(sub_y)) > 1 else 0.5
    project_breakdown[proj] = {
        "n_commits": int(len(sub)),
        "n_faults": int(np.sum(sub_y)),
        "fault_rate_pct": float(round(np.sum(sub_y)/len(sub)*100, 2)),
        "tp": int(tp_p), "fp": int(fp_p), "fn": int(fn_p), "tn": int(tn_p),
        "precision": float(round(p_p, 4)),
        "recall": float(round(r_p, 4)),
        "f1": float(round(f1_p, 4)),
        "pr_auc": float(round(pr_auc_p, 4)),
        "roc_auc": float(round(roc_auc_p, 4)),
        "accuracy": float(round(accuracy_score(sub_y, sub_preds), 4)),
        "balanced_accuracy": float(round(balanced_accuracy_score(sub_y, sub_preds), 4)),
        "specificity": float(round(tn_p / (tn_p + fp_p) if (tn_p + fp_p) > 0 else 0.0, 4)),
        "fpr": float(round(fp_p / (tn_p + fp_p) if (tn_p + fp_p) > 0 else 0.0, 4)),
        "fnr": float(round(fn_p / (fn_p + tp_p) if (fn_p + tp_p) > 0 else 0.0, 4))
    }
    print(f"  {proj:25s}: TP={tp_p:4d}, FP={fp_p:4d}, FN={fn_p:4d}, TN={tn_p:4d} | P={p_p:.4f}, R={r_p:.4f}, F1={f1_p:.4f}, PR-AUC={pr_auc_p:.4f}")

report["error_analysis"] = {
    "model": "HistGradientBoostingClassifier",
    "threshold": best_val_th,
    "metrics": {
        "accuracy": acc, "balanced_accuracy": bal_acc, "precision": prec,
        "recall": rec, "f1": f1, "pr_auc": pr_auc, "roc_auc": roc_auc,
        "mcc": mcc, "brier_score": brier, "specificity": spec,
        "fpr": fpr, "fnr": fnr, "tp": int(tp), "fp": int(fp), "fn": int(fn), "tn": int(tn)
    },
    "feature_distributions": feature_distributions,
    "extreme_false_positives": extreme_fps,
    "extreme_false_negatives": extreme_fns,
    "project_breakdown": project_breakdown
}


# ==============================================================================
# SECTION 2: VALIDATION THRESHOLD OPTIMIZATION CURVES & MULTI-OBJECTIVE TRADEOFFS
# ==============================================================================
print("\n" + "="*80)
print("SECTION 2: VALIDATION THRESHOLD SWEEP & OBJECTIVE TRADEOFFS")
print("="*80)

threshold_curves = []
operating_points = {}

best_f1_th = 0.5; max_f1 = -1.0
best_mcc_th = 0.5; max_mcc = -1.0
best_bal_th = 0.5; max_bal = -1.0
best_prec_for_rec50_th = 0.5; max_p_at_r50 = -1.0
best_rec_for_prec30_th = 0.5; max_r_at_p30 = -1.0

for th in np.linspace(0.05, 0.95, 91):
    val_preds = (val_probs >= th).astype(int)
    v_acc = accuracy_score(y_val, val_preds)
    v_bal = balanced_accuracy_score(y_val, val_preds)
    v_p, v_r, v_f1, _ = precision_recall_fscore_support(y_val, val_preds, average="binary", zero_division=0)
    v_mcc = matthews_corrcoef(y_val, val_preds)
    cm_v = confusion_matrix(y_val, val_preds)
    v_tn, v_fp = cm_v[0][0], cm_v[0][1]
    v_fpr = v_fp / (v_tn + v_fp) if (v_tn + v_fp) > 0 else 0.0
    
    threshold_curves.append({
        "threshold": float(round(th, 3)),
        "precision": float(round(v_p, 4)),
        "recall": float(round(v_r, 4)),
        "f1": float(round(v_f1, 4)),
        "balanced_accuracy": float(round(v_bal, 4)),
        "mcc": float(round(v_mcc, 4)),
        "fpr": float(round(v_fpr, 4))
    })
    
    if v_f1 > max_f1:
        max_f1 = v_f1
        best_f1_th = th
    if v_mcc > max_mcc:
        max_mcc = v_mcc
        best_mcc_th = th
    if v_bal > max_bal:
        max_bal = v_bal
        best_bal_th = th
    if v_r >= 0.50 and v_p > max_p_at_r50:
        max_p_at_r50 = v_p
        best_prec_for_rec50_th = th
    if v_p >= 0.30 and v_r > max_r_at_p30:
        max_r_at_p30 = v_r
        best_rec_for_prec30_th = th

def eval_test_at_threshold(th):
    te_preds = (test_probs >= th).astype(int)
    p, r, f1_val, _ = precision_recall_fscore_support(y_test, te_preds, average="binary", zero_division=0)
    acc_val = accuracy_score(y_test, te_preds)
    bal_val = balanced_accuracy_score(y_test, te_preds)
    mcc_val = matthews_corrcoef(y_test, te_preds)
    cm_val = confusion_matrix(y_test, te_preds).tolist()
    return {
        "selected_val_threshold": float(round(th, 3)),
        "test_precision": float(round(p, 4)),
        "test_recall": float(round(r, 4)),
        "test_f1": float(round(f1_val, 4)),
        "test_balanced_accuracy": float(round(bal_val, 4)),
        "test_accuracy": float(round(acc_val, 4)),
        "test_mcc": float(round(mcc_val, 4)),
        "test_confusion_matrix": cm_val
    }

operating_points["Default_0.5"] = eval_test_at_threshold(0.5)
operating_points["Max_Validation_F1"] = eval_test_at_threshold(best_f1_th)
operating_points["Max_Validation_MCC"] = eval_test_at_threshold(best_mcc_th)
operating_points["Max_Validation_BalancedAcc"] = eval_test_at_threshold(best_bal_th)
operating_points["Max_Precision_Given_Recall_ge_50"] = eval_test_at_threshold(best_prec_for_rec50_th)
operating_points["Max_Recall_Given_Precision_ge_30"] = eval_test_at_threshold(best_rec_for_prec30_th)

print("Validation-Selected Operating Points Evaluated on Test Set:")
for k, v in operating_points.items():
    print(f"  {k:35s} (Th={v['selected_val_threshold']:.3f}): Test F1={v['test_f1']:.4f}, Prec={v['test_precision']:.4f}, Rec={v['test_recall']:.4f}, BalAcc={v['test_balanced_accuracy']:.4f}, MCC={v['test_mcc']:.4f}")

report["threshold_optimization"] = {
    "validation_curves_sample": threshold_curves[::5], # sampled for compact JSON
    "operating_points": operating_points
}


# ==============================================================================
# SECTION 3: FEATURE QUALITY, LEAKAGE AUDIT & PERMUTATION IMPORTANCE
# ==============================================================================
print("\n" + "="*80)
print("SECTION 3: FEATURE AUDIT, CORRELATIONS & PERMUTATION IMPORTANCE")
print("="*80)

# Correlation with target on training data
train_numeric = train_df[base_features].copy()
imputed_train_df = pd.DataFrame(imputer.transform(train_numeric), columns=base_features)
imputed_train_df[target] = y_train
corr_matrix = imputed_train_df.corr()
label_correlations = corr_matrix[target].drop(target).to_dict()

# Permutation importance strictly on validation data
perm_res = permutation_importance(hgb, X_val_raw, y_val, n_repeats=10, random_state=42, scoring="f1", n_jobs=1)
perm_importances = {}
for i, feat in enumerate(base_features):
    perm_importances[feat] = {
        "mean_f1_drop": float(round(perm_res.importances_mean[i], 5)),
        "std_f1_drop": float(round(perm_res.importances_std[i], 5)),
        "label_correlation": float(round(label_correlations[feat], 4))
    }
    print(f"  {feat:20s}: Mean F1 Drop = {perm_res.importances_mean[i]:.5f} (±{perm_res.importances_std[i]:.5f}), Corr = {label_correlations[feat]:.4f}")

# Feature Ablation: Leave-One-Feature-Out on Training/Validation
print("\n--- Leave-One-Feature-Out Ablation Study ---")
ablation_results = {}
for feat_to_drop in base_features:
    sub_feats = [f for f in base_features if f != feat_to_drop]
    X_tr_sub = imputer.fit_transform(train_df[sub_feats])
    X_v_sub = imputer.transform(val_df[sub_feats])
    X_te_sub = imputer.transform(test_df[sub_feats])
    
    clf_sub = HistGradientBoostingClassifier(max_iter=150, max_depth=8, class_weight="balanced", random_state=42)
    clf_sub.fit(X_tr_sub, y_train)
    v_prob_sub = clf_sub.predict_proba(X_v_sub)[:, 1]
    te_prob_sub = clf_sub.predict_proba(X_te_sub)[:, 1]
    
    b_th, val_f1_sub = find_best_threshold(y_val, v_prob_sub, metric="f1")
    te_preds_sub = (te_prob_sub >= b_th).astype(int)
    p_sub, r_sub, te_f1_sub, _ = precision_recall_fscore_support(y_test, te_preds_sub, average="binary", zero_division=0)
    pr_auc_sub = average_precision_score(y_test, te_prob_sub)
    
    ablation_results[f"drop_{feat_to_drop}"] = {
        "dropped_feature": feat_to_drop,
        "val_f1": float(round(val_f1_sub, 4)),
        "test_f1": float(round(te_f1_sub, 4)),
        "test_precision": float(round(p_sub, 4)),
        "test_recall": float(round(r_sub, 4)),
        "test_pr_auc": float(round(pr_auc_sub, 4))
    }
    print(f"  Dropped {feat_to_drop:20s}: Val F1 = {val_f1_sub:.4f}, Test F1 = {te_f1_sub:.4f}, Test PR-AUC = {pr_auc_sub:.4f}")

report["feature_analysis"] = {
    "permutation_importance": perm_importances,
    "feature_ablation": ablation_results
}


# ==============================================================================
# SECTION 4: RIGOROUS PROJECT-GROUPED CROSS-VALIDATION
# ==============================================================================
print("\n" + "="*80)
print("SECTION 4: 5-FOLD PROJECT-GROUPED CROSS-VALIDATION (ZERO LEAKAGE)")
print("="*80)

# Combine train + val into development dataset (130,903 commits across 25 development projects)
dev_df = pd.concat([train_df, val_df], ignore_index=True)
dev_projects = dev_df["PROJECT_ID"].values
X_dev_raw = imputer.fit_transform(dev_df[base_features])
y_dev = dev_df[target].values

gkf = GroupKFold(n_splits=5)
cv_folds = []
cv_f1s = []
cv_pr_aucs = []
cv_bal_accs = []
cv_mccs = []

fold_idx = 1
for train_idx, test_idx in gkf.split(X_dev_raw, y_dev, groups=dev_projects):
    X_tr_cv, X_te_cv = X_dev_raw[train_idx], X_dev_raw[test_idx]
    y_tr_cv, y_te_cv = y_dev[train_idx], y_dev[test_idx]
    held_out_projs = sorted(list(np.unique(dev_projects[test_idx])))
    
    # Internal validation split to tune threshold strictly inside training fold
    # Sub-group split inside training fold
    sub_groups = dev_projects[train_idx]
    sub_gkf = GroupKFold(n_splits=4)
    sub_tr_idx, sub_val_idx = next(sub_gkf.split(X_tr_cv, y_tr_cv, groups=sub_groups))
    
    cv_clf = HistGradientBoostingClassifier(max_iter=150, max_depth=8, class_weight="balanced", random_state=42)
    cv_clf.fit(X_tr_cv[sub_tr_idx], y_tr_cv[sub_tr_idx])
    
    sub_val_prob = cv_clf.predict_proba(X_tr_cv[sub_val_idx])[:, 1]
    cv_best_th, _ = find_best_threshold(y_tr_cv[sub_val_idx], sub_val_prob, metric="f1")
    
    # Refit on entire fold train set and evaluate on held-out projects
    cv_clf.fit(X_tr_cv, y_tr_cv)
    cv_te_prob = cv_clf.predict_proba(X_te_cv)[:, 1]
    cv_te_preds = (cv_te_prob >= cv_best_th).astype(int)
    
    f_acc = accuracy_score(y_te_cv, cv_te_preds)
    f_bal = balanced_accuracy_score(y_te_cv, cv_te_preds)
    f_p, f_r, f_f1, _ = precision_recall_fscore_support(y_te_cv, cv_te_preds, average="binary", zero_division=0)
    f_pr_auc = average_precision_score(y_te_cv, cv_te_prob)
    f_mcc = matthews_corrcoef(y_te_cv, cv_te_preds)
    
    cv_folds.append({
        "fold": fold_idx,
        "held_out_projects": held_out_projs,
        "test_samples": int(len(test_idx)),
        "test_faults": int(np.sum(y_te_cv)),
        "best_threshold": float(round(cv_best_th, 3)),
        "precision": float(round(f_p, 4)),
        "recall": float(round(f_r, 4)),
        "f1": float(round(f_f1, 4)),
        "balanced_accuracy": float(round(f_bal, 4)),
        "pr_auc": float(round(f_pr_auc, 4)),
        "mcc": float(round(f_mcc, 4))
    })
    cv_f1s.append(f_f1)
    cv_pr_aucs.append(f_pr_auc)
    cv_bal_accs.append(f_bal)
    cv_mccs.append(f_mcc)
    
    print(f"Fold {fold_idx}: {len(held_out_projs)} projects ({', '.join(held_out_projs[:2])}...) | F1 = {f_f1:.4f}, P = {f_p:.4f}, R = {f_r:.4f}, PR-AUC = {f_pr_auc:.4f}, MCC = {f_mcc:.4f}")
    fold_idx += 1

cv_summary = {
    "mean_f1": float(round(np.mean(cv_f1s), 4)),
    "std_f1": float(round(np.std(cv_f1s), 4)),
    "mean_pr_auc": float(round(np.mean(cv_pr_aucs), 4)),
    "std_pr_auc": float(round(np.std(cv_pr_aucs), 4)),
    "mean_bal_acc": float(round(np.mean(cv_bal_accs), 4)),
    "mean_mcc": float(round(np.mean(cv_mccs), 4))
}
print(f"Grouped CV Summary: Mean F1 = {cv_summary['mean_f1']:.4f} (±{cv_summary['std_f1']:.4f}), Mean PR-AUC = {cv_summary['mean_pr_auc']:.4f} (±{cv_summary['std_pr_auc']:.4f})")

report["grouped_cross_validation"] = {
    "folds": cv_folds,
    "summary": cv_summary
}


# ==============================================================================
# SECTION 5: FEATURE ENGINEERING & TARGETED CALIBRATION
# ==============================================================================
print("\n" + "="*80)
print("SECTION 5: FEATURE ENGINEERING & PROBABILITY CALIBRATION")
print("="*80)

def add_engineered_features(df):
    d = df.copy()
    n_files = d["N_FILES_CHANGED"].fillna(0).values
    l_add = d["LINES_ADDED"].fillna(0).values
    l_rem = d["LINES_REMOVED"].fillna(0).values
    refac = d["N_REFACTORINGS"].fillna(0).values
    
    # 1. Total Churn
    churn_total = l_add + l_rem
    # 2. Churn Density per File
    churn_per_file = churn_total / np.maximum(1, n_files)
    # 3. Addition vs Deletion Ratio
    add_ratio = l_add / np.maximum(1, churn_total)
    # 4. Refactoring Density
    refac_density = refac / np.maximum(1, n_files)
    # 5. Commit Message Length
    msg_len = d["COMMIT_MESSAGE"].fillna("").apply(len).values
    
    feat_matrix = np.column_stack([
        n_files, l_add, l_rem, refac,
        d["N_PARENTS"].fillna(1).values,
        d["IN_MAIN_BRANCH"].fillna(1).values,
        d["AUTHOR_TIMEZONE"].fillna(0).values,
        churn_total, churn_per_file, add_ratio, refac_density, msg_len
    ])
    return feat_matrix

X_tr_eng = add_engineered_features(train_df)
X_val_eng = add_engineered_features(val_df)
X_test_eng = add_engineered_features(test_df)

clf_eng = HistGradientBoostingClassifier(max_iter=150, max_depth=8, class_weight="balanced", random_state=42)
clf_eng.fit(X_tr_eng, y_train)

v_p_eng = clf_eng.predict_proba(X_val_eng)[:, 1]
te_p_eng = clf_eng.predict_proba(X_test_eng)[:, 1]

eng_th, eng_val_f1 = find_best_threshold(y_val, v_p_eng, metric="f1")
te_preds_eng = (te_p_eng >= eng_th).astype(int)

acc_eng = accuracy_score(y_test, te_preds_eng)
bal_eng = balanced_accuracy_score(y_test, te_preds_eng)
p_eng, r_eng, f1_eng, _ = precision_recall_fscore_support(y_test, te_preds_eng, average="binary", zero_division=0)
pr_auc_eng = average_precision_score(y_test, te_p_eng)
roc_auc_eng = roc_auc_score(y_test, te_p_eng)
mcc_eng = matthews_corrcoef(y_test, te_preds_eng)

print(f"Engineered Features (12 features) | Val F1 = {eng_val_f1:.4f} (Th={eng_th:.3f}) | Test F1 = {f1_eng:.4f}, P = {p_eng:.4f}, R = {r_eng:.4f}, PR-AUC = {pr_auc_eng:.4f}, MCC = {mcc_eng:.4f}")

# Probability Calibration using CalibratedClassifierCV
print("\n--- Probability Calibration Study ---")
calibrated_clf = CalibratedClassifierCV(estimator=hgb, method="sigmoid", cv="prefit")
calibrated_clf.fit(X_val_raw, y_val) # calibrate on validation set
cal_te_prob = calibrated_clf.predict_proba(X_test_raw)[:, 1]
brier_before = brier_score_loss(y_test, test_probs)
brier_after = brier_score_loss(y_test, cal_te_prob)
print(f"Brier Score Before Calibration: {brier_before:.4f} -> After Sigmoid Calibration: {brier_after:.4f} (Lower = Better)")

report["feature_engineering"] = {
    "features_added": ["churn_total", "churn_per_file", "add_ratio", "refac_density", "msg_length"],
    "val_threshold": float(round(eng_th, 3)),
    "test_accuracy": float(round(acc_eng, 4)),
    "test_balanced_accuracy": float(round(bal_eng, 4)),
    "test_precision": float(round(p_eng, 4)),
    "test_recall": float(round(r_eng, 4)),
    "test_f1": float(round(f1_eng, 4)),
    "test_pr_auc": float(round(pr_auc_eng, 4)),
    "test_roc_auc": float(round(roc_auc_eng, 4)),
    "test_mcc": float(round(mcc_eng, 4)),
    "brier_score_before": float(round(brier_before, 4)),
    "brier_score_after": float(round(brier_after, 4))
}


# ==============================================================================
# SECTION 6: BOOTSTRAP CONFIDENCE INTERVALS (1,000 RESAMPLES)
# ==============================================================================
print("\n" + "="*80)
print("SECTION 6: BOOTSTRAP 95% CONFIDENCE INTERVALS (1,000 RESAMPLES)")
print("="*80)

rng = np.random.default_rng(42)
n_bootstraps = 1000
boot_f1s = []
boot_pr_aucs = []
boot_roc_aucs = []
boot_bals = []
boot_mccs = []

n_test = len(y_test)
for _ in range(n_bootstraps):
    b_indices = rng.integers(0, n_test, n_test)
    y_b = y_test[b_indices]
    prob_b = test_probs[b_indices]
    pred_b = test_preds[b_indices]
    
    if np.sum(y_b) == 0:
        continue
    
    _, _, f1_b, _ = precision_recall_fscore_support(y_b, pred_b, average="binary", zero_division=0)
    pr_auc_b = average_precision_score(y_b, prob_b)
    roc_auc_b = roc_auc_score(y_b, prob_b) if len(np.unique(y_b)) > 1 else 0.5
    bal_b = balanced_accuracy_score(y_b, pred_b)
    mcc_b = matthews_corrcoef(y_b, pred_b)
    
    boot_f1s.append(f1_b)
    boot_pr_aucs.append(pr_auc_b)
    boot_roc_aucs.append(roc_auc_b)
    boot_bals.append(bal_b)
    boot_mccs.append(mcc_b)

def get_ci(arr):
    return (float(round(np.percentile(arr, 2.5), 4)), float(round(np.percentile(arr, 97.5), 4)))

ci_f1 = get_ci(boot_f1s)
ci_pr_auc = get_ci(boot_pr_aucs)
ci_roc_auc = get_ci(boot_roc_aucs)
ci_bal = get_ci(boot_bals)
ci_mcc = get_ci(boot_mccs)

print(f"Test F1 95% CI:        [{ci_f1[0]:.4f}, {ci_f1[1]:.4f}] (Mean = {np.mean(boot_f1s):.4f})")
print(f"Test PR-AUC 95% CI:    [{ci_pr_auc[0]:.4f}, {ci_pr_auc[1]:.4f}] (Mean = {np.mean(boot_pr_aucs):.4f})")
print(f"Test ROC-AUC 95% CI:   [{ci_roc_auc[0]:.4f}, {ci_roc_auc[1]:.4f}] (Mean = {np.mean(boot_roc_aucs):.4f})")
print(f"Test BalAcc 95% CI:    [{ci_bal[0]:.4f}, {ci_bal[1]:.4f}] (Mean = {np.mean(boot_bals):.4f})")
print(f"Test MCC 95% CI:       [{ci_mcc[0]:.4f}, {ci_mcc[1]:.4f}] (Mean = {np.mean(boot_mccs):.4f})")

report["bootstrap_confidence_intervals"] = {
    "n_bootstraps": n_bootstraps,
    "f1_ci_95": ci_f1,
    "pr_auc_ci_95": ci_pr_auc,
    "roc_auc_ci_95": ci_roc_auc,
    "balanced_accuracy_ci_95": ci_bal,
    "mcc_ci_95": ci_mcc
}

out_report_file = os.path.join(OUT_DIR, "td_error_analysis_report.json")
with open(out_report_file, "w") as f:
    json.dump(report, f, indent=2)

print("\n" + "="*80)
print(f"Comprehensive error analysis complete! Full report written to: {out_report_file}")
print("="*80)
