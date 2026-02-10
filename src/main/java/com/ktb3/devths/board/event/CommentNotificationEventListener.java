package com.ktb3.devths.board.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ktb3.devths.notification.service.NotificationService;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentNotificationEventListener {

	private final NotificationService notificationService;
	private final UserRepository userRepository;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handlePostCommentCreated(PostCommentCreatedEvent event) {
		User recipient = userRepository.findByIdAndIsWithdrawFalse(event.postAuthorId())
			.orElse(null);
		if (recipient == null) {
			log.warn("댓글 알림 대상 사용자를 찾을 수 없습니다: postAuthorId={}", event.postAuthorId());
			return;
		}

		try {
			notificationService.createPostCommentNotification(
				recipient,
				event.commenterId(),
				event.postId(),
				event.commenterNickname(),
				event.previewContent()
			);
		} catch (Exception e) {
			log.warn(
				"댓글 알림 생성 실패: postId={}, commentId={}, commenterId={}",
				event.postId(),
				event.commentId(),
				event.commenterId(),
				e
			);
		}
	}
}
