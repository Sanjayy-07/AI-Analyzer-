package com.archanalyzer.explanation.evidence;

/**
 * An immutable unit of authoritative architectural ground truth extracted directly
 * from Java source ASTs, dependency graphs, metric engines, and deterministic algorithms.
 */
public record EvidenceItem(
        String evidenceId,
        EvidenceType type,
        String subjectId,
        String targetId,
        String metricName,
        Double numericValue,
        String textValue,
        String sourceEngine,
        String description
) {
    public static EvidenceItem metric(String evidenceId, String componentId, String metricName, double value, String description) {
        return new EvidenceItem(
                evidenceId,
                EvidenceType.METRIC,
                componentId,
                null,
                metricName,
                value,
                String.valueOf(value),
                "MetricsEngine",
                description
        );
    }

    public static EvidenceItem dependency(String evidenceId, String fromComponent, String toComponent, String description) {
        return new EvidenceItem(
                evidenceId,
                EvidenceType.DEPENDENCY,
                fromComponent,
                toComponent,
                "dependency",
                null,
                fromComponent + " -> " + toComponent,
                "DependencyGraph",
                description
        );
    }

    public static EvidenceItem cycle(String evidenceId, String componentId, String cycleMembers, String description) {
        return new EvidenceItem(
                evidenceId,
                EvidenceType.CYCLE,
                componentId,
                null,
                "cycle",
                null,
                cycleMembers,
                "TarjanSCC",
                description
        );
    }

    public static EvidenceItem risk(String evidenceId, String componentId, double score, String category, String description) {
        return new EvidenceItem(
                evidenceId,
                EvidenceType.RISK,
                componentId,
                null,
                "riskScore",
                score,
                category,
                "ArchitectureRiskService",
                description
        );
    }

    public static EvidenceItem simulation(String evidenceId, String subjectId, String metricName, double delta, String description) {
        return new EvidenceItem(
                evidenceId,
                EvidenceType.SIMULATION,
                subjectId,
                null,
                metricName,
                delta,
                String.valueOf(delta),
                "SimulationPlanner",
                description
        );
    }
}
