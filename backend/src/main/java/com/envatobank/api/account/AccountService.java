package com.envatobank.api.account;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.envatobank.api.account.dto.AccountResponse;
import com.envatobank.api.account.dto.CreateAccountRequest;
import com.envatobank.api.audit.AuditService;
import com.envatobank.api.common.error.ApiException;
import com.envatobank.api.common.error.ErrorCode;
import com.envatobank.api.common.util.MoneyUtil;
import com.envatobank.api.customer.CustomerRepository;
import com.envatobank.api.customer.Role;
import com.envatobank.api.security.AuthenticatedUser;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final AuditService auditService;

    public AccountService(AccountRepository accounts, CustomerRepository customers, AuditService auditService) {
        this.accounts = accounts;
        this.customers = customers;
        this.auditService = auditService;
    }

    @Transactional
    public AccountResponse create(UUID customerId, CreateAccountRequest request, AuthenticatedUser actor) {
        if (Role.CUSTOMER.name().equals(actor.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not permitted to open accounts");
        }
        if (!customers.existsById(customerId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Customer not found");
        }

        Instant now = Instant.now();
        Account account = new Account();
        account.setAccountNo(generateAccountNo());
        account.setCustomerId(customerId);
        account.setCurrency(request.currency());
        account.setType(request.type());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalanceMinor(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accounts.save(account);

        auditService.record("ACCOUNT_OPENED", actor.id(), actor.role(), "Account", account.getId().toString(),
                Map.of("accountNo", account.getAccountNo(), "currency", request.currency().name()));

        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listForCustomer(UUID customerId, AuthenticatedUser actor) {
        assertSameCustomer(customerId, actor);
        return accounts.findByCustomerIdOrderByCreatedAtAsc(customerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> myAccounts(AuthenticatedUser actor) {
        return accounts.findByCustomerIdOrderByCreatedAtAsc(actor.id()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID accountId, AuthenticatedUser actor) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found"));
        assertAccess(account, actor);
        return toResponse(account);
    }

    private void assertSameCustomer(UUID customerId, AuthenticatedUser actor) {
        if (Role.CUSTOMER.name().equals(actor.role()) && !actor.id().equals(customerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not permitted to read these accounts");
        }
    }

    private void assertAccess(Account account, AuthenticatedUser actor) {
        if (Role.CUSTOMER.name().equals(actor.role()) && !actor.id().equals(account.getCustomerId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not permitted to read this account");
        }
    }

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNo(),
                account.getCustomerId(),
                account.getCurrency(),
                account.getType(),
                account.getStatus(),
                account.getBalanceMinor(),
                MoneyUtil.format(account.getBalanceMinor(), account.getCurrency()),
                account.getCreatedAt());
    }

    private String generateAccountNo() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("TR");
            for (int i = 0; i < 24; i++) {
                sb.append(ThreadLocalRandom.current().nextInt(10));
            }
            String candidate = sb.toString();
            if (!accounts.existsByAccountNo(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not allocate an account number");
    }
}
