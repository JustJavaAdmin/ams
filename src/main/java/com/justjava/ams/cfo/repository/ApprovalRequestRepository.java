package com.justjava.ams.cfo.repository;

import com.justjava.ams.cfo.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    Optional<ApprovalRequest> findByEntityTypeAndEntityId(String entityType, Long entityId);
    Optional<ApprovalRequest> findByEntityTypeAndEntityIdAndStatus(String entityType, Long entityId, ApprovalRequest.ApprovalStatus status);
    List<ApprovalRequest> findByOrganizationIdAndStatus(Long organizationId, ApprovalRequest.ApprovalStatus status);
    List<ApprovalRequest> findByAssignedToUserId(Long userId);

    @Query("""
            select case when count(a) > 0 then true else false end
            from ApprovalRequest a
            where a.organization.id = :organizationId
              and a.status in :statuses
              and (a.submittedDate is null or a.submittedDate between :startDate and :endDate)
            """)
    boolean existsOpenWorkInPeriod(
            @Param("organizationId") Long organizationId,
            @Param("statuses") Collection<ApprovalRequest.ApprovalStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

