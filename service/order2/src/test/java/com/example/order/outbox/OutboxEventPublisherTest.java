package com.example.order.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

//단위테스트
@ExtendWith(MockitoExtension.class)         //mock을 쓰려면 이 친구를 넣어줘야함.
class OutboxEventPublisherTest {

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    ObjectMapper objectMapper;
    OutboxEventPublisher outboxEventPublisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        outboxEventPublisher = new OutboxEventPublisher(
                null,
                applicationEventPublisher,
                objectMapper
        );
    }

    @Test
    void publish_호출_시_OutboxEvent를_발행한다() {
        //given
        OrderConfirmedEventPayload payload = OrderConfirmedEventPayload.builder()
                .orderId(1L)
                .memberId(10L)
                .totalPrice(30000L)
                .reservedItemList(List.of(
                        new OrderConfirmedEventPayload.ReservedItem(1L, 3L)
                ))
                .build();

        //when
        outboxEventPublisher.publish(payload);

        //then
        Mockito.verify(applicationEventPublisher).publishEvent(Mockito.any(OutboxEvent.class));

    }


}