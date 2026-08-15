package com.commercecore.inventory.controller;

import com.commercecore.inventory.dto.*;
import com.commercecore.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Stock level management, optimistic locking reservations and releases")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get stock status for a product", description = "Retrieves available, reserved, and threshold stock levels with Redis Cache-Aside.")
    public ResponseEntity<InventoryResponse> getInventoryByProductId(@PathVariable Long productId) {
        InventoryResponse response = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/reserve")
    @Operation(summary = "Reserve stock for order checkout", description = "Safely reserves stock using optimistic concurrency control.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock reserved successfully"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock or concurrent modification", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InventoryResponse> reserveStock(
            @PathVariable Long productId,
            @Valid @RequestBody ReserveStockRequest request) {

        InventoryResponse response = inventoryService.reserveStock(productId, request.quantity());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/release")
    @Operation(summary = "Release reserved stock", description = "Returns previously reserved stock back to available stock pool.")
    public ResponseEntity<InventoryResponse> releaseStock(
            @PathVariable Long productId,
            @Valid @RequestBody ReleaseStockRequest request) {

        InventoryResponse response = inventoryService.releaseStock(productId, request.quantity());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/batch-reserve")
    @Operation(summary = "Atomically reserve multiple order items", description = "Reserves stock for multiple items in deadlock-free sorted order. Rolls back entirely if any item lacks stock.")
    public ResponseEntity<Void> batchReserve(@Valid @RequestBody BatchReserveRequest request) {
        inventoryService.batchReserve(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch-release")
    @Operation(summary = "Batch release multiple order items", description = "Releases multiple reserved items back into available inventory.")
    public ResponseEntity<Void> batchRelease(@Valid @RequestBody BatchReleaseRequest request) {
        inventoryService.batchRelease(request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{productId}/adjust")
    @Operation(summary = "Adjust inventory stock (Admin only)", description = "Sets new stock levels and threshold. Requires ADMIN role.")
    public ResponseEntity<InventoryResponse> adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody AdjustStockRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        InventoryResponse response = inventoryService.adjustStock(productId, request, requesterRole);
        return ResponseEntity.ok(response);
    }
}
