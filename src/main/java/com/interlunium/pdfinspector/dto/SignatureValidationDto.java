package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * Result of validating a single digital signature of a PDF document.
 *
 * @param signatureIndex       zero based index of the signature
 * @param signerName           signer name as extracted from the CMS
 * @param signatureIntegrity   VALID when the CMS signature mathematically verifies against the signer certificate
 * @param documentIntegrity    VALID when the /ByteRange content digests match the message-digest attribute
 * @param coversWholeDocument  whether the byte range covers the whole current file
 * @param modifiedAfterSigning true when later data (revisions) exists beyond the signature byte range
 * @param messages             explanatory messages
 */
public record SignatureValidationDto(
        int signatureIndex,
        String signerName,
        String signatureIntegrity,
        String documentIntegrity,
        boolean coversWholeDocument,
        boolean modifiedAfterSigning,
        List<String> messages) {
}
