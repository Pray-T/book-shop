package com.example.book2.service;

import com.example.book2.domain.Book;
import com.example.book2.domain.BookReservation;
import com.example.book2.repository.BookRedisRepository;
import com.example.book2.repository.BookRepository;
import com.example.book2.repository.BookReservationRepository;
import com.example.book2.repository.dto.RedisReserveResult;
import com.example.book2.service.dto.BookReserveCancelDTO;
import com.example.book2.service.dto.BookReserveConfirmDTO;
import com.example.book2.service.dto.BookReserveDTO;
import com.example.book2.service.dto.BookReserveResultDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BookRedisRepository bookRedisRepository;
    private final BookReservationRepository bookReservationRepository;

//    private static final long RESERVATION_EXPIRE_MINUTES = 15L;
//    예약 후 15분 내에 어떠한 조치도 없으면 예약 취소를 할 예정인데, RESERVATION_EXPIRE_MINUTES는 그것을 위한 인자 값이다.
    @Transactional
    public BookReserveResultDTO tryReserve(BookReserveDTO bookReserveDTO) {

//        LocalDateTime expireMinute = LocalDateTime.now().plusMinutes(RESERVATION_EXPIRE_MINUTES);
        List<BookReservation> result = bookReservationRepository.findAllByRequestId(bookReserveDTO.getRequestId());

        if (!result.isEmpty()) {
            Long totalPrice = result.stream().mapToLong(BookReservation::getReservedPrice).sum();
            return new BookReserveResultDTO(totalPrice);
        }

        Long totalPrice = 0L;
        List<ReservedRedisBook> reservedBookList = new ArrayList<>();

        try {

            for (BookReserveDTO.ReserveItem item : bookReserveDTO.getItems()) {
                //            예약 후 반환값은 price여야 한다. 왜냐하면 totalPrice를 반환해야하니께.. 이걸 고민해보자.

                Long price = reserveRedis(item.getBookId(), item.getReserveQuantity());
                reservedBookList.add(new ReservedRedisBook(item.getBookId(), item.getReserveQuantity()));

                bookReservationRepository.save(
                        new BookReservation(
                                bookReserveDTO.getRequestId(),
                                item.getBookId(),
                                item.getReserveQuantity(),
                                price
                        )
                );

                totalPrice += price;
            }

            return new BookReserveResultDTO(totalPrice);

        } catch (Exception e) {

            compensateRedis(reservedBookList);
            throw e;        //runtimeException 예외 던짐.

        }

    }

    @Transactional
    public void confirmReservePessimistic(BookReserveConfirmDTO bookReserveConfirmDTO) {

        List<BookReservation> result = bookReservationRepository.findAllByRequestId(bookReserveConfirmDTO.getRequestId());

        if (result.isEmpty()) {
            throw new RuntimeException("예약된 정보가 없어요.");
        }

        boolean alreadyConfirmed = result.stream().allMatch(
                item -> item.getStatus() == BookReservation.BookReservationStatus.CONFIRMED
        );

        if (alreadyConfirmed) {
            System.out.println("이미 확정됐어요.");
            return;
        }

        //#####데드락 방지용#####
        List<BookReservation> reservations = result.stream()
                .sorted(Comparator.comparing(BookReservation::getBookId))
                .toList();

        for (BookReservation reservation : reservations) {
            Book book = bookRepository.findByIdForUpdate(reservation.getBookId()).orElseThrow(
                    () -> new RuntimeException("해당하는 책이 없어요.")
            );
            book.confirm(reservation.getReservedQuantity());
            reservation.confirm();

            bookRepository.save(book);
            bookReservationRepository.save(reservation);
        }

    }

    @Transactional
    public void confirmReserveAtomicUpdate(BookReserveConfirmDTO bookReserveConfirmDTO) {

        List<BookReservation> result = bookReservationRepository.findAllByRequestId(bookReserveConfirmDTO.getRequestId());

        if (result.isEmpty()) {
            throw new RuntimeException("예약된 정보가 없어요.");
        }

        boolean alreadyConfirmed = result.stream().allMatch(
                item -> item.getStatus() == BookReservation.BookReservationStatus.CONFIRMED
        );

        if (alreadyConfirmed) {
            System.out.println("이미 확정됐어요.");
            return;
        }

        //#####데드락 방지용#####
        List<BookReservation> reservations = result.stream()
                .sorted(Comparator.comparing(BookReservation::getBookId))
                .toList();


        for (BookReservation reservation : reservations) {

            int updated = bookRepository.decreaseQuantity(reservation.getBookId(), reservation.getReservedQuantity());

            if (updated == 0) {
                throw new RuntimeException("confirm을 실패했습니다. cf. failurl에서 3회 재시도 후에 안되면 결제 취소로 진행. " +
                        "실패 이유는 디비연결 종료말고는 상상되지 않는다.");
            }
            reservation.confirm();

            bookReservationRepository.save(reservation);

        }

    }

    private Long reserveRedis(Long bookId, Long quantity) {

        Book book = bookRepository.findById(bookId).orElseThrow(
                () -> new RuntimeException("해당하는 책이 없어요.")
        );      //책 재고를 위한 조회.

        RedisReserveResult result = bookRedisRepository.reserve(bookId, quantity);

        if (result == RedisReserveResult.SUCCESS) {
            return book.getPrice() * quantity;
        }

        if (result == RedisReserveResult.OUT_OF_STOCK) {
            throw new RuntimeException("재고가 부족해요. bookId = " + bookId);
        }

        RedisReserveResult result1 = bookRedisRepository.initAndReserve(bookId, book.getQuantity(), quantity);

        if (result1 == RedisReserveResult.SUCCESS) {
            System.out.println("redis예약에 성공했어요.");
            return book.getPrice() * quantity;
        }

        if (result1 == RedisReserveResult.OUT_OF_STOCK) {
            throw new RuntimeException("재고가 부족해요. bookId = " + bookId);
        }

        throw new RuntimeException("redis예약에 실패했어요.");

    }

    @Transactional
    public void cancelReserve(BookReserveCancelDTO bookReserveCancelDTO) {
        //###########################################
        //15분이 지난 예약들은 배치 처리를 통해 자동취소한다.
        //###########################################
        List<BookReservation> result = bookReservationRepository.findAllByRequestId(bookReserveCancelDTO.getRequestId());

        if (result.isEmpty()) {
            throw new RuntimeException("예약된 정보가 없어요.");
        }

        boolean alreadyCancelled = result.stream().allMatch(
                item -> item.getStatus() == BookReservation.BookReservationStatus.CANCELLED
        );

        if (alreadyCancelled) {
            System.out.println("이미 취소됐어요.");
            return;
        }

        for (BookReservation reservation : result) {
            bookRedisRepository.cancel(reservation.getBookId(), reservation.getReservedQuantity());
            reservation.cancel();
            bookReservationRepository.save(reservation);
        }

    }

    private void compensateRedis(List<ReservedRedisBook> reservedRedisBooks) {
        for (ReservedRedisBook reservedRedisBook : reservedRedisBooks) {
            bookRedisRepository.cancel(reservedRedisBook.getBookId(), reservedRedisBook.getQuantity());
        }
    }

    @Getter
    @AllArgsConstructor
    private static class ReservedRedisBook{
        Long bookId;
        Long quantity;
    }

}
