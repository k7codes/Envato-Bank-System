package com.envatobank.api.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.envatobank.api.ledger.dto.LedgerEntryResponse;
import com.envatobank.api.security.CurrentUser;

@RestController
public class LedgerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/api/accounts/{accountId}/statement")
    public List<LedgerEntryResponse> statement(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ledgerService.statement(accountId, CurrentUser.get(), PageRequest.of(Math.max(page, 0), safeSize));
    }
}
