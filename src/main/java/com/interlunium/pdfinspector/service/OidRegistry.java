package com.interlunium.pdfinspector.service;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Registry mapping well-known object identifiers (OIDs) to human readable names.
 *
 * <p>Used for CMS attributes, signature algorithms, digest algorithms, X.509 extensions,
 * extended key usages and distinguished name attribute types.</p>
 */
@ApplicationScoped
public class OidRegistry {

    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        // --- PKCS#9 attributes -------------------------------------------------
        NAMES.put("1.2.840.113549.1.9.1", "emailAddress");
        NAMES.put("1.2.840.113549.1.9.3", "contentType");
        NAMES.put("1.2.840.113549.1.9.4", "messageDigest");
        NAMES.put("1.2.840.113549.1.9.5", "signingTime");
        NAMES.put("1.2.840.113549.1.9.6", "counterSignature");
        NAMES.put("1.2.840.113549.1.9.7", "challengePassword");
        NAMES.put("1.2.840.113549.1.9.14", "extensionRequest");
        NAMES.put("1.2.840.113549.1.9.15", "sMIMECapabilities");
        NAMES.put("1.2.840.113549.1.9.16.2.4", "signatureTimeStampToken");
        NAMES.put("1.2.840.113549.1.9.16.2.12", "signerAttributes");
        NAMES.put("1.2.840.113549.1.9.16.2.14", "contentTimeStamp");
        NAMES.put("1.2.840.113549.1.9.16.2.17", "cmsAlgorithmProtection");
        NAMES.put("1.2.840.113549.1.9.16.2.20", "signaturePolicyIdentifier");
        NAMES.put("1.2.840.113549.1.9.16.2.47", "signingCertificateV2");
        NAMES.put("1.2.840.113549.1.9.16.2.12.1", "otherRevocationInfoFormat");
        NAMES.put("1.2.840.113549.1.9.16.6.1", "etsiSignedData");
        NAMES.put("1.2.840.113549.1.9.16.2.11", "id_aa_ets_signerAttr");
        NAMES.put("1.2.840.113549.1.9.16.2.15", "id_aa_ets_signerLocation");
        NAMES.put("1.2.840.113549.1.9.16.2.16", "id_aa_ets_mimeType");
        NAMES.put("1.2.840.113549.1.9.16.2.18", "id_aa_ets_signingCertificate");

        // --- CMS content types ---------------------------------------------------
        NAMES.put("1.2.840.113549.1.7.1", "data");
        NAMES.put("1.2.840.113549.1.7.2", "signedData");
        NAMES.put("1.2.840.113549.1.7.3", "envelopedData");
        NAMES.put("1.2.840.113549.1.7.6", "encryptedData");
        NAMES.put("1.2.840.113549.1.7.11", "otherCertificateFormats");
        NAMES.put("1.2.840.113549.1.7.4", "digestedData");
        NAMES.put("1.2.840.113549.1.7.5", "authenticatedData");

        // --- Digest algorithms ---------------------------------------------------
        NAMES.put("1.2.840.113549.2.2", "MD2");
        NAMES.put("1.2.840.113549.2.5", "MD5");
        NAMES.put("1.3.14.3.2.26", "SHA-1");
        NAMES.put("2.16.840.1.101.3.4.2.1", "SHA-256");
        NAMES.put("2.16.840.1.101.3.4.2.2", "SHA-384");
        NAMES.put("2.16.840.1.101.3.4.2.3", "SHA-512");
        NAMES.put("2.16.840.1.101.3.4.2.4", "SHA-224");
        NAMES.put("2.16.840.1.101.3.4.2.6", "SHA-512/224");
        NAMES.put("2.16.840.1.101.3.4.2.7", "SHA-512/256");
        NAMES.put("2.16.840.1.101.3.4.2.8", "SHA3-224");
        NAMES.put("2.16.840.1.101.3.4.2.9", "SHA3-256");
        NAMES.put("2.16.840.1.101.3.4.2.10", "SHA3-384");
        NAMES.put("2.16.840.1.101.3.4.2.11", "SHA3-512");

        // --- Signature algorithms ------------------------------------------------
        NAMES.put("1.2.840.113549.1.1.1", "RSA encryption");
        NAMES.put("1.2.840.113549.1.1.5", "SHA-1 with RSA");
        NAMES.put("1.2.840.113549.1.1.4", "MD5 with RSA");
        NAMES.put("1.2.840.113549.1.1.10", "RSASSA-PSS");
        NAMES.put("1.2.840.113549.1.1.11", "SHA-256 with RSA");
        NAMES.put("1.2.840.113549.1.1.12", "SHA-384 with RSA");
        NAMES.put("1.2.840.113549.1.1.13", "SHA-512 with RSA");
        NAMES.put("1.2.840.113549.1.1.14", "SHA-224 with RSA");
        NAMES.put("1.2.840.113549.1.1.2", "MD2 with RSA");
        NAMES.put("1.2.840.113549.1.1.7", "RSA-OAEP");
        NAMES.put("1.2.840.10045.4.1", "ECDSA with SHA-1");
        NAMES.put("1.2.840.10045.4.3.2", "ECDSA with SHA-256");
        NAMES.put("1.2.840.10045.4.3.3", "ECDSA with SHA-384");
        NAMES.put("1.2.840.10045.4.3.4", "ECDSA with SHA-512");
        NAMES.put("1.2.840.10045.4.3.1", "ECDSA with SHA-224");
        NAMES.put("1.2.840.10040.4.3", "DSA with SHA-1");
        NAMES.put("2.16.840.1.101.3.4.3.2", "DSA with SHA-256");
        NAMES.put("1.3.101.112", "Ed25519");
        NAMES.put("1.3.101.113", "Ed448");

