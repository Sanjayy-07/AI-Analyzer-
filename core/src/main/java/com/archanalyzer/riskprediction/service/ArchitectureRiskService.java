package com.archanalyzer.riskprediction.service;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.riskprediction.cochange.CoChangeEdge;
import com.archanalyzer.riskprediction.cochange.CoChangeGraph;
import com.archanalyzer.riskprediction.dataset.RiskLabel;
import com.archanalyzer.riskprediction.dataset.TemporalDataset;
import com.archanalyzer.riskprediction.dataset.TemporalDatasetBuilder;
import com.archanalyzer.riskprediction.evaluation.AblationStudy;
import com.archanalyzer.riskprediction.features.ComponentFeatureVector;
import com.archanalyzer.riskprediction.features.EvolutionaryFeatures;
import com.archanalyzer.riskprediction.features.StaticArchitectureFeatures;
import com.archanalyzer.riskprediction.history.GitHistory;
import com.archanalyzer.riskprediction.history.GitHistoryExtractor;
import com.archanalyzer.riskprediction.model.RandomForestClassifier;
import com.archanalyzer.riskprediction.model.TrainableClassifier;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service facade for AI-Based Architecture Risk Prediction (Novelty 3).
 * Integrates static dependency graph metrics, historical Git churn, and evolutionary co-change mining
 * into a pure-Java machine learning ensemble to predict architectural defect and change risk.
 */
public class ArchitectureRiskService {

    public enum RiskCategory {
        HIGH,
        MEDIUM,
        LOW
    }

    public record CoChangeNeighborDto(
            String neighborComponent,
            int coChangeCount,
            double jaccardStrength
    ) {}

    public record ComponentRiskResult(
            String componentName,
            double riskProbability,
            double riskPercentage,
            RiskCategory riskCategory,
            boolean inCycle,
            List<String> topStaticFactors,
            List<String> topEvolutionaryFactors,
            List<CoChangeNeighborDto> coChangeNeighbors,
            Map<String, Double> staticMetrics,
            Map<String, Double> evolutionaryMetrics
    ) {}

    public record ProjectRiskOverview(
            String projectName,
            boolean gitHistoryAvailable,
            int totalCommits,
            int totalComponents,
            int highRiskCount,
            int mediumRiskCount,
            int lowRiskCount,
            double averageRiskProbability,
            List<ComponentRiskResult> components,
            List<String> cycleComponentsWithHighRisk,
            AblationStudy.AblationResult ablationResult,
            String modelDescription
    ) {}

    private final GitHistoryExtractor gitExtractor;
    private final TemporalDatasetBuilder datasetBuilder;
    private final AblationStudy ablationStudy;

    // Cache of latest assessments keyed by project identifier or path string
    private final Map<String, ProjectRiskOverview> cachedAssessments = new ConcurrentHashMap<>();

    public ArchitectureRiskService() {
        this(new GitHistoryExtractor(), new TemporalDatasetBuilder(), new AblationStudy());
    }

    public ArchitectureRiskService(GitHistoryExtractor gitExtractor,
                                   TemporalDatasetBuilder datasetBuilder,
                                   AblationStudy ablationStudy) {
        this.gitExtractor = Objects.requireNonNull(gitExtractor);
        this.datasetBuilder = Objects.requireNonNull(datasetBuilder);
        this.ablationStudy = Objects.requireNonNull(ablationStudy);
    }

    /**
     * Assesses architectural risk for all components in the project using static dependency graph
     * and historical Git metrics.
     */
    public ProjectRiskOverview assessProjectRisk(Path projectRoot, ProjectModel model, DependencyGraph graph) {
        return assessProjectRisk(projectRoot, model, graph, 0L, 0L);
    }

