package com.possaas.dto.superadmin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class SuperAdminDtos {

    public record DashboardResponse(
            long totalCompanies,
            long activeCompanies,
            long trialCompanies,
            long expiredOrSuspendedCompanies,
            long totalSalesTodayCount,
            BigDecimal totalSalesTodayAmount,
            long totalDianFoliosUsedThisPeriod
    ) {}

    public record TenantListItem(
            UUID id,
            String nit,
            String businessName,
            String tradeName,
            String planCode,
            String subscriptionStatus,
            Instant trialEndsAt,
            Instant subscriptionEndsAt,
            int electronicFoliosUsed,
            int electronicFoliosLimit,
            long branchCount,
            long terminalCount,
            Instant lastSyncAt,
            Instant createdAt
    ) {}

    public record TenantListResponse(
            List<TenantListItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record SubscriptionUpdateRequest(
            String planCode,
            String subscriptionStatus,
            Instant subscriptionEndsAt,
            LocalDate trialEndsAtDate,
            Integer electronicFoliosLimit,
            Integer addElectronicFolios
    ) {}

    public record TenantDetailResponse(TenantListItem tenant) {}

    public record TerminalHealth(
            UUID id,
            String name,
            String branchName,
            String dianPrefix,
            boolean active,
            Instant updatedAt
    ) {}

    public record SyncHealth(
            Instant lastSyncAt,
            String lastEntityType
    ) {}

    public record DianIssue(
            String type,
            UUID id,
            String documentNumber,
            String dianStatus,
            String detail,
            Instant createdAt
    ) {}

    public record TenantHealthResponse(
            UUID tenantId,
            String nit,
            String businessName,
            List<TerminalHealth> terminals,
            List<SyncHealth> recentSyncs,
            List<DianIssue> dianPendingOrErrors
    ) {}
}
