"""
research.evaluation.prevalence_estimator
========================================
Python companion for Phase 3A Prevalence Estimation.
Ensures bit-level parity with Java implementation (com.archanalyzer.research.adaptation.PrevalenceEstimator).
"""

import numpy as np

DEFAULT_EPSILON = 1e-4
DEFAULT_PI_SOURCE = 0.111394

def estimate_soft_mean(probabilities: np.ndarray, epsilon: float = DEFAULT_EPSILON) -> float:
    p = np.asarray(probabilities, dtype=float)
    if len(p) == 0:
        raise ValueError("Probabilities cannot be empty")
    return float(np.clip(np.mean(p), epsilon, 1.0 - epsilon))

def estimate_bbse(
    probabilities: np.ndarray,
    threshold: float,
    tpr_source: float,
    fpr_source: float,
    epsilon: float = DEFAULT_EPSILON
) -> float:
    p = np.asarray(probabilities, dtype=float)
    if len(p) == 0:
        raise ValueError("Probabilities cannot be empty")
    denom = tpr_source - fpr_source
    if abs(denom) < 1e-6:
        raise ArithmeticError(f"BBSE singular: TPR - FPR = {denom}")
    mu_hat = np.mean(p >= threshold)
    pi_hat = (mu_hat - fpr_source) / denom
    return float(np.clip(pi_hat, epsilon, 1.0 - epsilon))

def estimate_saerens_em(
    probabilities: np.ndarray,
    pi_source: float,
    max_iter: int = 50,
    tol: float = 1e-5,
    epsilon: float = DEFAULT_EPSILON
) -> float:
    p0 = np.asarray(probabilities, dtype=float)
    if len(p0) == 0:
        raise ValueError("Probabilities cannot be empty")
    p0_clamped = np.clip(p0, 1e-6, 1.0 - 1e-6)
    
    current_pi = estimate_soft_mean(p0, epsilon=epsilon)
    for _ in range(max_iter):
        w1 = current_pi / pi_source
        w0 = (1.0 - current_pi) / (1.0 - pi_source)
        num = p0_clamped * w1
        denom = num + (1.0 - p0_clamped) * w0
        p_updated = np.where(denom > 0.0, num / denom, p0_clamped)
        next_pi = float(np.clip(np.mean(p_updated), epsilon, 1.0 - epsilon))
        if abs(next_pi - current_pi) < tol:
            return next_pi
        current_pi = next_pi
    return current_pi

def estimate_empirical_burn_in(labels: np.ndarray, is_matured: np.ndarray = None, pi_fallback: float = DEFAULT_PI_SOURCE) -> float:
    y = np.asarray(labels, dtype=int)
    if len(y) == 0:
        raise ValueError("Labels cannot be empty")
    if is_matured is not None:
        y_m = y[is_matured]
    else:
        y_m = y
    if len(y_m) == 0:
        return pi_fallback
    return float(np.clip(np.mean(y_m), DEFAULT_EPSILON, 1.0 - DEFAULT_EPSILON))

def estimate_bayesian_beta_binomial(
    labels: np.ndarray,
    is_matured: np.ndarray = None,
    pi_source: float = DEFAULT_PI_SOURCE,
    pseudo_weight_m: float = 10.0,
    epsilon: float = DEFAULT_EPSILON
) -> float:
    y = np.asarray(labels, dtype=int)
    if is_matured is not None:
        y_m = y[is_matured]
    else:
        y_m = y
    k = np.sum(y_m)
    n = len(y_m)
    smoothed = (k + pseudo_weight_m * pi_source) / (n + pseudo_weight_m)
    return float(np.clip(smoothed, epsilon, 1.0 - epsilon))
