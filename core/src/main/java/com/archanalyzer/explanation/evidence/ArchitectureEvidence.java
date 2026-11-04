package com.archanalyzer.explanation.evidence;

import java.util.*;

/**
 * Immutable container for all architectural ground-truth evidence collected for a project.
 * Serves as the authoritative source of truth against which LLM explanation claims are verified.
 */
public class ArchitectureEvidence {

    private final List<EvidenceItem> items;
    private final Map<String, EvidenceItem> itemsById = new LinkedHashMap<>();
    private final Map<String, List<EvidenceItem>> itemsBySubject = new LinkedHashMap<>();
    private final Map<EvidenceType, List<EvidenceItem>> itemsByType = new EnumMap<>(EvidenceType.class);

    private final Set<String> allComponentIds = new LinkedHashSet<>();
    private final Map<String, Map<String, Double>> componentMetrics = new LinkedHashMap<>();
    private final Set<String> dependencyEdges = new LinkedHashSet<>(); // "from->to"
    private final List<Set<String>> cycles = new ArrayList<>();
    private final Map<String, Double> riskScores = new LinkedHashMap<>();
    private final Map<String, String> riskCategories = new LinkedHashMap<>();

    public ArchitectureEvidence(
            List<EvidenceItem> items,
            Set<String> allComponentIds,
            Map<String, Map<String, Double>> componentMetrics,
            Set<String> dependencyEdges,
            List<Set<String>> cycles,
            Map<String, Double> riskScores,
            Map<String, String> riskCategories
    ) {
        this.items = items != null ? List.copyOf(items) : List.of();
        if (allComponentIds != null) this.allComponentIds.addAll(allComponentIds);
        if (componentMetrics != null) {
            componentMetrics.forEach((k, v) -> this.componentMetrics.put(k, new LinkedHashMap<>(v)));
        }
        if (dependencyEdges != null) this.dependencyEdges.addAll(dependencyEdges);
        if (cycles != null) {
            for (Set<String> c : cycles) {
                this.cycles.add(Set.copyOf(c));
            }
        }
        if (riskScores != null) this.riskScores.putAll(riskScores);
        if (riskCategories != null) this.riskCategories.putAll(riskCategories);

        for (EvidenceType t : EvidenceType.values()) {
            itemsByType.put(t, new ArrayList<>());
        }

        for (EvidenceItem item : this.items) {
            if (item.evidenceId() != null) {
                itemsById.put(item.evidenceId().toLowerCase(), item);
            }
            if (item.subjectId() != null) {
                itemsBySubject.computeIfAbsent(item.subjectId().toLowerCase(), k -> new ArrayList<>()).add(item);
                if (!"PROJECT".equalsIgnoreCase(item.subjectId())) {
                    this.allComponentIds.add(item.subjectId());
                }
            }
            if (item.targetId() != null && !"PROJECT".equalsIgnoreCase(item.targetId())) {
                this.allComponentIds.add(item.targetId());
            }
            itemsByType.computeIfAbsent(item.type(), k -> new ArrayList<>()).add(item);
        }
    }

    public List<EvidenceItem> getAllItems() {
        return items;
    }

    public int size() {
        return items.size();
    }

    public Optional<EvidenceItem> findById(String evidenceId) {
        if (evidenceId == null) return Optional.empty();
        return Optional.ofNullable(itemsById.get(evidenceId.trim().toLowerCase()));
    }

    public List<EvidenceItem> findBySubject(String subjectId) {
        if (subjectId == null) return List.of();
        return itemsBySubject.getOrDefault(subjectId.trim().toLowerCase(), List.of());
    }

    public List<EvidenceItem> findByType(EvidenceType type) {
        if (type == null) return List.of();
        return itemsByType.getOrDefault(type, List.of());
    }

    public boolean containsComponent(String componentId) {
        if (componentId == null) return false;
        String normalized = componentId.trim().toLowerCase();
        for (String c : allComponentIds) {
            if (c.equalsIgnoreCase(normalized) || normalizeName(c).equalsIgnoreCase(normalizeName(normalized))) {
                return true;
            }
        }
        return false;
    }

