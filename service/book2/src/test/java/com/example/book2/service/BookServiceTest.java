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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional      //@springboottest인 경우에는 자동 롤백된다.
class BookServiceTest {

    @Autowired
    BookService bookService;

    @Autowired
    BookRepository bookRepository;

    @Autowired
    BookReservationRepository bookReservationRepository;

    @MockitoBean
    BookRedisRepository bookRedisRepository;

    @Test
    void tryReserve_성공하면_예약정보를_저장하고_총금액을_반환한다() {
        //given
        Book book = bookRepository.save(
                new Book(10000L, 10L)
        );

        when(bookRedisRepository.reserve(book.getId(), 2L))
                .thenReturn(RedisReserveResult.SUCCESS);
        //Mockito.when static import.

        BookReserveDTO bookReserveDTO = new BookReserveDTO(
                "1",
                List.of(new BookReserveDTO.ReserveItem(
                        book.getId(),
                        2L
                ))
        );

        //when
        BookReserveResultDTO result = bookService.tryReserve(bookReserveDTO);

        //then
        assertThat(result.getTotalPrice()).isEqualTo(20000L);

        List<BookReservation> reservations = bookReservationRepository.findAllByRequestId("1");
        assertThat(reservations).hasSize(1);

        BookReservation reservation = reservations.get(0);
        assertThat(reservation.getRequestId()).isEqualTo("1");
        assertThat(reservation.getBookId()).isEqualTo(1L);
        assertThat(reservation.getReservedQuantity()).isEqualTo(2L);
        assertThat(reservation.getReservedPrice()).isEqualTo(20000L);
        assertThat(reservation.getStatus()).isEqualTo(BookReservation.BookReservationStatus.RESERVED);

        System.out.println("==============================" + book.getId() + "========================================");
    }

    @Test
    void tryReserve_redis에_재고가_없으면_DB로_초기화한_뒤_예약한다() {
        //given
        Book book = bookRepository.save(
                new Book(10000L, 10L)
        );

        when(bookRedisRepository.reserve(book.getId(), 2L))
                .thenReturn(RedisReserveResult.NOT_INITIALIZED);

        when(bookRedisRepository.initAndReserve(book.getId(), 10L, 2L))
                .thenReturn(RedisReserveResult.SUCCESS);

        BookReserveDTO bookReserveDTO = new BookReserveDTO(
                "1",
                List.of(new BookReserveDTO.ReserveItem(
                        book.getId(),
                        2L
                ))
        );

        //when
        BookReserveResultDTO result = bookService.tryReserve(bookReserveDTO);

        //then
        assertThat(result.getTotalPrice()).isEqualTo(20000L);

        verify(bookRedisRepository).reserve(book.getId(), 2L);
        verify(bookRedisRepository).initAndReserve(book.getId(), 10L, 2L);

        List<BookReservation> reservations = bookReservationRepository.findAllByRequestId("1");
        assertThat(reservations).hasSize(1);

    }

    @Test
    void tryReserve_같은_requestId가_이미_있으면_redis를_다시_호출하지_않고_기존금액을_반환한다() {
        //given
        Book book = bookRepository.save(
                new Book(10000L, 10L)
        );

        bookReservationRepository.save(
                new BookReservation("request-1", book.getId(), 2L, 20000L)
        );

        BookReserveDTO dto = new BookReserveDTO(
                "request-1",
                List.of(
                        new BookReserveDTO.ReserveItem(book.getId(), 2L)
                )
        );

        //when
        BookReserveResultDTO result = bookService.tryReserve(dto);

        //then
        assertThat(result.getTotalPrice()).isEqualTo(20000L);

        verify(bookRedisRepository, never()).reserve(anyLong(), anyLong());
        verify(bookRedisRepository, never()).initAndReserve(anyLong(), anyLong(), anyLong());

    }

