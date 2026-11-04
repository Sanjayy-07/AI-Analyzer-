"""
research.experiments.export_gate4_predictions
=============================================
Trains the frozen SUB-1__XGBoost model on the 16 training projects (107,582 commits)
and generates raw baseline probability predictions for all 9 authorized validation projects:
- org.apache:batik
- org.apache:codec
- org.apache:commons-exec (zero-defect project)
- org.apache:commons-fileupload
- org.apache:commons-jelly
- org.apache:daemon
- org.apache:digester
- org.apache:httpclient
- org.apache:httpcore

Strict Quarantine: Asserts ZERO access to the 6 quarantined test projects.
Exports both CSV and JSON formats to research/data/ for Java processing.
"""

import os
import sys
import json
import csv
import time
import pandas as pd
import numpy as np
from xgboost import XGBClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer

sys.path.insert(0, os.path.abspath("."))
from research.features.intent_nlp import extract_intent_features
from research.features.process_features import extract_process_features
from research.features.historical_features import compute_strict_historical_features
from research.features.channel_extractor import assemble_tabular_features
from research.utils.checkpoint import atomic_save_json

QUARANTINED_TEST_PROJECTS = [
    "org.apache:archiva",
    "org.apache:collections",
    "org.apache:commons-cli",
    "org.apache:dbcp",
    "org.apache:santuario",
    "org.apache:validator"
]

AUTHORIZED_VALIDATION_PROJECTS = [
    "org.apache:batik",
    "org.apache:codec",
    "org.apache:commons-exec",
    "org.apache:commons-fileupload",
    "org.apache:commons-jelly",
    "org.apache:daemon",
    "org.apache:digester",
    "org.apache:httpclient",
    "org.apache:httpcore"
]

