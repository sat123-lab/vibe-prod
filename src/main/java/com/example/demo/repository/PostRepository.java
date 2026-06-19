package com.example.demo.repository;

import com.example.demo.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository
        extends JpaRepository<Post, Long> {

    // FEED POSTS — fetch author in one query (avoids N+1 on user)

    @Query(
            "SELECT p FROM Post p "
                    + "JOIN FETCH p.user u "
                    + "ORDER BY p.createdAt DESC"
    )
    List<Post> findFeedPosts(Pageable pageable);

    List<Post>
    findAllByOrderByCreatedAtDesc();

    List<Post>
    findAllByOrderByCreatedAtDesc(Pageable pageable);

    // USER POSTS

    List<Post>
    findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    long countByUser_Id(Long userId);

    boolean existsByUser_IdAndVideoUrl(Long userId, String videoUrl);

    List<Post>
    findByVideoUrlIsNotNullOrderByCreatedAtDesc();

    List<Post>
    findByCaptionContainingIgnoreCaseOrderByCreatedAtDesc(
            String caption
    );

    List<Post>
    findByVideoUrlIsNotNullAndCaptionContainingIgnoreCaseOrderByCreatedAtDesc(
            String caption
    );

    @Query("SELECT p.imageData AS imageData, p.imageType AS imageType "
            + "FROM Post p WHERE p.id = :postId AND p.imageData IS NOT NULL")
    Optional<PostImageProjection> findImageProjectionById(@Param("postId") Long postId);
}