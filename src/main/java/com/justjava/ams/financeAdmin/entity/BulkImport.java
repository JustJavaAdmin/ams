package com.justjava.ams.financeAdmin.entity;

import com.justjava.ams.common.entity.Organization;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bulk_imports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportType importType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private Boolean updateExisting;

    private String uploadedBy;

    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private Integer failedCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "bulkImport", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BulkImportRow> rows = new ArrayList<>();

    public void addRow(BulkImportRow row) {
        rows.add(row);
        row.setBulkImport(this);
    }

    public enum ImportType {
        BRANCHES,
        CHART_OF_ACCOUNTS
    }

    public enum ImportStatus {
        VALIDATED,
        VALIDATION_FAILED,
        PROCESSING,
        COMPLETED,
        COMPLETED_WITH_ERRORS,
        FAILED
    }
}
