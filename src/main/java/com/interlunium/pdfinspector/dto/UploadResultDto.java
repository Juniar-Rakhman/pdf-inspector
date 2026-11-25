package com.interlunium.pdfinspector.dto;

/**
 * Result of a document upload.
 *
 * @param documentId the generated UUID that identifies the analyzed document
 * @param filename   the original file name as uploaded
 */
public record UploadResultDto(String documentId, String filename) {
}
