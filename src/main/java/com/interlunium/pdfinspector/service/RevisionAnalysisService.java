package com.interlunium.pdfinspector.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import com.interlunium.pdfinspector.dto.RevisionDto;

/**
 * Detects the incremental revisions of a PDF by scanning the raw file for the
 * {@code %%EOF} markers that terminate each revision.
 */
@ApplicationScoped
public class RevisionAnalysisService {

    private static final byte[] EOF = "%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] STARTXREF = "startxref".getBytes(StandardCharsets.US_ASCII);

    /**
     * Scans the raw file and produces a revision per {@code %%EOF} marker.
     *
     * @param data raw PDF bytes
     * @return ordered list of revisions
     */
    public List<RevisionDto> scan(byte[] data) {
        List<Long> eofPositions = findPattern(data, EOF);
        List<RevisionDto> revisions = new ArrayList<>(eofPositions.size());
        for (int i = 0; i < eofPositions.size(); i++) {
            long endOffset = eofPositions.get(i) + EOF.length;
            long startOffset = i == 0 ? 0 : eofPositions.get(i - 1) + EOF.length;
            long xrefOffset = findStartXref(data, eofPositions.get(i));
            boolean isLast = i == eofPositions.size() - 1;
            revisions.add(new RevisionDto(i, startOffset, endOffset, xrefOffset,
                    List.of(), List.of(), "Original document", isLast));
        }
        return revisions;
    }

    /**
     * Determines the revision a signature belongs to based on the start of its
     * second byte range segment.
     *
     * @param data                  raw PDF bytes
     * @param secondRangeStart      {@code byteRange[2]} of the signature
     * @return one based revision number
     */
    public int revisionForSignature(byte[] data, long secondRangeStart) {
        List<Long> eofPositions = findPattern(data, EOF);
        int revision = 1;
        for (Long eof : eofPositions) {
            if (eof < secondRangeStart) {
                revision++;
            }
        }
        return revision;
    }

    private long findStartXref(byte[] data, long eofPosition) {
        long windowStart = Math.max(0, eofPosition - 256);
        int index = lastIndexOf(data, STARTXREF, (int) windowStart, (int) eofPosition);
        if (index < 0) {
            return -1;
        }
        int pos = index + STARTXREF.length;
        while (pos < data.length && isWhitespace(data[pos])) {
            pos++;
        }
        long value = 0;
        while (pos < data.length && data[pos] >= '0' && data[pos] <= '9') {
            value = value * 10 + (data[pos] - '0');
            pos++;
        }
        return value;
    }

    private List<Long> findPattern(byte[] data, byte[] pattern) {
        List<Long> positions = new ArrayList<>();
        int max = data.length - pattern.length;
        for (int i = 0; i <= max; i++) {
            if (data[i] == pattern[0] && matches(data, i, pattern)) {
                positions.add((long) i);
                i += pattern.length - 1;
            }
        }
        return positions;
    }

    private boolean matches(byte[] data, int offset, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private int lastIndexOf(byte[] data, byte[] pattern, int from, int to) {
        for (int i = to - pattern.length; i >= from; i--) {
            if (data[i] == pattern[0] && matches(data, i, pattern)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isWhitespace(int b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == 0x0C;
    }
}
