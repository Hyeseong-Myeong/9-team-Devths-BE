package com.ktb3.devths.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.board.domain.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@Query("SELECT c FROM Comment c "
		+ "JOIN FETCH c.user "
		+ "WHERE c.id = :id "
		+ "AND c.isDeleted = false")
	Optional<Comment> findByIdAndIsDeletedFalseWithUser(@Param("id") Long id);

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

	@Query("SELECT c FROM Comment c "
		+ "JOIN FETCH c.post "
		+ "WHERE c.user.id = :userId "
		+ "AND c.isDeleted = false "
		+ "ORDER BY c.id DESC")
	List<Comment> findMyCommentsNotDeleted(
		@Param("userId") Long userId,
		Pageable pageable
	);

	@Query("SELECT c FROM Comment c "
		+ "JOIN FETCH c.post "
		+ "WHERE c.user.id = :userId "
		+ "AND c.isDeleted = false "
		+ "AND c.id < :lastId "
		+ "ORDER BY c.id DESC")
	List<Comment> findMyCommentsNotDeletedAfterCursor(
		@Param("userId") Long userId,
		@Param("lastId") Long lastId,
		Pageable pageable
	);
}
