package com.archanalyzer.riskprediction.model;

import java.util.*;

/**
 * Baseline classifier that predicts the marginal empirical training prior P(y=1).
 * Serves as the minimal sanity baseline for all experimental evaluations.
 */
public class BaselinePriorClassifier implements TrainableClassifier {

    private double priorProbability = 0.5;
    private String[] featureNames = new String[0];

    @Override
    public void train(double[][] X, int[] y, String[] featureNames) {
        this.featureNames = featureNames != null ? featureNames.clone() : new String[0];
        if (y == null || y.length == 0) {
            this.priorProbability = 0.5;
            return;
        }
        int positiveCount = 0;
        for (int label : y) {
            if (label == 1) positiveCount++;
        }
        this.priorProbability = (double) positiveCount / y.length;
    }

    @Override
    public double predictProbability(double[] x) {
        return priorProbability;
    }

    @Override
    public String getModelName() {
        return "BaselinePriorClassifier";
    }

    @Override
    public String[] getFeatureNames() {
        return featureNames;
    }

    @Override
    public Map<String, Double> getFeatureImportances() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (String f : featureNames) {
            map.put(f, 0.0);
        }
        return map;
    }

    @Override
    public String serialize() {
        return "MODEL=BaselinePrior\nPRIOR=" + priorProbability + "\nFEATURES=" + String.join(",", featureNames) + "\n";
    }

    @Override
    public void deserialize(String data) {
        for (String line : data.split("\n")) {
            line = line.trim();
            if (line.startsWith("PRIOR=")) {
                this.priorProbability = Double.parseDouble(line.substring("PRIOR=".length()));
            } else if (line.startsWith("FEATURES=")) {
                this.featureNames = line.substring("FEATURES=".length()).split(",");
            }
        }
    }
}
