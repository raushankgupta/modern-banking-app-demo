package com.modernbank.transaction;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class TransactionHistoryController {

    private final TransactionHistoryService service;

    public TransactionHistoryController(TransactionHistoryService service) {
        this.service = service;
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionEntry> history(@PathVariable String accountNumber,
            @RequestParam(required = false) String type) {
        return service.history(accountNumber, type);
    }
}
