package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * A single CMS attribute (signed or unsigned).
 *
 * @param oid     the attribute type OID
 * @param name    friendly name for the OID
 * @param values  the decoded attribute values
 */
public record AttributeDto(String oid, String name, List<AttributeValueDto> values) {
}
