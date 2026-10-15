package com.justjava.ams.systemAdmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_background_job_config", uniqueConstraints = {
        @UniqueConstraint(columnNames = "job_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackgroundJobConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column
    private String scheduleExpression;

    @Builder.Default
    @Column(nullable = false)
    private Integer maxRetryAttempts = 5;

    @Column
    private String updatedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
