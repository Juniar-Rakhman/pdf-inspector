package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * A node of the parsed ASN.1 / DER tree.
 *
 * @param offset        byte offset of the tag in the source buffer
 * @param length        total encoded length (tag + length field + content)
 * @param headerLength  length of the tag + length field bytes
 * @param contentLength length of the value content in bytes
 * @param tagClass      UNIVERSAL, APPLICATION, CONTEXT-SPECIFIC or PRIVATE
 * @param tagNumber     numeric tag number
 * @param tagName       friendly name for universal tags
 * @param constructed   whether the value is constructed (contains children)
 * @param value         human readable decoded value
 * @param hex           hex dump of the content bytes (truncated)
 * @param children      child nodes for constructed or embedded values
 */
public record Asn1NodeDto(
        int offset,
        int length,
        int headerLength,
        int contentLength,
        String tagClass,
        int tagNumber,
        String tagName,
        boolean constructed,
        String value,
        String hex,
        List<Asn1NodeDto> children) {
}
