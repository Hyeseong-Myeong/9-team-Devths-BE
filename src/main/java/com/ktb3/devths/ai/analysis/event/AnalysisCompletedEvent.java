package com.ktb3.devths.ai.analysis.event;

import java.util.Map;

public record AnalysisCompletedEvent(
	Long taskId,
	Long userId,
	Long roomId,
	String summary,
	Map<String, Object> resultMetadata
) {
}
