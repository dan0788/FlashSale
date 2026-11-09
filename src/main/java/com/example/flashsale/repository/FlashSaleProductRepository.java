package com.example.flashsale.repository;

import com.example.flashsale.entity.FlashSaleProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlashSaleProductRepository extends JpaRepository<FlashSaleProduct, Long> {

    List<FlashSaleProduct> findByEventId(Long eventId);

    Optional<FlashSaleProduct> findByEventIdAndProductId(Long eventId, Long productId);

    @Modifying
    @Query("UPDATE FlashSaleProduct f SET f.availableStock = f.availableStock - :quantity " +
           "WHERE f.id = :id AND f.availableStock >= :quantity")
    int decreaseStockDirectly(@Param("id") Long id, @Param("quantity") int quantity);
}
