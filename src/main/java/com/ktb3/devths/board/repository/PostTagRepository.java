package com.ktb3.devths.board.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.board.domain.entity.PostTag;

public interface PostTagRepository extends JpaRepository<PostTag, Long> {

	@Query("SELECT pt FROM PostTag pt WHERE pt.post.id IN :postIds")
	List<PostTag> findByPostIdIn(@Param("postIds") List<Long> postIds);

	@Modifying
	@Query("DELETE FROM PostTag pt WHERE pt.post.id = :postId")
	void deleteAllByPostId(@Param("postId") Long postId);
}
