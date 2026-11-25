package com.interlunium.pdfinspector.service;

import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSignerInfoVerifierBuilder;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.interlunium.pdfinspector.dto.CertificateStatusDto;
import com.interlunium.pdfinspector.dto.ChainStatusDto;
import com.interlunium.pdfinspector.dto.SignatureValidationDto;
import com.interlunium.pdfinspector.dto.TrustStatusDto;
import com.interlunium.pdfinspector.dto.ValidationDto;
import com.interlunium.pdfinspector.service.SignatureExtractionService.ExtractedSignature;

/**
 * Validates every signature of a PDF and produces a multi-faceted report covering
 * signature integrity, document integrity, certificate validity, chain completeness
 * and trust. The different facets are intentionally reported independently rather
 * than collapsed into a single boolean.
 */
@ApplicationScoped
public class SignatureValidationService {

    private static final String STATUS_VALID = "VALID";
    private static final String STATUS_INVALID = "INVALID";
    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_NOT_YET_VALID = "NOT_YET_VALID";
    private static final String STATUS_TRUSTED = "TRUSTED";

    private final StorageService storage;
    private final SignatureExtractionService signatureExtractionService;
    private final CmsAnalysisService cmsAnalysisService;
    private final CertificateChainService certificateChainService;

    /**
     * @param storage                    file storage
     * @param signatureExtractionService signature extraction
     * @param cmsAnalysisService         CMS analysis
     * @param certificateChainService    chain building
     */
    @Inject
    public SignatureValidationService(StorageService storage,
            SignatureExtractionService signatureExtractionService,
            CmsAnalysisService cmsAnalysisService,
            CertificateChainService certificateChainService) {
        this.storage = storage;
        this.signatureExtractionService = signatureExtractionService;
        this.cmsAnalysisService = cmsAnalysisService;
        this.certificateChainService = certificateChainService;
    }

