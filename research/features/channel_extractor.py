"""
research.features.channel_extractor
===================================
Explicit feature channel partitioning and combination for Phase 2 ablation experiments.
Guarantees clean separation of:
- CH-1: Pure Churn Volume
- CH-2: Process & Code Structure
- CH-3: Developer Intent Mining
- CH-4: Sub-word & Semantic NLP (fitted fold-internally)
- CH-5A: Zero-Label Author Experience (0% defect labels used)
- CH-5B: Delayed Defect Feedback (simulated 90-day bug reporting lag)
- CH-5C: Combined Author History (CH-5A + CH-5B)
"""

from typing import List, Dict, Tuple, Set
import numpy as np
import pandas as pd

CH_1_COLS = [
    "LINES_ADDED", "LINES_REMOVED", "net_churn", "total_churn",
    "churn_ratio", "deletion_ratio"
]

CH_2_COLS = [
    "N_FILES_CHANGED", "N_REFACTORINGS", "N_PARENTS", "IN_MAIN_BRANCH",
    "AUTHOR_TIMEZONE", "churn_per_file", "lines_added_per_file",
    "lines_removed_per_file", "refactorings_per_file", "refactorings_per_kloc",
    "is_pure_refactoring", "commit_hour", "commit_dayofweek",
    "is_weekend", "is_off_hours"
]

CH_3_COLS = [
    "is_bug_fix_intent", "is_refactoring_intent", "has_ticket_reference",
    "is_maintenance_intent", "is_dependency_arch_intent", "msg_char_length",
    "msg_word_count", "msg_line_count", "msg_type_token_ratio",
    "msg_uppercase_ratio", "msg_digit_ratio", "bugfix_churn_interaction",
    "refactor_churn_interaction", "ticket_file_interaction"
]

CH_5A_COLS = [
    "author_prior_commits", "author_tenure_days", "author_velocity_30d",
    "project_prior_commits"
]

CH_5B_COLS = [
    "author_prior_faults_delayed", "author_fault_rate_delayed",
    "project_prior_faults_delayed", "project_prior_fault_rate_delayed"
]

CH_5C_COLS = CH_5A_COLS + CH_5B_COLS

CHANNEL_DEFINITIONS = {
    "CH-1": CH_1_COLS,
    "CH-2": CH_2_COLS,
    "CH-3": CH_3_COLS,
    "CH-5A": CH_5A_COLS,
    "CH-5B": CH_5B_COLS,
    "CH-5C": CH_5C_COLS
}

def verify_channel_disjointness():
    """
    Verifies that all tabular channels (CH-1, CH-2, CH-3, CH-5A, CH-5B)
    are 100% pairwise disjoint with zero shared column names.
    """
    base_channels = ["CH-1", "CH-2", "CH-3", "CH-5A", "CH-5B"]
    for i in range(len(base_channels)):
        for j in range(i + 1, len(base_channels)):
            c1, c2 = base_channels[i], base_channels[j]
            s1 = set(CHANNEL_DEFINITIONS[c1])
            s2 = set(CHANNEL_DEFINITIONS[c2])
            overlap = s1.intersection(s2)
            assert not overlap, f"Channel overlap detected between {c1} and {c2}: {overlap}"
    return True

def assemble_tabular_features(
    process_df: pd.DataFrame,
    intent_df: pd.DataFrame,
    historical_df: pd.DataFrame,
    channel_keys: List[str]
) -> np.ndarray:
    """
    Assembles a unified feature matrix from requested channel keys.
    Note: CH-4 (NLP SVD) is handled fold-internally via FoldFeaturePipeline.
    """
    # Combine feature sources
    full_df = pd.concat([process_df, intent_df, historical_df], axis=1)
    
    selected_cols = []
    for key in channel_keys:
        if key == "CH-4":
            continue
        if key in CHANNEL_DEFINITIONS:
            selected_cols.extend(CHANNEL_DEFINITIONS[key])
        else:
            raise ValueError(f"Unknown channel key: {key}")
            
    # Deduplicate while preserving order (e.g. if CH-5C and CH-5A are passed)
    seen = set()
    dedup_cols = []
    for col in selected_cols:
        if col not in seen:
            seen.add(col)
            dedup_cols.append(col)
            
    return full_df[dedup_cols].values.astype(np.float32)
