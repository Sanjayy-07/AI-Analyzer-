package com.archanalyzer.explanation.verification;

/**
 * The verification verdict assigned to a claim after rigorous evaluation
 * against the authoritative Java ground truth.
 */
public enum ClaimVerificationStatus {
    /** The claim is mathematically or topologically identical to ground truth. */
    VERIFIED,

    /** The claim has partial alignment or negligible numerical difference. */
    PARTIALLY_VERIFIED,

    /** No ground truth evidence exists to substantiate the claim. */
    UNSUPPORTED,

    /** Ground truth explicitly contradicts the asserted statement. */
    CONTRADICTED,

    /** The statement is a subjective opinion or general recommendation with no objective testable assertion. */
    NOT_VERIFIABLE
}
