package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    public String uploadFile(MultipartFile file) throws IOException {
        return uploadFile(file, null);
    }

    /**
     * Persists a file under {@code file.upload-dir}, optionally in a sub-folder
     * (e.g. {@code images}, {@code videos}). Returns a public path like
     * {@code /uploads/images/uuid_name.jpg}.
     */
    public String uploadFile(MultipartFile file, String subDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String safeOriginal = sanitizeFilename(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "_" + safeOriginal;

        Path basePath = Paths.get(uploadDir);
        Path targetDir = subDirectory == null || subDirectory.isBlank()
                ? basePath
                : basePath.resolve(subDirectory.trim());
        Files.createDirectories(targetDir);

        Path filePath = targetDir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        if (subDirectory == null || subDirectory.isBlank()) {
            return "/uploads/" + fileName;
        }
        return "/uploads/" + subDirectory.trim() + "/" + fileName;
    }

    public String uploadImage(MultipartFile file) throws IOException {
        return uploadFile(file, "images");
    }

    public String uploadVideo(MultipartFile file) throws IOException {
        return uploadFile(file, "videos");
    }

    /**
     * Removes a previously uploaded file referenced by its public path.
     */
    public void deleteFile(String publicPath) {
        if (publicPath == null || publicPath.isBlank()) {
            return;
        }
        if (!publicPath.startsWith("/uploads/")) {
            return;
        }

        String relative = publicPath.substring("/uploads/".length());
        if (relative.isBlank() || relative.contains("..")) {
            return;
        }

        Path filePath = Paths.get(uploadDir).resolve(relative).normalize();
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!filePath.toAbsolutePath().normalize().startsWith(basePath)) {
            return;
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Do not fail the main request if cleanup fails
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "upload";
        }
        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (sep >= 0) {
            name = name.substring(sep + 1);
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
