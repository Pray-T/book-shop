package com.example.book.service;

import com.example.book.controller.dto.BookReserveCancelDTO;
import com.example.book.domain.Book;
import com.example.book.domain.BookReservation;
import com.example.book.repository.BookRepository;
import com.example.book.repository.BookReservationRepository;
import com.example.book.service.dto.BookReserveConfirmDTO;
import com.example.book.service.dto.BookReserveDTO;
import com.example.book.service.dto.BookReserveResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BookReservationRepository bookReservationRepository;

    @Transactional
    public BookReserveResultDTO tryReserve(BookReserveDTO bookReserveDTO) {

        List<BookReservation> result = bookReservationRepository.findAllByRequestId(bookReserveDTO.getRequestId());

        if (!result.isEmpty()) {
            Long totalPrice = result.stream().mapToLong(BookReservation::getReservedPrice).sum();
            return new BookReserveResultDTO(totalPrice);
        }

        Long totalPrice = 0L;

        for (BookReserveDTO.ReserveItem item : bookReserveDTO.getItems()) {

            Book book = bookRepository.findById(item.getBookId()).orElseThrow(
                    () -> new RuntimeException("해당하는 책이 없어요.")
            );

            Long price = book.reserve(item.getReserveQuantity());

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

    }

    @Transactional
    public void confirmReserve(BookReserveConfirmDTO bookReserveConfirmDTO) {
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

        for (BookReservation reservation : result) {
            Book book = bookRepository.findById(reservation.getBookId()).orElseThrow();
            book.confirm(reservation.getReservedQuantity());
            reservation.confirm();

            bookRepository.save(book);
            bookReservationRepository.save(reservation);
        }
    }

    @Transactional
    public void cancelReserve(BookReserveCancelDTO bookReserveCancelDTO) {
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
            Book book = bookRepository.findById(reservation.getBookId()).orElseThrow();
            book.cancel(reservation.getReservedQuantity());
            reservation.cancel();

            bookRepository.save(book);
            bookReservationRepository.save(reservation);
        }
    }

}
