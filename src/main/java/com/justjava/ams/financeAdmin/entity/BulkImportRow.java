package com.justjava.ams.financeAdmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bulk_import_rows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulk_import_id", nullable = false)
    private BulkImport bulkImport;

    @Column(nullable = false)
    private Integer rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RowStatus status;

    @Lob
    @Column(nullable = false)
    private String rawDataJson;

    @Lob
    private String normalizedDataJson;

    @Lob
    private String errorMessage;

    private Long createdRecordId;
    private Long updatedRecordId;

    public enum RowStatus {
        VALID,
        ERROR,
        CREATED,
        UPDATED,
        SKIPPED,
        FAILED
    }
}
