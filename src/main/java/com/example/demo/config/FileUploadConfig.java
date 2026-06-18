package com.example.demo.config;

import org.springframework.context.annotation.Configuration;

/**
 * Upload serving is handled by {@link com.example.demo.controller.PublicUploadsController}
 * so Spring Security {@code permitAll} / {@code ignoring} apply reliably.
 */
@Configuration
public class FileUploadConfig {
}
