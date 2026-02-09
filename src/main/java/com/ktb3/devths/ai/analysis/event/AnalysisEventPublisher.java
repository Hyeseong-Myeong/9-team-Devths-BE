package com.ktb3.devths.ai.analysis.event;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.ktb3.devths.ai.analysis.dto.request.DocumentAnalysisRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AnalysisEventPublisher {

	private final ApplicationEventPublisher applicationEventPublisher;

	public void publishRequested(Long taskId, Long userId, Long roomId, DocumentAnalysisRequest request) {
		applicationEventPublisher.publishEvent(new AnalysisRequestedEvent(taskId, userId, roomId, request));
	}

	public void publishCompleted(
		Long taskId,
		Long userId,
		Long roomId,
		String summary,
		Map<String, Object> resultMetadata
	) {
		applicationEventPublisher.publishEvent(
			new AnalysisCompletedEvent(taskId, userId, roomId, summary, resultMetadata)
		);
	}
}
