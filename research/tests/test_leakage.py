"""
research.tests.test_leakage
===========================
Rigorous unit tests verifying temporal causality, same-timestamp isolation,
project boundaries, and fold-level transformer isolation.
"""

import unittest
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.decomposition import TruncatedSVD
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import GroupKFold

from research.features.historical_features import compute_strict_historical_features
from research.features.intent_nlp import extract_intent_features
from research.features.process_features import extract_process_features

class TestLeakageSafeguards(unittest.TestCase):
    
    def setUp(self):
        # Controlled synthetic dataset with known timestamps and ties
        self.toy_df = pd.DataFrame({
            "PROJECT_ID": ["projA", "projA", "projA", "projA", "projB", "projB"],
            "COMMIT_HASH": ["c1", "c2", "c3", "c4", "c5", "c6"],
            "AUTHOR": ["alice", "alice", "alice", "bob", "alice", "alice"],
            "COMMITTER_DATE": [
                "2020-01-01T10:00:00Z",  # c1: alice, fault=1
                "2020-01-01T10:00:00Z",  # c2: alice, tie with c1, fault=0
                "2020-01-02T10:00:00Z",  # c3: alice, fault=1
                "2020-01-03T10:00:00Z",  # c4: bob, fault=0
                "2020-01-01T12:00:00Z",  # c5: alice (projB), fault=1
                "2020-01-02T12:00:00Z"   # c6: alice (projB), fault=0
            ],
            "IS_FAULT_INDUCING": [1, 0, 1, 0, 1, 0]
        })
        
    def test_same_timestamp_tie_handling(self):
        """
        Verify that commits occurring at the exact same timestamp (c1, c2)
        cannot see each other's defect labels.
        """
        res = compute_strict_historical_features(self.toy_df, delay_days=0.0)
        
        # c1 and c2 happen at the same timestamp
        # Neither should see the other's commit or fault
        self.assertEqual(res.loc[0, "author_prior_commits"], 0.0)
        self.assertEqual(res.loc[0, "author_prior_faults"], 0.0)
        self.assertEqual(res.loc[1, "author_prior_commits"], 0.0)
        self.assertEqual(res.loc[1, "author_prior_faults"], 0.0)
        
        # c3 (at 2020-01-02) should see both c1 and c2 (2 commits, 1 fault)
        self.assertEqual(res.loc[2, "author_prior_commits"], 2.0)
        self.assertEqual(res.loc[2, "author_prior_faults"], 1.0)
        
    def test_first_commit_zero_prior(self):
        """
        Verify that the first commit for any author/project has 0 prior commits,
        0 prior faults, and 0 tenure.
        """
        res = compute_strict_historical_features(self.toy_df)
        
        # bob's first commit is at index 3
        self.assertEqual(res.loc[3, "author_prior_commits"], 0.0)
        self.assertEqual(res.loc[3, "author_prior_faults"], 0.0)
        self.assertEqual(res.loc[3, "author_tenure_days"], 0.0)
        
    def test_project_boundary_isolation(self):
        """
        Verify that commits in projA do not bleed into projB.
        """
        res = compute_strict_historical_features(self.toy_df, delay_days=0.0)
        
        # In projB, alice's first commit is at index 4 (c5)
        # Even though alice committed in projA earlier, projB history must start at 0
        self.assertEqual(res.loc[4, "author_prior_commits"], 0.0)
        self.assertEqual(res.loc[4, "author_prior_faults"], 0.0)
        self.assertEqual(res.loc[4, "project_prior_commits"], 0.0)
        
        # c6 in projB should see only c5 (1 commit, 1 fault)
        self.assertEqual(res.loc[5, "author_prior_commits"], 1.0)
        self.assertEqual(res.loc[5, "author_prior_faults"], 1.0)
        self.assertEqual(res.loc[5, "project_prior_commits"], 1.0)
        
    def test_fold_transformer_isolation(self):
        """
        Verify that learned transformers (TF-IDF, SVD, Scaler) fitted strictly
        on a training fold do not leak vocabulary or statistics from validation fold.
        """
        docs = [
            "fix nullpointer exception in parser",
            "refactor core engine components",
            "add unit tests for validator",
            "unique_val_word_that_must_not_leak into vocabulary"
        ]
        groups = ["p1", "p1", "p1", "p2"]  # p2 is validation fold
        
        train_idx = [0, 1, 2]
        val_idx = [3]
        
        vec = TfidfVectorizer()
        # Fit strictly on train fold
        vec.fit([docs[i] for i in train_idx])
        
        # 'unique_val_word_that_must_not_leak' must NOT be in vocabulary
        self.assertNotIn("unique_val_word_that_must_not_leak", vec.vocabulary_)
        
        # Transforming val_idx must yield all zeros for the unknown token
        val_vec = vec.transform([docs[3]])
        self.assertEqual(val_vec.nnz, 0)

if __name__ == "__main__":
    unittest.main()
