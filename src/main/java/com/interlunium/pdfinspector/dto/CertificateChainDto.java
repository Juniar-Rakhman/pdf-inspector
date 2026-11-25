package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * Visualized certificate chain for a signature.
 *
 * @param signatureIndex index of the signature this chain was built for
 * @param nodes          chain nodes ordered from root to signer
 * @param complete       whether the chain is complete up to a self-signed root
 * @param pathLength     number of certificates in the chain
 * @param missing        whether certificates were missing to reach a root
 * @param messages       explanatory messages
 */
public record CertificateChainDto(
        int signatureIndex,
        List<ChainNodeDto> nodes,
        boolean complete,
        int pathLength,
        boolean missing,
        List<String> messages) {
}
