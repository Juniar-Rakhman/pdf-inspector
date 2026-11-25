package com.interlunium.pdfinspector.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.interlunium.pdfinspector.dto.Asn1NodeDto;
import com.interlunium.pdfinspector.dto.CertificateChainDto;
import com.interlunium.pdfinspector.dto.CertificateDto;
import com.interlunium.pdfinspector.dto.CmsDto;
import com.interlunium.pdfinspector.dto.PdfMetadataDto;
import com.interlunium.pdfinspector.dto.RevisionDto;
import com.interlunium.pdfinspector.dto.SignatureDto;
import com.interlunium.pdfinspector.dto.UploadResultDto;
import com.interlunium.pdfinspector.dto.ValidationDto;
import com.interlunium.pdfinspector.dto.WidgetDto;
import com.interlunium.pdfinspector.service.Asn1ParserService;
import com.interlunium.pdfinspector.service.CertificateChainService;
import com.interlunium.pdfinspector.service.CmsAnalysisService;
import com.interlunium.pdfinspector.service.PdfAnalysisService;
import com.interlunium.pdfinspector.service.SignatureExtractionService;
import com.interlunium.pdfinspector.service.SignatureValidationService;

/**
 * Single resource root for the PDF signature inspector. Every endpoint lives
 * on the literal {@code /api/pdf} class path with method level paths; RESTEasy
 * Reactive cannot reliably route when template paths such as
 * {@code /api/pdf/{id}} are declared at the class level, so {@code {id}} is
 * always a method level template here.
 */
@Path("/api/pdf")
@Produces(MediaType.APPLICATION_JSON)
public class PdfResource {

    private final PdfAnalysisService pdfAnalysisService;
    private final CmsAnalysisService cmsAnalysisService;
    private final Asn1ParserService asn1ParserService;
    private final SignatureExtractionService signatureExtractionService;
    private final CertificateChainService certificateChainService;
    private final SignatureValidationService signatureValidationService;

    /**
     * @param pdfAnalysisService           document analysis and storage
     * @param cmsAnalysisService           CMS parsing
     * @param asn1ParserService            ASN.1 parsing
     * @param signatureExtractionService   signature content extraction
     * @param certificateChainService      certificate chain building
     * @param signatureValidationService   full validation reports
     */
    @Inject
    public PdfResource(PdfAnalysisService pdfAnalysisService,
            CmsAnalysisService cmsAnalysisService,
            Asn1ParserService asn1ParserService,
            SignatureExtractionService signatureExtractionService,
            CertificateChainService certificateChainService,
            SignatureValidationService signatureValidationService) {
        this.pdfAnalysisService = pdfAnalysisService;
        this.cmsAnalysisService = cmsAnalysisService;
        this.asn1ParserService = asn1ParserService;
        this.signatureExtractionService = signatureExtractionService;
        this.certificateChainService = certificateChainService;
        this.signatureValidationService = signatureValidationService;
    }

    /**
     * Uploads a PDF, analyzes it and returns the generated document id.
     *
     * @param file the uploaded file
     * @return the upload result
     */
    @POST
    @Path("/analyze")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UploadResultDto analyze(@RestForm("file") FileUpload file) {
        byte[] content = readUpload(file);
        validatePdf(content);
        PdfMetadataDto metadata = pdfAnalysisService.analyze(file.fileName(), content);
        return new UploadResultDto(metadata.documentId(), metadata.filename());
    }

    /**
     * Returns the metadata of a previously analyzed document.
     *
     * @param id document id
     * @return the metadata
     */
    @GET
    @Path("/{id}")
    public PdfMetadataDto metadata(@PathParam("id") String id) {
        return pdfAnalysisService.load(id).metadata();
    }

