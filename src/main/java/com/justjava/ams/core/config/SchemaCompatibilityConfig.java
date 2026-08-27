package com.justjava.ams.core.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SchemaCompatibilityConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void alignAuditLogActionConstraint() {
        try {
            jdbcTemplate.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_action_check");
            jdbcTemplate.execute("""
                    ALTER TABLE audit_logs
                    ADD CONSTRAINT audit_logs_action_check
                    CHECK (action IN (
                        'CREATE',
                        'UPDATE',
                        'DELETE',
                        'VIEW',
                        'EXPORT',
                        'IMPORT',
                        'LOGIN',
                        'LOGOUT',
                        'SUBMIT',
                        'POST',
                        'CLOSE',
                        'LOCK',
                        'APPROVE',
                        'REJECT'
                    ))
                    """);
        } catch (Exception ex) {
            log.warn("Audit log action constraint alignment failed: {}", ex.getMessage());
        }
    }
}