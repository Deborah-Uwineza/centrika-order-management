package rw.centrika.orders.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.centrika.orders.domain.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
