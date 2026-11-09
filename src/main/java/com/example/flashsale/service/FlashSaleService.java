package com.example.flashsale.service;

import com.example.flashsale.config.RabbitMQConfig;
import com.example.flashsale.dto.FlashSaleRequest;
import com.example.flashsale.dto.OrderMessage;
import com.example.flashsale.entity.FlashSaleProduct;
import com.example.flashsale.repository.FlashSaleProductRepository;
import com.example.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlashSaleService {

    private final FlashSaleProductRepository flashSaleProductRepository;
    private final OrderRepository orderRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> stockDecreaseScript;
    private final RabbitTemplate rabbitTemplate;

    private static final String STOCK_KEY_PREFIX = "flash_sale:stock:";

    public void preheatStock(Long flashSaleProductId) {
        FlashSaleProduct product = flashSaleProductRepository.findById(flashSaleProductId)
                .orElseThrow(() -> new RuntimeException("Producto en oferta flash no encontrado"));

        String stockKey = STOCK_KEY_PREFIX + flashSaleProductId;
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getAvailableStock()));
        log.info("Stock pre-calentado en Redis para el producto {}: {} unidades", flashSaleProductId,
                product.getAvailableStock());
    }

    public String processFlashSale(FlashSaleRequest request) {
        if (orderRepository.existsByUserIdAndFlashSaleProductId(request.getUserId(), request.getFlashSaleProductId())) {
            throw new RuntimeException("Ya has comprado este producto en esta oferta flash. Límite: 1 por persona.");
        }

        String stockKey = STOCK_KEY_PREFIX + request.getFlashSaleProductId();
        Long remainingStock = stringRedisTemplate.execute(
                stockDecreaseScript,
                Collections.singletonList(stockKey),
                String.valueOf(request.getQuantity())
        );

        if (remainingStock == null || remainingStock == -2) {
            throw new RuntimeException("Error: La oferta flash no está activa o el stock no ha sido pre-calentado.");
        }
        if (remainingStock == -1) {
            throw new RuntimeException("Lo sentimos, ¡el producto está totalmente agotado!");
        }

        log.info("Stock reservado con éxito en Redis. Quedan {} unidades", remainingStock);

        FlashSaleProduct product = flashSaleProductRepository.findById(request.getFlashSaleProductId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        BigDecimal totalAmount = product.getFlashPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        OrderMessage orderMessage = OrderMessage.builder()
                .userId(request.getUserId())
                .flashSaleProductId(request.getFlashSaleProductId())
                .quantity(request.getQuantity())
                .totalAmount(totalAmount)
                .createdAt(Instant.now())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.FLASH_SALE_EXCHANGE,
                RabbitMQConfig.FLASH_SALE_ROUTING_KEY,
                orderMessage
        );

        log.info("Pedido enviado por RabbitMQ para el usuario {}", request.getUserId());

        return "¡Tu orden ha sido recibida y está en proceso de creación! Cantidad: " + request.getQuantity();
    }
}
