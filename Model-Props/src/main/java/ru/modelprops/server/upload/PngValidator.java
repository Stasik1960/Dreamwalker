package ru.modelprops.server.upload;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

final class PngValidator {
    private static final byte[] SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final long MAX_PIXELS = 16_777_216L;
    private static final int MAX_DIMENSION = 4096;

    private PngValidator() {
    }

    static void validate(byte[] bytes) throws IOException {
        if (bytes.length < 45 || !Arrays.equals(SIGNATURE, Arrays.copyOf(bytes, SIGNATURE.length))) {
            throw new IOException("The selected texture is not a valid PNG file");
        }

        int offset = SIGNATURE.length;
        int chunks = 0;
        boolean sawHeader = false;
        boolean sawData = false;
        boolean sawEnd = false;
        while (offset < bytes.length) {
            if (++chunks > 10_000 || bytes.length - offset < 12) {
                throw new IOException("The PNG chunk table is invalid");
            }
            long lengthLong = unsignedInt(bytes, offset);
            if (lengthLong > Integer.MAX_VALUE || lengthLong > bytes.length - offset - 12L) {
                throw new IOException("The PNG contains an invalid chunk length");
            }
            int length = (int) lengthLong;
            int typeOffset = offset + 4;
            int dataOffset = offset + 8;
            int crcOffset = dataOffset + length;
            int nextOffset = crcOffset + 4;
            String type = new String(bytes, typeOffset, 4, StandardCharsets.US_ASCII);

            CRC32 crc = new CRC32();
            crc.update(bytes, typeOffset, 4 + length);
            if (crc.getValue() != unsignedInt(bytes, crcOffset)) {
                throw new IOException("The PNG checksum is invalid");
            }

            if (chunks == 1 && !"IHDR".equals(type)) {
                throw new IOException("The PNG does not start with IHDR");
            }
            if ("IHDR".equals(type)) {
                if (sawHeader || length != 13) {
                    throw new IOException("The PNG IHDR chunk is invalid");
                }
                validateHeader(bytes, dataOffset);
                sawHeader = true;
            } else if ("IDAT".equals(type)) {
                sawData = true;
            } else if ("IEND".equals(type)) {
                if (length != 0 || nextOffset != bytes.length) {
                    throw new IOException("The PNG IEND chunk is invalid");
                }
                sawEnd = true;
                break;
            }
            offset = nextOffset;
        }

        if (!sawHeader || !sawData || !sawEnd) {
            throw new IOException("The PNG is incomplete");
        }
        validateDecoding(bytes);
    }

    private static void validateDecoding(byte[] bytes) throws IOException {
        BufferedImage image;
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            image = ImageIO.read(input);
        } catch (IOException | RuntimeException exception) {
            throw new IOException("The PNG pixel data could not be decoded", exception);
        }
        if (image == null) {
            throw new IOException("The PNG pixel data could not be decoded");
        }
        try {
            if (image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION
                    || (long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
                throw new IOException("The decoded PNG dimensions are outside the supported limits");
            }
        } finally {
            image.flush();
        }
    }

    private static void validateHeader(byte[] bytes, int offset) throws IOException {
        long width = unsignedInt(bytes, offset);
        long height = unsignedInt(bytes, offset + 4);
        int bitDepth = bytes[offset + 8] & 0xFF;
        int colorType = bytes[offset + 9] & 0xFF;
        int compression = bytes[offset + 10] & 0xFF;
        int filter = bytes[offset + 11] & 0xFF;
        int interlace = bytes[offset + 12] & 0xFF;

        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || width * height > MAX_PIXELS) {
            throw new IOException("PNG dimensions must be between 1 and 4096 pixels (maximum 16M pixels)");
        }
        boolean validDepth = switch (colorType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            default -> false;
        };
        if (!validDepth || compression != 0 || filter != 0 || (interlace != 0 && interlace != 1)) {
            throw new IOException("The PNG uses an unsupported header format");
        }
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFFL);
    }
}
