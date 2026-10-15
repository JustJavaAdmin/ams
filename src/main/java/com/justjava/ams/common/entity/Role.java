package com.justjava.ams.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @Enumerated(EnumType.STRING)
    private RoleType roleType;

    @Column(nullable = false)
    private String description;

    @ManyToMany(mappedBy = "roles")
    private Set<User> users;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum RoleType {
        ADMIN,
        SYSTEM_ADMIN,
        FINANCE_ADMIN,
        ACCOUNTANT,
        CFO,
        AUDITOR
    }
}

