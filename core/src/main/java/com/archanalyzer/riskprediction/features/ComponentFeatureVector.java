package com.archanalyzer.riskprediction.features;

import java.util.Arrays;

/**
 * Encapsulates static, evolutionary, and combined feature representations for a component.
 */
public class ComponentFeatureVector {

    private final String componentId;
    private final StaticArchitectureFeatures staticFeatures;
    private final EvolutionaryFeatures evolutionaryFeatures;
    private final double[] combinedValues;

    public ComponentFeatureVector(String componentId,
                                  StaticArchitectureFeatures staticFeatures,
                                  EvolutionaryFeatures evolutionaryFeatures) {
        this.componentId = componentId;
        this.staticFeatures = staticFeatures;
        this.evolutionaryFeatures = evolutionaryFeatures;

        double[] s = staticFeatures.getValues();
        double[] e = evolutionaryFeatures.getValues();
        this.combinedValues = new double[s.length + e.length];
        System.arraycopy(s, 0, this.combinedValues, 0, s.length);
        System.arraycopy(e, 0, this.combinedValues, s.length, e.length);
    }

    public String getComponentId() { return componentId; }
    public StaticArchitectureFeatures getStaticFeatures() { return staticFeatures; }
    public EvolutionaryFeatures getEvolutionaryFeatures() { return evolutionaryFeatures; }

    public double[] getStaticValues() { return staticFeatures.getValues(); }
    public double[] getEvolutionaryValues() { return evolutionaryFeatures.getValues(); }
    public double[] getCombinedValues() { return combinedValues; }

    public static String[] getStaticFeatureNames() { return StaticArchitectureFeatures.getFeatureNames(); }
    public static String[] getEvolutionaryFeatureNames() { return EvolutionaryFeatures.getFeatureNames(); }

    public static String[] getCombinedFeatureNames() {
        String[] s = StaticArchitectureFeatures.getFeatureNames();
        String[] e = EvolutionaryFeatures.getFeatureNames();
        String[] c = new String[s.length + e.length];
        System.arraycopy(s, 0, c, 0, s.length);
        System.arraycopy(e, 0, c, s.length, e.length);
        return c;
    }

    @Override
    public String toString() {
        return String.format("ComponentFeatureVector[%s: static=%d, evol=%d, combined=%d]",
                componentId, staticFeatures.getValues().length,
                evolutionaryFeatures.getValues().length, combinedValues.length);
    }
}
