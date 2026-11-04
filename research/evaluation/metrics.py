"""
research.evaluation.metrics
===========================
Comprehensive metric calculation for Just-In-Time defect prediction.
Calculates Accuracy, Balanced Accuracy, Precision, Recall, F1-Score,
PR-AUC (Average Precision), ROC-AUC, Matthews Correlation Coefficient (MCC),
and Brier Score Loss.
"""

from typing import Dict, Any
import numpy as np
from sklearn.metrics import (
    accuracy_score,
    balanced_accuracy_score,
    precision_recall_fscore_support,
    average_precision_score,
    roc_auc_score,
    matthews_corrcoef,
    brier_score_loss
)

def compute_expected_calibration_error(
    y_true: np.ndarray,
    y_proba: np.ndarray,
    n_bins: int = 10
) -> float:
    """
    Computes Expected Calibration Error (ECE) across n_bins equal-width bins:
    ECE = sum_m (|B_m| / N) * |acc(B_m) - conf(B_m)|
    """
    bin_edges = np.linspace(0.0, 1.0, n_bins + 1)
    n = len(y_true)
    if n == 0:
        return 0.0
        
    ece = 0.0
    for i in range(n_bins):
        low, high = bin_edges[i], bin_edges[i + 1]
        if i == n_bins - 1:
            mask = (y_proba >= low) & (y_proba <= high)
        else:
            mask = (y_proba >= low) & (y_proba < high)
            
        bin_count = np.sum(mask)
        if bin_count > 0:
            bin_acc = np.mean(y_true[mask])
            bin_conf = np.mean(y_proba[mask])
            ece += (bin_count / n) * abs(bin_acc - bin_conf)
            
    return float(ece)

def evaluate_predictions(
    y_true: np.ndarray,
    y_proba: np.ndarray,
    threshold: float = 0.5
) -> Dict[str, float]:
    """
    Evaluates probability predictions against ground-truth binary labels.
    Calculates Accuracy, Balanced Accuracy, Precision, Recall, F1,
    PR-AUC, ROC-AUC, MCC, Brier Score, and Expected Calibration Error (ECE).
    """
    y_true = np.asarray(y_true).astype(int)
    y_proba = np.asarray(y_proba).astype(float)
    y_pred = (y_proba >= threshold).astype(int)
    
    acc = accuracy_score(y_true, y_pred)
    bal_acc = balanced_accuracy_score(y_true, y_pred)
    p, r, f1, _ = precision_recall_fscore_support(
        y_true, y_pred, average="binary", zero_division=0
    )
    
    # Handle single-class edge cases in PR-AUC / ROC-AUC
    if len(np.unique(y_true)) > 1:
        pr_auc = average_precision_score(y_true, y_proba)
        roc_auc = roc_auc_score(y_true, y_proba)
    else:
        pr_auc = float(np.mean(y_true))
        roc_auc = 0.5
        
    mcc = matthews_corrcoef(y_true, y_pred)
    brier = brier_score_loss(y_true, y_proba)
    ece = compute_expected_calibration_error(y_true, y_proba, n_bins=10)
    
    return {
        "threshold": float(threshold),
        "accuracy": float(acc),
        "balanced_accuracy": float(bal_acc),
        "precision": float(p),
        "recall": float(r),
        "f1": float(f1),
        "pr_auc": float(pr_auc),
        "roc_auc": float(roc_auc),
        "mcc": float(mcc),
        "brier": float(brier),
        "ece": float(ece)
    }

def find_best_threshold_val(
    y_true: np.ndarray,
    y_proba: np.ndarray,
    metric: str = "f1",
    num_thresholds: int = 91
) -> float:
    """
    Finds optimal decision threshold on validation data.
    """
    best_th = 0.5
    best_val = -1.0
    for th in np.linspace(0.05, 0.95, num_thresholds):
        preds = (y_proba >= th).astype(int)
        if metric == "f1":
            _, _, sc, _ = precision_recall_fscore_support(
                y_true, preds, average="binary", zero_division=0
            )
        elif metric == "mcc":
            sc = matthews_corrcoef(y_true, preds)
        elif metric == "bal_acc":
            sc = balanced_accuracy_score(y_true, preds)
        else:
            raise ValueError(f"Unknown metric: {metric}")
            
        if sc > best_val:
            best_val = sc
            best_th = th
            
    return float(best_th)
