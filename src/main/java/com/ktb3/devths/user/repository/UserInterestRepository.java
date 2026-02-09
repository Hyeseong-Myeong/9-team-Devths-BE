package com.ktb3.devths.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.user.domain.constant.Interests;
import com.ktb3.devths.user.domain.entity.UserInterest;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {
	@Query("SELECT ui.interest FROM UserInterest ui WHERE ui.user.id = :userId")
	List<Interests> findInterestsByUserId(@Param("userId") Long userId);

	@Query("SELECT ui FROM UserInterest ui WHERE ui.user.id IN :userIds")
	List<UserInterest> findByUserIdIn(@Param("userIds") List<Long> userIds);

	@Modifying(clearAutomatically = false)
	@Query("DELETE FROM UserInterest ui WHERE ui.user.id = :userId")
	void deleteAllByUser_Id(Long userId);
}
