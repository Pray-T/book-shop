package com.example.book2.consumer;

import com.example.book2.service.BookService;
import com.example.book2.service.dto.BookReserveConfirmDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedEventConsumer {

    private final BookService bookService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "order-confirmed",
            groupId = "book-service"
    )
    public void consume(String message, Acknowledgment ack) {
        log.info("[OrderConfirmedEventConsumer.consume] message={}", message);

        try {
            BookReserveConfirmDTO bookReserveConfirmDTO = OrderConfirmedEventPayload.toBookReserveConfirmDTO(
                    objectMapper.readValue(message, OrderConfirmedEventPayload.class)
            );

            bookService.confirmReserveAtomicUpdate(bookReserveConfirmDTO);
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("역직렬화 실패 message = {}", message, e);
            //ack잘못된 메세지를 ack하지 않으면 카프카에서 계속해서 그 이벤트(메세지)를 읽겠지만, 일단 ack하지 않는다.
            //왜냐하면, 오류 추적을 위해, 추후 order상태 중 confirm 직렬화 실패 상태를 만들어 따로 관리할 예정.
            //ack는 상태변화 후에 진행.
        } catch (Exception e) {
            log.error("처리 실패 message = {}", message, e);
            throw e;
            //여기서도 ack하지 않는다. 이유는 위와 같음. 관리하기.
        }

    }

}
