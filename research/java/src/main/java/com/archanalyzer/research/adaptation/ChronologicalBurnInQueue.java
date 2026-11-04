package com.archanalyzer.research.adaptation;

import java.util.*;

/**
 * ChronologicalBurnInQueue
 * ========================
 * Enforces strict chronological ordering and causal defect feedback maturation
 * for Phase 3A burn-in and online adaptation experiments.
 *
 * Requirements:
 * 1. Strict time sort: Commits ordered by timestamp; stable tie-breaking on index.
 * 2. 90-Day Delay Queue: Label y_j is matured only when T_j + 90 days <= T_query.
 * 3. Window Isolation: Burn-in [0, K) and Evaluation [K, N) strictly separated.
 * 4. Zero Future Contamination: Future outcomes never visible to adaptation state.
 */
public final class ChronologicalBurnInQueue {

    public static final long NANOS_PER_DAY = 86_400L * 1_000_000_000L;
    public static final long DEFAULT_DELAY_NANOS = 90L * NANOS_PER_DAY;

    public static class CommitRecord {
        public final int originalIndex;
        public final String commitId;
        public final long timestampNanos;
        public final int isFaultInducing;
        public final double baselineProbability;

        public CommitRecord(int originalIndex, String commitId, long timestampNanos, int isFaultInducing, double baselineProbability) {
            this.originalIndex = originalIndex;
            this.commitId = commitId;
            this.timestampNanos = timestampNanos;
            this.isFaultInducing = isFaultInducing;
            this.baselineProbability = baselineProbability;
        }
    }

    public static class PartitionResult {
        public final List<CommitRecord> burnInCommits;
        public final List<CommitRecord> evaluationCommits;
        public final boolean[] burnInMaturityMask;
        public final int maturedDefectCount;
        public final int maturedTotalCount;

        public PartitionResult(
                List<CommitRecord> burnInCommits,
                List<CommitRecord> evaluationCommits,
                boolean[] burnInMaturityMask,
                int maturedDefectCount,
                int maturedTotalCount
        ) {
            this.burnInCommits = burnInCommits;
            this.evaluationCommits = evaluationCommits;
            this.burnInMaturityMask = burnInMaturityMask;
            this.maturedDefectCount = maturedDefectCount;
            this.maturedTotalCount = maturedTotalCount;
        }
    }

    private ChronologicalBurnInQueue() {}

    /**
     * Sorts commits strictly by timestamp with stable originalIndex tie-breaking.
     */
    public static List<CommitRecord> sortChronologically(List<CommitRecord> commits) {
        Objects.requireNonNull(commits, "commits cannot be null");
        List<CommitRecord> sorted = new ArrayList<>(commits);
        sorted.sort((a, b) -> {
            int cmp = Long.compare(a.timestampNanos, b.timestampNanos);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a.originalIndex, b.originalIndex);
        });
        return Collections.unmodifiableList(sorted);
    }

    /**
     * Partitions chronological commits into Burn-in [0, K) and Evaluation [K, N),
     * enforcing the simulated 90-day maturation delay at the evaluation boundary.
     *
     * @param sortedCommits Chronologically ordered commits
     * @param burnInSize K commits in burn-in window
     * @param delayNanos Maturation delay in nanoseconds (default 90 days)
     */
    public static PartitionResult partitionWithDelay(
            List<CommitRecord> sortedCommits,
            int burnInSize,
            long delayNanos
    ) {
        Objects.requireNonNull(sortedCommits, "sortedCommits cannot be null");
        int total = sortedCommits.size();
        if (burnInSize <= 0 || burnInSize >= total) {
            throw new IllegalArgumentException("burnInSize must be in (0, total Commits), got: " + burnInSize + ", total: " + total);
        }

        List<CommitRecord> burnIn = new ArrayList<>(sortedCommits.subList(0, burnInSize));
        List<CommitRecord> eval = new ArrayList<>(sortedCommits.subList(burnInSize, total));

        // Cutoff timestamp is the timestamp of the first evaluation commit (start of operational prediction)
        long evalStartTimestamp = eval.get(0).timestampNanos;

        boolean[] maturityMask = new boolean[burnInSize];
        int maturedCount = 0;
        int maturedFaults = 0;

        for (int i = 0; i < burnInSize; i++) {
            CommitRecord rec = burnIn.get(i);
            // Label is matured iff rec.timestampNanos + delayNanos <= evalStartTimestamp
            if (rec.timestampNanos + delayNanos <= evalStartTimestamp) {
                maturityMask[i] = true;
                maturedCount++;
                if (rec.isFaultInducing == 1) {
                    maturedFaults++;
                }
            } else {
                maturityMask[i] = false;
            }
        }

        return new PartitionResult(
                Collections.unmodifiableList(burnIn),
                Collections.unmodifiableList(eval),
                maturityMask,
                maturedFaults,
                maturedCount
        );
    }

    /**
     * Retrospective Burn-in Partitioning:
     * Treats the burn-in sample as having matured its full 90-day incubation window
     * prior to the organization initiating operational scoring on subsequent commits.
     */
    public static PartitionResult partitionRetrospective(List<CommitRecord> sortedCommits, int burnInSize) {
        Objects.requireNonNull(sortedCommits, "sortedCommits cannot be null");
        int total = sortedCommits.size();
        if (burnInSize <= 0 || burnInSize >= total) {
            throw new IllegalArgumentException("burnInSize must be in (0, total Commits), got: " + burnInSize);
        }

        List<CommitRecord> burnIn = new ArrayList<>(sortedCommits.subList(0, burnInSize));
        List<CommitRecord> eval = new ArrayList<>(sortedCommits.subList(burnInSize, total));

        boolean[] maturityMask = new boolean[burnInSize];
        Arrays.fill(maturityMask, true);

        int maturedFaults = 0;
        for (CommitRecord rec : burnIn) {
            if (rec.isFaultInducing == 1) {
                maturedFaults++;
            }
        }

        return new PartitionResult(
                Collections.unmodifiableList(burnIn),
                Collections.unmodifiableList(eval),
                maturityMask,
                maturedFaults,
                burnInSize
        );
    }
}
