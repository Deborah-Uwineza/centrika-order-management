package rw.centrika.orders.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.centrika.orders.domain.Order;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * Overrides the default JpaSpecificationExecutor method purely to
     * attach an EntityGraph: without it, rendering each row's customer
     * name in the list view would fire one extra SELECT per row (N+1).
     * The WHERE clause still comes entirely from the Specification, so
     * filtering/pagination stays server-side.
     */
    @EntityGraph(attributePaths = "customer")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    // Full detail view: customer + items + each item's product, in one
    // round trip, for GET /api/orders/{id}.
    @Query("""
        SELECT DISTINCT o FROM Order o
        JOIN FETCH o.customer
        LEFT JOIN FETCH o.items i
        LEFT JOIN FETCH i.product
        WHERE o.id = :id
        """)
    Optional<Order> findDetailedById(@Param("id") Long id);

    /**
     * Single aggregate query for GET /api/customers/{id}/summary — total
     * spend, order count, and last order date computed in the database
     * rather than pulling every order into the application to sum in
     * Java. Cancelled orders are excluded from spend, consistent with
     * queries.sql.
     *
     * Uses three correlated subqueries against Customer, rather than
     * joining Order -> OrderItem directly. Two reasons:
     *   1. A customer with zero orders would make a direct JOIN version
     *      return NO rows at all (not a row of zeros), so COALESCE never
     *      even gets a chance to kick in.
     *   2. Selecting FROM Customer (already validated to exist by the
     *      caller) guarantees exactly one row back, every time.
     */
    @Query("""
        SELECT
            COALESCE((SELECT SUM(oi.unitPrice * oi.quantity)
                      FROM OrderItem oi
                      WHERE oi.order.customer.id = :customerId
                        AND oi.order.status <> rw.centrika.orders.domain.OrderStatus.CANCELLED), 0),
            (SELECT COUNT(o.id) FROM Order o
             WHERE o.customer.id = :customerId
               AND o.status <> rw.centrika.orders.domain.OrderStatus.CANCELLED),
            (SELECT MAX(o.createdAt) FROM Order o
             WHERE o.customer.id = :customerId
               AND o.status <> rw.centrika.orders.domain.OrderStatus.CANCELLED)
        FROM Customer c
        WHERE c.id = :customerId
        """)
    List<Object[]> aggregateCustomerSummary(@Param("customerId") Long customerId);

    /**
     * Totals for a whole page of orders in a single query (GROUP BY),
     * instead of one query per order. Used by OrderService to enrich the
     * list view without turning "20 orders per page" into "21 queries".
     */
    @Query("""
        SELECT oi.order.id, SUM(oi.unitPrice * oi.quantity)
        FROM OrderItem oi
        WHERE oi.order.id IN :orderIds
        GROUP BY oi.order.id
        """)
    List<Object[]> sumTotalsForOrderIds(@Param("orderIds") List<Long> orderIds);
}