package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.GeneralLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface GeneralLedgerRepository extends JpaRepository<GeneralLedger, Long> {
    List<GeneralLedger> findByAccountIdOrderByTransactionDateDesc(Long accountId);
    List<GeneralLedger> findByJournalNumber(String journalNumber);
    List<GeneralLedger> findBySourceTypeAndSourceId(GeneralLedger.SourceType sourceType, Long sourceId);
    List<GeneralLedger> findByJournalNumberAndStatus(String journalNumber, GeneralLedger.TransactionStatus status);
    List<GeneralLedger> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);
    boolean existsByAccountIdAndStatus(Long accountId, GeneralLedger.TransactionStatus status);

    // Bank reconciliation queries
    @Query("SELECT gl FROM GeneralLedger gl WHERE gl.transactionDate = :txDate AND gl.amount = :amount")
    List<GeneralLedger> findByTransactionDateAndAmount(@Param("txDate") LocalDate txDate, @Param("amount") BigDecimal amount);

    @Query("SELECT gl FROM GeneralLedger gl WHERE gl.account.id = :accountId AND gl.status = :status")
    List<GeneralLedger> findByAccountIdAndStatus(@Param("accountId") Long accountId, @Param("status") GeneralLedger.TransactionStatus status);

    @Query("""
            SELECT gl FROM GeneralLedger gl
            WHERE gl.account.id = :accountId
              AND gl.status = com.justjava.ams.accountant.entity.GeneralLedger.TransactionStatus.POSTED
              AND gl.transactionDate = :transactionDate
            ORDER BY gl.id ASC
            """)
    List<GeneralLedger> findPostedBankEntriesByAccountAndDate(
            @Param("accountId") Long accountId,
            @Param("transactionDate") LocalDate transactionDate);

    // Reporting queries - filter by the organization through the GL.account.organization.id path
    @Query("SELECT gl FROM GeneralLedger gl WHERE gl.status = com.justjava.ams.accountant.entity.GeneralLedger.TransactionStatus.POSTED AND gl.account.organization.id = :organizationId AND gl.transactionDate BETWEEN :fromDate AND :toDate ORDER BY gl.transactionDate ASC")
    List<GeneralLedger> findPostedEntriesByOrganizationAndDateRange(@Param("organizationId") Long organizationId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT gl FROM GeneralLedger gl WHERE gl.status = com.justjava.ams.accountant.entity.GeneralLedger.TransactionStatus.POSTED AND gl.account.organization.id = :organizationId AND gl.transactionDate <= :asOfDate ORDER BY gl.transactionDate ASC")
    List<GeneralLedger> findPostedEntriesByOrganizationAsOf(@Param("organizationId") Long organizationId, @Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT CASE WHEN COUNT(gl) > 0 THEN true ELSE false END FROM GeneralLedger gl WHERE gl.status = com.justjava.ams.accountant.entity.GeneralLedger.TransactionStatus.POSTED AND gl.account.organization.id = :organizationId")
    boolean existsPostedByOrganization(@Param("organizationId") Long organizationId);
}
