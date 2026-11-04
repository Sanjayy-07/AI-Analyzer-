"""
research.features.historical_features
=====================================
Strict chronological author and project history feature generator.
Implements the refined tripartite author architecture:
- CH-5A: Zero-label experience metrics (prior commits, tenure, velocity, project commit seq).
         Uses 0% defect information; strictly causal at commit time.
- CH-5B: Delayed defect feedback metrics (prior faults, smoothed fault rate) with simulated
         operational bug-discovery delay (default 90 days).
         Prior commit at T_j only contributes fault label if T_j + delay < T_curr.
- CH-5C: Combined author history (Union of CH-5A and CH-5B).

Zero Future Contamination:
- Same-timestamp tie handling: All commits with identical timestamps form an atomic batch
  and never see each other's commits or defect labels.
"""

from collections import defaultdict, deque
import numpy as np
import pandas as pd

CH_5A_FEATURE_NAMES = [
    "author_prior_commits",
    "author_tenure_days",
    "author_velocity_30d",
    "project_prior_commits"
]

CH_5B_FEATURE_NAMES = [
    "author_prior_faults_delayed",
    "author_fault_rate_delayed",
    "project_prior_faults_delayed",
    "project_prior_fault_rate_delayed"
]

CH_5C_FEATURE_NAMES = CH_5A_FEATURE_NAMES + CH_5B_FEATURE_NAMES
# Backward-compatibility alias
HISTORICAL_FEATURE_NAMES = CH_5C_FEATURE_NAMES

