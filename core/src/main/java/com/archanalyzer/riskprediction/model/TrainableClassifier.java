package com.archanalyzer.riskprediction.model;

import java.util.Map;

/**
 * Common interface for trainable, explainable pure-Java machine learning classifiers.
 */
public interface TrainableClassifier {

    /**
     * Trains the classifier on feature matrix X and binary target labels y (0 or 1).
     */
    void train(double[][] X, int[] y, String[] featureNames);

    /**
     * Predicts the continuous risk probability P(y=1|x) in range [0.0, 1.0].
     */
    double predictProbability(double[] x);

    /**
     * Predicts binary class (0 or 1) using a decision threshold of 0.5.
     */
    default int predict(double[] x) {
        return predictProbability(x) >= 0.5 ? 1 : 0;
    }

    String getModelName();
    String[] getFeatureNames();

    /**
     * Returns learned relative feature importances (sums to 1.0 or non-negative scores).
     */
    Map<String, Double> getFeatureImportances();

    /**
     * Serializes model parameters to a line-delimited text format.
     */
    String serialize();

    /**
     * Deserializes model parameters from a line-delimited text format.
     */
    void deserialize(String data);
}
