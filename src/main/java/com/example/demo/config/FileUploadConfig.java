package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Maps {@code /uploads/**} to {@code file.upload-dir} on disk.
 *
 * <p>On Render, set {@code UPLOAD_DIR=/var/social/uploads} and mount a persistent
 * disk at that path so uploaded media survives redeploys.</p>
 */
@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = uploadPath.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .resourceChain(true);
    }
}
