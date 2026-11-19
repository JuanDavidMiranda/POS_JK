package com.possaas.web;

import com.possaas.dto.CreditNoteRequest;
import com.possaas.dto.CreditNoteResponse;
import com.possaas.service.CreditNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credit-notes")
@RequiredArgsConstructor
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditNoteResponse create(@Valid @RequestBody CreditNoteRequest request) {
        return creditNoteService.create(request);
    }
}
