package com.example.flashsale.repository;

import com.example.flashsale.entity.FlashSaleEvent;
import com.example.flashsale.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FlashSaleEventRepository extends JpaRepository<FlashSaleEvent, Long> {

    List<FlashSaleEvent> findByStatus(EventStatus status);

    @Query("SELECT e FROM FlashSaleEvent e WHERE e.status = 'ACTIVE' AND :now BETWEEN e.startTime AND e.endTime")
    List<FlashSaleEvent> findActiveEventsAt(@Param("now") Instant now);
}
