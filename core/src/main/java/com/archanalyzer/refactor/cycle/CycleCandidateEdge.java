package com.archanalyzer.refactor.cycle;

import java.nio.file.Path;

/**
 * Represents a candidate directed dependency edge within a cyclic Strongly Connected Component (SCC)
 * evaluated for potential dependency inversion.
 */
public class CycleCandidateEdge {

    private final String fromComponent;
    private final String toComponent;
    private final Path fromFile;
    private final Path toFile;

    private boolean feasible = true;
    private String rejectionReason = "";
    private double costScore = 0.0;
    private int callerCount = 0;
    private int fieldCount = 0;
    private int constructorUsageCount = 0;
    private int methodParameterCount = 0;
    private int cycleReductionCount = 0;
    private String rationale = "";

    public CycleCandidateEdge(String fromComponent, String toComponent, Path fromFile, Path toFile) {
        this.fromComponent = fromComponent;
        this.toComponent = toComponent;
        this.fromFile = fromFile;
        this.toFile = toFile;
    }

    public String getFromComponent() { return fromComponent; }
    public String getToComponent() { return toComponent; }
    public Path getFromFile() { return fromFile; }
    public Path getToFile() { return toFile; }

    public boolean isFeasible() { return feasible; }
    public void setFeasible(boolean feasible) { this.feasible = feasible; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public double getCostScore() { return costScore; }
    public void setCostScore(double costScore) { this.costScore = costScore; }

    public int getCallerCount() { return callerCount; }
    public void setCallerCount(int callerCount) { this.callerCount = callerCount; }

    public int getFieldCount() { return fieldCount; }
    public void setFieldCount(int fieldCount) { this.fieldCount = fieldCount; }

    public int getConstructorUsageCount() { return constructorUsageCount; }
    public void setConstructorUsageCount(int constructorUsageCount) { this.constructorUsageCount = constructorUsageCount; }

    public int getMethodParameterCount() { return methodParameterCount; }
    public void setMethodParameterCount(int methodParameterCount) { this.methodParameterCount = methodParameterCount; }

    public int getCycleReductionCount() { return cycleReductionCount; }
    public void setCycleReductionCount(int cycleReductionCount) { this.cycleReductionCount = cycleReductionCount; }

    public String getToSimpleName() {
        int idx = toComponent.lastIndexOf('.');
        return idx >= 0 ? toComponent.substring(idx + 1) : toComponent;
    }

    public String getFromSimpleName() {
        int idx = fromComponent.lastIndexOf('.');
        return idx >= 0 ? fromComponent.substring(idx + 1) : fromComponent;
    }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getEdgeKey() {
        return fromComponent + " -> " + toComponent;
    }

    @Override
    public String toString() {
        return String.format("CycleCandidateEdge[%s -> %s, cost=%.1f, feasible=%s, cycleReduction=%d]",
                fromComponent, toComponent, costScore, feasible, cycleReductionCount);
    }
}
