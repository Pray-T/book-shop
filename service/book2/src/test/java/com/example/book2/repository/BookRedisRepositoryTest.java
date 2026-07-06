package com.example.book2.repository;

import com.example.book2.repository.dto.RedisReserveResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BookRedisRepositoryTest {

    @Autowired
    BookRedisRepository bookRedisRepository;

    @Autowired
    StringRedisTemplate redisTemplate;

    private static final String BOOK_1_AVAILABLE_KEY = "book:1:available";
    private static final String BOOK_2_AVAILABLE_KEY = "book:2:available";

    @BeforeEach     //테스트 시작하기 전 실행
    void setUp() {
        redisTemplate.delete(
                List.of(
                        BOOK_1_AVAILABLE_KEY,
                        BOOK_2_AVAILABLE_KEY
                )
        );
    }

    @AfterEach      //테스트 완료 후 실행
    void tearDown() {
        redisTemplate.delete(
                List.of(
                        BOOK_1_AVAILABLE_KEY,
                        BOOK_2_AVAILABLE_KEY
                )
        );
    }

    @Test
    void reserve_redis에_재고가_있으면_재고를_차감한다() {

        //given
        redisTemplate.opsForValue().set(BOOK_1_AVAILABLE_KEY, "10");

        //when
        RedisReserveResult result = bookRedisRepository.reserve(1L, 3L);

        //then
        assertThat(result).isEqualTo(RedisReserveResult.SUCCESS);
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY)).isEqualTo("7");
    }

    @Test
    void reserve_redis에_재고가_없으면_NOT_INITIALIZED_를_반환한다() {

        //when
        RedisReserveResult result = bookRedisRepository.reserve(1L, 3L);

        //then
        assertThat(result).isEqualTo(RedisReserveResult.NOT_INITIALIZED);
    }

    @Test
    void reserve_redis에_재고가_부족하면_OUT_OF_STOCK_을_반환한다() {

        //given
        redisTemplate.opsForValue().set(BOOK_1_AVAILABLE_KEY, "2");

        //when
        RedisReserveResult result = bookRedisRepository.reserve(1L, 3L);

        assertThat(result).isEqualTo(RedisReserveResult.OUT_OF_STOCK);
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY)).isEqualTo("2");
    }

    @Test
    void initAndReserve_redis에_재고가_없으면_DB재고로_초기화한_뒤_차감한다() {
        RedisReserveResult reserveResult = bookRedisRepository.initAndReserve(1L, 10L, 3L);

        assertThat(reserveResult).isEqualTo(RedisReserveResult.SUCCESS);
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY)).isEqualTo("7");
    }


    //주의! 이 테스트는 동시성 문제를 방지하는 테스트다. lua script로 진행한다해도, initAndReserve를 두 스레드가 실행될 수 있다.
    //예를 들어 레디스에 저장되지 않은 책을 두 스레드가 예약한다고 하자.
    //첫 번째가 lua script를 통해 예약을 하려 했건만, 레디스에 캐싱된 값이없네? 그러면 initAndReserve로 이행할 것이다.
    //그런데 이행하기 전에 두 번째 스레드가 들어와서 reserve를 했더니 어라 레디스에 캐싱된 값이 없네?
    //그럼 두 스레드 모두 initializing을 할 위험이 있다.
    //나는 이 것을 막지 않을 것이다.
    //다만, initAndReserve에서 만약 값이 있는지 한 번 더 체크하는 로직을 가지고, 이미 있다면 현재 존재하는 값에서 차감하면 그만이다.
    @Test
    void initAndReserve_redis에_이미_재고가_있으면_기존_redis재고를_기준으로_차감한다() {
        //given
        redisTemplate.opsForValue().set(BOOK_1_AVAILABLE_KEY, "5");

        //when
        RedisReserveResult reserveResult = bookRedisRepository.initAndReserve(1L, 10L, 2L);

        //then
        assertThat(reserveResult).isEqualTo(RedisReserveResult.SUCCESS);
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY)).isEqualTo("3");
        //3개가 되어야 한다. 왜냐하면 자기 자신이 init을하려고 하지만 찰나의 순간에 누가 이미 예약을 해서 초기화 되어 있었기 때문.
        //물론 디비에 10개가 있었겠지만, 이전 스레드가 5개를 예약한 시나리오다.
    }

    @Test
    void initAndReserve_redis에_이미_재고가_있지만_재고가_부족하면_OUT_OF_STOCK을_반환한다() {
        //given
        redisTemplate.opsForValue().set(BOOK_2_AVAILABLE_KEY, "3");

        //when
        RedisReserveResult reserveResult = bookRedisRepository.initAndReserve(2L, 10L, 5L);

        assertThat(reserveResult).isEqualTo(RedisReserveResult.OUT_OF_STOCK);
        assertThat(redisTemplate.opsForValue().get(BOOK_2_AVAILABLE_KEY)).isEqualTo("3");
    }

    @Test
    void initAndReserve_재고가_부족하면_OUT_OF_STOCK_을_반환한다() {
        //when
        RedisReserveResult reserveResult = bookRedisRepository.initAndReserve(1L, 10L, 30L);

        //then
        assertThat(reserveResult).isEqualTo(RedisReserveResult.OUT_OF_STOCK);
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY)).isNull();
    }

    @Test
    void cancel_예약수량만큼_redis재고를_복구한다() {
        //given
        redisTemplate.opsForValue().set(BOOK_1_AVAILABLE_KEY, "7");

        //when
        bookRedisRepository.cancel(1L, 3L);

        //then
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY)).isEqualTo("10");
    }


    //ttl 관련 테스트
    @Test
    void reserve_ttl이_지나면_key가_사라진다() throws InterruptedException {
        // given
        redisTemplate.opsForValue().set(BOOK_1_AVAILABLE_KEY, "10");

        // when
        RedisReserveResult result = bookRedisRepository.reserve(1L, 3L);

        // then
        assertThat(result).isEqualTo(RedisReserveResult.SUCCESS);
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY))
                .isEqualTo("7");

        Thread.sleep(5000);

        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY))
                .isNull();
    }

    @Test
    void initAndReserve_TTL이_지나면_Redis_key가_사라진다() throws InterruptedException {
        // when
        RedisReserveResult result = bookRedisRepository.initAndReserve(1L, 10L, 3L);

        // then
        assertThat(result).isEqualTo(RedisReserveResult.SUCCESS);
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY))
                .isEqualTo("7");

        Thread.sleep(3500);

        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY))
                .isNull();
    }

    @Test
    void cancel_TTL이_지나면_Redis_key가_사라진다() throws InterruptedException {
        // given
        redisTemplate.opsForValue().set(BOOK_1_AVAILABLE_KEY, "7");

        // when
        bookRedisRepository.cancel(1L, 3L);

        // then
        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY))
                .isEqualTo("10");

        Thread.sleep(3500);

        assertThat(redisTemplate.opsForValue().get(BOOK_1_AVAILABLE_KEY))
                .isNull();
    }

}