def compute_strict_historical_features(
    df: pd.DataFrame,
    bayes_prior_commits: float = 10.0,
    bayes_prior_faults: float = 1.0,
    velocity_window_days: float = 30.0,
    delay_days: float = 90.0
) -> pd.DataFrame:
    """
    Computes strict chronological author and project metrics with simulated
    operational feedback delay.
    Preserves input DataFrame index.
    """
    n = len(df)
    original_index = df.index
    
    # Required columns
    for col in ["PROJECT_ID", "COMMITTER_DATE", "IS_FAULT_INDUCING"]:
        if col not in df.columns:
            raise ValueError(f"Missing required column: {col}")
            
    authors = df["AUTHOR"].fillna("Unknown").astype(str).values
    projects = df["PROJECT_ID"].astype(str).values
    targets = df["IS_FAULT_INDUCING"].astype(int).values
    
    # Parse timestamps
    ts_series = pd.to_datetime(df["COMMITTER_DATE"], utc=True)
    timestamps = ts_series.astype("int64").values
    
    # Preallocate output arrays for CH-5A (Zero-label experience)
    out_auth_commits = np.zeros(n, dtype=np.float32)
    out_auth_tenure = np.zeros(n, dtype=np.float32)
    out_auth_velocity = np.zeros(n, dtype=np.float32)
    out_proj_commits = np.zeros(n, dtype=np.float32)
    
    # Preallocate output arrays for CH-5B (Delayed defect feedback)
    out_auth_faults = np.zeros(n, dtype=np.float32)
    out_auth_rate = np.zeros(n, dtype=np.float32)
    out_proj_faults = np.zeros(n, dtype=np.float32)
    out_proj_rate = np.zeros(n, dtype=np.float32)
    
    # Sort indices by project then timestamp
    order = np.lexsort((timestamps, projects))
    
    nanos_in_day = int(86_400 * 1e9)
    velocity_window_ns = int(velocity_window_days * nanos_in_day)
    delay_ns = int(delay_days * nanos_in_day)
    
    curr_proj = None
    proj_start = 0
    
    def process_project(proj_indices):
        auth_commits = defaultdict(int)
        auth_first_seen = {}
        auth_recent_timestamps = defaultdict(deque)
        
        # Pending defect queue for delayed feedback: deque of (avail_timestamp, author, is_fault)
        pending_faults_queue = deque()
        auth_known_faults = defaultdict(int)
        auth_known_commits_for_rate = defaultdict(int)
        
        proj_commits_total = 0
        proj_known_faults_total = 0
        proj_known_commits_for_rate = 0
        
        p_len = len(proj_indices)
        i = 0
        while i < p_len:
            j = i
            t_curr = timestamps[proj_indices[i]]
            while j < p_len and timestamps[proj_indices[j]] == t_curr:
                j += 1
                
            batch_indices = proj_indices[i:j]
            
            # Step 0: Flush any pending defect outcomes whose delay has elapsed:
            # i.e., commit occurred at T_commit, outcome becomes available at T_commit + delay_ns <= t_curr
            while pending_faults_queue and pending_faults_queue[0][0] <= t_curr:
                _, a_hist, y_hist = pending_faults_queue.popleft()
                auth_known_faults[a_hist] += y_hist
                auth_known_commits_for_rate[a_hist] += 1
                proj_known_faults_total += y_hist
                proj_known_commits_for_rate += 1
                
            # Step 1: Query prior state for all items in batch
            for idx in batch_indices:
                a = authors[idx]
                c = auth_commits[a]
                
                # CH-5A: Zero-label metrics
                out_auth_commits[idx] = float(c)
                out_proj_commits[idx] = float(proj_commits_total)
                
                if a in auth_first_seen:
                    tenure_ns = t_curr - auth_first_seen[a]
                    out_auth_tenure[idx] = max(0.0, float(tenure_ns / nanos_in_day))
                else:
                    out_auth_tenure[idx] = 0.0
                    
                dq = auth_recent_timestamps[a]
                cutoff = t_curr - velocity_window_ns
                while dq and dq[0] < cutoff:
                    dq.popleft()
                out_auth_velocity[idx] = float(len(dq))
                
                # CH-5B: Delayed defect feedback metrics
                known_f = auth_known_faults[a]
                known_c = auth_known_commits_for_rate[a]
                out_auth_faults[idx] = float(known_f)
                out_auth_rate[idx] = (known_f + bayes_prior_faults) / (known_c + bayes_prior_commits)
                
                out_proj_faults[idx] = float(proj_known_faults_total)
                out_proj_rate[idx] = (proj_known_faults_total + bayes_prior_faults) / (proj_known_commits_for_rate + bayes_prior_commits)
                
            # Step 2: Update state after all batch items have been queried
            for idx in batch_indices:
                a = authors[idx]
                y = targets[idx]
                
                if a not in auth_first_seen:
                    auth_first_seen[a] = t_curr
                    
                auth_commits[a] += 1
                auth_recent_timestamps[a].append(t_curr)
                proj_commits_total += 1
                
                # Schedule defect outcome to become available at t_curr + delay_ns
                pending_faults_queue.append((t_curr + delay_ns, a, y))
                
            i = j
            
    for i in range(n):
        idx = order[i]
        p = projects[idx]
        if curr_proj is None:
            curr_proj = p
            proj_start = i
        elif p != curr_proj:
            process_project(order[proj_start:i])
            curr_proj = p
            proj_start = i
    if proj_start < n:
        process_project(order[proj_start:n])
        
    return pd.DataFrame({
        # CH-5A (Zero-label experience)
        "author_prior_commits": out_auth_commits,
        "author_tenure_days": out_auth_tenure,
        "author_velocity_30d": out_auth_velocity,
        "project_prior_commits": out_proj_commits,
        # CH-5B (Delayed defect feedback)
        "author_prior_faults_delayed": out_auth_faults,
        "author_fault_rate_delayed": out_auth_rate,
        "project_prior_faults_delayed": out_proj_faults,
        "project_prior_fault_rate_delayed": out_proj_rate,
        # Backward compatibility aliases
        "author_prior_faults": out_auth_faults,
        "author_fault_rate": out_auth_rate,
        "project_prior_fault_rate": out_proj_rate
    }, index=original_index)
