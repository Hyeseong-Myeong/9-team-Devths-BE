package com.ktb3.devths.ai.analysis.service;

import org.springframework.stereotype.Service;

import com.ktb3.devths.ai.analysis.dto.request.DocumentAnalysisRequest;
import com.ktb3.devths.ai.analysis.dto.response.DocumentAnalysisResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentAnalysisFacade {

	private final DocumentAnalysisService documentAnalysisService;

	public DocumentAnalysisResponse startAnalysis(Long userId, Long roomId, DocumentAnalysisRequest request) {
		return documentAnalysisService.startAnalysis(userId, roomId, request);
	}
}
