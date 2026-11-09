package com.example.flashsale.service;

import com.example.flashsale.config.RabbitMQConfig;
import com.example.flashsale.dto.OrderMessage;
import com.example.flashsale.entity.FlashSaleProduct;
import com.example.flashsale.entity.Order;
import com.example.flashsale.enums.OrderStatus;
import com.example.flashsale.repository.FlashSaleProductRepository;
import com.example.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderRepository orderRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;

    @RabbitListener(queues = RabbitMQConfig.FLASH_SALE_QUEUE)
    @Transactional
    public void receiveOrder(OrderMessage message) {
        log.info("Cocinero: Cápsula recibida de RabbitMQ para el usuario {}", message.getUserId());

        try {
            FlashSaleProduct product = flashSaleProductRepository.findById(message.getFlashSaleProductId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado en el almacén"));

            int updatedRows = flashSaleProductRepository.decreaseStockDirectly(
                    message.getFlashSaleProductId(),
                    message.getQuantity()
            );

            if (updatedRows == 0) {
                log.error("Fallo crítico: No se pudo descontar el stock en base de datos para el producto {}", message.getFlashSaleProductId());
                return;
            }

            Order order = Order.builder()
                    .userId(message.getUserId())
                    .flashSaleProduct(product)
                    .quantity(message.getQuantity())
                    .totalAmount(message.getTotalAmount())
                    .status(OrderStatus.CONFIRMED)
                    .build();

            orderRepository.save(order);

            log.info("Cocinero: ¡Orden #{} guardada con éxito en PostgreSQL con estado CONFIRMED!", order.getId());

        } catch (Exception e) {
            log.error("Error al procesar el pedido asíncrono para el usuario {}: {}", message.getUserId(), e.getMessage());
            throw e;
        }
    }
}
