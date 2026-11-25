package com.interlunium.pdfinspector.dto;

import java.time.Instant;

/**
 * High level metadata for an analyzed PDF document.
 *
 * @param documentId     the storage UUID of the document
 * @param filename       original file name
 * @param fileSize       file size in bytes
 * @param pageCount      number of pages
 * @param sha256         hex encoded SHA-256 of the raw file
 * @param md5            hex encoded MD5 of the raw file
 * @param signatureCount number of detected signatures
 * @param revisionCount  number of detected incremental revisions
 * @param createdAt      analysis creation time
 * @param title          PDF document title (may be null)
 * @param author         PDF document author (may be null)
 * @param producer       PDF producer (may be null)
 */
public record PdfMetadataDto(
        String documentId,
        String filename,
        long fileSize,
        int pageCount,
        String sha256,
        String md5,
        int signatureCount,
        int revisionCount,
        Instant createdAt,
        String title,
        String author,
        String producer) {
}
