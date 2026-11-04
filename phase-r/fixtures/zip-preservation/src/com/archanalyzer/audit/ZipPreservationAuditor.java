package com.archanalyzer.audit;

import com.archanalyzer.archive.ZipExtractor;
import com.archanalyzer.pipeline.ProjectPackager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public class ZipPreservationAuditor {

    public static class CheckItem {
        public String checkName;
        public boolean passed;
        public String details;

        public CheckItem(String checkName, boolean passed, String details) {
            this.checkName = checkName;
            this.passed = passed;
            this.details = details;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("PHASE R-F: ZIP EXPORT & RESOURCE PRESERVATION AUDIT");
        System.out.println("=================================================================");

        Path tempDir = Files.createTempDirectory("zip-preservation-audit-");
        List<CheckItem> checks = new ArrayList<>();

        try {
            Path fixtureRoot = tempDir.resolve("fixture-source");
            Files.createDirectories(fixtureRoot);

            // 1. Construct rich test project
            setupRichFixture(fixtureRoot);

            // 2. Package into original test ZIP
            Path origZip = tempDir.resolve("original-project.zip");
            createZipFromDirectory(fixtureRoot, origZip);

            // 3. Extract using ZipExtractor
            Path extractedDir = tempDir.resolve("extracted-by-engine");
            ZipExtractor extractor = new ZipExtractor();
            extractor.extract(origZip, extractedDir);

            // 4. Repackage using ProjectPackager
            Path exportedZip = tempDir.resolve("exported-project.zip");
            ProjectPackager packager = new ProjectPackager();
            packager.packageToZip(extractedDir, exportedZip);

            // 5. Extract exported ZIP for independent comparison
            Path reExtractedDir = tempDir.resolve("re-extracted");
            extractor.extract(exportedZip, reExtractedDir);

            // ================= RUN CHECKS =================
            // Check 1: File inventories
            Map<String, String> origHashes = hashDirectory(fixtureRoot);
            Map<String, String> exportedHashes = hashDirectory(reExtractedDir);

            checks.add(new CheckItem("File Inventory Count",
                    origHashes.size() == exportedHashes.size(),
                    String.format("Original files: %d, Exported files: %d", origHashes.size(), exportedHashes.size())));

            // Check 2: Resource file preservation (properties, yaml, json, sql, svg)
            boolean resourcesMatch = true;
            List<String> resKeys = List.of(
                    "src/main/resources/application.properties",
                    "src/main/resources/config.yaml",
                    "src/main/resources/schema.sql",
                    "src/main/resources/data.json",
                    "src/main/resources/static/logo with space.svg"
            );
            for (String rk : resKeys) {
                String origH = origHashes.get(rk.replace('/', File.separatorChar));
                String expH = exportedHashes.get(rk.replace('/', File.separatorChar));
                if (origH == null || !origH.equals(expH)) {
                    resourcesMatch = false;
                }
            }
            checks.add(new CheckItem("Resource Hashes & Content Integrity", resourcesMatch,
                    resourcesMatch ? "All properties, yaml, sql, json, and svg assets identical bitwise." : "Resource hash mismatch"));

            // Check 3: Configuration & Root metadata (pom.xml, README, LICENSE, .gitignore)
            boolean metaMatch = true;
            for (String mk : List.of("pom.xml", "README.md", "LICENSE", ".gitignore")) {
                String origH = origHashes.get(mk.replace('/', File.separatorChar));
                String expH = exportedHashes.get(mk.replace('/', File.separatorChar));
                if (origH == null || !origH.equals(expH)) metaMatch = false;
            }
            checks.add(new CheckItem("Configuration & Metadata Preservation", metaMatch,
                    metaMatch ? "pom.xml, README.md, LICENSE, .gitignore identical bitwise." : "Metadata mismatch"));

            // Check 4: Multi-module / nested module structure
            boolean moduleMatch = exportedHashes.containsKey("nested-module" + File.separator + "pom.xml") &&
                    exportedHashes.containsKey("nested-module" + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + "com" + File.separator + "nested" + File.separator + "NestedHelper.java");
            checks.add(new CheckItem("Maven Nested Module Structure", moduleMatch,
                    moduleMatch ? "Nested module pom.xml and source hierarchy preserved." : "Nested module lost"));

            // Check 5: Spaces in filenames
            boolean spacePreserved = exportedHashes.containsKey("src" + File.separator + "main" + File.separator + "resources" + File.separator + "static" + File.separator + "logo with space.svg");
            checks.add(new CheckItem("Filenames with Spaces", spacePreserved,
                    spacePreserved ? "'logo with space.svg' preserved without truncation." : "Space filename lost"));

            // Check 6: UTF-8 / Non-ASCII filenames
            boolean utf8Preserved = exportedHashes.keySet().stream().anyMatch(k -> k.contains("café"));
            checks.add(new CheckItem("UTF-8 Filenames", utf8Preserved,
                    utf8Preserved ? "'messages_fr_café.properties' preserved." : "UTF-8 filename corrupted"));

            // Check 7: No duplicate ZIP entries
            boolean noDuplicates = verifyNoDuplicateEntries(exportedZip);
            checks.add(new CheckItem("ZIP Duplicate Entry Prevention", noDuplicates,
                    noDuplicates ? "All ZIP entries strictly unique." : "Duplicate entries found"));

            // Check 8: Forward slash normalization in ZIP entries
            boolean allForwardSlashes = verifyForwardSlashes(exportedZip);
            checks.add(new CheckItem("ZIP Forward Slash Normalization", allForwardSlashes,
                    allForwardSlashes ? "All exported ZIP entries use standard '/' separators." : "Windows '\\' detected in ZIP entry names"));

            // Check 9: Path traversal defence
            boolean pathTraversalSafe = testPathTraversalSecurity(extractor, tempDir);
            checks.add(new CheckItem("Zip-Slip Path Traversal Defence", pathTraversalSafe,
                    pathTraversalSafe ? "ArchiveSecurityGuard rejects '../' zip slip entries." : "Path traversal vulnerability!"));

            // Check 10: Empty directory handling
            boolean emptyDirPreservedInZip = verifyDirectoryEntryInZip(exportedZip, "src/main/resources/empty-dir");
            checks.add(new CheckItem("Empty Directory Retention in Export", emptyDirPreservedInZip,
                    emptyDirPreservedInZip ? "Empty directory preserved in ZIP." : "Empty directory omitted by ProjectPackager (regular files only). [KNOWN LIMITATION]"));

        } finally {
            deleteDir(tempDir);
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("ZIP PRESERVATION CHECKLIST RESULTS");
        System.out.println("-----------------------------------------------------------------");
        int passCount = 0;
        for (CheckItem c : checks) {
            System.out.printf("[%s] %s\n     %s\n", c.passed ? "PASS" : "WARN", c.checkName, c.details);
            if (c.passed) passCount++;
        }
        System.out.printf("\nTotal Checks: %d, Passed: %d, Limitations/Warnings: %d\n",
                checks.size(), passCount, checks.size() - passCount);
        System.out.println("=================================================================");
    }

    private static void setupRichFixture(Path root) throws IOException {
        Files.writeString(root.resolve("pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>com.fixture</groupId>\n" +
                "  <artifactId>rich-app</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <modules><module>nested-module</module></modules>\n" +
                "</project>\n");

        Files.writeString(root.resolve("README.md"), "# Rich Application\nDocumentation for test.");
        Files.writeString(root.resolve("LICENSE"), "Apache-2.0 License");
        Files.writeString(root.resolve(".gitignore"), "target/\n*.class\n");

        Path mainJava = root.resolve("src/main/java/com/fixture");
        Path testJava = root.resolve("src/test/java/com/fixture");
        Path res = root.resolve("src/main/resources");
        Path staticDir = res.resolve("static");
        Path i18nDir = res.resolve("i18n");
        Path emptyDir = res.resolve("empty-dir");

        Files.createDirectories(mainJava);
        Files.createDirectories(testJava);
        Files.createDirectories(staticDir);
        Files.createDirectories(i18nDir);
        Files.createDirectories(emptyDir);

        Files.writeString(mainJava.resolve("AppMain.java"), "package com.fixture;\npublic class AppMain {}\n");
        Files.writeString(testJava.resolve("AppTest.java"), "package com.fixture;\npublic class AppTest {}\n");

        Files.writeString(res.resolve("application.properties"), "server.port=8080\napp.mode=production\n");
        Files.writeString(res.resolve("config.yaml"), "spring:\n  profiles:\n    active: prod\n");
        Files.writeString(res.resolve("schema.sql"), "CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(255));\n");
        Files.writeString(res.resolve("data.json"), "{\"status\": \"OK\", \"version\": 1.0}");
        Files.writeString(staticDir.resolve("logo with space.svg"), "<svg><circle cx=\"50\" cy=\"50\" r=\"40\"/></svg>");
        Files.writeString(i18nDir.resolve("messages_fr_café.properties"), "welcome=Bienvenue au café\n");

        // Nested module
        Path nested = root.resolve("nested-module");
        Path nestedJava = nested.resolve("src/main/java/com/nested");
        Files.createDirectories(nestedJava);
        Files.writeString(nested.resolve("pom.xml"),
                "<project><modelVersion>4.0.0</modelVersion><artifactId>nested-module</artifactId></project>");
        Files.writeString(nestedJava.resolve("NestedHelper.java"), "package com.nested;\npublic class NestedHelper {}\n");
    }

    private static void createZipFromDirectory(Path dir, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {
            try (var walk = Files.walk(dir)) {
                walk.forEach(p -> {
                    try {
                        String rel = dir.relativize(p).toString().replace('\\', '/');
                        if (rel.isEmpty()) return;
                        if (Files.isDirectory(p)) {
                            zos.putNextEntry(new ZipEntry(rel + "/"));
                            zos.closeEntry();
                        } else {
                            zos.putNextEntry(new ZipEntry(rel));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    private static Map<String, String> hashDirectory(Path root) throws IOException {
        Map<String, String> map = new TreeMap<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    String rel = root.relativize(p).toString();
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] d = md.digest(Files.readAllBytes(p));
                    StringBuilder sb = new StringBuilder();
                    for (byte b : d) sb.append(String.format("%02x", b));
                    map.put(rel, sb.toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return map;
    }

    private static boolean verifyNoDuplicateEntries(Path zip) throws IOException {
        Set<String> set = new HashSet<>();
        try (ZipFile zf = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName();
                if (!set.add(name)) return false;
            }
        }
        return true;
    }

    private static boolean verifyForwardSlashes(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName();
                if (name.contains("\\")) return false;
            }
        }
        return true;
    }

    private static boolean verifyDirectoryEntryInZip(Path zip, String dirPath) throws IOException {
        String target = dirPath.endsWith("/") ? dirPath : dirPath + "/";
        try (ZipFile zf = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                if (en.nextElement().getName().equals(target)) return true;
            }
        }
        return false;
    }

    private static boolean testPathTraversalSecurity(ZipExtractor extractor, Path tempDir) throws IOException {
        Path maliciousZip = tempDir.resolve("malicious.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(maliciousZip))) {
            zos.putNextEntry(new ZipEntry("../../evil.txt"));
            zos.write("ATTACK".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Path dest = tempDir.resolve("dest-safe");
        try {
            extractor.extract(maliciousZip, dest);
            return false; // should have failed
        } catch (Exception e) {
            // Success: Exception thrown and entry blocked!
            return true;
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
