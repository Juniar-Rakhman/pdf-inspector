package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * Validation status of a single certificate.
 *
 * @param index        zero based index within the certificate collection
 * @param subject      certificate subject
 * @param status       VALID, EXPIRED, NOT_YET_VALID or UNKNOWN
 * @param validFrom    not-before date (ISO-8601)
 * @param validUntil   not-after date (ISO-8601)
 * @param checkTime    the reference time the validity period was checked against (ISO-8601)
 * @param messages     explanatory messages
 */
public record CertificateStatusDto(
        int index,
        String subject,
        String status,
        String validFrom,
        String validUntil,
        String checkTime,
        List<String> messages) {
}
