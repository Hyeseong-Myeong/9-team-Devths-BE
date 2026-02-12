package com.ktb3.devths.ai.chatbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ktb3.devths.ai.analysis.repository.AiOcrResultRepository;
import com.ktb3.devths.ai.chatbot.domain.constant.InterviewStatus;
import com.ktb3.devths.ai.chatbot.domain.constant.InterviewType;
import com.ktb3.devths.ai.chatbot.domain.constant.MessageRole;
import com.ktb3.devths.ai.chatbot.domain.constant.MessageType;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatInterview;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatMessage;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatRoom;
import com.ktb3.devths.ai.chatbot.dto.request.FastApiInterviewEvaluationRequest;
import com.ktb3.devths.ai.chatbot.repository.AiChatInterviewRepository;
import com.ktb3.devths.ai.chatbot.repository.AiChatMessageRepository;
import com.ktb3.devths.ai.client.FastApiClient;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.util.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatInterviewService {

	private final AiChatInterviewRepository aiChatInterviewRepository;
	private final AiChatMessageRepository aiChatMessageRepository;
	private final AiOcrResultRepository aiOcrResultRepository;
	private final FastApiClient fastApiClient;
	private final TransactionTemplate transactionTemplate;

	@Transactional
	public AiChatInterview startInterview(AiChatRoom room, InterviewType interviewType) {
		// 진행 중인 면접이 있는지 확인
		var existingInterview = aiChatInterviewRepository.findByRoomIdAndStatus(
			room.getId(), InterviewStatus.IN_PROGRESS);

		if (existingInterview.isPresent()) {
			AiChatInterview interview = existingInterview.get();

			// 면접 타입이 다르면 에러 발생
			if (interview.getInterviewType() != interviewType) {
				throw new CustomException(ErrorCode.INTERVIEW_TYPE_MISMATCH);
			}

			// 동일한 타입이면 기존 면접 반환 (멱등성)
			log.info("기존 진행 중인 면접 재사용: interviewId={}, roomId={}, type={}, currentCount={}",
				interview.getId(), room.getId(), interviewType, interview.getCurrentQuestionCount());

			return interview;
		}

		// 진행 중인 면접이 없으면 새로 생성
		AiChatInterview interview = AiChatInterview.builder()
			.room(room)
			.interviewType(interviewType)
			.currentQuestionCount(0)
			.status(InterviewStatus.IN_PROGRESS)
			.build();

		AiChatInterview savedInterview = aiChatInterviewRepository.save(interview);
		log.info("새 면접 시작: interviewId={}, roomId={}, type={}", savedInterview.getId(), room.getId(), interviewType);

		return savedInterview;
	}

	@Transactional(readOnly = true)
	public AiChatInterview getInterview(Long interviewId) {
		return aiChatInterviewRepository.findById(interviewId)
			.orElseThrow(() -> new CustomException(ErrorCode.INTERVIEW_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public Optional<AiChatInterview> getCurrentInterview(Long roomId) {
		return aiChatInterviewRepository.findByRoomIdAndStatus(roomId, InterviewStatus.IN_PROGRESS);
	}

	public Flux<String> evaluateInterview(Long interviewId, boolean retry) {
		// 현재 스레드의 Authentication 캡처 (여기서는 SecurityContext가 존재함)
		Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

		AiChatInterview interview = getInterview(interviewId);
		AiChatRoom room = interview.getRoom();

		List<AiChatMessage> messages = aiChatMessageRepository.findAll().stream()
			.filter(msg -> msg.getInterview() != null && msg.getInterview().getId().equals(interviewId))
			.sorted((a, b) -> a.getId().compareTo(b.getId()))
			.collect(Collectors.toList());

		if (messages.isEmpty()) {
			throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
		}

		// ASSISTANT(질문) → USER(답변) 순서로 페어링하여 Q&A 쌍 생성
		List<FastApiInterviewEvaluationRequest.ContextEntry> context = new java.util.ArrayList<>();
		for (int i = 0; i < messages.size() - 1; i++) {
			AiChatMessage current = messages.get(i);
			AiChatMessage next = messages.get(i + 1);
			if (current.getRole() == MessageRole.ASSISTANT && next.getRole() == MessageRole.USER) {
				context.add(new FastApiInterviewEvaluationRequest.ContextEntry(
					current.getContent(),
					next.getContent()
				));
			}
		}

		// roomId와 userId 추출
		Long roomId = room.getId();
		Long userId = room.getUser().getId();

		FastApiInterviewEvaluationRequest request = new FastApiInterviewEvaluationRequest(
			"면접 리포트 생성 (면접 종료)",
			new FastApiInterviewEvaluationRequest.Value(
				context,
				retry,
				roomId,
				interviewId,
				userId,
				interview.getInterviewType().name().toLowerCase()
			)
		);

		log.info("면접 평가 시작: interviewId={}, roomId={}, userId={}, contextCount={}",
			interviewId, roomId, userId, context.size());

		// 전체 평가 결과 누적용
		StringBuilder fullEvaluation = new StringBuilder();
		AtomicBoolean hasError = new AtomicBoolean(false);

		return fastApiClient.streamInterviewEvaluation(request)
			.doOnNext(chunk -> {
				fullEvaluation.append(chunk);
				log.debug("평가 결과 청크 수신: length={}", chunk.length());
			})
			.doOnComplete(() -> {
				if (!hasError.get() && fullEvaluation.length() > 0) {
					try {
						// SecurityContext 복원
						SecurityContextHolder.getContext().setAuthentication(currentAuth);

						// TransactionTemplate을 사용하여 명시적으로 트랜잭션 시작
						transactionTemplate.executeWithoutResult(status -> {
							try {
								// 1. 평가 결과 메시지 저장
								saveEvaluationMessage(room, interview, fullEvaluation.toString());

								// 2. 면접 상태를 COMPLETED로 변경
								AiChatInterview interviewEntity = aiChatInterviewRepository.findById(interviewId)
									.orElseThrow(() -> new CustomException(ErrorCode.INTERVIEW_NOT_FOUND));
								interviewEntity.complete();

								log.info("면접 평가 완료 및 저장: interviewId={}, evaluationLength={}",
									interviewId, fullEvaluation.length());
							} catch (Exception e) {
								log.error("트랜잭션 내 처리 실패: interviewId={}", interviewId, e);
								status.setRollbackOnly();
								throw e;
							}
						});
					} catch (Exception e) {
						log.error("면접 평가 완료 처리 실패: interviewId={}", interviewId, e);
					} finally {
						// SecurityContext 정리 (메모리 누수 방지)
						SecurityContextHolder.clearContext();
					}
				}
			})
			.doOnError(e -> {
				hasError.set(true);
				log.error("면접 평가 스트리밍 실패: interviewId={}",
					LogSanitizer.sanitize(String.valueOf(interviewId)), e);
			});
	}

	/**
	 * 면접 평가 결과를 메시지로 저장
	 */
	private void saveEvaluationMessage(AiChatRoom room, AiChatInterview interview, String evaluationContent) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("interview_type", interview.getInterviewType().name());
		metadata.put("evaluation", true);

		AiChatMessage message = AiChatMessage.builder()
			.room(room)
			.interview(interview)
			.role(MessageRole.ASSISTANT)
			.type(MessageType.INTERVIEW)
			.content(evaluationContent)
			.metadata(metadata)
			.build();

		aiChatMessageRepository.save(message);
		log.debug("면접 평가 메시지 저장 완료: interviewId={}, messageId={}",
			interview.getId(), message.getId());
	}
}
