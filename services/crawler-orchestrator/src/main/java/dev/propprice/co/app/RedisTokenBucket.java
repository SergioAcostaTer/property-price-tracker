package dev.propprice.co.app;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Simple token-bucket (tokens/sec, burst=capacity). Returns true if one token
 * was consumed.
 * Keys used: "tb:{key}:tokens", "tb:{key}:ts"
 */
@Component
@RequiredArgsConstructor
public class RedisTokenBucket {
  private final StringRedisTemplate redis;

  private static final String LUA = """
      local tokens_key = KEYS[1]
      local ts_key     = KEYS[2]
      local rate       = tonumber(ARGV[1])   -- tokens per second
      local capacity   = tonumber(ARGV[2])   -- bucket size
      local now_ms     = tonumber(ARGV[3])   -- current time millis
      local one        = 1

      local tokens = tonumber(redis.call('GET', tokens_key))
      local ts     = tonumber(redis.call('GET', ts_key))

      if tokens == nil then tokens = capacity end
      if ts == nil then ts = now_ms end

      local delta = math.max(0, now_ms - ts)
      local refill = delta * (rate / 1000.0)
      tokens = math.min(capacity, tokens + refill)
      if tokens < 1.0 then
        redis.call('SET', tokens_key, tokens)
        redis.call('SET', ts_key, now_ms)
        return 0
      else
        tokens = tokens - 1.0
        redis.call('SET', tokens_key, tokens)
        redis.call('SET', ts_key, now_ms)
        return 1
      end
      """;

  private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);

  public boolean allow(String key, double tokensPerSec, int capacity) {
    long now = Instant.now().toEpochMilli();
    String tk = "tb:" + key + ":tokens";
    String ts = "tb:" + key + ":ts";
    List<String> keys = Arrays.asList(tk, ts);
    Long ok = redis.execute(script, keys, String.valueOf(tokensPerSec), String.valueOf(capacity), String.valueOf(now));
    return ok != null && ok == 1L;
  }
}