    /**
     * Assesses architectural risk with explicit temporal observation and prediction horizon boundaries.
     */
    public ProjectRiskOverview assessProjectRisk(Path projectRoot,
                                                ProjectModel model,
                                                DependencyGraph graph,
                                                long observationTimestamp,
                                                long predictionHorizon) {
        Objects.requireNonNull(model, "ProjectModel cannot be null");
        Objects.requireNonNull(graph, "DependencyGraph cannot be null");

        String cacheKey = (projectRoot != null ? projectRoot.toAbsolutePath().normalize().toString() : model.getProjectName());

        // 1. Identify cyclic components (cross-reference with Novelty 1 cycle detection)
        Set<String> cyclicComponents = findCyclicComponents(graph);

        // 2. Extract Git History safely
        GitHistory history = (projectRoot != null) ? gitExtractor.extract(projectRoot) : GitHistory.empty(null);
        boolean gitAvailable = history != null && history.isAvailable() && history.getCommitCount() >= 2;

        AblationStudy.AblationResult ablationResult = null;
        TrainableClassifier trainedModel = null;
        CoChangeGraph coChangeGraph = null;

        // 3. Temporal dataset construction & ML training
        if (gitAvailable) {
            TemporalDataset fullDataset = datasetBuilder.buildDataset(
                    model, graph, history, observationTimestamp, predictionHorizon, RiskLabel.LabelType.DEFECT_RISK
            );

            // Compute co-change graph strictly up to observation timestamp
            long obsTime = fullDataset.getObservationTimestamp();
            GitHistory obsHistory = history.filterWindow(0L, obsTime);
            coChangeGraph = CoChangeGraph.build(obsHistory, model, 1);

            // If we have enough components and instances, run ablation and train ensemble
            if (fullDataset.size() >= 4) {
                // Check if there is variation in target labels
                var matrix = fullDataset.getCombinedMatrix();
                int positiveCount = 0;
                for (int y : matrix.y()) {
                    if (y == 1) positiveCount++;
                }

                if (positiveCount > 0 && positiveCount < matrix.y().length) {
                    TemporalDataset[] splits = fullDataset.split(0.70, 42L);
                    try {
                        ablationResult = ablationStudy.runAblation(splits[0], splits[1]);
                    } catch (Exception ignored) {
                        // In case of small dataset evaluation anomaly, proceed gracefully
                    }
                }

                // Train production multi-modal Random Forest ensemble
                try {
                    RandomForestClassifier rf = new RandomForestClassifier(20, 5, 2, 42L);
                    rf.train(matrix.X(), matrix.y(), matrix.featureNames());
                    trainedModel = rf;
                } catch (Exception ignored) {}
            }
        }

        if (coChangeGraph == null) {
            coChangeGraph = CoChangeGraph.build(GitHistory.empty(null), model, 1);
        }

        // 4. Score all components in ProjectModel
        List<ComponentRiskResult> componentResults = new ArrayList<>();
        int highCount = 0;
        int medCount = 0;
        int lowCount = 0;
        double sumProb = 0.0;
        List<String> highRiskCycleComponents = new ArrayList<>();

        for (ProjectModel.ComponentNode node : model.getComponents()) {
            String compName = node.getId();
            boolean inCycle = cyclicComponents.contains(compName);

            // Extract static features
            StaticArchitectureFeatures staticFeats = StaticArchitectureFeatures.extract(compName, model, graph);

            // Extract evolutionary features
            EvolutionaryFeatures evolFeats;
            if (gitAvailable) {
                long effectiveObsTime = (observationTimestamp > 0)
                        ? observationTimestamp
                        : history.getLatestTimestamp();
                GitHistory featureHistory = (observationTimestamp > 0)
                        ? history.filterWindow(0L, effectiveObsTime)
                        : history;
                evolFeats = EvolutionaryFeatures.extract(
                        compName, featureHistory, coChangeGraph, effectiveObsTime,
                        Math.max(86400L * 7L, effectiveObsTime - history.getEarliestTimestamp())
                );
            } else {
                evolFeats = EvolutionaryFeatures.empty();
            }

            ComponentFeatureVector vector = new ComponentFeatureVector(compName, staticFeats, evolFeats);

            // Predict risk probability
            double prob;
            if (trainedModel != null) {
                prob = trainedModel.predictProbability(vector.getCombinedValues());
            } else {
                // Fallback calibrated architectural scoring function
                prob = computeCalibratedRiskHeuristic(staticFeats, evolFeats, inCycle, gitAvailable);
            }

            // Ensure valid probability range [0.05, 0.99]
            prob = Math.max(0.05, Math.min(0.99, prob));
            double percentage = Math.round(prob * 1000.0) / 10.0;

            RiskCategory category;
            if (prob >= 0.60) {
                category = RiskCategory.HIGH;
                highCount++;
                if (inCycle) {
                    highRiskCycleComponents.add(compName);
                }
            } else if (prob >= 0.35) {
                category = RiskCategory.MEDIUM;
                medCount++;
            } else {
                category = RiskCategory.LOW;
                lowCount++;
            }
            sumProb += prob;

            // Extract top contributing factors
            List<String> topStatic = identifyTopStaticFactors(staticFeats, inCycle);
            List<String> topEvol = identifyTopEvolutionaryFactors(evolFeats, gitAvailable);

            // Co-change neighbors
            List<CoChangeNeighborDto> neighbors = extractTopCoChangeNeighbors(compName, coChangeGraph);

            componentResults.add(new ComponentRiskResult(
                    compName,
                    prob,
                    percentage,
                    category,
                    inCycle,
                    topStatic,
                    topEvol,
                    neighbors,
                    staticFeats.asMap(),
                    evolFeats.asMap()
            ));
        }

        // Sort components descending by risk probability
        componentResults.sort(Comparator.comparingDouble(ComponentRiskResult::riskProbability).reversed());

        double avgProb = componentResults.isEmpty() ? 0.0 : sumProb / componentResults.size();
        String modelDesc = (trainedModel != null)
                ? "Trained Random Forest Multi-Modal Ensemble (20 trees, 24 static+evolutionary features)"
                : (gitAvailable
                ? "Calibrated Multi-Modal Risk Estimator (Static Architecture + Git Churn/Co-Change)"
                : "Static Structural Architecture Risk Estimator (Zero Git history available)");

        ProjectRiskOverview overview = new ProjectRiskOverview(
                model.getProjectName(),
                gitAvailable,
                gitAvailable ? history.getCommitCount() : 0,
                componentResults.size(),
                highCount,
                medCount,
                lowCount,
                Math.round(avgProb * 1000.0) / 1000.0,
                Collections.unmodifiableList(componentResults),
                Collections.unmodifiableList(highRiskCycleComponents),
                ablationResult,
                modelDesc
        );

        cachedAssessments.put(cacheKey, overview);
        return overview;
    }

