package com.interlunium.pdfinspector.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.interlunium.pdfinspector.dto.CertificateChainDto;
import com.interlunium.pdfinspector.dto.ChainNodeDto;
import com.interlunium.pdfinspector.service.SignatureExtractionService.ExtractedSignature;

/**
 * Builds the certificate chain (root -&gt; intermediate -&gt; signer) for a signature
 * using only the certificates embedded in the document.
 */
@ApplicationScoped
public class CertificateChainService {

    private final StorageService storage;
    private final SignatureExtractionService signatureExtractionService;
    private final CmsAnalysisService cmsAnalysisService;

    /**
     * @param storage                    file storage
     * @param signatureExtractionService signature extraction
     * @param cmsAnalysisService         CMS analysis
     */
    @Inject
    public CertificateChainService(StorageService storage,
            SignatureExtractionService signatureExtractionService,
            CmsAnalysisService cmsAnalysisService) {
        this.storage = storage;
        this.signatureExtractionService = signatureExtractionService;
        this.cmsAnalysisService = cmsAnalysisService;
    }

    /**
     * Builds the chain for every signature of a document.
     *
     * @param documentId document id
     * @return one chain per signature
     */
    public List<CertificateChainDto> forDocument(String documentId) {
        byte[] file;
        try {
            file = storage.read(documentId);
        } catch (IOException e) {
            throw new NotFoundException("Document " + documentId + " not found");
        }
        try (PDDocument document = Loader.loadPDF(file)) {
            List<ExtractedSignature> extracted = signatureExtractionService.extract(document);
            Set<X509Certificate> unique = new LinkedHashSet<>();
            for (ExtractedSignature signature : extracted) {
                if (signature.contents().length == 0) {
                    continue;
                }
                try {
                    unique.addAll(cmsAnalysisService.certificates(cmsAnalysisService.parse(signature.contents())));
                } catch (RuntimeException e) {
                    // unparsable signatures are skipped
                }
            }
            List<X509Certificate> certificates = new ArrayList<>(unique);
            List<CertificateChainDto> result = new ArrayList<>();
            for (ExtractedSignature signature : extracted) {
                result.add(buildForSignature(signature, certificates));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to analyze chains for document " + documentId, e);
        }
    }

    private CertificateChainDto buildForSignature(ExtractedSignature signature, List<X509Certificate> certificates) {
        X509Certificate signer = null;
        if (signature.contents().length > 0) {
            try {
                CMSSignedData cms = cmsAnalysisService.parse(signature.contents());
                List<SignerInformation> signers = new ArrayList<>(cms.getSignerInfos().getSigners());
                if (!signers.isEmpty()) {
                    signer = cmsAnalysisService.signerCertificate(cms, signers.get(0));
                }
            } catch (RuntimeException e) {
                // chain cannot be built for unparsable CMS
            }
        }
        return build(signature.index(), certificates, signer);
    }

    /**
     * Builds the chain for a single signature.
     *
     * @param signatureIndex index of the signature
     * @param certificates   all certificates embedded in the document
     * @param signer         the signer certificate (or null if it cannot be resolved)
     * @return the visualized chain
     */
    public CertificateChainDto build(int signatureIndex, List<X509Certificate> certificates, X509Certificate signer) {
        List<String> messages = new ArrayList<>();

        if (signer == null) {
            return new CertificateChainDto(signatureIndex, List.of(), false, 0, true,
                    List.of("The signer certificate could not be resolved from the embedded certificates."));
        }

        List<X509Certificate> pool = new ArrayList<>(certificates);
        List<X509Certificate> chain = new ArrayList<>();
        List<Integer> chainIndices = new ArrayList<>();
        chain.add(signer);
        chainIndices.add(indexOf(certificates, signer));

        boolean complete = false;
        X509Certificate current = signer;
        while (true) {
            if (isSelfSigned(current)) {
                complete = true;
                break;
            }
            X509Certificate issuer = findIssuer(pool, current);
            if (issuer == null) {
                messages.add("The issuer of \"" + current.getSubjectX500Principal().getName()
                        + "\" is not embedded in the document. The chain is incomplete.");
                break;
            }
            chain.add(issuer);
            chainIndices.add(indexOf(certificates, issuer));
            current = issuer;
        }

        if (complete && chain.size() == 1) {
            messages.add("The signer certificate is self-signed.");
        } else if (complete) {
            messages.add("The chain is complete and anchored on a self-signed root certificate.");
        }

        List<ChainNodeDto> nodes = new ArrayList<>(chain.size());
        int last = chain.size() - 1;
        for (int i = 0; i < chain.size(); i++) {
            X509Certificate cert = chain.get(i);
            String type = i == 0 ? "SIGNER"
                    : (i == last && isSelfSigned(cert)) ? "ROOT" : "INTERMEDIATE";
            nodes.add(new ChainNodeDto(
                    cert.getSubjectX500Principal().getName(),
                    cert.getIssuerX500Principal().getName(),
                    sha256(cert),
                    type,
                    isSelfSigned(cert),
                    cert.getBasicConstraints() >= 0,
                    hasIssuerInPool(pool, cert),
                    chainIndices.get(i),
                    i));
        }

        return new CertificateChainDto(signatureIndex, nodes, complete, chain.size(), !complete, messages);
    }

    private boolean isSelfSigned(X509Certificate certificate) {
        return certificate.getIssuerX500Principal().equals(certificate.getSubjectX500Principal());
    }

    private X509Certificate findIssuer(List<X509Certificate> pool, X509Certificate subject) {
        for (X509Certificate candidate : pool) {
            if (candidate.getSubjectX500Principal().equals(subject.getIssuerX500Principal())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean hasIssuerInPool(List<X509Certificate> pool, X509Certificate certificate) {
        for (X509Certificate candidate : pool) {
            if (candidate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
                return true;
            }
        }
        return false;
    }

    private int indexOf(List<X509Certificate> certificates, X509Certificate target) {
        for (int i = 0; i < certificates.size(); i++) {
            if (certificates.get(i).equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private String sha256(X509Certificate certificate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(certificate.getEncoded());
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
