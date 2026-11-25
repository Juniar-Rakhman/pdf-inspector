package com.interlunium.pdfinspector.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.interlunium.pdfinspector.dto.CertificateDto;
import com.interlunium.pdfinspector.dto.PdfMetadataDto;
import com.interlunium.pdfinspector.dto.RevisionDto;
import com.interlunium.pdfinspector.dto.SignatureDto;
import com.interlunium.pdfinspector.dto.WidgetDto;
import com.interlunium.pdfinspector.service.SignatureExtractionService.ExtractedSignature;

/**
 * Top level analysis orchestrator.
 *
 * <p>Runs the whole analysis pipeline on upload (metadata, signatures, widgets,
 * revisions) and caches the resulting bundle on the filesystem. All other
 * inspection endpoints re-derive their data on demand from the stored PDF.</p>
 */
@ApplicationScoped
public class PdfAnalysisService {

    private final StorageService storage;
    private final SignatureExtractionService signatureExtractionService;
    private final PdfWidgetService pdfWidgetService;
    private final RevisionAnalysisService revisionAnalysisService;
    private final CmsAnalysisService cmsAnalysisService;
    private final CertificateAnalysisService certificateAnalysisService;
    private final ObjectMapper objectMapper;

    /**
     * @param storage                      file storage
     * @param signatureExtractionService   signature extraction
     * @param pdfWidgetService             widget analysis
     * @param revisionAnalysisService      revision scanning
     * @param cmsAnalysisService           CMS analysis
     * @param certificateAnalysisService   certificate analysis
     * @param objectMapper                 Jackson mapper for the analysis cache
     */
    @Inject
    public PdfAnalysisService(StorageService storage,
            SignatureExtractionService signatureExtractionService,
            PdfWidgetService pdfWidgetService,
            RevisionAnalysisService revisionAnalysisService,
            CmsAnalysisService cmsAnalysisService,
            CertificateAnalysisService certificateAnalysisService,
            ObjectMapper objectMapper) {
        this.storage = storage;
        this.signatureExtractionService = signatureExtractionService;
        this.pdfWidgetService = pdfWidgetService;
        this.revisionAnalysisService = revisionAnalysisService;
        this.cmsAnalysisService = cmsAnalysisService;
        this.certificateAnalysisService = certificateAnalysisService;
        this.objectMapper = objectMapper;
    }

    /**
     * Stores the upload and runs the full analysis pipeline.
     *
     * @param filename original file name
     * @param content  raw PDF bytes
     * @return the document metadata
     */
    public PdfMetadataDto analyze(String filename, byte[] content) {
        String documentId;
        try {
            documentId = storage.store(filename, content);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist the uploaded document", e);
        }
        AnalysisBundle bundle = compute(documentId, filename, content);
        storeBundle(bundle);
        return bundle.metadata();
    }

    /**
     * @param documentId document id
     * @return the cached analysis bundle
     */
    public AnalysisBundle load(String documentId) {
        byte[] json;
        try {
            json = storage.readAnalysis(documentId);
        } catch (IOException e) {
            throw new NotFoundException("Analysis for document " + documentId + " not found");
        }
        if (json == null) {
            throw new NotFoundException("Analysis for document " + documentId + " not found");
        }
        try {
            return objectMapper.readValue(json, AnalysisBundle.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read cached analysis for document " + documentId, e);
        }
    }

    /**
     * Returns all unique certificates embedded across every signature of the document.
     *
     * @param documentId document id
     * @return deduplicated certificate list
     */
    public List<CertificateDto> certificates(String documentId) {
        byte[] content;
        try {
            content = storage.read(documentId);
        } catch (IOException e) {
            throw new NotFoundException("Document " + documentId + " not found");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            List<ExtractedSignature> signatures = signatureExtractionService.extract(document);
            Set<X509Certificate> unique = new LinkedHashSet<>();
            for (ExtractedSignature signature : signatures) {
                if (signature.contents().length == 0) {
                    continue;
                }
                try {
                    var cms = cmsAnalysisService.parse(signature.contents());
                    unique.addAll(cmsAnalysisService.certificates(cms));
                } catch (RuntimeException e) {
                    // skip signatures whose CMS cannot be parsed
                }
            }
            List<CertificateDto> dtos = new ArrayList<>(unique.size());
            int index = 0;
            for (X509Certificate certificate : unique) {
                dtos.add(certificateAnalysisService.analyze(index++, certificate));
            }
            return dtos;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read document " + documentId, e);
        }
    }

    /**
     * Returns the bytes of the file truncated to a specific revision.
     *
     * @param documentId    document id
     * @param revisionIndex zero based revision index
     * @return the revision bytes
     */
    public byte[] revisionBytes(String documentId, int revisionIndex) {
        byte[] content;
        try {
            content = storage.read(documentId);
        } catch (IOException e) {
            throw new NotFoundException("Document " + documentId + " not found");
        }
        List<RevisionDto> revisions = load(documentId).revisions();
        if (revisionIndex < 0 || revisionIndex >= revisions.size()) {
            throw new NotFoundException("Unknown revision " + revisionIndex);
        }
        long endOffset = revisions.get(revisionIndex).endOffset();
        return Arrays.copyOf(content, (int) endOffset);
    }

    /**
     * Returns the raw bytes of a document.
     *
     * @param documentId document id
     * @return raw bytes
     */
    public byte[] documentBytes(String documentId) {
        try {
            return storage.read(documentId);
        } catch (IOException e) {
            throw new NotFoundException("Document " + documentId + " not found");
        }
    }

    private AnalysisBundle compute(String documentId, String filename, byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            List<ExtractedSignature> extracted = signatureExtractionService.extract(document);
            List<WidgetDto> widgets = pdfWidgetService.analyze(document, extracted);
            List<ExtractedSignature> enriched = pdfWidgetService.enrich(extracted, widgets);
            List<RevisionDto> revisions = revisionAnalysisService.scan(content);

            List<SignatureDto> signatures = new ArrayList<>(enriched.size());
            for (ExtractedSignature signature : enriched) {
                signatures.add(toDto(signature, content, revisions.size()));
            }

            List<RevisionDto> enrichedRevisions = enrichRevisions(revisions, signatures);

            PdfMetadataDto metadata = new PdfMetadataDto(
                    documentId,
                    filename,
                    content.length,
                    document.getNumberOfPages(),
                    sha256(content),
                    md5(content),
                    signatures.size(),
                    enrichedRevisions.size(),
                    storage.createdAt(documentId),
                    document.getDocumentInformation().getTitle(),
                    document.getDocumentInformation().getAuthor(),
                    document.getDocumentInformation().getProducer());

            return new AnalysisBundle(metadata, signatures, widgets, enrichedRevisions);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse the uploaded PDF: " + e.getMessage(), e);
        }
    }

