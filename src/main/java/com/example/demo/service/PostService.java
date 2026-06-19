package com.example.demo.service;

import com.example.demo.dto.PostFeedDto;
import com.example.demo.dto.PostRequest;
import com.example.demo.dto.UserSummaryDto;
import com.example.demo.entity.Post;
import com.example.demo.entity.Reel;
import com.example.demo.entity.User;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ReelRepository;
import com.example.demo.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private FileService fileService;

    @Autowired
    private MediaUrlService mediaUrlService;

    @Autowired
    private ReelRepository reelRepository;

    /**
     * Optional — used to flip a SIGNED_UP referral to ACTIVATED when
     * the referee creates their first post. Keeps the funnel honest;
     * the future rewards engine keys off this transition. Wrapped in a
     * try/catch in {@link #createPost} so it can never block content
     * creation.
     */
    @Autowired(required = false)
    private ReferralService referralService;

    // CREATE POST

    public Post createPost(

            PostRequest request,
            String email,

            MultipartFile image,
            MultipartFile video

    ) throws Exception {

        User user =
                userRepository.findByEmail(email)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        Post post = new Post();

        // CAPTION

        post.setCaption(
                request.getCaption()
        );

        // USER

        post.setUser(user);

        // CREATED TIME

        post.setCreatedAt(
                LocalDateTime.now()
        );

        // IMAGE UPLOAD — store bytes in MySQL (persists across Render redeploys)

        if (image != null &&
                !image.isEmpty()) {

            post.setImageData(image.getBytes());
            post.setImageType(
                    image.getContentType() != null
                            && !image.getContentType().isBlank()
                            ? image.getContentType()
                            : "image/jpeg"
            );
            post.setType("image");
        }

        // VIDEO UPLOAD

        if (video != null &&
                !video.isEmpty()) {

            String videoUrl =
                    fileService.uploadFile(
                            video
                    );

            post.setVideoUrl(videoUrl);

            post.setType("video");
        }

        // THUMBNAIL

        if (request.getThumbnailUrl()
                != null) {

            post.setThumbnailUrl(
                    request.getThumbnailUrl()
            );
        }

        Post saved = postRepository.save(post);

        if (saved.hasStoredImage()) {
            saved.setImageUrl("/api/posts/" + saved.getId() + "/image");
            saved = postRepository.save(saved);
        }
        // Best-effort referral activation. If the user came in via a
        // referral, their first post moves the funnel SIGNED_UP -> ACTIVATED.
        // Idempotent: ReferralService.markActivated only flips on the first call.
        if (referralService != null && user.getReferredByUserId() != null) {
            try {
                referralService.markActivated(user.getId());
            } catch (Exception ignored) {
                // Funnel telemetry is never allowed to break a real post.
            }
        }
        return saved;
    }

    private User resolveViewer(String viewerEmail) {
        if (viewerEmail == null || viewerEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(viewerEmail).orElse(null);
    }

    private boolean canViewUserContent(User author, User viewer) {
        if (author == null) {
            return true;
        }
        if (!author.isPrivateAccount()) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        if (viewer.getId().equals(author.getId())) {
            return true;
        }
        return followRepository.existsByFollowerAndFollowing(viewer, author);
    }

    private List<Post> filterVisiblePosts(List<Post> posts, String viewerEmail) {
        User viewer = resolveViewer(viewerEmail);
        return posts.stream()
                .filter(post -> canViewUserContent(post.getUser(), viewer))
                .toList();
    }

    private List<PostFeedDto> mapVisibleFeedDtos(List<Post> posts, String viewerEmail) {
        return filterVisiblePosts(posts, viewerEmail).stream()
                .map(this::toFeedDto)
                .toList();
    }

    private PostFeedDto toFeedDto(Post post) {
        PostFeedDto dto = PostFeedDto.from(post);
        if (dto == null) {
            return null;
        }
        dto.setImageUrl(resolvePostImageUrl(post));
        dto.setVideoUrl(mediaUrlService.resolve(dto.getVideoUrl()));
        dto.setThumbnailUrl(mediaUrlService.resolve(dto.getThumbnailUrl()));
        if (dto.getUser() != null) {
            dto.getUser().setProfileImage(
                    mediaUrlService.resolve(dto.getUser().getProfileImage()));
        }
        return dto;
    }

    private String resolvePostImageUrl(Post post) {
        if (post.getImageType() != null && !post.getImageType().isBlank()) {
            return mediaUrlService.resolve("/api/posts/" + post.getId() + "/image");
        }
        return mediaUrlService.resolve(post.getImageUrl());
    }

    /**
     * Streams image bytes stored in MySQL for {@code GET /api/posts/{id}/image}.
     */
    public ResponseEntity<byte[]> getPostImage(Long postId) {
        return postRepository.findImageProjectionById(postId)
                .filter(img -> img.getImageData() != null && img.getImageData().length > 0)
                .map(img -> {
                    String contentType = img.getImageType();
                    if (contentType == null || contentType.isBlank()) {
                        contentType = MediaType.IMAGE_JPEG_VALUE;
                    }
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, contentType)
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                            .header("Cross-Origin-Resource-Policy", "cross-origin")
                            .header("Access-Control-Allow-Origin", "*")
                            .body(img.getImageData());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private PostFeedDto toFeedDto(Reel reel, User user) {
        UserSummaryDto author = null;
        if (user != null) {
            author = UserSummaryDto.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .bio(user.getBio())
                    .profileImage(mediaUrlService.resolve(user.getProfileImage()))
                    .privateAccount(user.isPrivateAccount())
                    .build();
        }
        PostFeedDto dto = PostFeedDto.builder()
                .id(reel.getId())
                .caption(reel.getCaption())
                .videoUrl(mediaUrlService.resolve(reel.getVideoUrl()))
                .thumbnailUrl(mediaUrlService.resolve(reel.getThumbnailUrl()))
                .type("video")
                .likesCount(reel.getLikesCount())
                .commentsCount(reel.getCommentsCount())
                .createdAt(reel.getCreatedAt())
                .user(author)
                .build();
        return dto;
    }

    @Transactional(readOnly = true)
    public List<PostFeedDto> getPostsByUserDto(Long userId, String viewerEmail) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User viewer = resolveViewer(viewerEmail);
        // Own profile always shows all posts, even when the account is private.
        if (viewer != null && viewer.getId().equals(target.getId())) {
            return buildUserFeedDtos(userId, target);
        }
        if (!canViewUserContent(target, viewer)) {
            return List.of();
        }

        return buildUserFeedDtos(userId, target);
    }

    private List<PostFeedDto> buildUserFeedDtos(Long userId, User target) {
        List<PostFeedDto> result = new ArrayList<>(
                postRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(this::toFeedDto)
                        .toList());

        Set<String> videoUrls = result.stream()
                .map(PostFeedDto::getVideoUrl)
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toSet());

        for (Reel reel : reelRepository.findByUser(userId, PageRequest.of(0, 100))) {
            if (reel.isDeleted()) continue;
            if (reel.getVideoUrl() != null && videoUrls.contains(reel.getVideoUrl())) {
                continue;
            }
            result.add(toFeedDto(reel, target));
        }

        result.sort(Comparator.comparing(
                PostFeedDto::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    // GET FEED

    @Transactional(readOnly = true)
    public List<PostFeedDto> getFeed(String viewerEmail) {
        return mapVisibleFeedDtos(
                postRepository.findFeedPosts(PageRequest.of(0, 50)),
                viewerEmail
        );
    }

    @Transactional(readOnly = true)
    public List<PostFeedDto> getFeedPage(int page, int size, String viewerEmail) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        return mapVisibleFeedDtos(
                postRepository.findFeedPosts(PageRequest.of(page, safeSize)),
                viewerEmail
        );
    }

    @Transactional(readOnly = true)
    public PostFeedDto getPostDtoById(Long postId) {
        return postRepository.findById(postId)
                .map(this::toFeedDto)
                .orElseGet(() -> reelRepository.findById(postId)
                        .filter(r -> !r.isDeleted())
                        .map(r -> userRepository.findById(r.getUserId())
                                .map(u -> toFeedDto(r, u))
                                .orElseGet(() -> toFeedDto(r, null)))
                        .orElseThrow(() -> new RuntimeException("Post not found")));
    }

    // GET SINGLE POST

    public Post getPostById(
            Long postId
    ) {

        return postRepository.findById(postId)

                .orElseThrow(() ->
                        new RuntimeException(
                                "Post not found"
                        )
                );
    }

    // GET POSTS BY USER

    public long countPostsByUser(Long userId) {
        long posts = postRepository.countByUser_Id(userId);
        long reels = reelRepository.countActiveByUserId(userId);
        return posts + reels - countOverlappingReelVideos(userId);
    }

    private long countOverlappingReelVideos(Long userId) {
        long overlap = 0;
        for (Reel reel : reelRepository.findByUser(userId, PageRequest.of(0, 200))) {
            if (reel.isDeleted()) continue;
            if (reel.getVideoUrl() != null
                    && postRepository.existsByUser_IdAndVideoUrl(userId, reel.getVideoUrl())) {
                overlap++;
            }
        }
        return overlap;
    }

    public List<Post> getPostsByUser(
            Long userId,
            String viewerEmail
    ) {

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Post> posts = postRepository
                .findByUserIdOrderByCreatedAtDesc(userId);

        if (!target.isPrivateAccount()) {
            return posts;
        }

        if (viewerEmail == null || viewerEmail.isBlank()) {
            return List.of();
        }

        User viewer = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (viewer.getId().equals(target.getId())) {
            return posts;
        }

        if (followRepository.existsByFollowerAndFollowing(viewer, target)) {
            return posts;
        }

        return List.of();
    }

    // DELETE POST

    public void deletePost(

            Long postId,
            String email

    ) {

        Post post =
                postRepository.findById(postId)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Post not found"
                                )
                        );

        // SECURITY CHECK

        if (!post.getUser()
                .getEmail()
                .equals(email)) {

            throw new RuntimeException(
                    "You can delete only your posts"
            );
        }

        postRepository.delete(post);
    }

    public List<Post> getReels(String viewerEmail) {
        return filterVisiblePosts(
                postRepository.findByVideoUrlIsNotNullOrderByCreatedAtDesc(),
                viewerEmail
        );
    }

    public List<Post> searchPosts(String query, String viewerEmail) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return filterVisiblePosts(
                postRepository.findByCaptionContainingIgnoreCaseOrderByCreatedAtDesc(
                        query.trim()
                ),
                viewerEmail
        );
    }

    public List<Post> searchReels(String query, String viewerEmail) {
        if (query == null || query.isBlank()) {
            return getReels(viewerEmail);
        }
        return filterVisiblePosts(
                postRepository
                        .findByVideoUrlIsNotNullAndCaptionContainingIgnoreCaseOrderByCreatedAtDesc(
                                query.trim()
                        ),
                viewerEmail
        );
    }
}