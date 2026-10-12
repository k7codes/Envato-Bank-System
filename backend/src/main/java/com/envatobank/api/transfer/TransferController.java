package com.envatobank.api.transfer;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.envatobank.api.common.web.PageResponse;
import com.envatobank.api.security.CurrentUser;
import com.envatobank.api.transfer.dto.TransferRequest;
import com.envatobank.api.transfer.dto.TransferResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return transferService.transfer(request, CurrentUser.get());
    }

    @GetMapping
    public PageResponse<TransferResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(transferService.list(CurrentUser.get(), pageable));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        return transferService.get(id, CurrentUser.get());
    }
}