    /**
     * Validates the whole document.
     *
     * @param documentId document id
     * @return the validation report
     */
    public ValidationDto validate(String documentId) {
        byte[] file;
        try {
            file = storage.read(documentId);
        } catch (IOException e) {
            throw new NotFoundException("Document " + documentId + " not found");
        }
        List<String> globalMessages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(file)) {
            List<ExtractedSignature> extracted = signatureExtractionService.extract(document);

            Set<X509Certificate> allCertificates = new LinkedHashSet<>();
            for (ExtractedSignature signature : extracted) {
                if (signature.contents().length == 0) {
                    continue;
                }
                try {
                    allCertificates.addAll(cmsAnalysisService.certificates(cmsAnalysisService.parse(signature.contents())));
                } catch (RuntimeException e) {
                    globalMessages.add("CMS of signature #" + signature.index() + " could not be parsed: " + e.getMessage());
                }
            }
            List<X509Certificate> certificateList = new ArrayList<>(allCertificates);
            Instant referenceTime = referenceTime(extracted);

            List<SignatureValidationDto> signatureResults = new ArrayList<>();
            for (ExtractedSignature signature : extracted) {
                signatureResults.add(validateSignature(signature, file, certificateList, referenceTime));
            }

            List<CertificateStatusDto> certificateResults = validateCertificates(certificateList, referenceTime);
            List<ChainStatusDto> chainResults = buildChains(extracted, certificateList);
            List<TrustStatusDto> trustResults = evaluateTrust(extracted, chainResults);

            if (extracted.isEmpty()) {
                globalMessages.add("The document does not contain any digital signatures.");
            }

            String summary = summarize(signatureResults, extracted.size());
            return new ValidationDto(signatureResults, certificateResults, chainResults, trustResults, summary, globalMessages);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read document " + documentId, e);
        }
    }

    private SignatureValidationDto validateSignature(ExtractedSignature signature, byte[] file,
            List<X509Certificate> certificates, Instant referenceTime) {
        List<String> messages = new ArrayList<>();
        long[] byteRange = signature.byteRange();
        byte[] rangeContent = byteRangeContent(file, byteRange);

        String signatureIntegrity = STATUS_UNKNOWN;
        String documentIntegrity = STATUS_UNKNOWN;
        String signerName = null;

        if (signature.contents().length == 0) {
            messages.add("The signature dictionary contains no CMS payload.");
        } else {
            try {
                CMSSignedData cms;
                if (rangeContent != null) {
                    cms = new CMSSignedData(new CMSProcessableByteArray(rangeContent), signature.contents());
                } else {
                    cms = cmsAnalysisService.parse(signature.contents());
                }
                List<SignerInformation> signers = new ArrayList<>(cms.getSignerInfos().getSigners());
                if (signers.isEmpty()) {
                    messages.add("The CMS contains no SignerInfo.");
                } else {
                    SignerInformation signer = signers.get(0);
                    X509Certificate signerCertificate = cmsAnalysisService.signerCertificate(cms, signer);
                    if (signerCertificate == null) {
                        messages.add("No certificate matching the signer identifier was found in the document.");
                    } else {
                        signerName = commonName(signerCertificate);
                        try {
                            DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder()
                                    .setProvider("BC").build();
                            SignerInformationVerifier verifier = new JcaSignerInfoVerifierBuilder(digestProvider)
                                    .setProvider("BC")
                                    .build(signerCertificate.getPublicKey());
                            boolean verifies = signer.verify(verifier);
                            signatureIntegrity = verifies ? STATUS_VALID : STATUS_INVALID;
                            messages.add(signatureIntegrity.equals(STATUS_VALID)
                                    ? "The CMS signature mathematically verifies against the signer certificate."
                                    : "Signature verification FAILED: the signature value does not verify with the signer certificate.");
                        } catch (Exception e) {
                            messages.add("Signature verification error: " + e.getMessage());
                        }
                    }

                    Boolean docIntegrity = checkDocumentIntegrity(rangeContent, signer);
                    if (docIntegrity == null) {
                        documentIntegrity = STATUS_UNKNOWN;
                        messages.add("Document integrity cannot be checked: no message-digest signed attribute is present.");
                    } else if (docIntegrity) {
                        documentIntegrity = STATUS_VALID;
                        messages.add("Document integrity OK: the /ByteRange content digest matches the message-digest attribute.");
                    } else {
                        documentIntegrity = STATUS_INVALID;
                        messages.add("Document integrity FAILED: the /ByteRange content digest does not match the message-digest attribute. The signed content was modified after signing.");
                    }
                }
            } catch (Exception e) {
                messages.add("CMS parsing error: " + e.getMessage());
            }
        }

        boolean coversWhole = byteRange.length >= 4 && (byteRange[2] + byteRange[3]) >= file.length - 2;
        boolean modifiedAfter = byteRange.length >= 4 && (byteRange[2] + byteRange[3]) < file.length - 2;
        if (modifiedAfter) {
            messages.add("The signature byte range does not cover the whole current file; the document was modified after this signature was applied.");
        }

        return new SignatureValidationDto(
                signature.index(),
                signerName,
                signatureIntegrity,
                documentIntegrity,
                coversWhole,
                modifiedAfter,
                messages);
    }

    private Boolean checkDocumentIntegrity(byte[] rangeContent, SignerInformation signer) {
        if (rangeContent == null) {
            return false;
        }
        try {
            AttributeTable attributes = signer.getSignedAttributes();
            if (attributes == null) {
                return null;
            }
            Attribute messageDigest = attributes.get(PKCSObjectIdentifiers.pkcs_9_at_messageDigest);
            if (messageDigest == null) {
                return null;
            }
            DEROctetString expected = (DEROctetString) messageDigest.getAttrValues().getObjectAt(0);
            AlgorithmIdentifier digestAlgorithm = new AlgorithmIdentifier(
                    new ASN1ObjectIdentifier(signer.getDigestAlgOID()));
            JcaDigestCalculatorProviderBuilder providerBuilder = new JcaDigestCalculatorProviderBuilder()
                    .setProvider("BC");
            DigestCalculator calculator = providerBuilder.build().get(digestAlgorithm);
            try (OutputStream out = calculator.getOutputStream()) {
                out.write(rangeContent);
            }
            return Arrays.equals(calculator.getDigest(), expected.getOctets());
        } catch (Exception e) {
            return false;
        }
    }

    private List<CertificateStatusDto> validateCertificates(List<X509Certificate> certificates, Instant referenceTime) {
        List<CertificateStatusDto> result = new ArrayList<>(certificates.size());
        for (int i = 0; i < certificates.size(); i++) {
            X509Certificate certificate = certificates.get(i);
            String status;
            List<String> messages = new ArrayList<>();
            if (referenceTime.isBefore(certificate.getNotBefore().toInstant())) {
                status = STATUS_NOT_YET_VALID;
                messages.add("The certificate is not yet valid (valid from " + certificate.getNotBefore() + ").");
            } else if (referenceTime.isAfter(certificate.getNotAfter().toInstant())) {
                status = STATUS_EXPIRED;
                messages.add("The certificate expired on " + certificate.getNotAfter() + ".");
            } else {
                status = STATUS_VALID;
                messages.add("The certificate was within its validity period at the reference time.");
            }
            result.add(new CertificateStatusDto(i, certificate.getSubjectX500Principal().getName(),
                    status, certificate.getNotBefore().toInstant().toString(),
                    certificate.getNotAfter().toInstant().toString(), referenceTime.toString(), messages));
        }
        return result;
    }

    private List<ChainStatusDto> buildChains(List<ExtractedSignature> extracted, List<X509Certificate> certificates) {
        List<ChainStatusDto> result = new ArrayList<>();
        for (ExtractedSignature signature : extracted) {
            X509Certificate signerCertificate = null;
            if (signature.contents().length > 0) {
            try {
                CMSSignedData cms = cmsAnalysisService.parse(signature.contents());
                List<SignerInformation> signers = new ArrayList<>(cms.getSignerInfos().getSigners());
                    if (!signers.isEmpty()) {
                        signerCertificate = cmsAnalysisService.signerCertificate(cms, signers.get(0));
                    }
                } catch (RuntimeException e) {
                    // chain cannot be built for unparsable CMS
                }
            }
            var chain = certificateChainService.build(signature.index(), certificates, signerCertificate);
            result.add(new ChainStatusDto(signature.index(), chain.complete(), chain.pathLength(), chain.messages()));
        }
        return result;
    }

    private List<TrustStatusDto> evaluateTrust(List<ExtractedSignature> extracted, List<ChainStatusDto> chains) {
        List<TrustStatusDto> result = new ArrayList<>();
        for (int i = 0; i < extracted.size(); i++) {
            List<String> messages = new ArrayList<>();
            String status = STATUS_UNKNOWN;
            if (!chains.get(i).complete()) {
                messages.add("Trust cannot be established: the certificate chain is incomplete.");
            } else {
                X509Certificate[] chain = null;
                // chain completeness was established, trust check against JVM default trust store
                // The full chain is rebuilt from the CMS below.
                if (extracted.get(i).contents().length > 0) {
                    chain = trustChain(extracted.get(i));
                }
                if (chain == null || chain.length == 0) {
                    messages.add("Trust cannot be established: the certificate chain is incomplete.");
                } else {
                    try {
                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        tmf.init((KeyStore) null);
                        X509TrustManager trustManager = (X509TrustManager) tmf.getTrustManagers()[0];
                        trustManager.checkServerTrusted(chain, "RSA");
                        status = STATUS_TRUSTED;
                        messages.add("The certificate chain is trusted: it anchors in the JVM default trust store.");
                    } catch (Exception e) {
                        messages.add("The certificate chain does not anchor in the JVM default trust store: " + e.getMessage());
                    }
                }
            }
            result.add(new TrustStatusDto(i, status, messages));
        }
        return result;
    }

    private X509Certificate[] trustChain(ExtractedSignature signature) {
        try {
            CMSSignedData cms = cmsAnalysisService.parse(signature.contents());
            List<SignerInformation> signers = new ArrayList<>(cms.getSignerInfos().getSigners());
            if (signers.isEmpty()) {
                return new X509Certificate[0];
            }
            List<X509Certificate> certs = cmsAnalysisService.certificates(cms);
            X509Certificate signer = cmsAnalysisService.signerCertificate(cms, signers.get(0));
            if (signer == null) {
                return new X509Certificate[0];
            }
            List<X509Certificate> chain = new ArrayList<>();
            chain.add(signer);
            X509Certificate current = signer;
            while (true) {
                if (current.getIssuerX500Principal().equals(current.getSubjectX500Principal())) {
                    break;
                }
                X509Certificate issuer = findIssuer(certs, current);
                if (issuer == null) {
                    break;
                }
                chain.add(issuer);
                current = issuer;
            }
            return chain.toArray(new X509Certificate[0]);
        } catch (RuntimeException e) {
            return new X509Certificate[0];
        }
    }

    private X509Certificate findIssuer(List<X509Certificate> certs, X509Certificate subject) {
        for (X509Certificate candidate : certs) {
            if (candidate.getSubjectX500Principal().equals(subject.getIssuerX500Principal())) {
                return candidate;
            }
        }
        return null;
    }

    private byte[] byteRangeContent(byte[] file, long[] byteRange) {
        if (byteRange == null || byteRange.length < 4) {
            return new byte[0];
        }
        long start1 = byteRange[0];
        long length1 = byteRange[1];
        long start2 = byteRange[2];
        long length2 = byteRange[3];
        if (start1 < 0 || length1 < 0 || start2 < 0 || length2 < 0
                || start1 + length1 > file.length || start2 + length2 > file.length) {
            return null;
        }
        byte[] result = new byte[(int) (length1 + length2)];
        System.arraycopy(file, (int) start1, result, 0, (int) length1);
        System.arraycopy(file, (int) start2, result, (int) length1, (int) length2);
        return result;
    }

    private Instant referenceTime(List<ExtractedSignature> extracted) {
        for (ExtractedSignature signature : extracted) {
            if (signature.contents().length == 0) {
                continue;
            }
            try {
                CMSSignedData cms = cmsAnalysisService.parse(signature.contents());
                List<SignerInformation> signers = new ArrayList<>(cms.getSignerInfos().getSigners());
                if (!signers.isEmpty()) {
                    AttributeTable attributes = signers.get(0).getSignedAttributes();
                    if (attributes != null) {
                        Attribute signingTime = attributes.get(PKCSObjectIdentifiers.pkcs_9_at_signingTime);
                        if (signingTime != null && signingTime.getAttrValues().size() > 0) {
                            org.bouncycastle.asn1.cms.Time time = org.bouncycastle.asn1.cms.Time
                                    .getInstance(signingTime.getAttrValues().getObjectAt(0));
                            return time.getDate().toInstant();
                        }
                    }
                }
            } catch (RuntimeException e) {
                // fall through
            }
        }
        return Instant.now();
    }

    private String commonName(X509Certificate certificate) {
        String name = certificate.getSubjectX500Principal().getName();
        for (String part : name.split(",")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "CN=", 0, 3)) {
                return trimmed.substring(3);
            }
        }
        return name;
    }

    private String summarize(List<SignatureValidationDto> signatures, int signatureCount) {
        if (signatureCount == 0) {
            return "No signatures found";
        }
        int valid = 0;
        for (SignatureValidationDto signature : signatures) {
            if (STATUS_VALID.equals(signature.signatureIntegrity())) {
                valid++;
            }
        }
        return valid + " of " + signatureCount + " signatures cryptographically valid";
    }
}
