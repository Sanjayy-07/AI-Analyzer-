package com.archanalyzer.refactor.cycle;

/**
 * Represents a single text substitution at exact character offsets within a source file.
 */
public class SourceEdit implements Comparable<SourceEdit> {

    private final int startOffset;
    private final int endOffset;
    private final String replacementText;
    private final String description;

    public SourceEdit(int startOffset, int endOffset, String replacementText, String description) {
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException(String.format("Invalid edit range [%d, %d]", startOffset, endOffset));
        }
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.replacementText = replacementText != null ? replacementText : "";
        this.description = description != null ? description : "";
    }

    public int getStartOffset() { return startOffset; }
    public int getEndOffset() { return endOffset; }
    public String getReplacementText() { return replacementText; }
    public String getDescription() { return description; }

    @Override
    public int compareTo(SourceEdit other) {
        // Sort descending by startOffset
        int cmp = Integer.compare(other.startOffset, this.startOffset);
        if (cmp != 0) return cmp;
        return Integer.compare(other.endOffset, this.endOffset);
    }

    @Override
    public String toString() {
        return String.format("SourceEdit[%d..%d -> '%s' (%s)]", startOffset, endOffset,
                replacementText.length() > 20 ? replacementText.substring(0, 17) + "..." : replacementText,
                description);
    }
}
