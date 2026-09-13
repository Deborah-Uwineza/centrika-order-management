package rw.centrika.orders.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rw.centrika.orders.domain.OrderStatus;
import rw.centrika.orders.dto.OrderRequest;
import rw.centrika.orders.dto.OrderResponse;
import rw.centrika.orders.dto.OrderStatusUpdateRequest;
import rw.centrika.orders.service.OrderService;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    @GetMapping
    public Page<OrderResponse> listOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(required = false) Long customerId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        // Hard cap regardless of what the client asks for — an
        // unbounded `size` would defeat the whole point of pagination.
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        return orderService.listOrders(status, customerId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderService.getOrderDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest request) {
        return orderService.placeOrder(request);
    }

    @PutMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}
