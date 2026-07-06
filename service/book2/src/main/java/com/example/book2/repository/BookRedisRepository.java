package com.example.book2.repository;

import com.example.book2.repository.dto.RedisReserveResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final Duration BOOK_STOCK_TTL = Duration.ofDays(3);        //실제 정책은 3일
//    private static final Duration BOOK_STOCK_TTL = Duration.ofSeconds(3);      //ttl이 정상작동하는지에 대한 테스트 용임

    private static final String RESERVE_SCRIPT = """
            local available = redis.call("GET", KEYS[1])
            
            if not available then
                return -1
            end
            
            available = tonumber(available)
            local quantity = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            
            if available < quantity then
                return 0
            end
            
            redis.call("DECRBY", KEYS[1], quantity)
            redis.call("EXPIRE", KEYS[1], ttl)
            
            return 1
            """;

    private static final String INIT_AND_RESERVE_SCRIPT = """
            local current = redis.call("GET", KEYS[1])
            local dbQuantity = tonumber(ARGV[1])
            local requestQuantity = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            
            if current then
                current = tonumber(current)
            
                if current < requestQuantity then
                    return 0
                end
            
                redis.call("DECRBY", KEYS[1], requestQuantity)
                redis.call("EXPIRE", KEYS[1], ttl)
            
                return 1
            end
            
            if dbQuantity < requestQuantity then
                return 0
            end
            
            redis.call("SET", KEYS[1], dbQuantity)
            redis.call("EXPIRE", KEYS[1], ttl)
            redis.call("DECRBY", KEYS[1], requestQuantity)
            
            return 1
            """;

    private static final String CANCEL_SCRIPT = """
            local quantity = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            
            redis.call("INCRBY", KEYS[1], quantity)
            redis.call("EXPIRE", KEYS[1], ttl)
            
            return 1
            """;

    public RedisReserveResult reserve(Long bookId, Long quantity) {
        Long result = redisTemplate.execute(
                redisScript(RESERVE_SCRIPT),
                List.of(availableKey(bookId)),
                String.valueOf(quantity),
                String.valueOf(BOOK_STOCK_TTL.toSeconds())
        );

        return RedisReserveResult.from(result);
    }

    public RedisReserveResult initAndReserve(Long bookId, Long dbQuantity, Long requestQuantity) {
        Long result = redisTemplate.execute(
                redisScript(INIT_AND_RESERVE_SCRIPT),
                List.of(availableKey(bookId)),
                String.valueOf(dbQuantity),
                String.valueOf(requestQuantity),
                String.valueOf(BOOK_STOCK_TTL.toSeconds())
        );

        return RedisReserveResult.from(result);
    }

    public void cancel(Long bookId, Long quantity) {
        redisTemplate.execute(
                redisScript(CANCEL_SCRIPT),
                List.of(availableKey(bookId)),
                String.valueOf(quantity),
                String.valueOf(BOOK_STOCK_TTL.toSeconds())
        );
    }

    private DefaultRedisScript<Long> redisScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);           //반환형 설정인가?
        return script;
    }

    private String availableKey(Long bookId) {
        return "book:%d:available".formatted(bookId);
    }

}
