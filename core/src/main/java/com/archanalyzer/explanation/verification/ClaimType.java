package com.archanalyzer.explanation.verification;

/**
 * Categorization of claims made within an architectural explanation.
 */
public enum ClaimType {
    METRIC_FACT,
    DEPENDENCY_FACT,
    CYCLE_FACT,
    RISK_FACT,
    INTERPRETATION,
    RECOMMENDATION
}
