package com.ktb3.devths.ai.analysis.event;

import com.ktb3.devths.ai.analysis.dto.request.DocumentAnalysisRequest;

public record AnalysisRequestedEvent(
	Long taskId,
	Long userId,
	Long roomId,
	DocumentAnalysisRequest request
) {
}
