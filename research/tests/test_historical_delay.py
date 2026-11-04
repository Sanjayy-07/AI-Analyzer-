"""
research.tests.test_historical_delay
====================================
Unit tests verifying the simulated operational defect feedback delay (90 days).
Ensures:
1. Commits within the 90-day window count toward prior commits, but NOT prior faults.
2. Commits past the 90-day window count toward both prior commits AND prior faults.
3. CH-5A (zero-label) features remain completely unaffected by the defect delay parameter.
"""

import unittest
import pandas as pd
from research.features.historical_features import compute_strict_historical_features

class TestHistoricalDelay(unittest.TestCase):
    
    def setUp(self):
        self.toy_df = pd.DataFrame({
            "PROJECT_ID": ["p1", "p1", "p1"],
            "AUTHOR": ["alice", "alice", "alice"],
            "COMMITTER_DATE": [
                "2020-01-01T00:00:00Z",  # day 0: fault=1
                "2020-02-01T00:00:00Z",  # day 31: (< 90d delay)
                "2020-04-15T00:00:00Z"   # day 105: (> 90d delay)
            ],
            "IS_FAULT_INDUCING": [1, 0, 0]
        })
        
    def test_90_day_delayed_feedback(self):
        res = compute_strict_historical_features(self.toy_df, delay_days=90.0)
        
        # Row 0 (day 0): first commit
        self.assertEqual(res.loc[0, "author_prior_commits"], 0.0)
        self.assertEqual(res.loc[0, "author_prior_faults_delayed"], 0.0)
        
        # Row 1 (day 31): commit count must be 1, but fault count must be 0 (delay pending!)
        self.assertEqual(res.loc[1, "author_prior_commits"], 1.0)
        self.assertEqual(res.loc[1, "author_prior_faults_delayed"], 0.0)
        
        # Row 2 (day 105): commit count must be 2, fault count must be 1 (90d delay elapsed!)
        self.assertEqual(res.loc[2, "author_prior_commits"], 2.0)
        self.assertEqual(res.loc[2, "author_prior_faults_delayed"], 1.0)
        
    def test_ch5a_zero_label_invariance(self):
        """
        Verify that CH-5A (prior commits, tenure, velocity) is identical
        regardless of whether delay is 0, 90, or 365 days.
        """
        res_0 = compute_strict_historical_features(self.toy_df, delay_days=0.0)
        res_90 = compute_strict_historical_features(self.toy_df, delay_days=90.0)
        res_365 = compute_strict_historical_features(self.toy_df, delay_days=365.0)
        
        pd.testing.assert_series_equal(res_0["author_prior_commits"], res_90["author_prior_commits"])
        pd.testing.assert_series_equal(res_0["author_prior_commits"], res_365["author_prior_commits"])
        pd.testing.assert_series_equal(res_0["author_tenure_days"], res_90["author_tenure_days"])

if __name__ == "__main__":
    unittest.main()
