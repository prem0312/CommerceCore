package com.commercecore.order.service;

import com.commercecore.order.exception.IdempotencyConflictException;
import com.commercecore.order.exception.RequestInProgressException;
import com.commercecore.order.model.IdempotencyRecord;
import com.commercecore.order.model.IdempotencyStatus;
import com.commercecore.order.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Distributed Idempotency Coordinator
 * Implements two-phase coordination:
 * 1. Redis SET NX for microsecond-fast distributed lock & coordination.
 * 2. PostgreSQL unique constraint (user_id, idempotency_key) as durable correctness boundary.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String REDIS_PREFIX = "idempotency:order:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final IdempotencyRecordRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final OrderMetricsService metricsService;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              StringRedisTemplate redisTemplate,
                              OrderMetricsService metricsService,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
    }

    public static record IdempotencyCheckResult<T>(
            boolean isReplay,
            T payload
    ) {}

    public String computeHash(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Establishes durable idempotency ownership BEFORE any business side effects occur.
     * Returns an existing completed response if this is an idempotent replay,
     * or empty if the request has successfully acquired ownership to proceed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<IdempotencyCheckResult<T>> acquireOwnership(
            Long userId, String idempotencyKey, String requestBody, Class<T> responseType) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty(); // Idempotency key not provided; proceed without idempotency guard
        }

        String requestHash = computeHash(requestBody);
        String redisKey = REDIS_PREFIX + userId + ":" + idempotencyKey;

        // 1. Fast path: Check Redis for existing COMPLETED or IN_PROGRESS state
        try {
            String redisVal = redisTemplate.opsForValue().get(redisKey);
            if (redisVal != null) {
                if ("IN_PROGRESS".equals(redisVal)) {
                    metricsService.recordIdempotencyConflict();
                    throw new RequestInProgressException(idempotencyKey);
                }
                // Redis holds cached JSON response
                try {
                    T cachedPayload = objectMapper.readValue(redisVal, responseType);
                    metricsService.recordIdempotencyReplay();
                    log.info("Idempotent replay served from Redis for key {}", idempotencyKey);
                    return Optional.of(new IdempotencyCheckResult<>(true, cachedPayload));
                } catch (Exception ex) {
                    log.debug("Redis cache deserialization missed, falling back to DB: {}", ex.getMessage());
                }
            }
        } catch (RequestInProgressException ripe) {
            throw ripe;
        } catch (Exception e) {
            log.warn("Redis coordination unavailable, falling back directly to durable DB: {}", e.getMessage());
        }

        // 2. Durable boundary: Check PostgreSQL
        Optional<IdempotencyRecord> existingRecord = repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);

        if (existingRecord.isPresent()) {
            IdempotencyRecord record = existingRecord.get();
            if (!record.getRequestHash().equals(requestHash)) {
                metricsService.recordIdempotencyConflict();
                log.warn("Idempotency key '{}' reused with mismatched request payload", idempotencyKey);
                throw new IdempotencyConflictException("Idempotency key was previously used with a different request payload.");
            }

            if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                metricsService.recordIdempotencyConflict();
                throw new RequestInProgressException(idempotencyKey);
            }

            if (record.getStatus() == IdempotencyStatus.COMPLETED && record.getResponsePayload() != null) {
                try {
                    T payload = objectMapper.readValue(record.getResponsePayload(), responseType);
                    metricsService.recordIdempotencyReplay();
                    log.info("Idempotent replay served from PostgreSQL for key {}", idempotencyKey);
                    return Optional.of(new IdempotencyCheckResult<>(true, payload));
                } catch (Exception e) {
                    log.error("Failed to deserialize durable idempotency response payload", e);
                }
            }
        }

        // 3. Acquire ownership: Persist IN_PROGRESS record in PostgreSQL
        try {
            Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
            IdempotencyRecord newRecord = new IdempotencyRecord(userId, idempotencyKey, requestHash, expiresAt);
            repository.saveAndFlush(newRecord);

            // Fast lock in Redis
            try {
                redisTemplate.opsForValue().set(redisKey, "IN_PROGRESS", LOCK_TTL);
            } catch (Exception ignored) {}

            log.info("Acquired idempotency ownership for user {} with key {}", userId, idempotencyKey);
            return Optional.empty(); // Acquired; proceed with business logic
        } catch (DataIntegrityViolationException dive) {
            // Concurrent race won by another thread
            metricsService.recordIdempotencyConflict();
            log.warn("Concurrent race detected on idempotency key {}", idempotencyKey);
            throw new RequestInProgressException(idempotencyKey);
        }
    }

    /**
     * Atomically marks the idempotency record as COMPLETED and caches the response.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId, String idempotencyKey, Long resourceId, Object response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;

        try {
            String jsonResponse = objectMapper.writeValueAsString(response);
            repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).ifPresent(record -> {
                record.setStatus(IdempotencyStatus.COMPLETED);
                record.setResourceId(resourceId);
                record.setResponsePayload(jsonResponse);
                repository.save(record);
            });

            // Cache result in Redis for rapid subsequent replays
            String redisKey = REDIS_PREFIX + userId + ":" + idempotencyKey;
            try {
                redisTemplate.opsForValue().set(redisKey, jsonResponse, COMPLETED_TTL);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("Failed to commit completed idempotency record for key {}", idempotencyKey, e);
        }
    }

    /**
     * Cleans up or marks FAILED so the user may retry cleanly.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;

        try {
            repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).ifPresent(record -> {
                record.setStatus(IdempotencyStatus.FAILED);
                repository.save(record);
            });

            String redisKey = REDIS_PREFIX + userId + ":" + idempotencyKey;
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
}
