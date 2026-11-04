package com.archanalyzer.riskprediction.dataset;

import java.util.*;

/**
 * A reproducible, leakage-safe dataset containing feature vectors and labels
 * constructed across a temporal boundary.
 */
public class TemporalDataset {

    public record FeatureMatrix(double[][] X, int[] y, String[] featureNames, List<String> componentIds) {}

    private final String projectName;
    private final List<DatasetInstance> instances;
    private final long observationTimestamp;
    private final long predictionHorizonSeconds;

    public TemporalDataset(String projectName,
                           List<DatasetInstance> instances,
                           long observationTimestamp,
                           long predictionHorizonSeconds) {
        this.projectName = projectName;
        this.instances = Collections.unmodifiableList(new ArrayList<>(instances));
        this.observationTimestamp = observationTimestamp;
        this.predictionHorizonSeconds = predictionHorizonSeconds;
    }

    public String getProjectName() { return projectName; }
    public List<DatasetInstance> getInstances() { return instances; }
    public int size() { return instances.size(); }
    public boolean isEmpty() { return instances.isEmpty(); }
    public long getObservationTimestamp() { return observationTimestamp; }
    public long getPredictionHorizonSeconds() { return predictionHorizonSeconds; }

    public FeatureMatrix getStaticMatrix() {
        return buildMatrix(DatasetInstance::getStaticFeatures,
                com.archanalyzer.riskprediction.features.ComponentFeatureVector.getStaticFeatureNames());
    }

    public FeatureMatrix getEvolutionaryMatrix() {
        return buildMatrix(DatasetInstance::getEvolutionaryFeatures,
                com.archanalyzer.riskprediction.features.ComponentFeatureVector.getEvolutionaryFeatureNames());
    }

    public FeatureMatrix getCombinedMatrix() {
        return buildMatrix(DatasetInstance::getCombinedFeatures,
                com.archanalyzer.riskprediction.features.ComponentFeatureVector.getCombinedFeatureNames());
    }

    private FeatureMatrix buildMatrix(java.util.function.Function<DatasetInstance, double[]> extractor, String[] featureNames) {
        int n = instances.size();
        if (n == 0) {
            return new FeatureMatrix(new double[0][0], new int[0], featureNames, List.of());
        }
        int p = featureNames.length;
        double[][] X = new double[n][p];
        int[] y = new int[n];
        List<String> ids = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            DatasetInstance inst = instances.get(i);
            X[i] = extractor.apply(inst);
            y[i] = inst.getTarget();
            ids.add(inst.getComponentId());
        }
        return new FeatureMatrix(X, y, featureNames, ids);
    }

    public TemporalDataset[] split(double trainRatio, long seed) {
        if (instances.size() < 2) {
            return new TemporalDataset[]{this, this};
        }
        List<DatasetInstance> shuffled = new ArrayList<>(instances);
        Collections.shuffle(shuffled, new Random(seed));
        int splitPoint = (int) Math.round(shuffled.size() * trainRatio);
        splitPoint = Math.max(1, Math.min(splitPoint, shuffled.size() - 1));

        TemporalDataset train = new TemporalDataset(
                projectName + "-train",
                shuffled.subList(0, splitPoint),
                observationTimestamp,
                predictionHorizonSeconds
        );
        TemporalDataset test = new TemporalDataset(
                projectName + "-test",
                shuffled.subList(splitPoint, shuffled.size()),
                observationTimestamp,
                predictionHorizonSeconds
        );
        return new TemporalDataset[]{train, test};
    }
}
