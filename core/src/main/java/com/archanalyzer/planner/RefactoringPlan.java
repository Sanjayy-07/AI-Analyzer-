package com.archanalyzer.planner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Manages an ordered collection of RefactoringOperations, their execution lifecycle,
 * and deterministic plan versioning via cryptographic hashing.
 */
public class RefactoringPlan {

    private final String planId;
    private String name;
    private PlanLifecycle lifecycle = PlanLifecycle.DRAFT;
    private final List<RefactoringOperation> operations = new ArrayList<>();
    private final long createdAt;
    private long updatedAt;
    private String lastSimulationHash = null;

    public RefactoringPlan(String name) {
        this("plan-" + UUID.randomUUID().toString().substring(0, 8), name);
    }

    public RefactoringPlan(String planId, String name) {
        this.planId = planId != null ? planId : "plan-" + UUID.randomUUID().toString().substring(0, 8);
        this.name = name != null ? name : "Refactoring Plan";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public synchronized void addOperation(RefactoringOperation op) {
        Objects.requireNonNull(op, "Refactoring operation cannot be null");
        operations.removeIf(existing -> existing.getId().equals(op.getId()));
        operations.add(op);
        invalidateSimulation();
    }

    public synchronized boolean removeOperation(String operationId) {
        boolean removed = operations.removeIf(op -> op.getId().equals(operationId));
        if (removed) {
            invalidateSimulation();
        }
        return removed;
    }

    public synchronized void reorderOperations(List<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return;
        Map<String, RefactoringOperation> map = new HashMap<>();
        for (RefactoringOperation op : operations) {
            map.put(op.getId(), op);
        }
        List<RefactoringOperation> reordered = new ArrayList<>();
        for (String id : orderedIds) {
            RefactoringOperation op = map.remove(id);
            if (op != null) {
                reordered.add(op);
            }
        }
        // Append any unlisted operations to preserve them
        reordered.addAll(map.values());
        operations.clear();
        operations.addAll(reordered);
        invalidateSimulation();
    }

    public synchronized void clear() {
        operations.clear();
        invalidateSimulation();
    }

    /**
     * Resets lifecycle to DRAFT whenever the plan structure changes,
     * invalidating prior simulation results and preventing stale applications.
     */
    private void invalidateSimulation() {
        this.lifecycle = PlanLifecycle.DRAFT;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Computes a canonical, deterministic SHA-256 hash of all ordered operation signatures.
     */
    public synchronized String getPlanHash() {
        if (operations.isEmpty()) {
            return "empty-plan";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < operations.size(); i++) {
                String entry = i + ":" + operations.get(i).signature() + "\n";
                digest.update(entry.getBytes(StandardCharsets.UTF_8));
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "plan-fallback-" + operations.size() + "-" + operations.hashCode();
        }
    }

    /**
     * Checks whether the current plan has un-simulated changes.
     */
    public synchronized boolean isSimulationStale() {
        if (lifecycle == PlanLifecycle.DRAFT || lifecycle == PlanLifecycle.VALIDATED) {
            return true;
        }
        return lastSimulationHash == null || !lastSimulationHash.equals(getPlanHash());
    }

    /**
     * Marks the plan as successfully simulated for the given plan hash.
     */
    public synchronized void markSimulated(String hash) {
        this.lastSimulationHash = hash;
        this.lifecycle = PlanLifecycle.SIMULATED;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Approves the plan, strictly validating that the simulation is not stale.
     *
     * @throws IllegalStateException if plan was modified since last simulation
     */
    public synchronized void approve(String currentSimulationHash) {
        if (isSimulationStale()) {
            throw new IllegalStateException("Cannot approve a stale plan. The plan was modified since the last simulation.");
        }
        if (!Objects.equals(this.lastSimulationHash, currentSimulationHash)) {
            throw new IllegalStateException("Simulation hash mismatch. Expected " + this.lastSimulationHash + " but was " + currentSimulationHash);
        }
        this.lifecycle = PlanLifecycle.APPROVED;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Marks the plan as successfully applied to the project.
     */
    public synchronized void markApplied() {
        if (this.lifecycle != PlanLifecycle.APPROVED) {
            throw new IllegalStateException("Only an APPROVED plan can be marked as APPLIED.");
        }
        this.lifecycle = PlanLifecycle.APPLIED;
        this.updatedAt = System.currentTimeMillis();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────────────────────────────────

    public String getPlanId() { return planId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PlanLifecycle getLifecycle() { return lifecycle; }
    public void setLifecycle(PlanLifecycle lifecycle) { this.lifecycle = lifecycle; }
    public List<RefactoringOperation> getOperations() { return Collections.unmodifiableList(operations); }
    public int size() { return operations.size(); }
    public boolean isEmpty() { return operations.isEmpty(); }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public String getLastSimulationHash() { return lastSimulationHash; }
}
