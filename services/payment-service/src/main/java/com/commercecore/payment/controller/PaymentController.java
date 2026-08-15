package com.commercecore.payment.controller;

import com.commercecore.payment.dto.ErrorResponse;
import com.commercecore.payment.dto.InitiatePaymentRequest;
import com.commercecore.payment.dto.PaymentResponse;
import com.commercecore.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment simulation, transactional outbox publishing and idempotency")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Initiate payment transaction", description = "Processes payment idempotently, atomically commits payment and Outbox event.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment processed", headers = @Header(name = "Idempotent-Replay", description = "True if replaying previous result")),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        PaymentService.PaymentProcessResult result =
                paymentService.processPayment(userId, request, idempotencyKey, correlationId);

        HttpHeaders headers = new HttpHeaders();
        if (result.isReplay()) {
            headers.set("Idempotent-Replay", "true");
            return ResponseEntity.ok().headers(headers).body(result.response());
        }

        return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(result.response());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment details by ID")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment details by Order ID")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable Long orderId) {
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }
}
