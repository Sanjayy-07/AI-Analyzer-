package com.archanalyzer.riskprediction.evaluation;

import com.archanalyzer.riskprediction.model.TrainableClassifier;

import java.util.*;

/**
 * Computes empirical software defect & change risk evaluation metrics:
 * Precision, Recall, F1, ROC-AUC, PR-AUC, Brier Score, Precision@20%, Recall@20%.
 */
public class ModelEvaluator {

    public EvaluationMetrics evaluate(TrainableClassifier model, double[][] X, int[] y) {
        Objects.requireNonNull(model, "Model cannot be null");
        Objects.requireNonNull(X, "Evaluation matrix X cannot be null");
        Objects.requireNonNull(y, "Target labels y cannot be null");

        int n = X.length;
        if (n == 0) {
            return new EvaluationMetrics(model.getModelName(), 0, 0, 0, 0, 0, 0.5, 0, 0.25, 0, 0);
        }

        double[] probs = new double[n];
        int tp = 0, fp = 0, tn = 0, fn = 0;
        int totalPositives = 0;
        double brierSum = 0.0;

        for (int i = 0; i < n; i++) {
            probs[i] = model.predictProbability(X[i]);
            int pred = probs[i] >= 0.5 ? 1 : 0;
            int actual = y[i];

            if (actual == 1) totalPositives++;

            if (pred == 1 && actual == 1) tp++;
            else if (pred == 1 && actual == 0) fp++;
            else if (pred == 0 && actual == 0) tn++;
            else if (pred == 0 && actual == 1) fn++;

            double err = probs[i] - actual;
            brierSum += err * err;
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
        double f1 = (precision + recall) > 0 ? (2.0 * precision * recall) / (precision + recall) : 0.0;
        double brier = brierSum / n;

        double rocAuc = computeRocAuc(probs, y);
        double prAuc = computePrAuc(probs, y);

        // Compute Top 20% ranking metrics (Precision@20, Recall@20)
        int k = Math.max(1, (int) Math.ceil(0.20 * n));
        RankingMetrics topK = computeTopK(probs, y, k);

        return new EvaluationMetrics(
                model.getModelName(),
                n,
                totalPositives,
                precision,
                recall,
                f1,
                rocAuc,
                prAuc,
                brier,
                topK.precisionAtK,
                topK.recallAtK
        );
    }

    private record RankingMetrics(double precisionAtK, double recallAtK) {}

    private RankingMetrics computeTopK(double[] probs, int[] y, int k) {
        int n = probs.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(probs[b], probs[a]));

        int posInTopK = 0;
        int totalPos = 0;
        for (int i = 0; i < n; i++) {
            if (y[i] == 1) totalPos++;
            if (i < k && y[order[i]] == 1) {
                posInTopK++;
            }
        }

        double pAtK = (double) posInTopK / k;
        double rAtK = totalPos > 0 ? (double) posInTopK / totalPos : 0.0;
        return new RankingMetrics(pAtK, rAtK);
    }

    /**
     * Computes ROC-AUC via trapezoidal integration of TPR vs FPR across sorted thresholds.
     */
    private double computeRocAuc(double[] probs, int[] y) {
        int n = probs.length;
        int pos = 0, neg = 0;
        for (int label : y) {
            if (label == 1) pos++;
            else neg++;
        }
        if (pos == 0 || neg == 0) return 0.5;

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(probs[b], probs[a]));

        double auc = 0.0;
        int tp = 0, fp = 0;
        double prevFpr = 0.0, prevTpr = 0.0;

        for (int i = 0; i < n; i++) {
            int idx = order[i];
            if (y[idx] == 1) tp++;
            else fp++;

            // Only update trapezoid when threshold changes or at the end (Fawcett 2006 Algorithm 1)
            if (i == n - 1 || Double.compare(probs[order[i]], probs[order[i + 1]]) != 0) {
                double curFpr = (double) fp / neg;
                double curTpr = (double) tp / pos;

                auc += (curFpr - prevFpr) * (curTpr + prevTpr) / 2.0;
                prevFpr = curFpr;
                prevTpr = curTpr;
            }
        }

        return Math.max(0.0, Math.min(1.0, auc));
    }

    /**
     * Computes PR-AUC via trapezoidal integration of Precision vs Recall.
     */
    private double computePrAuc(double[] probs, int[] y) {
        int n = probs.length;
        int totalPos = 0;
        for (int label : y) {
            if (label == 1) totalPos++;
        }
        if (totalPos == 0) return 0.0;

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(probs[b], probs[a]));

        double prAuc = 0.0;
        int tp = 0;
        double prevRecall = 0.0;

        for (int k = 1; k <= n; k++) {
            if (y[order[k - 1]] == 1) tp++;

            // Only update PR-AUC slice when threshold changes or at the end
            if (k == n || Double.compare(probs[order[k - 1]], probs[order[k]]) != 0) {
                double curRecall = (double) tp / totalPos;
                double curPrecision = (double) tp / k;

                prAuc += (curRecall - prevRecall) * curPrecision;
                prevRecall = curRecall;
            }
        }

        return Math.max(0.0, Math.min(1.0, prAuc));
    }
}
