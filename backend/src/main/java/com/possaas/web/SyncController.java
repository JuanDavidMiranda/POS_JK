package com.possaas.web;

import com.possaas.dto.SyncPullResponse;
import com.possaas.dto.SyncPushRequest;
import com.possaas.dto.SyncPushResponse;
import com.possaas.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @GetMapping("/pull")
    public SyncPullResponse pull(
            @RequestParam(name = "terminalId", required = false) UUID terminalId,
            @RequestParam(name = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return syncService.pull(terminalId, since);
    }

    @PostMapping("/push")
    @ResponseStatus(HttpStatus.OK)
    public SyncPushResponse push(@Valid @RequestBody SyncPushRequest request) {
        return syncService.push(request);
    }
}
