package com.example.demo.service;

import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Resizes and compresses post images before persisting to MySQL.
 */
@Service
public class ImageCompressionService {

    private static final int MAX_WIDTH = 1280;
    private static final float JPEG_QUALITY = 0.85f;

    public record CompressedImage(byte[] data, String contentType) {}

    public CompressedImage compressForStorage(byte[] raw, String contentType) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
        if (source == null) {
            String type = (contentType != null && !contentType.isBlank())
                    ? contentType
                    : "application/octet-stream";
            return new CompressedImage(raw, type);
        }

        BufferedImage prepared = scaleDown(source);
        byte[] jpeg = encodeJpeg(prepared);
        return new CompressedImage(jpeg, "image/jpeg");
    }

    private BufferedImage scaleDown(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();

        if (width <= MAX_WIDTH) {
            return toRgb(source);
        }

        int newHeight = (int) Math.round((double) height * MAX_WIDTH / width);
        BufferedImage scaled = new BufferedImage(MAX_WIDTH, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        graphics.drawImage(source, 0, 0, MAX_WIDTH, newHeight, null);
        graphics.dispose();
        return scaled;
    }

    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter available");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
