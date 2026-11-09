package com.example.flashsale.repository;

import com.example.flashsale.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    boolean existsByUserIdAndFlashSaleProductId(Long userId, Long flashSaleProductId);
}
