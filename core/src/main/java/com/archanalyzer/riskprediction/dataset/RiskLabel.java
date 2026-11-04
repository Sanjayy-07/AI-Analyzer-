package com.archanalyzer.riskprediction.dataset;

/**
 * Encapsulates the ground truth target label evaluated strictly within the future
 * prediction horizon (T_obs, T_target].
 */
public class RiskLabel {

    public enum LabelType {
        DEFECT_RISK,
        CHANGE_RISK_PROXY
    }

    private final String componentId;
    private final int futureBugFixCount;
    private final int futureCommitCount;
    private final int futureChurn;
    private final boolean isDefectRisk;
    private final boolean isHighChangeRisk;
    private final LabelType labelType;

    public RiskLabel(String componentId,
                     int futureBugFixCount,
                     int futureCommitCount,
                     int futureChurn,
                     LabelType labelType) {
        this.componentId = componentId;
        this.futureBugFixCount = futureBugFixCount;
        this.futureCommitCount = futureCommitCount;
        this.futureChurn = futureChurn;
        this.labelType = labelType;
        this.isDefectRisk = futureBugFixCount > 0;
        this.isHighChangeRisk = futureCommitCount >= 3;
    }

    public String getComponentId() { return componentId; }
    public int getFutureBugFixCount() { return futureBugFixCount; }
    public int getFutureCommitCount() { return futureCommitCount; }
    public int getFutureChurn() { return futureChurn; }
    public boolean isDefectRisk() { return isDefectRisk; }
    public boolean isHighChangeRisk() { return isHighChangeRisk; }
    public LabelType getLabelType() { return labelType; }

    /**
     * Binary target integer (1 = at-risk, 0 = low-risk).
     */
    public int getBinaryLabel() {
        if (labelType == LabelType.DEFECT_RISK) {
            return isDefectRisk ? 1 : 0;
        } else {
            return isHighChangeRisk ? 1 : 0;
        }
    }

    @Override
    public String toString() {
        return String.format("RiskLabel[%s: bugFixes=%d, futureCommits=%d, binary=%d, type=%s]",
                componentId, futureBugFixCount, futureCommitCount, getBinaryLabel(), labelType);
    }
}
