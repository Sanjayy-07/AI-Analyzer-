package com.archanalyzer.riskprediction.cochange;

import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.riskprediction.history.GitCommit;
import com.archanalyzer.riskprediction.history.GitHistory;

import java.util.*;

/**
 * Mines and analyzes the evolutionary co-change graph from Git history.
 * Nodes represent architectural components; edges represent components repeatedly
 * modified together within the same commit.
 */
public class CoChangeGraph {

    private final Map<String, Integer> componentCommitCounts = new HashMap<>();
    private final Map<String, Map<String, Integer>> coChangeCounts = new HashMap<>();
    private final Map<String, Map<String, CoChangeEdge>> edges = new HashMap<>();
    private final int minSupport;

    public CoChangeGraph(int minSupport) {
        this.minSupport = Math.max(1, minSupport);
    }

    public static CoChangeGraph build(GitHistory history, ProjectModel model, int minSupport) {
        CoChangeGraph graph = new CoChangeGraph(minSupport);
        if (history == null || !history.isAvailable() || history.getCommits().isEmpty()) {
            return graph;
        }

        Map<String, String> pathToComponent = buildPathToComponentMap(model);

        for (GitCommit commit : history.getCommits()) {
            Set<String> touchedComponents = new LinkedHashSet<>();
            for (String file : commit.getChangedFiles().keySet()) {
                String comp = resolveComponent(file, pathToComponent);
                if (comp != null) {
                    touchedComponents.add(comp);
                }
            }

            // Update individual component counts
            for (String comp : touchedComponents) {
                graph.componentCommitCounts.put(comp, graph.componentCommitCounts.getOrDefault(comp, 0) + 1);
            }

            // Update co-change pairs
            List<String> list = new ArrayList<>(touchedComponents);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    String u = list.get(i);
                    String v = list.get(j);
                    graph.incrementCoChange(u, v);
                    graph.incrementCoChange(v, u);
                }
            }
        }

        graph.computeMetrics();
        return graph;
    }

    private void incrementCoChange(String u, String v) {
        coChangeCounts.computeIfAbsent(u, k -> new HashMap<>())
                .put(v, coChangeCounts.get(u).getOrDefault(v, 0) + 1);
    }

    private void computeMetrics() {
        for (String u : coChangeCounts.keySet()) {
            int nU = componentCommitCounts.getOrDefault(u, 0);
            for (Map.Entry<String, Integer> entry : coChangeCounts.get(u).entrySet()) {
                String v = entry.getKey();
                int count = entry.getValue();
                if (count >= minSupport) {
                    int nV = componentCommitCounts.getOrDefault(v, 0);
                    // Jaccard similarity: C(u,v) / (N(u) + N(v) - C(u,v))
                    double union = (nU + nV - count);
                    double jaccard = union > 0 ? (double) count / union : 0.0;
                    double confUtoV = nU > 0 ? (double) count / nU : 0.0;
                    double confVtoU = nV > 0 ? (double) count / nV : 0.0;

                    CoChangeEdge edge = new CoChangeEdge(u, v, count, jaccard, confUtoV, confVtoU);
                    edges.computeIfAbsent(u, k -> new HashMap<>()).put(v, edge);
                }
            }
        }
    }

    public int getCoChangeDegree(String componentId) {
        return edges.getOrDefault(componentId, Collections.emptyMap()).size();
    }

    public double getCoChangeMaxStrength(String componentId) {
        Map<String, CoChangeEdge> neighbors = edges.getOrDefault(componentId, Collections.emptyMap());
        double max = 0.0;
        for (CoChangeEdge e : neighbors.values()) {
            if (e.getJaccardStrength() > max) {
                max = e.getJaccardStrength();
            }
        }
        return max;
    }

    public double getCoChangeWeightedCentrality(String componentId) {
        Map<String, CoChangeEdge> neighbors = edges.getOrDefault(componentId, Collections.emptyMap());
        double sum = 0.0;
        for (CoChangeEdge e : neighbors.values()) {
            sum += e.getJaccardStrength();
        }
        return sum;
    }

    public List<CoChangeEdge> getNeighbors(String componentId) {
        return new ArrayList<>(edges.getOrDefault(componentId, Collections.emptyMap()).values());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapping utilities
    // ──────────────────────────────────────────────────────────────────────────

    public static Map<String, String> buildPathToComponentMap(ProjectModel model) {
        Map<String, String> map = new HashMap<>();
        if (model == null) return map;
        for (ProjectModel.ComponentNode c : model.getComponents()) {
            String fqn = c.getId();
            String simple = c.getSimpleName();
            String pathStyle = fqn.replace('.', '/') + ".java";
            map.put(pathStyle, fqn);
            map.put(simple + ".java", fqn);
        }
        return map;
    }

    public static String resolveComponent(String gitFilePath, Map<String, String> pathToComponent) {
        if (gitFilePath == null || !gitFilePath.endsWith(".java")) return null;
        String norm = gitFilePath.replace('\\', '/');

        // Direct lookup
        if (pathToComponent.containsKey(norm)) {
            return pathToComponent.get(norm);
        }

        // Substring / simple name match
        int lastSlash = norm.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? norm.substring(lastSlash + 1) : norm;
        if (pathToComponent.containsKey(fileName)) {
            return pathToComponent.get(fileName);
        }

        // Strip prefix (e.g. src/main/java/)
        for (String prefix : List.of("src/main/java/", "src/test/java/", "src/", "app/")) {
            if (norm.startsWith(prefix)) {
                String sub = norm.substring(prefix.length());
                if (pathToComponent.containsKey(sub)) {
                    return pathToComponent.get(sub);
                }
                String guessedFqn = sub.replace(".java", "").replace('/', '.');
                return guessedFqn;
            }
        }

        return fileName.replace(".java", "");
    }
}
