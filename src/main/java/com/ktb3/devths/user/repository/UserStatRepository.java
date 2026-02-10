package com.ktb3.devths.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.user.domain.entity.UserStat;

import jakarta.persistence.LockModeType;

public interface UserStatRepository extends JpaRepository<UserStat, Long> {
	Optional<UserStat> findByUserId(Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT us FROM UserStat us WHERE us.user.id = :userId")
	Optional<UserStat> findByUserIdForUpdate(@Param("userId") Long userId);
}
