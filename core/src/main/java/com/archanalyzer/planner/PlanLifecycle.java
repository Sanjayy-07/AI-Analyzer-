package com.archanalyzer.planner;

/**
 * State machine representing the formal lifecycle of a RefactoringPlan.
 *
 * Transitions:
 * DRAFT -> VALIDATED -> SIMULATED -> APPROVED -> APPLIED
 *
 * Any structural mutation (add, remove, reorder) resets the state back to DRAFT,
 * preventing stale simulation results from ever being approved or applied.
 */
public enum PlanLifecycle {
    /**
     * Plan is being constructed or modified. Simulations have not yet run or are invalidated.
     */
    DRAFT,

    /**
     * Plan has passed static conflict detection and precondition checks.
     */
    VALIDATED,

    /**
     * Plan has been executed on an isolated graph snapshot and metrics recomputed.
     */
    SIMULATED,

    /**
     * Explicit user approval has been granted for the exact simulated plan version.
     */
    APPROVED,

    /**
     * Plan transformations have been executed, verified, and applied to the project codebase.
     */
    APPLIED
}
