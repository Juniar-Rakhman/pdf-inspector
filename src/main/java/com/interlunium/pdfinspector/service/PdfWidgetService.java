package com.interlunium.pdfinspector.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

import jakarta.enterprise.context.ApplicationScoped;

import com.interlunium.pdfinspector.dto.RectangleDto;
import com.interlunium.pdfinspector.dto.WidgetDto;
import com.interlunium.pdfinspector.service.SignatureExtractionService.ExtractedSignature;

/**
 * Locates every visible signature widget (signature form field annotation) in a PDF
 * and maps it to the matching extracted signature.
 */
@ApplicationScoped
public class PdfWidgetService {

    /**
     * Analyzes the AcroForm of a document.
     *
     * @param document   the loaded PDF document
     * @param signatures extracted signatures
     * @return all signature widgets, each referencing its signature index
     */
    public List<WidgetDto> analyze(PDDocument document, List<ExtractedSignature> signatures) {
        List<WidgetDto> widgets = new ArrayList<>();
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        if (acroForm == null) {
            return widgets;
        }

        Map<COSDictionary, Integer> byCos = new HashMap<>();
        for (ExtractedSignature signature : signatures) {
            byCos.put(signature.signature().getCOSObject(), signature.index());
        }

        for (PDField field : acroForm.getFieldTree()) {
            if (!(field instanceof PDSignatureField signatureField)) {
                continue;
            }
            PDSignature fieldSignature = signatureField.getSignature();
            Integer signatureIndex = fieldSignature == null ? null : byCos.get(fieldSignature.getCOSObject());
            String fieldName = field.getFullyQualifiedName();

            for (PDAnnotationWidget widget : signatureField.getWidgets()) {
                Integer pageNumber = resolvePageNumber(document, widget.getPage());
                RectangleDto rectangle = toRectangle(widget.getRectangle());
                if (pageNumber == null) {
                    continue;
                }
                widgets.add(new WidgetDto(pageNumber, rectangle, fieldName, signatureIndex));
            }
        }
        return widgets;
    }

    /**
     * Attaches rendering metadata (field name, page, rectangle) to each extracted signature.
     *
     * @param signatures extracted signatures
     * @param widgets    widgets produced by {@link #analyze}
     * @return a new list of signatures enriched with widget metadata
     */
    public List<ExtractedSignature> enrich(List<ExtractedSignature> signatures, List<WidgetDto> widgets) {
        Map<Integer, WidgetDto> firstWidgetBySignature = new HashMap<>();
        for (WidgetDto widget : widgets) {
            if (widget.signatureIndex() != null) {
                firstWidgetBySignature.putIfAbsent(widget.signatureIndex(), widget);
            }
        }
        List<ExtractedSignature> enriched = new ArrayList<>(signatures.size());
        for (ExtractedSignature signature : signatures) {
            WidgetDto widget = firstWidgetBySignature.get(signature.index());
            if (widget == null) {
                enriched.add(signature);
            } else {
                enriched.add(signature.withField(widget.fieldName(), widget.page(), widget.rectangle()));
            }
        }
        return enriched;
    }

    private Integer resolvePageNumber(PDDocument document, PDPage page) {
        if (page == null) {
            return null;
        }
        int index = document.getPages().indexOf(page);
        return index >= 0 ? index + 1 : null;
    }

    private RectangleDto toRectangle(PDRectangle rectangle) {
        if (rectangle == null) {
            return null;
        }
        return new RectangleDto(rectangle.getLowerLeftX(), rectangle.getLowerLeftY(),
                rectangle.getWidth(), rectangle.getHeight());
    }
}
