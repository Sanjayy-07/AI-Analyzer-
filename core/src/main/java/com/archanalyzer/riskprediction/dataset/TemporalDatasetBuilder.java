package com.archanalyzer.riskprediction.dataset;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.riskprediction.cochange.CoChangeGraph;
import com.archanalyzer.riskprediction.features.ComponentFeatureVector;
import com.archanalyzer.riskprediction.features.EvolutionaryFeatures;
import com.archanalyzer.riskprediction.features.StaticArchitectureFeatures;
import com.archanalyzer.riskprediction.history.GitCommit;
import com.archanalyzer.riskprediction.history.GitHistory;

import java.util.*;

/**
 * Constructs reproducible, leakage-safe temporal datasets for training and evaluating
 * architecture risk prediction models.
 *
 * CRITICAL LEAKAGE SAFEGUARD:
 * Commits are split strictly across the observation boundary T_obs.
 * Features are extracted strictly from [T_start, T_obs].
 * Labels are extracted strictly from (T_obs, T_target].
 * Future commits cannot alter past feature representations.
 */
public class TemporalDatasetBuilder {

    public TemporalDataset buildDataset(ProjectModel model,
                                         DependencyGraph graph,
                                         GitHistory history,
                                         long observationTimestampSeconds,
                                         long predictionHorizonSeconds,
                                         RiskLabel.LabelType labelType) {
        Objects.requireNonNull(model, "ProjectModel cannot be null");
        Objects.requireNonNull(graph, "DependencyGraph cannot be null");

        String projectName = model.getProjectName();
        long earliest = history != null && history.isAvailable() ? history.getEarliestTimestamp() : 0L;
        long latest = history != null && history.isAvailable() ? history.getLatestTimestamp() : 0L;

        // Auto-configure temporal cutoff if not explicitly set (70% observation, 30% horizon)
        if (observationTimestampSeconds <= 0 && latest > earliest) {
            observationTimestampSeconds = earliest + (long) ((latest - earliest) * 0.70);
            predictionHorizonSeconds = Math.max(86400L * 14L, latest - observationTimestampSeconds);
        } else if (predictionHorizonSeconds <= 0) {
            predictionHorizonSeconds = 86400L * 90L; // default 90 days
        }

        long obsWindowDuration = Math.max(86400L * 7L, observationTimestampSeconds - earliest);

        // 1. Partition history into strictly isolated temporal windows
        GitHistory obsHistory = (history != null && history.isAvailable())
                ? history.filterWindow(0L, observationTimestampSeconds)
                : GitHistory.empty(null);

        GitHistory futureHistory = (history != null && history.isAvailable())
                ? history.filterWindow(observationTimestampSeconds + 1L, observationTimestampSeconds + predictionHorizonSeconds)
                : GitHistory.empty(null);

        // 2. Mine co-change graph strictly from the observation window (NO FUTURE LEAKAGE)
        CoChangeGraph coChangeGraph = CoChangeGraph.build(obsHistory, model, 1);

        // Precompute SCCs for static features
        List<Set<String>> sccs = GraphAlgorithms.stronglyConnectedComponents(graph);
        Map<String, String> pathToComponent = CoChangeGraph.buildPathToComponentMap(model);

        List<DatasetInstance> instances = new ArrayList<>();

        // 3. Extract instances for each component
        for (ProjectModel.ComponentNode node : model.getComponents()) {
            String componentId = node.getId();
            String relativePath = node.getId().replace('.', '/') + ".java";

            // Static features
            StaticArchitectureFeatures staticFeat = StaticArchitectureFeatures.extract(
                    componentId, graph, model, null, sccs);

            // Evolutionary features from observation window ONLY
            EvolutionaryFeatures evolFeat = EvolutionaryFeatures.extract(
                    componentId, relativePath, obsHistory, coChangeGraph,
                    observationTimestampSeconds, obsWindowDuration);

            ComponentFeatureVector fv = new ComponentFeatureVector(componentId, staticFeat, evolFeat);

            // Target labels from prediction horizon ONLY
            RiskLabel label = extractFutureLabel(componentId, relativePath, futureHistory, labelType);

            instances.add(new DatasetInstance(componentId, fv, label, observationTimestampSeconds, predictionHorizonSeconds));
        }

        return new TemporalDataset(projectName, instances, observationTimestampSeconds, predictionHorizonSeconds);
    }

    private RiskLabel extractFutureLabel(String componentId,
                                         String relativePath,
                                         GitHistory futureHistory,
                                         RiskLabel.LabelType labelType) {
        if (futureHistory == null || !futureHistory.isAvailable() || futureHistory.getCommits().isEmpty()) {
            return new RiskLabel(componentId, 0, 0, 0, labelType != null ? labelType : RiskLabel.LabelType.CHANGE_RISK_PROXY);
        }

        int bugFixes = 0;
        int totalCommits = 0;
        int totalChurn = 0;
        String simple = simpleName(componentId);

        for (GitCommit c : futureHistory.getCommits()) {
            boolean touched = c.touchesFile(relativePath);
            if (!touched) {
                for (String path : c.getChangedFiles().keySet()) {
                    if (path.contains(componentId.replace('.', '/')) || path.contains(simple + ".java")) {
                        touched = true;
                        var churn = c.getChangedFiles().get(path);
                        totalChurn += churn.totalChurn();
                        break;
                    }
                }
            } else {
                totalChurn += c.getChurn(relativePath).totalChurn();
            }

            if (touched) {
                totalCommits++;
                if (c.isBugFix()) {
                    bugFixes++;
                }
            }
        }

        RiskLabel.LabelType effectiveType = labelType != null ? labelType :
                (bugFixes > 0 ? RiskLabel.LabelType.DEFECT_RISK : RiskLabel.LabelType.CHANGE_RISK_PROXY);

        return new RiskLabel(componentId, bugFixes, totalCommits, totalChurn, effectiveType);
    }

    private static String simpleName(String fqn) {
        if (fqn == null) return "";
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
