package com.justjava.ams.auditor.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_logs_id_seq")
    @SequenceGenerator(name = "audit_logs_id_seq", sequenceName = "audit_logs_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @Column
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AuditAction {
        CREATE,
        UPDATE,
        DELETE,
        VIEW,
        EXPORT,
        IMPORT,
        LOGIN,
        LOGOUT,
        SUBMIT,
        POST,
        CLOSE,
        LOCK,
        APPROVE,
        REJECT
    }
}

