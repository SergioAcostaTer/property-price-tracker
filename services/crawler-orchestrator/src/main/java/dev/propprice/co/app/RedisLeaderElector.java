package dev.propprice.co.app;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisLeaderElector {
  private final StringRedisTemplate redis;
  private final String key = "co:leader";
  private final String myId = UUID.randomUUID().toString();
  // TTL for leadership; the leader refreshes this by calling isLeader() regularly
  private final Duration ttl = Duration.ofSeconds(15);

  /**
   * Returns true iff this instance is (or becomes) the leader; also refreshes
   * TTL.
   */
  public boolean isLeader() {
    var ops = redis.opsForValue();
    Boolean ok = ops.setIfAbsent(key, myId, ttl);
    if (Boolean.TRUE.equals(ok)) {
      return true; // we acquired leadership
    }
    String curr = ops.get(key);
    if (myId.equals(curr)) {
      redis.expire(key, ttl);
      return true; // we are still leader; refresh TTL
    }
    return false; // someone else leads
  }
}
