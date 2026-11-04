"""
research.evaluation.prevalence_adaptation
=========================================
Python companion for Phase 3A Prior Log-Odds Adaptation and Platt Scaling.
Ensures bit-level parity with Java implementation (com.archanalyzer.research.adaptation.ProbabilityAdapter).
"""

import numpy as np

EPSILON = 1e-7

def logit(p: np.ndarray, eps: float = EPSILON) -> np.ndarray:
    p_clamped = np.clip(p, eps, 1.0 - eps)
    return np.log(p_clamped / (1.0 - p_clamped))

def sigmoid(z: np.ndarray) -> np.ndarray:
    return np.where(z >= 0, 1.0 / (1.0 + np.exp(-z)), np.exp(z) / (1.0 + np.exp(z)))

def adapt_probability(p: np.ndarray, pi_source: float, pi_target: float) -> np.ndarray:
    delta = logit(pi_target) - logit(pi_source)
    return sigmoid(logit(p) + delta)

def fit_platt_scaling(probabilities: np.ndarray, labels: np.ndarray, max_iter: int = 100) -> tuple:
    p = np.asarray(probabilities, dtype=float)
    y = np.asarray(labels, dtype=float)
    x = logit(p)
    n = len(p)
    a, b, lr = 1.0, 0.0, 0.1
    for _ in range(max_iter):
        pred = sigmoid(a * x + b)
        err = pred - y
        grad_a = np.sum(err * x) / n
        grad_b = np.sum(err) / n
        a = max(0.01, a - lr * grad_a)
        b -= lr * grad_b
    return float(a), float(b)

def apply_platt_scaling(probabilities: np.ndarray, a: float, b: float) -> np.ndarray:
    p = np.asarray(probabilities, dtype=float)
    return sigmoid(a * logit(p) + b)

def compute_quantile_threshold(probabilities: np.ndarray, pi_target: float) -> float:
    p = np.asarray(probabilities, dtype=float)
    return float(np.quantile(p, 1.0 - pi_target))
