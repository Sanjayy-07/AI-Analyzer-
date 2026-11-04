package com.archanalyzer.riskprediction.cochange;

/**
 * Represents an evolutionary co-change relationship between two architecture components.
 */
public class CoChangeEdge {

    private final String componentA;
    private final String componentB;
    private final int coChangeCount;
    private final double jaccardStrength;
    private final double confidenceAtoB;
    private final double confidenceBtoA;

    public CoChangeEdge(String componentA,
                        String componentB,
                        int coChangeCount,
                        double jaccardStrength,
                        double confidenceAtoB,
                        double confidenceBtoA) {
        this.componentA = componentA;
        this.componentB = componentB;
        this.coChangeCount = coChangeCount;
        this.jaccardStrength = jaccardStrength;
        this.confidenceAtoB = confidenceAtoB;
        this.confidenceBtoA = confidenceBtoA;
    }

    public String getComponentA() { return componentA; }
    public String getComponentB() { return componentB; }
    public int getCoChangeCount() { return coChangeCount; }
    public double getJaccardStrength() { return jaccardStrength; }
    public double getConfidenceAtoB() { return confidenceAtoB; }
    public double getConfidenceBtoA() { return confidenceBtoA; }

    public String componentA() { return componentA; }
    public String componentB() { return componentB; }
    public int coChangeCount() { return coChangeCount; }
    public double jaccardStrength() { return jaccardStrength; }

    @Override
    public String toString() {
        return String.format("CoChange[%s <-> %s: count=%d, jaccard=%.3f]",
                componentA, componentB, coChangeCount, jaccardStrength);
    }
}
