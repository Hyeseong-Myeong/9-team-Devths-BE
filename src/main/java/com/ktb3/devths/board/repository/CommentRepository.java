package com.ktb3.devths.board.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.board.domain.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@Query("SELECT c FROM Comment c "
		+ "JOIN FETCH c.user "
		+ "LEFT JOIN FETCH c.parent "
		+ "WHERE c.post.id = :postId "
		+ "ORDER BY c.id ASC")
	List<Comment> findCommentsByPostId(
		@Param("postId") Long postId,
		Pageable pageable
	);

	@Query("SELECT c FROM Comment c "
		+ "JOIN FETCH c.user "
		+ "LEFT JOIN FETCH c.parent "
		+ "WHERE c.post.id = :postId "
		+ "AND c.id > :lastId "
		+ "ORDER BY c.id ASC")
	List<Comment> findCommentsByPostIdAfterCursor(
		@Param("postId") Long postId,
		@Param("lastId") Long lastId,
		Pageable pageable
	);
}
