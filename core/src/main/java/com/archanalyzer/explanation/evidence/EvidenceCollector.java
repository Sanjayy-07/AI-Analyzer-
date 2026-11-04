package com.archanalyzer.explanation.evidence;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.metrics.MetricsEngine;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.riskprediction.service.ArchitectureRiskService;

import java.util.*;

/**
 * Extracts and compiles authoritative, immutable architectural ground truth from
 * the dependency graph, AST metrics, SCC cycles, and Novelty 3 risk predictions.
 */
public class EvidenceCollector {

    public ArchitectureEvidence collect(
            ProjectModel model,
            DependencyGraph graph,
            MetricsEngine.ProjectMetrics projectMetrics,
            List<ArchitectureRiskService.ComponentRiskResult> riskResults
    ) {
        List<EvidenceItem> items = new ArrayList<>();
        Set<String> allComponentIds = new LinkedHashSet<>();
        Map<String, Map<String, Double>> componentMetrics = new LinkedHashMap<>();
        Set<String> dependencyEdges = new LinkedHashSet<>();
        List<Set<String>> cycles = new ArrayList<>();
        Map<String, Double> riskScores = new LinkedHashMap<>();
        Map<String, String> riskCategories = new LinkedHashMap<>();

        // 1. Collect Project-level Evidence
        if (projectMetrics != null) {
            items.add(EvidenceItem.metric("ev-proj-health", "PROJECT", "architectureHealthScore",
                    projectMetrics.architectureHealthScore, "Overall architecture health score (0-100)"));
            items.add(EvidenceItem.metric("ev-proj-depscore", "PROJECT", "dependencyScore",
                    projectMetrics.dependencyScore, "Overall dependency pressure score (0-100)"));
            items.add(EvidenceItem.metric("ev-proj-cycles", "PROJECT", "cycleCount",
                    projectMetrics.cycleCount, "Total strongly connected circular dependency cycles"));
            items.add(EvidenceItem.metric("ev-proj-components", "PROJECT", "totalComponents",
                    projectMetrics.totalComponents, "Total analyzed architectural components"));
            items.add(EvidenceItem.metric("ev-proj-dependencies", "PROJECT", "totalDependencies",
                    projectMetrics.totalDependencies, "Total internal dependency couplings"));
        }

        // 2. Collect Component-level Metrics Evidence
        if (projectMetrics != null && projectMetrics.perComponent != null) {
            for (Map.Entry<String, MetricsEngine.ComponentMetrics> entry : projectMetrics.perComponent.entrySet()) {
                String id = entry.getKey();
                MetricsEngine.ComponentMetrics cm = entry.getValue();
                allComponentIds.add(id);

                Map<String, Double> metricsMap = new LinkedHashMap<>();
                metricsMap.put("fanin", (double) cm.fanIn);
                metricsMap.put("fanout", (double) cm.fanOut);
                metricsMap.put("loc", (double) cm.loc);
                metricsMap.put("cyclomaticcomplexity", (double) cm.cyclomaticComplexity);
                metricsMap.put("methodcount", (double) cm.methodCount);
                metricsMap.put("fieldcount", (double) cm.fieldCount);
                metricsMap.put("dependencydepth", (double) cm.dependencyDepth);
                metricsMap.put("instability", cm.instability);
                metricsMap.put("centrality", cm.centrality);
                metricsMap.put("incycle", cm.inCycle ? 1.0 : 0.0);
                componentMetrics.put(id, metricsMap);

                String slug = slugify(id);
                items.add(EvidenceItem.metric("ev-metric-" + slug + "-fanin", id, "fanIn", cm.fanIn, "Incoming dependency count"));
                items.add(EvidenceItem.metric("ev-metric-" + slug + "-fanout", id, "fanOut", cm.fanOut, "Outgoing dependency count"));
                items.add(EvidenceItem.metric("ev-metric-" + slug + "-loc", id, "loc", cm.loc, "Lines of code"));
                items.add(EvidenceItem.metric("ev-metric-" + slug + "-cc", id, "cyclomaticComplexity", cm.cyclomaticComplexity, "Cyclomatic complexity"));
                items.add(EvidenceItem.metric("ev-metric-" + slug + "-instability", id, "instability", round(cm.instability), "Instability ratio Ce/(Ca+Ce)"));
                items.add(EvidenceItem.metric("ev-metric-" + slug + "-depth", id, "dependencyDepth", cm.dependencyDepth, "Longest downstream dependency path"));
            }
        }

        // Ensure graph vertices are present
        if (graph != null) {
            for (String v : graph.vertices()) {
                allComponentIds.add(v);
            }

            // 3. Collect Dependency Edges Evidence
            for (String from : graph.vertices()) {
                for (String to : graph.outgoing(from)) {
                    allComponentIds.add(from);
                    allComponentIds.add(to);
                    dependencyEdges.add(from + "->" + to);
                    String depSlug = slugify(from) + "-" + slugify(to);
                    items.add(EvidenceItem.dependency("ev-dep-" + depSlug, from, to,
                            "Direct architectural dependency edge: " + from + " -> " + to));
                }
            }

            // 4. Collect Strongly Connected Components (Cycles)
            List<Set<String>> sccs = GraphAlgorithms.stronglyConnectedComponents(graph);
            int sccIndex = 0;
            for (Set<String> scc : sccs) {
                if (scc.size() > 1) {
                    sccIndex++;
                    cycles.add(scc);
                    String cycleMembers = String.join(", ", scc);
                    for (String member : scc) {
                        String cycleSlug = "c" + sccIndex + "-" + slugify(member);
                        items.add(EvidenceItem.cycle("ev-cycle-" + cycleSlug, member, cycleMembers,
                                "Part of SCC Cycle #" + sccIndex + " containing: " + cycleMembers));
                    }
                }
            }
        }

        // 5. Collect Novelty 3 Architecture Risk Evidence
        if (riskResults != null) {
            for (ArchitectureRiskService.ComponentRiskResult r : riskResults) {
                String id = r.componentName();
                allComponentIds.add(id);
                double prob = round(r.riskProbability());
                riskScores.put(id, prob);
                riskCategories.put(id, r.riskCategory().name());

                String slug = slugify(id);
                items.add(EvidenceItem.risk("ev-risk-" + slug, id, prob, r.riskCategory().name(),
                        String.format("Defect & change risk probability: %.2f (%s risk)", prob, r.riskCategory().name())));
            }
        }

        return new ArchitectureEvidence(
                items,
                allComponentIds,
                componentMetrics,
                dependencyEdges,
                cycles,
                riskScores,
                riskCategories
        );
    }

    private String slugify(String s) {
        if (s == null) return "na";
        int idx = s.lastIndexOf('.');
        String shortName = idx >= 0 ? s.substring(idx + 1) : s;
        return shortName.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase();
    }

    private double round(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }
}
