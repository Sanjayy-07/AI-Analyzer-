package com.archanalyzer.research.adaptation;

import java.util.Arrays;
import java.util.Objects;

/**
 * ProbabilityAdapter
 * ==================
 * Implements numerically stable logit and sigmoid transformations, prior log-odds adjustments,
 * Platt scaling calibration, and empirical quantile threshold adaptation.
 *
 * Guarantees:
 * - Mathematical monotonicity: Log-odds shift preserves relative score ranking (p_i < p_j <=> p_i* < p_j*).
 * - Finite bounds: Automatic clamping to [epsilon, 1 - epsilon] prevents NaN or +/-Inf collapse.
 */
public final class ProbabilityAdapter {

    public static final double EPSILON = 1e-7;

    private ProbabilityAdapter() {}

    /**
     * Numerically stable logit function:
     * logit(p) = ln(p / (1 - p))
     */
    public static double logit(double p) {
        if (Double.isNaN(p) || Double.isInfinite(p)) {
            throw new IllegalArgumentException("Probability must be a finite number: " + p);
        }
        double pClamped = Math.max(EPSILON, Math.min(1.0 - EPSILON, p));
        return Math.log(pClamped / (1.0 - pClamped));
    }

    /**
     * Numerically stable sigmoid function:
     * sigma(z) = 1 / (1 + exp(-z))
     */
    public static double sigmoid(double z) {
        if (Double.isNaN(z)) {
            throw new IllegalArgumentException("Input z cannot be NaN");
        }
        if (z >= 0.0) {
            return 1.0 / (1.0 + Math.exp(-z));
        } else {
            double expZ = Math.exp(z);
            return expZ / (1.0 + expZ);
        }
    }

    /**
     * Prior Probability Log-Odds Adjustment:
     * logit(p*) = logit(p) - logit(pi_source) + logit(pi_target)
     * p* = sigmoid(logit(p*))
     */
    public static double adaptProbability(double p, double piSource, double piTarget) {
        if (piSource <= 0.0 || piSource >= 1.0) {
            throw new IllegalArgumentException("piSource must be in (0, 1): " + piSource);
        }
        if (piTarget <= 0.0 || piTarget >= 1.0) {
            throw new IllegalArgumentException("piTarget must be in (0, 1): " + piTarget);
        }
        double logitP = logit(p);
        double delta = logit(piTarget) - logit(piSource);
        return sigmoid(logitP + delta);
    }

    /**
     * Batch log-odds adaptation for array of probabilities.
     */
    public static double[] adaptProbabilities(double[] probabilities, double piSource, double piTarget) {
        Objects.requireNonNull(probabilities, "probabilities array cannot be null");
        double[] adapted = new double[probabilities.length];
        double delta = logit(piTarget) - logit(piSource);
        for (int i = 0; i < probabilities.length; i++) {
            adapted[i] = sigmoid(logit(probabilities[i]) + delta);
        }
        return adapted;
    }

    /**
     * Platt Scaling Calibration on burn-in probabilities:
     * Fits logistic regression: P(Y=1|p) = sigma(a * logit(p) + b) via mini Newton-Raphson.
     * Returns double[] {a, b}.
     */
    public static double[] fitPlattScaling(double[] probabilities, int[] labels, int maxIter) {
        Objects.requireNonNull(probabilities, "probabilities cannot be null");
        Objects.requireNonNull(labels, "labels cannot be null");
        if (probabilities.length != labels.length || probabilities.length < 5) {
            throw new IllegalArgumentException("At least 5 sample pairs required for Platt scaling");
        }

        int n = probabilities.length;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = logit(probabilities[i]);
        }

        // Initialize a = 1.0 (slope), b = 0.0 (intercept)
        double a = 1.0;
        double b = 0.0;
        double lr = 0.1;

        for (int iter = 0; iter < maxIter; iter++) {
            double gradA = 0.0;
            double gradB = 0.0;
            for (int i = 0; i < n; i++) {
                double pred = sigmoid(a * x[i] + b);
                double err = pred - labels[i];
                gradA += err * x[i];
                gradB += err;
            }
            a -= lr * (gradA / n);
            b -= lr * (gradB / n);
            // Constrain a to be positive to maintain monotonicity
            a = Math.max(0.01, a);
        }

        return new double[] { a, b };
    }

    /**
     * Applies fitted Platt scaling parameters {a, b} to an array of probabilities.
     */
    public static double[] applyPlattScaling(double[] probabilities, double a, double b) {
        Objects.requireNonNull(probabilities, "probabilities cannot be null");
        double[] scaled = new double[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            scaled[i] = sigmoid(a * logit(probabilities[i]) + b);
        }
        return scaled;
    }

    /**
     * Target Quantile Threshold Adaptation:
     * Determines threshold tau such that P(p >= tau) == pi_target.
     */
    public static double computeQuantileThreshold(double[] probabilities, double piTarget) {
        Objects.requireNonNull(probabilities, "probabilities cannot be null");
        if (probabilities.length == 0) {
            throw new IllegalArgumentException("probabilities array cannot be empty");
        }
        double[] sorted = probabilities.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        int targetIndex = (int) Math.round((1.0 - piTarget) * (n - 1));
        targetIndex = Math.max(0, Math.min(n - 1, targetIndex));
        return sorted[targetIndex];
    }
}