    private SignatureDto toDto(ExtractedSignature signature, byte[] content, int revisionCount) {
        PDSignature pd = signature.signature();
        long[] byteRange = signature.byteRange();
        long coveredBytes = byteRangeLength(byteRange);
        boolean coversWhole = byteRange.length >= 4
                && (byteRange[2] + byteRange[3]) >= content.length - 2;
        int revision = revisionFor(byteRange, content, revisionCount);
        String signingTime = null;
        Calendar signDate = pd.getSignDate();
        if (signDate != null) {
            signingTime = signDate.toInstant().toString();
        }
        return new SignatureDto(
                signature.index(),
                signature.fieldName(),
                signature.page(),
                signature.rectangle(),
                pd.getName(),
                pd.getReason(),
                pd.getLocation(),
                pd.getContactInfo(),
                signingTime,
                revision,
                pd.getFilter(),
                pd.getSubFilter(),
                toLongList(byteRange),
                coveredBytes,
                coversWhole,
                signature.contents().length);
    }

    private int revisionFor(long[] byteRange, byte[] content, int revisionCount) {
        if (byteRange.length >= 2 && byteRange[2] > 0) {
            return revisionAnalysisService.revisionForSignature(content, byteRange[2]);
        }
        return Math.max(1, revisionCount);
    }

    private List<RevisionDto> enrichRevisions(List<RevisionDto> revisions, List<SignatureDto> signatures) {
        List<RevisionDto> result = new ArrayList<>(revisions.size());
        for (RevisionDto revision : revisions) {
            List<Integer> signatureIndices = new ArrayList<>();
            List<String> signerNames = new ArrayList<>();
            for (SignatureDto signature : signatures) {
                if (signature.revision() != null && signature.revision() - 1 == revision.index()) {
                    signatureIndices.add(signature.index());
                    signerNames.add(signature.signerName() == null ? "Unknown signer" : signature.signerName());
                }
            }
            String description;
            if (signatureIndices.isEmpty()) {
                description = revision.index() == 0 ? "Original document" : "Incremental update";
            } else {
                description = revision.index() == 0
                        ? "Signed by " + String.join(", ", signerNames)
                        : "Signed by " + String.join(", ", signerNames);
            }
            result.add(new RevisionDto(revision.index(), revision.startOffset(), revision.endOffset(),
                    revision.xrefOffset(), signatureIndices, signerNames, description, revision.isLast()));
        }
        return result;
    }

    private void storeBundle(AnalysisBundle bundle) {
        try {
            storage.writeAnalysis(bundle.metadata().documentId(), objectMapper.writeValueAsBytes(bundle));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to cache analysis result", e);
        }
    }

    private long byteRangeLength(long[] byteRange) {
        long total = 0;
        for (long value : byteRange) {
            total += Math.max(0, value);
        }
        return total;
    }

    private List<Long> toLongList(long[] byteRange) {
        if (byteRange == null) {
            return List.of();
        }
        return Arrays.stream(byteRange).boxed().toList();
    }

    private String sha256(byte[] content) {
        return hex(digest(content, "SHA-256"));
    }

    private String md5(byte[] content) {
        return hex(digest(content, "MD5"));
    }

    private byte[] digest(byte[] content, String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm).digest(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Cached result of the upload analysis.
     *
     * @param metadata   document metadata
     * @param signatures extracted signatures
     * @param widgets    signature widgets
     * @param revisions  incremental revisions
     */
    public record AnalysisBundle(
            PdfMetadataDto metadata,
            List<SignatureDto> signatures,
            List<WidgetDto> widgets,
            List<RevisionDto> revisions) {
    }
}
