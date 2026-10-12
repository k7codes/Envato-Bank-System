package com.envatobank.api.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.envatobank.api.account.Account;
import com.envatobank.api.account.AccountRepository;
import com.envatobank.api.common.error.ApiException;
import com.envatobank.api.common.error.ErrorCode;
import com.envatobank.api.customer.Role;
import com.envatobank.api.ledger.dto.LedgerEntryResponse;
import com.envatobank.api.security.AuthenticatedUser;

@Service
public class LedgerService {

    private static final String CURRENCY_MISMATCH = "Ledger posting requires matching currencies";

    private final LedgerRepository ledgerRepository;
    private final AccountRepository accountRepository;

    public LedgerService(LedgerRepository ledgerRepository, AccountRepository accountRepository) {
        this.ledgerRepository = ledgerRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Posts a balanced double-entry pair (debit source, credit target) and moves
     * the corresponding balances. Both accounts must already be locked by the
     * caller inside the current transaction.
     */
    @Transactional
    public void postTransfer(UUID transactionId, UUID transferId, Account source, Account target,
            long amountMinor, String description) {

        if (source.getCurrency() != target.getCurrency()) {
            throw new IllegalArgumentException(CURRENCY_MISMATCH);
        }
        Instant now = Instant.now();

        source.setBalanceMinor(source.getBalanceMinor() - amountMinor);
        source.setUpdatedAt(now);
        target.setBalanceMinor(target.getBalanceMinor() + amountMinor);
        target.setUpdatedAt(now);
        accountRepository.save(source);
        accountRepository.save(target);

        ledgerRepository.save(entry(transactionId, transferId, source, LedgerEntryType.DEBIT,
                amountMinor, source.getBalanceMinor(), description, now));
        ledgerRepository.save(entry(transactionId, transferId, target, LedgerEntryType.CREDIT,
                amountMinor, target.getBalanceMinor(), description, now));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> statement(UUID accountId, AuthenticatedUser actor, Pageable pageable) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found"));
        if (Role.CUSTOMER.name().equals(actor.role()) && !actor.id().equals(account.getCustomerId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not permitted to read this statement");
        }
        return ledgerRepository.findByAccountIdOrderByCreatedAtDescIdDesc(accountId, pageable).stream()
                .map(entry -> new LedgerEntryResponse(
                        entry.getCreatedAt(),
                        entry.getEntryType(),
                        entry.getAmountMinor(),
                        entry.getCurrency(),
                        entry.getBalanceAfterMinor(),
                        entry.getDescription(),
                        entry.getTransferId(),
                        entry.getTransactionId()))
                .toList();
    }

    private LedgerEntry entry(UUID transactionId, UUID transferId, Account account, LedgerEntryType type,
            long amountMinor, long balanceAfter, String description, Instant now) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransactionId(transactionId);
        entry.setTransferId(transferId);
        entry.setAccountId(account.getId());
        entry.setEntryType(type);
        entry.setAmountMinor(amountMinor);
        entry.setCurrency(account.getCurrency());
        entry.setBalanceAfterMinor(balanceAfter);
        entry.setDescription(description);
        entry.setCreatedAt(now);
        return entry;
    }
}
