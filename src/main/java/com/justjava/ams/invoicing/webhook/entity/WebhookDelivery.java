package com.justjava.ams.invoicing.webhook.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_deliveries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_endpoint_id", nullable = false)
    private WebhookEndpoint webhookEndpoint;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Integer attemptCount;

    @Lob
    @Column(nullable = false)
    private String requestBody;

    @Column
    private Integer responseCode;

    @Column(length = 2000)
    private String responseBody;

    @Column
    private LocalDateTime nextRetryAt;

    @Column
    private String signature;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,
        SUCCESS,
        FAILED,
        RETRYING,
        DEAD_LETTER
    }
}
