package com.commercecore.order.controller;

import com.commercecore.order.dto.CancelOrderRequest;
import com.commercecore.order.dto.CreateOrderRequest;
import com.commercecore.order.dto.ErrorResponse;
import com.commercecore.order.dto.OrderResponse;
import com.commercecore.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order lifecycle, placement, state machine transitions, and history")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place a new order", description = "Executes the authoritative order creation workflow: distributed idempotency guard, synchronous inventory reservation, atomic DB+Outbox commit, and synchronous payment initiation.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created and payment initiated", headers = @Header(name = "Idempotent-Replay", description = "True if replaying an existing idempotent response")),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Concurrent request in progress or idempotency conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        OrderService.OrderCreationResult result =
                orderService.createOrder(userId, request, idempotencyKey, correlationId);

        HttpHeaders headers = new HttpHeaders();
        if (result.isReplay()) {
            headers.set("Idempotent-Replay", "true");
            return ResponseEntity.ok().headers(headers).body(result.response());
        }

        return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(result.response());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details", description = "Retrieves authoritative current order details and state machine status.")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        OrderResponse response = orderService.getOrderById(id, requesterId, requesterRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user order history", description = "Retrieves a paginated list of orders placed by a specific user.")
    public ResponseEntity<Page<OrderResponse>> getUserOrders(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderResponse> orders = orderService.getUserOrders(userId, requesterId, requesterRole, pageable);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order", description = "Cancels an order, releases reserved stock, and dispatches an OrderCancelled outbox event.")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) CancelOrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        OrderResponse response = orderService.cancelOrder(id, request, requesterId, requesterRole, correlationId);
        return ResponseEntity.ok(response);
    }
}
