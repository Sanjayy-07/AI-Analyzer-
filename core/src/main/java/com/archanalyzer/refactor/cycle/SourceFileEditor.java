package com.archanalyzer.refactor.cycle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Applies multiple source-position-based edits to a source file safely in descending offset order.
 * Prevents offset corruption and detects overlapping edits.
 */
public class SourceFileEditor {

    /**
     * Applies a list of edits to the provided source code string in descending position order.
     */
    public static String applyEdits(String source, List<SourceEdit> edits) {
        if (edits == null || edits.isEmpty()) {
            return source;
        }

        // Sort descending by start offset
        List<SourceEdit> sorted = new ArrayList<>(edits);
        Collections.sort(sorted);

        // Verify bounds and non-overlapping invariant
        for (int i = 0; i < sorted.size(); i++) {
            SourceEdit current = sorted.get(i);
            if (current.getEndOffset() > source.length()) {
                throw new IndexOutOfBoundsException(String.format("Edit range [%d, %d] exceeds source length %d",
                        current.getStartOffset(), current.getEndOffset(), source.length()));
            }
            if (i + 1 < sorted.size()) {
                SourceEdit next = sorted.get(i + 1); // next has <= startOffset
                if (next.getEndOffset() > current.getStartOffset()) {
                    throw new IllegalStateException(String.format("Overlapping edits detected: %s overlaps with %s",
                            next, current));
                }
            }
        }

        StringBuilder sb = new StringBuilder(source);
        for (SourceEdit edit : sorted) {
            sb.replace(edit.getStartOffset(), edit.getEndOffset(), edit.getReplacementText());
        }
        return sb.toString();
    }

    /**
     * Reads a file, applies the edits in descending order, and writes the result back.
     */
    public static void applyEditsToFile(Path file, List<SourceEdit> edits) throws IOException {
        String original = Files.readString(file, StandardCharsets.UTF_8);
        String updated = applyEdits(original, edits);
        if (!updated.equals(original)) {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        }
    }
}
