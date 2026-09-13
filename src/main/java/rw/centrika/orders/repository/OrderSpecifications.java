package rw.centrika.orders.repository;

import org.springframework.data.jpa.domain.Specification;
import rw.centrika.orders.domain.Order;
import rw.centrika.orders.domain.OrderStatus;

import java.time.OffsetDateTime;


public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) ->
            status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasCustomerId(Long customerId) {
        return (root, query, cb) ->
            customerId == null ? null : cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Order> createdFrom(OffsetDateTime from) {
        return (root, query, cb) ->
            from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdTo(OffsetDateTime to) {
        return (root, query, cb) ->
            to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Order> build(OrderStatus status, Long customerId,
                                              OffsetDateTime from, OffsetDateTime to) {
        return Specification.where(hasStatus(status))
            .and(hasCustomerId(customerId))
            .and(createdFrom(from))
            .and(createdTo(to));
    }
}
