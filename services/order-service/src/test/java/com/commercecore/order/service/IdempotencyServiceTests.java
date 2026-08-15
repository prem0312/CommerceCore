package com.commercecore.order.service;

import com.commercecore.order.dto.OrderResponse;
import com.commercecore.order.exception.IdempotencyConflictException;
import com.commercecore.order.exception.RequestInProgressException;
import com.commercecore.order.model.IdempotencyRecord;
import com.commercecore.order.model.IdempotencyStatus;
import com.commercecore.order.model.OrderStatus;
import com.commercecore.order.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTests {

    @Mock
    private IdempotencyRecordRepository repository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private OrderMetricsService metricsService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private Long userId = 1L;
    private String idempotencyKey = "test-key-123";
    private String requestJson = "{\"items\":[]}";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Case 1: First request acquires ownership to process")
    void acquireOwnership_FirstRequest_Success() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());

        Optional<IdempotencyService.IdempotencyCheckResult<OrderResponse>> result =
                idempotencyService.acquireOwnership(userId, idempotencyKey, requestJson, OrderResponse.class);

        assertThat(result).isEmpty();
        verify(repository, times(1)).saveAndFlush(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Case 2: Repeated request after completion returns cached replay")
    void acquireOwnership_RepeatedRequest_ReturnsReplay() throws Exception {
        String hash = idempotencyService.computeHash(requestJson);
        IdempotencyRecord record = new IdempotencyRecord(userId, idempotencyKey, hash, Instant.now().plusSeconds(3600));
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResponsePayload("{\"id\":10}");

        OrderResponse expectedResponse = new OrderResponse(10L, 1L, OrderStatus.CONFIRMED, BigDecimal.TEN, "USD", List.of(), null, null);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(record));
        when(objectMapper.readValue("{\"id\":10}", OrderResponse.class)).thenReturn(expectedResponse);

        Optional<IdempotencyService.IdempotencyCheckResult<OrderResponse>> result =
                idempotencyService.acquireOwnership(userId, idempotencyKey, requestJson, OrderResponse.class);

        assertThat(result).isPresent();
        assertThat(result.get().isReplay()).isTrue();
        assertThat(result.get().payload().id()).isEqualTo(10L);
        verify(metricsService, times(1)).recordIdempotencyReplay();
    }

    @Test
    @DisplayName("Case 3: Concurrent request while processing throws RequestInProgressException (409)")
    void acquireOwnership_InProgress_ThrowsException() {
        String hash = idempotencyService.computeHash(requestJson);
        IdempotencyRecord record = new IdempotencyRecord(userId, idempotencyKey, hash, Instant.now().plusSeconds(3600));
        record.setStatus(IdempotencyStatus.IN_PROGRESS);

        when(valueOperations.get(anyString())).thenReturn("IN_PROGRESS");

        assertThatThrownBy(() -> idempotencyService.acquireOwnership(userId, idempotencyKey, requestJson, OrderResponse.class))
                .isInstanceOf(RequestInProgressException.class);

        verify(metricsService, times(1)).recordIdempotencyConflict();
    }

    @Test
    @DisplayName("Case 4: Reused key with different payload throws IdempotencyConflictException (409)")
    void acquireOwnership_PayloadMismatch_ThrowsConflict() {
        IdempotencyRecord record = new IdempotencyRecord(userId, idempotencyKey, "different-hash", Instant.now().plusSeconds(3600));
        record.setStatus(IdempotencyStatus.COMPLETED);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> idempotencyService.acquireOwnership(userId, idempotencyKey, requestJson, OrderResponse.class))
                .isInstanceOf(IdempotencyConflictException.class);
    }
}
