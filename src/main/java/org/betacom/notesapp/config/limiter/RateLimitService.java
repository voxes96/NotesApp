package org.betacom.notesapp.config.limiter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
    private final RateLimitProperties rateLimitProperties;

    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> createNewBucket());
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimitProperties.getCapacity())
                .refillIntervally(
                        rateLimitProperties.getRefillTokens(),
                        Duration.ofMinutes(rateLimitProperties.getRefillMinutes()))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
