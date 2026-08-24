package com.justjava.ams.financeAdmin.repository;

import com.justjava.ams.financeAdmin.entity.BulkImportRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BulkImportRowRepository extends JpaRepository<BulkImportRow, Long> {
    List<BulkImportRow> findByBulkImportIdOrderByRowNumber(Long bulkImportId);
}
