package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Turns stored relative media paths ({@code /uploads/...}) into absolute URLs
 * clients can load directly — required for Render/production deployments.
 */
@Service
public class MediaUrlService {

    @Value("${app.public-api-url:}")
    private String publicApiUrl;

    public String resolve(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String base = publicApiUrl == null ? "" : publicApiUrl.trim();
        if (base.isEmpty()) {
            return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (trimmed.startsWith("/")) {
            return base + trimmed;
        }
        return base + "/" + trimmed;
    }
}
