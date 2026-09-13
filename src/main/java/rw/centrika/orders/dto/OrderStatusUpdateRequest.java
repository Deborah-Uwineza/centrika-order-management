package rw.centrika.orders.dto;

import jakarta.validation.constraints.NotNull;
import rw.centrika.orders.domain.OrderStatus;

public record OrderStatusUpdateRequest(

    @NotNull(message = "status is required")
    OrderStatus status
) {}
