package com.interlunium.pdfinspector.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.interlunium.pdfinspector.dto.Asn1NodeDto;

/**
 * Recursive DER / BER parser that turns a raw ASN.1 byte stream into a tree of
 * {@link Asn1NodeDto} nodes, similar to the output of OpenSSL's {@code asn1parse}.
 *
 * <p>Supports definite and indefinite lengths, constructed tags, and best-effort
 * decoding of primitive values (integers, OIDs, strings, times, bit strings).
 * Where a value is itself DER encoded (e.g. an OCTET STRING holding a certificate),
 * it is transparently expanded into child nodes.</p>
 */
@ApplicationScoped
public class Asn1ParserService {

    /** Maximum number of content bytes rendered as hex before truncation. */
    private static final int MAX_HEX = 1024;

    /** Maximum depth to prevent stack exhaustion on malicious input. */
    private static final int MAX_DEPTH = 128;

    private static final String[] UNIVERSAL_NAMES = {
            "EOC", "BOOLEAN", "INTEGER", "BIT STRING", "OCTET STRING", "NULL",
            "OBJECT IDENTIFIER", "OBJECT DESCRIPTOR", "EXTERNAL", "REAL", "ENUMERATED",
            "EMBEDDED PDV", "UTF8String", "RELATIVE-OID", "TIME", "RESERVED",
            "SEQUENCE", "SET", "NumericString", "PrintableString", "T61String",
            "VideotexString", "IA5String", "UTCTime", "GeneralizedTime",
            "GraphicString", "VisibleString", "GeneralString", "UniversalString",
            "RESERVED", "BMPString"};

    private final OidRegistry oidRegistry;

    /**
     * @param oidRegistry registry used to resolve OIDs inside the parsed tree
     */
    @Inject
    public Asn1ParserService(OidRegistry oidRegistry) {
        this.oidRegistry = oidRegistry;
    }

    /**
     * Parses a complete DER/BER value.
     *
     * @param data the raw bytes
     * @return the root node
     */
    public Asn1NodeDto parse(byte[] data) {
        return parseNode(new Cursor(data), 0);
    }

    private Asn1NodeDto parseNode(Cursor cursor, int depth) {
        int startOffset = cursor.pos;
        int tag = cursor.readByte();

        int tagClassBits = (tag >> 6) & 0x03;
        boolean constructed = (tag & 0x20) != 0;
        int tagNumber = tag & 0x1F;
        if (tagNumber == 0x1F) {
            tagNumber = cursor.readLongFormTagNumber();
        }

        int[] lengthInfo = cursor.readLength();
        boolean indefinite = lengthInfo[0] == -1;
        int contentLength = (int) lengthInfo[1];
        int contentStart = cursor.pos;

        byte[] content;
        if (indefinite) {
            int contentEnd = cursor.findEoc();
            content = cursor.slice(contentStart, contentEnd - contentStart);
            cursor.pos = contentEnd + 2;
        } else {
            content = cursor.slice(contentStart, contentLength);
            cursor.pos = contentStart + contentLength;
        }

        String tagClass = switch (tagClassBits) {
            case 0 -> "UNIVERSAL";
            case 1 -> "APPLICATION";
            case 2 -> "CONTEXT-SPECIFIC";
            default -> "PRIVATE";
        };

        List<Asn1NodeDto> children = new ArrayList<>();
        String value = null;
        String hex = null;

        boolean isUniversal = tagClassBits == 0;
        if (constructed || indefinite) {
            Cursor childCursor = new Cursor(content);
            while (!childCursor.eof()) {
                if (depth + 1 >= MAX_DEPTH) {
                    break;
                }
                children.add(parseNode(childCursor, depth + 1));
            }
        }

        if (!constructed && isUniversal) {
            Decoded decoded = decodePrimitive(tagNumber, content);
            value = decoded.value();
            hex = decoded.hex();
            if (decoded.children() != null) {
                children = decoded.children();
            }
        }

        if (hex == null) {
            hex = hexLimit(content, MAX_HEX);
        }

        int headerLength = contentStart - startOffset;
        int totalLength = indefinite ? cursor.pos - startOffset : headerLength + contentLength;

        String tagName = isUniversal && tagNumber < UNIVERSAL_NAMES.length
                ? UNIVERSAL_NAMES[tagNumber]
                : tagClass + " [" + tagNumber + "]";

        return new Asn1NodeDto(startOffset, totalLength, headerLength,
                indefinite ? content.length : contentLength,
                tagClass, tagNumber, tagName, constructed || indefinite,
                value, hex, children);
    }

