package com.archanalyzer.riskprediction.history;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Immutable value object representing a single Git commit and its file modifications.
 */
public class GitCommit {

    private static final Pattern BUG_FIX_PATTERN = Pattern.compile(
            "\\b(fix|fixed|fixes|bug|bugs|defect|defects|patch|patched|issue|issues|resolve|resolved|resolves|error|errors)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public record FileChurn(int linesAdded, int linesDeleted) {
        public int totalChurn() {
            return linesAdded + linesDeleted;
        }
    }

    private final String commitHash;
    private final long timestampSeconds;
    private final String message;
    private final boolean isBugFix;
    private final Map<String, FileChurn> changedFiles;

    public GitCommit(String commitHash,
                     long timestampSeconds,
                     String message,
                     Map<String, FileChurn> changedFiles) {
        this.commitHash = Objects.requireNonNull(commitHash, "Commit hash cannot be null");
        this.timestampSeconds = timestampSeconds;
        this.message = message != null ? message : "";
        this.isBugFix = BUG_FIX_PATTERN.matcher(this.message).find();
        this.changedFiles = changedFiles != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(changedFiles))
                : Collections.emptyMap();
    }

    public String getCommitHash() { return commitHash; }
    public long getTimestampSeconds() { return timestampSeconds; }
    public String getMessage() { return message; }
    public boolean isBugFix() { return isBugFix; }
    public Map<String, FileChurn> getChangedFiles() { return changedFiles; }

    public boolean touchesFile(String relativePath) {
        if (relativePath == null) return false;
        String normalized = relativePath.replace('\\', '/');
        return changedFiles.containsKey(normalized);
    }

    public FileChurn getChurn(String relativePath) {
        if (relativePath == null) return new FileChurn(0, 0);
        String normalized = relativePath.replace('\\', '/');
        return changedFiles.getOrDefault(normalized, new FileChurn(0, 0));
    }

    @Override
    public String toString() {
        return String.format("Commit[%s, time=%d, files=%d, bugFix=%s, msg='%s']",
                commitHash.substring(0, Math.min(8, commitHash.length())),
                timestampSeconds, changedFiles.size(), isBugFix,
                message.length() > 40 ? message.substring(0, 37) + "..." : message);
    }
}
