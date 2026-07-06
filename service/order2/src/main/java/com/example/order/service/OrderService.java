package com.example.order.service;

import com.example.order.api.book.BookApiClient;
import com.example.order.api.book.dto.BookReserveApiRequestDTO;
import com.example.order.api.book.dto.BookReserveApiResponseDTO;
import com.example.order.api.book.dto.BookReserveCancelApiRequestDTO;
import com.example.order.api.point.PointApiClient;
import com.example.order.api.point.dto.PointReserveApiRequestDTO;
import com.example.order.api.point.dto.PointReserveCancelApiRequestDTO;
import com.example.order.api.point.dto.PointReserveConfirmApiRequestDTO;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.outbox.OrderConfirmedEventPayload;
import com.example.order.outbox.OutboxEventPublisher;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
import com.example.order.service.dto.CreateOrderDTO;
import com.example.order.service.dto.CreateOrderResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private final BookApiClient bookApiClient;
    private final PointApiClient pointApiClient;

    private final Snowflake snowflake;

    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public CreateOrderResultDTO createOrder(CreateOrderDTO createOrderDTO) {
        Order order = orderRepository.save(
                new Order(snowflake.nextId(), createOrderDTO.getPriceAmount(), createOrderDTO.getUsePoint(),
                        createOrderDTO.getUserId())
        );

        List<OrderItem> orderItems = createOrderDTO.getOrderItems().stream().map(
                orderItem -> new OrderItem(order.getId(), orderItem.getBookId(), orderItem.getQuantity())
        ).toList();

        orderItemRepository.saveAll(orderItems);

        return new CreateOrderResultDTO(order.getId(),
                createOrderDTO.getPriceAmount() - order.getUsePoint());

    }

    @Transactional
    public boolean reserveOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new RuntimeException("해당하는 주문번호가 없어요.")
        );

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());

        reserve(orderId);

        try {
            BookReserveApiRequestDTO bookReserveApiRequestDTO = new BookReserveApiRequestDTO(
                    String.valueOf(orderId),
                    orderItems.stream().map(
                            item -> new BookReserveApiRequestDTO.ReserveItem(
                                    item.getBookId(),
                                    item.getQuantity()
                            )
                    ).toList()
            );

            BookReserveApiResponseDTO bookReserveApiResponseDTO = bookApiClient.reserve(bookReserveApiRequestDTO);

//            PointReserveApiRequestDTO pointReserveApiRequestDTO = new PointReserveApiRequestDTO(
//                    String.valueOf(orderId),
//                    1L,
//                    order.getUsePoint()
//            );
//
//            pointApiClient.reserve(pointReserveApiRequestDTO);

            return true;
        } catch (Exception e) {
            cancel(orderId);

            BookReserveCancelApiRequestDTO bookReserveCancelApiRequestDTO =
                    new BookReserveCancelApiRequestDTO(String.valueOf(orderId));
            bookApiClient.cancel(bookReserveCancelApiRequestDTO);

//            PointReserveCancelApiRequestDTO pointReserveCancelApiRequestDTO =
//                    new PointReserveCancelApiRequestDTO(String.valueOf(orderId));
//            pointApiClient.cancel(pointReserveCancelApiRequestDTO);

            return false;

            //여기서 궁금했던 점, try{}에서 book reserve가 실패했다면, catch에서 cancel을 진행하는데,
            //그러면 성공하지 못한 reserve의 개수를 여기서 취소시키면,
            //있지도 않은 reserve 개수를 --처리하니, 데이터 정합성에 어긋나지 않나 고민했었다.
            //하지만 걱정안해도 된다.
            //각 book, point 서버에 cancel메서드는 reservation을 검사할 것이다.
            //reserve가 성공하지 못했다면, reservation도 없을 것이다.
            //현재 bookServer는 성공 pointServer가 실패했다면,
            //cancel메서드도 bookServer에서 진행, pointServer는 진행하지 않을 것이다. 위에 나온 이유인 reservation검사 때문이다.
            //걱정말자.

        }
    }

    @Transactional
    public void confirmOrder(Long orderId) {
        //기존에는 api client를 이용했지만, 이제 카프카를 사용할 것이다.

        //order confirm처리와 outbox저장은 여기서!
        //두 개의 작업은 같은 트랜젝션 내에서 처리되어야 한다!

        try {
            Order order = orderRepository.findById(orderId).orElseThrow(
                    () -> new RuntimeException("해당하는 주문번호가 없어요.")
            );

            order.confirm();

            List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);

            List<OrderConfirmedEventPayload.ReservedItem> reservedItems = orderItems.stream().map(
                    orderItem -> new OrderConfirmedEventPayload.ReservedItem(
                            orderItem.getBookId(), orderItem.getQuantity()
                    )
            ).toList();

            outboxEventPublisher.publish(OrderConfirmedEventPayload.builder()
                    .orderId(orderId)
                    .memberId(1L)
                    .totalPrice(order.getTotalPrice())
                    .reservedItemList(reservedItems)
                    .build());
            //이벤트 생성

        } catch (Exception e) {
            Order order = orderRepository.findById(orderId).orElseThrow(
                    () -> new RuntimeException("해당하는 주문번호가 없어요.")
            );

            order.pending();
            //confirm이 실패하면 어떻게 해야할까. order취소?
            //pending처리 후 배치로 재시도를 노려보자!
            //배치는 디비에서 pending상태인 order를 모두 비동기로 confirm처리 해주는 것으로 가정하자.
            //사용자에겐 동일하게 확정처리하면 될 것이다.
            //왜냐하면 결제도 성공했고 재고도 이미 선점했기 때문이다!
        }
    }

    public void cancelOrder(Long orderId) {
        //정책1: 15분 내에 어떠한 조치도 없으면 바로 cancel처리. 15분 마다 배치 메서드가 돌 것이다.
        //정책2: 토스 결제 실패도 마찬가지다. 토스 결제 실패url로 리다이렉팅되면 cancelOrder를 실행한다.
        //정책1,2는 개념만 정하고 구현은 하지 않는다.
        BookReserveCancelApiRequestDTO bookReserveCancelApiRequestDTO =
                new BookReserveCancelApiRequestDTO(String.valueOf(orderId));

        bookApiClient.cancel(bookReserveCancelApiRequestDTO);

//        PointReserveCancelApiRequestDTO pointReserveCancelApiRequestDTO =
//                new PointReserveCancelApiRequestDTO(String.valueOf(orderId));
//
//        pointApiClient.cancel(pointReserveCancelApiRequestDTO);

    }

    @Transactional
    public void reserve(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new RuntimeException("해당하는 주문번호가 없어요.")
        );

        order.reserve();
        orderRepository.save(order);
    }

    @Transactional
    public void confirm(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new RuntimeException("해당하는 주문번호가 없어요.")
        );

        order.confirm();
        orderRepository.save(order);
    }

    @Transactional
    public void cancel(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new RuntimeException("해당하는 주문번호가 없어요.")
        );

        order.cancel();
        orderRepository.save(order);
    }

    @Transactional
    public void pending(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new RuntimeException("해당하는 주문번호가 없어요.")
        );

        order.pending();
        orderRepository.save(order);
    }


}
