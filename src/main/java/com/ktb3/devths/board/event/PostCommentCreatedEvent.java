package com.ktb3.devths.board.event;

public record PostCommentCreatedEvent(
	Long commentId,
	Long postId,
	Long postAuthorId,
	Long commenterId,
	String commenterNickname,
	String previewContent
) {
}
