package com.ktb3.devths.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.user.domain.entity.Follow;

public interface FollowRepository extends JpaRepository<Follow, Long> {
	boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

	Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

	@Query("SELECT f FROM Follow f JOIN FETCH f.follower WHERE f.following.id = :userId AND f.follower.isWithdraw = false ORDER BY f.id DESC")
	List<Follow> findFollowersByUserId(@Param("userId") Long userId, Pageable pageable);

	@Query("SELECT f FROM Follow f JOIN FETCH f.follower WHERE f.following.id = :userId AND f.follower.isWithdraw = false AND f.id < :lastId ORDER BY f.id DESC")
	List<Follow> findFollowersByUserIdAfterCursor(@Param("userId") Long userId, @Param("lastId") Long lastId, Pageable pageable);

	@Query("SELECT f.following.id FROM Follow f WHERE f.follower.id = :followerId AND f.following.id IN :followingIds")
	List<Long> findFollowingIdsByFollowerIdAndFollowingIdIn(@Param("followerId") Long followerId, @Param("followingIds") List<Long> followingIds);

	@Query("SELECT f FROM Follow f JOIN FETCH f.following WHERE f.follower.id = :userId AND f.following.isWithdraw = false "
		+ "AND (:nickname IS NULL OR f.following.nickname LIKE CONCAT('%', :nickname, '%')) ORDER BY f.id DESC")
	List<Follow> findFollowingsByUserId(@Param("userId") Long userId, @Param("nickname") String nickname, Pageable pageable);

	@Query("SELECT f FROM Follow f JOIN FETCH f.following WHERE f.follower.id = :userId AND f.following.isWithdraw = false "
		+ "AND (:nickname IS NULL OR f.following.nickname LIKE CONCAT('%', :nickname, '%')) AND f.id < :lastId ORDER BY f.id DESC")
	List<Follow> findFollowingsByUserIdAfterCursor(@Param("userId") Long userId, @Param("nickname") String nickname, @Param("lastId") Long lastId, Pageable pageable);
}
