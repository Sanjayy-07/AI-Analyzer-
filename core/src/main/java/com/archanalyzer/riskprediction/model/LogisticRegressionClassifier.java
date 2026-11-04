package com.archanalyzer.riskprediction.model;

import java.util.*;

/**
 * Trainable, pure-Java Logistic Regression classifier with L2 regularization
 * and automatic feature standardization.
 */
public class LogisticRegressionClassifier implements TrainableClassifier {

    private String[] featureNames = new String[0];
    private double[] scalerMean = new double[0];
    private double[] scalerStd = new double[0];
    private double[] weights = new double[0];
    private double bias = 0.0;

    private double learningRate = 0.05;
    private double lambdaL2 = 0.01;
    private int epochs = 300;

    public LogisticRegressionClassifier() {}

    public LogisticRegressionClassifier(double learningRate, double lambdaL2, int epochs) {
        this.learningRate = learningRate;
        this.lambdaL2 = lambdaL2;
        this.epochs = epochs;
    }

    @Override
    public void train(double[][] X, int[] y, String[] featureNames) {
        Objects.requireNonNull(X, "Training matrix X cannot be null");
        Objects.requireNonNull(y, "Target labels y cannot be null");
        int n = X.length;
        if (n == 0) return;
        int p = X[0].length;

        this.featureNames = featureNames != null ? featureNames.clone() : new String[p];
        this.scalerMean = new double[p];
        this.scalerStd = new double[p];
        this.weights = new double[p];
        this.bias = 0.0;

        // 1. Compute standardization statistics (mean & sample standard deviation)
        for (int j = 0; j < p; j++) {
            double sum = 0.0;
            for (int i = 0; i < n; i++) sum += X[i][j];
            scalerMean[j] = sum / n;

            double sqDiff = 0.0;
            for (int i = 0; i < n; i++) {
                double diff = X[i][j] - scalerMean[j];
                sqDiff += diff * diff;
            }
            double std = Math.sqrt(sqDiff / Math.max(1, n - 1));
            scalerStd[j] = std > 1e-6 ? std : 1.0;
        }

        // 2. Standardize training matrix
        double[][] scaledX = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                scaledX[i][j] = (X[i][j] - scalerMean[j]) / scalerStd[j];
            }
        }

        // 3. Gradient descent with L2 regularization
        double[] probs = new double[n];
        for (int epoch = 0; epoch < epochs; epoch++) {
            // Forward pass
            for (int i = 0; i < n; i++) {
                double z = bias;
                for (int j = 0; j < p; j++) {
                    z += weights[j] * scaledX[i][j];
                }
                probs[i] = sigmoid(z);
            }

            // Compute gradients
            double gradBias = 0.0;
            for (int i = 0; i < n; i++) {
                gradBias += (probs[i] - y[i]);
            }
            gradBias /= n;

            double[] gradWeights = new double[p];
            for (int j = 0; j < p; j++) {
                double g = 0.0;
                for (int i = 0; i < n; i++) {
                    g += (probs[i] - y[i]) * scaledX[i][j];
                }
                gradWeights[j] = (g / n) + (lambdaL2 * weights[j]);
            }

            // Update parameters
            bias -= learningRate * gradBias;
            for (int j = 0; j < p; j++) {
                weights[j] -= learningRate * gradWeights[j];
            }
        }
    }

    @Override
    public double predictProbability(double[] x) {
        if (weights.length == 0 || x == null || x.length != weights.length) {
            return 0.5;
        }
        double z = bias;
        for (int j = 0; j < weights.length; j++) {
            double val = (Double.isNaN(x[j]) || Double.isInfinite(x[j])) ? scalerMean[j] : x[j];
            double scaled = (val - scalerMean[j]) / scalerStd[j];
            z += weights[j] * scaled;
        }
        return sigmoid(z);
    }

    private static double sigmoid(double z) {
        if (z < -45.0) return 0.0;
        if (z > 45.0) return 1.0;
        return 1.0 / (1.0 + Math.exp(-z));
    }

    @Override
    public String getModelName() {
        return "LogisticRegressionClassifier";
    }

    @Override
    public String[] getFeatureNames() {
        return featureNames;
    }

    @Override
    public Map<String, Double> getFeatureImportances() {
        Map<String, Double> importances = new LinkedHashMap<>();
        double sum = 0.0;
        for (double w : weights) {
            sum += Math.abs(w);
        }
        for (int j = 0; j < featureNames.length; j++) {
            String name = featureNames[j];
            double imp = sum > 1e-6 ? Math.abs(weights[j]) / sum : (1.0 / featureNames.length);
            importances.put(name, Math.round(imp * 1000.0) / 1000.0);
        }
        return importances;
    }

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("MODEL=LogisticRegression\n");
        sb.append("FEATURES=").append(String.join(",", featureNames)).append("\n");
        sb.append("BIAS=").append(bias).append("\n");
        sb.append("WEIGHTS=").append(joinDoubles(weights)).append("\n");
        sb.append("SCALER_MEAN=").append(joinDoubles(scalerMean)).append("\n");
        sb.append("SCALER_STD=").append(joinDoubles(scalerStd)).append("\n");
        return sb.toString();
    }

    @Override
    public void deserialize(String data) {
        for (String line : data.split("\n")) {
            line = line.trim();
            if (line.startsWith("FEATURES=")) {
                this.featureNames = line.substring("FEATURES=".length()).split(",");
            } else if (line.startsWith("BIAS=")) {
                this.bias = Double.parseDouble(line.substring("BIAS=".length()));
            } else if (line.startsWith("WEIGHTS=")) {
                this.weights = parseDoubles(line.substring("WEIGHTS=".length()));
            } else if (line.startsWith("SCALER_MEAN=")) {
                this.scalerMean = parseDoubles(line.substring("SCALER_MEAN=".length()));
            } else if (line.startsWith("SCALER_STD=")) {
                this.scalerStd = parseDoubles(line.substring("SCALER_STD=".length()));
            }
        }
    }

    private static String joinDoubles(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static double[] parseDoubles(String csv) {
        String[] parts = csv.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        return out;
    }
}
