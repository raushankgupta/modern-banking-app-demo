package com.modernbank.customer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/{customerId}")
    public CustomerWithAccounts byId(@PathVariable String customerId) {
        return service.findById(customerId);
    }

    @GetMapping("/by-account/{accountNumber}")
    public CustomerWithAccounts byAccountNumber(@PathVariable String accountNumber) {
        return service.findByAccountNumber(accountNumber);
    }
}
