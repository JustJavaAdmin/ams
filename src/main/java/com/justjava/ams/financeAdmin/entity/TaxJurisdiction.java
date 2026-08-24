package com.justjava.ams.financeAdmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.justjava.ams.common.entity.Organization;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_jurisdictions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "jurisdiction_code"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxJurisdiction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String jurisdictionName;

    @Column(nullable = false)
    private String jurisdictionCode;

    @Column
    private String country;

    @Column
    private String state;

    @Column
    private String municipality;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaxType taxType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaxCalculationType calculationType;

    @Column
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum TaxType {
        VAT,
        GST,
        SALES_TAX,
        INCOME_TAX,
        CORPORATE_TAX,
        PROPERTY_TAX,
        OTHER
    }

    public enum TaxCalculationType {
        PERCENTAGE,
        FIXED_AMOUNT,
        TIERED
    }
}
