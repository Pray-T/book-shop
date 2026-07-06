package com.example.book.controller;

import com.example.book.controller.dto.BookReserveCancelRequestDTO;
import com.example.book.controller.dto.BookReserveConfirmRequestDTO;
import com.example.book.controller.dto.BookReserveRequestDTO;
import com.example.book.controller.dto.BookReserveResponseDTO;
import com.example.book.service.BookFacadeService;
import com.example.book.service.RedisLockService;
import com.example.book.service.dto.BookReserveResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookController {

    private final BookFacadeService bookFacadeService;
    private final RedisLockService redisLockService;

//    @PostMapping("/book/reserve")
//    public BookReserveResponseDTO reserve(@RequestBody BookReserveRequestDTO bookReserveRequestDTO) {
//        String key = "book:" + bookReserveRequestDTO.getRequestId();
//        boolean acquiredLock = redisLockService.tryLock(key, bookReserveRequestDTO.getRequestId());
//
//        if (!acquiredLock) {
//            throw new RuntimeException("락 획득에 실패했어요.");
//        }
//
//        try {
//            BookReserveResultDTO bookReserveResultDTO = bookFacadeService.tryReserve(bookReserveRequestDTO.toBookReserveDTO());
//            return new BookReserveResponseDTO(bookReserveResultDTO.getTotalPrice());
//        } finally {
//            redisLockService.releaseLock(key);
//        }
//    }
//
//    @PostMapping("/book/confirm")
//    public void confirm(@RequestBody BookReserveConfirmRequestDTO bookReserveConfirmRequestDTO) {
//        String key = "book:" + bookReserveConfirmRequestDTO.getRequestId();
//        boolean acquiredLock = redisLockService.tryLock(key, bookReserveConfirmRequestDTO.getRequestId());
//
//        if (!acquiredLock) {
//            throw new RuntimeException("락 획득에 실패했어요.");
//        }
//
//        try {
//            bookFacadeService.confirmReserve(bookReserveConfirmRequestDTO.toBookReserveConfirmDTO());
//        } finally {
//            redisLockService.releaseLock(key);
//        }
//    }
//
//    @PostMapping("/book/cancel")
//    public void cancel(@RequestBody BookReserveCancelRequestDTO bookReserveCancelRequestDTO) {
//        String key = "book:" + bookReserveCancelRequestDTO.getRequestId();
//        boolean acquiredLock = redisLockService.tryLock(key, bookReserveCancelRequestDTO.getRequestId());
//
//        if (!acquiredLock) {
//            throw new RuntimeException("락 획득에 실패했어요.");
//        }
//
//        try {
//            bookFacadeService.cancelReserve(bookReserveCancelRequestDTO.toBookReserveCancelDTO());
//        } finally {
//            redisLockService.releaseLock(key);
//        }
//    }

    @PostMapping("/book/reserve")
    public BookReserveResponseDTO reserve(@RequestBody BookReserveRequestDTO bookReserveRequestDTO) {
        System.out.println("[book reserve 요청] requestId=" + bookReserveRequestDTO.getRequestId());

        String key = "book:" + bookReserveRequestDTO.getRequestId();
        boolean acquiredLock = redisLockService.tryLock(key, bookReserveRequestDTO.getRequestId());

        System.out.println("[book reserve 락 결과] requestId=" + bookReserveRequestDTO.getRequestId()
                + ", acquiredLock=" + acquiredLock);

        if (!acquiredLock) {
            throw new RuntimeException("락 획득에 실패했어요.");
        }

        try {
            BookReserveResultDTO bookReserveResultDTO =
                    bookFacadeService.tryReserve(bookReserveRequestDTO.toBookReserveDTO());

            System.out.println("[book reserve 성공] requestId=" + bookReserveRequestDTO.getRequestId());

            return new BookReserveResponseDTO(bookReserveResultDTO.getTotalPrice());
        } catch (Exception e) {
            System.out.println("[book reserve 실패] requestId=" + bookReserveRequestDTO.getRequestId()
                    + ", message=" + e.getMessage());
            throw e;
        } finally {
            redisLockService.releaseLock(key);
            System.out.println("[book reserve 락 해제] requestId=" + bookReserveRequestDTO.getRequestId());
        }
    }

    @PostMapping("/book/confirm")
    public void confirm(@RequestBody BookReserveConfirmRequestDTO bookReserveConfirmRequestDTO) {
        System.out.println("[book confirm 요청] requestId=" + bookReserveConfirmRequestDTO.getRequestId());

        String key = "book:" + bookReserveConfirmRequestDTO.getRequestId();
        boolean acquiredLock = redisLockService.tryLock(key, bookReserveConfirmRequestDTO.getRequestId());

        System.out.println("[book confirm 락 결과] requestId=" + bookReserveConfirmRequestDTO.getRequestId()
                + ", acquiredLock=" + acquiredLock);

        if (!acquiredLock) {
            throw new RuntimeException("락 획득에 실패했어요.");
        }

        try {
            bookFacadeService.confirmReserve(bookReserveConfirmRequestDTO.toBookReserveConfirmDTO());
            System.out.println("[book confirm 성공] requestId=" + bookReserveConfirmRequestDTO.getRequestId());
        } catch (Exception e) {
            System.out.println("[book confirm 실패] requestId=" + bookReserveConfirmRequestDTO.getRequestId()
                    + ", message=" + e.getMessage());
            throw e;
        } finally {
            redisLockService.releaseLock(key);
            System.out.println("[book confirm 락 해제] requestId=" + bookReserveConfirmRequestDTO.getRequestId());
        }
    }

    @PostMapping("/book/cancel")
    public void cancel(@RequestBody BookReserveCancelRequestDTO bookReserveCancelRequestDTO) {
        System.out.println("[book cancel 요청] requestId=" + bookReserveCancelRequestDTO.getRequestId());

        String key = "book:" + bookReserveCancelRequestDTO.getRequestId();
        boolean acquiredLock = redisLockService.tryLock(key, bookReserveCancelRequestDTO.getRequestId());

        System.out.println("[book cancel 락 결과] requestId=" + bookReserveCancelRequestDTO.getRequestId()
                + ", acquiredLock=" + acquiredLock);

        if (!acquiredLock) {
            throw new RuntimeException("cancel 락 획득에 실패했어요. requestId="
                    + bookReserveCancelRequestDTO.getRequestId());
        }

        try {
            bookFacadeService.cancelReserve(bookReserveCancelRequestDTO.toBookReserveCancelDTO());
            System.out.println("[book cancel 성공] requestId=" + bookReserveCancelRequestDTO.getRequestId());
        } catch (Exception e) {
            System.out.println("[book cancel 실패] requestId=" + bookReserveCancelRequestDTO.getRequestId()
                    + ", message=" + e.getMessage());
            throw e;
        } finally {
            redisLockService.releaseLock(key);
            System.out.println("[book cancel 락 해제] requestId=" + bookReserveCancelRequestDTO.getRequestId());
        }
    }

}
