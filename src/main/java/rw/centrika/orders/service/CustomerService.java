package rw.centrika.orders.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.centrika.orders.domain.Customer;
import rw.centrika.orders.dto.CustomerSummaryResponse;
import rw.centrika.orders.exception.ResourceNotFoundException;
import rw.centrika.orders.repository.CustomerRepository;
import rw.centrika.orders.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public CustomerSummaryResponse getSummary(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> ResourceNotFoundException.forEntity("Customer", customerId));

        
        List<Object[]> rows = orderRepository.aggregateCustomerSummary(customerId);
        Object[] row = rows.isEmpty() ? new Object[]{BigDecimal.ZERO, 0L, null} : rows.get(0);

        BigDecimal totalSpend = (BigDecimal) row[0];
        long orderCount = (long) row[1];
        OffsetDateTime lastOrderDate = (OffsetDateTime) row[2];

        return new CustomerSummaryResponse(
            customer.getId(),
            customer.getName(),
            totalSpend,
            orderCount,
            lastOrderDate
        );
    }
}