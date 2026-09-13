package rw.centrika.orders.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.centrika.orders.domain.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * SELECT ... FOR UPDATE. Any other transaction calling this for the
     * same product id blocks until the first transaction commits or
     * rolls back — that's what makes "two concurrent orders for the
     * last unit of stock" resolve safely instead of both succeeding.
     * See OrderService.placeOrder() and DESIGN.md for the trade-off
     * against optimistic locking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
