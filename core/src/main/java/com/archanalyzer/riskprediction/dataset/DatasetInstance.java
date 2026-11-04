package com.archanalyzer.riskprediction.dataset;

import com.archanalyzer.riskprediction.features.ComponentFeatureVector;

/**
 * A single observation instance in a machine learning risk prediction dataset.
 */
public class DatasetInstance {

    private final String componentId;
    private final ComponentFeatureVector featureVector;
    private final RiskLabel label;
    private final long observationTimestamp;
    private final long predictionHorizonSeconds;

    public DatasetInstance(String componentId,
                           ComponentFeatureVector featureVector,
                           RiskLabel label) {
        this(componentId, featureVector, label, 0L, 0L);
    }

    public DatasetInstance(String componentId,
                           ComponentFeatureVector featureVector,
                           RiskLabel label,
                           long observationTimestamp,
                           long predictionHorizonSeconds) {
        this.componentId = componentId;
        this.featureVector = featureVector;
        this.label = label;
        this.observationTimestamp = observationTimestamp;
        this.predictionHorizonSeconds = predictionHorizonSeconds;
    }

    public String getComponentId() { return componentId; }
    public ComponentFeatureVector getFeatureVector() { return featureVector; }
    public RiskLabel getLabel() { return label; }
    public long getObservationTimestamp() { return observationTimestamp; }
    public long getPredictionHorizonSeconds() { return predictionHorizonSeconds; }

    public double[] getStaticFeatures() { return featureVector.getStaticValues(); }
    public double[] getEvolutionaryFeatures() { return featureVector.getEvolutionaryValues(); }
    public double[] getCombinedFeatures() { return featureVector.getCombinedValues(); }
    public int getTarget() { return label.getBinaryLabel(); }
}