    private Decoded decodePrimitive(int tagNumber, byte[] content) {
        return switch (tagNumber) {
            case 0x01 -> new Decoded((content.length == 1 && content[0] != 0) ? "true" : "false", null, null);
            case 0x02 -> {
                BigInteger integer = new BigInteger(content);
                yield new Decoded(integer.toString(10) + " (0x" + integer.toString(16) + ")", null, null);
            }
            case 0x03 -> {
                int unusedBits = content.length > 0 ? (content[0] & 0xFF) : 0;
                byte[] bits = content.length > 1 ? Arrays.copyOfRange(content, 1, content.length) : new byte[0];
                List<Asn1NodeDto> embedded = maybeEmbedded(bits, unusedBits);
                String label = "BIT STRING (" + (bits.length * 8 - unusedBits) + " bits, " + unusedBits + " unused)";
                yield new Decoded(label, null, embedded);
            }
            case 0x04 -> {
                List<Asn1NodeDto> embedded = maybeEmbedded(content, 0);
                String label = "OCTET STRING (" + content.length + " bytes)"
                        + (embedded == null ? "" : ", DER encoded");
                yield new Decoded(label, null, embedded);
            }
            case 0x05 -> new Decoded("NULL", null, null);
            case 0x06 -> decodeOid(content);
            case 0x0A -> {
                BigInteger integer = new BigInteger(content);
                yield new Decoded(integer.toString(10), null, null);
            }
            case 0x0C, 0x12, 0x13, 0x14, 0x15, 0x16, 0x19, 0x1A, 0x1B, 0x1C, 0x1E ->
                    new Decoded(text(content), null, null);
            case 0x17 -> new Decoded("UTCTime " + text(content), null, null);
            case 0x18 -> new Decoded("GeneralizedTime " + text(content), null, null);
            default -> new Decoded(null, null, null);
        };
    }

    /**
     * Attempts to interpret an OCTET STRING / BIT STRING payload as DER.
     *
     * @return parsed children or null when the payload is not valid DER
     */
    private List<Asn1NodeDto> maybeEmbedded(byte[] content, int skip) {
        if (content.length - skip < 4) {
            return null;
        }
        try {
            Cursor probe = new Cursor(content, skip);
            Asn1NodeDto node = parseNode(probe, 1);
            if (probe.eof() && node.length() == content.length - skip) {
                return List.of(node);
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Decoded decodeOid(byte[] content) {
        StringBuilder raw = new StringBuilder();
        long value = 0;
        boolean first = true;
        long firstArc = -1;
        for (byte b : content) {
            long cur = b & 0xFF;
            value = (value << 7) | (cur & 0x7F);
            if ((cur & 0x80) == 0) {
                if (first) {
                    firstArc = Math.min(value / 40, 2);
                    long second = value - firstArc * 40;
                    raw.append(firstArc).append('.').append(second);
                    first = false;
                } else {
                    raw.append('.').append(value);
                }
                value = 0;
            }
        }
        String oid = raw.toString();
        String name = oidRegistry.name(oid);
        if (name.equals(oid)) {
            return new Decoded(oid, null, null);
        }
        return new Decoded(oid + " (" + name + ")", null, null);
    }

    private String text(byte[] content) {
        return new String(content, StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}]", " ");
    }

    private String hexLimit(byte[] content, int max) {
        int len = Math.min(content.length, max);
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X", content[i]));
        }
        if (content.length > max) {
            sb.append("...");
        }
        return sb.toString();
    }

    /**
     * Internal cursor over a byte buffer.
     */
    private static final class Cursor {
        private final byte[] data;
        private int pos;

        private Cursor(byte[] data) {
            this.data = data;
            this.pos = 0;
        }

        private Cursor(byte[] data, int start) {
            this.data = data;
            this.pos = start;
        }

        boolean eof() {
            return pos >= data.length;
        }

        int readByte() {
            if (pos >= data.length) {
                throw new IllegalStateException("Unexpected end of ASN.1 data at offset " + pos);
            }
            return data[pos++] & 0xFF;
        }

        int readLongFormTagNumber() {
            int number = 0;
            while (true) {
                int b = readByte();
                number = (number << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) {
                    return number;
                }
            }
        }

        /**
         * Reads a length field.
         *
         * @return form 0 (short), 1 (long) or -1 (indefinite) plus the length value
         */
        int[] readLength() {
            int first = readByte();
            if (first == 0x80) {
                return new int[] {-1, 0};
            }
            if ((first & 0x80) == 0) {
                return new int[] {0, first};
            }
            int count = first & 0x7F;
            long length = 0;
            for (int i = 0; i < count; i++) {
                length = (length << 8) | readByte();
            }
            if (length > Integer.MAX_VALUE) {
                throw new IllegalStateException("ASN.1 length too large");
            }
            return new int[] {1, (int) length};
        }

        byte[] slice(int start, int len) {
            if (start < 0 || len < 0 || start + len > data.length) {
                throw new IllegalStateException("ASN.1 length out of bounds");
            }
            return Arrays.copyOfRange(data, start, start + len);
        }

        /**
         * Finds the position of the EOC marker (0x00 0x00) that closes the
         * current indefinite length element. Nested indefinite length elements
         * are skipped so their EOC markers do not close the outer element.
         */
        int findEoc() {
            while (pos + 1 < data.length) {
                int first = data[pos] & 0xFF;
                if (first == 0 && data[pos + 1] == 0) {
                    return pos;
                }
                pos++;
                if ((first & 0x1F) == 0x1F) {
                    while ((data[pos++] & 0x80) != 0) {
                        // consume high tag number octets
                    }
                }
                int lenByte = data[pos++] & 0xFF;
                if (lenByte == 0x80) {
                    int eoc = findEoc();
                    pos = eoc + 2;
                } else if ((lenByte & 0x80) == 0) {
                    pos += lenByte;
                } else {
                    int count = lenByte & 0x7F;
                    long length = 0;
                    for (int i = 0; i < count; i++) {
                        length = (length << 8) | (data[pos++] & 0xFF);
                    }
                    pos += length;
                }
            }
            throw new IllegalStateException("No EOC found for indefinite length");
        }
    }

    /**
     * Small carrier for a decoded primitive value.
     */
    private record Decoded(String value, String hex, List<Asn1NodeDto> children) {
    }
}
