package com.archanalyzer.refactor.cycle;

import java.nio.file.Path;
import java.util.*;

/**
 * Encapsulates a complete, validated architectural plan to break a circular dependency
 * through dependency inversion.
 */
public class CycleRefactoringPlan {

    private final Set<String> sccMembers;
    private final CycleCandidateEdge selectedEdge;
    private final List<CycleCandidateEdge> allCandidates;

    private String extractedInterfaceName;
    private String interfacePackage;
    private Path interfaceFilePath;
    private List<String> extractedMethodSignatures = new ArrayList<>();
    private List<Path> affectedFiles = new ArrayList<>();
    private List<String> potentialRisks = new ArrayList<>();
    private String strategyExplanation = "";

    public CycleRefactoringPlan(Set<String> sccMembers,
                                CycleCandidateEdge selectedEdge,
                                List<CycleCandidateEdge> allCandidates) {
        this.sccMembers = Collections.unmodifiableSet(new LinkedHashSet<>(sccMembers));
        this.selectedEdge = selectedEdge;
        this.allCandidates = Collections.unmodifiableList(new ArrayList<>(allCandidates));
    }

    public Set<String> getSccMembers() { return sccMembers; }
    public CycleCandidateEdge getSelectedEdge() { return selectedEdge; }
    public List<CycleCandidateEdge> getAllCandidates() { return allCandidates; }

    public String getExtractedInterfaceName() { return extractedInterfaceName; }
    public void setExtractedInterfaceName(String name) { this.extractedInterfaceName = name; }

    public String getInterfacePackage() { return interfacePackage; }
    public void setInterfacePackage(String pkg) { this.interfacePackage = pkg; }

    public String getInterfaceFqn() {
        if (interfacePackage == null || interfacePackage.isEmpty()) {
            return extractedInterfaceName;
        }
        return interfacePackage + "." + extractedInterfaceName;
    }

    public Path getInterfaceFilePath() { return interfaceFilePath; }
    public void setInterfaceFilePath(Path path) { this.interfaceFilePath = path; }

    public List<String> getExtractedMethodSignatures() { return extractedMethodSignatures; }
    public List<Path> getAffectedFiles() { return affectedFiles; }
    public List<String> getPotentialRisks() { return potentialRisks; }

    public String getStrategyExplanation() { return strategyExplanation; }
    public void setStrategyExplanation(String explanation) { this.strategyExplanation = explanation; }

    @Override
    public String toString() {
        return String.format("CycleRefactoringPlan[sccSize=%d, selected=%s, interface=%s, files=%d]",
                sccMembers.size(),
                selectedEdge != null ? selectedEdge.getEdgeKey() : "none",
                extractedInterfaceName,
                affectedFiles.size());
    }
}
