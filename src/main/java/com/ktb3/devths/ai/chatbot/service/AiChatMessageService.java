package com.ktb3.devths.ai.chatbot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.ai.analysis.domain.AiOcrResult;
import com.ktb3.devths.ai.analysis.repository.AiOcrResultRepository;
import com.ktb3.devths.ai.chatbot.domain.constant.InterviewStatus;
import com.ktb3.devths.ai.chatbot.domain.constant.MessageRole;
import com.ktb3.devths.ai.chatbot.domain.constant.MessageType;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatInterview;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatMessage;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatRoom;
import com.ktb3.devths.ai.chatbot.dto.request.FastApiChatContext;
import com.ktb3.devths.ai.chatbot.dto.request.FastApiChatRequest;
import com.ktb3.devths.ai.chatbot.repository.AiChatInterviewRepository;
import com.ktb3.devths.ai.chatbot.repository.AiChatMessageRepository;
import com.ktb3.devths.ai.chatbot.repository.AiChatRoomRepository;
import com.ktb3.devths.ai.client.FastApiClient;
import com.ktb3.devths.ai.constant.AiModel;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.util.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatMessageService {

	private final AiChatMessageRepository aiChatMessageRepository;
	private final AiChatRoomRepository aiChatRoomRepository;
	private final AiChatInterviewRepository aiChatInterviewRepository;
	private final AiOcrResultRepository aiOcrResultRepository;
	private final FastApiClient fastApiClient;

	@Transactional
	public AiChatMessage saveReportMessage(Long roomId, String content, Map<String, Object> metadata) {
		AiChatRoom room = aiChatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.AI_CHATROOM_NOT_FOUND));

		AiChatMessage message = AiChatMessage.builder()
			.room(room)
			.role(MessageRole.ASSISTANT)
			.type(MessageType.REPORT)
			.content(content)
			.metadata(metadata)
			.build();

		return aiChatMessageRepository.save(message);
	}

	public Flux<String> streamChatResponse(Long userId, Long roomId, String content, AiModel model,
		Long interviewId) {
		// 현재 스레드의 Authentication 캡처 (여기서는 SecurityContext가 존재함)
		Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

		log.info("AI 챗봇 스트리밍 시작: roomId={}, userId={}, model={}, interviewId={}",
			LogSanitizer.sanitize(String.valueOf(roomId)),
			LogSanitizer.sanitize(String.valueOf(userId)),
			model,
			interviewId);

		AiChatRoom room = aiChatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.AI_CHATROOM_NOT_FOUND));

		if (!room.getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.AI_CHATROOM_ACCESS_DENIED);
		}

		AiChatInterview interview = null;
		FastApiChatContext context = FastApiChatContext.createNormalMode();

		if (interviewId != null) {
			interview = aiChatInterviewRepository.findById(interviewId)
				.orElseThrow(() -> new CustomException(ErrorCode.INTERVIEW_NOT_FOUND));

			if (interview.getStatus() == InterviewStatus.COMPLETED) {
				throw new CustomException(ErrorCode.INTERVIEW_COMPLETED);
			}

		}

		MessageType messageType = interviewId != null ? MessageType.INTERVIEW : MessageType.NORMAL;
		AiChatInterview finalInterview = interview;

		saveUserMessage(room, content, messageType, interview);

		if (interviewId != null) {
			AiOcrResult ocrResult = aiOcrResultRepository.findByRoomId(roomId).orElse(null);
			String resumeOcr = ocrResult != null ? ocrResult.getResumeOcr() : "";
			String jobPostingOcr = ocrResult != null ? ocrResult.getJobPostingOcr() : "";

			context = new FastApiChatContext(
				MessageType.INTERVIEW.name().toLowerCase(),
				resumeOcr,
				jobPostingOcr,
				interview.getInterviewType().name().toLowerCase(),
				interview.getCurrentQuestionCount() + 1  // 다음 질문 번호 전달 (실제 증가는 성공 후)
			);
		}

		FastApiChatRequest request = new FastApiChatRequest(
			model.name().toLowerCase(),
			roomId,
			userId,
			content,
			interviewId,
			context
		);

		StringBuilder fullResponse = new StringBuilder();
		AtomicBoolean hasError = new AtomicBoolean(false);
		AtomicBoolean isFastApiError = new AtomicBoolean(false);  // FastAPI 에러 플래그 추가

		return fastApiClient.streamChatResponse(request)
			.doOnNext(chunk -> {
				// 에러 청크 감지
				if (chunk.startsWith("[ERROR]")) {
					isFastApiError.set(true);
					hasError.set(true);

					// [ERROR] 접두사 제거 후 fallback 메시지만 누적
					String fallbackMessage = chunk.substring("[ERROR]".length());
					fullResponse.append(fallbackMessage);

					log.warn("FastAPI 에러 청크 감지: roomId={}, interviewId={}, fallback='{}'",
						roomId, interviewId, fallbackMessage);
				} else {
					// 정상 청크
					fullResponse.append(chunk);
				}

				log.debug("청크 수신: length={}", chunk.length());
			})
			.filter(chunk -> !chunk.startsWith("[ERROR]"))  // ← 에러 청크는 클라이언트에 전송하지 않음
			.doOnComplete(() -> {
				if (!hasError.get()) {
					try {
						// SecurityContext 복원
						SecurityContextHolder.getContext().setAuthentication(currentAuth);

						// 면접 모드이고 에러가 아닌 경우에만 질문 개수 증가
						if (finalInterview != null && !isFastApiError.get()) {
							finalInterview.incrementQuestionCount();
							log.info("면접 질문 개수 증가: interviewId={}, count={}",
								finalInterview.getId(), finalInterview.getCurrentQuestionCount());
						}
						saveAssistantMessage(room, fullResponse.toString(), model, messageType, finalInterview);
						log.info("AI 챗봇 스트리밍 완료: roomId={}, totalLength={}",
							LogSanitizer.sanitize(String.valueOf(roomId)),
							fullResponse.length());
					} catch (Exception e) {
						log.error("어시스턴트 메시지 저장 실패: roomId={}, length={}",
							LogSanitizer.sanitize(String.valueOf(roomId)),
							fullResponse.length(),
							e);
					} finally {
						// SecurityContext 정리 (메모리 누수 방지)
						SecurityContextHolder.clearContext();
					}
				} else if (isFastApiError.get()) {
					// FastAPI 에러 - fallback 메시지만 저장 (질문 개수 증가 안 함)
					try {
						SecurityContextHolder.getContext().setAuthentication(currentAuth);

						Map<String, Object> metadata = new HashMap<>();
						metadata.put("model", model.name());
						metadata.put("fastapi_error", true);
						metadata.put("error_type", "PARSE_FAILED");

						saveAssistantMessage(room, fullResponse.toString(), metadata, messageType, finalInterview);

						log.warn("FastAPI 에러 응답 저장 완료 (질문 개수 증가 안 함): roomId={}, interviewId={}, count={}",
							roomId, interviewId,
							finalInterview != null ? finalInterview.getCurrentQuestionCount() : null);
					} catch (Exception e) {
						log.error("FastAPI 에러 응답 저장 실패", e);
					} finally {
						SecurityContextHolder.clearContext();
					}
				}
			})
			.doOnError(e -> {
				hasError.set(true);
				log.error("AI 챗봇 스트리밍 실패: roomId={}", LogSanitizer.sanitize(String.valueOf(roomId)), e);

				if (!fullResponse.isEmpty()) {
					try {
						// SecurityContext 복원
						SecurityContextHolder.getContext().setAuthentication(currentAuth);

						Map<String, Object> metadata = new HashMap<>();
						metadata.put("model", model.name());
						metadata.put("incomplete", true);
						metadata.put("error", e.getMessage());
						saveAssistantMessage(room, fullResponse.toString(), metadata, messageType, finalInterview);
					} catch (Exception ex) {
						log.error("부분 응답 저장 실패: roomId={}",
							LogSanitizer.sanitize(String.valueOf(roomId)), ex);
					} finally {
						SecurityContextHolder.clearContext();
					}
				}
			})
			.onErrorResume(e -> Flux.just("ERROR:" + e.getMessage()));
	}

	@Transactional
	public AiChatMessage saveUserMessage(AiChatRoom room, String content, MessageType type,
		AiChatInterview interview) {
		AiChatMessage message = AiChatMessage.builder()
			.room(room)
			.interview(interview)
			.role(MessageRole.USER)
			.type(type)
			.content(content)
			.metadata(null)
			.build();

		return aiChatMessageRepository.save(message);
	}

	@Transactional
	public AiChatMessage saveAssistantMessage(AiChatRoom room, String content, AiModel model, MessageType type,
		AiChatInterview interview) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("model", model.name());

		return saveAssistantMessage(room, content, metadata, type, interview);
	}

	@Transactional
	public AiChatMessage saveAssistantMessage(AiChatRoom room, String content, Map<String, Object> metadata,
		MessageType type, AiChatInterview interview) {
		AiChatMessage message = AiChatMessage.builder()
			.room(room)
			.interview(interview)
			.role(MessageRole.ASSISTANT)
			.type(type)
			.content(content)
			.metadata(metadata)
			.build();

		return aiChatMessageRepository.save(message);
	}
}
