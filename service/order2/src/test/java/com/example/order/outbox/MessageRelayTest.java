package com.example.order.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

//단위테스트
@ExtendWith(MockitoExtension.class)
class MessageRelayTest {

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    MessageRelay messageRelay;

    @BeforeEach
    void setUp() {
        messageRelay = new MessageRelay(outboxRepository, kafkaTemplate);

        ReflectionTestUtils.setField(
                messageRelay,
                "orderConfirmedTopic",
                "order-confirmed"
        );
        //테스트에서 인자값을 강제로 넣을 때 사용한다. messageRelay클래스의 orderConfirmedTopic의 값을 order-confirmed로 넣어준다는 뜻이다.
    }

    @Test
    void createOutbox_호출_시_Outbox를_저장한다() {
        // given
        Outbox outbox = Outbox.create(1L,
                """
                        {
                            "orderId" : 1
                        }
                        """);
        OutboxEvent outboxEvent = OutboxEvent.of(outbox);

        //when
        messageRelay.createOutbox(outboxEvent);

        //then
        Mockito.verify(outboxRepository).save(outbox);
    }

    @Test
    void publishEvent_성공하면_카프카로_전송하고_outbox를_삭제한다() {
        //given
        Outbox outbox = Outbox.create(1L,
                """
                        {
                            "orderId" : 1
                        }
                        """);
        OutboxEvent outboxEvent = OutboxEvent.of(outbox);

        Mockito.when(kafkaTemplate.send(
                "order-confirmed",
                "1",
                """
                        {
                            "orderId" : 1
                        }
                        """
        )).thenReturn(CompletableFuture.completedFuture(null));

        // when
        messageRelay.publishEvent(outboxEvent);

        // then
        Mockito.verify(kafkaTemplate).send(
                "order-confirmed",
                "1",
                """
                        {
                            "orderId" : 1
                        }
                        """
        );

        Mockito.verify(outboxRepository).delete(outbox);

    }
}