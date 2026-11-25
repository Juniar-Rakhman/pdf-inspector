package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * Full analysis of a CMS (PKCS#7) SignedData structure extracted from a signature.
 *
 * @param version                   SignedData version
 * @param contentTypeOid            encapsulated content type OID
 * @param contentTypeName           friendly encapsulated content type name
 * @param digestAlgorithms          detected digest algorithms (OIDs)
 * @param signerInfos               decoded SignerInfo summaries
 * @param signedAttributes          signed attributes of the first (or selected) signer
 * @param unsignedAttributes        unsigned attributes of the first (or selected) signer
 * @param certificates              embedded X.509 certificates
 * @param encapsulatedContentInfo   encapsulated content information
 * @param rawSize                   size of the encoded SignedData in bytes
 */
public record CmsDto(
        int version,
        String contentTypeOid,
        String contentTypeName,
        List<String> digestAlgorithms,
        List<SignerInfoDto> signerInfos,
        List<AttributeDto> signedAttributes,
        List<AttributeDto> unsignedAttributes,
        List<CertificateDto> certificates,
        ContentInfoDto encapsulatedContentInfo,
        long rawSize) {
}
