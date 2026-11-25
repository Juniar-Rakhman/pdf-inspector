package com.interlunium.pdfinspector.dto;

/**
 * Summary of a CMS SignerInfo structure.
 *
 * @param version                 CMS version of the SignerInfo
 * @param signerIdentifierType    how the signer is identified (issuerAndSerialNumber or subjectKeyIdentifier)
 * @param signerIdentifier        the issuer DN + serial, or the subject key identifier hex
 * @param digestAlgorithm         digest algorithm OID
 * @param digestAlgorithmName     friendly digest algorithm name
 * @param signatureAlgorithm      signature algorithm OID
 * @param signatureAlgorithmName  friendly signature algorithm name
 * @param signatureSize           size of the raw signature value in bytes
 * @param signature               hex dump of the raw signature value
 * @param signedAttributeCount    number of signed attributes
 * @param unsignedAttributeCount  number of unsigned attributes
 * @param hasMessageDigest        whether a message-digest signed attribute is present
 * @param messageDigest           hex of the message-digest signed attribute, if present
 * @param hasSigningTime          whether a signing-time signed attribute is present
 * @param signingTime             decoded signing time, if present
 */
public record SignerInfoDto(
        int version,
        String signerIdentifierType,
        String signerIdentifier,
        String digestAlgorithm,
        String digestAlgorithmName,
        String signatureAlgorithm,
        String signatureAlgorithmName,
        long signatureSize,
        String signature,
        int signedAttributeCount,
        int unsignedAttributeCount,
        boolean hasMessageDigest,
        String messageDigest,
        boolean hasSigningTime,
        String signingTime) {
}
