package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * Validation status of the certificate chain of a signature.
 *
 * @param index         zero based index of the signature this chain belongs to
 * @param complete      whether a full chain from signer up to a self-signed root could be built from embedded certificates
 * @param pathLength    number of certificates in the built chain (root excluded if not embedded)
 * @param messages      explanatory messages
 */
public record ChainStatusDto(int index, boolean complete, int pathLength, List<String> messages) {
}
