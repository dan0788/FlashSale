package com.example.flashsale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

import com.example.flashsale.enums.EventStatus;

@Entity
@Table(name = "flash_sale_events")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder

public class FlashSaleEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private EventStatus status = EventStatus.SCHEDULED;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
