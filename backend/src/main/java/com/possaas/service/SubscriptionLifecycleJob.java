package com.possaas.service;

import com.possaas.domain.Tenant;
import com.possaas.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleJob.class);
    private static final int GRACE_DAYS = 5;

    private final TenantRepository tenantRepository;

    @Scheduled(cron = "0 15 2 * * *") // daily 02:15 AM
    @Transactional
    public void runDaily() {
        Instant now = Instant.now();
        int toPastDue = markActiveAsPastDue(now);
        int toSuspended = markPastDueAsSuspended(now);
        if (toPastDue > 0 || toSuspended > 0) {
            log.info("Subscription lifecycle job: {} tenants -> PAST_DUE, {} tenants -> SUSPENDED", toPastDue, toSuspended);
        }
    }

    private int markActiveAsPastDue(Instant now) {
        List<Tenant> active = tenantRepository.findBySubscriptionStatusIn(List.of("ACTIVE"));
        int changed = 0;
        for (Tenant t : active) {
            Instant due = t.getSubscriptionEndsAt() != null ? t.getSubscriptionEndsAt() : t.getTrialEndsAt();
            if (due != null && now.isAfter(due)) {
                t.setSubscriptionStatus("PAST_DUE");
                changed++;
            }
        }
        if (changed > 0) tenantRepository.saveAll(active);
        return changed;
    }

    private int markPastDueAsSuspended(Instant now) {
        List<Tenant> pastDue = tenantRepository.findBySubscriptionStatusIn(List.of("PAST_DUE"));
        int changed = 0;
        for (Tenant t : pastDue) {
            Instant due = t.getSubscriptionEndsAt() != null ? t.getSubscriptionEndsAt() : t.getTrialEndsAt();
            if (due == null) {
                // no due date but past due: suspend defensively
                t.setSubscriptionStatus("SUSPENDED");
                changed++;
                continue;
            }
            long daysPast = Duration.between(due, now).toDays();
            if (daysPast > GRACE_DAYS) {
                t.setSubscriptionStatus("SUSPENDED");
                changed++;
            }
        }
        if (changed > 0) tenantRepository.saveAll(pastDue);
        return changed;
    }
}
