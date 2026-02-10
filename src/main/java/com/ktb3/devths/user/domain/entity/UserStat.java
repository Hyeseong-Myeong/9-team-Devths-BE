package com.ktb3.devths.user.domain.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_stats")
public class UserStat {

	@Id
	private Long id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id", nullable = false)
	private User user;

	@Builder.Default
	@Column(name = "follower_count", nullable = false)
	private Long followerCount = 0L;

	@Builder.Default
	@Column(name = "following_count", nullable = false)
	private Long followingCount = 0L;

	@Builder.Default
	@Column(name = "updated_at", nullable = false)
	@LastModifiedDate
	private LocalDateTime updatedAt = LocalDateTime.now();

	public void incrementFollowerCount() {
		this.followerCount++;
	}

	public void incrementFollowingCount() {
		this.followingCount++;
	}

	public void decrementFollowerCount() {
		if (this.followerCount == null || this.followerCount <= 0L) {
			this.followerCount = 0L;
			return;
		}
		this.followerCount--;
	}

	public void decrementFollowingCount() {
		if (this.followingCount == null || this.followingCount <= 0L) {
			this.followingCount = 0L;
			return;
		}
		this.followingCount--;
	}
}
