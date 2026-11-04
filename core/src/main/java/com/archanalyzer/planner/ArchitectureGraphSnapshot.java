package com.archanalyzer.planner;

import com.archanalyzer.graph.DependencyGraph;
import com.archanalyzer.model.ProjectModel;
import com.archanalyzer.model.ProjectModel.ComponentNode;

import java.util.*;

/**
 * Creates an isolated, hypothetical copy of the project architecture (graph and component model).
 * Allows hypothetical transformations while strictly guaranteeing the baseline architecture
 * remains completely unmutated.
 */
public class ArchitectureGraphSnapshot {

    private final DependencyGraph originalGraph;
    private final int originalVertexCount;
    private final int originalEdgeCount;

    private final DependencyGraph workingGraph;
    private final ProjectModel workingModel;

    private final Set<String> addedNodes = new LinkedHashSet<>();
    private final Set<String> removedNodes = new LinkedHashSet<>();
    private final Set<String> addedEdges = new LinkedHashSet<>();
    private final Set<String> removedEdges = new LinkedHashSet<>();

    public ArchitectureGraphSnapshot(ProjectModel model, DependencyGraph graph) {
        Objects.requireNonNull(graph, "DependencyGraph cannot be null");
        this.originalGraph = graph;
        this.originalVertexCount = graph.vertexCount();
        this.originalEdgeCount = graph.edgeCount();

        // Deep copy of dependency graph
        this.workingGraph = graph.copy();

        // Shadow project model
        String projectName = model != null ? model.getProjectName() : "SimulatedProject";
        this.workingModel = new ProjectModel(projectName);
        if (model != null) {
            for (ComponentNode cn : model.getComponents()) {
                this.workingModel.addComponent(cn);
            }
        }
    }

    public static ArchitectureGraphSnapshot from(ProjectModel model, DependencyGraph graph) {
        return new ArchitectureGraphSnapshot(model, graph);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Graph Transformation Primitives
    // ──────────────────────────────────────────────────────────────────────────

    public void addInterfaceNode(String fqn) {
        if (fqn == null || fqn.isBlank()) return;
        workingGraph.addVertex(fqn);
        int dot = fqn.lastIndexOf('.');
        String simple = dot >= 0 ? fqn.substring(dot + 1) : fqn;
        String pkg = dot >= 0 ? fqn.substring(0, dot) : "";
        ComponentNode iface = new ComponentNode(fqn, simple, pkg, ComponentNode.Kind.INTERFACE, "");
        workingModel.addComponent(iface);
        addedNodes.add(fqn);
        removedNodes.remove(fqn);
    }

    public void removeNode(String fqn) {
        if (fqn == null || !workingGraph.containsVertex(fqn)) return;
        // Record all incident edges as removed
        for (String to : workingGraph.outgoing(fqn)) {
            removedEdges.add(fqn + " -> " + to);
            addedEdges.remove(fqn + " -> " + to);
        }
        for (String from : workingGraph.incoming(fqn)) {
            removedEdges.add(from + " -> " + fqn);
            addedEdges.remove(from + " -> " + fqn);
        }
        workingGraph.removeVertex(fqn);
        removedNodes.add(fqn);
        addedNodes.remove(fqn);
    }

    public void addEdge(String from, String to) {
        if (from == null || to == null || from.equals(to)) return;
        workingGraph.addEdge(from, to);
        String key = from + " -> " + to;
        addedEdges.add(key);
        removedEdges.remove(key);
    }

    public void removeEdge(String from, String to) {
        if (from == null || to == null) return;
        workingGraph.removeEdge(from, to);
        String key = from + " -> " + to;
        removedEdges.add(key);
        addedEdges.remove(key);
    }

    public void redirectEdge(String from, String oldTo, String newTo) {
        removeEdge(from, oldTo);
        addEdge(from, newTo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Integrity & Safety Verification
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validates that the original baseline graph was completely protected against mutation.
     */
    public boolean verifyBaselineIntegrity() {
        return originalGraph.vertexCount() == originalVertexCount
                && originalGraph.edgeCount() == originalEdgeCount;
    }

    /**
     * Validates that the working graph contains no invalid structural inconsistencies.
     */
    public List<String> validateWorkingGraphIntegrity() {
        List<String> violations = new ArrayList<>();
        for (String v : workingGraph.vertices()) {
            for (String target : workingGraph.outgoing(v)) {
                if (!workingGraph.containsVertex(target)) {
                    violations.add("Dangling edge from '" + v + "' to non-existent vertex '" + target + "'");
                }
            }
        }
        return violations;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Accessors
    // ──────────────────────────────────────────────────────────────────────────

    public DependencyGraph getWorkingGraph() { return workingGraph; }
    public ProjectModel getWorkingModel() { return workingModel; }
    public DependencyGraph getOriginalGraph() { return originalGraph; }
    public Set<String> getAddedNodes() { return Collections.unmodifiableSet(addedNodes); }
    public Set<String> getRemovedNodes() { return Collections.unmodifiableSet(removedNodes); }
    public Set<String> getAddedEdges() { return Collections.unmodifiableSet(addedEdges); }
    public Set<String> getRemovedEdges() { return Collections.unmodifiableSet(removedEdges); }
}
