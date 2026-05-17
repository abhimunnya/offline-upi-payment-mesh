package com.example.UPI.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class IdempotencyService {

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    @Value("${upi.mesh.idempotency-ttl-seconds:86400}")
    private long ttlSeconds;

    public boolean claim(String packetHash) {
        Instant existing = seen.putIfAbsent(packetHash, Instant.now());
        return existing == null;
    }

    public int size() {
        return seen.size();
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
        int before = seen.size();
        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        int evicted = before - seen.size();
        if (evicted > 0) {
            log.info("Idempotency cache eviction: removed {} expired entries, {} remaining",
                    evicted, seen.size());
        }
    }

    public void clear() {
        seen.clear();
        log.info("Idempotency cache cleared");
    }
}