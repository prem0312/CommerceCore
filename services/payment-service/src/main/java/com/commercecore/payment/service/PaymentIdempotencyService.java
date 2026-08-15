package com.commercecore.payment.service;

import com.commercecore.payment.model.IdempotencyRecord;
import com.commercecore.payment.model.IdempotencyStatus;
import com.commercecore.payment.repository.IdempotencyRecordRepository;
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

@Service
public class PaymentIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIdempotencyService.class);
    private static final String REDIS_PREFIX = "idempotency:payment:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final IdempotencyRecordRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PaymentIdempotencyService(IdempotencyRecordRepository repository,
                                    StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<IdempotencyCheckResult<T>> acquireOwnership(
            Long userId, String idempotencyKey, String requestBody, Class<T> responseType) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        String requestHash = computeHash(requestBody);
        String redisKey = REDIS_PREFIX + userId + ":" + idempotencyKey;

        // 1. Check Redis
        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null && !"IN_PROGRESS".equals(cached)) {
                T payload = objectMapper.readValue(cached, responseType);
                log.info("Idempotent replay served from Redis for payment key {}", idempotencyKey);
                return Optional.of(new IdempotencyCheckResult<>(true, payload));
            }
        } catch (Exception ignored) {}

        // 2. Check DB
        Optional<IdempotencyRecord> recordOpt = repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();
            if (record.getStatus() == IdempotencyStatus.COMPLETED && record.getResponsePayload() != null) {
                try {
                    T payload = objectMapper.readValue(record.getResponsePayload(), responseType);
                    log.info("Idempotent replay served from DB for payment key {}", idempotencyKey);
                    return Optional.of(new IdempotencyCheckResult<>(true, payload));
                } catch (Exception ignored) {}
            }
        }

        // 3. Insert IN_PROGRESS record
        try {
            Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
            IdempotencyRecord newRecord = new IdempotencyRecord(userId, idempotencyKey, requestHash, expiresAt);
            repository.saveAndFlush(newRecord);

            try {
                redisTemplate.opsForValue().set(redisKey, "IN_PROGRESS", LOCK_TTL);
            } catch (Exception ignored) {}

            return Optional.empty();
        } catch (DataIntegrityViolationException dive) {
            log.warn("Concurrent duplicate payment request detected for key {}", idempotencyKey);
            return Optional.empty();
        }
    }

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

            String redisKey = REDIS_PREFIX + userId + ":" + idempotencyKey;
            try {
                redisTemplate.opsForValue().set(redisKey, jsonResponse, COMPLETED_TTL);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
}
