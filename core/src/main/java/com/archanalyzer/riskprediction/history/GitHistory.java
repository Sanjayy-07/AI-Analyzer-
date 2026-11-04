package com.archanalyzer.riskprediction.history;

import java.nio.file.Path;
import java.util.*;

/**
 * Encapsulates the historical commit sequence of a project repository.
 * Supports chronological filtering, temporal observation windows, and file lookup.
 */
public class GitHistory {

    private final Path repositoryRoot;
    private final List<GitCommit> commits;
    private final boolean available;
    private final Map<String, List<GitCommit>> commitsByFile = new HashMap<>();

    public GitHistory(Path repositoryRoot, List<GitCommit> rawCommits, boolean available) {
        this.repositoryRoot = repositoryRoot;
        this.available = available;

        if (rawCommits != null && !rawCommits.isEmpty()) {
            List<GitCommit> sorted = new ArrayList<>(rawCommits);
            // Sort chronologically (oldest commit first)
            sorted.sort(Comparator.comparingLong(GitCommit::getTimestampSeconds));
            this.commits = Collections.unmodifiableList(sorted);

            for (GitCommit c : this.commits) {
                for (String file : c.getChangedFiles().keySet()) {
                    commitsByFile.computeIfAbsent(file, k -> new ArrayList<>()).add(c);
                }
            }
        } else {
            this.commits = Collections.emptyList();
        }
    }

    public static GitHistory empty(Path repositoryRoot) {
        return new GitHistory(repositoryRoot, List.of(), false);
    }

    public static GitHistory of(Path repositoryRoot, List<GitCommit> commits) {
        return new GitHistory(repositoryRoot, commits, true);
    }

    public boolean isAvailable() { return available; }
    public Path getRepositoryRoot() { return repositoryRoot; }
    public List<GitCommit> getCommits() { return commits; }
    public int commitCount() { return commits.size(); }
    public int getCommitCount() { return commits.size(); }

    public long getEarliestTimestamp() {
        return commits.isEmpty() ? 0L : commits.get(0).getTimestampSeconds();
    }

    public long getLatestTimestamp() {
        return commits.isEmpty() ? 0L : commits.get(commits.size() - 1).getTimestampSeconds();
    }

    public List<GitCommit> getCommitsForFile(String relativePath) {
        if (relativePath == null) return Collections.emptyList();
        String norm = relativePath.replace('\\', '/');
        return Collections.unmodifiableList(commitsByFile.getOrDefault(norm, Collections.emptyList()));
    }

    /**
     * Extracts a leakage-safe historical sub-window strictly constrained to [startSeconds, endSeconds].
     * Future commits beyond endSeconds are completely excluded.
     */
    public GitHistory filterWindow(long startSeconds, long endSeconds) {
        if (!available || commits.isEmpty()) return this;
        List<GitCommit> filtered = new ArrayList<>();
        for (GitCommit c : commits) {
            if (c.getTimestampSeconds() >= startSeconds && c.getTimestampSeconds() <= endSeconds) {
                filtered.add(c);
            }
        }
        return new GitHistory(repositoryRoot, filtered, true);
    }

    public Set<String> getAllModifiedFiles() {
        return Collections.unmodifiableSet(commitsByFile.keySet());
    }
}
