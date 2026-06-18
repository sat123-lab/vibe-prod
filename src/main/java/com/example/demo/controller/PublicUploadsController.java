package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Public media serving — images/videos/reels must load without JWT.
 *
 * <p>Uses an explicit MVC handler (not {@code ResourceHttpRequestHandler}) so Spring
 * Security {@code permitAll} and {@code WebSecurityCustomizer} apply reliably on Render.</p>
 */
@RestController
public class PublicUploadsController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping("/uploads/**")
    public ResponseEntity<Resource> serve(jakarta.servlet.http.HttpServletRequest request)
            throws IOException {

        String uri = request.getRequestURI();
        String prefix = "/uploads/";
        int idx = uri.indexOf(prefix);
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String relative = uri.substring(idx + prefix.length());
        if (relative.isBlank() || relative.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = base.resolve(relative.replace("/", java.io.File.separator)).normalize();
        if (!file.startsWith(base)) {
            return ResponseEntity.status(403).build();
        }
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = URLConnection.guessContentTypeFromName(file.getFileName().toString());
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL,
                        CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic().getHeaderValue())
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header("Access-Control-Allow-Origin", "*")
                .body(new FileSystemResource(file));
    }
}