    @Test
    void tryReserve_중간에_예약이_실패하면_이미_성공한_redis를_보상한다() {
        //given
        Book book1 = bookRepository.save(
                new Book(10000L, 10L)
        );

        Book book2 = bookRepository.save(
                new Book(20000L, 10L)
        );

        when(bookRedisRepository.reserve(book1.getId(), 1L))
                .thenReturn(RedisReserveResult.SUCCESS);

        when(bookRedisRepository.reserve(book2.getId(), 15L))
                .thenReturn(RedisReserveResult.OUT_OF_STOCK);

        BookReserveDTO dto = new BookReserveDTO(
                "1",
                List.of(
                        new BookReserveDTO.ReserveItem(book1.getId(), 1L),
                        new BookReserveDTO.ReserveItem(book2.getId(), 15L)
                )
        );

        //when & then
        assertThatThrownBy(() -> bookService.tryReserve(dto))
                .isInstanceOf(RuntimeException.class);

        verify(bookRedisRepository).cancel(book1.getId(), 1L);

        List<BookReservation> reservations =
                bookReservationRepository.findAllByRequestId("1");

//        assertThat(reservations).isEmpty();       이 테스트는 실패한다. 왜냐하면 아직 롤백되지 않기 때문.
        //재고 오류로 레디스 예약 실패 시 기존의 코드는 분명히 롤백되어 reservation 엔티티는 삭제된다.
        //다만 현재 이 테스트 코드에서는 실행 주체가 테스트 코드자체다. 이 친구가 트랜젝션을 열고 닫는다.
        //이 assertThat을 실행할 때는 아직 테스트가 끝나기 전이고, 트랜젝션도 닫기 전이겠지?
        //그래서 아직 미처 롤백이 되지 않기에, reservation은 존재한다고 나온다.

    }

    @Test
    void confirmReserveAtomicUpdate_성공하면_DB재고가_감소하고_예약상태가_confirmed_가_된다() {
        //given
        Book book = bookRepository.save(new Book(10000L, 10L));

        BookReservation reservation = bookReservationRepository.save(
                new BookReservation("request-1", book.getId(), 3L, 30000L)
        );

        BookReserveConfirmDTO bookReserveConfirmDTO = new BookReserveConfirmDTO("request-1");

        //when
        bookService.confirmReserveAtomicUpdate(bookReserveConfirmDTO);

        //then
        Book savedBook = bookRepository.findById(book.getId()).orElseThrow();
        BookReservation savedReservation = bookReservationRepository.findById(reservation.getBookId()).orElseThrow();

        assertThat(savedBook.getQuantity()).isEqualTo(7L);
        assertThat(savedReservation.getStatus()).isEqualTo(BookReservation.BookReservationStatus.CONFIRMED);
    }

    @Test
    void confirmReserveAtomicUpdate_예약정보가_없으면_예외가_발생한다() {
        //given
        BookReserveConfirmDTO bookReserveConfirmDTO = new BookReserveConfirmDTO("1");

        //when & then
        assertThatThrownBy(() -> bookService.confirmReserveAtomicUpdate(bookReserveConfirmDTO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("예약된 정보가 없어요.");
    }

    @Test
    void confirmReserveAtomicUpdate_이미_CONFIRMED이면_재고를_다시_차감하지_않는다() {
        //given
        Book book = bookRepository.save(
                new Book(10000L, 10L)
        );

        BookReservation reservation = bookReservationRepository.save(new BookReservation(
                "1",
                book.getId(),
                3L,
                30000L
        ));
        reservation.confirm();

        BookReserveConfirmDTO bookReserveConfirmDTO = new BookReserveConfirmDTO("1");

        //when
        bookService.confirmReserveAtomicUpdate(bookReserveConfirmDTO);

        //then
        Book book1 = bookRepository.findById(book.getId()).orElseThrow();
        assertThat(book1.getQuantity()).isEqualTo(10L);
    }

    @Test
    void cancelReserve_성공하면_Redis재고를_복구하고_예약상태가_CANCELLED_가_된다() {
        //given
        Book book = bookRepository.save(new Book(
                10000L, 10L
        ));

        BookReservation reservation = bookReservationRepository.save(new BookReservation(
                "1", book.getId(), 3L, 30000L
        ));

        BookReserveCancelDTO bookReserveCancelDTO = new BookReserveCancelDTO("1");

        //when
        bookService.cancelReserve(bookReserveCancelDTO);

        //then
        verify(bookRedisRepository).cancel(book.getId(), 3L);

        BookReservation reservation1 = bookReservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(reservation1.getStatus()).isEqualTo(BookReservation.BookReservationStatus.CANCELLED);
    }

    @Test
    void cancelReserve_이미_CANCELLED이면_Redis를_다시_복구하지_않는다() {
        //given
        Book book = bookRepository.save(
                new Book(10000L, 10L)
        );

        BookReservation reservation = new BookReservation(
                "1",
                book.getId(),
                3L,
                30000L
        );

        reservation.cancel();
        bookReservationRepository.save(reservation);

        BookReserveCancelDTO bookReserveCancelDTO = new BookReserveCancelDTO("1");

        //when
        bookService.cancelReserve(bookReserveCancelDTO);

        //then
        verify(bookRedisRepository, never()).cancel(anyLong(), anyLong());

    }

}