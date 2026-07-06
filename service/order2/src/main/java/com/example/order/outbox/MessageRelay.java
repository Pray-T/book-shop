package com.example.order.outbox;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> messageRelayKafkaTemplate;

    @Value("${app.kafka.topic.order-confirmed}")
    private String orderConfirmedTopic;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createOutbox(OutboxEvent outboxEvent) {
        //만약 order가 confirm은 됐는데, commit전에 여기서 아웃박스가 실패한다?
        //그럼 order까지 모두 롤백처리됨. afterCommit도 당연히 실행취소다.
        log.info("[MessageRelay.createOutbox] outboxEvent={}", outboxEvent);
        outboxRepository.save(outboxEvent.getOutbox());
    }

    @Async("messageRelayPublishEventExecutors")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishEvent(OutboxEvent outboxEvent) {
        publishEvent(outboxEvent.getOutbox());
    }

    //참고로 이건 트랜젝션 없어도 되네. outbox삭제실패했다고 카프카로 보내졌던 이벤트를 취소시킬 수도 없는 노릇이니까.
    //아웃박스 삭제 실패에 따라서 카프카 전송도 실패해야되는지에 대한 궁금증 때문에 트랜젝션을 붙여야 하는지 고민했다.
    //카프카로 전송하고 아웃박스 삭제가 실패했다고 해도, 그리하여 비동기 배치처리에서 다시 전송되어도 문제가 없게 만드는건, consumer단에서 멱등성처리다.
    private void publishEvent(Outbox outbox) {
        try {
            messageRelayKafkaTemplate.send(
                    orderConfirmedTopic,
                    String.valueOf(outbox.getOrderId()),        //아직 서버는 하나지만 확장성(scale-out)을 위해 준비.
                    outbox.getPayload()
                    //1. 토픽이름 2. 파티션을 구분할 때 쓰이는 해시값의 인자 값 key 3. payload
                    //특히 파티션 해시값은 내가 헷갈리는 요소다.
                    //파티션은 토픽에서 나눠지는 실제로 데이터가 소비되는 단위인데,
                    //하나의 컨슈머 그룹에 있는 여러 서비스의 개수에 대응되게끔 갯수를 설정해야한다.
                    //그래야 순서보장이 된다.
                    //만약 파티션1과 2가 있고 book서버 1과 2가있다고 하자.
                    //파티션 1에 orderId1이 계속해서 들어간다고 가정하자.
                    //order created, reserved, confirmed, pending등등 여러 이벤트가 발생하겠지.
                    //그런데 갑자기 orderId가 1인 주문번호가
                    // reserved와 confirmed이 발생하고 모종의 이유로 confirmed이벤트가 파티션 2로 들어간다고 하자.
                    //이 때 created가 파티션1에서 처리되고 파티션1에서 reserved 가 먼저 처리되어야 하는데
                    //confirmed이 book서버2에서 먼저처리될 수도 있다.
                    //그런 이유로 항상 이렇게 key값을 줘야한다. 만약 이렇게 주게되면 카프카가 해싱값을 이용하여
                    //항상 같은 파티션으로 push한다.
            ).get(1, TimeUnit.SECONDS);
            outboxRepository.delete(outbox);

        } catch (Exception e) {

            log.error("[아웃박스 이벤트 발행 오류.] outbox = {}", outbox, e);

        }
    }

    @Scheduled(
            fixedDelay = 10,
            initialDelay = 5,
            timeUnit = TimeUnit.SECONDS,
            scheduler = "messageRelayPublishPendingEventExecutor"
    )
    public void publishFailedEvent() {
        List<Outbox> outboxes = outboxRepository.findAllByCreatedAtLessThanEqual(
                LocalDateTime.now().minusSeconds(10),
                Pageable.ofSize(100)
        );

        for (Outbox outbox : outboxes) {
            publishEvent(outbox);
        }
    }

}
