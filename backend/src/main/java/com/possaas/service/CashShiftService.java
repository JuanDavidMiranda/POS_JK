package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.CashShift;
import com.possaas.domain.PosTerminal;
import com.possaas.dto.CashShiftDtos;
import com.possaas.repository.CashShiftRepository;
import com.possaas.repository.PosTerminalRepository;
import com.possaas.repository.UserRepository;
import com.possaas.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashShiftService {

    private final CashShiftRepository cashShiftRepository;
    private final PosTerminalRepository terminalRepository;
    private final UserRepository userRepository;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional
    public CashShiftDtos.CashShiftResponse open(CashShiftDtos.OpenRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        cashShiftRepository.findFirstByTenantIdAndStatusOrderByOpenedAtDesc(tenantId, "OPEN")
                .ifPresent(s -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un turno de caja abierto");
                });

        UUID branchId = request.branchId();
        UUID terminalId = request.terminalId();
        if (terminalId != null) {
            PosTerminal terminal = terminalRepository.findByTenantIdAndId(tenantId, terminalId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Terminal no encontrada"));
            if (branchId != null && !branchId.equals(terminal.getBranchId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La terminal no pertenece a la sede indicada");
            }
            branchId = terminal.getBranchId();
        }
        if (branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar terminalId o branchId para abrir el turno");
        }

        Instant now = Instant.now();
        CashShift shift = new CashShift();
        shift.setId(UuidV7.generate());
        shift.setTenantId(tenantId);
        shift.setBranchId(branchId);
        shift.setTerminalId(terminalId);
        shift.setStatus("OPEN");
        shift.setOpeningAmount(nz(request.openingAmount()));
        shift.setOpenedAt(now);
        shift.setNotes(request.notes());
        cashShiftRepository.save(shift);
        return toDto(shift);
    }

    @Transactional
    public CashShiftDtos.CashShiftResponse close(UUID shiftId, CashShiftDtos.CloseRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        CashShift shift = cashShiftRepository.findByIdAndTenantIdForUpdate(shiftId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno no encontrado"));
        if (!"OPEN".equals(shift.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El turno ya está cerrado");
        }
        if (request.authorizedByUserId() != null) {
            boolean exists = userRepository.findByIdAndTenantId(request.authorizedByUserId(), tenantId).isPresent();
            if (!exists) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario autorizador inválido");
            }
            shift.setAuthorizedByUserId(request.authorizedByUserId());
        }

        BigDecimal cashSales = nz(cashShiftRepository.sumCashSalesByShift(tenantId, shiftId));
        BigDecimal expected = nz(shift.getOpeningAmount()).add(cashSales).setScale(2, RoundingMode.HALF_UP);
        BigDecimal counted = nz(request.countedCashAmount());
        BigDecimal diff = counted.subtract(expected).setScale(2, RoundingMode.HALF_UP);

        shift.setExpectedCashAmount(expected);
        shift.setCountedCashAmount(counted);
        shift.setDifferenceAmount(diff);
        shift.setStatus("CLOSED");
        shift.setClosedAt(Instant.now());
        if (request.notes() != null && !request.notes().isBlank()) {
            shift.setNotes(request.notes());
        }
        cashShiftRepository.save(shift);
        return toDto(shift);
    }

    @Transactional(readOnly = true)
    public List<CashShiftDtos.CashShiftResponse> recent() {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        return cashShiftRepository.findTop20ByTenantIdOrderByOpenedAtDesc(tenantId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CashShiftDtos.CashShiftResponse current() {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        return cashShiftRepository.findFirstByTenantIdAndStatusOrderByOpenedAtDesc(tenantId, "OPEN")
                .map(this::toDto)
                .orElse(null);
    }

    private CashShiftDtos.CashShiftResponse toDto(CashShift s) {
        return new CashShiftDtos.CashShiftResponse(
                s.getId(), s.getBranchId(), s.getTerminalId(), s.getOpenedBy(), s.getClosedBy(), s.getAuthorizedByUserId(),
                s.getStatus(), s.getOpeningAmount(), s.getExpectedCashAmount(), s.getCountedCashAmount(),
                s.getDifferenceAmount(), s.getOpenedAt(), s.getClosedAt(), s.getNotes());
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v.setScale(2, RoundingMode.HALF_UP);
    }
}
