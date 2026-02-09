package com.ktb3.devths.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.board.domain.entity.Post;

import jakarta.persistence.LockModeType;

public interface PostRepository extends JpaRepository<Post, Long> {

	@Query("SELECT p FROM Post p "
		+ "JOIN FETCH p.user "
		+ "WHERE p.id = :id "
		+ "AND p.isDeleted = false")
	Optional<Post> findByIdAndIsDeletedFalseWithUser(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Post p "
		+ "WHERE p.id = :id "
		+ "AND p.isDeleted = false")
	Optional<Post> findByIdAndIsDeletedFalseForUpdate(@Param("id") Long id);

	@Query("SELECT p FROM Post p "
		+ "JOIN FETCH p.user "
		+ "WHERE p.isDeleted = false "
		+ "AND p.user.isWithdraw = false "
		+ "ORDER BY p.id DESC")
	List<Post> findPostsNotDeleted(Pageable pageable);

	@Query("SELECT p FROM Post p "
		+ "WHERE p.user.id = :userId "
		+ "AND p.isDeleted = false "
		+ "ORDER BY p.id DESC")
	List<Post> findMyPostsNotDeleted(@Param("userId") Long userId, Pageable pageable);

	@Query("SELECT p FROM Post p "
		+ "WHERE p.user.id = :userId "
		+ "AND p.isDeleted = false "
		+ "AND p.id < :lastId "
		+ "ORDER BY p.id DESC")
	List<Post> findMyPostsNotDeletedAfterCursor(
		@Param("userId") Long userId,
		@Param("lastId") Long lastId,
		Pageable pageable
	);

	@Query("SELECT p FROM Post p "
		+ "JOIN FETCH p.user "
		+ "WHERE p.isDeleted = false "
		+ "AND p.user.isWithdraw = false "
		+ "AND p.id < :lastId "
		+ "ORDER BY p.id DESC")
	List<Post> findPostsNotDeletedAfterCursor(
		@Param("lastId") Long lastId,
		Pageable pageable
	);

	@Query("SELECT p FROM Post p "
		+ "JOIN FETCH p.user "
		+ "WHERE p.isDeleted = false "
		+ "AND p.user.isWithdraw = false "
		+ "AND (p.title LIKE CONCAT('%', :keyword, '%') OR p.content LIKE CONCAT('%', :keyword, '%')) "
		+ "ORDER BY p.id DESC")
	List<Post> findPostsByKeywordNotDeleted(
		@Param("keyword") String keyword,
		Pageable pageable
	);

	@Query("SELECT p FROM Post p "
		+ "JOIN FETCH p.user "
		+ "WHERE p.isDeleted = false "
		+ "AND p.user.isWithdraw = false "
		+ "AND (p.title LIKE CONCAT('%', :keyword, '%') OR p.content LIKE CONCAT('%', :keyword, '%')) "
		+ "AND p.id < :lastId "
		+ "ORDER BY p.id DESC")
	List<Post> findPostsByKeywordNotDeletedAfterCursor(
		@Param("keyword") String keyword,
		@Param("lastId") Long lastId,
		Pageable pageable
	);
}
