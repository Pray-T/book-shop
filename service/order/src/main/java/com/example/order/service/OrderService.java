package com.example.order.service;

import com.example.order.api.book.BookApiClient;
import com.example.order.api.book.dto.BookReserveApiRequestDTO;
import com.example.order.api.book.dto.BookReserveApiResponseDTO;
import com.example.order.api.book.dto.BookReserveCancelApiRequestDTO;
import com.example.order.api.book.dto.BookReserveConfirmApiRequestDTO;
import com.example.order.api.point.PointApiClient;
import com.example.order.api.point.dto.PointReserveApiRequestDTO;
import com.example.order.api.point.dto.PointReserveCancelApiRequestDTO;
import com.example.order.api.point.dto.PointReserveConfirmApiRequestDTO;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
//import com.example.order.tossController.dto.ConfirmPaymentRequestDTO;
import com.example.order.service.dto.CreateOrderDTO;
import com.example.order.service.dto.CreateOrderResultDTO;
import com.example.order.service.dto.TossConfirmRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private final BookApiClient bookApiClient;
    private final PointApiClient pointApiClient;

    private final Snowflake snowflake;


//    @Value("${toss.secret-key}")
//    private String secretKey;


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

        return new CreateOrderResultDTO((order.getId()),
                createOrderDTO.getPriceAmount() - order.getUsePoint());

    }

    public boolean reserveOrder(Long orderId) {

        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new RuntimeException("해당하는 주문번호가 없어요.")
        );

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);


        try {
            reserve(orderId);

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
//            테스트를 위한 포인트서버 주석처리, 테스트 조건은 공평해야한다. order2서버는 포인트서버를 제외하고 테스트 하기에 1서버도 제외시켜주자.

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
        }

    }

    public void confirmOrder(Long orderId) {

        try {
            BookReserveConfirmApiRequestDTO bookReserveConfirmApiRequestDTO =
                    new BookReserveConfirmApiRequestDTO(String.valueOf(orderId));
            bookApiClient.confirm(bookReserveConfirmApiRequestDTO);

//            PointReserveConfirmApiRequestDTO pointReserveConfirmApiRequestDTO =
//                    new PointReserveConfirmApiRequestDTO(String.valueOf(orderId));
//            pointApiClient.confirm(pointReserveConfirmApiRequestDTO);

            confirm(orderId);

            Order order = orderRepository.findById(orderId).orElseThrow(
                    () -> new RuntimeException("해당하는 주문번호가 없어요.")
            );

            List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);

        } catch (Exception e) {

            pending(orderId);

        }

    }

    public void cancelOrder(Long orderId) {
        //정책1: 15분 내에 어떠한 조치도 없으면 바로 cancel처리. 15분 마다 배치 메서드가 돌 것이다.
        //정책2: 토스 결제 실패도 마찬가지다. 토스 결제 실패url로 리다이렉팅되면 cancelOrder를 실행한다.
        BookReserveCancelApiRequestDTO bookReserveCancelApiRequestDTO =
                new BookReserveCancelApiRequestDTO(String.valueOf(orderId));

        bookApiClient.cancel(bookReserveCancelApiRequestDTO);
        cancel(orderId);

//        PointReserveCancelApiRequestDTO pointReserveCancelApiRequestDTO =
//                new PointReserveCancelApiRequestDTO(String.valueOf(orderId));
//
//        pointApiClient.cancel(pointReserveCancelApiRequestDTO);

    }


////========================================================아래는 토스 관련
//
//
//    public boolean beforeConfirmApi(ConfirmPaymentRequestDTO confirmPaymentRequestDTO) {
//        //토스로 confirmAPI를 호출하기 전에 서버 내에서 이 주문이 요청한 주문이 맞는지 검증한다.
//
//        String orderId = confirmPaymentRequestDTO.getOrderId();
//
//        Order order = orderRepository.findById(Long.parseLong(orderId)).orElseThrow(
//                () -> new RuntimeException("잘못된 주문번호입니다.")
//        );
//
//        order.setPaymentKey(confirmPaymentRequestDTO.getPaymentKey());
//
//        Long totalPrice = order.getTotalPrice();
//        Long requestAmount = confirmPaymentRequestDTO.getAmount();
//
//        return totalPrice.equals(requestAmount);
//
//    }
//
//    public ResponseEntity<String> confirmPayment(String paymentKey, String orderId, Long amount) {
//
//        RestTemplate restTemplate = new RestTemplate();
//
//        String url = "https://api.tosspayments.com/v1/payments/confirm";
//
//        // 1) 시크릿키를 Authorization 헤더로 넣기
//        String auth = Base64.getEncoder()
//                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.set("Authorization", "Basic " + auth);
//
//        // 2) body (토스 confirm 규격)
//        TossConfirmRequestDTO body = new TossConfirmRequestDTO(paymentKey, orderId, amount);
//
//        HttpEntity<TossConfirmRequestDTO> request = new HttpEntity<>(body, headers);
//
//        return restTemplate.postForEntity(url, request, String.class);
//
//    }
//

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
