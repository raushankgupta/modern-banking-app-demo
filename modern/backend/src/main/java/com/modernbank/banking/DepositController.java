package com.modernbank.banking;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class DepositController {

    private final DepositService service;

    public DepositController(DepositService service) {
        this.service = service;
    }

    @PostMapping("/{accountNumber}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionReceipt deposit(@PathVariable String accountNumber, @RequestBody AmountRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.deposit(accountNumber, request.amount(), request.description(), jwt.getSubject());
    }
}
