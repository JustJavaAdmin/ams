package com.justjava.ams.financeAdmin.repository;

import com.justjava.ams.financeAdmin.entity.ApprovalRule;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {
    List<ApprovalRule> findByOrganizationIdOrderByPriorityAscIdAsc(Long organizationId);
    List<ApprovalRule> findByOrganizationIdAndActiveTrueOrderByPriorityAscIdAsc(Long organizationId);
    List<ApprovalRule> findByOrganizationIdAndModuleTypeAndActiveTrueOrderByPriorityAscIdAsc(Long organizationId, ModuleControl.ModuleType moduleType);
    Optional<ApprovalRule> findByOrganizationIdAndRuleNameIgnoreCase(Long organizationId, String ruleName);
}
