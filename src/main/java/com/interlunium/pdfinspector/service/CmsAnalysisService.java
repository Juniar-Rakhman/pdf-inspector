package com.interlunium.pdfinspector.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERUTCTime;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.util.Store;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.interlunium.pdfinspector.dto.AttributeDto;
import com.interlunium.pdfinspector.dto.AttributeValueDto;
import com.interlunium.pdfinspector.dto.CertificateDto;
import com.interlunium.pdfinspector.dto.CmsDto;
import com.interlunium.pdfinspector.dto.ContentInfoDto;
import com.interlunium.pdfinspector.dto.SignerInfoDto;

/**
 * Parses and exposes the structure of a CMS (PKCS#7) SignedData blob extracted from a PDF signature.
 */
@ApplicationScoped
public class CmsAnalysisService {

    private static final String OID_MESSAGE_DIGEST = "1.2.840.113549.1.9.4";
    private static final String OID_SIGNING_TIME = "1.2.840.113549.1.9.5";

    private final OidRegistry oidRegistry;
    private final CertificateAnalysisService certificateAnalysisService;

    /**
     * @param oidRegistry                  OID name registry
     * @param certificateAnalysisService   certificate analyzer
     */
    @Inject
    public CmsAnalysisService(OidRegistry oidRegistry, CertificateAnalysisService certificateAnalysisService) {
        this.oidRegistry = oidRegistry;
        this.certificateAnalysisService = certificateAnalysisService;
    }

    /**
     * Parses raw CMS bytes into a SignedData structure.
     *
     * @param contents the DER encoded CMS bytes
     * @return the parsed SignedData
     */
    public CMSSignedData parse(byte[] contents) {
        try {
            return new CMSSignedData(contents);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse CMS SignedData", e);
        }
    }

    /**
     * Fully analyzes a CMS blob.
     *
     * @param contents     the DER encoded CMS bytes
     * @param signerIndex  which SignerInfo to use for the "selected signer" attributes
     * @return the analysis result
     */
    public CmsDto analyze(byte[] contents, int signerIndex) {
        CMSSignedData signedData = parse(contents);

        SignerInformationStore store = signedData.getSignerInfos();
        List<SignerInformation> signers = new ArrayList<>(store.getSigners());
        SignerInformation selected = signers.isEmpty() ? null : signers.get(Math.min(signerIndex, signers.size() - 1));

        List<SignerInfoDto> signerInfos = signers.stream().map(this::signerInfo).toList();

        List<java.security.cert.X509Certificate> rawCertificates = certificates(signedData);
        List<CertificateDto> certificateDtos = new ArrayList<>(rawCertificates.size());
        for (int i = 0; i < rawCertificates.size(); i++) {
            certificateDtos.add(certificateAnalysisService.analyze(i, rawCertificates.get(i)));
        }

        List<AttributeDto> signedAttributes = selected == null ? List.of() : attributes(selected.getSignedAttributes());
        List<AttributeDto> unsignedAttributes = selected == null ? List.of() : attributes(selected.getUnsignedAttributes());

        return new CmsDto(
                signedData.getVersion(),
                signedData.getSignedContentTypeOID(),
                oidRegistry.name(signedData.getSignedContentTypeOID()),
                signedData.getDigestAlgorithmIDs().stream().map(a -> a.getAlgorithm().getId()).toList(),
                signerInfos,
                signedAttributes,
                unsignedAttributes,
                certificateDtos,
                encapsulatedContent(signedData),
                contents.length);
    }

