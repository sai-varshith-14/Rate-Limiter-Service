package com.platform.ratelimiter.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final ConcurrentHashMap<String, KeyStats> statsMap = new ConcurrentHashMap<>();

    public static class KeyStats {
        private final LongAdder allowed = new LongAdder();
        private final LongAdder blocked = new LongAdder();

        public void incrementAllowed() {
            allowed.increment();
        }

        public void incrementBlocked() {
            blocked.increment();
        }

        public long getAllowed() {
            return allowed.sum();
        }

        public long getBlocked() {
            return blocked.sum();
        }
    }

    public void recordAllowed(String key) {
        statsMap.computeIfAbsent(key, k -> new KeyStats()).incrementAllowed();
    }

    public void recordBlocked(String key) {
        statsMap.computeIfAbsent(key, k -> new KeyStats()).incrementBlocked();
    }

    public Map<String, StatSummary> getAllStats() {
        return statsMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new StatSummary(e.getValue().getAllowed(), e.getValue().getBlocked())
                ));
    }

    public record StatSummary(long allowed, long blocked) {}
}