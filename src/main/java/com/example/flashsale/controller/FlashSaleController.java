package com.example.flashsale.controller;

import com.example.flashsale.dto.FlashSaleRequest;
import com.example.flashsale.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Flash Sales", description = "Endpoints para la gestión y compra de ventas flash de alto rendimiento")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @Operation(summary = "Pre-calentar stock en Redis", description = "Carga el stock disponible de PostgreSQL a la memoria RAM de Redis.")
    @PostMapping("/preheat/{flashSaleProductId}")
    public ResponseEntity<String> preheatStock(@PathVariable Long flashSaleProductId) {
        flashSaleService.preheatStock(flashSaleProductId);
        return ResponseEntity.ok("Stock pre-calentado exitosamente en Redis para el producto ID: " + flashSaleProductId);
    }

    @Operation(summary = "Realizar pedido de venta flash", description = "Valida y descuenta stock en Redis en 0.1 ms y envía la orden a RabbitMQ.")
    @PostMapping("/order")
    public ResponseEntity<String> processOrder(@Valid @RequestBody FlashSaleRequest request) {
        String result = flashSaleService.processFlashSale(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}
