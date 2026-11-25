package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * A single digital signature detected in a PDF document.
 *
 * @param index                 zero based index of the signature within the document
 * @param fieldName             signature field (form field) name
 * @param page                  one based page number the signature is rendered on, if known
 * @param rectangle             signature widget rectangle on the page, if known
 * @param signerName            name of the signer (PDF /Name entry)
 * @param reason                claimed signing reason (PDF /Reason entry)
 * @param location              claimed signing location (PDF /Location entry)
 * @param contactInfo           claimed contact information (PDF /ContactInfo entry)
 * @param signingTime           claimed signing time (PDF /M entry), ISO-8601 or null
 * @param revision              incremental revision this signature belongs to (1 based)
 * @param filter                signature handler filter (e.g. Adobe.PPKLite)
 * @param subFilter             signature sub-filter (e.g. adbe.pkcs7.detached, ETSI.CAdES.detached)
 * @param byteRange             the /ByteRange array of the signature dictionary
 * @param byteRangeContentLength total bytes covered by the signature byte range
 * @param coversWholeDocument   true when the byte range extends to the very end of the file
 * @param contentsSize          size in bytes of the embedded CMS signature blob
 */
public record SignatureDto(
        int index,
        String fieldName,
        Integer page,
        RectangleDto rectangle,
        String signerName,
        String reason,
        String location,
        String contactInfo,
        String signingTime,
        Integer revision,
        String filter,
        String subFilter,
        List<Long> byteRange,
        long byteRangeContentLength,
        boolean coversWholeDocument,
        long contentsSize) {
}
