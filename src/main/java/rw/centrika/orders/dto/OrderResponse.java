package rw.centrika.orders.dto;

import rw.centrika.orders.domain.Order;
import rw.centrika.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    Long customerId,
    String customerName,
    OrderStatus status,
    BigDecimal totalAmount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<OrderItemResponse> items
) {
    /** Summary view for list endpoints — omits items to keep list payloads light. */
    public static OrderResponse summary(Order order, BigDecimal totalAmount) {
        return new OrderResponse(
            order.getId(),
            order.getCustomer().getId(),
            order.getCustomer().getName(),
            order.getStatus(),
            totalAmount,
            order.getCreatedAt(),
            order.getUpdatedAt(),
            null
        );
    }

    /** Full view for GET /api/orders/{id} — includes line items. */
    public static OrderResponse detailed(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(OrderItemResponse::from)
            .toList();

        BigDecimal total = itemResponses.stream()
            .map(OrderItemResponse::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderResponse(
            order.getId(),
            order.getCustomer().getId(),
            order.getCustomer().getName(),
            order.getStatus(),
            total,
            order.getCreatedAt(),
            order.getUpdatedAt(),
            itemResponses
        );
    }
}
