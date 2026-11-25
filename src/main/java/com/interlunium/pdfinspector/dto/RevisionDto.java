package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * A single incremental revision of the PDF file.
 *
 * @param index            zero based revision index
 * @param startOffset      byte offset where this revision begins
 * @param endOffset        byte offset (exclusive) where this revision ends
 * @param xrefOffset       byte offset of the xref table of this revision
 * @param signatureIndices indices of signatures introduced by this revision
 * @param signerNames      signer names of signatures introduced by this revision
 * @param description      human readable description
 * @param isLast           whether this is the most recent revision of the file
 */
public record RevisionDto(
        int index,
        long startOffset,
        long endOffset,
        long xrefOffset,
        List<Integer> signatureIndices,
        List<String> signerNames,
        String description,
        boolean isLast) {
}
