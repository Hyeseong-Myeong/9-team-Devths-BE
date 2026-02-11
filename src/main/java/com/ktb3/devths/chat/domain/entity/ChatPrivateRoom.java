package com.ktb3.devths.chat.domain.entity;

import com.ktb3.devths.user.domain.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "chat_private_rooms")
public class ChatPrivateRoom {

	@Id
	private Long id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private ChatRoom room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user1_id", nullable = false)
	private User user1;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user2_id", nullable = false)
	private User user2;

	@Column(name = "dm_key", nullable = false, unique = true)
	private String dmKey;

	public static String computeDmKey(long user1Id, long user2Id) {
		long min = Math.min(user1Id, user2Id);
		long max = Math.max(user1Id, user2Id);
		return min + ":" + max;
	}

	public void setDmKeyFromUsers() {
		this.dmKey = computeDmKey(user1.getId(), user2.getId());
	}
}
