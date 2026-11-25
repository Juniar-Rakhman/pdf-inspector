package com.interlunium.pdfinspector.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform API error payload.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     HTTP error reason phrase
 * @param message   human readable message
 * @param details   optional additional details
 */
public record ErrorDto(Instant timestamp, int status, String error, String message, List<String> details) {
}
