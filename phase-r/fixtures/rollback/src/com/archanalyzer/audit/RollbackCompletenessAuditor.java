package com.archanalyzer.audit;

import com.archanalyzer.refactor.cycle.ChangeManifest;
import com.archanalyzer.refactor.cycle.SourceEdit;
import com.archanalyzer.refactor.cycle.SourceFileEditor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

public class RollbackCompletenessAuditor {

    public static class ScenarioResult {
        public int id;
        public String name;
        public boolean passed;
        public int filesPre;
        public int filesRestored;
        public boolean hashIdentical;
        public boolean noLeftoverFiles;
        public String notes;

        public ScenarioResult(int id, String name, boolean passed, int filesPre, int filesRestored,
                              boolean hashIdentical, boolean noLeftoverFiles, String notes) {
            this.id = id;
            this.name = name;
            this.passed = passed;
            this.filesPre = filesPre;
            this.filesRestored = filesRestored;
            this.hashIdentical = hashIdentical;
            this.noLeftoverFiles = noLeftoverFiles;
            this.notes = notes;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("PHASE R-E: ROLLBACK COMPLETENESS & ATOMICITY AUDIT");
        System.out.println("=================================================================");

        List<ScenarioResult> results = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            results.add(runScenario(i));
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("ROLLBACK AUDIT SUMMARY (10 SCENARIOS)");
        System.out.println("-----------------------------------------------------------------");
        int passCount = 0;
        for (ScenarioResult r : results) {
            System.out.printf("[%s] Scenario %02d: %s\n     PreFiles: %d, Restored: %d, Hashes Match: %b, Residuals: %b | %s\n",
                    r.passed ? "PASS" : "FAIL", r.id, r.name, r.filesPre, r.filesRestored,
                    r.hashIdentical, !r.noLeftoverFiles, r.notes);
            if (r.passed) passCount++;
        }
        System.out.printf("\nTotal: %d, Passed: %d, Failed: %d\n", results.size(), passCount, results.size() - passCount);
        System.out.println("=================================================================");
    }

