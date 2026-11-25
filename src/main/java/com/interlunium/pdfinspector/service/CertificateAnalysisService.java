package com.interlunium.pdfinspector.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.interlunium.pdfinspector.dto.BasicConstraintsDto;
import com.interlunium.pdfinspector.dto.CertificateDto;

/**
 * Decomposes a single X.509 certificate into a flat, developer friendly
 * {@link CertificateDto} with every field the UI needs to display.
 */
@ApplicationScoped
public class CertificateAnalysisService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private static final String OID_SKI = "2.5.29.14";
    private static final String OID_AKI = "2.5.29.35";
    private static final String OID_BASIC_CONSTRAINTS = "2.5.29.19";
    private static final String OID_CRL_DP = "2.5.29.31";
    private static final String OID_AIA = "1.3.6.1.5.5.7.1.1";
    private static final String OID_CERT_POLICIES = "2.5.29.32";

    private final OidRegistry oidRegistry;

    /**
     * @param oidRegistry OID name registry
     */
    @Inject
    public CertificateAnalysisService(OidRegistry oidRegistry) {
        this.oidRegistry = oidRegistry;
    }

    /**
     * Analyzes a certificate.
     *
     * @param index zero based index within the certificate collection
     * @param cert  the certificate
     * @return the analyzed certificate
     */
    public CertificateDto analyze(int index, X509Certificate cert) {
        String subject = rfc2253(cert.getSubjectX500Principal().getName());
        String issuer = rfc2253(cert.getIssuerX500Principal().getName());
        boolean selfSigned = subject.equalsIgnoreCase(issuer);

        DnComponents dn = extractDn(cert.getSubjectX500Principal().getName());

        List<String> san = subjectAlternativeNames(cert);
        List<String> keyUsage = keyUsage(cert);
        List<String> eku = extendedKeyUsage(cert);
        List<String> policies = certificatePolicies(cert);
        List<String> crlDps = crlDistributionPoints(cert);
        List<String> aia = authorityInformationAccess(cert);

        String email = extractEmail(san, dn);

        return new CertificateDto(
                index,
                subject,
                issuer,
                serialHex(cert.getSerialNumber()),
                cert.getVersion(),
                cert.getSigAlgName(),
                cert.getPublicKey().getAlgorithm(),
                publicKeySize(cert.getPublicKey()),
                fingerprint(cert, "SHA-1"),
                fingerprint(cert, "SHA-256"),
                toIso(cert.getNotBefore().toInstant()),
                toIso(cert.getNotAfter().toInstant()),
                san,
                keyUsage,
                eku,
                basicConstraints(cert),
                hex(subjectKeyIdentifier(cert)),
                hex(authorityKeyIdentifier(cert)),
                policies,
                crlDps,
                aia,
                email,
                dn.commonName,
                dn.organization,
                dn.organizationalUnit,
                dn.country,
                selfSigned,
                isCa(cert),
                toPem(cert));
    }

    private boolean isCa(X509Certificate cert) {
        return cert.getBasicConstraints() >= 0;
    }

    private BasicConstraintsDto basicConstraints(X509Certificate cert) {
        ASN1Encodable value = extensionValue(cert, OID_BASIC_CONSTRAINTS);
        if (value == null) {
            return null;
        }
        BasicConstraints constraints = BasicConstraints.getInstance(value);
        BigInteger pathLen = constraints.getPathLenConstraint();
        return new BasicConstraintsDto(constraints.isCA(),
                pathLen == null ? null : pathLen.intValue());
    }

    private byte[] subjectKeyIdentifier(X509Certificate cert) {
        ASN1Encodable value = extensionValue(cert, OID_SKI);
        if (value == null) {
            return null;
        }
        return SubjectKeyIdentifier.getInstance(value).getKeyIdentifier();
    }

    private byte[] authorityKeyIdentifier(X509Certificate cert) {
        ASN1Encodable value = extensionValue(cert, OID_AKI);
        if (value == null) {
            return null;
        }
        AuthorityKeyIdentifier aki = AuthorityKeyIdentifier.getInstance(value);
        return aki.getKeyIdentifier();
    }

    private List<String> certificatePolicies(X509Certificate cert) {
        ASN1Encodable value = extensionValue(cert, OID_CERT_POLICIES);
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        PolicyInformation[] policies = CertificatePolicies.getInstance(value).getPolicyInformation();
        for (PolicyInformation policy : policies) {
            String oid = policy.getPolicyIdentifier().getId();
            String name = oidRegistry.name(oid);
            result.add(name.equals(oid) ? oid : oid + " (" + name + ")");
        }
        return result;
    }

    private List<String> crlDistributionPoints(X509Certificate cert) {
        ASN1Encodable value = extensionValue(cert, OID_CRL_DP);
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        DistributionPoint[] points = CRLDistPoint.getInstance(value).getDistributionPoints();
        for (DistributionPoint point : points) {
            if (point.getDistributionPoint() == null
                    || point.getDistributionPoint().getName() == null) {
                continue;
            }
            GeneralNames names = (GeneralNames) point.getDistributionPoint().getName();
            for (GeneralName name : names.getNames()) {
                if (name.getTagNo() == GeneralName.uniformResourceIdentifier) {
                    result.add(name.getName().toString());
                }
            }
        }
        return result;
    }

    private List<String> authorityInformationAccess(X509Certificate cert) {
        ASN1Encodable value = extensionValue(cert, OID_AIA);
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        AccessDescription[] descriptions = AuthorityInformationAccess.getInstance(value)
                .getAccessDescriptions();
        for (AccessDescription description : descriptions) {
            String method = oidRegistry.name(description.getAccessMethod().getId());
            String location = generalName(description.getAccessLocation());
            result.add(method + ": " + location);
        }
        return result;
    }

    private List<String> subjectAlternativeNames(X509Certificate cert) {
        List<String> result = new ArrayList<>();
        try {
            Collection<List<?>> altNames = cert.getSubjectAlternativeNames();
            if (altNames == null) {
                return result;
            }
            for (List<?> altName : altNames) {
                Integer type = (Integer) altName.get(0);
                Object rawValue = altName.get(1);
                String value;
                if (rawValue instanceof byte[] bytes) {
                    value = new String(bytes, StandardCharsets.ISO_8859_1);
                } else {
                    value = String.valueOf(rawValue);
                }
                result.add(switch (type) {
                    case GeneralName.otherName -> "otherName: " + value;
                    case GeneralName.rfc822Name -> "email: " + value;
                    case GeneralName.dNSName -> "dns: " + value;
                    case GeneralName.x400Address -> "x400: " + value;
                    case GeneralName.directoryName -> "directoryName: " + value;
                    case GeneralName.ediPartyName -> "ediPartyName: " + value;
                    case GeneralName.uniformResourceIdentifier -> "uri: " + value;
                    case GeneralName.iPAddress -> "ip: " + value;
                    case GeneralName.registeredID -> "registeredID: " + value;
                    default -> type + ": " + value;
                });
            }
        } catch (Exception e) {
            // SAN parsing is best effort
        }
        return result;
    }

    private List<String> keyUsage(X509Certificate cert) {
        boolean[] usage = cert.getKeyUsage();
        if (usage == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < usage.length && i < 9; i++) {
            if (usage[i]) {
                result.add(oidRegistry.name(Integer.toString(i)));
            }
        }
        return result;
    }

    private List<String> extendedKeyUsage(X509Certificate cert) {
        List<String> result = new ArrayList<>();
        try {
            List<String> oids = cert.getExtendedKeyUsage();
            if (oids == null) {
                return result;
            }
            for (String oid : oids) {
                String name = oidRegistry.name(oid);
                result.add(name.equals(oid) ? oid : oid + " (" + name + ")");
            }
            return result;
        } catch (Exception e) {
            // If the built-in parser fails, fall back to a raw DER parse.
            ASN1Encodable value = extensionValue(cert, "2.5.29.37");
            if (value == null) {
                return result;
            }
            for (var purpose : ExtendedKeyUsage.getInstance(value).getUsages()) {
                String oid = purpose.getId();
                String name = oidRegistry.name(oid);
                result.add(name.equals(oid) ? oid : oid + " (" + name + ")");
            }
            return result;
        }
    }

    /**
     * Parses the DER encoded value of an extension, unwrapping the outer OCTET STRING.
     */
    private ASN1Encodable extensionValue(X509Certificate cert, String oid) {
        try {
            byte[] extension = cert.getExtensionValue(oid);
            if (extension == null) {
                return null;
            }
            byte[] inner = ASN1OctetString.getInstance(extension).getOctets();
            return ASN1Primitive.fromByteArray(inner);
        } catch (Exception e) {
            return null;
        }
    }

    private DnComponents extractDn(String rfc2253) {
        DnComponents components = new DnComponents();
        try {
            X500Name name = new X500Name(rfc2253);
            for (RDN rdn : name.getRDNs()) {
                AttributeTypeAndValue[] typesAndValues = rdn.getTypesAndValues();
                for (AttributeTypeAndValue atav : typesAndValues) {
                    String oid = atav.getType().getId();
                    String value = IETFUtils.valueToString(atav.getValue());
                    switch (oid) {
                        case "2.5.4.3" -> components.commonName = value;
                        case "2.5.4.10" -> components.organization = value;
                        case "2.5.4.11" -> components.organizationalUnit = value;
                        case "2.5.4.6" -> components.country = value;
                        case "1.2.840.113549.1.9.1" -> components.email = value;
                        default -> {
                        }
                    }
                }
            }
        } catch (Exception e) {
            // DN parsing is best effort
        }
        return components;
    }

    private String extractEmail(List<String> san, DnComponents dn) {
        if (dn.email != null) {
            return dn.email;
        }
        for (String entry : san) {
            if (entry.startsWith("email: ")) {
                return entry.substring("email: ".length());
            }
        }
        return null;
    }

    private String generalName(GeneralName name) {
        return switch (name.getTagNo()) {
            case GeneralName.uniformResourceIdentifier -> name.getName().toString();
            case GeneralName.dNSName -> name.getName().toString();
            case GeneralName.rfc822Name -> name.getName().toString();
            case GeneralName.iPAddress -> name.getName().toString();
            case GeneralName.directoryName -> name.getName().toString();
            default -> name.toString();
        };
    }

    private String serialHex(BigInteger serial) {
        String hex = serial.toString(16).toUpperCase(Locale.ROOT);
        return (hex.length() % 2 == 0 ? hex : "0" + hex);
    }

    private String fingerprint(X509Certificate cert, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return hex(digest.digest(cert.getEncoded()));
        } catch (Exception e) {
            return null;
        }
    }

    private int publicKeySize(PublicKey key) {
        if (key instanceof RSAPublicKey rsa) {
            return rsa.getModulus().bitLength();
        }
        if (key instanceof DSAPublicKey dsa) {
            return dsa.getParams().getP().bitLength();
        }
        if (key instanceof ECPublicKey ec) {
            return ec.getParams().getCurve().getField().getFieldSize();
        }
        String algorithm = key.getAlgorithm();
        if ("Ed25519".equalsIgnoreCase(algorithm)) {
            return 256;
        }
        if ("Ed448".equalsIgnoreCase(algorithm)) {
            return 448;
        }
        if ("X25519".equalsIgnoreCase(algorithm)) {
            return 256;
        }
        if ("X448".equalsIgnoreCase(algorithm)) {
            return 448;
        }
        return -1;
    }

    private String toPem(X509Certificate cert) {
        try {
            String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(cert.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----\n";
        } catch (Exception e) {
            return null;
        }
    }

    private String rfc2253(String principalName) {
        return principalName != null ? principalName : "";
    }

    private String toIso(Instant instant) {
        return instant == null ? null : ISO.format(instant);
    }

    private String hex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Simple carrier for the most useful DN attributes.
     */
    private static final class DnComponents {
        private String commonName;
        private String organization;
        private String organizationalUnit;
        private String country;
        private String email;
    }
}
