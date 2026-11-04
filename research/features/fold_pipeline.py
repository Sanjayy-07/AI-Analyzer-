"""
research.features.fold_pipeline
===============================
Strict fold-independent feature transformation pipeline for controlled NLP
and multi-modal experiments.

Guarantees:
1. Preprocessing is fitted ONLY on the training fold indices.
2. Validation fold is transformed using pre-fitted transformers without fitting.
3. Zero leakage of vocabulary, IDF weights, SVD projection matrices, or scalers.
"""

from typing import Dict, Any, Tuple
import numpy as np
import scipy.sparse as sp
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.decomposition import TruncatedSVD
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer

class FoldFeaturePipeline:
    """
    Manages feature extraction, vectorization, and transformation
    strictly within a single cross-validation fold.
    """
    
    def __init__(self, config_name: str, random_state: int = 42):
        """
        Valid config_names:
        1. 'word_tfidf_no_svd'
        2. 'char_tfidf_no_svd'
        3. 'word_char_tfidf_no_svd'
        4. 'word_char_tfidf_svd'
        5. 'nlp_plus_intent'
        6. 'churn_plus_nlp_plus_historical'
        7. 'churn_baseline'
        """
        self.config_name = config_name
        self.random_state = random_state
        
        self.word_vec = None
        self.char_vec = None
        self.svd = None
        self.imputer = None
        self.scaler = None
        self.is_fitted = False
        
    def fit_transform(
        self,
        messages: np.ndarray,
        intent_features: np.ndarray = None,
        process_features: np.ndarray = None,
        historical_features: np.ndarray = None
    ):
        """
        Fits transformers strictly on training fold data and returns transformed matrix.
        """
        self.is_fitted = True
        cfg = self.config_name
        
        if cfg == "churn_baseline":
            self.imputer = SimpleImputer(strategy="constant", fill_value=0)
            self.scaler = StandardScaler()
            X_proc = self.imputer.fit_transform(process_features)
            return self.scaler.fit_transform(X_proc)
            
        # 1. Word TF-IDF without SVD
        if cfg == "word_tfidf_no_svd":
            self.word_vec = TfidfVectorizer(
                max_features=2000,
                stop_words="english",
                ngram_range=(1, 2),
                sublinear_tf=True
            )
            return self.word_vec.fit_transform(messages)
            
        # 2. Character TF-IDF without SVD
        if cfg == "char_tfidf_no_svd":
            self.char_vec = TfidfVectorizer(
                max_features=2000,
                analyzer="char",
                ngram_range=(3, 5),
                sublinear_tf=True
            )
            return self.char_vec.fit_transform(messages)
            
        # 3. Word + Char TF-IDF without SVD
        if cfg == "word_char_tfidf_no_svd":
            self.word_vec = TfidfVectorizer(
                max_features=1000,
                stop_words="english",
                ngram_range=(1, 2),
                sublinear_tf=True
            )
            self.char_vec = TfidfVectorizer(
                max_features=1000,
                analyzer="char",
                ngram_range=(3, 5),
                sublinear_tf=True
            )
            X_w = self.word_vec.fit_transform(messages)
            X_c = self.char_vec.fit_transform(messages)
            return sp.hstack([X_w, X_c], format="csr")
            
        # 4. Word + Char TF-IDF + SVD
        if cfg == "word_char_tfidf_svd":
            self.word_vec = TfidfVectorizer(
                max_features=1000,
                stop_words="english",
                ngram_range=(1, 2),
                sublinear_tf=True
            )
            self.char_vec = TfidfVectorizer(
                max_features=1000,
                analyzer="char",
                ngram_range=(3, 5),
                sublinear_tf=True
            )
            X_w = self.word_vec.fit_transform(messages)
            X_c = self.char_vec.fit_transform(messages)
            X_fused = sp.hstack([X_w, X_c], format="csr")
            self.svd = TruncatedSVD(n_components=50, random_state=self.random_state)
            return self.svd.fit_transform(X_fused)
            
        # 5. NLP + Intent Features
        if cfg == "nlp_plus_intent":
            self.word_vec = TfidfVectorizer(
                max_features=1000,
                stop_words="english",
                ngram_range=(1, 2),
                sublinear_tf=True
            )
            self.char_vec = TfidfVectorizer(
                max_features=1000,
                analyzer="char",
                ngram_range=(3, 5),
                sublinear_tf=True
            )
            X_w = self.word_vec.fit_transform(messages)
            X_c = self.char_vec.fit_transform(messages)
            X_fused = sp.hstack([X_w, X_c], format="csr")
            self.svd = TruncatedSVD(n_components=50, random_state=self.random_state)
            X_svd = self.svd.fit_transform(X_fused)
            
            self.scaler = StandardScaler()
            X_intent_scaled = self.scaler.fit_transform(intent_features)
            return np.hstack([X_svd, X_intent_scaled])
            
        # 6. Churn + NLP + Historical Features (Full Fused)
        if cfg == "churn_plus_nlp_plus_historical":
            self.word_vec = TfidfVectorizer(
                max_features=1000,
                stop_words="english",
                ngram_range=(1, 2),
                sublinear_tf=True
            )
            self.char_vec = TfidfVectorizer(
                max_features=1000,
                analyzer="char",
                ngram_range=(3, 5),
                sublinear_tf=True
            )
            X_w = self.word_vec.fit_transform(messages)
            X_c = self.char_vec.fit_transform(messages)
            X_fused = sp.hstack([X_w, X_c], format="csr")
            self.svd = TruncatedSVD(n_components=50, random_state=self.random_state)
            X_svd = self.svd.fit_transform(X_fused)
            
            # Combine tabular features
            tabular = np.hstack([process_features, intent_features, historical_features])
            self.imputer = SimpleImputer(strategy="constant", fill_value=0)
            self.scaler = StandardScaler()
            X_tab_imp = self.imputer.fit_transform(tabular)
            X_tab_scaled = self.scaler.fit_transform(X_tab_imp)
            return np.hstack([X_tab_scaled, X_svd])
            
        raise ValueError(f"Unknown config_name: {cfg}")
        
    def transform(
        self,
        messages: np.ndarray,
        intent_features: np.ndarray = None,
        process_features: np.ndarray = None,
        historical_features: np.ndarray = None
    ):
        """
        Transforms validation/test fold data strictly using fitted estimators.
        """
        if not self.is_fitted:
            raise RuntimeError("Pipeline must be fitted before calling transform()")
            
        cfg = self.config_name
        
        if cfg == "churn_baseline":
            X_proc = self.imputer.transform(process_features)
            return self.scaler.transform(X_proc)
            
        if cfg == "word_tfidf_no_svd":
            return self.word_vec.transform(messages)
            
        if cfg == "char_tfidf_no_svd":
            return self.char_vec.transform(messages)
            
        if cfg == "word_char_tfidf_no_svd":
            X_w = self.word_vec.transform(messages)
            X_c = self.char_vec.transform(messages)
            return sp.hstack([X_w, X_c], format="csr")
            
        if cfg == "word_char_tfidf_svd":
            X_w = self.word_vec.transform(messages)
            X_c = self.char_vec.transform(messages)
            X_fused = sp.hstack([X_w, X_c], format="csr")
            return self.svd.transform(X_fused)
            
        if cfg == "nlp_plus_intent":
            X_w = self.word_vec.transform(messages)
            X_c = self.char_vec.transform(messages)
            X_fused = sp.hstack([X_w, X_c], format="csr")
            X_svd = self.svd.transform(X_fused)
            X_intent_scaled = self.scaler.transform(intent_features)
            return np.hstack([X_svd, X_intent_scaled])
            
        if cfg == "churn_plus_nlp_plus_historical":
            X_w = self.word_vec.transform(messages)
            X_c = self.char_vec.transform(messages)
            X_fused = sp.hstack([X_w, X_c], format="csr")
            X_svd = self.svd.transform(X_fused)
            
            tabular = np.hstack([process_features, intent_features, historical_features])
            X_tab_imp = self.imputer.transform(tabular)
            X_tab_scaled = self.scaler.transform(X_tab_imp)
            return np.hstack([X_tab_scaled, X_svd])
            
        raise ValueError(f"Unknown config_name: {cfg}")
