package com.archanalyzer.riskprediction.features;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.graph.GraphAlgorithms;
import com.archanalyzer.metrics.MetricsEngine.ComponentMetrics;
import com.archanalyzer.model.ProjectModel;

import java.util.*;

/**
 * Extracts static architectural features from the DependencyGraph and ProjectModel.
 */
public class StaticArchitectureFeatures {

    public static final String[] FEATURE_NAMES = {
            "loc",
            "cyclomatic_complexity",
            "method_count",
            "field_count",
            "fan_in",
            "fan_out",
            "coupling",
            "instability",
            "degree_centrality",
            "cycle_membership",
            "dependency_depth"
    };

    private final double[] values;

    public StaticArchitectureFeatures(double[] values) {
        if (values.length != FEATURE_NAMES.length) {
            throw new IllegalArgumentException("Expected " + FEATURE_NAMES.length + " static features, got " + values.length);
        }
        this.values = values;
    }

    public static StaticArchitectureFeatures extract(String componentId,
                                                    ProjectModel model,
                                                    DependencyGraph graph) {
        List<Set<String>> sccs = graph != null ? GraphAlgorithms.tarjanSCC(graph) : List.of();
        return extract(componentId, graph, model, null, sccs);
    }

    public static StaticArchitectureFeatures extract(String componentId,
                                                    DependencyGraph graph,
                                                    ProjectModel model,
                                                    ComponentMetrics cm,
                                                    List<Set<String>> sccs) {
        double[] v = new double[FEATURE_NAMES.length];

        int loc = 0;
        int cc = 0;
        int methods = 0;
        int fields = 0;

        if (model != null) {
            var opt = model.getComponent(componentId);
            if (opt.isPresent()) {
                var c = opt.get();
                loc = c.getLoc();
                cc = c.getCyclomaticComplexity();
                methods = c.getMethodCount();
                fields = c.getFieldCount();
            }
        }

        int fanIn = graph != null ? graph.fanIn(componentId) : (cm != null ? cm.fanIn : 0);
        int fanOut = graph != null ? graph.fanOut(componentId) : (cm != null ? cm.fanOut : 0);
        int coupling = fanIn + fanOut;
        double instability = coupling == 0 ? 0.0 : (double) fanOut / coupling;
        double centrality = graph != null ? GraphAlgorithms.degreeCentrality(graph, componentId) : (cm != null ? cm.centrality : 0.0);
        boolean inCycle = sccs != null ? GraphAlgorithms.isInCycle(sccs, componentId) : (cm != null && cm.inCycle);
        int depth = graph != null ? GraphAlgorithms.longestChainFrom(graph, componentId, new HashSet<>()) : (cm != null ? cm.dependencyDepth : 0);

        v[0] = loc;
        v[1] = cc;
        v[2] = methods;
        v[3] = fields;
        v[4] = fanIn;
        v[5] = fanOut;
        v[6] = coupling;
        v[7] = instability;
        v[8] = centrality;
        v[9] = inCycle ? 1.0 : 0.0;
        v[10] = depth;

        return new StaticArchitectureFeatures(v);
    }

    public double[] getValues() { return values; }
    public static String[] getFeatureNames() { return FEATURE_NAMES; }

    public double loc() { return values[0]; }
    public double linesOfCode() { return values[0]; }
    public double cyclomaticComplexity() { return values[1]; }
    public double methodCount() { return values[2]; }
    public double fieldCount() { return values[3]; }
    public double fanIn() { return values[4]; }
    public double fanOut() { return values[5]; }
    public double couplingIntensity() { return values[6]; }
    public double instability() { return values[7]; }
    public double degreeCentrality() { return values[8]; }
    public boolean inCycle() { return values[9] > 0.5; }
    public double inheritanceDepth() { return values[10]; }

    public Map<String, Double> asMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < FEATURE_NAMES.length; i++) {
            map.put(FEATURE_NAMES[i], values[i]);
        }
        return map;
    }
}
