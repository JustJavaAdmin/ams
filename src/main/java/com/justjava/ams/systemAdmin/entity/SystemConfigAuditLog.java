package com.justjava.ams.systemAdmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_config_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfigAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String configType;

    @Column
    private Long configId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String changedBy;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
