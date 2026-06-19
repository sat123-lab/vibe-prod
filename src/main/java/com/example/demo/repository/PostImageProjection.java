package com.example.demo.repository;

/**
 * Lightweight projection — loads only image bytes + MIME type (not the full Post row).
 */
public interface PostImageProjection {

    byte[] getImageData();

    String getImageType();
}
