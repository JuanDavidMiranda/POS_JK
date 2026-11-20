package com.possaas.service;

import com.possaas.domain.Branch;
import com.possaas.domain.CreditNote;
import com.possaas.domain.PosTerminal;
import com.possaas.domain.Sale;
import com.possaas.domain.SyncInboxLog;
import com.possaas.domain.Tenant;
import com.possaas.dto.superadmin.SuperAdminDtos;
import com.possaas.repository.BranchRepository;
import com.possaas.repository.CreditNoteRepository;
import com.possaas.repository.PosTerminalRepository;
import com.possaas.repository.SaleRepository;
import com.possaas.repository.SyncInboxLogRepository;
import com.possaas.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private static final Set<String> VALID_PLANS = Set.of("TRIAL", "BASIC", "PRO", "MULTI_BRANCH");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "PAST_DUE", "SUSPENDED");

    private final TenantRepository tenantRepository;
    private final SaleRepository saleRepository;
    private final BranchRepository branchRepository;
    private final PosTerminalRepository terminalRepository;
    private final SyncInboxLogRepository syncInboxLogRepository;
    private final CreditNoteRepository creditNoteRepository;

    @Transactional(readOnly = true)
    public SuperAdminDtos.DashboardResponse dashboard() {
        Instant now = Instant.now();
        ZoneId zone = ZoneId.of("America/Bogota");
        ZonedDateTime startOfToday = LocalDate.now(zone).atStartOfDay(zone);
        Instant todayFrom = startOfToday.toInstant();
        Instant todayTo = startOfToday.plusDays(1).toInstant();

        long total = tenantRepository.count();
        long active = tenantRepository.countBySubscriptionStatus("ACTIVE");
        long trial = tenantRepository.countByPlanCode("TRIAL");
        long expired = tenantRepository.countExpiredOrSuspended(now);

        BigDecimal salesTodayAmount = saleRepository.sumGrandTotalBetweenAllTenants(todayFrom, todayTo);
        long salesTodayCount = saleRepository.countBetweenAllTenants(todayFrom, todayTo);
        long foliosUsed = tenantRepository.sumElectronicFoliosUsed();

        return new SuperAdminDtos.DashboardResponse(
                total,
                active,
                trial,
                expired,
                salesTodayCount,
                salesTodayAmount == null ? BigDecimal.ZERO : salesTodayAmount,
                foliosUsed
        );
    }

    @Transactional(readOnly = true)
    public SuperAdminDtos.TenantListResponse listTenants(String q, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Tenant> result = tenantRepository.search(q == null ? "" : q.trim(), status == null ? "ALL" : status.trim(), pageable);
        List<UUID> ids = result.getContent().stream().map(Tenant::getId).toList();

        Map<UUID, TenantRepository.TenantCounts> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (TenantRepository.TenantCounts c : tenantRepository.countsForTenants(ids)) {
                counts.put(c.getTenantId(), c);
            }
        }

        List<SuperAdminDtos.TenantListItem> items = new ArrayList<>();
        for (Tenant t : result.getContent()) {
            TenantRepository.TenantCounts c = counts.get(t.getId());
            long branchCount = c == null ? 0 : c.getBranchCount();
            long terminalCount = c == null ? 0 : c.getTerminalCount();
            Instant lastSync = syncInboxLogRepository.findTopByTenantIdOrderByProcessedAtDesc(t.getId())
                    .map(SyncInboxLog::getProcessedAt).orElse(null);
            if (lastSync == null) {
                lastSync = saleRepository.findTopByTenantIdOrderBySyncedAtDesc(t.getId())
                        .map(Sale::getSyncedAt).orElse(null);
            }
            items.add(toItem(t, branchCount, terminalCount, lastSync));
        }

        return new SuperAdminDtos.TenantListResponse(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public SuperAdminDtos.TenantListItem updateSubscription(UUID tenantId, SuperAdminDtos.SubscriptionUpdateRequest req) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        if (req.planCode() != null && !req.planCode().isBlank()) {
            String plan = req.planCode().trim().toUpperCase();
            if (!VALID_PLANS.contains(plan)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan invalido");
            }
            tenant.setPlanCode(plan);
        }
        if (req.subscriptionStatus() != null && !req.subscriptionStatus().isBlank()) {
            String st = req.subscriptionStatus().trim().toUpperCase();
            if (!VALID_STATUSES.contains(st)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de suscripcion invalido");
            }
            tenant.setSubscriptionStatus(st);
        }
        if (req.subscriptionEndsAt() != null) {
            tenant.setSubscriptionEndsAt(req.subscriptionEndsAt());
        }
        if (req.trialEndsAtDate() != null) {
            tenant.setTrialEndsAt(req.trialEndsAtDate().atStartOfDay(ZoneId.of("America/Bogota")).toInstant());
        }
        if (req.electronicFoliosLimit() != null) {
            if (req.electronicFoliosLimit() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El limite de folios no puede ser negativo");
            }
            tenant.setElectronicFoliosLimit(req.electronicFoliosLimit());
        }
        if (req.addElectronicFolios() != null) {
            if (req.addElectronicFolios() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La recarga de folios debe ser positiva");
            }
            tenant.setElectronicFoliosLimit(tenant.getElectronicFoliosLimit() + req.addElectronicFolios());
        }
        tenantRepository.save(tenant);

        long branches = branchRepository.countByTenantId(tenantId);
        long terminals = terminalRepository.countByTenantId(tenantId);
        Instant lastSync = syncInboxLogRepository.findTopByTenantIdOrderByProcessedAtDesc(tenantId)
                .map(SyncInboxLog::getProcessedAt)
                .orElseGet(() -> saleRepository.findTopByTenantIdOrderBySyncedAtDesc(tenantId).map(Sale::getSyncedAt).orElse(null));
        return toItem(tenant, branches, terminals, lastSync);
    }

    @Transactional(readOnly = true)
    public SuperAdminDtos.TenantHealthResponse health(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        Map<UUID, String> branchNames = new HashMap<>();
        for (Branch b : branchRepository.findByTenantIdOrderByMainDescNameAsc(tenantId)) {
            branchNames.put(b.getId(), b.getName());
        }

        List<SuperAdminDtos.TerminalHealth> terminals = terminalRepository.findByTenantId(tenantId).stream()
                .sorted(Comparator.comparing(PosTerminal::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(t -> new SuperAdminDtos.TerminalHealth(
                        t.getId(), t.getName(), branchNames.getOrDefault(t.getBranchId(), null),
                        t.getDianPrefix(), Boolean.TRUE.equals(t.getActive()), t.getUpdatedAt()))
                .toList();

        List<SuperAdminDtos.SyncHealth> syncs = new ArrayList<>();
        syncInboxLogRepository.findTopByTenantIdOrderByProcessedAtDesc(tenantId).ifPresent(log ->
                syncs.add(new SuperAdminDtos.SyncHealth(log.getProcessedAt(), log.getEntityType())));
        saleRepository.findTopByTenantIdOrderBySyncedAtDesc(tenantId).ifPresent(s -> {
            if (syncs.stream().noneMatch(x -> x.lastSyncAt() != null && x.lastSyncAt().equals(s.getSyncedAt()))) {
                syncs.add(new SuperAdminDtos.SyncHealth(s.getSyncedAt(), "SALE"));
            }
        });

        List<SuperAdminDtos.DianIssue> issues = new ArrayList<>();
        for (Sale s : saleRepository.findDianPendingOrErrorByTenant(tenantId)) {
            issues.add(new SuperAdminDtos.DianIssue("SALE", s.getId(), s.getFullDocumentNumber(), s.getDianStatus(),
                    "Venta con estado DIAN " + s.getDianStatus(), s.getSoldAt()));
        }
        for (CreditNote cn : creditNoteRepository.findDianPendingOrErrorByTenant(tenantId)) {
            issues.add(new SuperAdminDtos.DianIssue("CREDIT_NOTE", cn.getId(), cn.getFullDocumentNumber(), cn.getDianStatus(),
                    "Nota credito con estado DIAN " + cn.getDianStatus(), cn.getCreatedAt()));
        }
        issues.sort(Comparator.comparing(SuperAdminDtos.DianIssue::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));

        return new SuperAdminDtos.TenantHealthResponse(tenant.getId(), tenant.getNit(), tenant.getBusinessName(), terminals, syncs, issues);
    }

    private SuperAdminDtos.TenantListItem toItem(Tenant t, long branchCount, long terminalCount, Instant lastSync) {
        return new SuperAdminDtos.TenantListItem(
                t.getId(), t.getNit(), t.getBusinessName(), t.getTradeName(),
                t.getPlanCode(), t.getSubscriptionStatus(),
                t.getTrialEndsAt(), t.getSubscriptionEndsAt(),
                t.getElectronicFoliosUsed() == null ? 0 : t.getElectronicFoliosUsed(),
                t.getElectronicFoliosLimit() == null ? 0 : t.getElectronicFoliosLimit(),
                branchCount, terminalCount, lastSync, t.getCreatedAt()
        );
    }
}
