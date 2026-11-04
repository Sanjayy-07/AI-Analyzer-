package com.archanalyzer.pipeline;

import com.archanalyzer.archive.ZipExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for DEF-04:
 * Verifies that empty directories (e.g., src/test/resources, nested empty dirs)
 * are preserved when packaging a project to ZIP and extracting it.
 */
public class EmptyDirectoryPreservationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("DEF-04: Empty directories are preserved across ZIP packaging and extraction")
    public void testEmptyDirectoryPreservation() throws Exception {
        Path sourceProj = tempDir.resolve("source-project");
        Files.createDirectories(sourceProj);

        // 1. Create regular files
        Path javaFile = sourceProj.resolve("src/main/java/com/example/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "package com.example; public class App {}", StandardCharsets.UTF_8);

        // 2. Create empty directories
        Path emptyMainResources = sourceProj.resolve("src/main/resources");
        Files.createDirectories(emptyMainResources);

        Path emptyTestResources = sourceProj.resolve("src/test/resources");
        Files.createDirectories(emptyTestResources);

        Path emptyNestedDir = sourceProj.resolve("config/templates/custom");
        Files.createDirectories(emptyNestedDir);

        // Verify they are indeed empty directories before packaging
        assertTrue(Files.isDirectory(emptyMainResources));
        assertEquals(0, Files.list(emptyMainResources).count());
        assertTrue(Files.isDirectory(emptyTestResources));
        assertEquals(0, Files.list(emptyTestResources).count());
        assertTrue(Files.isDirectory(emptyNestedDir));
        assertEquals(0, Files.list(emptyNestedDir).count());

        // 3. Package to ZIP
        Path zipFile = tempDir.resolve("packaged-project.zip");
        ProjectPackager packager = new ProjectPackager();
        packager.packageToZip(sourceProj, zipFile);

        assertTrue(Files.exists(zipFile), "ZIP file must be created");

        // 4. Verify ZIP entries contain directory entries ending with '/'
        List<String> entryNames = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryNames.add(entry.getName());
            }
        }

        assertTrue(entryNames.contains("src/main/resources/"),
                "ZIP must contain entry for empty main resources dir. Entries: " + entryNames);
        assertTrue(entryNames.contains("src/test/resources/"),
                "ZIP must contain entry for empty test resources dir. Entries: " + entryNames);
        assertTrue(entryNames.contains("config/templates/custom/"),
                "ZIP must contain entry for nested empty custom dir. Entries: " + entryNames);

        // 5. Extract using ZipExtractor into a fresh directory
        Path extractedProj = tempDir.resolve("extracted-project");
        ZipExtractor extractor = new ZipExtractor();
        extractor.extract(zipFile, extractedProj);

        // 6. Assert all empty directories are recreated as actual directories
        Path extractedMainResources = extractedProj.resolve("src/main/resources");
        Path extractedTestResources = extractedProj.resolve("src/test/resources");
        Path extractedNestedDir = extractedProj.resolve("config/templates/custom");
        Path extractedJavaFile = extractedProj.resolve("src/main/java/com/example/App.java");

        assertTrue(Files.exists(extractedMainResources), "Extracted src/main/resources must exist");
        assertTrue(Files.isDirectory(extractedMainResources), "Extracted src/main/resources must be a directory");

        assertTrue(Files.exists(extractedTestResources), "Extracted src/test/resources must exist");
        assertTrue(Files.isDirectory(extractedTestResources), "Extracted src/test/resources must be a directory");

        assertTrue(Files.exists(extractedNestedDir), "Extracted config/templates/custom must exist");
        assertTrue(Files.isDirectory(extractedNestedDir), "Extracted config/templates/custom must be a directory");

        assertTrue(Files.exists(extractedJavaFile), "Extracted App.java must exist");
        assertEquals("package com.example; public class App {}", Files.readString(extractedJavaFile).trim());
    }
}
