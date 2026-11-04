"""
research.tests.test_checkpoint
==============================
Unit tests verifying atomic JSON checkpointing and resumable execution.
"""

import os
import shutil
import tempfile
import unittest

from research.utils.checkpoint import atomic_save_json, load_checkpoint, ExperimentCheckpointer

class TestCheckpoint(unittest.TestCase):
    
    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="test_ckpt_")
        self.ckpt_path = os.path.join(self.test_dir, "stage2_checkpoint.json")
        
    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)
            
    def test_atomic_save_and_load(self):
        """
        Verify that atomic JSON saving preserves complex nested dictionaries.
        """
        payload = {
            "experiment_key": "stepwise_ch1_ch2",
            "metrics": {"f1": 0.354, "roc_auc": 0.782},
            "folds": [0, 1, 2, 3, 4]
        }
        atomic_save_json(self.ckpt_path, payload)
        loaded = load_checkpoint(self.ckpt_path)
        self.assertEqual(loaded, payload)
        
    def test_resumable_execution(self):
        """
        Verify that ExperimentCheckpointer tracks completed experiments and resumes.
        """
        checkpointer = ExperimentCheckpointer(self.ckpt_path)
        self.assertFalse(checkpointer.is_completed("exp_A"))
        
        # Record completion
        checkpointer.record_completed("exp_A", {"status": "success", "f1": 0.42})
        self.assertTrue(checkpointer.is_completed("exp_A"))
        
        # Simulate new session / restart
        new_checkpointer = ExperimentCheckpointer(self.ckpt_path)
        self.assertTrue(new_checkpointer.is_completed("exp_A"))
        self.assertEqual(new_checkpointer.get_results("exp_A")["f1"], 0.42)

if __name__ == "__main__":
    unittest.main()