        // --- Public key algorithms -----------------------------------------------
        NAMES.put("1.2.840.113549.1.1.1", "RSA");
        NAMES.put("1.2.840.10045.2.1", "EC");
        NAMES.put("1.2.840.10040.4.1", "DSA");
        NAMES.put("1.3.101.112", "Ed25519");
        NAMES.put("1.3.101.113", "Ed448");

        // --- X.509 extensions -----------------------------------------------------
        NAMES.put("2.5.29.9", "subjectDirectoryAttributes");
        NAMES.put("2.5.29.14", "subjectKeyIdentifier");
        NAMES.put("2.5.29.15", "keyUsage");
        NAMES.put("2.5.29.16", "privateKeyUsagePeriod");
        NAMES.put("2.5.29.17", "subjectAlternativeName");
        NAMES.put("2.5.29.18", "issuerAlternativeName");
        NAMES.put("2.5.29.19", "basicConstraints");
        NAMES.put("2.5.29.30", "nameConstraints");
        NAMES.put("2.5.29.31", "crlDistributionPoints");
        NAMES.put("2.5.29.32", "certificatePolicies");
        NAMES.put("2.5.29.33", "policyMappings");
        NAMES.put("2.5.29.35", "authorityKeyIdentifier");
        NAMES.put("2.5.29.36", "policyConstraints");
        NAMES.put("2.5.29.37", "extendedKeyUsage");
        NAMES.put("2.5.29.46", "freshestCRL");
        NAMES.put("2.5.29.54", "inhibitAnyPolicy");
        NAMES.put("1.3.6.1.5.5.7.1.1", "authorityInformationAccess");
        NAMES.put("1.3.6.1.5.5.7.1.11", "subjectInformationAccess");
        NAMES.put("1.3.6.1.5.5.7.1.3", "qCStatements");

        // --- Extended key usages --------------------------------------------------
        NAMES.put("2.5.29.37.0", "anyExtendedKeyUsage");
        NAMES.put("1.3.6.1.5.5.7.3.1", "serverAuth");
        NAMES.put("1.3.6.1.5.5.7.3.2", "clientAuth");
        NAMES.put("1.3.6.1.5.5.7.3.3", "codeSigning");
        NAMES.put("1.3.6.1.5.5.7.3.4", "emailProtection");
        NAMES.put("1.3.6.1.5.5.7.3.8", "timeStamping");
        NAMES.put("1.3.6.1.5.5.7.3.9", "ocspSigning");
        NAMES.put("1.3.6.1.5.5.7.3.36", "documentSigning");
        NAMES.put("1.3.6.1.4.1.311.10.3.12", "documentSigning (Microsoft)");
        NAMES.put("1.3.6.1.4.1.311.2.1.21", "individualCodeSigning");
        NAMES.put("1.3.6.1.4.1.311.2.1.22", "commercialCodeSigning");

        // --- DN attribute types ----------------------------------------------------
        NAMES.put("2.5.4.3", "commonName");
        NAMES.put("2.5.4.4", "surname");
        NAMES.put("2.5.4.5", "serialNumber");
        NAMES.put("2.5.4.6", "countryName");
        NAMES.put("2.5.4.7", "localityName");
        NAMES.put("2.5.4.8", "stateOrProvinceName");
        NAMES.put("2.5.4.9", "streetAddress");
        NAMES.put("2.5.4.10", "organizationName");
        NAMES.put("2.5.4.11", "organizationalUnitName");
        NAMES.put("2.5.4.12", "title");
        NAMES.put("2.5.4.13", "description");
        NAMES.put("2.5.4.15", "businessCategory");
        NAMES.put("2.5.4.17", "postalCode");
        NAMES.put("2.5.4.20", "telephoneNumber");
        NAMES.put("2.5.4.42", "givenName");
        NAMES.put("2.5.4.46", "dnQualifier");
        NAMES.put("2.5.4.65", "pseudonym");
        NAMES.put("1.2.840.113549.1.9.1", "emailAddress");
        NAMES.put("0.9.2342.19200300.100.1.25", "domainComponent");

        // --- PKCS#7 signing certificate ------------------------------------------
        NAMES.put("1.3.6.1.5.5.7.4.1", "signingCertificate");
        NAMES.put("1.2.840.113549.1.9.16.2.47", "signingCertificateV2");

        // --- ETSI policies ---------------------------------------------------------
        NAMES.put("0.4.0.194112.1.1", "ETSI qcs-oid");
        NAMES.put("0.4.0.194112.1.2", "ETSI CAdES");
        NAMES.put("0.4.0.194112.1.3", "ETSI policy-based (CAdES)");
        NAMES.put("0.4.0.194112.1.4", "ETSI archive (CAdES)");
        NAMES.put("0.4.0.194121.1.1", "ESIG Policy");

        // --- Key usage bits ---------------------------------------------------------
        NAMES.put("0", "digitalSignature");
        NAMES.put("1", "nonRepudiation");
        NAMES.put("2", "keyEncipherment");
        NAMES.put("3", "dataEncipherment");
        NAMES.put("4", "keyAgreement");
        NAMES.put("5", "keyCertSign");
        NAMES.put("6", "cRLSign");
        NAMES.put("7", "encipherOnly");
        NAMES.put("8", "decipherOnly");
    }

    /**
     * @param oid the object identifier
     * @return a friendly name, or {@code oid} itself when unknown
     */
    public String name(String oid) {
        String name = NAMES.get(oid);
        return name != null ? name : oid;
    }
}
