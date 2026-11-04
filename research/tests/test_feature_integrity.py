"""
research.tests.test_feature_integrity
=====================================
Unit tests verifying output shapes, non-null constraints, and numerical stability
of process features, intent NLP features, and historical features.
"""

import unittest
import numpy as np
import pandas as pd

from research.features.intent_nlp import extract_intent_features, INTENT_FEATURE_NAMES
from research.features.process_features import extract_process_features, PROCESS_FEATURE_NAMES
from research.features.historical_features import compute_strict_historical_features, HISTORICAL_FEATURE_NAMES

class TestFeatureIntegrity(unittest.TestCase):
    
    def setUp(self):
        self.sample_df = pd.DataFrame({
            "PROJECT_ID": ["p1", "p1", "p2"],
            "COMMIT_HASH": ["hash1", "hash2", "hash3"],
            "AUTHOR": ["alice", "bob", "carol"],
            "AUTHOR_DATE": ["2015-05-01T08:00:00Z", "2015-05-02T22:30:00Z", "2015-05-03T14:15:00Z"],
            "AUTHOR_TIMEZONE": [60, -300, 0],
            "COMMITTER": ["alice", "bob", "carol"],
            "COMMITTER_DATE": ["2015-05-01T08:00:00Z", "2015-05-02T22:30:00Z", "2015-05-03T14:15:00Z"],
            "COMMITTER_TIMEZONE": [60, -300, 0],
            "BRANCH": ["trunk", "trunk", "master"],
            "IN_MAIN_BRANCH": [1, 1, 1],
            "MERGE": [0, 0, 0],
            "N_PARENTS": [1, 1, 1],
            "N_FILES_CHANGED": [2, np.nan, 5],
            "LINES_ADDED": [100, np.nan, 0],
            "LINES_REMOVED": [20, np.nan, 50],
            "N_REFACTORINGS": [3, 0, 0],
            "COMMIT_MESSAGE": [
                "Fix NPE in parser module [MRM-102]",
                "",
                "Refactor and clean up documentation"
            ],
            "IS_FAULT_INDUCING": [1, 0, 0]
        })
        
    def test_intent_feature_integrity(self):
        intent_df = extract_intent_features(self.sample_df["COMMIT_MESSAGE"])
        self.assertEqual(len(intent_df), len(self.sample_df))
        self.assertEqual(list(intent_df.columns), INTENT_FEATURE_NAMES)
        self.assertFalse(intent_df.isnull().any().any(), "Intent features contain unexpected NaN")
        
        # Row 0: "Fix NPE in parser module [MRM-102]"
        self.assertEqual(intent_df.loc[0, "is_bug_fix_intent"], 1.0)
        self.assertEqual(intent_df.loc[0, "has_ticket_reference"], 1.0)
        
        # Row 1: empty string
        self.assertEqual(intent_df.loc[1, "msg_char_length"], 0.0)
        self.assertEqual(intent_df.loc[1, "msg_word_count"], 0.0)
        
        # Row 2: "Refactor and clean up documentation"
        self.assertEqual(intent_df.loc[2, "is_refactoring_intent"], 1.0)
        self.assertEqual(intent_df.loc[2, "is_maintenance_intent"], 1.0)
        
    def test_process_feature_integrity(self):
        intent_df = extract_intent_features(self.sample_df["COMMIT_MESSAGE"])
        proc_df = extract_process_features(self.sample_df, intent_df=intent_df)
        self.assertEqual(len(proc_df), len(self.sample_df))
        self.assertEqual(list(proc_df.columns), PROCESS_FEATURE_NAMES)
        self.assertFalse(proc_df.isnull().any().any(), "Process features contain unexpected NaN")
        
        # Check off-hours: row 1 was at 22:30 (>= 20), should be off-hours
        self.assertEqual(proc_df.loc[1, "is_off_hours"], 1.0)
        # Check row 0: at 08:00, not off-hours
        self.assertEqual(proc_df.loc[0, "is_off_hours"], 0.0)
        
    def test_historical_feature_integrity(self):
        hist_df = compute_strict_historical_features(self.sample_df)
        self.assertEqual(len(hist_df), len(self.sample_df))
        for col in HISTORICAL_FEATURE_NAMES:
            self.assertIn(col, hist_df.columns)
        self.assertFalse(hist_df.isnull().any().any(), "Historical features contain unexpected NaN")
        
        # Check Bayesian smoothed rates are strictly within (0, 1)
        self.assertTrue((hist_df["author_fault_rate"] > 0.0).all())
        self.assertTrue((hist_df["author_fault_rate"] < 1.0).all())

if __name__ == "__main__":
    unittest.main()
