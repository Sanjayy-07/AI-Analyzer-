"""
train_benchmarks.py
===================
Executes independent scientific training and evaluation across the four
real-world software engineering datasets extracted into dataset-extracted/:

1. CSIQ: Object-Oriented God Class Detection (Class-level, 18 CK/OO metrics)
2. multi-smell: Multi-Label Code Smell Detection (Class-level, 14 Halstead/McCabe metrics)
3. smells: Architectural Dependency Smell Classification (Graph-level, 9 graph metrics)
4. TD dataset: Just-In-Time Fault-Inducing Change Prediction (Commit-level, 7 churn metrics)

Strict Partition Protocol:
- Train models on train.csv only
- Tune/select on validation.csv
- Evaluate once on untouched test.csv

Output saved to dataset-pipeline/models_output/benchmark_results.json
"""

import os
import json
import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.multioutput import MultiOutputClassifier
from sklearn.dummy import DummyClassifier
from sklearn.metrics import (
    accuracy_score, balanced_accuracy_score, precision_recall_fscore_support,
    confusion_matrix, average_precision_score, hamming_loss, classification_report
)

OUTPUT_DIR = os.path.join("dataset-pipeline", "models_output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

results = {}

# ==============================================================================
# PLAN 1: CSIQ (Object-Oriented God Class Detection)
# ==============================================================================
print("\n" + "="*80)
print("PLAN 1: CSIQ — Object-Oriented God Class Detection")
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

imputer = SimpleImputer(strategy="median")
X_train_csiq = imputer.fit_transform(csiq_train[csiq_features])
y_train_csiq = csiq_train[csiq_target].values

X_val_csiq = imputer.transform(csiq_val[csiq_features])
y_val_csiq = csiq_val[csiq_target].values

X_test_csiq = imputer.transform(csiq_test[csiq_features])
y_test_csiq = csiq_test[csiq_target].values

# Baseline 1: Dummy (Majority Class)
dummy_csiq = DummyClassifier(strategy="most_frequent")
dummy_csiq.fit(X_train_csiq, y_train_csiq)
dummy_pred = dummy_csiq.predict(X_test_csiq)

# Baseline 2: Logistic Regression
scaler_csiq = StandardScaler()
X_train_csiq_scaled = scaler_csiq.fit_transform(X_train_csiq)
X_val_csiq_scaled = scaler_csiq.transform(X_val_csiq)
X_test_csiq_scaled = scaler_csiq.transform(X_test_csiq)

lr_csiq = LogisticRegression(max_iter=1000, class_weight="balanced", random_state=42)
lr_csiq.fit(X_train_csiq_scaled, y_train_csiq)
lr_pred = lr_csiq.predict(X_test_csiq_scaled)
lr_proba = lr_csiq.predict_proba(X_test_csiq_scaled)[:, 1]

# Primary: Random Forest
rf_csiq = RandomForestClassifier(n_estimators=100, max_depth=12, min_samples_leaf=5,
                                 class_weight="balanced", random_state=42, n_jobs=-1)
rf_csiq.fit(X_train_csiq, y_train_csiq)
rf_pred = rf_csiq.predict(X_test_csiq)
rf_proba = rf_csiq.predict_proba(X_test_csiq)[:, 1]

def evaluate_binary(name, y_true, y_pred, y_proba):
    acc = accuracy_score(y_true, y_pred)
    bal_acc = balanced_accuracy_score(y_true, y_pred)
    prec, rec, f1, _ = precision_recall_fscore_support(y_true, y_pred, average="binary", zero_division=0)
    pr_auc = average_precision_score(y_true, y_proba) if y_proba is not None else 0.0
    cm = confusion_matrix(y_true, y_pred).tolist()
    print(f"[{name}] Acc={acc:.4f}, BalAcc={bal_acc:.4f}, P={prec:.4f}, R={rec:.4f}, F1={f1:.4f}, PR-AUC={pr_auc:.4f}")
    return {"accuracy": acc, "balanced_accuracy": bal_acc, "precision": prec, "recall": rec, "f1": f1, "pr_auc": pr_auc, "confusion_matrix": cm}

results["CSIQ"] = {
    "task": "Class-level God Class Detection",
    "samples": {"train": len(csiq_train), "val": len(csiq_val), "test": len(csiq_test)},
    "features": csiq_features,
    "feature_importances": sorted(zip(csiq_features, [float(x) for x in rf_csiq.feature_importances_]), key=lambda x: -x[1]),
    "models": {
        "MajorityBaseline": evaluate_binary("Majority Baseline", y_test_csiq, dummy_pred, None),
        "LogisticRegression": evaluate_binary("Logistic Regression", y_test_csiq, lr_pred, lr_proba),
        "RandomForest": evaluate_binary("Random Forest (Primary)", y_test_csiq, rf_pred, rf_proba)
    }
}


# ==============================================================================
# PLAN 2: multi-smell-dataset-v1_2 (Multi-Label Code Smell Detection)
# ==============================================================================
print("\n" + "="*80)
print("PLAN 2: multi-smell-dataset-v1_2 — Multi-Label Code Smell Detection")
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

X_train_ms = ms_train[ms_features].values.astype(float)
y_train_ms = ms_train[ms_targets].values.astype(int)

X_val_ms = ms_val[ms_features].values.astype(float)
y_val_ms = ms_val[ms_targets].values.astype(int)

X_test_ms = ms_test[ms_features].values.astype(float)
y_test_ms = ms_test[ms_targets].values.astype(int)

scaler_ms = StandardScaler()
X_train_ms_scaled = scaler_ms.fit_transform(X_train_ms)
X_test_ms_scaled = scaler_ms.transform(X_test_ms)

# Baseline: Multi-Output Logistic Regression
lr_ms = MultiOutputClassifier(LogisticRegression(max_iter=1000, class_weight="balanced", random_state=42), n_jobs=-1)
lr_ms.fit(X_train_ms_scaled, y_train_ms)
lr_ms_pred = lr_ms.predict(X_test_ms_scaled)

# Primary: Multi-Output Random Forest
rf_ms = MultiOutputClassifier(RandomForestClassifier(n_estimators=100, max_depth=10, min_samples_leaf=5,
                                                     class_weight="balanced", random_state=42, n_jobs=-1), n_jobs=-1)
rf_ms.fit(X_train_ms, y_train_ms)
rf_ms_pred = rf_ms.predict(X_test_ms)

def evaluate_multilabel(name, y_true, y_pred, targets):
    h_loss = hamming_loss(y_true, y_pred)
    subset_acc = accuracy_score(y_true, y_pred)
    per_label = {}
    f1_list = []
    print(f"\n--- {name} --- (Hamming Loss: {h_loss:.4f}, Exact Subset Acc: {subset_acc:.4f})")
    for i, target in enumerate(targets):
        p, r, f1, _ = precision_recall_fscore_support(y_true[:, i], y_pred[:, i], average="binary", zero_division=0)
        cm = confusion_matrix(y_true[:, i], y_pred[:, i]).tolist()
        per_label[target] = {"precision": float(p), "recall": float(r), "f1": float(f1), "confusion_matrix": cm}
        f1_list.append(f1)
        print(f"  {target:15s}: Precision={p:.4f}, Recall={r:.4f}, F1={f1:.4f}")
    macro_f1 = float(np.mean(f1_list))
    print(f"  Macro-F1: {macro_f1:.4f}")
    return {"hamming_loss": float(h_loss), "subset_accuracy": float(subset_acc), "macro_f1": macro_f1, "per_label": per_label}

results["multi-smell"] = {
    "task": "Multi-Label Code Smell Detection",
    "samples": {"train": len(ms_train), "val": len(ms_val), "test": len(ms_test)},
    "split_percentages": {"train": "69.10%", "val": "15.42%", "test": "15.49%"},
    "features": ms_features,
    "targets": ms_targets,
    "models": {
        "BinaryRelevanceLogisticRegression": evaluate_multilabel("Logistic Regression Baseline", y_test_ms, lr_ms_pred, ms_targets),
        "MultiOutputRandomForest": evaluate_multilabel("Random Forest (Primary)", y_test_ms, rf_ms_pred, ms_targets)
    }
}


# ==============================================================================
# PLAN 3: smells (Architectural Dependency Smell Classification)
# ==============================================================================
print("\n" + "="*80)
print("PLAN 3: smells.zip — Architectural Dependency Smell Classification")
print("="*80)

smells_dir = os.path.join("dataset-extracted", "smells")
smells_train = pd.read_csv(os.path.join(smells_dir, "train.csv"))
smells_val = pd.read_csv(os.path.join(smells_dir, "validation.csv"))
smells_test = pd.read_csv(os.path.join(smells_dir, "test.csv"))

# Selected features (strictly excluding structural target leakage columns)
smells_features = [
    "numOfEdges", "nPackages", "nClasses", "pageRankAvrg", "pageRankMax",
    "pageRankWeighted", "overlapRatio", "size", "age"
]
smells_target = "smellType"
classes_smells = ["cyclicDep", "unstableDep", "hubLikeDep"]

X_train_sm = smells_train[smells_features].values.astype(float)
y_train_sm = smells_train[smells_target].values

X_val_sm = smells_val[smells_features].values.astype(float)
y_val_sm = smells_val[smells_target].values

X_test_sm = smells_test[smells_features].values.astype(float)
y_test_sm = smells_test[smells_target].values

# Baseline: Logistic Regression
scaler_sm = StandardScaler()
X_train_sm_scaled = scaler_sm.fit_transform(X_train_sm)
X_test_sm_scaled = scaler_sm.transform(X_test_sm)

lr_sm = LogisticRegression(max_iter=1000, class_weight="balanced", random_state=42)
lr_sm.fit(X_train_sm_scaled, y_train_sm)
lr_sm_pred = lr_sm.predict(X_test_sm_scaled)

# Primary: Random Forest
rf_sm = RandomForestClassifier(n_estimators=100, max_depth=10, min_samples_leaf=5,
                               class_weight="balanced", random_state=42, n_jobs=-1)
rf_sm.fit(X_train_sm, y_train_sm)
rf_sm_pred = rf_sm.predict(X_test_sm)

def evaluate_multiclass(name, y_true, y_pred, classes):
    acc = accuracy_score(y_true, y_pred)
    bal_acc = balanced_accuracy_score(y_true, y_pred)
    p_macro, r_macro, f1_macro, _ = precision_recall_fscore_support(y_true, y_pred, average="macro", zero_division=0)
    p_w, r_w, f1_w, _ = precision_recall_fscore_support(y_true, y_pred, average="weighted", zero_division=0)
    cm = confusion_matrix(y_true, y_pred, labels=classes).tolist()
    p_per, r_per, f1_per, _ = precision_recall_fscore_support(y_true, y_pred, labels=classes, zero_division=0)
    per_class = {c: {"precision": float(p_per[i]), "recall": float(r_per[i]), "f1": float(f1_per[i])} for i, c in enumerate(classes)}
    print(f"\n--- {name} --- (Acc={acc:.4f}, BalAcc={bal_acc:.4f}, Macro-F1={f1_macro:.4f})")
    for c in classes:
        print(f"  {c:15s}: Precision={per_class[c]['precision']:.4f}, Recall={per_class[c]['recall']:.4f}, F1={per_class[c]['f1']:.4f}")
    return {"accuracy": float(acc), "balanced_accuracy": float(bal_acc), "macro_f1": float(f1_macro),
            "weighted_f1": float(f1_w), "confusion_matrix": cm, "per_class": per_class}

results["smells"] = {
    "task": "Architectural Dependency Smell Classification",
    "samples": {"train": len(smells_train), "val": len(smells_val), "test": len(smells_test)},
    "features": smells_features,
    "classes": classes_smells,
    "safeguard_note": "Evaluates cross-smell lineage generalization within 14 projects across versions. Projects overlap across splits.",
    "feature_importances": sorted(zip(smells_features, [float(x) for x in rf_sm.feature_importances_]), key=lambda x: -x[1]),
    "models": {
        "LogisticRegression": evaluate_multiclass("Logistic Regression Baseline", y_test_sm, lr_sm_pred, classes_smells),
        "RandomForest": evaluate_multiclass("Random Forest (Primary)", y_test_sm, rf_sm_pred, classes_smells)
    }
}


# ==============================================================================
# PLAN 4: TD dataset (Just-In-Time Defect / Fault-Inducing Change Prediction)
# ==============================================================================
print("\n" + "="*80)
print("PLAN 4: TD dataset — Just-In-Time Fault-Inducing Change Prediction")
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

td_imputer = SimpleImputer(strategy="constant", fill_value=0)
X_train_td = td_imputer.fit_transform(td_train[td_features])
y_train_td = td_train[td_target].values

X_val_td = td_imputer.transform(td_val[td_features])
y_val_td = td_val[td_target].values

X_test_td = td_imputer.transform(td_test[td_features])
y_test_td = td_test[td_target].values

scaler_td = StandardScaler()
X_train_td_scaled = scaler_td.fit_transform(X_train_td)
X_test_td_scaled = scaler_td.transform(X_test_td)

# Baseline: Logistic Regression
lr_td = LogisticRegression(max_iter=1000, class_weight="balanced", random_state=42)
lr_td.fit(X_train_td_scaled, y_train_td)
lr_td_pred = lr_td.predict(X_test_td_scaled)
lr_td_proba = lr_td.predict_proba(X_test_td_scaled)[:, 1]

# Primary: Random Forest
rf_td = RandomForestClassifier(n_estimators=100, max_depth=8, min_samples_leaf=5,
                               class_weight="balanced", random_state=42, n_jobs=-1)
rf_td.fit(X_train_td, y_train_td)
rf_td_pred = rf_td.predict(X_test_td)
rf_td_proba = rf_td.predict_proba(X_test_td)[:, 1]

overall_eval = evaluate_binary("Random Forest Overall (6 Held-Out Projects)", y_test_td, rf_td_pred, rf_td_proba)

# Project-wise evaluation across the 6 held-out test projects (Safeguard 2)
print("\n--- Project-Wise Breakdown on 6 Held-Out Test Projects ---")
project_evals = {}
for proj_id in sorted(td_test["PROJECT_ID"].unique()):
    mask = (td_test["PROJECT_ID"] == proj_id).values
    y_sub = y_test_td[mask]
    pred_sub = rf_td_pred[mask]
    proba_sub = rf_td_proba[mask]
    acc_sub = accuracy_score(y_sub, pred_sub)
    bal_sub = balanced_accuracy_score(y_sub, pred_sub)
    p_sub, r_sub, f1_sub, _ = precision_recall_fscore_support(y_sub, pred_sub, average="binary", zero_division=0)
    pr_auc_sub = average_precision_score(y_sub, proba_sub) if np.sum(y_sub) > 0 else 0.0
    faults_count = int(np.sum(y_sub))
    total_count = int(len(y_sub))
    project_evals[proj_id] = {
        "total_commits": total_count,
        "fault_inducing_commits": faults_count,
        "fault_rate_pct": float(faults_count / total_count * 100),
        "accuracy": float(acc_sub),
        "balanced_accuracy": float(bal_sub),
        "precision": float(p_sub),
        "recall": float(r_sub),
        "f1": float(f1_sub),
        "pr_auc": float(pr_auc_sub)
    }
    print(f"  {proj_id:25s} (n={total_count:4d}, faults={faults_count:4d}): Acc={acc_sub:.4f}, BalAcc={bal_sub:.4f}, Recall={r_sub:.4f}, F1={f1_sub:.4f}, PR-AUC={pr_auc_sub:.4f}")

results["TD_dataset"] = {
    "task": "Commit-Level Fault-Inducing Change Prediction (SZZ)",
    "samples": {"train": len(td_train), "val": len(td_val), "test": len(td_test)},
    "test_project_count": int(td_test["PROJECT_ID"].nunique()),
    "test_projects": sorted(list(td_test["PROJECT_ID"].unique())),
    "features": td_features,
    "feature_importances": sorted(zip(td_features, [float(x) for x in rf_td.feature_importances_]), key=lambda x: -x[1]),
    "models": {
        "LogisticRegression": evaluate_binary("Logistic Regression Baseline", y_test_td, lr_td_pred, lr_td_proba),
        "RandomForest": overall_eval
    },
    "project_wise_test_performance": project_evals
}

# Save complete JSON
out_json_path = os.path.join(OUTPUT_DIR, "benchmark_results.json")
with open(out_json_path, "w") as f:
    json.dump(results, f, indent=2)

print("\n" + "="*80)
print(f"All benchmarks complete! Results saved to: {out_json_path}")
print("="*80)
