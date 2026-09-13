package rw.centrika.orders.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerSummaryResponse(
    Long customerId,
    String customerName,
    BigDecimal totalSpend,
    long orderCount,
    OffsetDateTime lastOrderDate
) {}
