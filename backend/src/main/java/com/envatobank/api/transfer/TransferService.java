package com.envatobank.api.transfer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.envatobank.api.account.Account;
import com.envatobank.api.account.AccountRepository;
import com.envatobank.api.account.AccountStatus;
import com.envatobank.api.account.CurrencyCode;
import com.envatobank.api.audit.AuditService;
import com.envatobank.api.common.error.ApiException;
import com.envatobank.api.common.error.ErrorCode;
import com.envatobank.api.config.TransferProperties;
import com.envatobank.api.customer.Role;
import com.envatobank.api.ledger.LedgerService;
import com.envatobank.api.security.AuthenticatedUser;
import com.envatobank.api.transfer.dto.TransferRequest;
import com.envatobank.api.transfer.dto.TransferResponse;

@Service
public class TransferService {

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final TransferProperties limits;

    public TransferService(TransferRepository transfers, AccountRepository accounts, LedgerService ledgerService,
            AuditService auditService, TransferProperties limits) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.limits = limits;
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request, AuthenticatedUser actor) {
        Account source = accounts.findById(request.sourceAccountId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Source account not found"));
        assertOwnership(source, actor);

        Account target = accounts.findByAccountNo(request.targetAccountNo().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Target account not found"));

        Optional<Transfer> existing = transfers.findByCreatedByAndIdempotencyKey(actor.id(), request.idempotencyKey());
        if (existing.isPresent()) {
            return replayIdempotent(existing.get(), source, target, request);
        }

        if (request.amountMinor() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Amount must be positive");
        }
        if (source.getId().equals(target.getId())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Source and target accounts must differ");
        }

        // Lock both accounts in a deterministic order to avoid deadlocks.
        UUID firstId = source.getId().compareTo(target.getId()) <= 0 ? source.getId() : target.getId();
        UUID secondId = source.getId().compareTo(target.getId()) <= 0 ? target.getId() : source.getId();
        Account lockedFirst = accounts.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found"));
        Account lockedSecond = accounts.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found"));
        Account lockedSource = lockedFirst.getId().equals(source.getId()) ? lockedFirst : lockedSecond;
        Account lockedTarget = lockedFirst.getId().equals(target.getId()) ? lockedFirst : lockedSecond;

        if (lockedSource.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Source account is not active");
        }
        if (lockedTarget.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Target account is not active");
        }
        if (lockedSource.getCurrency() != lockedTarget.getCurrency()) {
            throw new ApiException(ErrorCode.CURRENCY_MISMATCH,
                    "Source and target accounts must use the same currency");
        }

        long amount = request.amountMinor();
        CurrencyCode currency = lockedSource.getCurrency();

        if (amount > limits.perTransactionLimitMinor()) {
            throw new ApiException(ErrorCode.LIMIT_EXCEEDED, "Amount exceeds the per-transaction limit");
        }
        Instant startOfDay = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        long alreadyMoved = transfers.sumCompletedSince(lockedSource.getId(), startOfDay);
        if (alreadyMoved + amount > limits.dailyLimitMinor()) {
            throw new ApiException(ErrorCode.LIMIT_EXCEEDED, "Amount exceeds the daily limit");
        }
        if (lockedSource.getBalanceMinor() < amount) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds");
        }

        Instant now = Instant.now();
        Transfer transfer = new Transfer();
        transfer.setReference(generateReference());
        transfer.setSourceAccountId(lockedSource.getId());
        transfer.setTargetAccountId(lockedTarget.getId());
        transfer.setAmountMinor(amount);
        transfer.setCurrency(currency);
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setIdempotencyKey(request.idempotencyKey());
        transfer.setCreatedBy(actor.id());
        transfer.setDescription(request.description());
        transfer.setCreatedAt(now);
        transfer.setCompletedAt(now);
        transfers.save(transfer);

        UUID transactionId = UUID.randomUUID();
        ledgerService.postTransfer(transactionId, transfer.getId(), lockedSource, lockedTarget, amount,
                request.description());

        auditService.record("TRANSFER_COMPLETED", actor.id(), actor.role(), "Transfer", transfer.getId().toString(),
                Map.<String, Object>of(
                        "reference", transfer.getReference(),
                        "sourceAccountId", transfer.getSourceAccountId().toString(),
                        "targetAccountId", transfer.getTargetAccountId().toString(),
                        "amountMinor", amount,
                        "currency", currency.name()));

        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID id, AuthenticatedUser actor) {
        Transfer transfer = transfers.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Transfer not found"));
        if (Role.CUSTOMER.name().equals(actor.role()) && !actor.id().equals(transfer.getCreatedBy())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not permitted to read this transfer");
        }
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> list(AuthenticatedUser actor, Pageable pageable) {
        Page<Transfer> page = Role.CUSTOMER.name().equals(actor.role())
                ? transfers.findByCreatedBy(actor.id(), pageable)
                : transfers.findAll(pageable);
        return page.map(this::toResponse);
    }

    private TransferResponse replayIdempotent(Transfer existing, Account source, Account target,
            TransferRequest request) {
        boolean sameRequest = existing.getAmountMinor() == request.amountMinor()
                && existing.getSourceAccountId().equals(source.getId())
                && existing.getTargetAccountId().equals(target.getId());
        if (!sameRequest) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used with different parameters");
        }
        return toResponse(existing);
    }

    private void assertOwnership(Account account, AuthenticatedUser actor) {
        if (Role.CUSTOMER.name().equals(actor.role()) && !actor.id().equals(account.getCustomerId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not permitted to transfer from this account");
        }
    }

    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getStatus(),
                transfer.getSourceAccountId(),
                transfer.getTargetAccountId(),
                transfer.getAmountMinor(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt());
    }

    private String generateReference() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "TRF" + Long.toHexString(ThreadLocalRandom.current().nextLong()).toUpperCase();
            if (transfers.findByReference(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not allocate a transfer reference");
    }
}
