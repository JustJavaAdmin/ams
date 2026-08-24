package com.justjava.ams.accountant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fixed_asset_depreciation_runs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fixed_asset_id", "period_end_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedAssetDepreciationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fixed_asset_id", nullable = false)
    private FixedAsset fixedAsset;

    @Column(nullable = false)
    private LocalDate periodEndDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal depreciationAmount;

    @Column
    private String postedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
