package com.example.order.repository;

import com.example.order.domain.Order;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
    SELECT o
    FROM Order o
    WHERE o.status = :status
      AND o.reservedAt <= :threshold
""")
    List<Order> findExpiredOrders(
            @Param("status") Order.OrderStatus status,
            @Param("threshold") LocalDateTime threshold
    );

}
