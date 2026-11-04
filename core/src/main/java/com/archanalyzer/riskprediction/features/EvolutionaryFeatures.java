package com.archanalyzer.riskprediction.features;

import com.archanalyzer.riskprediction.cochange.CoChangeGraph;
import com.archanalyzer.riskprediction.history.GitCommit;
import com.archanalyzer.riskprediction.history.GitHistory;

import java.util.*;

/**
 * Extracts evolutionary code-churn and co-change features strictly from historical
 * commits within the observation window [T_start, T_obs].
 * Prevents temporal leakage by strictly ignoring any future commits after T_obs.
 */
public class EvolutionaryFeatures {

    public static final String[] FEATURE_NAMES = {
            "commit_count",
            "churn_added",
            "churn_deleted",
            "churn_total",
            "change_frequency_weekly",
            "recent_change_ratio",
            "time_since_last_change_days",
            "distinct_change_episodes",
            "repeated_modifications",
            "volatility",
            "cochange_degree",
            "cochange_max_strength",
            "cochange_weighted_centrality"
    };

    private final double[] values;

    public EvolutionaryFeatures(double[] values) {
        if (values.length != FEATURE_NAMES.length) {
            throw new IllegalArgumentException("Expected " + FEATURE_NAMES.length + " evolutionary features, got " + values.length);
        }
        this.values = values;
    }

    public static EvolutionaryFeatures empty() {
        return new EvolutionaryFeatures(new double[FEATURE_NAMES.length]);
    }

    public static EvolutionaryFeatures extract(String componentId,
                                               GitHistory windowHistory,
                                               CoChangeGraph coChangeGraph,
                                               long observationTimestampSeconds,
                                               long windowDurationSeconds) {
        return extract(componentId, null, windowHistory, coChangeGraph, observationTimestampSeconds, windowDurationSeconds);
    }

    public static EvolutionaryFeatures extract(String componentId,
                                              String componentRelativePath,
                                              GitHistory windowHistory,
                                              CoChangeGraph coChangeGraph,
                                              long observationTimestampSeconds,
                                              long windowDurationSeconds) {
        double[] v = new double[FEATURE_NAMES.length];

        if (windowHistory == null || !windowHistory.isAvailable() || windowHistory.getCommits().isEmpty()) {
            // Default 0 for missing history
            return new EvolutionaryFeatures(v);
        }

        List<GitCommit> commits = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;
        Set<Long> distinctDays = new HashSet<>();
        long lastCommitTime = 0L;
        int repeatedMods = 0;
        long prevCommitTime = 0L;

        // Sub-window threshold for recent change (last 30% of observation window)
        long recentThreshold = observationTimestampSeconds - (long) (windowDurationSeconds * 0.30);
        int recentCommits = 0;

        for (GitCommit c : windowHistory.getCommits()) {
            // Check if commit touched this component
            boolean touched = (componentRelativePath != null && c.touchesFile(componentRelativePath));
            if (!touched) {
                // Check if any file path matches componentId
                for (String path : c.getChangedFiles().keySet()) {
                    if (path.contains(componentId.replace('.', '/')) || path.contains(simpleName(componentId) + ".java")) {
                        touched = true;
                        var churn = c.getChangedFiles().get(path);
                        totalAdded += churn.linesAdded();
                        totalDeleted += churn.linesDeleted();
                        break;
                    }
                }
            } else {
                var churn = c.getChurn(componentRelativePath);
                totalAdded += churn.linesAdded();
                totalDeleted += churn.linesDeleted();
            }

            if (touched) {
                commits.add(c);
                long t = c.getTimestampSeconds();
                distinctDays.add(t / 86400L); // 86400 seconds in a day

                if (t >= recentThreshold) {
                    recentCommits++;
                }

                if (prevCommitTime > 0 && (t - prevCommitTime) <= 172800L) { // 48 hours
                    repeatedMods++;
                }
                prevCommitTime = t;
                lastCommitTime = Math.max(lastCommitTime, t);
            }
        }

        int commitCount = commits.size();
        int totalChurn = totalAdded + totalDeleted;
        double windowWeeks = Math.max(1.0, windowDurationSeconds / (86400.0 * 7.0));
        double changeFreqWeekly = commitCount / windowWeeks;
        double recentRatio = commitCount > 0 ? (double) recentCommits / commitCount : 0.0;

        double daysSinceLastChange = lastCommitTime > 0
                ? Math.max(0.0, (observationTimestampSeconds - lastCommitTime) / 86400.0)
                : (windowDurationSeconds / 86400.0);

        int distinctEpisodes = distinctDays.size();
        double volatility = commitCount > 0 ? (double) totalChurn / commitCount : 0.0;

        // Co-change features
        int coChangeDeg = coChangeGraph != null ? coChangeGraph.getCoChangeDegree(componentId) : 0;
        double coChangeMax = coChangeGraph != null ? coChangeGraph.getCoChangeMaxStrength(componentId) : 0.0;
        double coChangeWeight = coChangeGraph != null ? coChangeGraph.getCoChangeWeightedCentrality(componentId) : 0.0;

        v[0] = commitCount;
        v[1] = totalAdded;
        v[2] = totalDeleted;
        v[3] = totalChurn;
        v[4] = changeFreqWeekly;
        v[5] = recentRatio;
        v[6] = daysSinceLastChange;
        v[7] = distinctEpisodes;
        v[8] = repeatedMods;
        v[9] = volatility;
        v[10] = coChangeDeg;
        v[11] = coChangeMax;
        v[12] = coChangeWeight;

        return new EvolutionaryFeatures(v);
    }

    private static String simpleName(String fqn) {
        if (fqn == null) return "";
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    public double[] getValues() { return values; }
    public static String[] getFeatureNames() { return FEATURE_NAMES; }

    public double commitCount() { return values[0]; }
    public double churnAdded() { return values[1]; }
    public double churnDeleted() { return values[2]; }
    public double churnTotal() { return values[3]; }
    public double changeFrequencyWeekly() { return values[4]; }
    public double recentChangeRatio() { return values[5]; }
    public double daysSinceLastChange() { return values[6]; }
    public double timeSinceLastChange() { return values[6] * 86400.0; }
    public double distinctChangeEpisodes() { return values[7]; }
    public double repeatedModifications() { return values[8]; }
    public double volatility() { return values[9]; }
    public double cochangeDegree() { return values[10]; }
    public double cochangeMaxStrength() { return values[11]; }
    public double cochangeWeightedCentrality() { return values[12]; }

    public Map<String, Double> asMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < FEATURE_NAMES.length; i++) {
            map.put(FEATURE_NAMES[i], values[i]);
        }
        return map;
    }
}