    public ProjectRiskOverview getCachedAssessment(String key) {
        if (key == null) return null;
        return cachedAssessments.get(key);
    }

    public void clearCache() {
        cachedAssessments.clear();
    }

    private Set<String> findCyclicComponents(DependencyGraph graph) {
        Set<String> cyclic = new HashSet<>();
        List<Set<String>> sccs = GraphAlgorithms.tarjanSCC(graph);
        for (Set<String> scc : sccs) {
            if (scc.size() > 1) {
                cyclic.addAll(scc);
            }
        }
        return cyclic;
    }

    private double computeCalibratedRiskHeuristic(StaticArchitectureFeatures sf,
                                                  EvolutionaryFeatures ef,
                                                  boolean inCycle,
                                                  boolean gitAvailable) {
        double score = 0.15; // base prior

        // Static architectural weights
        if (inCycle) score += 0.25;
        if (sf.cyclomaticComplexity() > 30) score += 0.15;
        else if (sf.cyclomaticComplexity() > 15) score += 0.08;

        if (sf.couplingIntensity() > 12) score += 0.15;
        else if (sf.couplingIntensity() > 6) score += 0.08;

        if (sf.instability() > 0.75 && sf.fanIn() > 3) score += 0.10;
        if (sf.inheritanceDepth() > 3) score += 0.08;
        if (sf.linesOfCode() > 400) score += 0.08;

        // Evolutionary weights (when git available)
        if (gitAvailable) {
            if (ef.commitCount() > 15) score += 0.15;
            else if (ef.commitCount() > 5) score += 0.08;

            if (ef.churnTotal() > 500) score += 0.12;
            else if (ef.churnTotal() > 100) score += 0.06;

            if (ef.cochangeDegree() > 4) score += 0.10;
            if (ef.cochangeWeightedCentrality() > 0.4) score += 0.08;
        }

        return Math.min(0.95, score);
    }

