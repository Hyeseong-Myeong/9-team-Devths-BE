package com.ktb3.devths.board.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoardEventPublisher {

	private final ApplicationEventPublisher applicationEventPublisher;

	public void publishPostCommentCreated(
		Long commentId,
		Long postId,
		Long postAuthorId,
		Long commenterId,
		String commenterNickname,
		String previewContent
	) {
		applicationEventPublisher.publishEvent(
			new PostCommentCreatedEvent(
				commentId,
				postId,
				postAuthorId,
				commenterId,
				commenterNickname,
				previewContent
			)
		);
	}
}
