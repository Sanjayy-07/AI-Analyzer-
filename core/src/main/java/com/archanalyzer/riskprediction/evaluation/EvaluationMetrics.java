package com.archanalyzer.riskprediction.evaluation;

/**
 * Encapsulates standard empirical machine learning evaluation metrics.
 */
public class EvaluationMetrics {

    private final String modelName;
    private final int sampleCount;
    private final int positiveCount;
    private final double precision;
    private final double recall;
    private final double f1Score;
    private final double rocAuc;
    private final double prAuc;
    private final double brierScore;
    private final double precisionAt20;
    private final double recallAt20;

    public EvaluationMetrics(String modelName,
                             int sampleCount,
                             int positiveCount,
                             double precision,
                             double recall,
                             double f1Score,
                             double rocAuc,
                             double prAuc,
                             double brierScore,
                             double precisionAt20,
                             double recallAt20) {
        this.modelName = modelName;
        this.sampleCount = sampleCount;
        this.positiveCount = positiveCount;
        this.precision = round3(precision);
        this.recall = round3(recall);
        this.f1Score = round3(f1Score);
        this.rocAuc = round3(rocAuc);
        this.prAuc = round3(prAuc);
        this.brierScore = round3(brierScore);
        this.precisionAt20 = round3(precisionAt20);
        this.recallAt20 = round3(recallAt20);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public String getModelName() { return modelName; }
    public int getSampleCount() { return sampleCount; }
    public int getPositiveCount() { return positiveCount; }
    public double getPrecision() { return precision; }
    public double getRecall() { return recall; }
    public double getF1Score() { return f1Score; }
    public double getRocAuc() { return rocAuc; }
    public double getPrAuc() { return prAuc; }
    public double getBrierScore() { return brierScore; }
    public double getPrecisionAt20() { return precisionAt20; }
    public double getRecallAt20() { return recallAt20; }

    public String modelName() { return modelName; }
    public int sampleCount() { return sampleCount; }
    public int positiveCount() { return positiveCount; }
    public double precision() { return precision; }
    public double recall() { return recall; }
    public double f1() { return f1Score; }
    public double f1Score() { return f1Score; }
    public double rocAuc() { return rocAuc; }
    public double prAuc() { return prAuc; }
    public double brierScore() { return brierScore; }
    public double precisionAt20() { return precisionAt20; }
    public double recallAt20() { return recallAt20; }

    @Override
    public String toString() {
        return String.format("%s: P=%.3f, R=%.3f, F1=%.3f, ROC-AUC=%.3f, PR-AUC=%.3f, Brier=%.3f, P@20=%.3f",
                modelName, precision, recall, f1Score, rocAuc, prAuc, brierScore, precisionAt20);
    }
}
