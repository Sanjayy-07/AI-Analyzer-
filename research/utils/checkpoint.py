"""
research.utils.checkpoint
=========================
Resilient experiment checkpointing, atomic disk serialization,
and leakage-safe feature cache management.
"""

import os
import json
import hashlib
import tempfile
from typing import Dict, Any, Optional
import numpy as np

def atomic_save_json(filepath: str, data: Dict[str, Any]) -> None:
    """
    Atomically writes JSON to disk via temporary file rename.
    Guarantees no corrupted or partially written JSON files.
    """
    directory = os.path.dirname(os.path.abspath(filepath))
    os.makedirs(directory, exist_ok=True)
    
    # Create temp file in same directory for atomic replace
    temp_fd, temp_path = tempfile.mkstemp(dir=directory, prefix="ckpt_", suffix=".tmp")
    with os.fdopen(temp_fd, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.flush()
        os.fsync(f.fileno())
        
    os.replace(temp_path, filepath)

def load_checkpoint(filepath: str) -> Optional[Dict[str, Any]]:
    """
    Loads JSON checkpoint if it exists, else returns None.
    """
    if os.path.exists(filepath):
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return None
    return None

class ExperimentCheckpointer:
    """
    Manages stage progress, allowing resumable execution of multi-fold benchmarks.
    """
    def __init__(self, checkpoint_path: str):
        self.checkpoint_path = checkpoint_path
        self.state = load_checkpoint(checkpoint_path) or {
            "completed_experiments": {},
            "active_stage": None,
            "metadata": {}
        }
        
    def is_completed(self, experiment_key: str) -> bool:
        return experiment_key in self.state.get("completed_experiments", {})
        
    def record_completed(self, experiment_key: str, results: Dict[str, Any]) -> None:
        self.state["completed_experiments"][experiment_key] = results
        atomic_save_json(self.checkpoint_path, self.state)
        
    def get_results(self, experiment_key: str) -> Optional[Dict[str, Any]]:
        return self.state.get("completed_experiments", {}).get(experiment_key)
        
    def clear(self) -> None:
        if os.path.exists(self.checkpoint_path):
            os.remove(self.checkpoint_path)
        self.state = {"completed_experiments": {}, "active_stage": None, "metadata": {}}
