package rw.centrika.orders.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rw.centrika.orders.dto.CustomerSummaryResponse;
import rw.centrika.orders.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/{id}/summary")
    public CustomerSummaryResponse getSummary(@PathVariable Long id) {
        return customerService.getSummary(id);
    }
}
