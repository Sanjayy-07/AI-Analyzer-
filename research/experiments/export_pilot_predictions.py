"""
research.experiments.export_pilot_predictions
=============================================
Trains the frozen SUB-1__XGBoost model on the 16 training projects (107,582 commits)
and generates raw baseline probability predictions for the 3 pilot projects:
- org.apache:batik
- org.apache:digester
- org.apache:httpclient

Saves raw commit records (commit_id, timestamp, ground truth, baseline probability)
to research/data/pilot_raw_predictions.json for consumption by the Java adaptation engine.
"""

import os
import sys
import json
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

def main():
    print("=" * 80)
    print("GENERATING PILOT BASELINE PREDICTIONS (SUB-1__XGBoost)")
    print("=" * 80)
    
    t0 = time.time()
    train_df = pd.read_csv("dataset-extracted/TD dataset/train.csv")
    val_df = pd.read_csv("dataset-extracted/TD dataset/validation.csv")
    
    pilot_projects = ["org.apache:batik", "org.apache:digester", "org.apache:httpclient"]
    val_pilot = val_df[val_df["PROJECT_ID"].isin(pilot_projects)].copy()
    
    print(f"Loaded {len(train_df):,} Train Commits and {len(val_pilot):,} Pilot Validation Commits")
    
    # Feature extraction (SUB-1: CH-1 + CH-2 + CH-3 + CH-5C)
    print("Extracting features ...")
    intent_tr = extract_intent_features(train_df["COMMIT_MESSAGE"])
    proc_tr = extract_process_features(train_df, intent_df=intent_tr)
    hist_tr = compute_strict_historical_features(train_df, delay_days=90.0)
    
    intent_va = extract_intent_features(val_pilot["COMMIT_MESSAGE"])
    proc_va = extract_process_features(val_pilot, intent_df=intent_va)
    hist_va = compute_strict_historical_features(val_pilot, delay_days=90.0)
    
    sub1_channels = ["CH-1", "CH-2", "CH-3", "CH-5C"]
    X_tr = assemble_tabular_features(proc_tr, intent_tr, hist_tr, sub1_channels)
    X_va = assemble_tabular_features(proc_va, intent_va, hist_va, sub1_channels)
    
    y_tr = train_df["IS_FAULT_INDUCING"].values
    
    imputer = SimpleImputer(strategy="constant", fill_value=0)
    scaler = StandardScaler()
    X_tr_proc = scaler.fit_transform(imputer.fit_transform(X_tr))
    X_va_proc = scaler.transform(imputer.transform(X_va))
    
    print("Fitting SUB-1__XGBoost on 107,582 train commits ...")
    model = XGBClassifier(n_estimators=100, max_depth=5, learning_rate=0.08, tree_method="hist", n_jobs=4, random_state=42)
    model.fit(X_tr_proc, y_tr)
    
    print("Inferring baseline validation probabilities ...")
    p_va = model.predict_proba(X_va_proc)[:, 1]
    
    val_pilot["BASELINE_PROBABILITY"] = p_va
    val_pilot["TIMESTAMP_NANOS"] = pd.to_datetime(val_pilot["COMMITTER_DATE"], utc=True).astype("int64")
    
    # Structure records by project
    records_by_project = {}
    for p_id in pilot_projects:
        p_df = val_pilot[val_pilot["PROJECT_ID"] == p_id].sort_values("COMMITTER_DATE").reset_index(drop=True)
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
        print(f"  {p_id}: {len(records):,} commits | Defect Rate: {p_df['IS_FAULT_INDUCING'].mean():.2%}")
        
    output = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "source_training_prevalence": float(np.mean(y_tr)),
        "frozen_training_threshold_f1": 0.170,
        "pilot_projects": records_by_project
    }
    
    out_path = "research/data/pilot_raw_predictions.json"
    atomic_save_json(out_path, output)
    print(f"\n[OK] Pilot baseline predictions exported to {out_path} in {time.time() - t0:.2f}s")

if __name__ == "__main__":
    main()
