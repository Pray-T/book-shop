package com.example.book.service;

import com.example.book.controller.dto.BookReserveCancelDTO;
import com.example.book.service.dto.BookReserveConfirmDTO;
import com.example.book.service.dto.BookReserveDTO;
import com.example.book.service.dto.BookReserveResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookFacadeService {

    private final BookService bookService;

    public BookReserveResultDTO tryReserve(BookReserveDTO bookReserveDTO) {
        int count = 0;

        while (count <= 3) {
            try {
                return bookService.tryReserve(bookReserveDTO);
            } catch (ObjectOptimisticLockingFailureException e) {
                count++;
            }
        }

        throw new RuntimeException("예약에 실패했어요. [낙관적 락]");
    }

    public void confirmReserve(BookReserveConfirmDTO bookReserveConfirmDTO) {
        int count = 0;

        while (count <= 3) {
            try {
                bookService.confirmReserve(bookReserveConfirmDTO);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                count++;
            }
        }
    }

    public void cancelReserve(BookReserveCancelDTO bookReserveCancelDTO) {
        int count = 0;

        while (count <= 3) {
            try {
                bookService.cancelReserve(bookReserveCancelDTO);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                count++;
            }
        }
    }

}
