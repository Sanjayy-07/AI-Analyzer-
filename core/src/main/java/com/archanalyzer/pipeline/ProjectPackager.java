package com.archanalyzer.pipeline;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.zip.*;

/**
 * PART 27: packages the final (possibly refactored) project directory into a real ZIP file.
 * Preserves both regular files and empty directory structures (DEF-04).
 */
public class ProjectPackager {

    public void packageToZip(Path projectDir, Path outputZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            try (Stream<Path> walk = Files.walk(projectDir)) {
                walk.filter(p -> !p.equals(projectDir)).forEach(p -> {
                    try {
                        String relPath = projectDir.relativize(p).toString().replace('\\', '/');
                        if (Files.isDirectory(p)) {
                            // Preserve empty directories so that critical project directory structures
                            // (e.g. src/test/resources, empty package folders) are retained on extraction.
                            try (Stream<Path> children = Files.list(p)) {
                                if (children.findFirst().isEmpty()) {
                                    String entryName = relPath.endsWith("/") ? relPath : relPath + "/";
                                    zos.putNextEntry(new ZipEntry(entryName));
                                    zos.closeEntry();
                                }
                            }
                        } else if (Files.isRegularFile(p)) {
                            zos.putNextEntry(new ZipEntry(relPath));
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
}