    public String resolveCanonicalComponentName(String name) {
        if (name == null) return null;
        for (String c : allComponentIds) {
            if (c.equalsIgnoreCase(name) || normalizeName(c).equalsIgnoreCase(normalizeName(name))) {
                return c;
            }
        }
        return name;
    }

    private String normalizeName(String id) {
        if (id == null) return "";
        int idx = id.lastIndexOf('.');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }

    public Double getMetricValue(String componentId, String metricName) {
        if (componentId == null || metricName == null) return null;
        String canonical = resolveCanonicalComponentName(componentId);
        Map<String, Double> metrics = componentMetrics.get(canonical);
        if (metrics == null) return null;
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(metricName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean hasDependency(String from, String to) {
        if (from == null || to == null) return false;
        String cFrom = resolveCanonicalComponentName(from);
        String cTo = resolveCanonicalComponentName(to);
        return dependencyEdges.contains(cFrom + "->" + cTo)
                || dependencyEdges.contains(from + "->" + to);
    }

    public boolean isInCycle(String componentId) {
        if (componentId == null) return false;
        String canonical = resolveCanonicalComponentName(componentId);
        for (Set<String> cycle : cycles) {
            for (String member : cycle) {
                if (member.equalsIgnoreCase(canonical) || member.equalsIgnoreCase(componentId)
                        || normalizeName(member).equalsIgnoreCase(normalizeName(componentId))) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<String> getCycleMembers(String componentId) {
        if (componentId == null) return Set.of();
        String canonical = resolveCanonicalComponentName(componentId);
        for (Set<String> cycle : cycles) {
            for (String member : cycle) {
                if (member.equalsIgnoreCase(canonical) || member.equalsIgnoreCase(componentId)
                        || normalizeName(member).equalsIgnoreCase(normalizeName(componentId))) {
                    return Collections.unmodifiableSet(cycle);
                }
            }
        }
        return Set.of();
    }

    public Double getRiskScore(String componentId) {
        if (componentId == null) return null;
        String canonical = resolveCanonicalComponentName(componentId);
        return riskScores.get(canonical);
    }

    public String getRiskCategory(String componentId) {
        if (componentId == null) return null;
        String canonical = resolveCanonicalComponentName(componentId);
        return riskCategories.get(canonical);
    }

    public Set<String> getAllComponentIds() {
        return Collections.unmodifiableSet(allComponentIds);
    }

    public Set<String> getDependencyEdges() {
        return Collections.unmodifiableSet(dependencyEdges);
    }

    public List<Set<String>> getCycles() {
        return Collections.unmodifiableList(cycles);
    }

    public Map<String, Map<String, Double>> getComponentMetrics() {
        return Collections.unmodifiableMap(componentMetrics);
    }

    public Map<String, Double> getRiskScores() {
        return Collections.unmodifiableMap(riskScores);
    }

    public Map<String, String> getRiskCategories() {
        return Collections.unmodifiableMap(riskCategories);
    }

    /**
     * Compact evidence representation suitable for embedding in an LLM prompt.
     */
    public String toPromptEvidenceSummary(int maxItems) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== GROUND TRUTH EVIDENCE REGISTER ===\n");
        sb.append(String.format("Total components: %d, Total dependencies: %d, Total cycles: %d\n\n",
                allComponentIds.size(), dependencyEdges.size(), cycles.size()));

        int count = 0;
        for (EvidenceItem item : items) {
            if (++count > maxItems) {
                sb.append(String.format("... [%d additional evidence items truncated]\n", items.size() - maxItems));
                break;
            }
            sb.append(String.format("[%s] Type: %s | Subject: %s", item.evidenceId(), item.type(), item.subjectId()));
            if (item.targetId() != null) sb.append(" -> ").append(item.targetId());
            if (item.metricName() != null) sb.append(" | ").append(item.metricName()).append(" = ").append(item.textValue());
            if (item.description() != null && !item.description().isBlank()) {
                sb.append(" | ").append(item.description());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
