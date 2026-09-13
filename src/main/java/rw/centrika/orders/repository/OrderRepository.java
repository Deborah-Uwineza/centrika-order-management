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

   
    @EntityGraph(attributePaths = "customer")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    
    @Query("""
        SELECT DISTINCT o FROM Order o
        JOIN FETCH o.customer
        LEFT JOIN FETCH o.items i
        LEFT JOIN FETCH i.product
        WHERE o.id = :id
        """)
    Optional<Order> findDetailedById(@Param("id") Long id);

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

    
    @Query("""
        SELECT oi.order.id, SUM(oi.unitPrice * oi.quantity)
        FROM OrderItem oi
        WHERE oi.order.id IN :orderIds
        GROUP BY oi.order.id
        """)
    List<Object[]> sumTotalsForOrderIds(@Param("orderIds") List<Long> orderIds);
}