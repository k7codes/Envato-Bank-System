package com.envatobank.api.customer;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.envatobank.api.common.web.PageResponse;
import com.envatobank.api.customer.dto.CreateCustomerRequest;
import com.envatobank.api.customer.dto.CustomerResponse;
import com.envatobank.api.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(request, CurrentUser.get());
    }

    @GetMapping("/me")
    public CustomerResponse me() {
        return customerService.get(CurrentUser.get().id(), CurrentUser.get());
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerService.get(id, CurrentUser.get());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public PageResponse<CustomerResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(customerService.list(pageable, CurrentUser.get()));
    }
}
