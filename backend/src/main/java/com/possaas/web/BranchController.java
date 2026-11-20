package com.possaas.web;

import com.possaas.dto.BranchDtos;
import com.possaas.dto.TerminalDtos;
import com.possaas.service.AdminAccessGuard;
import com.possaas.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;
    private final AdminAccessGuard adminAccessGuard;

    @GetMapping
    public List<BranchDtos.BranchResponse> listBranches() {
        adminAccessGuard.requireAdmin();
        return branchService.listBranches();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchDtos.BranchResponse createBranch(@Valid @RequestBody BranchDtos.BranchRequest request) {
        adminAccessGuard.requireAdmin();
        return branchService.createBranch(request);
    }

    @PutMapping("/{branchId}")
    public BranchDtos.BranchResponse updateBranch(@PathVariable UUID branchId,
                                                  @Valid @RequestBody BranchDtos.BranchRequest request) {
        adminAccessGuard.requireAdmin();
        return branchService.updateBranch(branchId, request);
    }

    @GetMapping("/terminals")
    public List<TerminalDtos.TerminalResponse> listTerminals(@RequestParam(required = false) UUID branchId) {
        adminAccessGuard.requireAdmin();
        return branchService.listTerminals(branchId);
    }

    @PostMapping("/terminals")
    @ResponseStatus(HttpStatus.CREATED)
    public TerminalDtos.TerminalResponse createTerminal(@Valid @RequestBody TerminalDtos.TerminalRequest request) {
        adminAccessGuard.requireAdmin();
        return branchService.createTerminal(request);
    }

    @PutMapping("/terminals/{terminalId}")
    public TerminalDtos.TerminalResponse updateTerminal(@PathVariable UUID terminalId,
                                                        @Valid @RequestBody TerminalDtos.TerminalRequest request) {
        adminAccessGuard.requireAdmin();
        return branchService.updateTerminal(terminalId, request);
    }
}
