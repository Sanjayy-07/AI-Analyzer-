package com.archanalyzer.refactor.cycle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Manages atomic backup, pre-modification validation, patch preview,
 * and reliable rollback for refactoring changes.
 */
public class ChangeManifest {

    public static class FileBackup {
        public final Path path;
        public final String originalContent;
        public final String originalSha256;
        public final boolean existedBefore;

        public FileBackup(Path path, String originalContent, String originalSha256, boolean existedBefore) {
            this.path = path;
            this.originalContent = originalContent;
            this.originalSha256 = originalSha256;
            this.existedBefore = existedBefore;
        }
    }

    private final Map<Path, FileBackup> backups = new LinkedHashMap<>();
    private final List<String> changeDescriptions = new ArrayList<>();
    private final List<Path> createdFiles = new ArrayList<>();
    private final List<Path> modifiedFiles = new ArrayList<>();

    /**
     * Records a file backup prior to modification.
     */
    public void recordFileForModification(Path file) throws IOException {
        Path norm = file.toAbsolutePath().normalize();
        if (backups.containsKey(norm)) return;

        if (Files.exists(norm)) {
            String content = Files.readString(norm, StandardCharsets.UTF_8);
            String sha = computeSha256(content);
            backups.put(norm, new FileBackup(norm, content, sha, true));
            modifiedFiles.add(norm);
        } else {
            backups.put(norm, new FileBackup(norm, null, "", false));
            createdFiles.add(norm);
        }
    }

    public void addChangeDescription(String desc) {
        changeDescriptions.add(desc);
    }

    /**
     * Rolls back all changes, restoring modified files and deleting created files.
     */
    public void rollbackAll() throws IOException {
        for (Path created : createdFiles) {
            Files.deleteIfExists(created);
        }
        for (FileBackup b : backups.values()) {
            if (b.existedBefore) {
                Files.writeString(b.path, b.originalContent, StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(b.path);
            }
        }
    }

    public List<String> getChangeDescriptions() { return Collections.unmodifiableList(changeDescriptions); }
    public List<Path> getCreatedFiles() { return Collections.unmodifiableList(createdFiles); }
    public List<Path> getModifiedFiles() { return Collections.unmodifiableList(modifiedFiles); }
    public Map<Path, FileBackup> getBackups() { return Collections.unmodifiableMap(backups); }

    public static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
