package dev.propprice.co.app;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisLeaderElector {
  private final StringRedisTemplate redis;
  private final String key = "co:leader";
  private final String myId = UUID.randomUUID().toString();
  private final Duration ttl = Duration.ofSeconds(15);

  // Atomic leader election using Lua script
  private static final String LUA_SCRIPT = """
      local key = KEYS[1]
      local my_id = ARGV[1]
      local ttl_seconds = tonumber(ARGV[2])

      local current = redis.call('GET', key)

      -- If no leader exists, claim leadership
      if current == false then
        redis.call('SET', key, my_id, 'EX', ttl_seconds)
        return 1
      end

      -- If we are the current leader, refresh TTL
      if current == my_id then
        redis.call('EXPIRE', key, ttl_seconds)
        return 1
      end

      -- Someone else is leader
      return 0
      """;

  private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

  /**
   * Returns true iff this instance is (or becomes) the leader; also refreshes TTL
   * atomically.
   */
  public boolean isLeader() {
    List<String> keys = Arrays.asList(key);
    Long result = redis.execute(script, keys, myId, String.valueOf(ttl.getSeconds()));
    return result != null && result == 1L;
  }

  /**
   * Voluntarily release leadership (for graceful shutdown)
   */
  public void releaseLeadership() {
    String current = redis.opsForValue().get(key);
    if (myId.equals(current)) {
      redis.delete(key);
    }
  }

  public String getMyId() {
    return myId;
  }
}