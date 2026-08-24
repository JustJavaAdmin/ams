package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.FixedAssetDepreciationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface FixedAssetDepreciationRunRepository extends JpaRepository<FixedAssetDepreciationRun, Long> {
    boolean existsByFixedAssetIdAndPeriodEndDate(Long fixedAssetId, LocalDate periodEndDate);
}