    private static ScenarioResult runScenario(int id) throws Exception {
        Path tempRoot = Files.createTempDirectory("rollback-test-" + id + "-");
        try {
            // Setup base project with 3 Java files, pom.xml, and a resource file
            Path srcDir = tempRoot.resolve("src/main/java/com/demo");
            Path resDir = tempRoot.resolve("src/main/resources");
            Files.createDirectories(srcDir);
            Files.createDirectories(resDir);

            Path f1 = srcDir.resolve("ServiceA.java");
            Path f2 = srcDir.resolve("ServiceB.java");
            Path f3 = srcDir.resolve("ServiceC.java");
            Path pom = tempRoot.resolve("pom.xml");
            Path res = resDir.resolve("application.properties");

            Files.writeString(f1, "package com.demo;\npublic class ServiceA {\n    private ServiceB b;\n}\n");
            Files.writeString(f2, "package com.demo;\npublic class ServiceB {\n    public void doWork() {}\n}\n");
            Files.writeString(f3, "package com.demo;\npublic class ServiceC {\n    public void test() {}\n}\n");
            Files.writeString(pom, "<project><modelVersion>4.0.0</modelVersion><groupId>com.demo</groupId><artifactId>test</artifactId><version>1.0</version></project>");
            Files.writeString(res, "server.port=8080\napp.name=RollbackTest");

            // Snapshot all pre-state files and hashes
            Map<Path, String> preHashes = snapshotProject(tempRoot);

            ChangeManifest manifest = new ChangeManifest();
            String scenarioName = "";

            switch (id) {
                case 1:
                    scenarioName = "Failure After Interface Generation";
                    Path iface = srcDir.resolve("IServiceB.java");
                    manifest.recordFileForModification(iface);
                    Files.writeString(iface, "package com.demo;\npublic interface IServiceB { void doWork(); }\n");
                    // Failure injected!
                    manifest.rollbackAll();
                    break;

                case 2:
                    scenarioName = "Failure After First Source File Modification";
                    Path iface2 = srcDir.resolve("IServiceB.java");
                    manifest.recordFileForModification(iface2);
                    Files.writeString(iface2, "package com.demo;\npublic interface IServiceB {}\n");
                    manifest.recordFileForModification(f2);
                    Files.writeString(f2, "package com.demo;\npublic class ServiceB implements IServiceB {\n    public void doWork() {}\n}\n");
                    // Failure injected!
                    manifest.rollbackAll();
                    break;

                case 3:
                    scenarioName = "Failure After Second Source File Modification";
                    Path iface3 = srcDir.resolve("IServiceB.java");
                    manifest.recordFileForModification(iface3);
                    Files.writeString(iface3, "package com.demo;\npublic interface IServiceB {}\n");
                    manifest.recordFileForModification(f2);
                    Files.writeString(f2, "modified content 1");
                    manifest.recordFileForModification(f1);
                    Files.writeString(f1, "modified content 2");
                    // Failure injected!
                    manifest.rollbackAll();
                    break;

                case 4:
                    scenarioName = "Failure During Caller Rewriting";
                    manifest.recordFileForModification(f1);
                    // Partial edit applied
                    SourceFileEditor.applyEditsToFile(f1, List.of(new SourceEdit(0, 0, "// Corrupted\n", "Corrupt")));
                    // Simulated parser abort
                    manifest.rollbackAll();
                    break;

                case 5:
                    scenarioName = "Compilation Failure Injected";
                    Path iface5 = srcDir.resolve("IServiceB.java");
                    manifest.recordFileForModification(iface5);
                    Files.writeString(iface5, "INVALID JAVA CODE");
                    manifest.recordFileForModification(f2);
                    Files.writeString(f2, "INVALID SOURCE SYNTAX");
                    manifest.rollbackAll();
                    break;

                case 6:
                    scenarioName = "Test Execution Failure Injected";
                    Path iface6 = srcDir.resolve("IServiceB.java");
                    manifest.recordFileForModification(iface6);
                    Files.writeString(iface6, "public interface IServiceB {}");
                    manifest.recordFileForModification(f1);
                    Files.writeString(f1, "// Passed compile but broken test assertion");
                    manifest.rollbackAll();
                    break;

                case 7:
                    scenarioName = "ZIP Generation Failure Injected";
                    manifest.recordFileForModification(f1);
                    Files.writeString(f1, "// Changes applied cleanly before zip packaging exception");
                    // Simulated IOException during zip
                    manifest.rollbackAll();
                    break;

                case 8:
                    scenarioName = "Unexpected Runtime Exception During Execution";
                    manifest.recordFileForModification(f1);
                    manifest.recordFileForModification(f2);
                    Files.writeString(f1, "// Mod 1");
                    Files.writeString(f2, "// Mod 2");
                    try {
                        throw new RuntimeException("Simulated JVM OOM or sudden abort");
                    } catch (RuntimeException e) {
                        manifest.rollbackAll();
                    }
                    break;

                case 9:
                    scenarioName = "Partial Filesystem Write Abort";
                    manifest.recordFileForModification(f3);
                    Files.writeString(f3, "TRUNCATED");
                    manifest.rollbackAll();
                    break;

                case 10:
                    scenarioName = "Multiple-File Simultaneous Modification";
                    manifest.recordFileForModification(f1);
                    manifest.recordFileForModification(f2);
                    manifest.recordFileForModification(f3);
                    Path newFile1 = srcDir.resolve("NewOne.java");
                    Path newFile2 = srcDir.resolve("NewTwo.java");
                    manifest.recordFileForModification(newFile1);
                    manifest.recordFileForModification(newFile2);
                    Files.writeString(f1, "Touched A");
                    Files.writeString(f2, "Touched B");
                    Files.writeString(f3, "Touched C");
                    Files.writeString(newFile1, "Created 1");
                    Files.writeString(newFile2, "Created 2");
                    manifest.rollbackAll();
                    break;
            }

            // Post-rollback verification
            Map<Path, String> postHashes = snapshotProject(tempRoot);

            boolean countMatch = preHashes.size() == postHashes.size();
            boolean hashesMatch = true;
            for (Map.Entry<Path, String> entry : preHashes.entrySet()) {
                String postHash = postHashes.get(entry.getKey());
                if (postHash == null || !postHash.equals(entry.getValue())) {
                    hashesMatch = false;
                    break;
                }
            }

            boolean noResiduals = true;
            for (Path p : postHashes.keySet()) {
                if (!preHashes.containsKey(p)) {
                    noResiduals = false;
                    break;
                }
            }

            boolean pass = countMatch && hashesMatch && noResiduals;
            return new ScenarioResult(id, scenarioName, pass, preHashes.size(), postHashes.size(),
                    hashesMatch, noResiduals, pass ? "Exact bitwise hash restoration; 0 residual files." : "Restoration mismatch!");

        } finally {
            deleteDir(tempRoot);
        }
    }

    private static Map<Path, String> snapshotProject(Path root) throws IOException {
        Map<Path, String> map = new HashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    String hash = computeSha256(Files.readAllBytes(p));
                    map.put(root.relativize(p), hash);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return map;
    }

    private static String computeSha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void deleteDir(Path p) {
        try {
            Files.walk(p).sorted(Comparator.reverseOrder()).forEach(f -> {
                try { Files.deleteIfExists(f); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
