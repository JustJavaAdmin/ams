package com.justjava.ams.systemAdmin.repository;

import com.justjava.ams.systemAdmin.entity.SystemConfigAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemConfigAuditLogRepository extends JpaRepository<SystemConfigAuditLog, Long> {
    List<SystemConfigAuditLog> findTop50ByOrderByCreatedAtDesc();
}
