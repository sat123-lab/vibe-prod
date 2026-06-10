package com.example.demo.controller;

import com.example.demo.service.FileService;
import com.example.demo.service.MediaUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/upload")
@CrossOrigin("*")
@RequiredArgsConstructor
public class UploadController {

    private final FileService fileService;
    private final MediaUrlService mediaUrlService;

    @PostMapping(value = "/media", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam(value = "kind", required = false) String kind
    ) throws IOException {

        boolean isVideo = isVideo(file, kind);
        String savedUrl = isVideo
                ? fileService.uploadVideo(file)
                : fileService.uploadImage(file);

        String thumbUrl = null;
        if (thumbnail != null && !thumbnail.isEmpty()) {
            thumbUrl = fileService.uploadImage(thumbnail);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("url", mediaUrlService.resolve(savedUrl));
        body.put("path", savedUrl);
        body.put("kind", isVideo ? "video" : "image");
        body.put("size", file.getSize());
        if (thumbUrl != null) {
            body.put("thumbnailUrl", mediaUrlService.resolve(thumbUrl));
            body.put("thumbnailPath", thumbUrl);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/video")
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        String savedUrl = fileService.uploadVideo(file);
        return ResponseEntity.ok(Map.of(
                "url", mediaUrlService.resolve(savedUrl),
                "path", savedUrl,
                "kind", "video",
                "size", file.getSize()));
    }

    @PostMapping("/image")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        String savedUrl = fileService.uploadImage(file);
        return ResponseEntity.ok(Map.of(
                "url", mediaUrlService.resolve(savedUrl),
                "path", savedUrl,
                "kind", "image",
                "size", file.getSize()));
    }

    private static boolean isVideo(MultipartFile file, String hint) {
        if (hint != null && !hint.isBlank()) {
            return "video".equalsIgnoreCase(hint.trim());
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("video/")) return true;
        String name = file.getOriginalFilename();
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov")
                || lower.endsWith(".webm") || lower.endsWith(".m4v")
                || lower.endsWith(".mkv");
    }
}
