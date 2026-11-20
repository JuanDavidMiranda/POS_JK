package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.Branch;
import com.possaas.domain.BranchInventory;
import com.possaas.domain.PosTerminal;
import com.possaas.domain.Product;
import com.possaas.dto.BranchDtos;
import com.possaas.dto.TerminalDtos;
import com.possaas.repository.BranchInventoryRepository;
import com.possaas.repository.BranchRepository;
import com.possaas.repository.PosTerminalRepository;
import com.possaas.repository.ProductRepository;
import com.possaas.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchInventoryRepository branchInventoryRepository;
    private final ProductRepository productRepository;
    private final PosTerminalRepository terminalRepository;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional(readOnly = true)
    public List<BranchDtos.BranchResponse> listBranches() {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        return branchRepository.findByTenantIdOrderByMainDescNameAsc(tenantId).stream()
                .map(this::toBranchResponse)
                .toList();
    }

    @Transactional
    public BranchDtos.BranchResponse createBranch(BranchDtos.BranchRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        String code = normalizeCode(request.code());
        if (branchRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una sede con el código " + code);
        }
        Instant now = Instant.now();
        Branch branch = new Branch();
        branch.setId(UuidV7.generate());
        branch.setTenantId(tenantId);
        branch.setCode(code);
        branch.setName(request.name().trim());
        branch.setAddress(blankToNull(request.address()));
        branch.setCity(blankToNull(request.city()));
        branch.setPhone(blankToNull(request.phone()));
        branch.setMain(false);
        branch.setActive(request.active() == null || request.active());
        branch.setCreatedAt(now);
        branch.setUpdatedAt(now);
        branchRepository.save(branch);
        initializeInventoryForActiveProducts(tenantId, branch.getId(), now);
        return toBranchResponse(branch);
    }

    @Transactional
    public BranchDtos.BranchResponse updateBranch(UUID branchId, BranchDtos.BranchRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        Branch branch = branchRepository.findByTenantIdAndId(tenantId, branchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada"));
        String code = normalizeCode(request.code());
        branchRepository.findByTenantIdAndCode(tenantId, code).ifPresent(existing -> {
            if (!existing.getId().equals(branchId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una sede con el código " + code);
            }
        });
        branch.setCode(code);
        branch.setName(request.name().trim());
        branch.setAddress(blankToNull(request.address()));
        branch.setCity(blankToNull(request.city()));
        branch.setPhone(blankToNull(request.phone()));
        if (request.active() != null) {
            if (Boolean.FALSE.equals(request.active()) && Boolean.TRUE.equals(branch.getMain())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede desactivar la sede principal");
            }
            branch.setActive(request.active());
        }
        branch.setUpdatedAt(Instant.now());
        return toBranchResponse(branchRepository.save(branch));
    }

    @Transactional(readOnly = true)
    public List<TerminalDtos.TerminalResponse> listTerminals(UUID branchId) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        List<PosTerminal> terminals = branchId != null
                ? terminalRepository.findByTenantIdAndBranchIdOrderByNameAsc(tenantId, branchId)
                : terminalRepository.findByTenantIdOrderByNameAsc(tenantId);
        return terminals.stream().map(this::toTerminalResponse).toList();
    }

    @Transactional
    public TerminalDtos.TerminalResponse createTerminal(TerminalDtos.TerminalRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        Branch branch = requireActiveBranch(tenantId, request.branchId());
        if (request.rangeTo() < request.rangeFrom()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rangeTo debe ser mayor o igual a rangeFrom");
        }
        terminalRepository.findByTenantIdAndDianPrefix(tenantId, request.dianPrefix().trim())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una caja con ese prefijo DIAN");
                });
        Instant now = Instant.now();
        PosTerminal terminal = new PosTerminal();
        terminal.setId(UuidV7.generate());
        terminal.setTenantId(tenantId);
        terminal.setBranchId(branch.getId());
        terminal.setName(request.name().trim());
        terminal.setDianPrefix(request.dianPrefix().trim());
        terminal.setRangeFrom(request.rangeFrom());
        terminal.setRangeTo(request.rangeTo());
        terminal.setLastSyncedSequence(0L);
        String cnPrefix = (request.creditNotePrefix() == null || request.creditNotePrefix().isBlank())
                ? "NC" + terminal.getDianPrefix()
                : request.creditNotePrefix().trim();
        terminal.setCreditNotePrefix(cnPrefix);
        terminal.setCreditNoteLastSequence(0L);
        terminal.setActive(request.active() == null || request.active());
        terminal.setUpdatedAt(now);
        return toTerminalResponse(terminalRepository.save(terminal));
    }

    @Transactional
    public TerminalDtos.TerminalResponse updateTerminal(UUID terminalId, TerminalDtos.TerminalRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        PosTerminal terminal = terminalRepository.findByTenantIdAndId(tenantId, terminalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caja no encontrada"));
        Branch branch = requireActiveBranch(tenantId, request.branchId());
        if (request.rangeTo() < request.rangeFrom()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rangeTo debe ser mayor o igual a rangeFrom");
        }
        terminalRepository.findByTenantIdAndDianPrefix(tenantId, request.dianPrefix().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(terminalId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una caja con ese prefijo DIAN");
            }
        });
        terminal.setBranchId(branch.getId());
        terminal.setName(request.name().trim());
        terminal.setDianPrefix(request.dianPrefix().trim());
        terminal.setRangeFrom(request.rangeFrom());
        terminal.setRangeTo(request.rangeTo());
        if (request.creditNotePrefix() != null && !request.creditNotePrefix().isBlank()) {
            terminal.setCreditNotePrefix(request.creditNotePrefix().trim());
        }
        if (request.active() != null) {
            terminal.setActive(request.active());
        }
        terminal.setUpdatedAt(Instant.now());
        return toTerminalResponse(terminalRepository.save(terminal));
    }

    @Transactional
    public void initializeInventoryForActiveProducts(UUID tenantId, UUID branchId, Instant now) {
        List<Product> products = productRepository.findByTenantIdAndActiveTrue(tenantId);
        for (Product product : products) {
            branchInventoryRepository.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, product.getId())
                    .orElseGet(() -> {
                        BranchInventory inv = new BranchInventory();
                        inv.setId(UuidV7.generate());
                        inv.setTenantId(tenantId);
                        inv.setBranchId(branchId);
                        inv.setProductId(product.getId());
                        inv.setCurrentStock(BigDecimal.ZERO);
                        inv.setMinStock(BigDecimal.ZERO);
                        inv.setUpdatedAt(now);
                        return branchInventoryRepository.save(inv);
                    });
        }
    }

    public BranchDtos.BranchResponse toBranchResponse(Branch branch) {
        return new BranchDtos.BranchResponse(
                branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(), branch.getCity(),
                branch.getPhone(), Boolean.TRUE.equals(branch.getMain()), Boolean.TRUE.equals(branch.getActive()),
                branch.getCreatedAt(), branch.getUpdatedAt());
    }

    public TerminalDtos.TerminalResponse toTerminalResponse(PosTerminal terminal) {
        Branch branch = branchRepository.findById(terminal.getBranchId()).orElse(null);
        return new TerminalDtos.TerminalResponse(
                terminal.getId(), terminal.getBranchId(),
                branch != null ? branch.getCode() : null,
                branch != null ? branch.getName() : null,
                terminal.getName(), terminal.getDianPrefix(),
                terminal.getRangeFrom(), terminal.getRangeTo(), terminal.getLastSyncedSequence(),
                terminal.getCreditNotePrefix(), terminal.getCreditNoteLastSequence(),
                Boolean.TRUE.equals(terminal.getActive()), terminal.getUpdatedAt());
    }

    private Branch requireActiveBranch(UUID tenantId, UUID branchId) {
        if (branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "branchId es obligatorio");
        }
        Branch branch = branchRepository.findByTenantIdAndId(tenantId, branchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede no existe o no pertenece al tenant"));
        if (!Boolean.TRUE.equals(branch.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede seleccionada está inactiva");
        }
        return branch;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de sede es obligatorio");
        }
        return code.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
