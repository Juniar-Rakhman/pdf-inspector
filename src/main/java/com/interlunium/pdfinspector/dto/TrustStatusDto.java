package com.interlunium.pdfinspector.dto;

import java.util.List;

/**
 * Trust status of a validated chain.
 *
 * @param index      zero based index of the signature this trust evaluation belongs to
 * @param status     TRUSTED when the chain anchors in the JVM default trust store, otherwise UNKNOWN
 * @param messages   explanatory messages
 */
public record TrustStatusDto(int index, String status, List<String> messages) {
}
