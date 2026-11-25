package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * Complete validation report for a document.
 *
 * @param signatures   per signature integrity + document integrity results
 * @param certificates per certificate validity results
 * @param chains       per signature chain completeness results
 * @param trust        per signature trust results
 * @param summary      a short human readable summary
 * @param messages     global messages
 */
public record ValidationDto(
        List<SignatureValidationDto> signatures,
        List<CertificateStatusDto> certificates,
        List<ChainStatusDto> chains,
        List<TrustStatusDto> trust,
        String summary,
        List<String> messages) {
}
