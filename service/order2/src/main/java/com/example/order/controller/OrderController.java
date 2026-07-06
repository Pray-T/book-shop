package com.example.order.controller;

import com.example.order.controller.dto.CreateOrderRequestDTO;
import com.example.order.controller.dto.CreateOrderResponseDTO;
import com.example.order.controller.dto.PlaceOrderRequestDTO;
import com.example.order.controller.dto.ReserveOrderRequestDTO;
import com.example.order.service.OrderService;
import com.example.order.service.dto.CreateOrderResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/createOrder")
    public CreateOrderResponseDTO createOrder(@RequestBody CreateOrderRequestDTO createOrderRequestDTO) {
        CreateOrderResultDTO order = orderService.createOrder(createOrderRequestDTO.toCreateOrderDTO());

        return new CreateOrderResponseDTO(String.valueOf(order.getOrderId()),
                String.valueOf(order.getTotalPrice()));

    }

    @PostMapping("/api/reserve")
    public ResponseEntity<Void> reserveOrder(@RequestBody ReserveOrderRequestDTO reserveOrderRequestDTO) {

        boolean reserved = orderService.reserveOrder(reserveOrderRequestDTO.getOrderId());

        if (reserved) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

    }

    @PostMapping("/api/placeOrder")
    public void placeOrder(@RequestBody PlaceOrderRequestDTO placeOrderRequestDTO) {

        if (orderService.reserveOrder(placeOrderRequestDTO.getOrderId())) {
            orderService.confirmOrder(placeOrderRequestDTO.getOrderId());
        }

        System.out.println("=================================거래 정상 종료.===================================");

    }

}
