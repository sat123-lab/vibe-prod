package com.example.demo.dto;

import com.example.demo.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight feed payload — excludes nested comments/likes collections so
 * feed responses stay small and fast to serialize.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostFeedDto {

    private Long id;
    private String caption;
    private String imageUrl;
    private String videoUrl;
    private String thumbnailUrl;
    private String type;
    private int likesCount;
    private int commentsCount;
    private LocalDateTime createdAt;
    private UserSummaryDto user;

    public static PostFeedDto from(Post post) {
        if (post == null) {
            return null;
        }
        return PostFeedDto.builder()
                .id(post.getId())
                .caption(post.getCaption())
                .imageUrl(post.getImageUrl())
                .videoUrl(post.getVideoUrl())
                .thumbnailUrl(post.getThumbnailUrl())
                .type(post.getType())
                .likesCount(post.getLikesCount())
                .commentsCount(post.getCommentsCount())
                .createdAt(post.getCreatedAt())
                .user(post.getUser() != null ? UserSummaryDto.from(post.getUser()) : null)
                .build();
    }
}
