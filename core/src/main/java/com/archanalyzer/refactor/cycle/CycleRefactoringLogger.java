package com.archanalyzer.refactor.cycle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Structured logger for automated cycle-breaking refactoring executions.
 * Emits human-readable and machine-parsable JSON repair records.
 */
public class CycleRefactoringLogger {

    public static class LogEntry {
        public String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        public String projectPath;
        public String status;
        public int initialCyclicSccCount;
        public int finalCyclicSccCount;
        public List<String> sccMembers;
        public String selectedEdgeFrom;
        public String selectedEdgeTo;
        public double selectedEdgeCost;
        public String extractedInterface;
        public String interfaceFilePath;
        public List<String> affectedFiles;
        public boolean compilationPassed;
        public boolean testsPassed;
        public long executionDurationMs;
        public List<String> errors;
        public String strategyExplanation;
    }

    public static String toJson(LogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"timestamp\": \"").append(escape(entry.timestamp)).append("\",\n");
        sb.append("  \"projectPath\": \"").append(escape(entry.projectPath)).append("\",\n");
        sb.append("  \"status\": \"").append(escape(entry.status)).append("\",\n");
        sb.append("  \"initialCyclicSccCount\": ").append(entry.initialCyclicSccCount).append(",\n");
        sb.append("  \"finalCyclicSccCount\": ").append(entry.finalCyclicSccCount).append(",\n");
        sb.append("  \"sccMembers\": [");
        if (entry.sccMembers != null) {
            for (int i = 0; i < entry.sccMembers.size(); i++) {
                sb.append("\"").append(escape(entry.sccMembers.get(i))).append("\"");
                if (i < entry.sccMembers.size() - 1) sb.append(", ");
            }
        }
        sb.append("],\n");
        sb.append("  \"selectedEdge\": {\n");
        sb.append("    \"from\": \"").append(escape(entry.selectedEdgeFrom)).append("\",\n");
        sb.append("    \"to\": \"").append(escape(entry.selectedEdgeTo)).append("\",\n");
        sb.append("    \"costScore\": ").append(entry.selectedEdgeCost).append("\n");
        sb.append("  },\n");
        sb.append("  \"extractedInterface\": \"").append(escape(entry.extractedInterface)).append("\",\n");
        sb.append("  \"interfaceFilePath\": \"").append(escape(entry.interfaceFilePath)).append("\",\n");
        sb.append("  \"affectedFiles\": [");
        if (entry.affectedFiles != null) {
            for (int i = 0; i < entry.affectedFiles.size(); i++) {
                sb.append("\"").append(escape(entry.affectedFiles.get(i))).append("\"");
                if (i < entry.affectedFiles.size() - 1) sb.append(", ");
            }
        }
        sb.append("],\n");
        sb.append("  \"compilationPassed\": ").append(entry.compilationPassed).append(",\n");
        sb.append("  \"testsPassed\": ").append(entry.testsPassed).append(",\n");
        sb.append("  \"executionDurationMs\": ").append(entry.executionDurationMs).append(",\n");
        sb.append("  \"errors\": [");
        if (entry.errors != null) {
            for (int i = 0; i < entry.errors.size(); i++) {
                sb.append("\"").append(escape(entry.errors.get(i))).append("\"");
                if (i < entry.errors.size() - 1) sb.append(", ");
            }
        }
        sb.append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static void writeLogFile(Path outputDir, LogEntry entry) throws IOException {
        Files.createDirectories(outputDir);
        String filename = "novelty1_repair_log_" + System.currentTimeMillis() + ".json";
        Path targetFile = outputDir.resolve(filename);
        Files.writeString(targetFile, toJson(entry), StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