    private List<String> identifyTopStaticFactors(StaticArchitectureFeatures sf, boolean inCycle) {
        List<String> factors = new ArrayList<>();
        if (inCycle) {
            factors.add("Participant in Architectural Dependency Cycle (Critical Structural Defect)");
        }
        if (sf.cyclomaticComplexity() >= 25) {
            factors.add("High Cyclomatic Complexity (" + (int) sf.cyclomaticComplexity() + ")");
        }
        if (sf.couplingIntensity() >= 10) {
            factors.add("High Architectural Coupling (" + (int) sf.couplingIntensity() + " connections, Fan-in: " + (int) sf.fanIn() + ", Fan-out: " + (int) sf.fanOut() + ")");
        }
        if (sf.instability() >= 0.80 && sf.fanIn() > 1) {
            factors.add("High Instability metric (I = " + String.format(Locale.US, "%.2f", sf.instability()) + ")");
        }
        if (sf.inheritanceDepth() >= 4) {
            factors.add("Deep Inheritance Hierarchy (depth = " + (int) sf.inheritanceDepth() + ")");
        }
        if (sf.linesOfCode() >= 350) {
            factors.add("Substantial Component Size (" + (int) sf.linesOfCode() + " LOC)");
        }
        if (factors.isEmpty()) {
            factors.add("Normal static complexity and coupling metrics");
        }
        return factors;
    }

    private List<String> identifyTopEvolutionaryFactors(EvolutionaryFeatures ef, boolean gitAvailable) {
        List<String> factors = new ArrayList<>();
        if (!gitAvailable || ef.commitCount() == 0) {
            factors.add("No evolutionary churn detected (zero historical commits in observation window)");
            return factors;
        }
        if (ef.commitCount() >= 10) {
            factors.add("Frequent historical modifications (" + (int) ef.commitCount() + " commits)");
        }
        if (ef.churnTotal() >= 300) {
            factors.add("High code churn (" + (int) ef.churnTotal() + " lines modified: +" + (int) ef.churnAdded() + "/-" + (int) ef.churnDeleted() + ")");
        }
        if (ef.cochangeDegree() >= 3) {
            factors.add("Widespread evolutionary co-change (" + (int) ef.cochangeDegree() + " co-modified components)");
        }
        if (ef.cochangeWeightedCentrality() >= 0.3) {
            factors.add("Elevated co-change graph centrality (" + String.format(Locale.US, "%.2f", ef.cochangeWeightedCentrality()) + ")");
        }
        if (ef.timeSinceLastChange() > 0 && ef.timeSinceLastChange() <= 86400L * 14L) {
            factors.add("Recent churn activity (< 14 days prior to observation cutoff)");
        }
        if (factors.isEmpty()) {
            factors.add("Low historical volatility (" + (int) ef.commitCount() + " commits, " + (int) ef.churnTotal() + " LOC churn)");
        }
        return factors;
    }

    private List<CoChangeNeighborDto> extractTopCoChangeNeighbors(String componentName, CoChangeGraph graph) {
        if (graph == null) return List.of();
        List<CoChangeEdge> edges = graph.getNeighbors(componentName);
        if (edges == null || edges.isEmpty()) return List.of();

        List<CoChangeNeighborDto> result = new ArrayList<>();
        for (int i = 0; i < Math.min(5, edges.size()); i++) {
            CoChangeEdge edge = edges.get(i);
            String neighbor = edge.componentA().equals(componentName) ? edge.componentB() : edge.componentA();
            result.add(new CoChangeNeighborDto(neighbor, edge.coChangeCount(), Math.round(edge.jaccardStrength() * 100.0) / 100.0));
        }
        return result;
    }
}
