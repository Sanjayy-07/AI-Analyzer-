"""
research.evaluation.nested_threshold
====================================
Unbiased nested threshold selection and evaluation.
Eliminates in-sample threshold selection bias:
1. Inner Out-of-Fold Optimization: Thresholds are selected strictly on inner folds
   within the training partition.
2. Blind Outer Evaluation: Outer evaluation folds are evaluated using frozen inner thresholds.
3. Level 2 Global Thresholding: Training OOF probabilities determine global thresholds
   for unseen validation repositories.
"""

from typing import Dict, Any, Tuple, List, Callable
import numpy as np
from sklearn.model_selection import GroupKFold
from research.evaluation.metrics import evaluate_predictions, find_best_threshold_val

def select_inner_threshold_oof(
    X_train: np.ndarray,
    y_train: np.ndarray,
    groups_train: np.ndarray,
    model_factory: Callable[[], Any],
    n_splits_inner: int = 4,
    metric: str = "f1"
) -> Tuple[float, Dict[str, float]]:
    """
    Performs inner GroupKFold cross-validation across training projects
    to identify the optimal decision threshold on inner out-of-fold predictions.
    
    Guarantees: Outer evaluation labels are never seen during threshold search.
    """
    unique_groups = np.unique(groups_train)
    # If fewer groups than n_splits_inner, clamp splits
    k_inner = min(n_splits_inner, len(unique_groups))
    inner_gkf = GroupKFold(n_splits=k_inner)
    
    inner_oof_probas = np.zeros(len(y_train), dtype=np.float32)
    inner_oof_mask = np.zeros(len(y_train), dtype=bool)
    
    for in_tr, in_va in inner_gkf.split(X_train, y_train, groups=groups_train):
        clf = model_factory()
        clf.fit(X_train[in_tr], y_train[in_tr])
        inner_oof_probas[in_va] = clf.predict_proba(X_train[in_va])[:, 1]
        inner_oof_mask[in_va] = True
        
    # Sweep threshold on inner pooled out-of-fold probabilities
    valid_probas = inner_oof_probas[inner_oof_mask]
    valid_y = y_train[inner_oof_mask]
    
    best_th = find_best_threshold_val(valid_y, valid_probas, metric=metric)
    inner_oof_metrics = evaluate_predictions(valid_y, valid_probas, threshold=best_th)
    
    return float(best_th), inner_oof_metrics

def evaluate_nested_fold(
    X_train: np.ndarray,
    y_train: np.ndarray,
    groups_train: np.ndarray,
    X_test: np.ndarray,
    y_test: np.ndarray,
    model_factory: Callable[[], Any],
    n_splits_inner: int = 4
) -> Dict[str, Any]:
    """
    Executes a complete nested fold evaluation:
    1. Selects tau*_inner strictly on inner training folds.
    2. Fits outer model on full training set.
    3. Evaluates outer test set at default tau=0.50 and frozen tau*_inner.
    """
    # Step 1: Select threshold on inner folds
    tau_inner, inner_oof_metrics = select_inner_threshold_oof(
        X_train=X_train,
        y_train=y_train,
        groups_train=groups_train,
        model_factory=model_factory,
        n_splits_inner=n_splits_inner,
        metric="f1"
    )
    
    # Step 2: Fit outer model
    outer_model = model_factory()
    outer_model.fit(X_train, y_train)
    test_probas = outer_model.predict_proba(X_test)[:, 1]
    
    # Step 3: Evaluate test fold using frozen tau_inner (Unbiased!)
    metrics_at_050 = evaluate_predictions(y_test, test_probas, threshold=0.50)
    metrics_at_nested_th = evaluate_predictions(y_test, test_probas, threshold=tau_inner)
    
    # Also record in-sample "optimistic" threshold for audit comparison
    optimistic_th = find_best_threshold_val(y_test, test_probas, metric="f1")
    metrics_at_optimistic_th = evaluate_predictions(y_test, test_probas, threshold=optimistic_th)
    
    return {
        "selected_inner_threshold": tau_inner,
        "optimistic_in_sample_threshold": optimistic_th,
        "threshold_selection_gap": float(optimistic_th - tau_inner),
        "metrics_at_050": metrics_at_050,
        "metrics_at_nested_th": metrics_at_nested_th,
        "metrics_at_optimistic_th": metrics_at_optimistic_th,
        "inner_oof_training_metrics": inner_oof_metrics
    }
