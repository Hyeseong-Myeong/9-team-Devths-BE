package com.ktb3.devths.ai.analysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.ai.analysis.dto.request.DocumentAnalysisRequest;
import com.ktb3.devths.ai.analysis.dto.response.DocumentAnalysisResponse;
import com.ktb3.devths.ai.analysis.event.AnalysisEventPublisher;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatRoom;
import com.ktb3.devths.ai.chatbot.repository.AiChatRoomRepository;
import com.ktb3.devths.async.domain.constant.TaskStatus;
import com.ktb3.devths.async.domain.constant.TaskType;
import com.ktb3.devths.async.domain.entity.AsyncTask;
import com.ktb3.devths.async.repository.AsyncTaskRepository;
import com.ktb3.devths.async.service.AsyncTaskService;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAnalysisService {

	private final UserRepository userRepository;
	private final AiChatRoomRepository aiChatRoomRepository;
	private final AsyncTaskRepository asyncTaskRepository;
	private final AsyncTaskService asyncTaskService;
	private final AnalysisEventPublisher analysisEventPublisher;

	@Transactional
	public DocumentAnalysisResponse startAnalysis(Long userId, Long roomId,
		DocumentAnalysisRequest request) {

		User user = userRepository.findByIdAndIsWithdrawFalse(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		AiChatRoom chatRoom = aiChatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.AI_CHATROOM_NOT_FOUND));

		if (!chatRoom.getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.AI_CHATROOM_ACCESS_DENIED);
		}

		validateDocumentInfo(request.resume());
		validateDocumentInfo(request.jobPost());

		List<AsyncTask> existingTasks = asyncTaskRepository.findByReferenceIdAndTaskTypeAndStatusIn(
			roomId,
			TaskType.ANALYSIS,
			List.of(TaskStatus.PENDING, TaskStatus.PROCESSING)
		);

		if (!existingTasks.isEmpty()) {
			AsyncTask existingTask = existingTasks.get(0);
			log.info("이미 진행 중인 분석 작업 존재: taskId={}", existingTask.getId());
			return new DocumentAnalysisResponse(
				existingTask.getId(),
				existingTask.getStatus().name()
			);
		}

		AsyncTask task = asyncTaskService.createTask(user, TaskType.ANALYSIS, roomId);
		analysisEventPublisher.publishRequested(task.getId(), userId, roomId, request);

		return new DocumentAnalysisResponse(task.getId(), TaskStatus.PENDING.name());
	}

	private void validateDocumentInfo(DocumentAnalysisRequest.DocumentInfo documentInfo) {
		if (!documentInfo.hasFileReference() && (documentInfo.text() == null || documentInfo.text()
			.isBlank())) {
			throw new CustomException(ErrorCode.INVALID_FILE_REFERENCE);
		}
	}
}
