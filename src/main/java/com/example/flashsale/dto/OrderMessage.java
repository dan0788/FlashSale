package com.example.flashsale.dto;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderMessage implements Serializable {

    private Long userId;
    private Long flashSaleProductId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private Instant createdAt;
}
