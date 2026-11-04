package com.archanalyzer.research.adaptation;

import java.util.*;

/**
 * AdaptationMetrics
 * =================
 * High-precision Java metric evaluator for Just-In-Time Software Defect Prediction.
 * Computes:
 * - Accuracy, Balanced Accuracy
 * - Precision, Recall, F1-Score
 * - Matthews Correlation Coefficient (MCC)
 * - Area Under Precision-Recall Curve (PR-AUC / Average Precision)
 * - Area Under Receiver Operating Characteristic (ROC-AUC)
 * - Brier Score Loss
 * - 10-Bin Expected Calibration Error (ECE)
 */
public final class AdaptationMetrics {

    public static class MetricsResult {
        public final int sampleCount;
        public final int defectCount;
        public final double prevalence;
        public final double threshold;
        public final int tp, fp, tn, fn;
        public final double accuracy;
        public final double balancedAccuracy;
        public final double precision;
        public final double recall;
        public final double f1;
        public final double mcc;
        public final double prAuc;
        public final double rocAuc;
        public final double brierScore;
        public final double ece;

        public MetricsResult(
                int sampleCount, int defectCount, double prevalence, double threshold,
                int tp, int fp, int tn, int fn,
                double accuracy, double balancedAccuracy, double precision, double recall,
                double f1, double mcc, double prAuc, double rocAuc, double brierScore, double ece
        ) {
            this.sampleCount = sampleCount;
            this.defectCount = defectCount;
            this.prevalence = prevalence;
            this.threshold = threshold;
            this.tp = tp;
            this.fp = fp;
            this.tn = tn;
            this.fn = fn;
            this.accuracy = accuracy;
            this.balancedAccuracy = balancedAccuracy;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
            this.mcc = mcc;
            this.prAuc = prAuc;
            this.rocAuc = rocAuc;
            this.brierScore = brierScore;
            this.ece = ece;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sample_count", sampleCount);
            map.put("defect_count", defectCount);
            map.put("prevalence", prevalence);
            map.put("threshold", threshold);
            map.put("TP", tp);
            map.put("FP", fp);
            map.put("TN", tn);
            map.put("FN", fn);
            map.put("accuracy", accuracy);
            map.put("balanced_accuracy", balancedAccuracy);
            map.put("precision", precision);
            map.put("recall", recall);
            map.put("f1", f1);
            map.put("mcc", mcc);
            map.put("pr_auc", prAuc);
            map.put("roc_auc", rocAuc);
            map.put("brier_score", brierScore);
            map.put("ece", ece);
            return map;
        }
    }

    private AdaptationMetrics() {}

    public static MetricsResult evaluate(int[] yTrue, double[] yProba, double threshold) {
        Objects.requireNonNull(yTrue, "yTrue cannot be null");
        Objects.requireNonNull(yProba, "yProba cannot be null");
        int n = yTrue.length;
        if (n != yProba.length || n == 0) {
            throw new IllegalArgumentException("Arrays must be non-empty and equal length: yTrue=" + n + ", yProba=" + yProba.length);
        }

        int tp = 0, fp = 0, tn = 0, fn = 0;
        int totalDefects = 0;
        double brierSum = 0.0;

        for (int i = 0; i < n; i++) {
            int y = yTrue[i];
            double p = yProba[i];
            if (y == 1) totalDefects++;

            int pred = (p >= threshold) ? 1 : 0;
            if (y == 1 && pred == 1) tp++;
            else if (y == 0 && pred == 1) fp++;
            else if (y == 0 && pred == 0) tn++;
            else if (y == 1 && pred == 0) fn++;

            double diff = p - y;
            brierSum += diff * diff;
        }

        double prevalence = (double) totalDefects / n;
        double accuracy = (double) (tp + tn) / n;

        double tpr = (tp + fn > 0) ? (double) tp / (tp + fn) : 0.0;
        double tnr = (tn + fp > 0) ? (double) tn / (tn + fp) : 0.0;
        double balancedAccuracy = 0.5 * (tpr + tnr);

        double precision = (tp + fp > 0) ? (double) tp / (tp + fp) : 0.0;
        double recall = tpr;
        double f1 = (precision + recall > 0.0) ? (2.0 * precision * recall) / (precision + recall) : 0.0;

        double mccDenominator = Math.sqrt((double) (tp + fp) * (tp + fn) * (tn + fp) * (tn + fn));
        double mcc = (mccDenominator > 0.0) ? ((double) tp * tn - (double) fp * fn) / mccDenominator : 0.0;

        double brierScore = brierSum / n;
        double ece = computeECE(yTrue, yProba, 10);
        double prAuc = computePRAUC(yTrue, yProba);
        double rocAuc = computeROCAUC(yTrue, yProba);

        return new MetricsResult(
                n, totalDefects, prevalence, threshold,
                tp, fp, tn, fn,
                accuracy, balancedAccuracy, precision, recall, f1, mcc,
                prAuc, rocAuc, brierScore, ece
        );
    }

    public static double computeECE(int[] yTrue, double[] yProba, int nBins) {
        int n = yTrue.length;
        if (n == 0) return 0.0;

        double binWidth = 1.0 / nBins;
        double ece = 0.0;

        for (int b = 0; b < nBins; b++) {
            double low = b * binWidth;
            double high = (b + 1) * binWidth;
            int count = 0;
            int trueCount = 0;
            double confSum = 0.0;

            for (int i = 0; i < n; i++) {
                double p = yProba[i];
                boolean inBin = (b == nBins - 1) ? (p >= low && p <= high) : (p >= low && p < high);
                if (inBin) {
                    count++;
                    confSum += p;
                    if (yTrue[i] == 1) trueCount++;
                }
            }

            if (count > 0) {
                double binAcc = (double) trueCount / count;
                double binConf = confSum / count;
                ece += ((double) count / n) * Math.abs(binAcc - binConf);
            }
        }
        return ece;
    }

    public static double computeROCAUC(int[] yTrue, double[] yProba) {
        int n = yTrue.length;
        int posCount = 0;
        for (int y : yTrue) if (y == 1) posCount++;
        int negCount = n - posCount;

        if (posCount == 0 || negCount == 0) {
            return 0.5; // Single-class degenerate case
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(yProba[b], yProba[a]));

        long rankSum = 0;
        for (int rank = 0; rank < n; rank++) {
            if (yTrue[order[rank]] == 1) {
                rankSum += (n - rank);
            }
        }

        // Wilcoxon-Mann-Whitney formulation
        double u = rankSum - ((long) posCount * (posCount + 1)) / 2.0;
        return u / ((double) posCount * negCount);
    }

    public static double computePRAUC(int[] yTrue, double[] yProba) {
        int n = yTrue.length;
        int totalPos = 0;
        for (int y : yTrue) if (y == 1) totalPos++;

        if (totalPos == 0) {
            return 0.0;
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(yProba[b], yProba[a]));

        double ap = 0.0;
        int truePosSoFar = 0;
        double prevRecall = 0.0;

        for (int i = 0; i < n; i++) {
            if (yTrue[order[i]] == 1) {
                truePosSoFar++;
                double currentPrecision = (double) truePosSoFar / (i + 1);
                double currentRecall = (double) truePosSoFar / totalPos;
                ap += currentPrecision * (currentRecall - prevRecall);
                prevRecall = currentRecall;
            }
        }
        return ap;
    }
}
