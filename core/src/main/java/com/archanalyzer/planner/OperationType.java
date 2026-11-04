package com.archanalyzer.planner;

/**
 * Types of architectural refactoring operations supported by the Simulation-Guided Planner.
 */
public enum OperationType {
    /**
     * Extracts an interface from a concrete component and optionally retargets incoming callers.
     */
    EXTRACT_INTERFACE("Extract Interface", "Extracts an interface abstraction from a concrete class"),

    /**
     * Inverts a dependency edge participating in a cycle by introducing an interface.
     */
    BREAK_CYCLE("Break Dependency Cycle", "Resolves cyclic coupling between components via dependency inversion"),

    /**
     * Hypothetically decouples or eliminates a direct dependency edge between two components.
     */
    REMOVE_DEPENDENCY("Remove Dependency", "Eliminates a direct coupling edge between two components"),

    /**
     * Redirects a dependency edge from a concrete provider to an abstraction or alternative component.
     */
    REDIRECT_DEPENDENCY("Redirect Dependency", "Reroutes calls from a direct dependency to an abstraction or new provider"),

    /**
     * Introduces an abstraction/facade node into the architecture graph.
     */
    INTRODUCE_ABSTRACTION("Introduce Abstraction", "Introduces an architectural abstraction layer to decouple multiple consumers");

    private final String displayName;
    private final String description;

    OperationType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
