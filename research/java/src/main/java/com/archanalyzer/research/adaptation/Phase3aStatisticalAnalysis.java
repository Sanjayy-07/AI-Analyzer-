package com.archanalyzer.research.adaptation;

import java.util.*;

/**
 * Phase3aStatisticalAnalysis
 * ==========================
 * Non-parametric statistical analysis module for cross-project defect prediction evaluations.
 * Implements:
 * 1. Paired Wilcoxon Signed-Rank Test with exact small-sample permutation distribution.
 * 2. Rank-Biserial Correlation effect size (r_rb = (W+ - W-) / (W+ + W-)).
 * 3. Macro-average, median, interquartile range (IQR), win/loss/tie counters.
 */
public final class Phase3aStatisticalAnalysis {

    public static class WilcoxonResult {
        public final int sampleSize;
        public final int nonZeroCount;
        public final double wPlus;
        public final double wMinus;
        public final double wStatistic;
        public final double pValue;
        public final double rankBiserialR;
        public final double meanDiff;
        public final double medianDiff;
        public final int wins;
        public final int losses;
        public final int ties;

        public WilcoxonResult(
                int sampleSize, int nonZeroCount,
                double wPlus, double wMinus, double wStatistic, double pValue,
                double rankBiserialR, double meanDiff, double medianDiff,
                int wins, int losses, int ties
        ) {
            this.sampleSize = sampleSize;
            this.nonZeroCount = nonZeroCount;
            this.wPlus = wPlus;
            this.wMinus = wMinus;
            this.wStatistic = wStatistic;
            this.pValue = pValue;
            this.rankBiserialR = rankBiserialR;
            this.meanDiff = meanDiff;
            this.medianDiff = medianDiff;
            this.wins = wins;
            this.losses = losses;
            this.ties = ties;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sample_size_projects", sampleSize);
            map.put("non_zero_diff_count", nonZeroCount);
            map.put("w_plus", wPlus);
            map.put("w_minus", wMinus);
            map.put("W_statistic", wStatistic);
            map.put("p_value", pValue);
            map.put("rank_biserial_r", rankBiserialR);
            map.put("mean_diff", meanDiff);
            map.put("median_diff", medianDiff);
            map.put("wins", wins);
            map.put("losses", losses);
            map.put("ties", ties);
            return map;
        }
    }

    private Phase3aStatisticalAnalysis() {}

    /**
     * Computes the paired Wilcoxon Signed-Rank test across project-level metric pairs.
     * x: treatment / adapted metric
     * y: control / baseline metric
     */
    public static WilcoxonResult pairedWilcoxon(double[] x, double[] y) {
        Objects.requireNonNull(x, "x cannot be null");
        Objects.requireNonNull(y, "y cannot be null");
        if (x.length != y.length || x.length == 0) {
            throw new IllegalArgumentException("Arrays must be equal length and non-empty");
        }

        int n = x.length;
        double[] diffs = new double[n];
        int wins = 0, losses = 0, ties = 0;
        List<Double> nonZeroDiffs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            double d = x[i] - y[i];
            diffs[i] = d;
            if (d > 1e-7) {
                wins++;
                nonZeroDiffs.add(d);
            } else if (d < -1e-7) {
                losses++;
                nonZeroDiffs.add(d);
            } else {
                ties++;
            }
        }

        int nNonZero = nonZeroDiffs.size();
        if (nNonZero == 0) {
            return new WilcoxonResult(n, 0, 0, 0, 0, 1.0, 0.0, 0.0, 0.0, wins, losses, ties);
        }

        // Rank absolute differences with average ranks for ties
        List<RankItem> items = new ArrayList<>(nNonZero);
        for (int i = 0; i < nNonZero; i++) {
            items.add(new RankItem(nonZeroDiffs.get(i), Math.abs(nonZeroDiffs.get(i))));
        }
        items.sort(Comparator.comparingDouble(a -> a.absVal));

        double[] ranks = new double[nNonZero];
        int i = 0;
        while (i < nNonZero) {
            int j = i;
            while (j < nNonZero && Math.abs(items.get(j).absVal - items.get(i).absVal) < 1e-9) {
                j++;
            }
            double avgRank = (i + 1 + j) / 2.0;
            for (int k = i; k < j; k++) {
                ranks[k] = avgRank;
            }
            i = j;
        }

        double wPlus = 0.0;
        double wMinus = 0.0;
        for (int k = 0; k < nNonZero; k++) {
            if (items.get(k).diff > 0) {
                wPlus += ranks[k];
            } else {
                wMinus += ranks[k];
            }
        }

        double wStat = Math.min(wPlus, wMinus);
        double totalRankSum = wPlus + wMinus;
        double rRb = (totalRankSum > 0.0) ? (wPlus - wMinus) / totalRankSum : 0.0;

        // Exact p-value for N <= 15 using combinatorial permutation
        double pValue = calculateExactPValue(nNonZero, wStat);

        double meanDiff = Arrays.stream(diffs).average().orElse(0.0);
        double medianDiff = computeMedian(diffs);

        return new WilcoxonResult(
                n, nNonZero, wPlus, wMinus, wStat, pValue, rRb, meanDiff, medianDiff, wins, losses, ties
        );
    }

    private static class RankItem {
        final double diff;
        final double absVal;
        RankItem(double diff, double absVal) {
            this.diff = diff;
            this.absVal = absVal;
        }
    }

    /**
     * Exact two-sided p-value for small N <= 15 via exhaustive 2^N sign permutation.
     */
    private static double calculateExactPValue(int n, double observedW) {
        if (n > 15) {
            // Asymptotic normal approximation with continuity correction
            double mean = n * (n + 1.0) / 4.0;
            double var = n * (n + 1.0) * (2.0 * n + 1.0) / 24.0;
            double z = (observedW - mean + 0.5) / Math.sqrt(var);
            return 2.0 * (1.0 - normalCdf(Math.abs(z)));
        }

        int totalPermutations = 1 << n;
        int countLessOrEqual = 0;

        for (int mask = 0; mask < totalPermutations; mask++) {
            double currentW = 0.0;
            for (int bit = 0; bit < n; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    currentW += (bit + 1); // Rank is (bit + 1)
                }
            }
            if (currentW <= observedW) {
                countLessOrEqual++;
            }
        }

        double oneSidedP = (double) countLessOrEqual / totalPermutations;
        return Math.min(1.0, 2.0 * oneSidedP);
    }

    private static double normalCdf(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    private static double erf(double z) {
        // Abramowitz and Stegun approximation 7.1.26
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(z));
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
        double ans = 1.0 - poly * Math.exp(-z * z);
        return (z >= 0) ? ans : -ans;
    }

    public static double computeMedian(double[] vals) {
        if (vals.length == 0) return 0.0;
        double[] sorted = vals.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[mid];
        } else {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
    }
}
