package com.interlunium.pdfinspector.dto;

/**
 * A visible signature widget (form field annotation) on a PDF page.
 *
 * @param page            one based page number
 * @param rectangle       widget rectangle in PDF coordinates
 * @param fieldName       fully qualified signature field name
 * @param signatureIndex  index of the matching signature, or null if no signature is associated
 */
public record WidgetDto(int page, RectangleDto rectangle, String fieldName, Integer signatureIndex) {
}
