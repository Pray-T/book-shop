package com.example.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
public class Order {

    @Id
    public Long id;
    private Long priceAmount;
    private Long usePoint;
    private Long totalPrice;

    private Long userId;

    @Setter
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public Order(){
        this.status = OrderStatus.CREATED;
    }

    public Order(Long orderId, Long priceAmount, Long usePoint, Long userId) {
        this.id = orderId;
        this.priceAmount = priceAmount;
        this.usePoint = usePoint;
        this.totalPrice = priceAmount - usePoint;
        this.userId = userId;
        this.status = OrderStatus.CREATED;
    }


    public enum OrderStatus{
        CREATED,
        RESERVED,
        CANCELLED,
        CONFIRMED,
        PENDING,
    }

    public void reserve() {
        this.status = OrderStatus.RESERVED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    public void pending() {
        this.status = OrderStatus.PENDING;
    }


}
