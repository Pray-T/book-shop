package com.example.order.service;

import com.example.order.api.book.BookApiClient;
import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderCancelScheduler {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    @Scheduled(fixedDelay = 30_000)
    public void cancelExpiredOrders() {

        LocalDateTime threshold =
                LocalDateTime.now().minusMinutes(15);

        List<Order> orders =
                orderRepository.findExpiredOrders(
                        Order.OrderStatus.RESERVED,
                        threshold
                );

        for (Order order : orders) {

            // Book 예약 취소
            orderService.cancelOrder(order.getId());

        }

    }

}
