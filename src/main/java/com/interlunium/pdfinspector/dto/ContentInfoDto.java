package com.interlunium.pdfinspector.dto;

/**
 * Encapsulated content info of a CMS SignedData structure.
 *
 * @param oid           the content type OID
 * @param name          friendly name for the content type
 * @param contentLength length in bytes of the encapsulated content, or null for detached content
 * @param hexPrefix     first bytes of the content in hex, or null when detached
 */
public record ContentInfoDto(String oid, String name, Long contentLength, String hexPrefix) {
}