    /**
     * Converts all certificates embedded in the CMS to Java certificates.
     *
     * @param signedData the SignedData
     * @return a list of certificates (may be empty)
     */
    public List<java.security.cert.X509Certificate> certificates(CMSSignedData signedData) {
        List<java.security.cert.X509Certificate> result = new ArrayList<>();
        try {
            Store<X509CertificateHolder> store = signedData.getCertificates();
            for (X509CertificateHolder holder : store.getMatches(null)) {
                result.add(new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read embedded certificates", e);
        }
        return result;
    }

    /**
     * Locates the certificate matching a given SignerInfo's signer identifier.
     *
     * @param signedData the SignedData
     * @param signer     the SignerInfo
     * @return the matching certificate or null
     */
    public java.security.cert.X509Certificate signerCertificate(CMSSignedData signedData, SignerInformation signer) {
        try {
            Store<X509CertificateHolder> store = signedData.getCertificates();
            Collection<X509CertificateHolder> matches = store.getMatches(signer.getSID());
            if (matches.isEmpty()) {
                return null;
            }
            return new JcaX509CertificateConverter().setProvider("BC")
                    .getCertificate(matches.iterator().next());
        } catch (Exception e) {
            return null;
        }
    }

    private SignerInfoDto signerInfo(SignerInformation signer) {
        SignerIdLike sid = describeSid(signer);
        byte[] digestAttr = attributeBytes(signer.getSignedAttributes(), OID_MESSAGE_DIGEST);
        Time signingTime = attributeTime(signer.getSignedAttributes(), OID_SIGNING_TIME);

        return new SignerInfoDto(
                signer.getVersion(),
                sid.type(),
                sid.display(),
                signer.getDigestAlgOID(),
                oidRegistry.name(signer.getDigestAlgOID()),
                signer.getEncryptionAlgOID(),
                oidRegistry.name(signer.getEncryptionAlgOID()),
                signer.getSignature().length,
                hex(signer.getSignature()),
                signedAttributeCount(signer),
                unsignedAttributeCount(signer),
                digestAttr != null,
                digestAttr == null ? null : hex(digestAttr),
                signingTime != null,
                signingTime == null ? null : signingTime.getDate().toInstant().toString());
    }

    private int signedAttributeCount(SignerInformation signer) {
        AttributeTable table = signer.getSignedAttributes();
        return table == null ? 0 : table.size();
    }

    private int unsignedAttributeCount(SignerInformation signer) {
        AttributeTable table = signer.getUnsignedAttributes();
        return table == null ? 0 : table.size();
    }

    private SignerIdLike describeSid(SignerInformation signer) {
        var sid = signer.getSID();
        byte[] skid = sid.getSubjectKeyIdentifier();
        if (skid != null) {
            return new SignerIdLike("subjectKeyIdentifier", hex(skid));
        }
        StringBuilder display = new StringBuilder();
        if (sid.getIssuer() != null) {
            display.append(sid.getIssuer().toString());
        }
        if (sid.getSerialNumber() != null) {
            display.append(" serial 0x").append(sid.getSerialNumber().toString(16).toUpperCase());
        }
        return new SignerIdLike("issuerAndSerialNumber", display.toString());
    }

    private byte[] attributeBytes(AttributeTable table, String oid) {
        if (table == null) {
            return null;
        }
        Attribute attribute = table.get(new ASN1ObjectIdentifier(oid));
        if (attribute == null || attribute.getAttrValues().size() == 0) {
            return null;
        }
        ASN1Encodable value = attribute.getAttrValues().getObjectAt(0);
        if (value instanceof ASN1OctetString octetString) {
            return octetString.getOctets();
        }
        return null;
    }

    private Time attributeTime(AttributeTable table, String oid) {
        if (table == null) {
            return null;
        }
        Attribute attribute = table.get(new ASN1ObjectIdentifier(oid));
        if (attribute == null || attribute.getAttrValues().size() == 0) {
            return null;
        }
        try {
            return Time.getInstance(attribute.getAttrValues().getObjectAt(0));
        } catch (Exception e) {
            return null;
        }
    }

    private List<AttributeDto> attributes(AttributeTable table) {
        if (table == null) {
            return List.of();
        }
        List<AttributeDto> result = new ArrayList<>();
        ASN1EncodableVector vector = table.toASN1EncodableVector();
        for (int i = 0; i < vector.size(); i++) {
            Attribute attribute = Attribute.getInstance(vector.get(i));
            String oid = attribute.getAttrType().getId();
            List<AttributeValueDto> values = new ArrayList<>();
            ASN1Set attrValues = attribute.getAttrValues();
            for (int j = 0; j < attrValues.size(); j++) {
                values.add(describeValue(attrValues.getObjectAt(j)));
            }
            result.add(new AttributeDto(oid, oidRegistry.name(oid), values));
        }
        return result;
    }

    private AttributeValueDto describeValue(ASN1Encodable value) {
        ASN1Primitive primitive = value.toASN1Primitive();
        String type = primitive.getClass().getSimpleName();
        String display;
        String hexValue = null;

        if (primitive instanceof ASN1ObjectIdentifier oid) {
            String name = oidRegistry.name(oid.getId());
            display = name.equals(oid.getId()) ? oid.getId() : oid.getId() + " (" + name + ")";
        } else if (primitive instanceof ASN1OctetString octetString) {
            display = "octet string (" + octetString.getOctets().length + " bytes)";
            hexValue = hex(octetString.getOctets());
        } else if (primitive instanceof DERUTCTime || primitive instanceof DERGeneralizedTime) {
            try {
                display = Time.getInstance(primitive).getDate().toInstant().toString();
            } catch (Exception e) {
                display = primitive.toString();
            }
        } else {
            display = primitive.toString();
            try {
                byte[] encoded = primitive.getEncoded();
                hexValue = hex(encoded);
            } catch (Exception e) {
                // ignore
            }
        }
        return new AttributeValueDto(type, display, hexValue);
    }

    private ContentInfoDto encapsulatedContent(CMSSignedData signedData) {
        CMSProcessable content = signedData.getSignedContent();
        if (content instanceof CMSProcessableByteArray byteArray) {
            byte[] data = (byte[]) byteArray.getContent();
            int prefix = Math.min(data.length, 64);
            StringBuilder hex = new StringBuilder(prefix * 2);
            for (int i = 0; i < prefix; i++) {
                hex.append(String.format("%02X", data[i]));
            }
            return new ContentInfoDto(signedData.getSignedContentTypeOID(),
                    oidRegistry.name(signedData.getSignedContentTypeOID()),
                    (long) data.length, hex.toString());
        }
        return new ContentInfoDto(signedData.getSignedContentTypeOID(),
                oidRegistry.name(signedData.getSignedContentTypeOID()), null, null);
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

    private record SignerIdLike(String type, String display) {
    }
}
