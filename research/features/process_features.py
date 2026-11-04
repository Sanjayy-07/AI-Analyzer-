"""
research.features.process_features
==================================
Deterministic, row-wise process, churn ratio, refactoring density,
temporal dynamics, and interaction feature extraction.
Zero learned parameters: purely row-level calculation with zero leakage.
"""

import numpy as np
import pandas as pd

PROCESS_FEATURE_NAMES = [
    # Base raw features (imputed cleanly)
    "N_FILES_CHANGED",
    "LINES_ADDED",
    "LINES_REMOVED",
    "N_REFACTORINGS",
    "N_PARENTS",
    "IN_MAIN_BRANCH",
    "AUTHOR_TIMEZONE",
    # Advanced churn metrics
    "net_churn",
    "total_churn",
    "churn_ratio",
    "deletion_ratio",
    "churn_per_file",
    "lines_added_per_file",
    "lines_removed_per_file",
    # Refactoring metrics
    "refactorings_per_file",
    "refactorings_per_kloc",
    "is_pure_refactoring",
    # Temporal patterns
    "commit_hour",
    "commit_dayofweek",
    "is_weekend",
    "is_off_hours",
    # Semantic-process interactions
    "bugfix_churn_interaction",
    "refactor_churn_interaction",
    "ticket_file_interaction"
]

def extract_process_features(df: pd.DataFrame, intent_df: pd.DataFrame = None) -> pd.DataFrame:
    """
    Extracts deterministic process, churn, refactoring, temporal,
    and interaction features from raw commit metadata.
    """
    n = len(df)
    
    # Base columns with safe fillna(0) for missing churn counts
    n_files = df["N_FILES_CHANGED"].fillna(0).astype(np.float32).values
    l_added = df["LINES_ADDED"].fillna(0).astype(np.float32).values
    l_removed = df["LINES_REMOVED"].fillna(0).astype(np.float32).values
    n_refactor = df["N_REFACTORINGS"].fillna(0).astype(np.float32).values
    n_parents = df["N_PARENTS"].fillna(1).astype(np.float32).values
    in_main = df["IN_MAIN_BRANCH"].fillna(1).astype(np.float32).values
    tz = df["AUTHOR_TIMEZONE"].fillna(0).astype(np.float32).values
    
    # Advanced churn metrics
    net_churn = l_added - l_removed
    total_churn = l_added + l_removed
    churn_ratio = l_added / (l_removed + 1.0)
    deletion_ratio = l_removed / (l_added + 1.0)
    
    eps = 1e-5
    file_denom = np.maximum(n_files, eps)
    churn_per_file = total_churn / file_denom
    added_per_file = l_added / file_denom
    removed_per_file = l_removed / file_denom
    
    # Refactoring metrics
    refactor_per_file = n_refactor / file_denom
    refactor_per_kloc = n_refactor / ((total_churn / 1000.0) + eps)
    is_pure_refactor = ((n_refactor > 0) & (l_added <= 5)).astype(np.float32)
    
    # Temporal metrics from AUTHOR_DATE
    if "AUTHOR_DATE" in df.columns:
        dt = pd.to_datetime(df["AUTHOR_DATE"], utc=True)
        commit_hour = dt.dt.hour.astype(np.float32).values
        commit_dow = dt.dt.dayofweek.astype(np.float32).values
        is_weekend = (commit_dow >= 5).astype(np.float32)
        is_off_hours = ((commit_hour < 6) | (commit_hour >= 20)).astype(np.float32)
    else:
        commit_hour = np.zeros(n, dtype=np.float32)
        commit_dow = np.zeros(n, dtype=np.float32)
        is_weekend = np.zeros(n, dtype=np.float32)
        is_off_hours = np.zeros(n, dtype=np.float32)
        
    # Interaction terms with intent features
    if intent_df is not None:
        is_bug = intent_df["is_bug_fix_intent"].values
        is_refactor_intent = intent_df["is_refactoring_intent"].values
        has_ticket = intent_df["has_ticket_reference"].values
    else:
        is_bug = np.zeros(n, dtype=np.float32)
        is_refactor_intent = np.zeros(n, dtype=np.float32)
        has_ticket = np.zeros(n, dtype=np.float32)
        
    log_churn = np.log1p(np.maximum(0, total_churn))
    bugfix_churn_inter = (is_bug * log_churn).astype(np.float32)
    refactor_churn_inter = (is_refactor_intent * log_churn).astype(np.float32)
    ticket_file_inter = (has_ticket * n_files).astype(np.float32)
    
    out_dict = {
        "N_FILES_CHANGED": n_files,
        "LINES_ADDED": l_added,
        "LINES_REMOVED": l_removed,
        "N_REFACTORINGS": n_refactor,
        "N_PARENTS": n_parents,
        "IN_MAIN_BRANCH": in_main,
        "AUTHOR_TIMEZONE": tz,
        "net_churn": net_churn,
        "total_churn": total_churn,
        "churn_ratio": churn_ratio,
        "deletion_ratio": deletion_ratio,
        "churn_per_file": churn_per_file,
        "lines_added_per_file": added_per_file,
        "lines_removed_per_file": removed_per_file,
        "refactorings_per_file": refactor_per_file,
        "refactorings_per_kloc": refactor_per_kloc,
        "is_pure_refactoring": is_pure_refactor,
        "commit_hour": commit_hour,
        "commit_dayofweek": commit_dow,
        "is_weekend": is_weekend,
        "is_off_hours": is_off_hours,
        "bugfix_churn_interaction": bugfix_churn_inter,
        "refactor_churn_interaction": refactor_churn_inter,
        "ticket_file_interaction": ticket_file_inter
    }
    
    return pd.DataFrame(out_dict, index=df.index)
