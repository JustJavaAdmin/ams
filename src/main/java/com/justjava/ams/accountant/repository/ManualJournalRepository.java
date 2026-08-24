package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.ManualJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ManualJournalRepository extends JpaRepository<ManualJournal, Long> {
	List<ManualJournal> findByOrganizationIdAndStatus(Long organizationId, ManualJournal.JournalStatus status);
	List<ManualJournal> findByOrganizationId(Long organizationId);
	List<ManualJournal> findByOrganizationIdAndJournalDateBetween(Long organizationId, LocalDate startDate, LocalDate endDate);
	List<ManualJournal> findByCreatedBy(String createdBy);
	boolean existsByOrganizationIdAndStatusIn(Long organizationId, Collection<ManualJournal.JournalStatus> statuses);

	@Query("""
			select case when count(j) > 0 then true else false end
			from ManualJournal j
			where j.organization.id = :organizationId
			  and j.status in :statuses
			  and j.journalDate between :startDate and :endDate
			""")
	boolean existsOpenWorkInPeriod(
			@Param("organizationId") Long organizationId,
			@Param("statuses") Collection<ManualJournal.JournalStatus> statuses,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);
}


