package com.interlunium.pdfinspector.dto;

/**
 * Basic constraints extension of an X.509 certificate.
 *
 * @param ca                    whether the certificate is a certification authority
 * @param pathLengthConstraint  the maximum number of intermediate CAs beneath this CA, if present
 */
public record BasicConstraintsDto(boolean ca, Integer pathLengthConstraint) {
}