def main():
    print("=" * 80)
    print("PHASE 3A GATE 4: EXPORTING NINE-PROJECT BASELINE PREDICTIONS (SUB-1__XGBoost)")
    print("=" * 80)
    
    t0 = time.time()
    train_df = pd.read_csv("dataset-extracted/TD dataset/train.csv")
    val_df = pd.read_csv("dataset-extracted/TD dataset/validation.csv")
    
    # Assert strict quarantine
    val_projects = set(val_df["PROJECT_ID"].unique())
    train_projects = set(train_df["PROJECT_ID"].unique())
    test_set = set(QUARANTINED_TEST_PROJECTS)
    
    assert val_projects.isdisjoint(test_set), "CRITICAL LEAKAGE: Validation set contains quarantined test projects!"
    assert train_projects.isdisjoint(test_set), "CRITICAL LEAKAGE: Training set contains quarantined test projects!"
    assert val_projects == set(AUTHORIZED_VALIDATION_PROJECTS), f"Validation projects mismatch: {val_projects}"
    
    print(f"Quarantine Verified: 6 test projects strictly quarantined (ZERO access confirmed).")
    print(f"Loaded {len(train_df):,} Train Commits (16 Projects, Defect Rate: {train_df['IS_FAULT_INDUCING'].mean():.2%})")
    print(f"Loaded {len(val_df):,} Validation Commits (9 Projects, Defect Rate: {val_df['IS_FAULT_INDUCING'].mean():.2%})")
    
    # Feature extraction (SUB-1: CH-1 + CH-2 + CH-3 + CH-5C, 43 features)
    print("\nExtracting Process, Intent, and 90d Delayed Historical Features ...")
    t_feat = time.time()
    intent_tr = extract_intent_features(train_df["COMMIT_MESSAGE"])
    proc_tr = extract_process_features(train_df, intent_df=intent_tr)
    hist_tr = compute_strict_historical_features(train_df, delay_days=90.0)
    
    intent_va = extract_intent_features(val_df["COMMIT_MESSAGE"])
    proc_va = extract_process_features(val_df, intent_df=intent_va)
    hist_va = compute_strict_historical_features(val_df, delay_days=90.0)
    print(f"Feature extraction completed in {time.time() - t_feat:.2f}s")
    
    sub1_channels = ["CH-1", "CH-2", "CH-3", "CH-5C"]
    X_tr = assemble_tabular_features(proc_tr, intent_tr, hist_tr, sub1_channels)
    X_va = assemble_tabular_features(proc_va, intent_va, hist_va, sub1_channels)
    
    assert X_tr.shape[1] == 43, f"Expected 43 features, got {X_tr.shape[1]}"
    assert X_va.shape[1] == 43, f"Expected 43 features, got {X_va.shape[1]}"
    
    y_tr = train_df["IS_FAULT_INDUCING"].values.astype(int)
    
    imputer = SimpleImputer(strategy="constant", fill_value=0)
    scaler = StandardScaler()
    X_tr_proc = scaler.fit_transform(imputer.fit_transform(X_tr))
    X_va_proc = scaler.transform(imputer.transform(X_va))
    
    print("\nFitting Frozen SUB-1__XGBoost on 107,582 train commits ...")
    model = XGBClassifier(n_estimators=100, max_depth=5, learning_rate=0.08, tree_method="hist", n_jobs=4, random_state=42)
    model.fit(X_tr_proc, y_tr)
    
    print("Generating validation probability predictions across 23,321 commits ...")
    p_va = model.predict_proba(X_va_proc)[:, 1]
    
    val_df["BASELINE_PROBABILITY"] = p_va
    val_df["TIMESTAMP_NANOS"] = pd.to_datetime(val_df["COMMITTER_DATE"], utc=True).astype("int64")
    
    # Save CSV format
    csv_out_path = "research/data/gate4_raw_predictions.csv"
    os.makedirs("research/data", exist_ok=True)
    with open(csv_out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["project_id", "original_index", "commit_id", "timestamp_nanos", "committer_date", "is_fault_inducing", "baseline_probability"])
        for idx, row in val_df.iterrows():
            writer.writerow([
                row["PROJECT_ID"],
                idx,
                row["COMMIT_HASH"],
                row["TIMESTAMP_NANOS"],
                row["COMMITTER_DATE"],
                int(row["IS_FAULT_INDUCING"]),
                float(row["BASELINE_PROBABILITY"])
            ])
            
    # Save JSON format
    records_by_project = {}
    print("\nValidation Project Breakdown:")
    for p_id in AUTHORIZED_VALIDATION_PROJECTS:
        p_df = val_df[val_df["PROJECT_ID"] == p_id].sort_values("COMMITTER_DATE").reset_index(drop=True)
        records = []
        for idx, row in p_df.iterrows():
            records.append({
                "original_index": int(idx),
                "commit_id": str(row["COMMIT_HASH"]),
                "timestamp_nanos": int(row["TIMESTAMP_NANOS"]),
                "committer_date": str(row["COMMITTER_DATE"]),
                "is_fault_inducing": int(row["IS_FAULT_INDUCING"]),
                "baseline_probability": float(row["BASELINE_PROBABILITY"])
            })
        records_by_project[p_id] = records
        faults = int(p_df["IS_FAULT_INDUCING"].sum())
        rate = p_df["IS_FAULT_INDUCING"].mean()
        print(f"  {p_id:35s}: {len(records):5d} commits | {faults:4d} faults ({rate:6.2%})")
        
    output_json = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "source_training_commits": len(train_df),
        "source_training_prevalence": float(np.mean(y_tr)),
        "frozen_training_threshold_f1": 0.170,
        "feature_dimensions": 43,
        "validation_projects": records_by_project
    }
    
    json_out_path = "research/data/gate4_raw_predictions.json"
    atomic_save_json(json_out_path, output_json)
    
    print(f"\n[OK] Gate 4 predictions exported successfully:")
    print(f"  CSV:  {csv_out_path} ({os.path.getsize(csv_out_path):,} bytes)")
    print(f"  JSON: {json_out_path} ({os.path.getsize(json_out_path):,} bytes)")
    print(f"  Total Elapsed Time: {time.time() - t0:.2f}s")

if __name__ == "__main__":
    main()
