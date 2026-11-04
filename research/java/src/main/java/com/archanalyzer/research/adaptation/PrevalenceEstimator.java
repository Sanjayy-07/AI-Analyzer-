package com.archanalyzer.research.adaptation;

import java.util.Objects;

/**
 * PrevalenceEstimator
 * ===================
 * Implements unsupervised and supervised target-project defect prevalence estimation algorithms
 * for Phase 3A Just-In-Time Software Defect Prediction.
 *
 * Estimators:
 * 1. Soft Mean: Unadjusted average of target prediction scores.
 * 2. Black Box Shift Estimator (BBSE / Adjusted Count): Confusion-matrix inversion.
 * 3. Saerens Expectation-Maximization (EM): Iterative ML estimation of target prior odds.
 * 4. Empirical Burn-in: Simple fraction of observed defect labels.
 * 5. Bayesian Beta-Binomial: Prior-smoothed estimate with source pseudo-counts.
 */
public final class PrevalenceEstimator {

    public static final double DEFAULT_EPSILON = 1e-4;
    public static final double DEFAULT_PI_SOURCE = 0.111394; // TD Dataset 16-project training prevalence

    private PrevalenceEstimator() {}

    /**
     * Soft Mean Prevalence Estimator:
     * \hat{\pi}_{soft} = \frac{1}{N} \sum_{i=1}^N p_i
     */
    public static double estimateSoftMean(double[] probabilities) {
        Objects.requireNonNull(probabilities, "probabilities cannot be null");
        if (probabilities.length == 0) {
            throw new IllegalArgumentException("probabilities array cannot be empty");
        }
        double sum = 0.0;
        for (double p : probabilities) {
            validateProbability(p);
            sum += p;
        }
        return clamp(sum / probabilities.length, DEFAULT_EPSILON, 1.0 - DEFAULT_EPSILON);
    }

    /**
     * Black Box Shift Estimator (BBSE / Adjusted Count):
     * Inverts source classifier TPR and FPR against target predicted positive rate \hat{\mu}:
     * \hat{\pi}_{BBSE} = \frac{\hat{\mu} - FPR_{source}}{TPR_{source} - FPR_{source}}
     */
    public static double estimateBBSE(
            double[] probabilities,
            double threshold,
            double tprSource,
            double fprSource
    ) {
        Objects.requireNonNull(probabilities, "probabilities cannot be null");
        if (probabilities.length == 0) {
            throw new IllegalArgumentException("probabilities array cannot be empty");
        }
        validateProbability(threshold);
        validateProbability(tprSource);
        validateProbability(fprSource);

        double denom = tprSource - fprSource;
        if (Math.abs(denom) < 1e-6) {
            throw new ArithmeticException("BBSE singular: TPR_source and FPR_source are nearly identical (" + denom + ")");
        }

        int positiveCount = 0;
        for (double p : probabilities) {
            validateProbability(p);
            if (p >= threshold) {
                positiveCount++;
            }
        }
        double muHat = (double) positiveCount / probabilities.length;
        double piHat = (muHat - fprSource) / denom;
        return clamp(piHat, DEFAULT_EPSILON, 1.0 - DEFAULT_EPSILON);
    }

