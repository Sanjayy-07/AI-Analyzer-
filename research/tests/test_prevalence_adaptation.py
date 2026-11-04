"""
research.tests.test_prevalence_adaptation
=========================================
Unit tests for Python prevalence estimators and probability adapters.
Verifies numerical stability, boundary handling, and parity with Java.
"""

import unittest
import numpy as np
from research.evaluation.prevalence_estimator import (
    estimate_soft_mean,
    estimate_bbse,
    estimate_saerens_em,
    estimate_bayesian_beta_binomial,
    estimate_empirical_burn_in
)
from research.evaluation.prevalence_adaptation import (
    logit,
    sigmoid,
    adapt_probability,
    fit_platt_scaling,
    apply_platt_scaling
)

class TestPrevalenceAdaptation(unittest.TestCase):

    def setUp(self):
        self.pi_source = 0.1114

    def test_logit_sigmoid_roundtrip(self):
        p = np.array([0.01, 0.10, 0.25, 0.50, 0.75, 0.90, 0.99])
        roundtrip = sigmoid(logit(p))
        np.testing.assert_allclose(p, roundtrip, atol=1e-6)

    def test_monotonicity(self):
        p = np.array([0.05, 0.12, 0.18, 0.35, 0.72, 0.89])
        pi_target = 0.2812
        adapted = adapt_probability(p, self.pi_source, pi_target)
        self.assertTrue(np.all(np.diff(adapted) > 0), "Monotonicity violated in log-odds adaptation")

    def test_bayesian_smoothing_zero_defects(self):
        labels = np.zeros(100, dtype=int)
        pi_bayes = estimate_bayesian_beta_binomial(labels, pi_source=self.pi_source, pseudo_weight_m=10.0)
        expected = (10.0 * self.pi_source) / 110.0
        self.assertAlmostEqual(pi_bayes, expected, places=5)
        self.assertGreater(pi_bayes, 0.0)

    def test_bbse_singular_exception(self):
        scores = np.array([0.1, 0.2, 0.3])
        with self.assertRaises(ArithmeticError):
            estimate_bbse(scores, threshold=0.2, tpr_source=0.5, fpr_source=0.5)

    def test_saerens_em_generative(self):
        rng = np.random.RandomState(42)
        scores = np.concatenate([0.20 + 0.30 * rng.rand(40), 0.02 + 0.12 * rng.rand(160)])
        em_pi = estimate_saerens_em(scores, pi_source=self.pi_source, max_iter=50)
        self.assertGreater(em_pi, 0.05)
        self.assertLess(em_pi, 0.60)

if __name__ == "__main__":
    unittest.main()
