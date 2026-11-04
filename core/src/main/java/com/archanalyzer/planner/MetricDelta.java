package com.archanalyzer.planner;

/**
 * Encapsulates the quantitative delta and improvement evaluation for a single architecture metric.
 */
public class MetricDelta {

    public enum Preference {
        HIGHER_IS_BETTER,
        LOWER_IS_BETTER,
        NEUTRAL
    }

    private final String metricName;
    private final double baselineValue;
    private final double simulatedValue;
    private final double absoluteDelta;
    private final double percentageChange;
    private final Preference preference;
    private final boolean isImprovement;

    public MetricDelta(String metricName,
                       double baselineValue,
                       double simulatedValue,
                       Preference preference) {
        this.metricName = metricName;
        this.baselineValue = baselineValue;
        this.simulatedValue = simulatedValue;
        this.absoluteDelta = round2(simulatedValue - baselineValue);
        this.preference = preference;

        if (baselineValue == 0) {
            this.percentageChange = simulatedValue == 0 ? 0.0 : 100.0;
        } else {
            this.percentageChange = round2(((simulatedValue - baselineValue) / Math.abs(baselineValue)) * 100.0);
        }

        if (preference == Preference.HIGHER_IS_BETTER) {
            this.isImprovement = absoluteDelta > 0.001;
        } else if (preference == Preference.LOWER_IS_BETTER) {
            this.isImprovement = absoluteDelta < -0.001;
        } else {
            this.isImprovement = false;
        }
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    public String getMetricName() { return metricName; }
    public double getBaselineValue() { return baselineValue; }
    public double getSimulatedValue() { return simulatedValue; }
    public double getAbsoluteDelta() { return absoluteDelta; }
    public double getPercentageChange() { return percentageChange; }
    public Preference getPreference() { return preference; }
    public boolean isImprovement() { return isImprovement; }

    @Override
    public String toString() {
        return String.format("%s: %.2f -> %.2f (Δ %.2f, %.1f%%)%s",
                metricName, baselineValue, simulatedValue, absoluteDelta, percentageChange,
                isImprovement ? " [IMPROVEMENT]" : "");
    }
}
