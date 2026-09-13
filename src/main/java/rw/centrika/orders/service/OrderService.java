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

   
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(OrderStatus status, Long customerId,
                                           OffsetDateTime from, OffsetDateTime to,
                                           Pageable pageable) {
        Page<Order> page = orderRepository.findAll(
            OrderSpecifications.build(status, customerId, from, to), pageable);

        List<Long> orderIds = page.getContent().stream().map(Order::getId).toList();

       
        Map<Long, BigDecimal> totalsByOrderId = orderIds.isEmpty()
            ? Map.of()
            : orderRepository.sumTotalsForOrderIds(orderIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));

        return page.map(order -> OrderResponse.summary(
            order, totalsByOrderId.getOrDefault(order.getId(), BigDecimal.ZERO)));
    }

  
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(Long id) {
        Order order = orderRepository.findDetailedById(id)
            .orElseThrow(() -> ResourceNotFoundException.forEntity("Order", id));
        return OrderResponse.detailed(order);
    }

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
