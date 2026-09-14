package com.moveiq.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisIdempotencyService {
    private final StringRedisTemplate redis;

    public RedisIdempotencyService(StringRedisTemplate redis) { this.redis = redis; }

    public boolean firstProcessing(String namespace, String key, Duration ttl) {
        Boolean inserted = redis.opsForValue().setIfAbsent("moveiq:" + namespace + ":" + key, "1", ttl);
        return Boolean.TRUE.equals(inserted);
    }
}
