package com.example.order.service;

import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.outbox.OutboxRepository;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.Extensions;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//@Transactional 이 애너테이션은 삭제해야한다. 현재 서비스 코드에서 eventListener를 다루기 때문이다.
// 타임라인을 그리면서 설명해보자.
// 트랜젝션을 붙이면 이 테스트 코드로부터 트랜젝션이 시작한다.
// orderService.confirmOrder를 진행해도 여기서부터 새로운 트랜젝션이 시작하는 게 아니라 여전히 테스트 코드에서 진행.
// orderService.confirmOrder가 끝나면 트랜젝션이 commit을 날리는 게 보통이지만, 테스트코드에서 시작된 트랜젝션이기에
// 코드가 전부 끝나도 커밋이 안될 것임. 하지만 이 앱의 주요 서비스 흐름인 커밋전과 커밋이후에 진행되어야 할 아웃박스패턴들은?
// 모두 흐름이 깨질 확률이 높다. 그리하여, 테스트코드에서 트랜젝션을 시작하지말고, orderService가 가지고 있던 트랜젝션 흐름을 유지시켜줘야함.

// 테스트코드에서 트랜젝션 애너테이션은 자동으로 롤백하는 기능을 쓰고 있는데, 그럼 여기선 어떻게 해야할까? tearDown()을 통해 보상해주자.

@SpringBootTest
class OrderServiceTest {
//orderService 단위테스트는 통합테스트로 대체한다.

    @Autowired
    OrderService orderService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @MockitoBean        //단위테스트에서는 ExtendWith와 Mock 조합으로 mock가짜 빈객체를 썼다. 반면 통합테스트에서는 이 애너테이션을 씀.
    KafkaTemplate<String, String> kafkaTemplate;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void confirmOrder_카프카발행에_성공하면_outbox를_삭제한다() {
        //given
        Long orderId = 1L;
        orderRepository.save(
                new Order(orderId, 30000L, 0L, 1L)
        );

        orderItemRepository.save(
                new OrderItem(orderId, 1L, 3L)
        );

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        //when
        orderService.confirmOrder(orderId);

        //then
        verify(kafkaTemplate).send(anyString(), anyString(), anyString());

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(outboxRepository.findAll()).isEmpty()
                );

        //바로 위에 await는 지피티가 추천한 코드.
        //최대 5초까지 기다리면서
        //outboxRepository.findAll()이 비었는지 반복 확인해라.
        //중간에 조건이 만족되면 바로 통과해라.

    }

    @Test
    void confirmOrder_카프카발행에_실패하면_outbox를_삭제하지_않는다() {
        //given
        Long orderId = 2L;

        orderRepository.save(
                new Order(orderId, 30000L, 0L, 1L)
        );

        orderItemRepository.save(
                new OrderItem(orderId, 1L, 3L)
        );

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("카프카 발행 실패"));

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        // when
        orderService.confirmOrder(orderId);

        // then
        verify(kafkaTemplate).send(anyString(), anyString(), anyString());

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(outboxRepository.findAll()).hasSize(1)
                );          //만약 카프카에 이벤트 전송이 실패했다? 그러면 아웃박스는 삭제되지 말아야 한다.
                            // 추후 배치처리를 통해 다시 시도될 것이다.
    }

}