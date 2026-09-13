package rw.centrika.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderRequest(

    @NotNull(message = "customerId is required")
    Long customerId,

    @NotEmpty(message = "items must contain at least one line item")
    @Valid
    List<OrderItemRequest> items
) {}
