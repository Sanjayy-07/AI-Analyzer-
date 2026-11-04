package com.archanalyzer.planner;

import java.util.*;

/**
 * Represents a single discrete architectural refactoring operation in a RefactoringPlan.
 */
public class RefactoringOperation {

    private final String id;
    private final OperationType type;
    private final String targetComponent;
    private final String secondaryComponent;
    private final String newComponent;
    private final String description;
    private final Map<String, String> parameters = new LinkedHashMap<>();
    private final List<String> preconditions = new ArrayList<>();
    private final List<String> expectedTransformations = new ArrayList<>();

    public RefactoringOperation(String id,
                                OperationType type,
                                String targetComponent,
                                String secondaryComponent,
                                String newComponent,
                                String description) {
        this.id = id != null ? id : "op-" + UUID.randomUUID().toString().substring(0, 8);
        this.type = Objects.requireNonNull(type, "Operation type cannot be null");
        this.targetComponent = targetComponent;
        this.secondaryComponent = secondaryComponent;
        this.newComponent = newComponent;
        this.description = description != null ? description : type.getDisplayName();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Factory Helpers
    // ──────────────────────────────────────────────────────────────────────────

    public static RefactoringOperation extractInterface(String targetClass, String interfaceName, boolean decoupleCallers) {
        String id = "op-extract-" + simpleName(targetClass);
        String iface = (interfaceName != null && !interfaceName.isBlank())
                ? interfaceName
                : deriveInterfaceName(targetClass);
        String desc = String.format("Extract interface '%s' from '%s'%s",
                iface, targetClass, decoupleCallers ? " and decouple callers" : "");
        RefactoringOperation op = new RefactoringOperation(id, OperationType.EXTRACT_INTERFACE, targetClass, null, iface, desc);
        op.parameters.put("decoupleCallers", Boolean.toString(decoupleCallers));
        op.parameters.put("interfaceName", iface);
        op.preconditions.add("Target class '" + targetClass + "' must exist in the project model.");
        op.expectedTransformations.add("Create new interface node '" + iface + "'.");
        op.expectedTransformations.add("Add implements edge '" + targetClass + " -> " + iface + "'.");
        if (decoupleCallers) {
            op.expectedTransformations.add("Redirect incoming caller edges from '" + targetClass + "' to '" + iface + "'.");
        }
        return op;
    }

    public static RefactoringOperation breakCycle(String fromComponent, String toComponent, String interfaceName) {
        String id = "op-cycle-" + simpleName(fromComponent) + "-" + simpleName(toComponent);
        String iface = (interfaceName != null && !interfaceName.isBlank())
                ? interfaceName
                : deriveInterfaceName(toComponent);
        String desc = String.format("Break cycle by extracting '%s' from '%s' and decoupling '%s'",
                iface, toComponent, fromComponent);
        RefactoringOperation op = new RefactoringOperation(id, OperationType.BREAK_CYCLE, fromComponent, toComponent, iface, desc);
        op.parameters.put("interfaceName", iface);
        op.parameters.put("providerClass", toComponent);
        op.parameters.put("callerClass", fromComponent);
        op.preconditions.add("Edge '" + fromComponent + " -> " + toComponent + "' must exist and participate in a cycle.");
        op.expectedTransformations.add("Extract interface node '" + iface + "'.");
        op.expectedTransformations.add("Remove direct edge '" + fromComponent + " -> " + toComponent + "'.");
        op.expectedTransformations.add("Add edge '" + fromComponent + " -> " + iface + "'.");
        op.expectedTransformations.add("Add edge '" + toComponent + " -> " + iface + "'.");
        return op;
    }

    public static RefactoringOperation removeDependency(String fromComponent, String toComponent, String reason) {
        String id = "op-remove-" + simpleName(fromComponent) + "-" + simpleName(toComponent);
        String desc = String.format("Remove direct coupling from '%s' to '%s'%s",
                fromComponent, toComponent, reason != null ? " (" + reason + ")" : "");
        RefactoringOperation op = new RefactoringOperation(id, OperationType.REMOVE_DEPENDENCY, fromComponent, toComponent, null, desc);
        if (reason != null) op.parameters.put("reason", reason);
        op.preconditions.add("Direct dependency edge from '" + fromComponent + "' to '" + toComponent + "' must exist.");
        op.expectedTransformations.add("Remove edge '" + fromComponent + " -> " + toComponent + "'.");
        return op;
    }

    public static RefactoringOperation redirectDependency(String fromComponent, String oldTarget, String newTarget) {
        String id = "op-redirect-" + simpleName(fromComponent) + "-" + simpleName(newTarget);
        String desc = String.format("Redirect dependency from '%s -> %s' to '%s -> %s'",
                fromComponent, oldTarget, fromComponent, newTarget);
        RefactoringOperation op = new RefactoringOperation(id, OperationType.REDIRECT_DEPENDENCY, fromComponent, oldTarget, newTarget, desc);
        op.parameters.put("oldTarget", oldTarget);
        op.parameters.put("newTarget", newTarget);
        op.preconditions.add("Edge '" + fromComponent + " -> " + oldTarget + "' must exist.");
        op.preconditions.add("New target component '" + newTarget + "' must exist or be created.");
        op.expectedTransformations.add("Remove edge '" + fromComponent + " -> " + oldTarget + "'.");
        op.expectedTransformations.add("Add edge '" + fromComponent + " -> " + newTarget + "'.");
        return op;
    }

    public static RefactoringOperation introduceAbstraction(String abstractionName, String providerComponent, List<String> callers) {
        String id = "op-abstract-" + simpleName(abstractionName);
        String desc = String.format("Introduce abstraction '%s' for '%s' across %d callers",
                abstractionName, providerComponent, callers != null ? callers.size() : 0);
        RefactoringOperation op = new RefactoringOperation(id, OperationType.INTRODUCE_ABSTRACTION, providerComponent, null, abstractionName, desc);
        op.parameters.put("abstractionName", abstractionName);
        op.parameters.put("callers", callers != null ? String.join(",", callers) : "");
        op.preconditions.add("Provider component '" + providerComponent + "' must exist.");
        op.expectedTransformations.add("Create new abstraction node '" + abstractionName + "'.");
        op.expectedTransformations.add("Add implements edge '" + providerComponent + " -> " + abstractionName + "'.");
        if (callers != null) {
            for (String c : callers) {
                op.expectedTransformations.add("Redirect edge '" + c + " -> " + providerComponent + "' to '" + c + " -> " + abstractionName + "'.");
            }
        }
        return op;
    }

    private static String simpleName(String fqn) {
        if (fqn == null) return "unknown";
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String deriveInterfaceName(String targetClass) {
        if (targetClass == null) return "IComponent";
        int dot = targetClass.lastIndexOf('.');
        if (dot >= 0) {
            String pkg = targetClass.substring(0, dot);
            String simple = targetClass.substring(dot + 1);
            return pkg + ".I" + simple;
        }
        return "I" + targetClass;
    }

    /**
     * Stable canonical signature used for plan hashing and determinism.
     */
    public String signature() {
        return type.name() + ":" +
                Objects.toString(targetComponent, "") + "->" +
                Objects.toString(secondaryComponent, "") + "=>" +
                Objects.toString(newComponent, "") + "{" +
                parameters.toString() + "}";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public OperationType getType() { return type; }
    public String getTargetComponent() { return targetComponent; }
    public String getSecondaryComponent() { return secondaryComponent; }
    public String getNewComponent() { return newComponent; }
    public String getDescription() { return description; }
    public Map<String, String> getParameters() { return Collections.unmodifiableMap(parameters); }
    public List<String> getPreconditions() { return Collections.unmodifiableList(preconditions); }
    public List<String> getExpectedTransformations() { return Collections.unmodifiableList(expectedTransformations); }

    public void setParameter(String key, String value) {
        this.parameters.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefactoringOperation that)) return false;
        return Objects.equals(id, that.id) || Objects.equals(signature(), that.signature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, signature());
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", id, description, type);
    }
}
