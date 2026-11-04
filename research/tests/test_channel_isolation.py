"""
research.tests.test_channel_isolation
=====================================
Unit tests verifying strict column isolation and assembly of feature channels.
"""

import unittest
import numpy as np
import pandas as pd

from research.features.channel_extractor import (
    verify_channel_disjointness,
    assemble_tabular_features,
    CHANNEL_DEFINITIONS,
    CH_1_COLS,
    CH_2_COLS,
    CH_3_COLS,
    CH_5A_COLS,
    CH_5B_COLS,
    CH_5C_COLS
)

class TestChannelIsolation(unittest.TestCase):
    
    def test_pairwise_disjointness(self):
        """
        Verify that tabular feature channels CH-1, CH-2, CH-3, CH-5A, CH-5B
        have zero overlapping features.
        """
        self.assertTrue(verify_channel_disjointness())
        
    def test_channel_5c_union(self):
        """
        Verify that CH-5C is exactly the union of CH-5A and CH-5B.
        """
        self.assertEqual(set(CH_5C_COLS), set(CH_5A_COLS).union(set(CH_5B_COLS)))
        self.assertEqual(len(CH_5C_COLS), len(CH_5A_COLS) + len(CH_5B_COLS))
        
    def test_assembly_dimensions(self):
        """
        Verify that assemble_tabular_features outputs the exact expected feature count.
        """
        # Create mock dataframes with exact column schemas
        n = 10
        all_cols = CH_1_COLS + CH_2_COLS + CH_3_COLS + CH_5C_COLS
        mock_data = {col: np.ones(n, dtype=np.float32) for col in all_cols}
        full_df = pd.DataFrame(mock_data)
        
        proc_df = full_df[CH_1_COLS + CH_2_COLS]
        intent_df = full_df[CH_3_COLS]
        hist_df = full_df[CH_5C_COLS]
        
        # Test CH-1 alone: 6 features
        mat_ch1 = assemble_tabular_features(proc_df, intent_df, hist_df, ["CH-1"])
        self.assertEqual(mat_ch1.shape, (n, len(CH_1_COLS)))
        
        # Test CH-1 + CH-2: 6 + 15 = 21 features
        mat_ch12 = assemble_tabular_features(proc_df, intent_df, hist_df, ["CH-1", "CH-2"])
        self.assertEqual(mat_ch12.shape, (n, len(CH_1_COLS) + len(CH_2_COLS)))
        
        # Test CH-5A alone (zero-label): 4 features
        mat_ch5a = assemble_tabular_features(proc_df, intent_df, hist_df, ["CH-5A"])
        self.assertEqual(mat_ch5a.shape, (n, len(CH_5A_COLS)))
        
        # Test CH-5B alone (delayed defect): 4 features
        mat_ch5b = assemble_tabular_features(proc_df, intent_df, hist_df, ["CH-5B"])
        self.assertEqual(mat_ch5b.shape, (n, len(CH_5B_COLS)))

if __name__ == "__main__":
    unittest.main()
