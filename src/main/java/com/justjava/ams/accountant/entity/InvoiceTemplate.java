package com.justjava.ams.accountant.entity;

import com.justjava.ams.common.entity.Organization;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_templates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "template_name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "template_name", nullable = false)
    private String templateName = "default";

    @Lob
    @Column(name = "blocks_json", nullable = false, columnDefinition = "TEXT")
    private String blocksJson;

    @Lob
    @Column(name = "theme_json", nullable = false, columnDefinition = "TEXT")
    private String themeJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
