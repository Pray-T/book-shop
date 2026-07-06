package com.example.book2.consumer;

import com.example.book2.service.BookService;
import com.example.book2.service.dto.BookReserveConfirmDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderConfirmedEventConsumerTest {

    @Mock
    BookService bookService;

    @Mock
    Acknowledgment ack;

    ObjectMapper objectMapper;

    OrderConfirmedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new OrderConfirmedEventConsumer(bookService, objectMapper);
    }

    @Test
    void consume_정상메세지면_예약확정_처리하고_ack한다() {
        //given
        String message = """
                {
                    "orderId" : 1,
                    "memberId" : 10,
                    "totalPrice" : 30000,
                    "reservedItemList" : [
                      {
                        "bookId" : 10000,
                        "reservedQuantity" : 3
                      }
                    ]
                }
                """;

        //when
        consumer.consume(message,ack);

        //then
        verify(bookService).confirmReserveAtomicUpdate(any(BookReserveConfirmDTO.class));
        verify(ack).acknowledge();
    }

    @Test
    void consume_JSON형식이_잘못되면_ack하지않고_서비스도_호출하지_않는다() {
        //given
        String invalidMessage = """
                    {
                      "orderId" : 1,
                      "reservedItemList" :
                    }
                """;

        //when
        consumer.consume(invalidMessage, ack);

        //then
        verify(bookService, never()).confirmReserveAtomicUpdate(any());
        verify(ack, never()).acknowledge();

    }

    @Test
    void consume_서비스처리_실패하면_ack하지_않고_예외를_던진다() {
        // given
        String message = """
                {
                  "orderId": 1,
                  "memberId": 10,
                  "totalPrice": 30000,
                  "reservedItemList": [
                    {
                      "bookId": 10000,
                      "reservedQuantity": 3
                    }
                  ]
                }
                """;

        doThrow(new RuntimeException("재고 감소 실패"))
                .when(bookService)
                .confirmReserveAtomicUpdate(any(BookReserveConfirmDTO.class));

        //when & then
        assertThatThrownBy(() -> consumer.consume(message, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("재고 감소 실패");

        verify(bookService).confirmReserveAtomicUpdate(any(BookReserveConfirmDTO.class));
        verify(ack, never()).acknowledge();

    }

}


//  ObjectMapper objectMapper;
//
//    OrderConfirmedEventConsumer consumer;
//
//얘네는 빈주입같은 거 안해? 그리고 나머지는 또 왜 Mock으로만 하는겨?
//
//맞아. 여기서는 스프링 빈 주입을 안 해.
//
//이 테스트는 @SpringBootTest가 아니라 Mockito 단위 테스트라서 그래.
//
//ObjectMapper objectMapper;
//OrderConfirmedEventConsumer consumer;
//
//얘네는 테스트에서 직접 만든 거야.
//
//@BeforeEach
//void setUp() {
//    objectMapper = new ObjectMapper();
//    consumer = new OrderConfirmedEventConsumer(bookService, objectMapper);
//}
//
//즉 실제로는 이거랑 같아.
//
//BookService 가짜객체
//ObjectMapper 실제객체
//
//→ 둘을 넣어서 Consumer 직접 생성
//
//왜 BookService, Acknowledgment만 Mock이냐면:
//
//@Mock
//BookService bookService;
//
//@Mock
//Acknowledgment ack;
//
//얘네는 진짜 동작시키면 테스트 범위가 커지기 때문이야.
//
//BookService를 Mock으로 하는 이유
//
//지금 테스트하고 싶은 건 BookService 내부 로직이 아니야.
//
//테스트 목적은 이거야.
//
//Consumer가 메시지를 받으면 BookService를 호출하냐?
//
//그래서 실제 DB 재고 감소까지 할 필요 없어.
//
//실제 BookService를 쓰면:
//
//DB 필요
//Repository 필요
//트랜잭션 필요
//Book/Reservation 데이터 필요
//
//이렇게 테스트가 커져.
//
//Acknowledgment를 Mock으로 하는 이유
//
//ack도 Kafka가 실제로 넘겨주는 객체야.
//
//단위 테스트에서는 진짜 Kafka가 없으니까 직접 만들 수 없어.
//그래서 Mock으로 두고,
//
//verify(ack).acknowledge();
//
//이렇게 “ack를 호출했는지만” 확인하는 거야.
//
//ObjectMapper는 왜 Mock이 아니라 실제 객체냐?
//
//JSON 파싱은 이 Consumer 로직의 일부라서 실제로 검증하는 게 좋아.
//
//즉 이 테스트에서는:
//
//objectMapper.readValue(message, OrderConfirmedEventPayload.class)
//
//이게 실제로 동작해야 해.
//
//그래야 정상 JSON/잘못된 JSON 테스트가 의미 있어.
//
//정리하면:
//
//BookService       → Mock, 호출 여부만 검증
//Acknowledgment    → Mock, ack 호출 여부만 검증
//ObjectMapper      → 실제 객체, JSON 파싱 검증
//Consumer          → 직접 생성, 테스트 대상
//
//스프링 빈 주입까지 쓰는 테스트는 나중에 @SpringBootTest나 @EmbeddedKafka로 하는 통합 테스트에서 하면 돼.