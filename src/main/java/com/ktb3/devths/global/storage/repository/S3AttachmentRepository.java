package com.ktb3.devths.global.storage.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.global.storage.domain.constant.RefType;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;

public interface S3AttachmentRepository extends JpaRepository<S3Attachment, Long> {
	Optional<S3Attachment> findTopByRefTypeAndRefIdAndIsDeletedFalseOrderByCreatedAtDesc(RefType refType, Long refId);

	@Query("SELECT s FROM S3Attachment s "
		+ "WHERE s.refType = :refType "
		+ "AND s.refId IN :refIds "
		+ "AND s.isDeleted = false "
		+ "ORDER BY s.createdAt DESC")
	List<S3Attachment> findByRefTypeAndRefIdInAndIsDeletedFalse(
		@Param("refType") RefType refType,
		@Param("refIds") List<Long> refIds
	);

	List<S3Attachment> findByRefTypeAndRefIdAndIsDeletedFalseOrderBySortOrderAsc(RefType refType, Long refId);

	@Query("SELECT s FROM S3Attachment s "
		+ "JOIN FETCH s.user "
		+ "WHERE s.id IN :ids "
		+ "AND s.isDeleted = false")
	List<S3Attachment> findByIdInAndIsDeletedFalse(@Param("ids") List<Long> ids);

	@Query("SELECT s FROM S3Attachment s JOIN FETCH s.user WHERE s.id = :id")
	Optional<S3Attachment> findByIdWithUser(@Param("id") Long id);
}
