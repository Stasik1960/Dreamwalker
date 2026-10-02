package ru.modelprops.server.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Strict, allocation-bounded validation for uploaded Ogg/Vorbis sounds.
 *
 * <p>This validator deliberately accepts one logical Vorbis stream only. Chained or
 * multiplexed Ogg files are not useful for short action sounds and make resource
 * accounting considerably harder.</p>
 */
public final class OggVorbisValidator {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_DURATION_SECONDS = 30;
    public static final int MIN_SAMPLE_RATE = 8_000;
    public static final int MAX_SAMPLE_RATE = 48_000;

    private static final byte[] CAPTURE_PATTERN = {'O', 'g', 'g', 'S'};
    private static final byte[] VORBIS = {'v', 'o', 'r', 'b', 'i', 's'};
    private static final int MAX_PAGES = 65_536;
    private static final int MAX_PACKETS = 131_072;
    private static final int MAX_COMMENT_COUNT = 4_096;
    private static final int[] CRC_TABLE = createCrcTable();

    private OggVorbisValidator() {
    }

    /**
     * Validates an entire Ogg/Vorbis file and returns its decoded stream metadata.
     * The byte array is never retained or modified.
     */
    public static Info validate(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new IOException("The selected sound is missing");
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IOException("OGG sounds must be between 1 byte and 2 MiB");
        }

        ByteArrayOutputStream packet = new ByteArrayOutputStream(Math.min(bytes.length, 64 * 1024));
        int offset = 0;
        int pages = 0;
        int packets = 0;
        int audioPackets = 0;
        int channels = -1;
        int sampleRate = -1;
        long serial = -1L;
        long expectedSequence = 0L;
        long previousGranule = -1L;
        long finalGranule = -1L;
        boolean sawEnd = false;

        while (offset < bytes.length) {
            if (sawEnd) {
                throw new IOException("The OGG contains data after its end-of-stream page");
            }
            if (++pages > MAX_PAGES || bytes.length - offset < 27) {
                throw new IOException("The OGG page table is invalid");
            }
            if (!matches(bytes, offset, CAPTURE_PATTERN) || (bytes[offset + 4] & 0xFF) != 0) {
                throw new IOException("The selected sound is not a supported OGG stream");
            }

            int headerType = bytes[offset + 5] & 0xFF;
            if ((headerType & ~0x07) != 0) {
                throw new IOException("The OGG page uses unsupported header flags");
            }
            boolean continued = (headerType & 0x01) != 0;
            boolean beginning = (headerType & 0x02) != 0;
            boolean end = (headerType & 0x04) != 0;
            if (continued != (packet.size() != 0)) {
                throw new IOException("The OGG packet continuation flags are inconsistent");
            }
            if (pages == 1) {
                if (!beginning || continued || end) {
                    throw new IOException("The OGG must begin with a standalone BOS page");
                }
            } else if (beginning) {
                throw new IOException("Chained or multiplexed OGG streams are not supported");
            }

            long granule = littleLong(bytes, offset + 6);
            if (granule < -1L) {
                throw new IOException("The OGG contains an unsupported granule position");
            }
            if (granule >= 0L) {
                if (previousGranule >= 0L && granule < previousGranule) {
                    throw new IOException("The OGG granule positions are not monotonic");
                }
                previousGranule = granule;
                finalGranule = granule;
            }

            long pageSerial = unsignedLittleInt(bytes, offset + 14);
            long sequence = unsignedLittleInt(bytes, offset + 18);
            if (pages == 1) {
                serial = pageSerial;
                if (sequence != 0L || granule != 0L) {
                    throw new IOException("The first OGG page has invalid sequence metadata");
                }
            } else if (pageSerial != serial || sequence != expectedSequence) {
                throw new IOException("The OGG contains multiple streams or missing pages");
            }
            expectedSequence = (sequence + 1L) & 0xFFFF_FFFFL;

            int segmentCount = bytes[offset + 26] & 0xFF;
            int segmentTableOffset = offset + 27;
            if (bytes.length - segmentTableOffset < segmentCount) {
                throw new IOException("The OGG segment table is truncated");
            }
            int bodyLength = 0;
            for (int i = 0; i < segmentCount; i++) {
                bodyLength += bytes[segmentTableOffset + i] & 0xFF;
            }
            int bodyOffset = segmentTableOffset + segmentCount;
            long pageEndLong = (long) bodyOffset + bodyLength;
            if (pageEndLong > bytes.length) {
                throw new IOException("The OGG page body is truncated");
            }
            int pageEnd = (int) pageEndLong;
            if (oggCrc(bytes, offset, pageEnd) != unsignedLittleInt(bytes, offset + 22)) {
                throw new IOException("The OGG page checksum is invalid");
            }

            int cursor = bodyOffset;
            int packetsBeforePage = packets;
            for (int i = 0; i < segmentCount; i++) {
                int length = bytes[segmentTableOffset + i] & 0xFF;
                if (packet.size() > MAX_BYTES - length) {
                    throw new IOException("The OGG contains an oversized packet");
                }
                packet.write(bytes, cursor, length);
                cursor += length;
                if (length < 255) {
                    if (++packets > MAX_PACKETS) {
                        throw new IOException("The OGG contains too many packets");
                    }
                    byte[] completePacket = packet.toByteArray();
                    if (packets == 1) {
                        StreamHeader header = validateIdentificationHeader(completePacket);
                        channels = header.channels();
                        sampleRate = header.sampleRate();
                    } else if (packets == 2) {
                        validateCommentHeader(completePacket);
                    } else if (packets == 3) {
                        validateSetupHeader(completePacket);
                    } else {
                        validateAudioPacket(completePacket);
                        audioPackets++;
                    }
                    packet.reset();
                }
            }

            if (pages == 1 && (packets - packetsBeforePage != 1 || packet.size() != 0)) {
                throw new IOException("The first OGG page must contain only the Vorbis identification header");
            }
            if (end) {
                if (granule < 0L || packet.size() != 0 || pageEnd != bytes.length) {
                    throw new IOException("The OGG end-of-stream page is incomplete");
                }
                sawEnd = true;
            }
            offset = pageEnd;
        }

