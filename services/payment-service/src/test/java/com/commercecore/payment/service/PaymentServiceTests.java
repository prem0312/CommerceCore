package com.commercecore.payment.service;

import com.commercecore.payment.dto.InitiatePaymentRequest;
import com.commercecore.payment.dto.PaymentResponse;
import com.commercecore.payment.model.OutboxEvent;
import com.commercecore.payment.model.Payment;
import com.commercecore.payment.model.PaymentStatus;
import com.commercecore.payment.repository.OutboxEventRepository;
import com.commercecore.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private PaymentIdempotencyService idempotencyService;

    @Mock
    private PaymentMetricsService metricsService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should process successful payment, save outbox event and update metrics")
    void processPayment_Success() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(10L, new BigDecimal("150.00"), "USD");

        when(idempotencyService.acquireOwnership(eq(1L), eq("pay-key-1"), any(), eq(PaymentResponse.class)))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(100L);
            return p;
        });

        PaymentService.PaymentProcessResult result =
                paymentService.processPayment(1L, request, "pay-key-1", "corr-1");

        assertThat(result.isReplay()).isFalse();
        assertThat(result.response().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.response().transactionId()).isNotBlank();

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
        verify(metricsService, times(1)).recordPaymentSuccess();
        verify(idempotencyService, times(1)).recordSuccess(eq(1L), eq("pay-key-1"), eq(100L), any());
    }

    @Test
    @DisplayName("Should mark payment as FAILED when exceeding credit threshold")
    void processPayment_OverCreditLimit_Fails() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(10L, new BigDecimal("15000.00"), "USD");

        when(idempotencyService.acquireOwnership(eq(1L), eq("pay-key-2"), any(), eq(PaymentResponse.class)))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(101L);
            return p;
        });

        PaymentService.PaymentProcessResult result =
                paymentService.processPayment(1L, request, "pay-key-2", "corr-2");

        assertThat(result.response().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.response().failureReason()).contains("exceeds simulated credit limit");
        verify(metricsService, times(1)).recordPaymentFailure();
    }
}
