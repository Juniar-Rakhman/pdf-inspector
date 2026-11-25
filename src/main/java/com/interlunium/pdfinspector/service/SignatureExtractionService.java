package com.interlunium.pdfinspector.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;

import com.interlunium.pdfinspector.dto.RectangleDto;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extracts every digital signature dictionary from a loaded PDF document.
 *
 * <p>For each signature the raw embedded CMS bytes, the /ByteRange array and the
 * signature dictionary are captured. Rendering metadata (field name, page,
 * rectangle) is resolved by the {@link PdfWidgetService}.</p>
 */
@ApplicationScoped
public class SignatureExtractionService {

    /**
     * Extracts all signatures from a document.
     *
     * @param document the loaded PDF document
     * @return an ordered list of extracted signatures
     */
    public List<ExtractedSignature> extract(PDDocument document) {
        List<PDSignature> signatures = document.getSignatureDictionaries();
        List<ExtractedSignature> result = new ArrayList<>(signatures.size());
        for (int i = 0; i < signatures.size(); i++) {
            PDSignature signature = signatures.get(i);
            try {
                result.add(new ExtractedSignature(
                        i,
                        signature,
                        readContents(signature),
                        toLongArray(signature.getByteRange()),
                        null,
                        null,
                        null));
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to read signature #" + i, e);
            }
        }
        return result;
    }

    private byte[] readContents(PDSignature signature) throws IOException {
        byte[] contents = signature.getContents();
        return contents != null ? contents : new byte[0];
    }

    private long[] toLongArray(int[] byteRange) {
        if (byteRange == null) {
            return new long[0];
        }
        long[] result = new long[byteRange.length];
        for (int i = 0; i < byteRange.length; i++) {
            result[i] = byteRange[i];
        }
        return result;
    }

    /**
     * Internal representation of an extracted signature.
     *
     * @param index       zero based signature index
     * @param signature   the PDFBox signature dictionary wrapper
     * @param contents    raw embedded CMS bytes (may be empty)
     * @param byteRange   the /ByteRange array (may be empty)
     * @param fieldName   resolved signature field name, set by {@link PdfWidgetService}
     * @param page        resolved one based page number, set by {@link PdfWidgetService}
     * @param rectangle   resolved widget rectangle, set by {@link PdfWidgetService}
     */
    public record ExtractedSignature(
            int index,
            PDSignature signature,
            byte[] contents,
            long[] byteRange,
            String fieldName,
            Integer page,
            RectangleDto rectangle) {

        /**
         * Creates a copy of this record with rendering metadata attached.
         */
        public ExtractedSignature withField(String fieldName, Integer page, RectangleDto rectangle) {
            return new ExtractedSignature(index, signature, contents, byteRange, fieldName, page, rectangle);
        }
    }
}
