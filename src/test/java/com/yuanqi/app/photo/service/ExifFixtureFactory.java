package com.yuanqi.app.photo.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 仅用于测试和本机证据的确定性 JPEG/EXIF Fixture 生成器。 */
public final class ExifFixtureFactory {
    private ExifFixtureFactory() {
    }

    public static byte[] jpeg(String captureTime, String camera, String lens, int iso) throws Exception {
        BufferedImage image = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(32, 96, 160)); graphics.fillRect(0, 0, 1200, 1200);
        } finally { graphics.dispose(); }
        ByteArrayOutputStream base = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", base)) throw new IllegalStateException("JPEG writer unavailable");
        byte[] jpeg = base.toByteArray();
        byte[] exif = exif(captureTime, camera, lens, iso);
        ByteArrayOutputStream output = new ByteArrayOutputStream(jpeg.length + exif.length + 4);
        output.write(jpeg, 0, 2);
        output.write(0xff); output.write(0xe1);
        int segmentLength = exif.length + 2;
        output.write((segmentLength >>> 8) & 0xff); output.write(segmentLength & 0xff);
        output.write(exif); output.write(jpeg, 2, jpeg.length - 2);
        return output.toByteArray();
    }

    public static byte[] jpegWithBrokenExif() throws Exception {
        byte[] value = jpeg("2020:01:02 03:04:05", "Broken Camera", "Broken Lens", 100);
        value[12] = 0; value[13] = 0;
        return value;
    }

    private static byte[] exif(String captureTime, String camera, String lens, int iso) {
        ByteBuffer tiff = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        int ifd0 = 8;
        int exifIfd = ifd0 + 2 + 2 * 12 + 4;
        int data = exifIfd + 2 + 6 * 12 + 4;

        tiff.position(ifd0); tiff.putShort((short) 2);
        data = asciiEntry(tiff, 0x0110, camera, data);
        entry(tiff, 0x8769, 4, 1, exifIfd);
        tiff.putInt(0);

        tiff.position(exifIfd); tiff.putShort((short) 6);
        data = asciiEntry(tiff, 0x9003, captureTime, data);
        data = rationalEntry(tiff, 0x829d, 18, 10, data);
        data = rationalEntry(tiff, 0x829a, 1, 125, data);
        entry(tiff, 0x8827, 3, 1, iso);
        data = rationalEntry(tiff, 0x920a, 35, 1, data);
        data = asciiEntry(tiff, 0xa434, lens, data);
        tiff.putInt(0);

        byte[] prefix = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[prefix.length + data];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(tiff.array(), 0, result, prefix.length, data);
        return result;
    }

    private static int asciiEntry(ByteBuffer buffer, int tag, String value, int data) {
        byte[] bytes = (value + "\0").getBytes(StandardCharsets.US_ASCII);
        entry(buffer, tag, 2, bytes.length, data);
        int restore = buffer.position(); buffer.position(data); buffer.put(bytes); buffer.position(restore);
        return data + bytes.length;
    }

    private static int rationalEntry(ByteBuffer buffer, int tag, int numerator, int denominator, int data) {
        entry(buffer, tag, 5, 1, data);
        int restore = buffer.position(); buffer.position(data); buffer.putInt(numerator).putInt(denominator); buffer.position(restore);
        return data + 8;
    }

    private static void entry(ByteBuffer buffer, int tag, int type, int count, int value) {
        buffer.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("output captureTime camera lens iso");
        Path output = Path.of(args[0]); Files.createDirectories(output.getParent());
        Files.write(output, jpeg(args[1], args[2], args[3], Integer.parseInt(args[4])));
        System.out.println(output + " " + Files.size(output));
    }
}