    /**
     * Returns the raw PDF content.
     *
     * @param id document id
     * @return the raw file
     */
    @GET
    @Path("/{id}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response content(@PathParam("id") String id) {
        PdfMetadataDto metadata = pdfAnalysisService.load(id).metadata();
        byte[] bytes = pdfAnalysisService.documentBytes(id);
        String filename = metadata.filename().replace("\"", "_");
        return Response.ok(bytes, MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .build();
    }

    /**
     * Returns every detected signature.
     *
     * @param id document id
     * @return the signatures
     */
    @GET
    @Path("/{id}/signatures")
    public List<SignatureDto> signatures(@PathParam("id") String id) {
        return pdfAnalysisService.load(id).signatures();
    }

    /**
     * Returns every visible signature widget.
     *
     * @param id document id
     * @return the widgets
     */
    @GET
    @Path("/{id}/widgets")
    public List<WidgetDto> widgets(@PathParam("id") String id) {
        return pdfAnalysisService.load(id).widgets();
    }

    /**
     * Returns the analyzed CMS of a specific signature.
     *
     * @param id             document id
     * @param signatureIndex zero based signature index
     * @return the CMS analysis
     */
    @GET
    @Path("/{id}/cms/{signatureIndex}")
    public CmsDto cms(@PathParam("id") String id, @PathParam("signatureIndex") int signatureIndex) {
        return cmsAnalysisService.analyze(signatureContents(id, signatureIndex), 0);
    }

    /**
     * Returns the parsed ASN.1 tree of a specific signature.
     *
     * @param id             document id
     * @param signatureIndex zero based signature index
     * @return the ASN.1 tree root node
     */
    @GET
    @Path("/{id}/asn1/{signatureIndex}")
    public Asn1NodeDto asn1(@PathParam("id") String id, @PathParam("signatureIndex") int signatureIndex) {
        return asn1ParserService.parse(signatureContents(id, signatureIndex));
    }

    /**
     * Returns every certificate embedded in the document.
     *
     * @param id document id
     * @return the certificates
     */
    @GET
    @Path("/{id}/certificates")
    public List<CertificateDto> certificates(@PathParam("id") String id) {
        return pdfAnalysisService.certificates(id);
    }

    /**
     * Returns the certificate chains for every signature.
     *
     * @param id document id
     * @return the chains
     */
    @GET
    @Path("/{id}/certificates/chains")
    public List<CertificateChainDto> chains(@PathParam("id") String id) {
        return certificateChainService.forDocument(id);
    }

    /**
     * Returns the full validation report for a document.
     *
     * @param id document id
     * @return the report
     */
    @GET
    @Path("/{id}/validation")
    public ValidationDto validation(@PathParam("id") String id) {
        return signatureValidationService.validate(id);
    }

    /**
     * Returns the revision timeline.
     *
     * @param id document id
     * @return the revisions
     */
    @GET
    @Path("/{id}/revisions")
    public List<RevisionDto> revisions(@PathParam("id") String id) {
        return pdfAnalysisService.load(id).revisions();
    }

    /**
     * Returns the file truncated to a specific revision.
     *
     * @param id            document id
     * @param revisionIndex zero based revision index
     * @return the revision bytes
     */
    @GET
    @Path("/{id}/revisions/{revisionIndex}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response revisionContent(@PathParam("id") String id, @PathParam("revisionIndex") int revisionIndex) {
        byte[] bytes = pdfAnalysisService.revisionBytes(id, revisionIndex);
        return Response.ok(bytes, MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "inline; filename=\"revision-" + revisionIndex + ".pdf\"")
                .build();
    }

    private byte[] signatureContents(String id, int signatureIndex) {
        var signatures = pdfAnalysisService.load(id).signatures();
        if (signatureIndex < 0 || signatureIndex >= signatures.size()) {
            throw new IllegalArgumentException("Unknown signature index " + signatureIndex);
        }
        byte[] documentBytes = pdfAnalysisService.documentBytes(id);
        try (PDDocument document = Loader.loadPDF(documentBytes)) {
            List<byte[]> contents = signatureExtractionService.extract(document)
                    .stream()
                    .map(s -> s.contents())
                    .toList();
            if (signatureIndex >= contents.size() || contents.get(signatureIndex).length == 0) {
                throw new IllegalArgumentException("Signature " + signatureIndex + " has no extractable CMS content.");
            }
            return contents.get(signatureIndex);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to extract CMS content for signature " + signatureIndex, e);
        }
    }

    private byte[] readUpload(FileUpload file) {
        if (file == null) {
            throw new IllegalArgumentException("No file part named 'file' was uploaded.");
        }
        try {
            var path = file.uploadedFile();
            try (InputStream in = Files.newInputStream(path)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read the uploaded file: " + e.getMessage(), e);
        }
    }

    private void validatePdf(byte[] content) {
        if (content.length < 5) {
            throw new IllegalArgumentException("The uploaded file is too small to be a PDF.");
        }
        boolean magic = content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
        if (!magic) {
            throw new IllegalArgumentException("The uploaded file does not start with the %PDF header.");
        }
    }
}
