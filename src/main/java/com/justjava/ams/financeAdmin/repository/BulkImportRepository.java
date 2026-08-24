package com.justjava.ams.financeAdmin.repository;

import com.justjava.ams.financeAdmin.entity.BulkImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BulkImportRepository extends JpaRepository<BulkImport, Long> {
}
