package com.justjava.ams.integration.entity;

import com.justjava.ams.common.entity.Organization;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * STAGE 1 (D2): says which service client (token "azp" claim) may act on which organisation.
 * Mappings are never deleted silently: deactivate them (active = false).
 */
@Entity
@Table(
        name = "client_organization_entitlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cle_client_organization",
                columnNames = {"client_id", "organization_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientOrganizationEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 150)
    private String clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cle_organization"))
    private Organization organization;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(length = 500)
    private String note;
}