    /**
     * Saerens EM Algorithm for Prior Shift:
     * Iteratively estimates target prior odds \pi^{(t)} by treating target class labels
     * as missing latent variables under class-conditional score invariance P(X|Y).
     *
     * E-step:
     * p_i^{(t+1)} = \frac{p_i^{(0)} \frac{\pi^{(t)}}{\pi_s}}{p_i^{(0)} \frac{\pi^{(t)}}{\pi_s} + (1 - p_i^{(0)}) \frac{1 - \pi^{(t)}}{1 - \pi_s}}
     *
     * M-step:
     * \pi^{(t+1)} = \frac{1}{N} \sum_{i=1}^N p_i^{(t+1)}
     */
    public static double estimateSaerensEM(
            double[] probabilities,
            double piSource,
            int maxIter,
            double tol
    ) {
        Objects.requireNonNull(probabilities, "probabilities cannot be null");
        if (probabilities.length == 0) {
            throw new IllegalArgumentException("probabilities array cannot be empty");
        }
        validateStrictProbability(piSource, "piSource");
        if (maxIter <= 0) {
            throw new IllegalArgumentException("maxIter must be > 0");
        }
        if (tol <= 0.0) {
            throw new IllegalArgumentException("tol must be > 0.0");
        }

        int n = probabilities.length;
        double currentPi = estimateSoftMean(probabilities); // Initial estimate

        for (int iter = 0; iter < maxIter; iter++) {
            double nextSum = 0.0;
            double w1 = currentPi / piSource;
            double w0 = (1.0 - currentPi) / (1.0 - piSource);

            for (double p0 : probabilities) {
                validateProbability(p0);
                double pClamped = clamp(p0, 1e-6, 1.0 - 1e-6);
                double num = pClamped * w1;
                double denom = num + (1.0 - pClamped) * w0;
                double pUpdated = (denom > 0.0) ? (num / denom) : pClamped;
                nextSum += pUpdated;
            }

            double nextPi = clamp(nextSum / n, DEFAULT_EPSILON, 1.0 - DEFAULT_EPSILON);
            if (Math.abs(nextPi - currentPi) < tol) {
                return nextPi;
            }
            currentPi = nextPi;
        }

        return currentPi;
    }

    /**
     * Empirical Burn-in Prevalence:
     * Fraction of matured defect labels in the burn-in window.
     */
    public static double estimateEmpiricalBurnIn(int[] labels, boolean[] isMatured) {
        Objects.requireNonNull(labels, "labels cannot be null");
        if (labels.length == 0) {
            throw new IllegalArgumentException("labels array cannot be empty");
        }
        if (isMatured != null && isMatured.length != labels.length) {
            throw new IllegalArgumentException("isMatured length must match labels length");
        }

        int maturedCount = 0;
        int faultCount = 0;
        for (int i = 0; i < labels.length; i++) {
            if (isMatured == null || isMatured[i]) {
                maturedCount++;
                if (labels[i] == 1) {
                    faultCount++;
                } else if (labels[i] != 0) {
                    throw new IllegalArgumentException("Label at index " + i + " must be 0 or 1, found: " + labels[i]);
                }
            }
        }

        if (maturedCount == 0) {
            return DEFAULT_PI_SOURCE; // Fallback to source when zero matured labels exist
        }
        return clamp((double) faultCount / maturedCount, DEFAULT_EPSILON, 1.0 - DEFAULT_EPSILON);
    }

    /**
     * Bayesian Beta-Binomial Smoothed Prevalence:
     * Incorporates source prior pseudo-counts M to prevent collapse on zero-defect projects:
     * \hat{\pi}_{Bayes} = \frac{k_{matured} + M \cdot \pi_{source}}{N_{matured} + M}
     */
    public static double estimateBayesianBetaBinomial(
            int[] labels,
            boolean[] isMatured,
            double piSource,
            double pseudoWeightM
    ) {
        Objects.requireNonNull(labels, "labels cannot be null");
        validateStrictProbability(piSource, "piSource");
        if (pseudoWeightM < 0.0) {
            throw new IllegalArgumentException("pseudoWeightM must be non-negative: " + pseudoWeightM);
        }

        int maturedCount = 0;
        int faultCount = 0;
        for (int i = 0; i < labels.length; i++) {
            if (isMatured == null || isMatured[i]) {
                maturedCount++;
                if (labels[i] == 1) {
                    faultCount++;
                } else if (labels[i] != 0) {
                    throw new IllegalArgumentException("Label at index " + i + " must be 0 or 1, found: " + labels[i]);
                }
            }
        }

        double numerator = faultCount + pseudoWeightM * piSource;
        double denominator = maturedCount + pseudoWeightM;
        return clamp(numerator / denominator, DEFAULT_EPSILON, 1.0 - DEFAULT_EPSILON);
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static void validateProbability(double p) {
        if (Double.isNaN(p) || Double.isInfinite(p) || p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid probability value: " + p);
        }
    }

    private static void validateStrictProbability(double p, String name) {
        if (Double.isNaN(p) || Double.isInfinite(p) || p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException(name + " must be in (0, 1), found: " + p);
        }
    }
}
