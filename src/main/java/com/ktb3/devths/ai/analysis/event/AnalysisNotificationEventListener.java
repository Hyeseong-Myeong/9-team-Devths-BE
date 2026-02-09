package com.ktb3.devths.ai.analysis.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ktb3.devths.notification.service.NotificationService;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisNotificationEventListener {

	private final NotificationService notificationService;
	private final UserRepository userRepository;

	@EventListener
	public void handleAnalysisCompleted(AnalysisCompletedEvent event) {
		User user = userRepository.findByIdAndIsWithdrawFalse(event.userId())
			.orElse(null);
		if (user == null) {
			log.warn("분석 완료 알림 대상 사용자를 찾을 수 없습니다: userId={}", event.userId());
			return;
		}

		try {
			notificationService.createAnalysisCompleteNotification(user, event.roomId(), event.summary());
		} catch (Exception e) {
			log.warn("분석 완료 알림 생성 실패: taskId={}, roomId={}", event.taskId(), event.roomId(), e);
		}
	}
}
