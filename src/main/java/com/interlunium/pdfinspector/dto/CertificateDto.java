package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * A fully analyzed X.509 certificate that was embedded in (or referenced by) a signature.
 *
 * @param index                     zero based index within the certificate collection
 * @param subject                   RFC 2253 subject distinguished name
 * @param issuer                    RFC 2253 issuer distinguished name
 * @param serialNumber              hex encoded serial number
 * @param version                   X.509 version (1, 2 or 3)
 * @param signatureAlgorithm        certificate signature algorithm name
 * @param publicKeyAlgorithm        public key algorithm name
 * @param publicKeySize             public key size in bits
 * @param sha1Fingerprint           hex encoded SHA-1 fingerprint
 * @param sha256Fingerprint         hex encoded SHA-256 fingerprint
 * @param validFrom                 not-before date (ISO-8601)
 * @param validUntil                not-after date (ISO-8601)
 * @param subjectAlternativeNames   human readable SAN entries (may be empty)
 * @param keyUsage                  human readable key usage flags (may be empty)
 * @param extendedKeyUsage          human readable extended key usage OIDs (may be empty)
 * @param basicConstraints          basic constraints extension, if present
 * @param subjectKeyIdentifier      hex encoded subject key identifier, if present
 * @param authorityKeyIdentifier    hex encoded authority key identifier, if present
 * @param certificatePolicies       human readable certificate policy OIDs (may be empty)
 * @param crlDistributionPoints     CRL distribution point URLs (may be empty)
 * @param authorityInformationAccess AIA entries as "type: location" strings (may be empty)
 * @param email                     email address derived from SAN / subject, if present
 * @param commonName                CN attribute, if present
 * @param organization              O attribute, if present
 * @param organizationalUnit        OU attribute, if present
 * @param country                   C attribute, if present
 * @param selfSigned                whether subject equals issuer
 * @param isCa                      whether basic constraints mark the certificate as a CA
 * @param pem                       PEM encoded certificate (DER wrapped in base64)
 */
public record CertificateDto(
        int index,
        String subject,
        String issuer,
        String serialNumber,
        int version,
        String signatureAlgorithm,
        String publicKeyAlgorithm,
        int publicKeySize,
        String sha1Fingerprint,
        String sha256Fingerprint,
        String validFrom,
        String validUntil,
        List<String> subjectAlternativeNames,
        List<String> keyUsage,
        List<String> extendedKeyUsage,
        BasicConstraintsDto basicConstraints,
        String subjectKeyIdentifier,
        String authorityKeyIdentifier,
        List<String> certificatePolicies,
        List<String> crlDistributionPoints,
        List<String> authorityInformationAccess,
        String email,
        String commonName,
        String organization,
        String organizationalUnit,
        String country,
        boolean selfSigned,
        boolean isCa,
        String pem) {
}
