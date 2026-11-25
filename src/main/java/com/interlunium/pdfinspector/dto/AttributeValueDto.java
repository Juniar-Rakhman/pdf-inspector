package com.interlunium.pdfinspector.dto;

/**
 * A single decoded value inside a CMS attribute.
 *
 * @param type    the ASN.1 type of the value (e.g. OCTET STRING, OID, UTF8String)
 * @param display human readable decoded representation
 * @param hex     hex dump of the raw value bytes
 */
public record AttributeValueDto(String type, String display, String hex) {
}
