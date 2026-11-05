package com.archanalyzer.audit;

import com.archanalyzer.archive.ArchiveSecurityException;
import com.archanalyzer.archive.ZipExtractor;
import com.archanalyzer.pipeline.ProjectPackager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S.1 — Independent Audit for DEF-04 (ZIP and Project Integrity Challenge):
 * Tests preservation of diverse file types, Unicode, spaces in filenames, empty directories,
 * SHA-256 roundtrip hash preservation, and security defenses (Zip Slip, absolute paths).
 */
public class Def04ZipIntegrityChallengeTest {

    @TempDir
    Path tempDir;

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void addEntry(ZipOutputStream zos, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        if (content != null && content.length > 0) {
            zos.write(content);
        }
        zos.closeEntry();
    }

    // =========================================================================
    // 1. Full Multi-Asset ZIP Roundtrip & SHA-256 Integrity Verification
    // =========================================================================
    @Test
    @DisplayName("1. Multi-asset ZIP roundtrip with SHA-256 preservation & empty directory preservation")
    public void testMultiAssetZipRoundtripIntegrity() throws Exception {
        Path origDir = tempDir.resolve("original-project");
        Files.createDirectories(origDir);

        // 1. Java sources & tests
        Path src = origDir.resolve("src/main/java/com/demo/App.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "package com.demo;\npublic class App {}", StandardCharsets.UTF_8);

        Path test = origDir.resolve("src/test/java/com/demo/AppTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "package com.demo;\npublic class AppTest {}", StandardCharsets.UTF_8);

        // 2. Metadata files
        Files.writeString(origDir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>", StandardCharsets.UTF_8);
        Files.writeString(origDir.resolve("README.md"), "# Demo Project\nDescription here.", StandardCharsets.UTF_8);
        Files.writeString(origDir.resolve("LICENSE"), "Apache-2.0", StandardCharsets.UTF_8);
        Files.writeString(origDir.resolve(".gitignore"), "target/\n*.class", StandardCharsets.UTF_8);

        // 3. Various data file formats
        Files.writeString(origDir.resolve("config.yaml"), "server:\n  port: 8080\n", StandardCharsets.UTF_8);
        Files.writeString(origDir.resolve("data.json"), "{\"status\": \"ok\", \"count\": 42}", StandardCharsets.UTF_8);
        Files.writeString(origDir.resolve("schema.sql"), "CREATE TABLE users (id INT, name VARCHAR(50));", StandardCharsets.UTF_8);
        Files.writeString(origDir.resolve("icon.svg"), "<svg xmlns='http://www.w3.org/2000/svg'><circle r='10'/></svg>", StandardCharsets.UTF_8);

        // 4. Unicode content & UTF-8 / filenames with spaces
        Path unicodeFile = origDir.resolve("unicode-sample.txt");
        Files.writeString(unicodeFile, "こんにちは / Bonjour / Привет / 🚀 Sparkle", StandardCharsets.UTF_8);

        Path fileWithSpaces = origDir.resolve("docs/user guide v1.0.txt");
        Files.createDirectories(fileWithSpaces.getParent());
        Files.writeString(fileWithSpaces, "Guide text with spaces in path", StandardCharsets.UTF_8);

        // 5. Binary file
        Path binaryFile = origDir.resolve("binary.dat");
        byte[] binaryBytes = new byte[]{0x00, 0x01, (byte) 0xFF, (byte) 0xFE, 0x42, 0x7A};
        Files.write(binaryFile, binaryBytes);

        // 6. Empty directories
        Path emptyMainResources = origDir.resolve("src/main/resources");
        Path emptyTestResources = origDir.resolve("src/test/resources");
        Path emptyNestedDir = origDir.resolve("docs/templates/empty_nested");
        Files.createDirectories(emptyMainResources);
        Files.createDirectories(emptyTestResources);
        Files.createDirectories(emptyNestedDir);

        // Record SHA-256 hashes of original files before packaging
        String origAppHash = sha256(Files.readAllBytes(src));
        String origPomHash = sha256(Files.readAllBytes(origDir.resolve("pom.xml")));
        String origUnicodeHash = sha256(Files.readAllBytes(unicodeFile));
        String origBinaryHash = sha256(Files.readAllBytes(binaryFile));

        // Package to ZIP
        Path zipFile = tempDir.resolve("deliverable.zip");
        ProjectPackager packager = new ProjectPackager();
        packager.packageToZip(origDir, zipFile);
        assertTrue(Files.exists(zipFile));

        // Extract ZIP to new location
        Path extractedDir = tempDir.resolve("extracted-project");
        ZipExtractor extractor = new ZipExtractor();
        extractor.extract(zipFile, extractedDir);

        // Verify SHA-256 hashes of extracted files match original exactly
        assertEquals(origAppHash, sha256(Files.readAllBytes(extractedDir.resolve("src/main/java/com/demo/App.java"))), "App.java hash mismatch");
        assertEquals(origPomHash, sha256(Files.readAllBytes(extractedDir.resolve("pom.xml"))), "pom.xml hash mismatch");
        assertEquals(origUnicodeHash, sha256(Files.readAllBytes(extractedDir.resolve("unicode-sample.txt"))), "Unicode file hash mismatch");
        assertEquals(origBinaryHash, sha256(Files.readAllBytes(extractedDir.resolve("binary.dat"))), "Binary file hash mismatch");

        // Verify empty directories were preserved
        assertTrue(Files.isDirectory(extractedDir.resolve("src/main/resources")), "src/main/resources must exist");
        assertTrue(Files.isDirectory(extractedDir.resolve("src/test/resources")), "src/test/resources must exist");
        assertTrue(Files.isDirectory(extractedDir.resolve("docs/templates/empty_nested")), "docs/templates/empty_nested must exist");
    }

    // =========================================================================
    // 2. Security Test: Zip Slip Path Traversal Attack Rejected
    // =========================================================================
    @Test
    @DisplayName("2. Security: Zip Slip path traversal attack is blocked")
    public void testZipSlipPathTraversalBlocked() throws Exception {
        Path maliciousZip = tempDir.resolve("zip-slip.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(maliciousZip))) {
            addEntry(zos, "../../evil.sh", "echo 'pwned'".getBytes(StandardCharsets.UTF_8));
        }

        Path dest = tempDir.resolve("safe-extract-slip");
        ZipExtractor extractor = new ZipExtractor();

        ArchiveSecurityException ex = assertThrows(ArchiveSecurityException.class, () -> extractor.extract(maliciousZip, dest));
        assertTrue(ex.getMessage().contains("Path traversal") || ex.getMessage().contains("escape") || ex.getMessage().contains("traversal"),
                "Must reject Zip Slip traversal attempt: " + ex.getMessage());
    }

    // =========================================================================
    // 3. Security Test: Absolute Paths and Drive Letters Rejected
    // =========================================================================
    @Test
    @DisplayName("3. Security: Absolute paths in ZIP entries are blocked")
    public void testAbsolutePathEntryBlocked() throws Exception {
        Path maliciousZip = tempDir.resolve("absolute-path.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(maliciousZip))) {
            addEntry(zos, "/etc/passwd", "root:x:0:0".getBytes(StandardCharsets.UTF_8));
        }

        Path dest = tempDir.resolve("safe-extract-abs");
        ZipExtractor extractor = new ZipExtractor();

        ArchiveSecurityException ex = assertThrows(ArchiveSecurityException.class, () -> extractor.extract(maliciousZip, dest));
        assertTrue(ex.getMessage().contains("absolute path"), "Must reject absolute path: " + ex.getMessage());
    }
}
