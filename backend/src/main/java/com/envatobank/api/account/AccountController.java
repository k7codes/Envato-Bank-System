package com.envatobank.api.account;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.envatobank.api.account.dto.AccountResponse;
import com.envatobank.api.account.dto.CreateAccountRequest;
import com.envatobank.api.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/customers/{customerId}/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public AccountResponse create(@PathVariable UUID customerId, @Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(customerId, request, CurrentUser.get());
    }

    @GetMapping("/customers/{customerId}/accounts")
    public List<AccountResponse> listForCustomer(@PathVariable UUID customerId) {
        return accountService.listForCustomer(customerId, CurrentUser.get());
    }

    @GetMapping("/accounts")
    public List<AccountResponse> myAccounts() {
        return accountService.myAccounts(CurrentUser.get());
    }

    @GetMapping("/accounts/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return accountService.get(id, CurrentUser.get());
    }
}
