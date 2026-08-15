package com.commercecore.order.dto;

import java.util.List;

public class InventoryReservationDto {

    public record ReservationItem(
            Long productId,
            int quantity
    ) {}

    public record BatchReserveRequest(
            List<ReservationItem> items
    ) {}

    public record BatchReleaseRequest(
            List<ReservationItem> items
    ) {}
}
