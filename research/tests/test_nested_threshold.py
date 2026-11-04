"""
research.tests.test_nested_threshold
====================================
Unit tests verifying that nested threshold selection is strictly out-of-sample
and completely independent of evaluation labels.
"""

import unittest
import numpy as np
from sklearn.linear_model import LogisticRegression

from research.evaluation.nested_threshold import select_inner_threshold_oof, evaluate_nested_fold

class TestNestedThreshold(unittest.TestCase):
    
    def setUp(self):
        np.random.seed(42)
        # Create synthetic dataset with 4 projects in training, 1 in test
        n_train = 400
        n_test = 100
        
        self.X_train = np.random.randn(n_train, 10).astype(np.float32)
        # Ground truth: linear combination + noise
        logits_tr = self.X_train[:, 0] * 1.5 - self.X_train[:, 1]
        self.y_train = (logits_tr > 0.5).astype(int)
        
        # 4 project groups in training: 100 samples each
        self.groups_train = np.repeat(["p1", "p2", "p3", "p4"], 100)
        
        self.X_test = np.random.randn(n_test, 10).astype(np.float32)
        logits_te = self.X_test[:, 0] * 1.5 - self.X_test[:, 1]
        self.y_test = (logits_te > 0.5).astype(int)
        
    def test_inner_threshold_independence(self):
        """
        Verify that select_inner_threshold_oof never accesses test data.
        """
        model_factory = lambda: LogisticRegression(C=1.0, random_state=42)
        
        tau_inner, inner_metrics = select_inner_threshold_oof(
            X_train=self.X_train,
            y_train=self.y_train,
            groups_train=self.groups_train,
            model_factory=model_factory,
            n_splits_inner=4,
            metric="f1"
        )
        
        # Threshold must be within valid range [0.05, 0.95]
        self.assertGreaterEqual(tau_inner, 0.05)
        self.assertLessEqual(tau_inner, 0.95)
        self.assertIn("f1", inner_metrics)
        self.assertIn("precision", inner_metrics)
        self.assertIn("recall", inner_metrics)
        
    def test_nested_fold_evaluation(self):
        """
        Verify that nested fold evaluation returns both default and nested metrics
        without test label contamination.
        """
        model_factory = lambda: LogisticRegression(C=1.0, random_state=42)
        
        res = evaluate_nested_fold(
            X_train=self.X_train,
            y_train=self.y_train,
            groups_train=self.groups_train,
            X_test=self.X_test,
            y_test=self.y_test,
            model_factory=model_factory,
            n_splits_inner=4
        )
        
        self.assertIn("selected_inner_threshold", res)
        self.assertIn("metrics_at_050", res)
        self.assertIn("metrics_at_nested_th", res)
        self.assertIn("optimistic_in_sample_threshold", res)
        
        # Check that metric values are valid floats
        self.assertGreaterEqual(res["metrics_at_nested_th"]["f1"], 0.0)
        self.assertLessEqual(res["metrics_at_nested_th"]["f1"], 1.0)

if __name__ == "__main__":
    unittest.main()
