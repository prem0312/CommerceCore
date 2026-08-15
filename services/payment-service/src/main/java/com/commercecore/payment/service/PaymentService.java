package com.commercecore.payment.service;

import com.commercecore.payment.dto.InitiatePaymentRequest;
import com.commercecore.payment.dto.PaymentResponse;
import com.commercecore.payment.event.EventEnvelope;
import com.commercecore.payment.event.PaymentEventPayloads;
import com.commercecore.payment.exception.PaymentNotFoundException;
import com.commercecore.payment.model.OutboxEvent;
import com.commercecore.payment.model.Payment;
import com.commercecore.payment.model.PaymentStatus;
import com.commercecore.payment.repository.OutboxEventRepository;
import com.commercecore.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final BigDecimal SIMULATED_MAX_AMOUNT = new BigDecimal("10000.00");

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxRepository;
    private final PaymentIdempotencyService idempotencyService;
    private final PaymentMetricsService metricsService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          OutboxEventRepository outboxRepository,
                          PaymentIdempotencyService idempotencyService,
                          PaymentMetricsService metricsService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.idempotencyService = idempotencyService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
    }

    public static record PaymentProcessResult(
            boolean isReplay,
            PaymentResponse response
    ) {}

    public PaymentProcessResult processPayment(
            Long userId,
            InitiatePaymentRequest request,
            String idempotencyKey,
            String correlationId) {

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            requestJson = request.toString();
        }

        // 1. Check Distributed Idempotency
        Optional<PaymentIdempotencyService.IdempotencyCheckResult<PaymentResponse>> replay =
                idempotencyService.acquireOwnership(userId, idempotencyKey, requestJson, PaymentResponse.class);

        if (replay.isPresent()) {
            return new PaymentProcessResult(true, replay.get().payload());
        }

        // 2. Execute Payment Simulation and persist Payment + OutboxEvent atomically
        Payment savedPayment = executePaymentTransaction(request, idempotencyKey, correlationId);
        PaymentResponse response = PaymentResponse.from(savedPayment);

        // 3. Mark idempotency record as COMPLETED
        idempotencyService.recordSuccess(userId, idempotencyKey, savedPayment.getId(), response);

        if (savedPayment.getStatus() == PaymentStatus.SUCCESS) {
            metricsService.recordPaymentSuccess();
        } else {
            metricsService.recordPaymentFailure();
        }

        return new PaymentProcessResult(false, response);
    }

    @Transactional
    public Payment executePaymentTransaction(InitiatePaymentRequest request, String idempotencyKey, String correlationId) {
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        Payment payment = new Payment(
                request.orderId(),
                transactionId,
                request.amount(),
                request.currency(),
                idempotencyKey
        );

        // Simulate payment logic: amounts > 10,000 fail simulation (e.g. credit limit exceeded)
        boolean isSuccess = request.amount().compareTo(SIMULATED_MAX_AMOUNT) <= 0;

        if (isSuccess) {
            payment.setStatus(PaymentStatus.SUCCESS);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Transaction amount exceeds simulated credit limit of $10,000.00");
        }

        Payment savedPayment = paymentRepository.save(payment);

        // Create Outbox Event
        try {
            if (isSuccess) {
                PaymentEventPayloads.PaymentCompletedPayload payload = new PaymentEventPayloads.PaymentCompletedPayload(
                        savedPayment.getId(),
                        savedPayment.getOrderId(),
                        savedPayment.getTransactionId(),
                        savedPayment.getAmount(),
                        savedPayment.getCurrency(),
                        savedPayment.getCreatedAt()
                );

                EventEnvelope<PaymentEventPayloads.PaymentCompletedPayload> envelope =
                        EventEnvelope.of("PaymentCompleted", String.valueOf(savedPayment.getOrderId()), correlationId, payload);

                OutboxEvent outboxEvent = new OutboxEvent(
                        "PaymentCompleted",
                        String.valueOf(savedPayment.getOrderId()),
                        envelope.correlationId(),
                        objectMapper.writeValueAsString(envelope)
                );
                outboxRepository.save(outboxEvent);
            } else {
                PaymentEventPayloads.PaymentFailedPayload payload = new PaymentEventPayloads.PaymentFailedPayload(
                        savedPayment.getId(),
                        savedPayment.getOrderId(),
                        savedPayment.getTransactionId(),
                        savedPayment.getAmount(),
                        savedPayment.getFailureReason(),
                        savedPayment.getCreatedAt()
                );

                EventEnvelope<PaymentEventPayloads.PaymentFailedPayload> envelope =
                        EventEnvelope.of("PaymentFailed", String.valueOf(savedPayment.getOrderId()), correlationId, payload);

                OutboxEvent outboxEvent = new OutboxEvent(
                        "PaymentFailed",
                        String.valueOf(savedPayment.getOrderId()),
                        envelope.correlationId(),
                        objectMapper.writeValueAsString(envelope)
                );
                outboxRepository.save(outboxEvent);
            }

            metricsService.recordOutboxPending();
        } catch (Exception ex) {
            log.error("Failed to generate payment outbox event: ", ex);
            throw new RuntimeException("Payment outbox serialization failed", ex);
        }

        log.info("Payment for Order ID {} processed as {} with TXN: {}",
                savedPayment.getOrderId(), savedPayment.getStatus(), savedPayment.getTransactionId());

        return savedPayment;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment record not found for Order ID: " + orderId));
        return PaymentResponse.from(payment);
    }
}
