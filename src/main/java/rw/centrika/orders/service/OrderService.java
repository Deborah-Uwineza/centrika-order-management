package rw.centrika.orders.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.centrika.orders.domain.*;
import rw.centrika.orders.dto.*;
import rw.centrika.orders.exception.InvalidStatusTransitionException;
import rw.centrika.orders.exception.ResourceNotFoundException;
import rw.centrika.orders.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    // -----------------------------------------------------------------
    // GET /api/orders — server-side filtered + paginated list
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(OrderStatus status, Long customerId,
                                           OffsetDateTime from, OffsetDateTime to,
                                           Pageable pageable) {
        Page<Order> page = orderRepository.findAll(
            OrderSpecifications.build(status, customerId, from, to), pageable);

        List<Long> orderIds = page.getContent().stream().map(Order::getId).toList();

        // One extra query for the whole page, not one per row (see
        // OrderRepository.sumTotalsForOrderIds Javadoc). Skipped entirely
        // on an empty page — an empty IN(...) clause is invalid SQL.
        Map<Long, BigDecimal> totalsByOrderId = orderIds.isEmpty()
            ? Map.of()
            : orderRepository.sumTotalsForOrderIds(orderIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));

        return page.map(order -> OrderResponse.summary(
            order, totalsByOrderId.getOrDefault(order.getId(), BigDecimal.ZERO)));
    }

    // -----------------------------------------------------------------
    // GET /api/orders/{id} — full detail including line items
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(Long id) {
        Order order = orderRepository.findDetailedById(id)
            .orElseThrow(() -> ResourceNotFoundException.forEntity("Order", id));
        return OrderResponse.detailed(order);
    }

    // -----------------------------------------------------------------
    // POST /api/orders — create order + atomically deduct stock
    // -----------------------------------------------------------------
    // See DESIGN.md ("Concurrency & stock deduction") for the full
    // pessimistic-vs-optimistic discussion. Summary of what happens here:
    //
    //   1. We lock every product row involved BEFORE checking any stock,
    //      using SELECT ... FOR UPDATE (ProductRepository.findByIdForUpdate).
    //      A second, concurrent request for the same product blocks at
    //      the database level until this transaction commits — so two
    //      requests can never both read "stock = 1" and both succeed.
    //   2. Products are locked in ascending id order regardless of the
    //      order they appear in the request. Two orders that both touch
    //      products {5, 9} will therefore always try to lock 5 before 9,
    //      which is what prevents a classic lock-ordering deadlock (order
    //      A locks 5 then waits on 9, order B locks 9 then waits on 5).
    //   3. Everything happens in one @Transactional method: if any line
    //      item fails (insufficient stock, unknown product), the whole
    //      order — including any stock already deducted in this method —
    //      is rolled back.
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
            .orElseThrow(() -> ResourceNotFoundException.forEntity("Customer", request.customerId()));

        List<OrderItemRequest> sortedItems = request.items().stream()
            .sorted(Comparator.comparing(OrderItemRequest::productId))
            .toList();

        Order order = Order.builder()
            .customer(customer)
            .status(OrderStatus.PENDING)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        for (OrderItemRequest itemRequest : sortedItems) {
            Product product = productRepository.findByIdForUpdate(itemRequest.productId())
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Product", itemRequest.productId()));

            product.deductStock(itemRequest.quantity()); // throws InsufficientStockException if not enough
            productRepository.save(product);

            OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(itemRequest.quantity())
                .unitPrice(product.getUnitPrice()) // frozen at purchase time
                .build();
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);
        return getOrderDetail(saved.getId());
    }

    // -----------------------------------------------------------------
    // PUT /api/orders/{id}/status
    // -----------------------------------------------------------------
    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.forEntity("Order", id));

        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(order.getStatus(), newStatus);
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        return getOrderDetail(order.getId());
    }
}
