package rw.centrika.orders.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.centrika.orders.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