        if (!sawEnd || packets < 4 || audioPackets == 0 || channels < 0 || sampleRate < 0 || finalGranule < 0L) {
            throw new IOException("The OGG/Vorbis stream is incomplete");
        }
        long maximumSamples = (long) sampleRate * MAX_DURATION_SECONDS;
        if (finalGranule > maximumSamples) {
            throw new IOException("OGG sounds may not be longer than 30 seconds");
        }
        long durationMillis = (finalGranule * 1_000L + sampleRate - 1L) / sampleRate;
        return new Info(channels, sampleRate, finalGranule, durationMillis, pages, packets);
    }

    private static StreamHeader validateIdentificationHeader(byte[] packet) throws IOException {
        if (packet.length != 30 || (packet[0] & 0xFF) != 1 || !matches(packet, 1, VORBIS)) {
            throw new IOException("The OGG does not start with a valid Vorbis identification header");
        }
        if (unsignedLittleInt(packet, 7) != 0L) {
            throw new IOException("Only Vorbis version 0 is supported");
        }
        int channels = packet[11] & 0xFF;
        long sampleRateLong = unsignedLittleInt(packet, 12);
        if ((channels != 1 && channels != 2)
                || sampleRateLong < MIN_SAMPLE_RATE || sampleRateLong > MAX_SAMPLE_RATE) {
            throw new IOException("Vorbis sounds must be mono/stereo at 8-48 kHz");
        }
        int blockSizes = packet[28] & 0xFF;
        int smallBlockExponent = blockSizes & 0x0F;
        int largeBlockExponent = (blockSizes >>> 4) & 0x0F;
        if (smallBlockExponent < 6 || largeBlockExponent > 13
                || smallBlockExponent > largeBlockExponent || (packet[29] & 0xFF) != 1) {
            throw new IOException("The Vorbis identification header has invalid block sizes or framing");
        }
        return new StreamHeader(channels, (int) sampleRateLong);
    }

    private static void validateCommentHeader(byte[] packet) throws IOException {
        if (packet.length < 16 || (packet[0] & 0xFF) != 3 || !matches(packet, 1, VORBIS)) {
            throw new IOException("The Vorbis comment header is missing or invalid");
        }
        int cursor = 7;
        int vendorLength = checkedLength(packet, cursor, "vendor string");
        cursor += 4;
        requireAvailable(packet, cursor, vendorLength, "vendor string");
        validateUtf8(packet, cursor, vendorLength, "Vorbis vendor string");
        cursor += vendorLength;

        int commentCount = checkedLength(packet, cursor, "comment count");
        cursor += 4;
        if (commentCount > MAX_COMMENT_COUNT) {
            throw new IOException("The Vorbis comment list is too large");
        }
        for (int i = 0; i < commentCount; i++) {
            int length = checkedLength(packet, cursor, "comment length");
            cursor += 4;
            requireAvailable(packet, cursor, length, "comment");
            validateUtf8(packet, cursor, length, "Vorbis comment");
            cursor += length;
        }
        if (cursor != packet.length - 1 || (packet[cursor] & 0xFF) != 1) {
            throw new IOException("The Vorbis comment header has invalid framing");
        }
    }

    private static void validateSetupHeader(byte[] packet) throws IOException {
        if (packet.length < 8 || (packet[0] & 0xFF) != 5 || !matches(packet, 1, VORBIS)
                || packet[packet.length - 1] == 0) {
            throw new IOException("The Vorbis setup header is missing or invalid");
        }
    }

    private static void validateAudioPacket(byte[] packet) throws IOException {
        if (packet.length == 0 || (packet[0] & 0x01) != 0) {
            throw new IOException("The Vorbis stream contains an invalid audio packet");
        }
    }

    private static int checkedLength(byte[] packet, int offset, String name) throws IOException {
        requireAvailable(packet, offset, 4, name);
        long length = unsignedLittleInt(packet, offset);
        if (length > Integer.MAX_VALUE) {
            throw new IOException("The Vorbis " + name + " is too large");
        }
        return (int) length;
    }

    private static void requireAvailable(byte[] bytes, int offset, int length, String name) throws IOException {
        if (offset < 0 || length < 0 || offset > bytes.length || length > bytes.length - offset) {
            throw new IOException("The Vorbis " + name + " is truncated");
        }
    }

    private static void validateUtf8(byte[] bytes, int offset, int length, String name) throws IOException {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length));
        } catch (CharacterCodingException exception) {
            throw new IOException(name + " is not valid UTF-8", exception);
        }
    }

    private static boolean matches(byte[] bytes, int offset, byte[] expected) {
        if (offset < 0 || bytes.length - offset < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static long unsignedLittleInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
    }

    private static long littleLong(byte[] bytes, int offset) {
        long result = 0L;
        for (int i = 0; i < 8; i++) {
            result |= (bytes[offset + i] & 0xFFL) << (8 * i);
        }
        return result;
    }

    private static long oggCrc(byte[] bytes, int start, int end) {
        int crc = 0;
        for (int i = start; i < end; i++) {
            int value = (i >= start + 22 && i < start + 26) ? 0 : bytes[i] & 0xFF;
            crc = (crc << 8) ^ CRC_TABLE[((crc >>> 24) & 0xFF) ^ value];
        }
        return Integer.toUnsignedLong(crc);
    }

    private static int[] createCrcTable() {
        int[] table = new int[256];
        for (int i = 0; i < table.length; i++) {
            int value = i << 24;
            for (int bit = 0; bit < 8; bit++) {
                value = (value & 0x8000_0000) != 0 ? (value << 1) ^ 0x04C1_1DB7 : value << 1;
            }
            table[i] = value;
        }
        return table;
    }

    /** Basic metadata extracted while validating the stream. */
    public record Info(int channels, int sampleRate, long sampleCount, long durationMillis,
                       int pageCount, int packetCount) {
    }

    private record StreamHeader(int channels, int sampleRate) {
    }
}
