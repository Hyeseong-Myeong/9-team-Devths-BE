package com.ktb3.devths.ai.analysis.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ktb3.devths.ai.analysis.dto.request.DocumentAnalysisRequest;
import com.ktb3.devths.ai.analysis.dto.request.FastApiAnalysisRequest;
import com.ktb3.devths.ai.analysis.dto.response.FastApiAnalysisResponse;
import com.ktb3.devths.ai.analysis.dto.response.FastApiTaskStatusResponse;
import com.ktb3.devths.ai.analysis.event.AnalysisEventPublisher;
import com.ktb3.devths.ai.analysis.event.AnalysisRequestedEvent;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatMessage;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatRoom;
import com.ktb3.devths.ai.chatbot.repository.AiChatRoomRepository;
import com.ktb3.devths.ai.chatbot.service.AiChatMessageService;
import com.ktb3.devths.ai.client.FastApiClient;
import com.ktb3.devths.async.domain.constant.TaskStatus;
import com.ktb3.devths.async.service.AsyncTaskService;
import com.ktb3.devths.global.config.properties.FastApiProperties;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.repository.S3AttachmentRepository;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.global.util.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAnalysisProcessor {

	private final AsyncTaskService asyncTaskService;
	private final FastApiClient fastApiClient;
	private final FastApiProperties fastApiProperties;
	private final AiChatMessageService aiChatMessageService;
	private final AiChatRoomRepository aiChatRoomRepository;
	private final S3AttachmentRepository s3AttachmentRepository;
	private final AiOcrResultService aiOcrResultService;
	private final S3StorageService s3StorageService;
	private final AnalysisEventPublisher analysisEventPublisher;

	@Async("taskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleAnalysisRequested(AnalysisRequestedEvent event) {
		Long taskId = event.taskId();
		Long userId = event.userId();
		Long roomId = event.roomId();
		DocumentAnalysisRequest request = event.request();

		try {
			String threadName = Thread.currentThread().getName();
			log.info("비동기 분석 처리 시작: taskId={}, roomId={}, thread={}", taskId, roomId, threadName);

			// 1. 상태 업데이트 (독립 트랜잭션)
			asyncTaskService.updateStatus(taskId, TaskStatus.PROCESSING);

			// 2. FastAPI 요청 준비 (트랜잭션 불필요 - Fetch Join으로 User 이미 로딩됨)
			FastApiAnalysisRequest fastApiRequest = buildFastApiRequest(taskId, userId, roomId, request);

			// 3. FastAPI 분석 요청 (트랜잭션 없음)
			FastApiAnalysisResponse analysisResponse = fastApiClient.requestAnalysis(fastApiRequest);

			// 4. 폴링 (트랜잭션 없음)
			FastApiTaskStatusResponse statusResponse = pollFastApiTask(taskId);

			// 5. 성공/실패 처리 (독립 트랜잭션)
			if ("completed".equalsIgnoreCase(statusResponse.status())) {
				String summary = handleAnalysisSuccess(taskId, roomId, statusResponse);
				Map<String, Object> metadata = new HashMap<>();
				metadata.put("fastApiTaskId", statusResponse.taskId());
				analysisEventPublisher.publishCompleted(taskId, userId, roomId, summary, metadata);
			} else {
				String reason = "FastAPI에서 분석이 완료되지 않았습니다: "
					+ LogSanitizer.sanitize(statusResponse.status());
				handleAnalysisFailure(taskId, reason);
			}

		} catch (CustomException e) {
			log.error("분석 처리 중 오류 발생: taskId={}", taskId, e);
			handleAnalysisFailure(taskId, e.getErrorCode().getMessage());
		} catch (Exception e) {
			log.error("분석 처리 중 예상치 못한 오류 발생: taskId={}", taskId, e);
			String reason = "분석 처리 중 오류가 발생했습니다";
			handleAnalysisFailure(taskId, reason);
		}
	}

	private FastApiAnalysisRequest buildFastApiRequest(Long taskId, Long userId, Long roomId,
		DocumentAnalysisRequest request) {

		FastApiAnalysisRequest.FastApiDocumentInfo resumeInfo = buildDocumentInfo(request.resume(), userId);
		FastApiAnalysisRequest.FastApiDocumentInfo jobPostingInfo = buildDocumentInfo(request.jobPost(),
			userId);

		return new FastApiAnalysisRequest(
			taskId,
			request.model().name().toLowerCase(),
			roomId,
			userId,
			resumeInfo,
			jobPostingInfo
		);
	}

	private FastApiAnalysisRequest.FastApiDocumentInfo buildDocumentInfo(
		DocumentAnalysisRequest.DocumentInfo documentInfo, Long userId) {

		String s3Key = documentInfo.s3Key();
		String fileType = documentInfo.fileType();
		Long fileId = documentInfo.fileId();

		if (fileId != null) {
			S3Attachment attachment = s3AttachmentRepository.findByIdWithUser(fileId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_FILE_REFERENCE));

			if (!attachment.getUser().getId().equals(userId)) {
				throw new CustomException(ErrorCode.ACCESS_DENIED);
			}

			s3Key = attachment.getS3Key();
			fileType = attachment.getMimeType();
		}

		String publicUrl = (s3Key != null) ? s3StorageService.getPublicUrl(s3Key) : null;

		return new FastApiAnalysisRequest.FastApiDocumentInfo(
			fileId,
			publicUrl,
			fileType,
			documentInfo.text()
		);
	}

	private FastApiTaskStatusResponse pollFastApiTask(Long taskId) {
		int maxAttempts = fastApiProperties.getMaxPollAttempts();
		int pollInterval = fastApiProperties.getPollInterval();

		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			try {
				FastApiTaskStatusResponse response = fastApiClient.pollTaskStatus(taskId);

				if ("completed".equalsIgnoreCase(response.status())) {
					log.info("FastAPI 작업 완료: taskId={}", taskId);
					return response;
				} else if ("failed".equalsIgnoreCase(response.status())) {
					throw new CustomException(ErrorCode.ANALYSIS_FAILED);
				}

				log.debug("FastAPI 작업 진행 중: taskId={}, status={}, attempt={}/{}",
					taskId, response.status(), attempt + 1, maxAttempts);
				Thread.sleep(pollInterval);

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CustomException(ErrorCode.ANALYSIS_FAILED);
			} catch (CustomException e) {
				if (attempt < maxAttempts - 1) {
					try {
						Thread.sleep(pollInterval);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw new CustomException(ErrorCode.ANALYSIS_FAILED);
					}
				} else {
					throw e;
				}
			}
		}

		throw new CustomException(ErrorCode.FASTAPI_TIMEOUT);
	}

	protected String handleAnalysisSuccess(Long taskId, Long roomId, FastApiTaskStatusResponse statusResponse) {
		try {
			AiChatRoom chatRoom = aiChatRoomRepository.findByIdAndIsDeletedFalse(roomId)
				.orElse(null);

			if (chatRoom == null) {
				log.warn("채팅방이 삭제되었습니다. 메시지 저장을 건너뜁니다: roomId={}", roomId);
				Map<String, Object> result = new HashMap<>();
				result.put("fastApiTaskId", statusResponse.taskId());
				String summary = "채팅방이 삭제되어 결과를 저장할 수 없습니다";
				result.put("summary", summary);
				asyncTaskService.updateResult(taskId, result);
				return summary;
			}

			String summary = extractSummary(statusResponse.result());

			Map<String, Object> metadata = new HashMap<>();
			metadata.put("summary", summary);
			metadata.put("taskId", taskId);
			metadata.put("analysisType", "DOCUMENT_ANALYSIS");

			AiChatMessage message = aiChatMessageService.saveReportMessage(
				roomId,
				formatAnalysisResult(statusResponse.result()),
				metadata
			);

			chatRoom.updateTitle(summary);
			aiChatRoomRepository.save(chatRoom);

			saveOcrResultIfPresent(chatRoom, statusResponse.result());

			Map<String, Object> result = new HashMap<>();
			result.put("messageId", message.getId());
			result.put("fastApiTaskId", statusResponse.taskId());
			result.put("summary", summary);
			result.put("analysisData", statusResponse.result());

			asyncTaskService.updateResult(taskId, result);

			log.info("분석 완료 및 결과 저장 성공: taskId={}, messageId={}", taskId, message.getId());
			return summary;

		} catch (Exception e) {
			log.error("분석 성공 처리 중 오류 발생: taskId={}", taskId, e);
			throw e;
		}
	}

	protected void handleAnalysisFailure(Long taskId, String reason) {
		asyncTaskService.markAsFailed(taskId, reason);
	}

	/**
	 * FastAPI 응답에서 summary 추출 (Graceful Fallback 방식)
	 * 1순위: result.summary (신규 구조)
	 * 2순위: result.resume_analysis.summary (레거시 구조)
	 * 3순위: 기본값 반환
	 */
	private String extractSummary(Map<String, Object> result) {
		if (result == null) {
			return "이력서 및 포트폴리오 분석 결과";
		}

		// 1순위: 새로운 구조 (result.summary)
		Object summary = result.get("summary");
		if (summary != null) {
			log.debug("summary 추출 성공 (신규 구조)");
			return summary.toString();
		}

		// 2순위: 기존 구조 (result.resume_analysis.summary) - Fallback
		Object resumeAnalysis = result.get("resume_analysis");
		if (resumeAnalysis instanceof Map) {
			Object legacySummary = ((Map<?, ?>)resumeAnalysis).get("summary");
			if (legacySummary != null) {
				log.warn("summary 추출 (레거시 구조) - FastAPI 업데이트 필요");
				return legacySummary.toString();
			}
		}

		// 3순위: 기본값
		log.warn("summary를 찾을 수 없음 - 기본값 반환");
		return "이력서 및 포트폴리오 분석 결과";
	}

	private String formatAnalysisResult(Map<String, Object> result) {
		if (result == null) {
			return "분석 결과를 생성할 수 없습니다.";
		}

		StringBuilder content = new StringBuilder();
		content.append("## 이력서 및 포트폴리오 분석 결과\n\n");

		Object resumeAnalysis = result.get("resume_analysis");
		if (resumeAnalysis instanceof Map) {
			content.append("### 이력서 분석\n");
			Map<?, ?> analysis = (Map<?, ?>)resumeAnalysis;

			if (analysis.containsKey("strengths")) {
				content.append("\n**강점:**\n");
				appendList(content, analysis.get("strengths"));
			}

			if (analysis.containsKey("weaknesses")) {
				content.append("\n**개선점:**\n");
				appendList(content, analysis.get("weaknesses"));
			}

			if (analysis.containsKey("suggestions")) {
				content.append("\n**제안사항:**\n");
				appendList(content, analysis.get("suggestions"));
			}
		}

		Object postingAnalysis = result.get("posting_analysis");
		if (postingAnalysis instanceof Map) {
			content.append("\n### 채용공고 분석\n");
			Map<?, ?> analysis = (Map<?, ?>)postingAnalysis;

			if (analysis.containsKey("company")) {
				content.append("\n**기업:** ").append(analysis.get("company")).append("\n");
			}

			if (analysis.containsKey("position")) {
				content.append("**포지션:** ").append(analysis.get("position")).append("\n");
			}

			if (analysis.containsKey("required_skills")) {
				content.append("\n**필수 기술:**\n");
				appendList(content, analysis.get("required_skills"));
			}

			if (analysis.containsKey("preferred_skills")) {
				content.append("\n**우대 기술:**\n");
				appendList(content, analysis.get("preferred_skills"));
			}
		}

		return content.toString();
	}

	private void appendList(StringBuilder content, Object listObj) {
		if (listObj instanceof List) {
			List<?> list = (List<?>)listObj;
			for (Object item : list) {
				content.append("- ").append(item).append("\n");
			}
		}
	}

	private void saveOcrResultIfPresent(AiChatRoom chatRoom, Map<String, Object> result) {
		try {
			if (result == null) {
				return;
			}

			Object resumeOcrObj = result.get("resume_ocr");
			Object jobPostingOcrObj = result.get("job_posting_ocr");

			if (resumeOcrObj == null || jobPostingOcrObj == null) {
				log.warn("OCR 데이터가 없습니다. OCR 저장을 건너뜁니다: roomId={}", chatRoom.getId());
				return;
			}

			String resumeOcr = resumeOcrObj.toString();
			String jobPostingOcr = jobPostingOcrObj.toString();

			aiOcrResultService.saveOcrResult(chatRoom, resumeOcr, jobPostingOcr);
			log.info("OCR 결과 저장 성공: roomId={}", chatRoom.getId());

		} catch (Exception e) {
			log.error("OCR 결과 저장 중 오류 발생 (분석 결과는 정상 처리됨): roomId={}", chatRoom.getId(), e);
		}
	}
}
