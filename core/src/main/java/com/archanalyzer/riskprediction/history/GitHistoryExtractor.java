package com.archanalyzer.riskprediction.history;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Extracts historical Git commits, timestamps, and file-level churn metrics.
 * Safely degrades if the project lacks a .git folder or if Git is uninstalled.
 */
public class GitHistoryExtractor {

    private static final long PROCESS_TIMEOUT_SECONDS = 30;

    /**
     * Extracts the commit history of the given project directory.
     */
    public GitHistory extractHistory(Path projectRoot) {
        return extract(projectRoot);
    }

    public GitHistory extract(Path projectRoot) {
        if (projectRoot == null || !Files.exists(projectRoot)) {
            return GitHistory.empty(projectRoot);
        }

        Path gitDir = locateGitDirectory(projectRoot);
        if (gitDir == null) {
            return GitHistory.empty(projectRoot);
        }

        Path repoRoot = gitDir.getParent();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "log", "--pretty=format:COMMIT:%H|%at|%s", "--numstat"
            );
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            List<GitCommit> commits;

            try (InputStream in = process.getInputStream()) {
                commits = parseGitLogOutput(in);
            }

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return GitHistory.empty(repoRoot);
            }

            if (process.exitValue() != 0) {
                return GitHistory.empty(repoRoot);
            }

            return GitHistory.of(repoRoot, commits);

        } catch (Exception e) {
            // Graceful fallback when git CLI is unavailable or fails
            return GitHistory.empty(repoRoot);
        }
    }

    /**
     * Parses standard git log --numstat output into structured GitCommit instances.
     */
    public List<GitCommit> parseGitLogOutput(InputStream inputStream) throws IOException {
        List<GitCommit> commits = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            String currentHash = null;
            long currentTimestamp = 0L;
            String currentMessage = "";
            Map<String, GitCommit.FileChurn> currentFiles = new LinkedHashMap<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("COMMIT:")) {
                    if (currentHash != null) {
                        commits.add(new GitCommit(currentHash, currentTimestamp, currentMessage, currentFiles));
                        currentFiles = new LinkedHashMap<>();
                    }
                    String header = line.substring("COMMIT:".length());
                    String[] parts = header.split("\\|", 3);
                    currentHash = parts.length > 0 ? parts[0] : "unknown";
                    try {
                        currentTimestamp = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
                    } catch (NumberFormatException e) {
                        currentTimestamp = 0L;
                    }
                    currentMessage = parts.length > 2 ? parts[2] : "";
                } else {
                    // Numstat line: <added>\t<deleted>\t<path>
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        int added = parseNumstatCount(parts[0]);
                        int deleted = parseNumstatCount(parts[1]);
                        String filePath = parts[2].replace('\\', '/');
                        currentFiles.put(filePath, new GitCommit.FileChurn(added, deleted));
                    }
                }
            }

            if (currentHash != null) {
                commits.add(new GitCommit(currentHash, currentTimestamp, currentMessage, currentFiles));
            }
        }
        return commits;
    }

    private int parseNumstatCount(String token) {
        if ("-".equals(token)) return 0; // binary files
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Path locateGitDirectory(Path start) {
        Path cur = start.toAbsolutePath().normalize();
        for (int i = 0; i < 4; i++) {
            if (cur == null) break;
            Path candidate = cur.resolve(".git");
            if (Files.exists(candidate)) {
                return candidate;
            }
            cur = cur.getParent();
        }
        return null;
    }
}
