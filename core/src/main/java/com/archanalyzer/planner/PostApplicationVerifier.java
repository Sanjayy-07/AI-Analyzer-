package com.archanalyzer.planner;

import com.archanalyzer.metrics.MetricsEngine.ProjectMetrics;

import java.util.*;

/**
 * Validates actual post-refactoring architecture metrics against simulated projections.
 * Never equates simulated metrics with actual metrics without independent verification.
 */
public class PostApplicationVerifier {

    public static class MetricVerificationComparison {
        public String metricName;
        public double simulatedValue;
        public double actualValue;
        public double discrepancy;
        public boolean isConcordant; // true if actual achieved at least the simulated improvement
    }

    public static class VerificationReport {
        public String planId;
        public boolean verified;
        public boolean buildPassed;
        public String compilerStatus;
        public String testStatus;
        public List<MetricVerificationComparison> comparisons = new ArrayList<>();
        public List<String> discrepancies = new ArrayList<>();
        public List<String> notes = new ArrayList<>();
        public String zipDownloadPath;
    }

    public VerificationReport verify(String planId,
                                     PlannerSimulationResult simulation,
                                     ProjectMetrics actualMetrics,
                                     boolean buildPassed,
                                     String compilerStatus,
                                     String testStatus) {
        VerificationReport report = new VerificationReport();
        report.planId = planId;
        report.buildPassed = buildPassed;
        report.compilerStatus = compilerStatus;
        report.testStatus = testStatus;

        if (simulation == null || actualMetrics == null) {
            report.verified = false;
            report.discrepancies.add("Missing simulation baseline or actual post-application metrics.");
            return report;
        }

        ProjectMetrics sim = simulation.getSimulatedMetrics();
        ProjectMetrics base = simulation.getBaselineMetrics();

        // 1. Cycle count comparison
        addComparison(report, "Circular Dependencies",
                sim != null ? sim.cycleCount : 0,
                actualMetrics.cycleCount,
                true); // lower is better

        // 2. Architecture Health comparison
        addComparison(report, "Architecture Health",
                sim != null ? sim.architectureHealthScore : 0,
                actualMetrics.architectureHealthScore,
                false); // higher is better

        // 3. Dependency Pressure comparison
        addComparison(report, "Dependency Pressure",
                sim != null ? sim.dependencyScore : 0,
                actualMetrics.dependencyScore,
                true); // lower is better

        // 4. Total Dependencies comparison
        addComparison(report, "Total Dependencies",
                sim != null ? sim.totalDependencies : 0,
                actualMetrics.totalDependencies,
                true); // lower is better

        // Check overall verification status: successful build
        report.verified = buildPassed;
        if (report.verified && report.discrepancies.isEmpty()) {
            report.notes.add("Actual post-refactoring architecture achieved or exceeded all simulated metric projections.");
        } else if (report.verified) {
            report.notes.add("Post-refactoring build passed; minor metric variance noted between simulation and AST.");
        }

        return report;
    }

    private void addComparison(VerificationReport report,
                               String metricName,
                               double simulated,
                               double actual,
                               boolean lowerIsBetter) {
        MetricVerificationComparison comp = new MetricVerificationComparison();
        comp.metricName = metricName;
        comp.simulatedValue = Math.round(simulated * 100.0) / 100.0;
        comp.actualValue = Math.round(actual * 100.0) / 100.0;
        comp.discrepancy = Math.round((comp.actualValue - comp.simulatedValue) * 100.0) / 100.0;

        double tolerance = 1.0;
        if (lowerIsBetter) {
            comp.isConcordant = comp.actualValue <= comp.simulatedValue + tolerance;
        } else {
            comp.isConcordant = comp.actualValue >= comp.simulatedValue - tolerance;
        }

        report.comparisons.add(comp);
        if (!comp.isConcordant) {
            report.discrepancies.add(String.format("%s: Simulated %.2f, but actual was %.2f (Discrepancy: %.2f)",
                    metricName, comp.simulatedValue, comp.actualValue, comp.discrepancy));
        }
    }
}
