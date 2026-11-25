package com.interlunium.pdfinspector.dto;

/**
 * A single node in a visualized certificate chain.
 *
 * @param subject             subject distinguished name
 * @param issuer              issuer distinguished name
 * @param sha256Fingerprint   hex encoded SHA-256 fingerprint
 * @param type                ROOT, INTERMEDIATE or SIGNER
 * @param selfSigned          whether the certificate is self-signed
 * @param isCa                whether the certificate is a certification authority
 * @param hasIssuerInDocument whether the issuer certificate is embedded in the document
 * @param certificateIndex    index of the certificate within the certificate collection, if embedded
 * @param depth               distance from the signer (0 for the signer)
 */
public record ChainNodeDto(
        String subject,
        String issuer,
        String sha256Fingerprint,
        String type,
        boolean selfSigned,
        boolean isCa,
        boolean hasIssuerInDocument,
        Integer certificateIndex,
        int depth) {
}
