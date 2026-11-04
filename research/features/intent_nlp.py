"""
research.features.intent_nlp
============================
Deterministic, rule-based NLP intent and lexical complexity extraction
from raw commit messages.
Zero learned parameters: row-wise mapping with no inter-row or temporal leakage.
"""

import re
import numpy as np
import pandas as pd

# Precompiled regex patterns for speed and precision
BUG_FIX_REGEX = re.compile(
    r"\b(fix|fixed|fixes|fixing|bug|bugs|patch|patched|issue|issues|"
    r"defect|defects|error|errors|fail|failed|failure|failures|"
    r"crash|crashed|npe|nullpointer|deadlock|leak|memory leak|regression)\b",
    re.IGNORECASE
)

REFACTORING_REGEX = re.compile(
    r"\b(refactor|refactored|refactoring|cleanup|clean up|cleaning|"
    r"reorganize|reorganized|reorganise|reorganised|reorganizing|"
    r"rename|renamed|renaming|restructure|restructured|restructuring|"
    r"simplify|simplified|simplifying|inline|extract|extracted)\b",
    re.IGNORECASE
)

TICKET_REF_REGEX = re.compile(
    r"\b[A-Z]{2,10}-\d+\b|#\d+\b|\bgh-\d+\b|\bpr-\d+\b",
    re.IGNORECASE
)

MAINTENANCE_REGEX = re.compile(
    r"\b(doc|docs|documentation|javadoc|comment|comments|license|header|headers|"
    r"format|formatting|indent|indentation|checkstyle|pom\.xml|maven|gradle|"
    r"build|test|tests|testing|junit|assert)\b",
    re.IGNORECASE
)

ARCH_DEP_REGEX = re.compile(
    r"\b(dep|deps|dependency|dependencies|upgrade|upgraded|upgrading|"
    r"bump|bumped|api|interface|interfaces|abstract|module|modules|"
    r"package|packages|import|imports|version|versions|release)\b",
    re.IGNORECASE
)

INTENT_FEATURE_NAMES = [
    "is_bug_fix_intent",
    "is_refactoring_intent",
    "has_ticket_reference",
    "is_maintenance_intent",
    "is_dependency_arch_intent",
    "msg_char_length",
    "msg_word_count",
    "msg_line_count",
    "msg_type_token_ratio",
    "msg_uppercase_ratio",
    "msg_digit_ratio"
]

def extract_intent_features(commit_messages: pd.Series) -> pd.DataFrame:
    """
    Extracts rule-based intent indicators and lexical complexity metrics.
    Operates strictly row-by-row on text strings.
    """
    messages = commit_messages.fillna("").astype(str).tolist()
    n = len(messages)
    
    # Initialize numpy arrays for performance
    is_bug = np.zeros(n, dtype=np.float32)
    is_refactor = np.zeros(n, dtype=np.float32)
    has_ticket = np.zeros(n, dtype=np.float32)
    is_maint = np.zeros(n, dtype=np.float32)
    is_arch = np.zeros(n, dtype=np.float32)
    
    char_len = np.zeros(n, dtype=np.float32)
    word_cnt = np.zeros(n, dtype=np.float32)
    line_cnt = np.zeros(n, dtype=np.float32)
    ttr = np.zeros(n, dtype=np.float32)
    upper_ratio = np.zeros(n, dtype=np.float32)
    digit_ratio = np.zeros(n, dtype=np.float32)
    
    for i, msg in enumerate(messages):
        c_len = len(msg)
        char_len[i] = c_len
        line_cnt[i] = msg.count("\n") + 1
        
        if c_len == 0:
            continue
            
        # Intent indicators
        if BUG_FIX_REGEX.search(msg):
            is_bug[i] = 1.0
        if REFACTORING_REGEX.search(msg):
            is_refactor[i] = 1.0
        if TICKET_REF_REGEX.search(msg):
            has_ticket[i] = 1.0
        if MAINTENANCE_REGEX.search(msg):
            is_maint[i] = 1.0
        if ARCH_DEP_REGEX.search(msg):
            is_arch[i] = 1.0
            
        # Lexical statistics
        tokens = msg.split()
        num_tokens = len(tokens)
        word_cnt[i] = num_tokens
        
        if num_tokens > 0:
            unique_tokens = len(set(t.lower() for t in tokens))
            ttr[i] = unique_tokens / num_tokens
        else:
            ttr[i] = 0.0
            
        n_upper = sum(1 for c in msg if c.isupper())
        n_digit = sum(1 for c in msg if c.isdigit())
        upper_ratio[i] = n_upper / c_len
        digit_ratio[i] = n_digit / c_len
        
    return pd.DataFrame({
        "is_bug_fix_intent": is_bug,
        "is_refactoring_intent": is_refactor,
        "has_ticket_reference": has_ticket,
        "is_maintenance_intent": is_maint,
        "is_dependency_arch_intent": is_arch,
        "msg_char_length": char_len,
        "msg_word_count": word_cnt,
        "msg_line_count": line_cnt,
        "msg_type_token_ratio": ttr,
        "msg_uppercase_ratio": upper_ratio,
        "msg_digit_ratio": digit_ratio
    }, index=commit_messages.index)
