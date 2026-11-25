package com.interlunium.pdfinspector.dto;

/**
 * Immutable rectangle in PDF coordinate space (origin at bottom-left, units in points).
 *
 * @param x      lower-left x coordinate
 * @param y      lower-left y coordinate
 * @param width  rectangle width
 * @param height rectangle height
 */
public record RectangleDto(float x, float y, float width, float height) {
}
